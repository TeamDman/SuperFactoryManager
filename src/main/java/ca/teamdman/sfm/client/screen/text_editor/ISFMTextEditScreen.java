package ca.teamdman.sfm.client.screen.text_editor;

import net.minecraft.client.gui.GuiScreen;

import ca.teamdman.sfm.client.text_editor.ISFMTextEditScreenOpenContext;

public interface ISFMTextEditScreen {

    ISFMTextEditScreenOpenContext openContext();

    default void onPreferenceChanged() {}

    default OpenBehaviour openBehaviour() {
        return OpenBehaviour.Push;
    }

    default GuiScreen asScreen() {
        return (GuiScreen) this;
    }

    enum OpenBehaviour {
        Push,
        Replace
    }
}
