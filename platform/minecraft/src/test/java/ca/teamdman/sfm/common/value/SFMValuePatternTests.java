package ca.teamdman.sfm.common.value;

import ca.teamdman.sfml.ast.ProgramDefinitions;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SFMValuePatternTests {
    @Test
    void openObjectMatchRetainsTheCompleteValueIncludingExtraFields() {
        SFMValuePattern response = new SFMValuePattern.ObjectPattern(Map.of(
                "type", new SFMValuePattern.LiteralPattern(SFMValue.of("Response")),
                "JobId", SFMValuePattern.GUID,
                "text", SFMValuePattern.STRING
        ));
        SFMValue value = SFMValue.object(Map.of(
                "type", SFMValue.of("Response"),
                "JobId", SFMValue.of("ce3d7b60-a8c9-4e07-a21f-01759a92fbfe"),
                "text", SFMValue.of("answer"),
                "worker", SFMValue.of("local-echo-demo")
        ));

        SFMValueMatch match = response.match(value).orElseThrow();

        assertSame(value, match.value());
        assertEquals(SFMValue.of("local-echo-demo"), ((SFMValue.ObjectValue) match.value()).fields().get("worker"));
        assertTrue(match.bindings().isEmpty());
    }

    @Test
    void missingOrWrongFieldsProduceNoMatch() {
        SFMValuePattern request = new SFMValuePattern.ObjectPattern(Map.of(
                "type", new SFMValuePattern.LiteralPattern(SFMValue.of("Request")),
                "prompt", SFMValuePattern.STRING
        ));

        assertFalse(request.matches(SFMValue.object(Map.of("type", SFMValue.of("Request")))));
        assertFalse(request.matches(SFMValue.object(Map.of(
                "type", SFMValue.of("Response"),
                "prompt", SFMValue.of("hello")
        ))));
        assertFalse(request.matches(SFMValue.of("Request")));
    }

    @Test
    void aliasesAreStructuralAndGuidIsAnUnbrandedStringConstraint() {
        SFMValuePattern first = new SFMValuePattern.ObjectPattern(Map.of("id", SFMValuePattern.GUID));
        SFMValuePattern equivalent = new SFMValuePattern.ObjectPattern(Map.of("id", SFMValuePattern.GUID));
        ProgramDefinitions definitions = new ProgramDefinitions(
                Map.of("First", first, "Second", equivalent),
                Map.of("Me", "TeamDman")
        );
        SFMValue value = SFMValue.object(Map.of(
                "id",
                SFMValue.of("ce3d7b60-a8c9-4e07-a21f-01759a92fbfe")
        ));

        assertTrue(definitions.pattern("first").orElseThrow().matches(value));
        assertTrue(definitions.pattern("SECOND").orElseThrow().matches(value));
        assertEquals("TeamDman", definitions.player("me").orElseThrow());
        assertTrue(SFMValuePattern.GUID.matches(SFMValue.of("ce3d7b60-a8c9-4e07-a21f-01759a92fbfe")));
        assertFalse(SFMValuePattern.GUID.matches(SFMValue.of("guid")));
        assertFalse(SFMValuePattern.GUID.matches(SFMValue.of(1L)));
    }
}
