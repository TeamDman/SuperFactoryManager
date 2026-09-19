package ca.teamdman.sfm.client.program;

import ca.teamdman.sfml.ast.ProgramExecutionSide;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.ArrayList;
import java.util.Objects;

/** A UI-free, default-deny gate. A caller must explicitly present any request to the player. */
public final class ClientProgramConsentGate {
    public static final ResourceLocation EXECUTE = new ResourceLocation("sfm", "client_program/execute");
    public static final ResourceLocation RENDER = new ResourceLocation("sfm", "touch_display/render");

    public enum ConsentState { ABSENT, PENDING, APPROVED, DENIED }
    public enum EffectiveState { ALLOWED, AWAITING_CONSENT, DENIED_BY_USER, BLOCKED_BY_POLICY }
    public enum Decision { APPROVE, DENY }

    public record RequestResult(ConsentState state, boolean created) {}

    public record Evaluation(ConsentState consent, EffectiveState effective, List<String> policyBlockers) {
        public Evaluation {
            policyBlockers = List.copyOf(policyBlockers);
        }

        public boolean allowed() {
            return effective == EffectiveState.ALLOWED;
        }
    }

    @FunctionalInterface
    public interface Policy {
        /** Empty means permitted; each non-empty value is a stable, user-displayable policy reason. */
        List<String> blockers(ClientProgramIdentity program, ResourceLocation capability);
    }

    private final ClientProgramConsentStore store;

    public ClientProgramConsentGate() {
        this(new ClientProgramConsentStore());
    }

    public ClientProgramConsentGate(ClientProgramConsentStore store) {
        this.store = Objects.requireNonNull(store);
    }

    public ClientProgramConsentStore store() {
        return store;
    }

    public synchronized ConsentState state(ClientProgramIdentity identity, ResourceLocation capability) {
        return store.state(identity, capability);
    }

    /** Creates pending state once. Repeated or denied requests never produce a fresh prompt. */
    public synchronized RequestResult request(ClientProgramIdentity identity, ResourceLocation capability) {
        return store.request(identity, capability);
    }

    /** A separate explicit user gesture may revisit a previous denial. */
    public synchronized RequestResult reopenDenied(ClientProgramIdentity identity, ResourceLocation capability) {
        return store.reopenDenied(identity, capability);
    }

    /** Only a pending request can be decided; ordinary program execution cannot approve itself. */
    public synchronized void decide(ClientProgramIdentity identity, ResourceLocation capability, Decision decision) {
        store.decide(identity, capability, decision, null, null);
    }

    public synchronized void revoke(ClientProgramIdentity identity, ResourceLocation capability) {
        store.revoke(identity, capability);
    }

    public synchronized Evaluation evaluate(
            ClientProgramIdentity identity,
            ResourceLocation capability,
            Policy policy
    ) {
        ConsentState consent = state(identity, capability);
        List<String> blockers = new ArrayList<>(Objects.requireNonNull(policy, "policy").blockers(identity, capability));
        if (store.stoppedAll()) blockers.add("client_programs_stopped_by_user");
        EffectiveState effective = !blockers.isEmpty()
                ? EffectiveState.BLOCKED_BY_POLICY
                : switch (consent) {
                    case APPROVED -> EffectiveState.ALLOWED;
                    case DENIED -> EffectiveState.DENIED_BY_USER;
                    case ABSENT, PENDING -> EffectiveState.AWAITING_CONSENT;
                };
        return new Evaluation(consent, effective, blockers);
    }

    public Evaluation execution(ClientProgramIdentity identity, Policy policy) {
        if (identity.hostSide() != ProgramExecutionSide.CLIENT) {
            throw new IllegalArgumentException("Only a Client Manager can pass the client execution gate");
        }
        return evaluate(identity, EXECUTE, policy);
    }

}
