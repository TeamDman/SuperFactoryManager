package ca.teamdman.sfm.client.screen.workspace.diagnostic;

import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SFMRawInputDiagnosticTapTests {
    @Test
    void modifierMasksAreStableAndHumanReadable() {
        assertEquals("none", SFMRawInputDiagnosticTap.modifierMask(0));
        assertEquals(
                "shift+control+alt+super",
                SFMRawInputDiagnosticTap.modifierMask(
                        GLFW.GLFW_MOD_SHIFT
                                | GLFW.GLFW_MOD_CONTROL
                                | GLFW.GLFW_MOD_ALT
                                | GLFW.GLFW_MOD_SUPER
                )
        );
    }
}
