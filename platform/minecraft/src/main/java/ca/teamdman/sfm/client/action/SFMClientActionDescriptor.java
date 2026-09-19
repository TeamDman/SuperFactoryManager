package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.common.value.SFMValue;
import ca.teamdman.sfm.common.value.SFMValueSchema;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Opt-in machine contract for an existing client action. A descriptor is not
 * authorisation: a caller still needs a principal, consent, invocation-time
 * scope check, rate budget and (for server effects) server validation.
 */
public record SFMClientActionDescriptor(
        ResourceLocation actionId,
        SFMValueSchema inputSchema,
        SFMValueSchema resultSchema,
        ExecutionSide executionSide,
        ResourceLocation controlPermission,
        ScopeResolver dataScopeResolver,
        CostClass costClass,
        Acknowledgement acknowledgement,
        Map<String, StatusKind> resultStatuses
) {
    public SFMClientActionDescriptor {
        Objects.requireNonNull(actionId, "actionId");
        Objects.requireNonNull(inputSchema, "inputSchema");
        Objects.requireNonNull(resultSchema, "resultSchema");
        Objects.requireNonNull(executionSide, "executionSide");
        Objects.requireNonNull(controlPermission, "controlPermission");
        Objects.requireNonNull(dataScopeResolver, "dataScopeResolver");
        Objects.requireNonNull(costClass, "costClass");
        Objects.requireNonNull(acknowledgement, "acknowledgement");
        Objects.requireNonNull(resultStatuses, "resultStatuses");
        TreeMap<String, StatusKind> sorted = new TreeMap<>();
        resultStatuses.forEach((status, kind) -> sorted.put(
                Objects.requireNonNull(status, "status"), Objects.requireNonNull(kind, "status kind")
        ));
        if (sorted.isEmpty()) throw new IllegalArgumentException("An action needs explicit result statuses");
        resultStatuses = Map.copyOf(sorted);
    }

    /** A logical action host, not the process distribution or the program host. */
    public enum ExecutionSide { CLIENT, SERVER }

    public enum CostClass { LOCAL_READ, CLIENT_EFFECT, SERVER_EFFECT, RASTER_BULK }

    /** Does not imply downstream completion unless that exact boundary acknowledges it. */
    public enum Acknowledgement {
        LOCAL_RESULT_ONLY,
        LOCAL_TRANSPORT_ATTEMPT_ONLY,
        SERVER_ACCEPTANCE_ONLY,
        FINAL_EFFECT
    }

    public enum StatusKind { ATTEMPTED, REJECTED }

    /** The permission and exact data subject to recheck after argument evaluation. */
    public record DataScope(ResourceLocation permission, SFMValue subject) {
        public DataScope {
            Objects.requireNonNull(permission, "permission");
            Objects.requireNonNull(subject, "subject");
            if (SFMValueSchema.any().validate(subject).isPresent()) {
                throw new IllegalArgumentException("Data scope subject exceeds the SFM value envelope");
            }
        }
    }

    public sealed interface InputCheck permits InputCheck.Accepted, InputCheck.Rejected {
        record Accepted(List<DataScope> dataScopes) implements InputCheck {
            public Accepted {
                dataScopes = List.copyOf(Objects.requireNonNull(dataScopes));
                if (dataScopes.size() > 32) throw new IllegalArgumentException("Too many action data scopes");
            }
        }

        record Rejected(SFMValueSchema.Failure failure) implements InputCheck {
            public Rejected { Objects.requireNonNull(failure); }
        }
    }

    @FunctionalInterface
    public interface ScopeResolver {
        InputCheck resolve(SFMValue input);

        static ScopeResolver none() { return ignored -> new InputCheck.Accepted(List.of()); }
    }

    /** Schema validation always precedes dynamic-scope extraction. */
    public InputCheck checkInput(SFMValue input) {
        Optional<SFMValueSchema.Failure> failure = inputSchema.validate(input);
        if (failure.isPresent()) return new InputCheck.Rejected(failure.orElseThrow());
        try {
            return Objects.requireNonNull(dataScopeResolver.resolve(input), "scope result");
        } catch (RuntimeException invalidResolver) {
            return new InputCheck.Rejected(new SFMValueSchema.Failure("scope_resolution_failed", ""));
        }
    }

    public Optional<SFMValueSchema.Failure> checkResult(SFMValue result) {
        Optional<SFMValueSchema.Failure> failure = resultSchema.validate(result);
        if (failure.isPresent()) return failure;
        if (!(result instanceof SFMValue.ObjectValue object)
                || !(object.fields().get("status") instanceof SFMValue.StringValue status)
                || !resultStatuses.containsKey(status.value())) {
            return Optional.of(new SFMValueSchema.Failure("unknown_status", "/status"));
        }
        return Optional.empty();
    }
}
