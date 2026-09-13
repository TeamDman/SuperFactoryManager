package ca.teamdman.sfm.common.program;

import ca.teamdman.sfml.ast.Label;
import ca.teamdman.sfml.ast.InputStatement;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

/**
 * One active view of resources that can participate in normal SFM output.
 *
 * <p>The source owns any limited-slot cache it creates. Forgetting returns the
 * retained view, or {@code null} when the source is no longer active. This keeps
 * source-specific forgetting rules out of the execution context and leaves room
 * for generated sources that are not backed by a labelled world input statement.</p>
 */
public interface ProgramInputSource {
    void gatherSlots(
            ProgramContext context,
            Consumer<LimitedInputSlot<?, ?, ?>> slotConsumer
    );

    @Nullable ProgramInputSource forget(
            ProgramContext context,
            Set<Label> labels
    );

    default Optional<InputStatement> inputStatement() {
        return Optional.empty();
    }

    void free();
}
