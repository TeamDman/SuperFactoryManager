package ca.teamdman.sfm;

import ca.teamdman.sfml.manipulation.ProgramStringManipulationUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class IndentationTests {
    @Test
    public void indent1() {
        String program = """
                EVERY 20 TICKS DO
                INPUT FROM a
                OUTPUT TO b
                END
                """.stripIndent();
        String selection = "INPUT FROM a\nOUTPUT TO b";
        int selectionStart = program.indexOf(selection);
        int selectionEnd = selectionStart + selection.length();
        assertEquals(selection, program.substring(selectionStart, selectionEnd));

        String expectedProgram = """
                EVERY 20 TICKS DO
                    INPUT FROM a
                    OUTPUT TO b
                END
                """.stripIndent();
        String expectedSelection = "    INPUT FROM a\n    OUTPUT TO b";
        int expectedSelectionStart = expectedProgram.indexOf(expectedSelection);
        int expectedSelectionEnd = expectedSelectionStart + expectedSelection.length();
        assertEquals(expectedSelection, expectedProgram.substring(expectedSelectionStart, expectedSelectionEnd));


        var result = ProgramStringManipulationUtils.indent(program, selectionStart, selectionEnd);
        assertEquals(expectedProgram, result.content());
        assertEquals(
                expectedSelection,
                result.content().substring(result.cursorPosition(), result.selectionCursorPosition())
        );
        assertEquals(expectedSelectionStart, result.cursorPosition());
        assertEquals(expectedSelectionEnd, result.selectionCursorPosition());
    }

    @Test
    public void indent2() {
        String program = """
                EVERY 20 TICKS DO
                    INPUT FROM a
                    OUTPUT TO b
                END
                """.stripIndent();
        String selection = "INPUT FROM a\n    OUTPUT TO b";
        int selectionStart = program.indexOf(selection);
        int selectionEnd = selectionStart + selection.length();
        assertEquals(selection, program.substring(selectionStart, selectionEnd));

        String expectedProgram = """
                EVERY 20 TICKS DO
                        INPUT FROM a
                        OUTPUT TO b
                END
                """.stripIndent();
        String expectedSelection = "INPUT FROM a\n        OUTPUT TO b";
        int expectedSelectionStart = expectedProgram.indexOf(expectedSelection);
        int expectedSelectionEnd = expectedSelectionStart + expectedSelection.length();
        assertEquals(expectedSelection, expectedProgram.substring(expectedSelectionStart, expectedSelectionEnd));


        var result = ProgramStringManipulationUtils.indent(program, selectionStart, selectionEnd);
        assertEquals(expectedProgram, result.content());
        assertEquals(
                expectedSelection,
                result.content().substring(result.cursorPosition(), result.selectionCursorPosition())
        );
        assertEquals(expectedSelectionStart, result.cursorPosition());
        assertEquals(expectedSelectionEnd, result.selectionCursorPosition());
    }


    @Test
    public void deindent1() {
        String program = """
                EVERY 20 TICKS DO
                    INPUT FROM a
                    OUTPUT TO b
                END
                """.stripIndent();
        String selection = "INPUT FROM a\n    OUTPUT TO b";
        int selectionStart = program.indexOf(selection);
        int selectionEnd = selectionStart + selection.length();
        assertEquals(selection, program.substring(selectionStart, selectionEnd));

        String expectedProgram = """
                EVERY 20 TICKS DO
                INPUT FROM a
                OUTPUT TO b
                END
                """.stripIndent();
        String expectedSelection = "INPUT FROM a\nOUTPUT TO b";
        int expectedSelectionStart = expectedProgram.indexOf(expectedSelection);
        int expectedSelectionEnd = expectedSelectionStart + expectedSelection.length();
        assertEquals(expectedSelection, expectedProgram.substring(expectedSelectionStart, expectedSelectionEnd));


        var result = ProgramStringManipulationUtils.deindent(program, selectionStart, selectionEnd);
        assertEquals(expectedProgram, result.content());
        assertEquals(
                expectedSelection,
                result.content().substring(result.cursorPosition(), result.selectionCursorPosition())
        );
        assertEquals(expectedSelectionStart, result.cursorPosition());
        assertEquals(expectedSelectionEnd, result.selectionCursorPosition());
    }


    @Test
    public void deindent2() {
        String program = """
                EVERY 20 TICKS DO
                        INPUT FROM a
                        OUTPUT TO b
                END
                """.stripIndent();
        String selection = "INPUT FROM a\n        OUTPUT TO b";
        int selectionStart = program.indexOf(selection);
        int selectionEnd = selectionStart + selection.length();
        assertEquals(selection, program.substring(selectionStart, selectionEnd));

        String expectedProgram = """
                EVERY 20 TICKS DO
                    INPUT FROM a
                    OUTPUT TO b
                END
                """.stripIndent();
        String expectedSelection = "INPUT FROM a\n    OUTPUT TO b";
        int expectedSelectionStart = expectedProgram.indexOf(expectedSelection);
        int expectedSelectionEnd = expectedSelectionStart + expectedSelection.length();
        assertEquals(expectedSelection, expectedProgram.substring(expectedSelectionStart, expectedSelectionEnd));


        var result = ProgramStringManipulationUtils.deindent(program, selectionStart, selectionEnd);
        assertEquals(expectedProgram, result.content());
        assertEquals(
                expectedSelection,
                result.content().substring(result.cursorPosition(), result.selectionCursorPosition())
        );
        assertEquals(expectedSelectionStart, result.cursorPosition());
        assertEquals(expectedSelectionEnd, result.selectionCursorPosition());
    }
}
