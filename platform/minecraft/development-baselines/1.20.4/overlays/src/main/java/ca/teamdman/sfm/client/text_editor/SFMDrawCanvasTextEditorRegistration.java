package ca.teamdman.sfm.client.text_editor;

import ca.teamdman.sfm.client.screen.SFMDrawCanvasScreen;
import ca.teamdman.sfm.client.screen.SFMScreenChangeHelpers;
import ca.teamdman.sfm.client.screen.text_editor.ISFMTextEditScreen;

public class SFMDrawCanvasTextEditorRegistration implements ISFMTextEditorRegistration {
    @Override
    public ISFMTextEditScreen createScreen(ISFMTextEditScreenOpenContext context) {
        return new SFMDrawCanvasScreen(
                context,
                SFMScreenChangeHelpers.getCurrentScreen(),
                context.preferPush()
        );
    }
}
