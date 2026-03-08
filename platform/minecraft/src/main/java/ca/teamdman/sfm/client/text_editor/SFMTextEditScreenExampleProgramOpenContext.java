package ca.teamdman.sfm.client.text_editor;

import ca.teamdman.sfm.client.examples.SFMExampleProgram;
import ca.teamdman.sfm.client.screen.SFMScreenChangeHelpers;
import ca.teamdman.sfm.common.label.LabelPositionHolder;
import ca.teamdman.sfm.common.localization.LocalizationEntry;
import ca.teamdman.sfm.common.localization.SFMLocalizationDatagen;
import net.minecraft.client.gui.screens.ConfirmScreen;

import java.util.List;
import java.util.function.Consumer;

public record SFMTextEditScreenExampleProgramOpenContext(
        String initialExampleContent,

        String initialDiskContent,

        List<SFMExampleProgram> examples,

        LabelPositionHolder labelPositionHolder,

        Consumer<String> saveWriter
) implements ISFMTextEditScreenOpenContext {
    @SFMLocalizationDatagen
    public static final LocalizationEntry SAVE_CHANGES_CONFIRM_SCREEN_TITLE = new LocalizationEntry(
            "gui.sfm.save_changes_confirm.title",
            "Save changes"
    );

    @SFMLocalizationDatagen
    public static final LocalizationEntry SAVE_CHANGES_CONFIRM_SCREEN_MESSAGE = new LocalizationEntry(
            "gui.sfm.save_changes_confirm.message",
            "Do you want to save before exiting?"
    );

    @SFMLocalizationDatagen
    public static final LocalizationEntry SAVE_CHANGES_CONFIRM_SCREEN_YES_BUTTON = new LocalizationEntry(
            "gui.sfm.save_changes_confirm.yes_button",
            "Overwrite disk"
    );

    @SFMLocalizationDatagen
    public static final LocalizationEntry SAVE_CHANGES_CONFIRM_SCREEN_NO_BUTTON = new LocalizationEntry(
            "gui.sfm.save_changes_confirm.no_button",
            "Continue editing"
    );

    @Override
    public void onSaveAndClose(String latestContent) {

        if (isSafeToOverwriteDisk()) {
            ISFMTextEditScreenOpenContext.super.onSaveAndClose(latestContent);
        } else {
            // The disk contains non-template code, ask before overwriting
            ConfirmScreen saveConfirmScreen = new ConfirmScreen(
                    saidYes -> {
                        SFMScreenChangeHelpers.popScreen(); // Close confirm screen
                        if (saidYes) {
                            ISFMTextEditScreenOpenContext.super.onSaveAndClose(latestContent);
                        }
                    },
                    SAVE_CHANGES_CONFIRM_SCREEN_TITLE.getComponent(),
                    SAVE_CHANGES_CONFIRM_SCREEN_MESSAGE.getComponent(),
                    SAVE_CHANGES_CONFIRM_SCREEN_YES_BUTTON.getComponent(),
                    SAVE_CHANGES_CONFIRM_SCREEN_NO_BUTTON.getComponent()
            );
            SFMScreenChangeHelpers.setOrPushScreen(saveConfirmScreen);
            saveConfirmScreen.setDelay(20);
        }
    }

    @Override
    public String initialValue() {

        return initialExampleContent();
    }

    public boolean equalsAnyTemplate(String content) {

        return examples()
                .stream()
                .map(SFMExampleProgram::programString)
                .map(String::trim)
                .anyMatch(content.trim()::equals);
    }

    /**
     * Check if it is safe to overwrite the disk with a new program.
     * If the disk is empty, it is safe to overwrite.
     * If the disk contains a template, it is safe to overwrite.
     *
     * @return true if it is safe to overwrite the disk, false otherwise
     */
    public boolean isSafeToOverwriteDisk() {

        if (initialDiskContent().isBlank()) return true;
        return equalsAnyTemplate(initialDiskContent());
    }

}
