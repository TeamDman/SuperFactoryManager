package ca.teamdman.sfm.client.text_editor;

import ca.teamdman.sfm.client.examples.SFMExampleProgram;
import ca.teamdman.sfm.client.screen.SFMScreenChangeHelpers;
import ca.teamdman.sfm.common.label.LabelPositionHolder;
import ca.teamdman.sfm.common.localization.LocalizationKeys;
import com.github.bsideup.jabel.Desugar;
import net.minecraft.client.gui.GuiYesNo;

import java.util.List;
import java.util.function.Consumer;

@Desugar
public record SFMTextEditScreenExampleProgramOpenContext(
        String initialExampleContent,
        String initialDiskContent,
        List<SFMExampleProgram> examples,
        LabelPositionHolder labelPositionHolder,
        Consumer<String> saveWriter
) implements ISFMTextEditScreenOpenContext {
    @Override
    public void onSaveAndClose(String latestContent) {
        if (isSafeToOverwriteDisk()) {
            ISFMTextEditScreenOpenContext.super.onSaveAndClose(latestContent);
        } else {
            // The disk contains non-template code, ask before overwriting
            GuiYesNo saveConfirmScreen = new GuiYesNo(
                    (saidYes, buttonId) -> {
                        SFMScreenChangeHelpers.popScreen(); // Close confirm screen
                        if (saidYes) {
                            ISFMTextEditScreenOpenContext.super.onSaveAndClose(latestContent);
                        }
                    },
                    LocalizationKeys.SAVE_CHANGES_CONFIRM_SCREEN_TITLE.getComponent().getFormattedText(),
                    LocalizationKeys.SAVE_CHANGES_CONFIRM_SCREEN_MESSAGE.getComponent().getFormattedText(),
                    LocalizationKeys.SAVE_CHANGES_CONFIRM_SCREEN_YES_BUTTON.getComponent().getFormattedText(),
                    LocalizationKeys.SAVE_CHANGES_CONFIRM_SCREEN_NO_BUTTON.getComponent().getFormattedText(),
                    0
            );
            SFMScreenChangeHelpers.setOrPushScreen(saveConfirmScreen);
            saveConfirmScreen.setButtonDelay(20);
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
        if (initialDiskContent().trim().isEmpty()) return true;
        return equalsAnyTemplate(initialDiskContent());
    }
}
