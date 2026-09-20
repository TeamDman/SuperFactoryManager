package ca.teamdman.sfml.test;

import ca.teamdman.sfm.common.label.LabelPositionHolder;
import ca.teamdman.sfm.common.program.linting.LegacyIntervalOffsetProgramLinter;
import ca.teamdman.sfm.common.program.linting.ProblemTracker;
import ca.teamdman.sfml.ast.Interval;
import ca.teamdman.sfml.ast.Program;
import ca.teamdman.sfml.ast.TimerTrigger;
import ca.teamdman.sfml.program_builder.ProgramBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IntervalGrammarCompatibilityTests {
    @Test
    void wordedSyntaxUsesIndependentPeriodAndOffsetUnits() {
        Interval local = interval("EVERY 3 SECONDS OFFSET BY 1 TICK DO END");
        assertEquals(60, local.ticks());
        assertEquals(1, local.offset());
        assertEquals(Interval.IntervalAlignment.LOCAL, local.alignment());
        assertEquals("60 TICKS OFFSET BY 1 TICK", local.toString());
        assertFalse(local.legacyOffsetSyntax());

        Interval global = interval("EVERY 40 GLOBAL TICKS OFFSET BY 20 TICKS DO END");
        assertEquals(40, global.ticks());
        assertEquals(20, global.offset());
        assertEquals(Interval.IntervalAlignment.GLOBAL, global.alignment());
        assertEquals("40 GLOBAL TICKS OFFSET BY 20 TICKS", global.toString());
        assertEquals(global, interval("EVERY " + global + " DO END"));
    }

    @Test
    void oldGlobalAndPlusFormsStillParseAndCanonicallyPrint() {
        Interval suffixed = interval("EVERY 20G+1 TICKS DO END");
        assertEquals(Interval.IntervalAlignment.GLOBAL, suffixed.alignment());
        assertEquals(1, suffixed.offset());
        assertTrue(suffixed.legacyOffsetSyntax());
        assertEquals("20 GLOBAL TICKS OFFSET BY 1 TICK", suffixed.toString());

        Interval plusSeconds = interval("EVERY 20 PLUS 1 SECONDS DO END");
        assertEquals(400, plusSeconds.ticks());
        assertEquals(20, plusSeconds.offset());
        assertTrue(plusSeconds.legacyOffsetSyntax());
        assertEquals("400 TICKS OFFSET BY 20 TICKS", plusSeconds.toString());
        assertEquals(Interval.IntervalAlignment.GLOBAL, interval("EVERY 20 G TICKS DO END").alignment());
    }

    @Test
    void legacyOffsetWarnsAndMixedOrFloatingControlSyntaxFails() {
        Program legacy = new ProgramBuilder("EVERY 20+1 TICKS DO END").build().program();
        ProblemTracker tracker = new ProblemTracker();
        new LegacyIntervalOffsetProgramLinter().gatherWarnings(
                legacy, LabelPositionHolder.empty(), null, tracker
        );
        assertEquals(1, tracker.size());
        assertFalse(new ProgramBuilder("EVERY 20+1 TICKS OFFSET BY 2 TICKS DO END")
                .build().isBuildSuccessful());
        assertFalse(new ProgramBuilder("EVERY 20.5 TICKS DO END")
                .build().isBuildSuccessful());
    }

    private static Interval interval(String source) {
        Program program = new ProgramBuilder(source).build().program();
        assertTrue(program != null, "Expected a valid interval: " + source);
        return ((TimerTrigger) program.triggers().get(0)).interval();
    }
}
