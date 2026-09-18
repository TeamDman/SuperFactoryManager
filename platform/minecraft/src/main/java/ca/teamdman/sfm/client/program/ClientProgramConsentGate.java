package ca.teamdman.sfm.client.program;

import ca.teamdman.sfml.ast.ProgramExecutionSide;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    private record GrantKey(ClientProgramIdentity identity, ResourceLocation capability) {}

    private final Map<GrantKey, ConsentState> decisions = new HashMap<>();

    public synchronized ConsentState state(ClientProgramIdentity identity, ResourceLocation capability) {
        return decisions.getOrDefault(key(identity, capability), ConsentState.ABSENT);
    }

    /** Creates pending state once. Repeated or denied requests never produce a fresh prompt. */
    public synchronized RequestResult request(ClientProgramIdentity identity, ResourceLocation capability) {
        GrantKey key = key(identity, capability);
        ConsentState existing = decisions.getOrDefault(key, ConsentState.ABSENT);
        if (existing != ConsentState.ABSENT) {
            return new RequestResult(existing, false);
        }
        decisions.put(key, ConsentState.PENDING);
        return new RequestResult(ConsentState.PENDING, true);
    }

    /** A separate explicit user gesture may revisit a previous denial. */
    public synchronized RequestResult reopenDenied(ClientProgramIdentity identity, ResourceLocation capability) {
        GrantKey key = key(identity, capability);
        if (decisions.get(key) != ConsentState.DENIED) {
            return request(identity, capability);
        }
        decisions.put(key, ConsentState.PENDING);
        return new RequestResult(ConsentState.PENDING, true);
    }

    /** Only a pending request can be decided; ordinary program execution cannot approve itself. */
    public synchronized void decide(ClientProgramIdentity identity, ResourceLocation capability, Decision decision) {
        GrantKey key = key(identity, capability);
        Objects.requireNonNull(decision, "decision");
        if (decisions.get(key) != ConsentState.PENDING) {
            throw new IllegalStateException("Consent is not pending");
        }
        decisions.put(key, decision == Decision.APPROVE ? ConsentState.APPROVED : ConsentState.DENIED);
    }

    public synchronized void revoke(ClientProgramIdentity identity, ResourceLocation capability) {
        decisions.remove(key(identity, capability));
    }

    public synchronized Evaluation evaluate(
            ClientProgramIdentity identity,
            ResourceLocation capability,
            Policy policy
    ) {
        ConsentState consent = state(identity, capability);
        List<String> blockers = List.copyOf(Objects.requireNonNull(policy, "policy").blockers(identity, capability));
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

    private static GrantKey key(ClientProgramIdentity identity, ResourceLocation capability) {
        Objects.requireNonNull(identity, "identity");
        Objects.requireNonNull(capability, "capability");
        if (!identity.requestedCapabilities().contains(capability)) {
            throw new IllegalArgumentException("Capability was not declared by this program: " + capability);
        }
        return new GrantKey(identity, capability);
    }
}
