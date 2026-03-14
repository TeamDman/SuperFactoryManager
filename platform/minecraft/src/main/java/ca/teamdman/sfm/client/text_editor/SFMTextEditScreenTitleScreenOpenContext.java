package ca.teamdman.sfm.client.text_editor;

import ca.teamdman.sfm.common.label.LabelPositionHolder;

import java.util.function.Consumer;

public record SFMTextEditScreenTitleScreenOpenContext(
        String initialValue,
        LabelPositionHolder labelPositionHolder,
        Consumer<String> saveWriter,
        net.minecraft.client.gui.screens.TitleScreen titleScreen
) implements ISFMTextEditScreenOpenContext {
    @Override
    public TextEditScreenContentLanguage contentLanguage() {

        return TextEditScreenContentLanguage.SFML;
    }

}
