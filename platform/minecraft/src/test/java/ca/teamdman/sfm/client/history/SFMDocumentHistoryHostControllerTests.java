package ca.teamdman.sfm.client.history;

import ca.teamdman.sfm.client.history.document.SFMDocumentHistoryContract;
import ca.teamdman.sfm.client.history.document.SFMDocumentHistorySession;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SFMDocumentHistoryHostControllerTests {
    @Test
    void consumedUndoRawEventIsRetainedBeforeCheckout() {
        var session = session();
        ArrayList<SFMDocumentHistoryContract.DocumentState> checkouts = new ArrayList<>();
        var host = new SFMDocumentHistoryHostController(session, "editor", "canvas", checkouts::add);
        appendCharacter(host, "h", 1);
        host.recordRawInput(2, SFMDocumentHistoryContract.RawEventKind.KEY_DOWN,
                "screen", "key=90", Optional.empty(), 2, true, false);

        var result = host.undo("ctrl-z");

        assertEquals(SFMHistoryGraphRuntime.OperationStatus.APPLIED, result.status());
        assertEquals("", session.currentState().text());
        assertEquals(List.of(SFMDocumentHistoryContract.DocumentState.withCaret("", 0)), checkouts);
        assertEquals(2, session.projection().rawEvents().size());
        assertEquals(1, session.projection().headMovements().size());
        assertEquals(1, session.projection().headMovements().get(0).rawEventIds().size());
    }

    @Test
    void undoThenMutationRetainsDepartedSiblingAndRedoIsExplicitlyAmbiguous() {
        var session = session();
        var host = new SFMDocumentHistoryHostController(session, "editor", "canvas", ignored -> { });
        appendCharacter(host, "a", 1);
        String departed = session.currentRevisionId();
        host.undo("undo");
        appendCharacter(host, "b", 2);
        host.undo("undo-again");

        var redo = host.redo(Optional.empty(), "redo");

        assertEquals(SFMHistoryGraphRuntime.OperationStatus.NO_CHANGE, redo.status());
        assertTrue(redo.message().contains("More than one redo child"));
        assertTrue(session.retainedRevisions().stream().anyMatch(value -> value.id().equals(departed)));
        assertEquals(2, session.eligibleRedoChildren().size());
    }

    private static SFMDocumentHistorySession session() {
        return SFMDocumentHistorySession.create(
                new SFMDocumentHistoryContract.SessionIdentity(
                        "sfm:test/host-session",
                        "document-1",
                        Optional.empty()
                ),
                SFMDocumentHistoryContract.DocumentState.withCaret("", 0)
        );
    }

    private static void appendCharacter(SFMDocumentHistoryHostController host, String text, long tick) {
        String before = host.session().currentState().text();
        String after = before + text;
        host.recordRawInput(tick, SFMDocumentHistoryContract.RawEventKind.CHARACTER,
                "screen", text, Optional.of(text), 0, false, true);
        host.observeMutation(
                SFMDocumentHistoryContract.MutationKind.TYPE,
                SFMDocumentHistoryContract.EditDirection.FORWARD,
                SFMDocumentHistoryContract.DocumentState.withCaret(before,
                        SFMDocumentHistoryContract.codePointLength(before)),
                SFMDocumentHistoryContract.DocumentState.withCaret(after,
                        SFMDocumentHistoryContract.codePointLength(after)),
                Optional.of(text),
                tick,
                "typed"
        );
    }
}
