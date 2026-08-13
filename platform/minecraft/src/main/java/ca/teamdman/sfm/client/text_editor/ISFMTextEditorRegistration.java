package ca.teamdman.sfm.client.text_editor;

import ca.teamdman.sfm.client.screen.text_editor.ISFMTextEditScreen;
import ca.teamdman.sfm.client.screen.text_editor.SFMTextEditorPanel;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenPanel;

public interface ISFMTextEditorRegistration {

    /** Whether this editor enforces {@code readOnly=true} throughout its UI. */
    default boolean supportsReadOnlyPanel() {
        return false;
    }

    /**
     * Create, but do not display, an editor screen for the given context.
     */
    ISFMTextEditScreen createScreen(ISFMTextEditScreenOpenContext context);

    /** Create a panel-capable projection of this editor implementation. */
    default SFMScreenPanel createPanel(SFMTextEditorPanelOpenContext context) {
        return SFMTextEditorPanel.legacy(context, this::createScreen);
    }
}
