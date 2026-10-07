package ca.teamdman.sfm.common.program.signature;

import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Pure revision/CAS boundary. Caller supplies server-thread authorization and global operation budgets. */
public final class ClientManagerSigningState {
    public enum Status {
        REVIEWED, SAVED, SIGNED, ALREADY_SIGNED, STALE_REVISION, CHALLENGE_UNAVAILABLE,
        INVALID_SIGNATURE, HISTORY_FULL, INVALID_PROGRAM, UNAUTHORIZED, RATE_LIMITED, UNAVAILABLE
    }
    public record Review(Status status, Optional<ClientManagerSigningAcknowledgement> acknowledgement) { }

    private final UUID incarnation;
    private long revision;
    private ClientManagerSigningBody body;
    private final ProgramAttestationHistory history = new ProgramAttestationHistory();
    private ClientManagerSigningSnapshot cachedSnapshot;

    public ClientManagerSigningState(UUID incarnation, ClientManagerSigningBody body) {
        this(incarnation, 0, body, List.of());
    }

    public ClientManagerSigningState(UUID incarnation, long revision, ClientManagerSigningBody body,
                                     List<ProgramAttestation> restoredHistory) {
        this.incarnation = Objects.requireNonNull(incarnation);
        this.body = Objects.requireNonNull(body);
        ProgramAttestationCodec.require(revision >= 0);
        this.revision = revision;
        ProgramAttestationCodec.encodeHistory(restoredHistory); // Bound the aggregate before any verification.
        for (var entry : restoredHistory) ProgramAttestationCodec.require(history.append(entry));
    }

    public synchronized ClientManagerSigningSnapshot snapshot() {
        // Runtime trust consults this for every eligible frame. Avoid re-encoding up to 64 KiB
        // of immutable public history on reads; only body/history mutations invalidate it.
        if (cachedSnapshot == null) {
            cachedSnapshot = new ClientManagerSigningSnapshot(incarnation, revision, body, history.snapshot());
        }
        return cachedSnapshot;
    }

    /** Called for every actual disk replacement/removal as well as label/source edits. */
    public synchronized void replaceBody(ClientManagerSigningBody replacement) {
        Objects.requireNonNull(replacement);
        if (revision == Long.MAX_VALUE) throw new IllegalStateException("Client Manager revision exhausted");
        body = replacement;
        revision++;
        cachedSnapshot = null;
        // History remains independent evidence; stale challenges cannot pass the revision comparison.
    }

    public synchronized Review review(ClientManagerSigningSession session, UUID player,
                                       Collection<ResourceLocation> declaredCapabilities, long tick) {
        return review(session, player, declaredCapabilities, tick, "");
    }

    public synchronized Review review(ClientManagerSigningSession session, UUID player,
                                       Collection<ResourceLocation> declaredCapabilities, long tick, String address) {
        ClientManagerSigningSnapshot snapshot = snapshot();
        final ProgramSignatureDescriptor descriptor;
        try { descriptor = snapshot.descriptor(declaredCapabilities); }
        catch (IllegalArgumentException invalid) { return new Review(Status.INVALID_PROGRAM, Optional.empty()); }
        var acknowledgement = session.issue(player, snapshot, descriptor, tick, address);
        return new Review(acknowledgement.isPresent() ? Status.REVIEWED : Status.CHALLENGE_UNAVAILABLE, acknowledgement);
    }

    /** Save changes source only; all current server-owned label bindings stay in the acknowledged scope. */
    public synchronized Review save(ClientManagerSigningSession session, UUID player, UUID expectedIncarnation,
                                     long expectedRevision, String source,
                                     Collection<ResourceLocation> declaredCapabilities, long tick) {
        return save(session, player, expectedIncarnation, expectedRevision, source, declaredCapabilities, tick, "");
    }

    public synchronized Review save(ClientManagerSigningSession session, UUID player, UUID expectedIncarnation,
                                     long expectedRevision, String source,
                                     Collection<ResourceLocation> declaredCapabilities, long tick, String address) {
        if (!incarnation.equals(expectedIncarnation) || revision != expectedRevision) {
            return new Review(Status.STALE_REVISION, Optional.empty());
        }
        final ClientManagerSigningBody replacement;
        try {
            replacement = new ClientManagerSigningBody(source, body.labels());
            ProgramSignatureDescriptor.fromSource(replacement.source(), ClientManagerSigningSnapshot.RUNTIME, declaredCapabilities);
        } catch (IllegalArgumentException invalid) { return new Review(Status.INVALID_PROGRAM, Optional.empty()); }
        if (revision == Long.MAX_VALUE) return new Review(Status.UNAVAILABLE, Optional.empty());
        session.invalidate(incarnation);
        var proposed = new ClientManagerSigningSnapshot(incarnation, revision + 1, replacement, history.snapshot());
        var acknowledgement = session.issue(player, proposed, proposed.descriptor(declaredCapabilities), tick, address);
        if (acknowledgement.isEmpty()) return new Review(Status.CHALLENGE_UNAVAILABLE, Optional.empty());
        replaceBody(replacement);
        return new Review(Status.SAVED, acknowledgement);
    }

    /** No crypto runs until the actor's one-use challenge, exact revision and descriptor all match. */
    public synchronized Status submit(ClientManagerSigningSession session, UUID player, UUID expectedIncarnation,
                                       long expectedRevision, UUID challenge, ProgramAttestation attestation, long tick) {
        Optional<ClientManagerSigningAcknowledgement> found = session.take(player, expectedIncarnation, challenge, tick);
        if (found.isEmpty()) return Status.CHALLENGE_UNAVAILABLE;
        return submitAcknowledged(found.get(), expectedIncarnation, expectedRevision, attestation);
    }

    /** Even malformed public-key bytes consume the one-use review before parsing or cryptography. */
    public synchronized Status submitEncoded(ClientManagerSigningSession session, UUID player, UUID expectedIncarnation,
                                              long expectedRevision, UUID challenge, byte[] encoded, long tick,
                                              String address) {
        var found = session.take(player, expectedIncarnation, challenge, tick, address);
        if (found.isEmpty()) return Status.CHALLENGE_UNAVAILABLE;
        if (!matchesRevision(found.get(), expectedIncarnation, expectedRevision)) return Status.STALE_REVISION;
        final ProgramAttestation attestation;
        try { attestation = ProgramAttestationCodec.decode(encoded); }
        catch (IllegalArgumentException invalid) { return Status.INVALID_SIGNATURE; }
        return submitAcknowledged(found.get(), expectedIncarnation, expectedRevision, attestation);
    }

    private boolean matchesRevision(ClientManagerSigningAcknowledgement acknowledgement,
                                    UUID expectedIncarnation, long expectedRevision) {
        return incarnation.equals(expectedIncarnation) && revision == expectedRevision
               && acknowledgement.snapshot().incarnation().equals(incarnation)
               && acknowledgement.snapshot().revision() == revision
               && acknowledgement.snapshot().body().equals(body);
    }

    private Status submitAcknowledged(ClientManagerSigningAcknowledgement acknowledgement,
                                       UUID expectedIncarnation, long expectedRevision, ProgramAttestation attestation) {
        if (!matchesRevision(acknowledgement, expectedIncarnation, expectedRevision)) return Status.STALE_REVISION;
        if (!acknowledgement.descriptor().equals(attestation.descriptor())) return Status.INVALID_SIGNATURE;
        List<ProgramAttestation> current = history.snapshot();
        if (current.contains(attestation)) return Status.ALREADY_SIGNED;
        if (current.size() >= ProgramAttestationHistory.MAX_ATTESTATIONS) return Status.HISTORY_FULL;
        var prospective = new ArrayList<>(current);
        prospective.add(attestation);
        try { ProgramAttestationCodec.encodeHistory(prospective); }
        catch (IllegalArgumentException tooLarge) { return Status.HISTORY_FULL; }
        try {
            if (!history.append(attestation)) return Status.ALREADY_SIGNED;
            cachedSnapshot = null;
            return Status.SIGNED;
        }
        catch (IllegalArgumentException invalid) { return Status.INVALID_SIGNATURE; }
    }
}
