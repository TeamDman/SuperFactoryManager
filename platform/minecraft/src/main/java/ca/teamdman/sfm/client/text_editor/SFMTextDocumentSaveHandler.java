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

    /** Requests cancellation before commit; false means not pending or too late, not discarded. */
    default boolean cancelPendingSave() { return false; }

    static SFMTextDocumentSaveHandler discard() {
        return ignored -> SFMTextDocumentSaveResult.success();
    }
}
