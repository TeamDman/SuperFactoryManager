package ca.teamdman.sfm.client.terminal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class SFMTerminalFocusSequenceTests {
    @Test
    void singleAndDoubleEscapeForwardButTripleEscapeDelegatesToTheHost() {
        SFMTerminalFocusSequence sequence = new SFMTerminalFocusSequence();

        assertEquals(SFMTerminalFocusSequence.Decision.FORWARD, sequence.escape(100));
        assertEquals(
                new SFMTerminalFocusSequence.Hint(SFMTerminalFocusSequence.HintKind.ESCAPE, 2),
                sequence.hint(100)
        );
        assertEquals(SFMTerminalFocusSequence.Decision.FORWARD, sequence.escape(200));
        assertEquals(
                new SFMTerminalFocusSequence.Hint(SFMTerminalFocusSequence.HintKind.ESCAPE, 1),
                sequence.hint(200)
        );
        assertEquals(SFMTerminalFocusSequence.Decision.HOST_ESCAPE, sequence.escape(300));
        assertNull(sequence.hint(300));
        assertEquals(SFMTerminalFocusSequence.Decision.FORWARD, sequence.escape(400));
    }

    @Test
    void tripleTabEscapesToJavaFocusWithinTheSameWindow() {
        SFMTerminalFocusSequence sequence = new SFMTerminalFocusSequence();

        assertEquals(SFMTerminalFocusSequence.Decision.FORWARD, sequence.tab(100));
        assertEquals(
                new SFMTerminalFocusSequence.Hint(SFMTerminalFocusSequence.HintKind.TAB, 2),
                sequence.hint(100)
        );
        assertEquals(SFMTerminalFocusSequence.Decision.FORWARD, sequence.tab(200));
        assertEquals(SFMTerminalFocusSequence.Decision.JAVA_FOCUS, sequence.tab(300));
        assertNull(sequence.hint(300));
        long afterWindow = SFMTerminalFocusSequence.WINDOW_NANOS + 301;
        assertEquals(SFMTerminalFocusSequence.Decision.FORWARD, sequence.tab(afterWindow));
        assertNull(sequence.hint(afterWindow + SFMTerminalFocusSequence.WINDOW_NANOS + 1));
    }

    @Test
    void hintExpiresAndTheOtherGestureClearsIt() {
        SFMTerminalFocusSequence sequence = new SFMTerminalFocusSequence();

        sequence.escape(100);
        assertNull(sequence.hint(100 + SFMTerminalFocusSequence.WINDOW_NANOS + 1));

        sequence.tab(200);
        assertEquals(
                new SFMTerminalFocusSequence.Hint(SFMTerminalFocusSequence.HintKind.TAB, 2),
                sequence.hint(200)
        );
    }
}
