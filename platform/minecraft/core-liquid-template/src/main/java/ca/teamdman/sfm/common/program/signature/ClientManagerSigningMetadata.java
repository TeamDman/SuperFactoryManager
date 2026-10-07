package ca.teamdman.sfm.common.program.signature;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Persisted/public metadata. Pending review challenges are deliberately absent. */
public record ClientManagerSigningMetadata(UUID incarnation, long revision, String sourceSha256,
                                           String bindingSha256, List<ProgramAttestation> history) {
    public ClientManagerSigningMetadata {
        Objects.requireNonNull(incarnation);
        ProgramAttestationCodec.require(revision >= 0 && sourceSha256.matches("[0-9a-f]{64}")
                && bindingSha256.matches("[0-9a-f]{64}"));
        ProgramAttestationCodec.require(history.size() <= ProgramAttestationHistory.MAX_ATTESTATIONS);
        history = List.copyOf(history);
        ProgramAttestationCodec.encodeHistory(history);
    }
    public static ClientManagerSigningMetadata from(ClientManagerSigningSnapshot snapshot) {
        return new ClientManagerSigningMetadata(snapshot.incarnation(), snapshot.revision(),
                snapshot.body().sourceSha256(), snapshot.body().bindingSha256(), snapshot.history());
    }
    public boolean matchesBody(ClientManagerSigningBody body) {
        return sourceSha256.equals(body.sourceSha256()) && bindingSha256.equals(body.bindingSha256());
    }
    /** Disk edits across a server reload advance the content revision; a client projection must reject mismatches instead. */
    public ClientManagerSigningState restoreServerState(ClientManagerSigningBody body) {
        long restoredRevision = revision;
        if (!matchesBody(body)) {
            if (revision == Long.MAX_VALUE) throw new IllegalArgumentException("Client Manager revision exhausted");
            restoredRevision++;
        }
        return new ClientManagerSigningState(incarnation, restoredRevision, body, history);
    }
}
