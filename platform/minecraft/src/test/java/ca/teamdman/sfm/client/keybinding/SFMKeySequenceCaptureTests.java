package ca.teamdman.sfm.client.keybinding;

import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SFMKeySequenceCaptureTests {
    @Test
    void tripleEscapeCancelsWithoutLeakingPendingStrokes() {
        SFMKeySequenceCapture capture = new SFMKeySequenceCapture();
        assertEquals(SFMKeySequenceCapture.EscapeResult.PENDING, capture.escape(100));
        assertEquals(SFMKeySequenceCapture.EscapeResult.PENDING, capture.escape(200));
        assertEquals(SFMKeySequenceCapture.EscapeResult.CANCELLED, capture.escape(300));
        assertTrue(capture.isEmpty());
    }

    @Test
    void singleAndDoubleEscapeRemainRecordableAndPhysicalTokensAreReadable() {
        SFMKeySequenceCapture capture = new SFMKeySequenceCapture();
        capture.escape(100);
        capture.escape(200);
        capture.commitPendingEscapes();
        capture.capture(GLFW.GLFW_KEY_EQUAL, Set.of(SFMKeyModifier.CONTROL), 300);
        assertEquals("Esc  Esc  Ctrl =", SFMKeyBindingDisplay.format(
                new SFMKeySequence(capture.strokes())));
    }

    @Test
    void eachModifierAndTheKeyCanBeRemovedAsAnIndividualKeycap() {
        SFMKeySequenceCapture capture = new SFMKeySequenceCapture();
        capture.capture(GLFW.GLFW_KEY_EQUAL,
                Set.of(SFMKeyModifier.CONTROL, SFMKeyModifier.SHIFT), 100);
        assertTrue(capture.removeToken(0, 0));
        assertEquals(List.of(SFMKeyModifier.SHIFT), capture.strokes().get(0).modifiers().stream().toList());
        assertTrue(capture.removeToken(0, 1));
        assertTrue(capture.isEmpty());
    }

    @Test
    void timeoutCommitsPendingEscapeBeforeTheNextCapture() {
        SFMKeySequenceCapture capture = new SFMKeySequenceCapture();
        capture.escape(100);
        capture.capture(GLFW.GLFW_KEY_A, Set.of(), 100 + SFMKeySequenceCapture.ESCAPE_WINDOW_MILLIS + 1);
        assertEquals(List.of(GLFW.GLFW_KEY_ESCAPE, GLFW.GLFW_KEY_A),
                capture.strokes().stream().map(SFMKeyStroke::keyCode).toList());
    }
}
