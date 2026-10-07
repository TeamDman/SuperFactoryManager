package ca.teamdman.sfm.client.action;

{% if features.client_program_actions %}
import ca.teamdman.sfm.client.program.ClientProgramConsentGate;
{% endif %}
{% if features.client_program_actions %}
import ca.teamdman.sfm.client.program.ClientProgramIdentity;
{% endif %}
{% if features.client_program_actions %}
{% if features.client_frame_language %}
import ca.teamdman.sfm.client.program.ClientManagerFrameRuntime;
{% endif %}
{% endif %}
import ca.teamdman.sfm.common.net.SFMBoundedEffectBudget;
import ca.teamdman.sfm.common.value.SFMValue;
import ca.teamdman.sfm.common.value.SFMValueSchema;
import ca.teamdman.sfm.client.registry.SFMClientActions;
{% if features.client_program_actions %}
import ca.teamdman.sfml.ast.ProgramExecutionSide;
{% endif %}
import net.minecraft.resources.ResourceLocation;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.stream.Collectors;

/**
 * The client-side effect admission boundary shared by human and program
 * adapters. It does not replace the server's independent private-world gate,
 * exact-target checks, or per-player network budget.
 */
public final class SFMClientActionAuthorizationService {
    public static final int MAX_ACTIONS_PER_SECOND = 32;
    public static final int MAX_ACTION_BYTES_PER_SECOND = 64 * 1024;
    public static final int MAX_TRACES = 128;
    private static final UUID LOCAL_CONNECTION_BUDGET_KEY = new UUID(0L, 0L);
    private static final SFMClientActionAuthorizationService SHARED = new SFMClientActionAuthorizationService(
{% if features.client_program_actions %}
            SFMClientActions::programmaticDescriptor,
{% else %}
            ignored -> Optional.empty(),
{% endif %}
            new SFMBoundedEffectBudget(MAX_ACTIONS_PER_SECOND, MAX_ACTION_BYTES_PER_SECOND),
{% if features.client_program_actions %}
            () -> System.nanoTime() / 1_000_000_000L,
{% if features.client_frame_language %}
            ClientManagerFrameRuntime::liveIdentityFor
{% else %}
            expected -> Optional.empty()
{% endif %}
{% else %}
            () -> System.nanoTime() / 1_000_000_000L
{% endif %}
    );

    public enum PrincipalKind { HUMAN, CLIENT_PROGRAM }

    public enum Status {
        ALLOWED,
        ACTION_UNDESCRIBED,
        WRONG_EXECUTION_SIDE,
        STALE_PROGRAM_CONTEXT,
        INVALID_INPUT,
        CAPABILITY_UNDECLARED,
        TARGET_UNAUTHORIZED,
        AWAITING_CONSENT,
        DENIED_BY_USER,
        BLOCKED_BY_POLICY,
        EFFECTS_DISABLED,
        RATE_LIMITED
    }

    /** Only bounded IDs/digests; no source text, action input, or packet payload. */
    public record ProgramWitness(String sourceSha256, String bindingSha256, String endpointSha256,
                                 UUID worldId, ResourceLocation dimension,
                                 int managerX, int managerY, int managerZ, String runtimeSha256,
                                 String capabilityManifestSha256) {}

    public record ScopeWitness(ResourceLocation permission, String subjectSha256) {}

    public record Trace(long sequence, ResourceLocation actionId, PrincipalKind principal,
{% if features.client_program_actions %}
                        Optional<ProgramWitness> program, Map<ResourceLocation, ClientProgramConsentGate.ConsentState> consent,
{% else %}
                        Optional<ProgramWitness> program, Map<ResourceLocation, ?> consent,
{% endif %}
                        List<ScopeWitness> scopes, int chargedBytes, Status status) {
        public Trace {
            program = Objects.requireNonNull(program);
            consent = Map.copyOf(consent);
            scopes = List.copyOf(scopes);
        }
    }

    public record Authorization(Status status, Optional<SFMValueSchema.Failure> failure,
                                List<SFMClientActionDescriptor.DataScope> scopes, Trace trace) {
        public Authorization {
            failure = Objects.requireNonNull(failure);
            scopes = List.copyOf(scopes);
            Objects.requireNonNull(trace);
        }

        public boolean allowed() { return status == Status.ALLOWED; }
    }

    public record EffectAttempt(Authorization authorization, boolean transportCalled,
                                boolean localTransportAccepted) {}

    @FunctionalInterface
    public interface Effect {
        /** Called only after validation, consent, exact scope, gate and budget. */
        boolean attempt(SFMValue validatedInput);
    }

    @FunctionalInterface
    public interface DescriptorLookup extends Function<ResourceLocation, Optional<SFMClientActionDescriptor>> {}

{% if features.client_program_actions %}
    @FunctionalInterface
    public interface ScopePolicy {
        /** Explicit target policy; consent for an action ID is not consent for every address. */
        boolean permits(ClientProgramIdentity program, SFMClientActionDescriptor.DataScope scope);
    }

    @FunctionalInterface
    public interface LiveProgramLookup {
        /** Resolve the current loaded manager on the client thread, never the saved grant alone. */
        Optional<ClientProgramIdentity> currentIdentity(ClientProgramIdentity expected);
    }

{% endif %}
    private final DescriptorLookup descriptors;
    private final SFMBoundedEffectBudget budget;
    // Cached reads must not starve the shared desktop/server-effect allowance.
    private final SFMBoundedEffectBudget readBudget = new SFMBoundedEffectBudget(2048, 1024 * 1024);
    private final LongSupplier secondWindow;
{% if features.client_program_actions %}
    private final LiveProgramLookup livePrograms;
{% endif %}
    private final ArrayDeque<Trace> traces = new ArrayDeque<>();
    private long nextTraceSequence;

    public SFMClientActionAuthorizationService(DescriptorLookup descriptors,
                                               SFMBoundedEffectBudget budget, LongSupplier secondWindow) {
{% if features.client_program_actions %}
{% if features.client_frame_language %}
        this(descriptors, budget, secondWindow, ClientManagerFrameRuntime::liveIdentityFor);
{% else %}
        this(descriptors, budget, secondWindow, expected -> Optional.empty());
{% endif %}
    }

    public SFMClientActionAuthorizationService(DescriptorLookup descriptors,
                                               SFMBoundedEffectBudget budget, LongSupplier secondWindow,
                                               LiveProgramLookup livePrograms) {
{% endif %}
        this.descriptors = Objects.requireNonNull(descriptors);
        this.budget = Objects.requireNonNull(budget);
        this.secondWindow = Objects.requireNonNull(secondWindow);
{% if features.client_program_actions %}
        this.livePrograms = Objects.requireNonNull(livePrograms);
{% endif %}
    }

    public static SFMClientActionAuthorizationService shared() { return SHARED; }

    boolean matchesDescriptor(ResourceLocation id, SFMClientActionDescriptor expected) {
        return descriptors.apply(id).filter(expected::equals).isPresent();
    }

    /** Only human action adapters in this package may choose this principal. */
    Authorization authorizeHuman(SFMClientActionDescriptor descriptor, SFMValue input,
                                 BooleanSupplier effectsAvailable) {
        Objects.requireNonNull(descriptor);
        return authorize(descriptor.actionId(), descriptor, input, PrincipalKind.HUMAN,
{% if features.client_program_actions %}
                Optional.empty(), null, null, null, effectsAvailable);
{% else %}
                effectsAvailable);
{% endif %}
    }

    EffectAttempt performHuman(SFMClientActionDescriptor descriptor, SFMValue input,
                               BooleanSupplier effectsAvailable, Effect effect) {
        Objects.requireNonNull(effect);
        Authorization authorization = authorizeHuman(descriptor, input, effectsAvailable);
        if (!authorization.allowed()) return new EffectAttempt(authorization, false, false);
        return new EffectAttempt(authorization, true, effect.attempt(input));
    }

{% if features.client_program_actions %}
    /** Program callers never fall back to human authority. IDs are registry-resolved. */
    public Authorization authorizeProgram(ResourceLocation actionId, SFMValue input,
                                          ClientProgramIdentity identity, ClientProgramConsentGate consent,
                                          ClientProgramConsentGate.Policy policy, ScopePolicy scopePolicy,
                                          BooleanSupplier effectsAvailable) {
        Objects.requireNonNull(actionId);
        Objects.requireNonNull(identity);
        Objects.requireNonNull(consent);
        Objects.requireNonNull(policy);
        Objects.requireNonNull(scopePolicy);
        Optional<SFMClientActionDescriptor> descriptor = descriptors.apply(actionId);
        return authorize(actionId, descriptor.orElse(null), input, PrincipalKind.CLIENT_PROGRAM,
                Optional.of(identity), consent, policy, scopePolicy, effectsAvailable);
    }

    public EffectAttempt performProgram(ResourceLocation actionId, SFMValue input,
                                        ClientProgramIdentity identity, ClientProgramConsentGate consent,
                                        ClientProgramConsentGate.Policy policy, ScopePolicy scopePolicy,
                                        BooleanSupplier effectsAvailable, Effect effect) {
        Objects.requireNonNull(effect);
        Authorization authorization = authorizeProgram(actionId, input, identity, consent,
                policy, scopePolicy, effectsAvailable);
        if (!authorization.allowed()) return new EffectAttempt(authorization, false, false);
        return new EffectAttempt(authorization, true, effect.attempt(input));
    }

{% endif %}
    private Authorization authorize(ResourceLocation actionId, SFMClientActionDescriptor descriptor, SFMValue input,
{% if features.client_program_actions %}
                                    PrincipalKind principal, Optional<ClientProgramIdentity> program,
                                    ClientProgramConsentGate consent, ClientProgramConsentGate.Policy policy,
                                    ScopePolicy scopePolicy,
{% else %}
                                    PrincipalKind principal,
{% endif %}
                                    BooleanSupplier effectsAvailable) {
        Objects.requireNonNull(input);
        Objects.requireNonNull(effectsAvailable);
{% if features.client_program_actions %}
        LinkedHashMap<ResourceLocation, ClientProgramConsentGate.ConsentState> decisions = new LinkedHashMap<>();
{% else %}
        LinkedHashMap<ResourceLocation, Object> decisions = new LinkedHashMap<>();
{% endif %}
        List<SFMClientActionDescriptor.DataScope> scopes = List.of();
        Optional<SFMValueSchema.Failure> failure = Optional.empty();
        int bytes = 0;
        Status status;

        if (descriptor == null || !descriptor.actionId().equals(actionId)) {
            status = Status.ACTION_UNDESCRIBED;
{% if features.client_program_actions %}
        } else if (descriptor.executionSide() != SFMClientActionDescriptor.ExecutionSide.CLIENT
                   || program.isPresent() && program.orElseThrow().hostSide() != ProgramExecutionSide.CLIENT) {
{% else %}
        } else if (descriptor.executionSide() != SFMClientActionDescriptor.ExecutionSide.CLIENT) {
{% endif %}
            status = Status.WRONG_EXECUTION_SIDE;
{% if features.client_program_actions %}
        } else if (program.isPresent() && !isCurrent(program.orElseThrow())) {
            status = Status.STALE_PROGRAM_CONTEXT;
        } else if (program.isPresent() && !program.orElseThrow().requestedCapabilities()
                .contains(ClientProgramConsentGate.EXECUTE)) {
            status = Status.CAPABILITY_UNDECLARED;
{% endif %}
        } else {
            SFMClientActionDescriptor.InputCheck inputCheck = descriptor.checkInput(input);
            if (inputCheck instanceof SFMClientActionDescriptor.InputCheck.Rejected rejected) {
                status = Status.INVALID_INPUT;
                failure = Optional.of(rejected.failure());
            } else {
                scopes = ((SFMClientActionDescriptor.InputCheck.Accepted) inputCheck).dataScopes();
{% if features.client_program_actions %}
                status = program.isEmpty() ? Status.ALLOWED : checkProgramPermissions(
                        program.orElseThrow(), descriptor, scopes, consent, policy, scopePolicy, decisions);
{% else %}
                status = Status.ALLOWED;
{% endif %}
                if (status == Status.ALLOWED && !effectsAvailable.getAsBoolean()) {
                    status = Status.EFFECTS_DISABLED;
                }
                // Recheck immediately before admission: a scope/policy adapter may
                // have observed a world change while resolving evaluated arguments.
{% if features.client_program_actions %}
                if (status == Status.ALLOWED && program.isPresent() && !isCurrent(program.orElseThrow())) {
                    status = Status.STALE_PROGRAM_CONTEXT;
                }
{% endif %}
                if (status == Status.ALLOWED) {
                    int cost = SFMValueSchema.boundedEncodedBytes(input);
                    SFMBoundedEffectBudget selectedBudget = descriptor.costClass() == SFMClientActionDescriptor.CostClass.LOCAL_READ
                            ? readBudget : budget;
                    if (selectedBudget.reserve(LOCAL_CONNECTION_BUDGET_KEY, secondWindow.getAsLong(), cost)
                            != SFMBoundedEffectBudget.Result.ALLOWED) {
                        status = Status.RATE_LIMITED;
                    } else {
                        bytes = cost;
                    }
                }
            }
        }

{% if features.client_program_actions %}
        Trace trace = appendTrace(actionId, principal, program, decisions, scopes, bytes, status);
{% else %}
        Trace trace = appendTrace(actionId, principal, decisions, scopes, bytes, status);
{% endif %}
        return new Authorization(status, failure, scopes, trace);
    }

{% if features.client_program_actions %}
    private boolean isCurrent(ClientProgramIdentity expected) {
        return livePrograms.currentIdentity(expected).filter(expected::equals).isPresent();
    }

    private static Status checkProgramPermissions(ClientProgramIdentity identity,
                                                  SFMClientActionDescriptor descriptor,
                                                  List<SFMClientActionDescriptor.DataScope> scopes,
                                                  ClientProgramConsentGate consent,
                                                  ClientProgramConsentGate.Policy policy,
                                                  ScopePolicy scopePolicy,
                                                  Map<ResourceLocation, ClientProgramConsentGate.ConsentState> decisions) {
        LinkedHashMap<ResourceLocation, Boolean> permissions = new LinkedHashMap<>();
        permissions.put(ClientProgramConsentGate.EXECUTE, true);
        permissions.put(descriptor.controlPermission(), true);
        for (SFMClientActionDescriptor.DataScope scope : scopes) permissions.put(scope.permission(), true);
        for (ResourceLocation permission : permissions.keySet()) {
            if (!identity.requestedCapabilities().contains(permission)) return Status.CAPABILITY_UNDECLARED;
            ClientProgramConsentGate.Evaluation evaluation = consent.evaluate(identity, permission, policy);
            decisions.put(permission, evaluation.consent());
            if (!evaluation.policyBlockers().isEmpty()) return Status.BLOCKED_BY_POLICY;
            if (evaluation.effective() == ClientProgramConsentGate.EffectiveState.DENIED_BY_USER) {
                return Status.DENIED_BY_USER;
            }
            if (!evaluation.allowed()) return Status.AWAITING_CONSENT;
        }
        for (SFMClientActionDescriptor.DataScope scope : scopes) {
            if (!scopePolicy.permits(identity, scope)) return Status.TARGET_UNAUTHORIZED;
        }
        return Status.ALLOWED;
    }

{% endif %}
    public synchronized List<Trace> traceSnapshot() {
        return List.copyOf(traces);
    }

    private synchronized Trace appendTrace(ResourceLocation actionId, PrincipalKind principal,
{% if features.client_program_actions %}
                                           Optional<ClientProgramIdentity> program,
                                           Map<ResourceLocation, ClientProgramConsentGate.ConsentState> decisions,
{% else %}
                                           Map<ResourceLocation, ?> decisions,
{% endif %}
                                           List<SFMClientActionDescriptor.DataScope> scopes, int bytes, Status status) {
        List<ScopeWitness> scopeWitnesses = new ArrayList<>(scopes.size());
        for (SFMClientActionDescriptor.DataScope scope : scopes) {
            scopeWitnesses.add(new ScopeWitness(scope.permission(), sha256(
                    SFMValueSchema.canonicalActionJson(scope.subject()))));
        }
{% if features.client_program_actions %}
        Optional<ProgramWitness> witness = program.map(identity -> new ProgramWitness(
                identity.sourceSha256(), identity.bindingSha256(), sha256(identity.world().serverEndpoint()),
                identity.world().worldId(), identity.dimension(),
                identity.managerPosition().getX(), identity.managerPosition().getY(),
                identity.managerPosition().getZ(), sha256(identity.runtimeRevision()),
                sha256(identity.requestedCapabilities().stream().map(ResourceLocation::toString)
                        .sorted().collect(Collectors.joining("\n")))));
{% else %}
        Optional<ProgramWitness> witness = Optional.empty();
{% endif %}
        Trace trace = new Trace(++nextTraceSequence, actionId, principal, witness,
                decisions, scopeWitnesses, bytes, status);
        if (traces.size() == MAX_TRACES) traces.removeFirst();
        traces.addLast(trace);
        return trace;
    }

    private static String sha256(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }
}
