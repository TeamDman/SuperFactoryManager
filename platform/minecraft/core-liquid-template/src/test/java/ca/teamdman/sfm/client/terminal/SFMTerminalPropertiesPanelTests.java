package ca.teamdman.sfm.client.terminal;

import ca.teamdman.sfm.client.screen.workspace.SFMPanelWidget;
import ca.teamdman.sfm.client.screen.workspace.SFMPanelWidgetHost;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspacePanelId;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SFMTerminalPropertiesPanelTests {
    @Test
    void everyVisibleTuningControlHasStableFocusNarrationAndAnExistingActionDraft() {
        List<String> executed = new ArrayList<>();
        SFMTerminalPropertiesPanel panel = new SFMTerminalPropertiesPanel(
                new SFMWorkspacePanelId(7), executed::add);
        SFMPanelWidgetHost host = panel.widgetHost().orElseThrow();
        List<SFMPanelWidget> controls = host.children();

        assertEquals(13, controls.size());
        assertEquals(13, new HashSet<>(controls.stream().map(SFMPanelWidget::elementId).toList()).size());
        for (SFMPanelWidget control : controls) {
            assertEquals(new ResourceLocation("sfm", "default"), control.keyboardUsageSituationId());
            assertTrue(control.narration().getString().startsWith("Use")
                    || control.narration().getString().startsWith("Fit")
                    || control.narration().getString().startsWith("Increase")
                    || control.narration().getString().startsWith("Decrease"));
            String draft = control.actionDraft().orElseThrow();
            assertTrue(draft.startsWith("sfm action invoke sfm:terminal/properties/"));

            assertTrue(host.focus(control.elementId()));
            assertTrue(host.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0));
            assertEquals(draft, executed.get(executed.size() - 1));
        }
    }

    @Test
    void actionDraftVocabularyMatchesRegisteredHierarchicalActions() {
        assertEquals(
                "sfm action invoke sfm:terminal/properties/surface/width/increase",
                SFMTerminalPropertiesPanel.actionDraft(
                        SFMTerminalTuningOperation.SURFACE_WIDTH_INCREASE));
        assertEquals(
                "sfm action invoke sfm:terminal/properties/font/auto",
                SFMTerminalPropertiesPanel.actionDraft(SFMTerminalTuningOperation.FONT_AUTO));
        assertEquals(
                "sfm action invoke sfm:terminal/properties/cells/rows/decrease",
                SFMTerminalPropertiesPanel.actionDraft(SFMTerminalTuningOperation.ROWS_DECREASE));
    }
}
