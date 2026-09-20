package ca.teamdman.sfm.client.program;

import ca.teamdman.sfm.common.program.signature.ProgramAttestation;
import ca.teamdman.sfm.common.program.signature.ProgramAttestationHistory;
import ca.teamdman.sfm.common.program.signature.ProgramSignatureDescriptor;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Function;
import java.util.function.LongSupplier;

/** Persists explicit local signer decisions and resolves them only against current client-verified programs. */
public final class ClientProgramSignerTrustService implements ClientProgramSignerAuthority {
    public record ObservedProgram(String source, List<ProgramAttestation> attestations) {
        public ObservedProgram {
            Objects.requireNonNull(source);
            // Runtime parsing must use the same LF-only source covered by the signature.
            if (source.indexOf('\r') >= 0) throw new IllegalArgumentException("Signing requires normalized stored source");
            ProgramSignatureDescriptor.normalizedSourceBytes(source);
            attestations = List.copyOf(attestations);
            if (attestations.size() > ProgramAttestationHistory.MAX_ATTESTATIONS) {
                throw new IllegalArgumentException("Too many program attestations");
            }
        }
    }
    private record Cached(ObservedProgram program, boolean allowed, long expiresAt) {}
    private final Path destination;
    private final ClientProgramSignerTrustStore store;
    private final Function<ClientProgramIdentity, Optional<ObservedProgram>> currentProgram;
    private final LongSupplier clock;
    private final Map<ClientProgramIdentity, Cached> cache = new LinkedHashMap<>();
    private String diagnostic;
    private boolean available;

    public ClientProgramSignerTrustService(Path destination, LongSupplier clock,
                                           Function<ClientProgramIdentity, Optional<ObservedProgram>> currentProgram) {
        this.destination = Objects.requireNonNull(destination);
        this.clock = Objects.requireNonNull(clock);
        this.currentProgram = Objects.requireNonNull(currentProgram);
        var loaded = ClientProgramSignerTrustStore.load(destination);
        store = loaded.store();
        diagnostic = String.join("; ", loaded.diagnostics());
        available = loaded.diagnostics().isEmpty();
    }

    public synchronized List<ClientProgramSignerTrustGrant> snapshot() { return store.snapshot(); }
    public synchronized String diagnostic() { return diagnostic; }
    public synchronized void clearTransient() { cache.clear(); }

    /** Explicit review only: these identities have a valid signature on the current, locally compiled source. */
    public synchronized List<String> currentSigners(ClientProgramIdentity identity) {
        var observed = currentProgram.apply(identity);
        if (observed.isEmpty()) return List.of();
        var program = observed.orElseThrow();
        var expected = ProgramSignatureDescriptor.fromSource(program.source(), identity.runtimeRevision(),
                identity.requestedCapabilities());
        if (!identity.sourceSha256().equals(expected.sourceSha256())) return List.of();
        return program.attestations().stream().filter(attestation -> attestation.verifies(expected))
                .map(ProgramAttestation::fingerprint).distinct().sorted().toList();
    }

    /** Revalidates after the confirmation delay; a stale review never grants a different program authority. */
    public synchronized void trustCurrent(ClientProgramIdentity identity, String fingerprint, Long expiresAt) throws IOException {
        if (expiresAt != null && expiresAt <= clock.getAsLong()) throw new IllegalArgumentException("Trust expiry has passed");
        if (!currentSigners(identity).contains(fingerprint)) throw new IllegalStateException("The reviewed signature is no longer current");
        trust(new ClientProgramSignerTrustGrant(fingerprint, identity.runtimeRevision(), identity.world(),
                identity.dimension(), identity.managerPosition(), identity.bindingSha256(),
                identity.requestedCapabilities(), expiresAt));
    }

    /** Call only after an explicit user review of the signer, resolved capabilities and exact local scope. */
    public synchronized void trust(ClientProgramSignerTrustGrant grant) throws IOException {
        store.put(grant);
        persist();
    }

    public synchronized void untrust(ClientProgramSignerTrustGrant grant) throws IOException {
        store.remove(grant);
        persist();
    }

    @Override public synchronized void revokeAll() throws IOException {
        store.clear();
        persist();
    }

    @Override public synchronized void revokeAtLocation(ClientProgramIdentity identity) throws IOException {
        store.revokeAtLocation(identity.world(), identity.dimension(), identity.managerPosition());
        persist();
    }

    @Override public synchronized boolean permits(ClientProgramIdentity identity, ResourceLocation capability) {
        if (!available) throw new IllegalStateException("Signer trust storage unavailable");
        if (!identity.requestedCapabilities().contains(capability)) return false;
        long now = clock.getAsLong();
        if (now < 0) return false;
        // The resolver must recheck the live manager even when the signature result is cached.
        var observed = currentProgram.apply(identity);
        if (observed.isEmpty()) { cache.remove(identity); return false; }
        ObservedProgram program = observed.orElseThrow();
        Cached known = cache.get(identity);
        if (known != null && known.program().equals(program)) {
            return known.allowed() && now < known.expiresAt();
        }
        boolean allowed = false;
        long expiresAt = 0;
        for (ClientProgramSignerTrustGrant grant : store.snapshot()) {
            if (grant.matches(identity, program.source(), capability, program.attestations(), now)) {
                allowed = true;
                expiresAt = Math.max(expiresAt, grant.expiresAt() == null ? Long.MAX_VALUE : grant.expiresAt());
            }
        }
        // A matching rule covers the complete resolved manifest, not just the queried capability.
        if (cache.size() >= ClientProgramConsentStore.MAX_PROGRAMS && !cache.containsKey(identity)) {
            cache.remove(cache.keySet().iterator().next());
        }
        cache.put(identity, new Cached(program, allowed, expiresAt));
        return allowed && now < expiresAt;
    }

    private void persist() throws IOException {
        cache.clear();
        try {
            store.save(destination);
            available = true;
            diagnostic = "";
        } catch (IOException | RuntimeException failure) {
            available = false;
            diagnostic = "Signer rules were not saved; signer authority is disabled locally. Restart may restore older rules";
            throw new IOException(diagnostic, failure);
        }
    }
}
