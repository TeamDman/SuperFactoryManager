package ca.teamdman.sfm.client.text_editor;

import ca.teamdman.sfm.common.localization.LocalizationEntry;
import ca.teamdman.sfm.common.localization.SFMLocalizationDatagen;
import net.minecraft.network.chat.Component;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Function;

/** Client-thread save lifecycle shared by editor presentations, independent of rendering. */
public final class SFMTextDocumentSaveSession {
    @SFMLocalizationDatagen
    public static final LocalizationEntry SAVING = new LocalizationEntry(
            "gui.sfm.text_editor.saving", "Saving… You can keep editing; Done closes only the saved version.");
    @SFMLocalizationDatagen
    public static final LocalizationEntry SAVED_NEWER_EDITS = new LocalizationEntry(
            "gui.sfm.text_editor.saved_newer_edits", "Saved the submitted version. Newer edits remain unsaved.");

    public record Completion(String submittedText, boolean closeRequested, SFMTextDocumentSaveResult result) {
        public boolean mayClose(String currentText) {
            return closeRequested && result.saved() && submittedText.equals(currentText);
        }
    }

    private long generation;
    private boolean pending;
    private boolean closeRequested;
    private String submittedText;

    public boolean pending() { return pending; }

    public boolean submit(
            String text, boolean closeAfter,
            Function<String, CompletableFuture<SFMTextDocumentSaveResult>> writer,
            Executor clientExecutor, Consumer<Completion> onComplete
    ) {
        Objects.requireNonNull(text, "text");
        if (pending) {
            if (closeAfter && text.equals(submittedText)) closeRequested = true;
            return false;
        }
        pending = true;
        submittedText = text;
        closeRequested = closeAfter;
        long request = ++generation;
        CompletableFuture<SFMTextDocumentSaveResult> future;
        try {
            future = Objects.requireNonNull(writer.apply(text), "save future");
        } catch (RuntimeException failure) {
            future = CompletableFuture.failedFuture(failure);
        }
        future.whenComplete((result, failure) -> clientExecutor.execute(() -> {
            if (generation != request) return;
            pending = false;
            if (failure != null) {
                Throwable root = failure;
                while (root.getCause() != null) root = root.getCause();
                onComplete.accept(new Completion(text, closeRequested, SFMTextDocumentSaveResult.rejected(
                        Component.literal(Objects.toString(root.getMessage(), root.getClass().getSimpleName())))));
            } else {
                onComplete.accept(new Completion(text, closeRequested, Objects.requireNonNull(result)));
            }
        }));
        return true;
    }

    /** Revoke presentation callbacks without pretending the underlying save has been cancelled. */
    public void detach() {
        generation++;
        pending = false;
    }
}
