package ca.teamdman.sfm.common.program;

import ca.teamdman.sfm.common.value.SFMValue;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProgramRelationTests {
    @Test
    void equalValuesRemainSeparateOccurrencesAndMappingPreservesTheirIdentity() {
        SFMValue equal = SFMValue.of("same");
        ProgramOccurrenceId firstId = ProgramOccurrenceId.create();
        ProgramOccurrenceId secondId = ProgramOccurrenceId.create();
        ProgramRelation relation = new ProgramRelation(List.of(
                new ProgramRelationRow(firstId, ProgramValueReference.resolved(equal)),
                new ProgramRelationRow(secondId, ProgramValueReference.resolved(equal))
        ));

        ProgramRelation mapped = relation.map(row -> ProgramValueReference.lazy(
                () -> SFMValue.object(java.util.Map.of(
                        "source",
                        ((ProgramValueReference) row.value()).get()
                ))
        ));

        assertEquals(2, mapped.rows().size());
        assertSame(firstId, mapped.rows().get(0).occurrenceId());
        assertSame(secondId, mapped.rows().get(1).occurrenceId());
        assertFalse(firstId == secondId);
        assertEquals(
                ((ProgramValueReference) mapped.rows().get(0).value()).get(),
                ((ProgramValueReference) mapped.rows().get(1).value()).get()
        );
    }

    @Test
    void lazyValueMemoizesSuccessAndFailure() {
        AtomicInteger successfulCalls = new AtomicInteger();
        ProgramValueReference successful = ProgramValueReference.lazy(() -> {
            successfulCalls.incrementAndGet();
            return SFMValue.of("created");
        });
        assertFalse(successful.isResolved());
        assertSame(successful.get(), successful.get());
        assertEquals(1, successfulCalls.get());
        assertTrue(successful.isResolved());

        AtomicInteger failingCalls = new AtomicInteger();
        IllegalStateException failure = new IllegalStateException("failed once");
        ProgramValueReference failing = ProgramValueReference.lazy(() -> {
            failingCalls.incrementAndGet();
            throw failure;
        });
        assertSame(failure, assertThrows(IllegalStateException.class, failing::get));
        assertSame(failure, assertThrows(IllegalStateException.class, failing::get));
        assertEquals(1, failingCalls.get());
    }

    @Test
    void variableEnvironmentStoresRelationsCaseInsensitivelyAndRetainsScalarCompatibility() {
        ProgramVariableEnvironment environment = new ProgramVariableEnvironment();
        ProgramRelation relation = ProgramRelation.singleton(ProgramValueReference.resolved(SFMValue.of(42L)));

        environment.setRelation("Answer", relation);

        assertSame(relation, environment.getRelation("answer").orElseThrow());
        assertEquals(SFMValue.of(42L), environment.get("ANSWER").orElseThrow());
        assertEquals(java.util.Map.of("answer", SFMValue.of(42L)), environment.snapshot());
        environment.free();
        assertThrows(IllegalStateException.class, () -> environment.getRelation("answer"));
    }
}
