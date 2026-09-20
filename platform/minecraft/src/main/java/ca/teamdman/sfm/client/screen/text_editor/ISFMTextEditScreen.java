package ca.teamdman.sfm.client.screen.text_editor;

import ca.teamdman.sfm.client.text_editor.ISFMTextEditScreenOpenContext;
import net.minecraft.client.gui.screens.Screen;

public interface ISFMTextEditScreen {
    ISFMTextEditScreenOpenContext openContext();
    default void onPreferenceChanged() {}
    /** Actual panel disposal, not transient screen covering or resizing. */
    default void onDocumentHostClosed() { }
    default OpenBehaviour openBehaviour() {
        return OpenBehaviour.Push;
    }
    default Screen asScreen() {
        return (Screen) this;
    }

    enum OpenBehaviour {
        Push,
        Replace
    }
}
