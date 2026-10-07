package ca.teamdman.sfm.client.screen.workspace;

import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SFMWorkspaceTabShortcutTests {
    @Test
    void plainControlDigitsSelectTabsButModifiedChordsReachPanelContent() {
        for (int key = GLFW.GLFW_KEY_1; key <= GLFW.GLFW_KEY_9; key++) {
            assertTrue(SFMScreenMultiplexer.isTabIndexShortcut(key, true, false, false));
            assertFalse(SFMScreenMultiplexer.isTabIndexShortcut(key, true, true, false));
            assertFalse(SFMScreenMultiplexer.isTabIndexShortcut(key, true, false, true));
            assertFalse(SFMScreenMultiplexer.isTabIndexShortcut(key, false, false, false));
        }
        assertFalse(SFMScreenMultiplexer.isTabIndexShortcut(GLFW.GLFW_KEY_0, true, false, false));
    }
}
