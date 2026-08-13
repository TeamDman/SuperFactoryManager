package ca.teamdman.sfm.client.text_editor;

/** Save authority carried by an editable panel document recipe. */
@FunctionalInterface
public interface SFMTextDocumentSaveHandler {
    SFMTextDocumentSaveResult save(String content);

    static SFMTextDocumentSaveHandler discard() {
        return ignored -> SFMTextDocumentSaveResult.success();
    }
}
