package ca.teamdman.sfm.common.program.signature;

import net.minecraft.resources.ResourceLocation;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Bounded public metadata only. No key material or user drawing is accepted by this format. */
public final class ProgramAttestationCodec {
    public static final int MAX_ATTESTATION_BYTES = 20 * 1024;
    public static final int MAX_HISTORY_BYTES = 64 * 1024;
    private static final int VERSION = 1;

    private ProgramAttestationCodec() { }

    public static byte[] encode(ProgramAttestation value) {
        try {
            var bytes = new ByteArrayOutputStream();
            var output = new DataOutputStream(bytes);
            output.writeInt(VERSION);
            writeDescriptor(output, value.descriptor());
            writeString(output, value.publicKey(), 172);
            writeString(output, value.signature(), 88);
            return bounded(bytes.toByteArray(), MAX_ATTESTATION_BYTES);
        } catch (IOException impossible) { throw new IllegalStateException(impossible); }
    }

    /** Bounds every string/count before constructing a public key. Signature verification is separate. */
    public static ProgramAttestation decode(byte[] encoded) {
        bounded(encoded, MAX_ATTESTATION_BYTES);
        try {
            var input = new DataInputStream(new ByteArrayInputStream(encoded));
            require(input.readInt() == VERSION);
            var descriptor = readDescriptor(input);
            String publicKey = readString(input, 172);
            String signature = readString(input, 88);
            require(input.available() == 0);
            return new ProgramAttestation(descriptor, publicKey, signature);
        } catch (IOException invalid) { throw new IllegalArgumentException("Invalid attestation encoding", invalid); }
    }

    public static byte[] encodeHistory(List<ProgramAttestation> history) {
        require(history.size() <= ProgramAttestationHistory.MAX_ATTESTATIONS);
        try {
            var bytes = new ByteArrayOutputStream();
            var output = new DataOutputStream(bytes);
            output.writeInt(VERSION);
            output.writeInt(history.size());
            for (var value : history) {
                byte[] encoded = encode(value);
                require(bytes.size() <= MAX_HISTORY_BYTES - 4 - encoded.length);
                output.writeInt(encoded.length);
                output.write(encoded);
            }
            return bounded(bytes.toByteArray(), MAX_HISTORY_BYTES);
        } catch (IOException impossible) { throw new IllegalStateException(impossible); }
    }

    /** Reject the complete history on any malformed/forged entry; never retain a purported partial authority. */
    public static List<ProgramAttestation> decodeHistory(byte[] encoded) {
        bounded(encoded, MAX_HISTORY_BYTES);
        try {
            var input = new DataInputStream(new ByteArrayInputStream(encoded));
            require(input.readInt() == VERSION);
            int count = input.readInt();
            require(count >= 0 && count <= ProgramAttestationHistory.MAX_ATTESTATIONS);
            var entries = new ArrayList<byte[]>(count);
            // Preflight every byte/count bound before any entry's public-key or signature work.
            for (int i = 0; i < count; i++) {
                int size = input.readInt();
                require(size > 0 && size <= MAX_ATTESTATION_BYTES && size <= input.available());
                entries.add(input.readNBytes(size));
            }
            require(input.available() == 0);
            var result = new ProgramAttestationHistory();
            for (byte[] entry : entries) require(result.append(decode(entry)));
            return result.snapshot();
        } catch (IOException invalid) { throw new IllegalArgumentException("Invalid attestation history", invalid); }
    }

    static void writeDescriptor(DataOutput output, ProgramSignatureDescriptor descriptor) throws IOException {
        writeString(output, descriptor.sourceSha256(), 64);
        writeString(output, descriptor.runtime(), 128);
        output.writeInt(descriptor.capabilities().size());
        for (var capability : descriptor.capabilities()) writeString(output, capability.toString(), 256);
    }

    static ProgramSignatureDescriptor readDescriptor(DataInput input) throws IOException {
        String sourceHash = readString(input, 64);
        String runtime = readString(input, 128);
        int count = input.readInt();
        require(count > 0 && count <= ProgramSignatureDescriptor.MAX_CAPABILITIES);
        var capabilities = new ArrayList<ResourceLocation>(count);
        for (int i = 0; i < count; i++) capabilities.add(new ResourceLocation(readString(input, 256)));
        var result = new ProgramSignatureDescriptor(sourceHash, runtime, capabilities);
        require(result.capabilities().equals(capabilities)); // Canonical order; no duplicate/wildcard expansion.
        return result;
    }

    static void writeString(DataOutput output, String value, int maxBytes) throws IOException {
        byte[] bytes = strictBytes(value, maxBytes);
        output.writeInt(bytes.length);
        output.write(bytes);
    }

    static String readString(DataInput input, int maxBytes) throws IOException {
        int length = input.readInt();
        require(length >= 0 && length <= maxBytes);
        byte[] bytes = new byte[length];
        input.readFully(bytes);
        return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
    }

    static byte[] strictBytes(String value, int maxBytes) {
        require(value.length() <= maxBytes);
        try {
            ByteBuffer bytes = StandardCharsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).encode(CharBuffer.wrap(value));
            require(bytes.remaining() <= maxBytes);
            byte[] result = new byte[bytes.remaining()];
            bytes.get(result);
            return result;
        } catch (IOException invalid) { throw new IllegalArgumentException("Invalid UTF-8 text", invalid); }
    }

    static byte[] bounded(byte[] bytes, int maximum) {
        require(bytes != null && bytes.length > 0 && bytes.length <= maximum);
        return bytes;
    }

    static void require(boolean value) {
        if (!value) throw new IllegalArgumentException("Public signing metadata exceeds its format or budget");
    }
}
