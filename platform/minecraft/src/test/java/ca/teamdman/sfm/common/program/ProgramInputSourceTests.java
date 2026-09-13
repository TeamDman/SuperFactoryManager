package ca.teamdman.sfm.common.program;

import ca.teamdman.sfm.common.label.LabelPositionHolder;
import ca.teamdman.sfm.common.value.SFMValue;
import ca.teamdman.sfml.ast.ForgetStatement;
import ca.teamdman.sfml.ast.Label;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class ProgramInputSourceTests {
    @Test
    void contextStoresAndCleansAbstractInputSources() {
        ProgramContext context = simulationContext();
        RecordingInputSource first = new RecordingInputSource(null);
        RecordingInputSource second = new RecordingInputSource(null);

        context.addInput(first);
        context.addInput(second);

        assertEquals(2, context.getInputs().size());
        assertSame(first, context.getInputs().get(0));
        assertSame(second, context.getInputs().get(1));

        context.free();

        assertEquals(1, first.freeCalls);
        assertEquals(1, second.freeCalls);
    }

    @Test
    void forgetDelegatesRetentionToEachInputSource() {
        ProgramContext context = simulationContext();
        RecordingInputSource replacement = new RecordingInputSource(null);
        RecordingInputSource retained = new RecordingInputSource(replacement);
        RecordingInputSource dropped = new RecordingInputSource(null);
        context.addInput(retained);
        context.addInput(dropped);
        context.getVariableEnvironment().set("captured", SFMValue.of("unchanged"));
        Set<Label> forgottenLabels = Set.of(new Label("source"));

        new ForgetStatement(forgottenLabels).tick(context);

        assertEquals(forgottenLabels, retained.forgottenLabels);
        assertEquals(forgottenLabels, dropped.forgottenLabels);
        assertEquals(1, context.getInputs().size());
        assertSame(replacement, context.getInputs().get(0));
        assertEquals(
                SFMValue.of("unchanged"),
                context.getVariableEnvironment().get("captured").orElseThrow()
        );
    }

    private static ProgramContext simulationContext() {
        return ProgramContext.createSimulationContext(
                null,
                LabelPositionHolder.empty(),
                0,
                new SimulateExploreAllPathsProgramBehaviour()
        );
    }

    private static final class RecordingInputSource implements ProgramInputSource {
        private final @Nullable ProgramInputSource retainedAfterForget;
        private int freeCalls;
        private Set<Label> forgottenLabels = Set.of();

        private RecordingInputSource(@Nullable ProgramInputSource retainedAfterForget) {
            this.retainedAfterForget = retainedAfterForget;
        }

        @Override
        public void gatherSlots(
                ProgramContext context,
                Consumer<LimitedInputSlot<?, ?, ?>> slotConsumer
        ) {
        }

        @Override
        public @Nullable ProgramInputSource forget(
                ProgramContext context,
                Set<Label> labels
        ) {
            forgottenLabels = Set.copyOf(labels);
            return retainedAfterForget;
        }

        @Override
        public void free() {
            freeCalls++;
        }
    }
}
