package ca.teamdman.sfm.client.history;

import ca.teamdman.sfm.client.history.document.SFMDocumentHistorySession;

/**
 * A live, independently addressable editable document backed by the generic
 * immutable history kernel.
 *
 * <p>The host identity names an editor/palette session, not a file path. Two
 * editors showing the same path therefore retain independent heads unless a
 * future explicit shared-document host says otherwise.</p>
 */
public interface SFMDocumentHistoryHost extends SFMDocumentHistoryTarget {
    default boolean documentHistoryAvailable() {
        return true;
    }

    String documentHistorySessionId();

    SFMDocumentHistorySession documentHistorySession();

    SFMDocumentHistoryHostController documentHistoryController();

    default SFMHistoryGraphRuntime.OperationResult undoDocumentHistory() {
        return documentHistoryController().undo("sfm:document/history/undo");
    }

    default SFMHistoryGraphRuntime.OperationResult redoDocumentHistory(String childRevisionId) {
        return documentHistoryController().redo(
                childRevisionId == null || childRevisionId.isBlank()
                        ? java.util.Optional.empty()
                        : java.util.Optional.of(childRevisionId),
                "sfm:document/history/redo"
        );
    }
}
