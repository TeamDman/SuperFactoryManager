package ca.teamdman.sfm.common.program;

import ca.teamdman.sfml.ast.InputStatement;
import ca.teamdman.sfml.ast.ProgramInputSelection;
import ca.teamdman.sfm.common.resourcetype.ResourceType;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

/** An input view that removes non-matching slots before transfer budgets are applied. */
public final class FilteringProgramInputSource implements ProgramInputSource {
    private final ProgramInputSource delegate;
    private final ProgramInputSelection selection;

    public FilteringProgramInputSource(
            ProgramInputSource delegate,
            ProgramInputSelection selection
    ) {
        this.delegate = Objects.requireNonNull(delegate);
        this.selection = Objects.requireNonNull(selection);
    }

    @Override
    public void gatherSlots(
            ProgramContext context,
            Consumer<LimitedInputSlot<?, ?, ?>> slotConsumer
    ) {
        delegate.gatherSlots(context, slot -> {
            Object stack = copyStack(slot.type, slot.peekStackInSlot());
            if (selection.matches(slot.type, stack)) {
                slotConsumer.accept(slot);
            }
        });
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Object copyStack(ResourceType type, Object stack) {
        return type.copy(stack);
    }

    @Override
    public @Nullable ProgramInputSource forget(
            ProgramContext context,
            ProgramInputForgetRequest request
    ) {
        ProgramInputSource retained = delegate.forget(context, request);
        return retained == null ? null : new FilteringProgramInputSource(retained, selection);
    }

    @Override
    public Optional<InputStatement> inputStatement() {
        return delegate.inputStatement();
    }

    @Override
    public void free() {
        delegate.free();
    }
}
