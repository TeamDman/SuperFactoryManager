package ca.teamdman.sfm.client.text_editor;

import ca.teamdman.sfm.common.label.LabelPositionHolder;

import java.util.function.Consumer;

/**
 * Opens a preferred text editor as a layer over the current screen. This is
 * used for transient/read-only documents, such as command-palette help.
 */
public record SFMTextEditScreenOverlayOpenContext(
        String initialValue,
        LabelPositionHolder labelPositionHolder,
        Consumer<String> saveWriter
) implements ISFMTextEditScreenOpenContext {
    @Override
    public boolean preferPush() {
        return true;
    }
}
