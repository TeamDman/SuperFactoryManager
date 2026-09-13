package ca.teamdman.sfm.common.program;

import ca.teamdman.sfm.common.label.LabelPositionHolder;
import ca.teamdman.sfm.common.value.SFMValue;
import ca.teamdman.sfml.ast.InputStatement;
import ca.teamdman.sfml.ast.Label;
import ca.teamdman.sfml.ast.LabelAccess;
import ca.teamdman.sfml.ast.NumberRangeSet;
import ca.teamdman.sfml.ast.ResourceIdSet;
import ca.teamdman.sfml.ast.ResourceLimits;
import ca.teamdman.sfml.ast.RoundRobin;
import ca.teamdman.sfml.ast.SideQualifier;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProgramExecutionScopeTests {
    @Test
    void forksHaveIsolatedExecutionState() {
        ProgramContext parent = simulationContext();
        RecordingInputSource parentInput = new RecordingInputSource();
        CountingResource parentResource = new CountingResource();
        parent.addInput(parentInput);
        parent.getVariableEnvironment().set("prompt", SFMValue.of("parent"));
        parent.getEphemeralResourceOwner().own(parentResource);

        ProgramContext child = parent.fork();

        assertNotSame(parent.getExecutionScope(), child.getExecutionScope());
        assertNotSame(parent.getVariableEnvironment(), child.getVariableEnvironment());
        assertNotSame(parent.getEphemeralResourceOwner(), child.getEphemeralResourceOwner());
        assertTrue(child.getInputs().isEmpty());
        assertTrue(child.getVariableEnvironment().snapshot().isEmpty());
        assertEquals(0, child.getEphemeralResourceOwner().size());

        RecordingInputSource childInput = new RecordingInputSource();
        CountingResource childResource = new CountingResource();
        child.addInput(childInput);
        child.getVariableEnvironment().set("prompt", SFMValue.of("child"));
        child.getEphemeralResourceOwner().own(childResource);
        child.free();

        assertEquals(1, childInput.freeCalls);
        assertEquals(1, childResource.freeCalls);
        assertEquals(0, parentInput.freeCalls);
        assertEquals(0, parentResource.freeCalls);
        assertEquals(SFMValue.of("parent"), parent.getVariableEnvironment().get("prompt").orElseThrow());

        parent.free();
        parent.free();

        assertEquals(1, parentInput.freeCalls);
        assertEquals(1, parentResource.freeCalls);
        assertTrue(parent.getVariableEnvironment().isFreed());
    }

    @Test
    void ephemeralResourcesAreOwnedByIdentityAndFreedExactlyOnce() {
        ProgramEphemeralResourceOwner owner = new ProgramEphemeralResourceOwner();
        EqualCountingResource first = owner.own(new EqualCountingResource());
        EqualCountingResource second = owner.own(new EqualCountingResource());

        assertEquals(first, second);
        assertEquals(2, owner.size());

        owner.free();
        owner.free();

        assertEquals(1, first.freeCalls);
        assertEquals(1, second.freeCalls);
        assertTrue(owner.isFreed());
        assertThrows(IllegalStateException.class, () -> owner.own(new CountingResource()));
    }

    @Test
    void releasedResourcesAreNotFreedAgainAtScopeTeardown() {
        ProgramEphemeralResourceOwner owner = new ProgramEphemeralResourceOwner();
        CountingResource resource = owner.own(new CountingResource());

        assertTrue(owner.release(resource));
        assertFalse(owner.release(resource));
        owner.free();

        assertEquals(1, resource.freeCalls);
    }

    @Test
    void variableSnapshotsAreImmutableCopiesAndEnvironmentEndsWithScope() {
        ProgramVariableEnvironment variables = new ProgramVariableEnvironment();
        variables.set("value", SFMValue.of(1L));
        Map<String, SFMValue> snapshot = variables.snapshot();

        variables.set("value", SFMValue.of(2L));

        assertEquals(SFMValue.of(1L), snapshot.get("value"));
        assertEquals(SFMValue.of(2L), variables.get("value").orElseThrow());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.put("extra", SFMValue.nullValue()));

        variables.free();

        assertTrue(variables.isFreed());
        assertThrows(IllegalStateException.class, () -> variables.get("value"));
        assertThrows(IllegalStateException.class, variables::snapshot);
        assertThrows(IllegalStateException.class, () -> variables.set("value", SFMValue.nullValue()));
    }

    @Test
    void inputStatementCreatesFreshWorldViewForEveryExecution() {
        InputStatement statement = new InputStatement(
                new LabelAccess(
                        List.of(new Label("source")),
                        SideQualifier.NULL,
                        NumberRangeSet.MAX_RANGE,
                        RoundRobin.disabled()
                ),
                new ResourceLimits(List.of(), ResourceIdSet.EMPTY),
                false
        );
        ProgramContext firstContext = inputRegistrationContext();
        ProgramContext secondContext = inputRegistrationContext();

        statement.tick(firstContext);
        statement.tick(secondContext);

        ProgramInputSource firstSource = firstContext.getInputs().get(0);
        ProgramInputSource secondSource = secondContext.getInputs().get(0);
        assertTrue(firstSource instanceof WorldProgramInputSource);
        assertTrue(secondSource instanceof WorldProgramInputSource);
        assertNotSame(firstSource, secondSource);
        assertSame(statement, firstSource.inputStatement().orElseThrow());
        assertSame(statement, secondSource.inputStatement().orElseThrow());
        assertFalse(ProgramInputSource.class.isAssignableFrom(InputStatement.class));
        assertTrue(Arrays.stream(InputStatement.class.getDeclaredFields())
                           .filter(field -> !Modifier.isStatic(field.getModifiers()))
                           .allMatch(field -> Modifier.isFinal(field.getModifiers())));
        assertThrows(UnsupportedOperationException.class, () -> firstContext.getInputs().add(secondSource));

        firstContext.free();
        secondContext.free();
    }

    private static ProgramContext simulationContext() {
        return ProgramContext.createSimulationContext(
                null,
                LabelPositionHolder.empty(),
                0,
                new SimulateExploreAllPathsProgramBehaviour()
        );
    }

    private static ProgramContext inputRegistrationContext() {
        return ProgramContext.createSimulationContext(
                null,
                LabelPositionHolder.empty(),
                0,
                new SimulateExploreAllPathsProgramBehaviour() {
                    @Override
                    public void onInputStatementExecution(
                            ProgramContext context,
                            InputStatement inputStatement
                    ) {
                    }
                }
        );
    }

    private static class CountingResource implements ProgramEphemeralResource {
        int freeCalls;

        @Override
        public void free() {
            freeCalls++;
        }
    }

    private static final class EqualCountingResource extends CountingResource {
        @Override
        public boolean equals(Object obj) {
            return obj instanceof EqualCountingResource;
        }

        @Override
        public int hashCode() {
            return 1;
        }
    }

    private static final class RecordingInputSource implements ProgramInputSource {
        private int freeCalls;

        @Override
        public void gatherSlots(
                ProgramContext context,
                Consumer<LimitedInputSlot<?, ?, ?>> slotConsumer
        ) {
        }

        @Override
        public @Nullable ProgramInputSource forget(
                ProgramContext context,
                ProgramInputForgetRequest request
        ) {
            return this;
        }

        @Override
        public void free() {
            freeCalls++;
        }
    }
}
