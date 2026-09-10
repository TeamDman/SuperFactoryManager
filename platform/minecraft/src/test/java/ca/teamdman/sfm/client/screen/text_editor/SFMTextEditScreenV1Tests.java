package ca.teamdman.sfm.client.screen.text_editor;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class SFMTextEditScreenV1Tests {
    @Test
    public void saveShortcutUsesEventModifiersWithoutPollingTheOperatingSystem() {
        assertTrue(SFMTextEditScreenV1.isSaveAndCloseShortcut(
                org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER, org.lwjgl.glfw.GLFW.GLFW_MOD_SHIFT));
        assertTrue(SFMTextEditScreenV1.isSaveAndCloseShortcut(
                org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER, org.lwjgl.glfw.GLFW.GLFW_MOD_SHIFT));
        org.junit.jupiter.api.Assertions.assertFalse(SFMTextEditScreenV1.isSaveAndCloseShortcut(
                org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER, 0));
        org.junit.jupiter.api.Assertions.assertFalse(SFMTextEditScreenV1.isSaveAndCloseShortcut(
                org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER, org.lwjgl.glfw.GLFW.GLFW_MOD_CONTROL));
    }

    @Test
    public void narrowPanelLayoutKeepsEditorAndFooterInsideViewport() {
        var layout = SFMTextEditScreenV1.editorLayout(103, 238, 9);

        assertTrue(layout.textareaX() >= 0);
        assertTrue(layout.textareaX() + layout.textareaWidth() <= 103);
        assertTrue(layout.textareaY() >= 0);
        assertTrue(layout.textareaY() + layout.textareaHeight() <= layout.footerY());
        assertTrue(layout.configX() >= 0);
        assertTrue(layout.doneX() >= 0);
        assertTrue(layout.doneX() + layout.doneWidth() <= 103);
        assertTrue(layout.cancelX() >= 0);
        assertTrue(layout.cancelX() + layout.cancelWidth() <= 103);
        assertTrue(layout.footerY() + 20 <= 238);
    }
}
