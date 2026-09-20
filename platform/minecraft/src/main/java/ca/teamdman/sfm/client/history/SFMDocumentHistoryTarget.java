package ca.teamdman.sfm.client.history;

/** Focused panel capability for moving a document head without destroying siblings. */
public interface SFMDocumentHistoryTarget {
    SFMHistoryGraphRuntime.OperationResult undoDocumentHistory();

    default SFMHistoryGraphRuntime.OperationResult redoDocumentHistory(String childRevisionId) {
        return SFMHistoryGraphRuntime.OperationResult.rejected(
                "This document host does not expose retained redo branches"
        );
    }
}
