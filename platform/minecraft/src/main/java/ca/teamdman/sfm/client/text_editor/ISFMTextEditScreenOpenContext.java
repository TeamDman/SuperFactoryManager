package ca.teamdman.sfm.client.text_editor;

import ca.teamdman.sfm.client.screen.SFMScreenChangeHelpers;
import ca.teamdman.sfm.common.label.LabelPositionHolder;
import ca.teamdman.sfm.common.localization.LocalizationEntry;
import ca.teamdman.sfm.common.localization.SFMLocalizationDatagen;
import net.minecraft.client.gui.screens.ConfirmScreen;

import java.util.Optional;
import java.util.function.Consumer;

public interface ISFMTextEditScreenOpenContext {
    @SFMLocalizationDatagen
    LocalizationEntry EXIT_WITHOUT_SAVING_CONFIRM_SCREEN_TITLE = new LocalizationEntry(
            "gui.sfm.exit_without_saving_confirm.title",
            "Exit without saving?"
    );

    @SFMLocalizationDatagen
    LocalizationEntry EXIT_WITHOUT_SAVING_CONFIRM_SCREEN_MESSAGE = new LocalizationEntry(
            "gui.sfm.exit_without_saving_confirm.message",
            "Are you sure you want to abandon your work?"
    );

    @SFMLocalizationDatagen
    LocalizationEntry EXIT_WITHOUT_SAVING_CONFIRM_SCREEN_YES_BUTTON = new LocalizationEntry(
            "gui.sfm.exit_without_saving_confirm.yes_button",
            "Exit without saving"
    );

    @SFMLocalizationDatagen
    LocalizationEntry EXIT_WITHOUT_SAVING_CONFIRM_SCREEN_NO_BUTTON = new LocalizationEntry(
            "gui.sfm.exit_without_saving_confirm.no_button",
            "Continue editing"
    );

    String initialValue();

    /**
     * Whether the preferred editor should be layered over the current screen
     * instead of replacing it. This is useful for read-only/help documents
     * opened from another transient screen, such as the command palette.
     */
    default boolean preferPush() {
        return false;
    }

    /** Whether the editor must present the document without allowing edits. */
    default boolean readOnly() {
        return false;
    }

    /** Concrete document metadata when this editor was opened from a typed panel document. */
    default Optional<SFMTextDocumentSnapshot> documentSnapshot() {
        return Optional.empty();
    }

    default void onTryClose(
            String latestContent,
            Runnable finalizeClose
    ) {
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
                    EXIT_WITHOUT_SAVING_CONFIRM_SCREEN_TITLE.getComponent(),
                    EXIT_WITHOUT_SAVING_CONFIRM_SCREEN_MESSAGE.getComponent(),
                    EXIT_WITHOUT_SAVING_CONFIRM_SCREEN_YES_BUTTON.getComponent(),
                    EXIT_WITHOUT_SAVING_CONFIRM_SCREEN_NO_BUTTON.getComponent()
            );
            SFMScreenChangeHelpers.setOrPushScreen(exitWithoutSavingConfirmScreen);
            exitWithoutSavingConfirmScreen.setDelay(20);
        }
    }

    default void onSaveAndClose(String latestContent) {
        if (saveDocument(latestContent).saved()) SFMScreenChangeHelpers.popScreen();
    }

    /**
     * Typed save-and-close seam used by editors that need to render a rejected
     * save diagnostic. Full-screen contexts retain their historical callback
     * behavior; panel contexts override this to close only their own entry.
     */
    default SFMTextDocumentSaveResult trySaveAndClose(String latestContent) {
        onSaveAndClose(latestContent);
        return SFMTextDocumentSaveResult.success();
    }

    default SFMTextDocumentSaveResult saveDocument(String latestContent) {
        try {
            saveWriter().accept(latestContent);
            return SFMTextDocumentSaveResult.success();
        } catch (RuntimeException failure) {
            String detail = failure.getMessage() == null
                    ? failure.getClass().getSimpleName()
                    : failure.getMessage();
            return SFMTextDocumentSaveResult.rejected(
                    net.minecraft.network.chat.Component.literal(detail)
            );
        }
    }

    default boolean asynchronousSave() { return false; }

    default java.util.concurrent.CompletableFuture<SFMTextDocumentSaveResult> saveDocumentAsync(String content) {
        return java.util.concurrent.CompletableFuture.completedFuture(saveDocument(content));
    }

    /** Called on the client thread only after durable async success. */
    default void documentSaved(String submittedText) { }

    default void finishAsyncSaveClose() { SFMScreenChangeHelpers.popScreen(); }

    default boolean saveHostIsCurrent() { return true; }

    default boolean cancelPendingSave() { return false; }

    Consumer<String> saveWriter();

    LabelPositionHolder labelPositionHolder();

}
