package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.client.keybinding.SFMKeyBindingConflict;
import ca.teamdman.sfm.client.keybinding.SFMKeyBindingSnapshot;
import ca.teamdman.sfm.client.keybinding.SFMKeyInputEvent;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Thread-scoped provenance for one registered client-action dispatch.
 *
 * <p>The dynamic keybinding service activates this scope on the Minecraft
 * client thread around Brigadier execution. Domain controllers may copy the
 * immutable provenance into their own episode archive without coupling the
 * generic action executor to a particular recorder.</p>
 */
public final class SFMClientActionInvocationTrace {
    private static final ThreadLocal<Provenance> CURRENT = new ThreadLocal<>();

    private SFMClientActionInvocationTrace() {
    }

    public static Optional<DynamicBindingProvenance> currentDynamicBinding() {
        return current().filter(DynamicBindingProvenance.class::isInstance)
                .map(DynamicBindingProvenance.class::cast);
    }

    public static Optional<Provenance> current() {
        return Optional.ofNullable(CURRENT.get());
    }

    public static Scope activate(Provenance provenance) {
        Objects.requireNonNull(provenance, "provenance");
        Provenance previous = CURRENT.get();
        CURRENT.set(provenance);
        return () -> {
            if (previous == null) CURRENT.remove();
            else CURRENT.set(previous);
        };
    }

    public sealed interface Provenance permits DynamicBindingProvenance, RegisteredActionProvenance {
        String commandDraft();
    }

    public record DynamicBindingProvenance(
            List<SFMKeyInputEvent> sourceEvents,
            SFMKeyBindingSnapshot effectiveSnapshot,
            String bindingId,
            String actionId,
            String commandDraft,
            List<String> activeSituationIds,
            boolean consumed,
            List<SFMKeyBindingConflict> conflicts
    ) implements Provenance {
        public DynamicBindingProvenance {
            sourceEvents = List.copyOf(sourceEvents);
            if (sourceEvents.isEmpty()) {
                throw new IllegalArgumentException("Dynamic binding provenance needs source events");
            }
            Objects.requireNonNull(effectiveSnapshot, "effectiveSnapshot");
            bindingId = requireText(bindingId, "bindingId");
            actionId = requireText(actionId, "actionId");
            commandDraft = requireText(commandDraft, "commandDraft");
            activeSituationIds = List.copyOf(activeSituationIds);
            conflicts = List.copyOf(conflicts);
        }
    }

    /** Ordinary palette/prompt/programmatic dispatch through the registered action surface. */
    public record RegisteredActionProvenance(String commandDraft) implements Provenance {
        public RegisteredActionProvenance {
            commandDraft = requireText(commandDraft, "commandDraft");
        }
    }

    @FunctionalInterface
    public interface Scope extends AutoCloseable {
        @Override
        void close();
    }

    private static String requireText(String value, String label) {
        Objects.requireNonNull(value, label);
        if (value.isEmpty()) throw new IllegalArgumentException(label + " must not be empty");
        return value;
    }
}
