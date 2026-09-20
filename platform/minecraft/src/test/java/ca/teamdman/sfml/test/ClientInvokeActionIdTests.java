package ca.teamdman.sfml.test;

import ca.teamdman.sfml.ast.ClientValueExpression;
import ca.teamdman.sfml.ast.FrameTrigger;
import ca.teamdman.sfml.ast.LetStatement;
import ca.teamdman.sfml.ast.ProgramExecutionSide;
import ca.teamdman.sfml.ast.TextReadValueExpression;
import ca.teamdman.sfml.ast.TimerTrigger;
import ca.teamdman.sfml.program_builder.ProgramBuilder;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ClientInvokeActionIdTests {
    private static String clientSource(String action) {
        return "CLIENT BTW\nEVERY FRAME FOR displays AS display DO\n"
                + "LET args BE JSON \"{}\"\nLET result BE INVOKE " + action + " WITH args\nEND";
    }

    @Test void ordinaryUnquotedActionIdsRemainSupported() {
        assertClientAction("sfm:terminal/display", "sfm:terminal/display");
    }

    @Test void quotedKeywordAndHyphenActionIdsRoundTripAsStaticLiterals() {
        for (String action : List.of("sfm:terminal/input/status", "sfm:terminal/input-read", "sfm:output/v1.0")) {
            assertClientAction("\"" + action + "\"", action);
        }
    }

    private static void assertClientAction(String literal, String expected) {
        var built = new ProgramBuilder(clientSource(literal)).forExecutionSide(ProgramExecutionSide.CLIENT).build();
        assertTrue(built.isBuildSuccessful(), () -> built.metadata().errors().toString());
        var trigger = assertInstanceOf(FrameTrigger.class, built.program().triggers().get(0));
        var statement = assertInstanceOf(LetStatement.class, trigger.block().statements().get(1));
        var invoke = assertInstanceOf(ClientValueExpression.Invoke.class, statement.expression());
        assertEquals(expected, invoke.action().toString());
        String printed = built.program().toString();
        assertTrue(printed.contains("INVOKE \"" + expected + "\" WITH args"));
        var reparsed = new ProgramBuilder(printed).forExecutionSide(ProgramExecutionSide.CLIENT).build();
        assertTrue(reparsed.isBuildSuccessful(), () -> reparsed.metadata().errors().toString());
        assertEquals(printed, reparsed.program().toString());
    }

    @Test void quotingDoesNotEnableDynamicOrMalformedActionIds() {
        for (String invalid : List.of("actionVariable", "sfm:terminal/input/status", "sfm:terminal/input-read",
                "\"not_qualified\"", "\":path\"", "\"sfm:\"", "\"sfm:has spaces\"",
                "\"SFM:path\"", "\"sfm:path:extra\"", "\"sfm:*\"", "\"\"")) {
            var built = new ProgramBuilder(clientSource(invalid)).forExecutionSide(ProgramExecutionSide.CLIENT).build();
            assertFalse(built.isBuildSuccessful(), invalid);
            assertFalse(built.metadata().errors().isEmpty(), invalid);
        }
    }

    @Test void serverTextInvokeAcceptsBothStaticSpellings() {
        for (String literal : List.of("sfm:text/read", "\"sfm:text/read\"")) {
            String source = "SERVER BTW\nEVERY 20 TICKS DO\n"
                    + "LET result BE STRING OF INVOKE " + literal + " WITH source\nEND";
            var built = new ProgramBuilder(source).forExecutionSide(ProgramExecutionSide.SERVER).build();
            assertTrue(built.isBuildSuccessful(), () -> built.metadata().errors().toString());
            var trigger = assertInstanceOf(TimerTrigger.class, built.program().triggers().get(0));
            var statement = assertInstanceOf(LetStatement.class, trigger.block().statements().get(0));
            assertEquals(new TextReadValueExpression("sfm:text/read", "source"), statement.expression());
            String printed = built.program().toString();
            assertTrue(printed.contains("STRING OF INVOKE \"sfm:text/read\" WITH source"));
            var reparsed = new ProgramBuilder(printed).forExecutionSide(ProgramExecutionSide.SERVER).build();
            assertTrue(reparsed.isBuildSuccessful(), () -> reparsed.metadata().errors().toString());
            assertEquals(printed, reparsed.program().toString());
        }
    }
}
