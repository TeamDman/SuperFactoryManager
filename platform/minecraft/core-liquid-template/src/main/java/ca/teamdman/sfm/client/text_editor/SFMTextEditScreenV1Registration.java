package ca.teamdman.sfm.client.text_editor;

import ca.teamdman.sfm.client.screen.text_editor.ISFMTextEditScreen;
import ca.teamdman.sfm.client.screen.text_editor.SFMTextEditScreenV1;

public class SFMTextEditScreenV1Registration implements ISFMTextEditorRegistration {
    @Override
    public ISFMTextEditScreen createScreen(ISFMTextEditScreenOpenContext context) {
        return new SFMTextEditScreenV1(context);
    }
}
