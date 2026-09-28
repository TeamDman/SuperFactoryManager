package ca.teamdman.sfm.client.text_editor;

import ca.teamdman.sfm.client.screen.text_editor.ISFMTextEditScreen;

public interface ISFMTextEditorRegistration {

    /**
     * Create, but do not display, an editor screen for the given context.
     */
    ISFMTextEditScreen createScreen(ISFMTextEditScreenOpenContext context);
}
