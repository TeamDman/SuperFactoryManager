package ca.teamdman.sfm.common.program.signature;

import ca.teamdman.sfm.common.blockentity.ClientManagerProgramProjection;

import java.io.*;
import java.util.ArrayList;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/** Bounded binary transport for public server revision evidence; unknown/trailing data fails closed. */
public final class ClientManagerSigningCodec {
    public static final int MAX_METADATA_BYTES = ProgramAttestationCodec.MAX_HISTORY_BYTES + 256;
    public static final int MAX_ACKNOWLEDGEMENT_BYTES = 192 * 1024;
    private static final int VERSION = 1;

    private ClientManagerSigningCodec() { }

    public static byte[] encodeMetadata(ClientManagerSigningMetadata metadata) {
        try {
            var bytes = new ByteArrayOutputStream();
            var output = new DataOutputStream(bytes);
            output.writeInt(VERSION);
            writeUuid(output, metadata.incarnation());
            output.writeLong(metadata.revision());
            ProgramAttestationCodec.writeString(output, metadata.sourceSha256(), 64);
            ProgramAttestationCodec.writeString(output, metadata.bindingSha256(), 64);
            writeHistory(output, metadata.history());
            return ProgramAttestationCodec.bounded(bytes.toByteArray(), MAX_METADATA_BYTES);
        } catch (IOException impossible) { throw new IllegalStateException(impossible); }
    }

    public static ClientManagerSigningMetadata decodeMetadata(byte[] encoded) {
        ProgramAttestationCodec.bounded(encoded, MAX_METADATA_BYTES);
        try {
            var input = new DataInputStream(new ByteArrayInputStream(encoded));
            ProgramAttestationCodec.require(input.readInt() == VERSION);
            UUID incarnation = readUuid(input);
            long revision = input.readLong();
            String source = ProgramAttestationCodec.readString(input, 64);
            String bindings = ProgramAttestationCodec.readString(input, 64);
            byte[] history = readHistoryBytes(input);
            ProgramAttestationCodec.require(input.available() == 0);
            return new ClientManagerSigningMetadata(incarnation, revision, source, bindings,
                    ProgramAttestationCodec.decodeHistory(history));
        } catch (IOException invalid) { throw new IllegalArgumentException("Invalid Client Manager signing metadata", invalid); }
    }

    public static byte[] encodeAcknowledgement(ClientManagerSigningAcknowledgement acknowledgement) {
        try {
            var bytes = new ByteArrayOutputStream();
            var output = new DataOutputStream(bytes);
            output.writeInt(VERSION);
            var snapshot = acknowledgement.snapshot();
            writeUuid(output, snapshot.incarnation());
            output.writeLong(snapshot.revision());
            writeBody(output, snapshot.body());
            writeHistory(output, snapshot.history());
            ProgramAttestationCodec.writeDescriptor(output, acknowledgement.descriptor());
            writeUuid(output, acknowledgement.challenge());
            output.writeLong(acknowledgement.expiresAtTick());
            return ProgramAttestationCodec.bounded(bytes.toByteArray(), MAX_ACKNOWLEDGEMENT_BYTES);
        } catch (IOException impossible) { throw new IllegalStateException(impossible); }
    }

    public static ClientManagerSigningAcknowledgement decodeAcknowledgement(byte[] encoded) {
        ProgramAttestationCodec.bounded(encoded, MAX_ACKNOWLEDGEMENT_BYTES);
        try {
            var input = new DataInputStream(new ByteArrayInputStream(encoded));
            ProgramAttestationCodec.require(input.readInt() == VERSION);
            UUID incarnation = readUuid(input);
            long revision = input.readLong();
            var body = readBody(input);
            byte[] history = readHistoryBytes(input);
            var descriptor = ProgramAttestationCodec.readDescriptor(input);
            UUID challenge = readUuid(input);
            long expires = input.readLong();
            ProgramAttestationCodec.require(input.available() == 0);
            return new ClientManagerSigningAcknowledgement(new ClientManagerSigningSnapshot(incarnation, revision,
                    body, ProgramAttestationCodec.decodeHistory(history)), descriptor, challenge, expires);
        } catch (IOException invalid) { throw new IllegalArgumentException("Invalid Client Manager signing acknowledgement", invalid); }
    }

    private static void writeBody(DataOutput output, ClientManagerSigningBody body) throws IOException {
        ProgramAttestationCodec.writeString(output, body.source(), ProgramSignatureDescriptor.MAX_SOURCE_BYTES);
        output.writeInt(body.labels().size());
        for (var entry : body.labels().entrySet()) {
            ProgramAttestationCodec.writeString(output, entry.getKey(), 1024);
            output.writeInt(entry.getValue().size());
            for (long position : entry.getValue()) output.writeLong(position);
        }
    }

    private static ClientManagerSigningBody readBody(DataInput input) throws IOException {
        String source = ProgramAttestationCodec.readString(input, ProgramSignatureDescriptor.MAX_SOURCE_BYTES);
        int labels = input.readInt();
        ProgramAttestationCodec.require(labels >= 0 && labels <= ClientManagerProgramProjection.MAX_LABELS);
        Map<String, java.util.List<Long>> result = new TreeMap<>();
        int remaining = ClientManagerProgramProjection.MAX_POSITIONS;
        for (int i = 0; i < labels; i++) {
            String name = ProgramAttestationCodec.readString(input, 1024);
            int positions = input.readInt();
            ProgramAttestationCodec.require(positions >= 0 && positions <= remaining);
            remaining -= positions;
            var values = new ArrayList<Long>(positions);
            for (int j = 0; j < positions; j++) values.add(input.readLong());
            ProgramAttestationCodec.require(result.put(name, values) == null);
        }
        var body = new ClientManagerSigningBody(source, result);
        // An acknowledgement promises exact already-normalized stored source, not client-side repair.
        ProgramAttestationCodec.require(body.source().equals(source));
        return body;
    }

    private static void writeHistory(DataOutput output, java.util.List<ProgramAttestation> history) throws IOException {
        byte[] encoded = ProgramAttestationCodec.encodeHistory(history);
        output.writeInt(encoded.length);
        output.write(encoded);
    }
    private static byte[] readHistoryBytes(DataInputStream input) throws IOException {
        int length = input.readInt();
        ProgramAttestationCodec.require(length > 0 && length <= ProgramAttestationCodec.MAX_HISTORY_BYTES
                && length <= input.available());
        return input.readNBytes(length);
    }
    private static void writeUuid(DataOutput output, UUID uuid) throws IOException {
        output.writeLong(uuid.getMostSignificantBits());
        output.writeLong(uuid.getLeastSignificantBits());
    }
    private static UUID readUuid(DataInput input) throws IOException { return new UUID(input.readLong(), input.readLong()); }
}
