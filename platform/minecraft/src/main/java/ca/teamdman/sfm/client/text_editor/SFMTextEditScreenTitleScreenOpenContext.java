package ca.teamdman.sfm.client.text_editor;

import ca.teamdman.sfm.common.label.LabelPositionHolder;
import net.minecraft.client.gui.screens.TitleScreen;

import java.util.function.Consumer;

public record SFMTextEditScreenTitleScreenOpenContext(
        String initialValue,
        LabelPositionHolder labelPositionHolder,
        Consumer<String> saveWriter,
        TitleScreen titleScreen
) implements ISFMTextEditScreenOpenContext {
}
