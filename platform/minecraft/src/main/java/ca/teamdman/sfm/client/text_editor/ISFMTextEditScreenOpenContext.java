package ca.teamdman.sfm.client.text_editor;

import ca.teamdman.sfm.client.screen.SFMScreenChangeHelpers;
import ca.teamdman.sfm.common.label.LabelPositionHolder;
import ca.teamdman.sfm.common.localization.LocalizationKeys;
import net.minecraft.client.gui.screens.ConfirmScreen;

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
            ConfirmScreen exitWithoutSavingConfirmScreen = new ConfirmScreen(
                    doSave -> {
                        // Close confirm screen
                        SFMScreenChangeHelpers.popScreen();
                        // Only close editor if user confirms
                        if (doSave) {
                            // close without saving
                            finalizeClose.run();
                        }
                    },
                    LocalizationKeys.EXIT_WITHOUT_SAVING_CONFIRM_SCREEN_TITLE.getComponent(),
                    LocalizationKeys.EXIT_WITHOUT_SAVING_CONFIRM_SCREEN_MESSAGE.getComponent(),
                    LocalizationKeys.EXIT_WITHOUT_SAVING_CONFIRM_SCREEN_YES_BUTTON.getComponent(),
                    LocalizationKeys.EXIT_WITHOUT_SAVING_CONFIRM_SCREEN_NO_BUTTON.getComponent()
            );
            SFMScreenChangeHelpers.setOrPushScreen(exitWithoutSavingConfirmScreen);
            exitWithoutSavingConfirmScreen.setDelay(20);
        }
    }
    default void onSaveAndClose(String latestContent) {
        saveWriter().accept(latestContent);
        SFMScreenChangeHelpers.popScreen();
    }
    Consumer<String> saveWriter();
    LabelPositionHolder labelPositionHolder();
    TextEditScreenContentLanguage contentLanguage();
}
