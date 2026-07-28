package ca.teamdman.sfm.client.terminal;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class SFMTerminalFocusSequenceTests {
    @Test
    void singleAndDoubleEscapeForwardButTripleEscapeExits() {
        SFMTerminalFocusSequence sequence = new SFMTerminalFocusSequence();

        assertEquals(SFMTerminalFocusSequence.Decision.FORWARD, sequence.escape(100));
        assertEquals(SFMTerminalFocusSequence.Decision.FORWARD, sequence.escape(200));
        assertEquals(SFMTerminalFocusSequence.Decision.EXIT, sequence.escape(300));
        assertEquals(SFMTerminalFocusSequence.Decision.FORWARD, sequence.escape(400));
    }

    @Test
    void tripleTabEscapesToJavaFocusWithinTheSameWindow() {
        SFMTerminalFocusSequence sequence = new SFMTerminalFocusSequence();

        assertEquals(SFMTerminalFocusSequence.Decision.FORWARD, sequence.tab(100));
        assertEquals(SFMTerminalFocusSequence.Decision.FORWARD, sequence.tab(200));
        assertEquals(SFMTerminalFocusSequence.Decision.JAVA_FOCUS, sequence.tab(300));
        assertEquals(SFMTerminalFocusSequence.Decision.FORWARD, sequence.tab(2_000));
    }
}
