package ca.teamdman.sfm.client.keybinding;

import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Focus/capture contract tests named after the user-facing widget surface. */
class SFMKeyBindingCaptureWidgetTests {
    @Test
    void keyboardFocusedKeycapIsRemovableWithoutMouseInput() {
        SFMKeySequenceCapture capture = new SFMKeySequenceCapture();
        capture.capture(GLFW.GLFW_KEY_EQUAL, Set.of(SFMKeyModifier.CONTROL), 100);
        capture.focusToken(0);

        assertTrue(capture.removeFocusedToken());
        assertEquals(1, capture.tokenCount());
        assertTrue(capture.removeFocusedToken());
        assertEquals(0, capture.tokenCount());
        assertTrue(capture.isEmpty());
    }
}
