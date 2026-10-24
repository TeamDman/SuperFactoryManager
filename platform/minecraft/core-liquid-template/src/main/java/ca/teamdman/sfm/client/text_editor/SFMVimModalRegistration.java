package ca.teamdman.sfm.client.text_editor;

import ca.teamdman.sfm.client.screen.text_editor.ISFMTextEditScreen;
import ca.teamdman.sfm.client.screen.text_editor.SFMVimModalScreen;

/** Experimental Java Vim-style subset; never advertised as actual Neovim. */
public final class SFMVimModalRegistration implements ISFMTextEditorRegistration {
    @Override
    public ISFMTextEditScreen createScreen(ISFMTextEditScreenOpenContext context) {
        return new SFMVimModalScreen(context);
    }
}
