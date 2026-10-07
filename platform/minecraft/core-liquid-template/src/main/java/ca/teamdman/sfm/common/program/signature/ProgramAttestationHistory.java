package ca.teamdman.sfm.common.program.signature;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Bounded independent attestations. Editing a body makes old records inactive, not invalid history. */
public final class ProgramAttestationHistory {
    public static final int MAX_ATTESTATIONS = 32;
    private final List<ProgramAttestation> records = new ArrayList<>();

    public synchronized boolean append(ProgramAttestation attestation) {
        Objects.requireNonNull(attestation);
        if (!attestation.verifies(attestation.descriptor())) throw new IllegalArgumentException("Invalid attestation");
        if (records.stream().anyMatch(existing -> existing.descriptor().equals(attestation.descriptor())
                && existing.fingerprint().equals(attestation.fingerprint()))) return false;
        // Do not evict trusted history just because an untrusted writer appends signatures.
        if (records.size() >= MAX_ATTESTATIONS) throw new IllegalStateException("Signature history is full");
        records.add(attestation);
        return true;
    }

    public synchronized List<ProgramAttestation> active(ProgramSignatureDescriptor current) {
        return records.stream().filter(record -> record.verifies(current)).toList();
    }

    public synchronized List<ProgramAttestation> snapshot() { return List.copyOf(records); }
}
