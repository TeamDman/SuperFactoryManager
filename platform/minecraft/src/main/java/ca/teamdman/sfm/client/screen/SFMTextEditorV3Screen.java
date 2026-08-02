package ca.teamdman.sfm.client.screen;

import ca.teamdman.sfm.client.text_editor.ISFMTextEditScreenOpenContext;
import net.minecraft.client.gui.screens.Screen;

/** Public Text Editor v3 identity for the former canvas implementation. */
public class SFMTextEditorV3Screen extends SFMDrawCanvasScreen {
    public SFMTextEditorV3Screen(Screen previousScreen) {
        super(previousScreen);
    }

    public SFMTextEditorV3Screen(Screen previousScreen, boolean pushed) {
        super(previousScreen, pushed);
    }

    public SFMTextEditorV3Screen(ISFMTextEditScreenOpenContext context, Screen previousScreen) {
        super(context, previousScreen);
    }

    public SFMTextEditorV3Screen(ISFMTextEditScreenOpenContext context, Screen previousScreen, boolean pushed) {
        super(context, previousScreen, pushed);
    }
}
