package ca.teamdman.sfm.client.text_editor;

import ca.teamdman.sfm.client.screen.SFMScreenChangeHelpers;
import ca.teamdman.sfm.client.screen.SFMTextEditorV3Screen;
import ca.teamdman.sfm.client.screen.text_editor.ISFMTextEditScreen;
import ca.teamdman.sfm.client.screen.text_editor.SFMTextEditorPanel;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenPanel;

/** Registration for the panel-capable Text Editor v3 implementation. */
public final class SFMTextEditorV3Registration implements ISFMTextEditorRegistration {
    @Override
    public boolean supportsReadOnlyPanel() {
        return true;
    }

    @Override
    public ISFMTextEditScreen createScreen(ISFMTextEditScreenOpenContext context) {
        return new SFMTextEditorV3Screen(
                context,
                SFMScreenChangeHelpers.getCurrentScreen(),
                context.preferPush()
        );
    }

    @Override
    public SFMScreenPanel createPanel(SFMTextEditorPanelOpenContext context) {
        return SFMTextEditorPanel.textEditorV3(context);
    }
}
