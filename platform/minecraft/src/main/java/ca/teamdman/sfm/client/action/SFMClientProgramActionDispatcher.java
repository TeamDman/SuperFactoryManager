package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.client.program.ClientProgramConsentGate;
import ca.teamdman.sfm.client.program.ClientProgramIdentity;
import ca.teamdman.sfm.common.value.SFMValue;
import ca.teamdman.sfm.common.value.SFMValueSchema;
import net.minecraft.resources.ResourceLocation;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BooleanSupplier;

/** Typed program adapter. It never constructs a command string or borrows human authority. */
public final class SFMClientProgramActionDispatcher {
    public record Binding(SFMClientActionDescriptor descriptor, SFMClientActionProgrammaticHandler handler) {
        public Binding { Objects.requireNonNull(descriptor); Objects.requireNonNull(handler); }
    }

    @FunctionalInterface
    public interface Lookup { Optional<Binding> find(ResourceLocation id); }

    private final Lookup lookup;
    private final SFMClientActionAuthorizationService authorization;

    public SFMClientProgramActionDispatcher(Lookup lookup, SFMClientActionAuthorizationService authorization) {
        this.lookup = Objects.requireNonNull(lookup);
        this.authorization = Objects.requireNonNull(authorization);
    }

    public SFMValue invoke(ResourceLocation id, SFMValue input, ClientProgramIdentity caller,
                           ClientProgramConsentGate gate, ClientProgramConsentGate.Policy policy,
                           SFMClientActionAuthorizationService.ScopePolicy scopes, BooleanSupplier effectsAvailable) {
        Optional<Binding> resolved = lookup.find(id).filter(binding -> binding.descriptor().actionId().equals(id));
        if (resolved.isEmpty()) return result("action_undescribed", SFMValue.nullValue());
        if (!authorization.matchesDescriptor(id, resolved.orElseThrow().descriptor())) {
            return result("action_contract_changed", SFMValue.nullValue());
        }
        var admitted = authorization.authorizeProgram(id, input, caller, gate, policy, scopes, effectsAvailable);
        if (!admitted.allowed()) return result(admitted.status().name().toLowerCase(Locale.ROOT), SFMValue.nullValue());
        Binding binding = resolved.orElseThrow();
        try {
            SFMValue value = binding.handler().invoke(input, new SFMClientActionProgrammaticContext(
                    SFMClientActionAuthorizationService.PrincipalKind.CLIENT_PROGRAM, Optional.of(caller)));
            if (value == null || binding.descriptor().checkResult(value).isPresent()) {
                return result("invalid_result", SFMValue.nullValue());
            }
            SFMValue envelope = result("ok", value);
            SFMValueSchema.canonicalActionJson(envelope);
            return envelope;
        } catch (RuntimeException failure) {
            // Diagnostics carry a stable outcome, never an exception that might expose input data.
            return result("action_failed", SFMValue.nullValue());
        }
    }

    public static SFMValueSchema resultSchema(SFMClientActionDescriptor descriptor) {
        return SFMValueSchema.object(Map.of(
                "status", SFMValueSchema.Field.required(SFMValueSchema.string(1, 64)),
                "result", SFMValueSchema.Field.required(SFMValueSchema.optional(descriptor.resultSchema()))
        ), false);
    }

    private static SFMValue result(String status, SFMValue value) {
        return SFMValue.object(Map.of("status", SFMValue.of(status), "result", value));
    }
}
