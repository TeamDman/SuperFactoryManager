package ca.teamdman.sfm.client.text_editor;

import ca.teamdman.sfm.client.screen.SFMScreenChangeHelpers;
import ca.teamdman.sfm.client.screen.text_editor.ISFMTextEditScreen;
import ca.teamdman.sfm.client.screen.text_editor.SFMTextEditScreenV3;

public class SFMTextEditScreenV3Registration implements ISFMTextEditorRegistration {
    @Override
    public ISFMTextEditScreen createScreen(ISFMTextEditScreenOpenContext context) {
        return new SFMTextEditScreenV3(
                context,
                SFMScreenChangeHelpers.getCurrentScreen()
        );
    }
}
