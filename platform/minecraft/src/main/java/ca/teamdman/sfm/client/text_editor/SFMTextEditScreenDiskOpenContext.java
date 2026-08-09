package ca.teamdman.sfm.client.text_editor;

import ca.teamdman.sfm.common.label.LabelPositionHolder;

import java.util.function.Consumer;

public record SFMTextEditScreenDiskOpenContext(
        String initialValue,
        LabelPositionHolder labelPositionHolder,
        Consumer<String> saveWriter
) implements ISFMTextEditScreenOpenContext {
}
