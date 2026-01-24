package ca.teamdman.sfm.client.text_editor;

import ca.teamdman.sfm.common.label.LabelPositionHolder;

import java.util.function.Consumer;

/**
 * Context for opening the text editor for a disk in a library block.
 * Library disks don't have label positions, so we use an empty holder.
 */
public record SFMTextEditScreenLibraryDiskOpenContext(
        String initialValue,
        Consumer<String> saveWriter
) implements ISFMTextEditScreenOpenContext {

    @Override
    public LabelPositionHolder labelPositionHolder() {
        return LabelPositionHolder.empty();
    }
}
