package ca.teamdman.sfm.client.program;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.function.LongSupplier;

/** Local user-decision boundary. Observation and direct gate fixtures are never automatically durable. */
public final class ClientProgramConsentService {
    public enum Status { OK, STORE_FULL, INVALID_EVIDENCE, MISSING_EVIDENCE, INVALID_CAPABILITY, NOT_PENDING, PERSISTENCE_FAILED }
    public record Result(Status status, boolean changed, boolean saved, String message) {
        public boolean successful() { return status == Status.OK; }
    }

    private final Path destination;
    private final ClientProgramConsentGate gate;
    private final Set<ClientProgramIdentity> durable = new HashSet<>();
    private final LongSupplier clock;
    private final ClientProgramSignerAuthority signerAuthority;
    private String diagnostic;

    public ClientProgramConsentService(Path destination, LongSupplier clock) {
        this(destination, clock, ClientProgramSignerAuthority.NONE);
    }

    public ClientProgramConsentService(Path destination, LongSupplier clock, ClientProgramSignerAuthority signerAuthority) {
        this.destination = Objects.requireNonNull(destination);
        this.clock = Objects.requireNonNull(clock);
        this.signerAuthority = Objects.requireNonNull(signerAuthority);
        var loaded = ClientProgramConsentStore.load(destination, clock);
        // A lost exact denial/revocation/stop must not fall through to a still-valid signer file.
        // Explicit Resume clears signer rules before removing this recovery stop.
        if (!loaded.diagnostics().isEmpty()) loaded.store().setStoppedAll(true);
        gate = new ClientProgramConsentGate(loaded.store(), signerAuthority::permits);
        loaded.store().snapshots().forEach(snapshot -> durable.add(snapshot.identity()));
        diagnostic = String.join("; ", loaded.diagnostics());
        if (!loaded.diagnostics().isEmpty()) diagnostic += "; client programs remain stopped until explicit recovery";
    }

    public ClientProgramConsentGate gate() { return gate; }
    public ClientProgramConsentStore store() { return gate.store(); }
    public synchronized String diagnostic() { return diagnostic; }
    public long now() { return clock.getAsLong(); }

    /** Passive discovery is bounded, non-modal and non-persistent. */
    public synchronized Result observe(ClientProgramIdentity identity, String source, String bindings,
                                       Map<String, String> versions) {
        try {
            store().observe(identity, source, bindings, versions);
            return new Result(Status.OK, false, false, "Program evidence observed locally");
        } catch (IllegalStateException full) {
            return error(Status.STORE_FULL, "Consent history is full; forget a remembered program to review new ones");
        } catch (IllegalArgumentException invalid) {
            return error(Status.INVALID_EVIDENCE, "Program source or bindings do not match the consent identity");
        }
    }

    /** A program can request only its declared capability. No modal and no denial reopening. */
    public synchronized Result request(ClientProgramIdentity identity, ResourceLocation capability) {
        Result invalid = validate(identity, capability);
        if (invalid != null) return invalid;
        // Do not turn an already-authorized signer's harmless status/request call into an exact pending override.
        if (gate.evaluate(identity, capability, (id, cap) -> List.of()).allowed()) {
            return new Result(Status.OK, false, durable.contains(identity), "Capability is already authorized");
        }
        var requested = gate.request(identity, capability);
        if (!requested.created()) return new Result(Status.OK, false, durable.contains(identity), requested.state().name());
        durable.add(identity);
        return save(true, "Consent request saved");
    }

    /** Explicit panel review may reopen a denial; programs cannot call this operation. */
    public synchronized Result reopen(ClientProgramIdentity identity, ResourceLocation capability) {
        Result invalid = validate(identity, capability);
        if (invalid != null) return invalid;
        var requested = gate.reopenDenied(identity, capability);
        durable.add(identity);
        return requested.created() ? save(true, "Consent review reopened")
                : new Result(Status.OK, false, true, requested.state().name());
    }

    /** Call only after the user reviewed this exact immutable identity and capability. */
    public synchronized Result decide(ClientProgramIdentity identity, ResourceLocation capability,
                                      ClientProgramConsentGate.Decision decision,
                                      @Nullable Long expiresAt, @Nullable Long retryAfter) {
        Result invalid = validate(identity, capability);
        if (invalid != null) return invalid;
        try {
            store().decide(identity, capability, decision, expiresAt, retryAfter);
            durable.add(identity);
            return save(true, decision == ClientProgramConsentGate.Decision.APPROVE ? "Approval saved" : "Denial saved");
        } catch (IllegalStateException notPending) {
            return error(Status.NOT_PENDING, "Request this capability before deciding it");
        } catch (IllegalArgumentException invalidLifetime) {
            return error(Status.INVALID_EVIDENCE, "Consent lifetime must be in the future");
        }
    }

    public synchronized Result revoke(ClientProgramIdentity identity, ResourceLocation capability) {
        Result invalid = validate(identity, capability);
        if (invalid != null) return invalid;
        gate.revoke(identity, capability);
        durable.add(identity);
        return save(true, "Approval revoked");
    }

    public synchronized Result forget(ClientProgramIdentity identity) {
        try { signerAuthority.revokeAtLocation(identity); }
        catch (IOException | RuntimeException failure) { return signerFailure(); }
        boolean changed = store().forget(identity);
        durable.remove(identity);
        return save(changed, "Program forgotten");
    }

    public synchronized Result stopAll() {
        store().setStoppedAll(true);
        Result stopped = save(true, "All client programs stopped; approvals revoked");
        if (!stopped.successful()) return stopped;
        try { signerAuthority.revokeAll(); }
        catch (IOException | RuntimeException failure) { return signerFailure(); }
        return stopped;
    }

    /** Explicit resume removes only the policy stop. It does not approve any capability. */
    public synchronized Result resume() {
        // Clear any old signer rules before removing a durable stop, including after a failed previous clear/restart.
        if (store().stoppedAll()) {
            try { signerAuthority.revokeAll(); }
            catch (IOException | RuntimeException failure) { return signerFailure(); }
        }
        store().setStoppedAll(false);
        return save(true, "Client program review resumed; no approvals restored");
    }

    public synchronized Result retrySave() { return save(false, "Consent decisions saved"); }

    private Result signerFailure() {
        store().setStoppedAll(true);
        Result stopped = save(true, "Client programs stopped");
        return error(Status.PERSISTENCE_FAILED, stopped.successful()
                ? "Signer revocation was not saved. Client programs remain durably stopped; retry before resuming"
                : "Signer revocation and stop were not saved. Restart may restore previous authority; retry before restarting");
    }

    private @Nullable Result validate(ClientProgramIdentity identity, ResourceLocation capability) {
        if (!identity.requestedCapabilities().contains(capability)) {
            return error(Status.INVALID_CAPABILITY, "This capability is not declared by the program");
        }
        if (store().snapshots().stream().noneMatch(s -> s.identity().equals(identity) && s.evidence().isPresent())) {
            return error(Status.MISSING_EVIDENCE, "No locally observed source and bindings are available for this program");
        }
        return null;
    }

    private Result save(boolean changed, String message) {
        try {
            store().save(destination, Set.copyOf(durable));
            diagnostic = "";
            return new Result(Status.OK, changed, true, message);
        } catch (IOException | RuntimeException failed) {
            // Fail closed in this process. An unwritable file cannot durably revoke old grants;
            // say so explicitly instead of claiming success or loading an older backup.
            store().setStoppedAll(true);
            return error(Status.PERSISTENCE_FAILED,
                    "Not saved. Client programs stopped locally. Restart may restore previous decisions; retry saving before restarting");
        }
    }

    private Result error(Status status, String message) {
        diagnostic = message;
        return new Result(status, false, false, message);
    }
}
