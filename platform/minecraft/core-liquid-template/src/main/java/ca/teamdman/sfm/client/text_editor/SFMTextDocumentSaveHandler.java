package ca.teamdman.sfm.client.text_editor;

/** Save authority carried by an editable panel document recipe. */
@FunctionalInterface
public interface SFMTextDocumentSaveHandler {
    SFMTextDocumentSaveResult save(String content);

    /** Opt-in only: ordinary Minecraft save callbacks retain their caller-thread contract. */
    default boolean asynchronous() { return false; }

    default java.util.concurrent.CompletableFuture<SFMTextDocumentSaveResult> saveAsync(String content) {
        return java.util.concurrent.CompletableFuture.completedFuture(save(content));
    }

    /**
     * Opt-in for durable handlers whose Save-and-close operation is observed outside the
     * transient editor. The editor may close as soon as submission succeeds; ordinary Save
     * still remains open and ordinary handlers still wait for acknowledgement.
     */
    default boolean detachSaveAndCloseAfterSubmission() { return false; }

    /** Requests cancellation before commit; false means not pending or too late, not discarded. */
    default boolean cancelPendingSave() { return false; }

    static SFMTextDocumentSaveHandler discard() {
        return ignored -> SFMTextDocumentSaveResult.success();
    }
}
