package ca.teamdman.sfm.client.text_editor;

import ca.teamdman.sfm.client.screen.SFMScreenChangeHelpers;
import ca.teamdman.sfm.common.label.LabelPositionHolder;
import ca.teamdman.sfm.common.localization.LocalizationKeys;
import net.minecraft.client.gui.GuiYesNo;
import net.minecraft.client.gui.GuiYesNoCallback;

import java.util.function.Consumer;

public interface ISFMTextEditScreenOpenContext {
    String initialValue();

    default void onTryClose(String latestContent, Runnable finalizeClose) {
        // If the content is different, ask to save
        if (initialValue().equals(latestContent)) {
            // Content is unmodified, close without confirmation
            finalizeClose.run();
        } else {
            // Confirm that the user wants to discard their changes
            GuiYesNo exitWithoutSavingConfirmScreen = new GuiYesNo(
                    (result, id) -> {
                        // Close confirm screen
                        SFMScreenChangeHelpers.popScreen();
                        // Only close editor if user confirms
                        if (result) {
                            // close without saving
                            finalizeClose.run();
                        }
                    },
                    LocalizationKeys.EXIT_WITHOUT_SAVING_CONFIRM_SCREEN_TITLE.getComponent().getUnformattedText(),
                    LocalizationKeys.EXIT_WITHOUT_SAVING_CONFIRM_SCREEN_MESSAGE.getComponent().getUnformattedText(),
                    LocalizationKeys.EXIT_WITHOUT_SAVING_CONFIRM_SCREEN_YES_BUTTON.getComponent().getUnformattedText(),
                    LocalizationKeys.EXIT_WITHOUT_SAVING_CONFIRM_SCREEN_NO_BUTTON.getComponent().getUnformattedText(),
                    0
            );
            SFMScreenChangeHelpers.setOrPushScreen(exitWithoutSavingConfirmScreen);
            exitWithoutSavingConfirmScreen.setButtonDelay(20);
        }
    }

    default void onSaveAndClose(String latestContent) {
        saveWriter().accept(latestContent);
        SFMScreenChangeHelpers.popScreen();
    }

    Consumer<String> saveWriter();

    LabelPositionHolder labelPositionHolder();
}
