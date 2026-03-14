package ca.teamdman.sfm.client.text_editor;

import ca.teamdman.sfm.client.screen.SFMScreenChangeHelpers;
import ca.teamdman.sfm.client.screen.text_editor.ISFMTextEditScreen;
import ca.teamdman.sfm.client.screen.text_editor.SFMTextEditScreenV2;

public class SFMTextEditScreenV2Registration implements ISFMTextEditorRegistration {
    @Override
    public ISFMTextEditScreen createScreen(ISFMTextEditScreenOpenContext context) {
        return new SFMTextEditScreenV2(
                context,
                SFMScreenChangeHelpers.getCurrentScreen()
        );
    }
}
