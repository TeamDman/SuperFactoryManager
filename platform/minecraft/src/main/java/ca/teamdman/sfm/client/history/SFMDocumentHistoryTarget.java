package ca.teamdman.sfm.client.history;

/** Focused panel capability for moving a document head without destroying siblings. */
public interface SFMDocumentHistoryTarget {
    SFMHistoryGraphRuntime.OperationResult undoDocumentHistory();
}
