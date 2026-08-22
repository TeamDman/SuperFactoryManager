package ca.teamdman.sfm.client.history.document;

import ca.teamdman.sfm.client.history.document.SFMDocumentHistoryContract.DocumentState;
import ca.teamdman.sfm.client.history.document.SFMDocumentHistoryContract.EditDirection;
import ca.teamdman.sfm.client.history.document.SFMDocumentHistoryContract.GroupingPolicy;
import ca.teamdman.sfm.client.history.document.SFMDocumentHistoryContract.LogicalPoint;
import ca.teamdman.sfm.client.history.document.SFMDocumentHistoryContract.LogicalSelection;
import ca.teamdman.sfm.client.history.document.SFMDocumentHistoryContract.MutationKind;
import ca.teamdman.sfm.client.history.document.SFMDocumentHistoryContract.MutationProvenance;
import ca.teamdman.sfm.client.history.document.SFMDocumentHistoryContract.MutationRequest;
import ca.teamdman.sfm.client.history.document.SFMDocumentHistoryContract.RawEventKind;
import ca.teamdman.sfm.client.history.document.SFMDocumentHistoryContract.RawInput;
import ca.teamdman.sfm.client.history.document.SFMDocumentHistoryContract.SemanticTransactionKind;
import ca.teamdman.sfm.client.history.document.SFMDocumentHistoryContract.SessionIdentity;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SFMDocumentHistorySessionTests {
    @Test
    void samePathDoesNotMergeExactSessionIdentityOrUndoHead() {
        SessionIdentity firstIdentity = identity("editor-1", "file:///tmp/A.java");
        SessionIdentity secondIdentity = identity("editor-2", "file:///tmp/A.java");
        SFMDocumentHistorySession first = SFMDocumentHistorySession.create(
                firstIdentity,
                DocumentState.withCaret("", 0)
        );
        SFMDocumentHistorySession second = SFMDocumentHistorySession.create(
                secondIdentity,
                DocumentState.withCaret("", 0)
        );

        appendAction(first, "first-edit", "a", 1);

        assertNotEquals(first.identity(), second.identity());
        assertNotEquals(first.rootRevisionId(), second.rootRevisionId());
        assertEquals("a", first.currentState().text());
        assertEquals("", second.currentState().text());
        assertEquals("file:///tmp/A.java", first.identity().sourceAddress().orElseThrow());
        assertEquals("file:///tmp/A.java", second.identity().sourceAddress().orElseThrow());
    }

    @Test
    void logicalStateUsesValidatedUnicodeCodePointCoordinates() {
        String text = "A\uD83D\uDE00e\u0301\n\u03B2";
        assertEquals(6, SFMDocumentHistoryContract.codePointLength(text));
        LogicalPoint afterEmoji = LogicalPoint.at(text, 2);
        LogicalPoint secondLine = LogicalPoint.at(text, 5);
        DocumentState state = new DocumentState(
                text,
                List.of(
                        new LogicalSelection("caret", afterEmoji, afterEmoji),
                        new LogicalSelection("range", LogicalPoint.at(text, 1), LogicalPoint.at(text, 4))
                ),
                Optional.of("caret")
        );

        assertEquals(1, afterEmoji.lineOneBased());
        assertEquals(3, afterEmoji.columnCodePointOneBased());
        assertEquals(2, secondLine.lineOneBased());
        assertEquals(1, secondLine.columnCodePointOneBased());
        assertEquals(2, state.selections().size());
        assertThrows(IllegalArgumentException.class, () -> DocumentState.withCaret("\uD800", 0));
        assertThrows(IllegalArgumentException.class, () -> new DocumentState(
                text,
                List.of(new LogicalSelection("bad", new LogicalPoint(2, 1, 99), afterEmoji)),
                Optional.of("bad")
        ));
    }

    @Test
    void rawIngressIsAppendOnlyDeduplicatedAndKeepsDisposition() {
        SFMDocumentHistorySession session = session("raw");
        RawInput consumed = new RawInput(
                "event-ctrl-z-down",
                10,
                RawEventKind.KEY_DOWN,
                "forge-pre-dispatch",
                "key:z",
                Optional.empty(),
                2,
                true,
                false
        );
        RawInput delivered = new RawInput(
                "event-char-emoji",
                11,
                RawEventKind.CHARACTER,
                "screen-char-typed",
                "unicode",
                Optional.of("\uD83D\uDE00"),
                0,
                false,
                true
        );

        assertEquals(SFMDocumentHistorySession.RawInputStatus.APPENDED,
                session.appendRawInput(consumed).status());
        long afterFirst = session.generation();
        assertEquals(SFMDocumentHistorySession.RawInputStatus.DUPLICATE,
                session.appendRawInput(consumed).status());
        assertEquals(afterFirst, session.generation());
        session.appendRawInput(delivered);

        assertEquals(2, session.projection().rawEvents().size());
        assertTrue(session.projection().rawEvents().get(0).input().consumed());
        assertFalse(session.projection().rawEvents().get(0).input().delivered());
        assertTrue(session.projection().rawEvents().get(1).input().delivered());
        assertThrows(IllegalArgumentException.class, () -> session.appendRawInput(new RawInput(
                consumed.id(),
                consumed.clientTick(),
                RawEventKind.KEY_UP,
                consumed.source(),
                consumed.code(),
                consumed.text(),
                consumed.modifiers(),
                false,
                true
        )));
    }

    @Test
    void openSpaceTheProducesThreeTransactionsWithExactRawExpansion() {
        SFMDocumentHistorySession session = session("typing");
        long tick = 1;
        for (String glyph : List.of("o", "p", "e", "n", " ", "t", "h", "e")) {
            typeGlyph(session, glyph, tick++);
        }

        var projection = session.projection();
        assertEquals("open the", session.currentState().text());
        assertEquals(3, projection.semanticTransactions().size());
        assertEquals(List.of(
                        SemanticTransactionKind.TYPE_WORD,
                        SemanticTransactionKind.TYPE_DELIMITER,
                        SemanticTransactionKind.TYPE_WORD
                ),
                projection.semanticTransactions().stream().map(value -> value.kind()).toList());
        assertEquals(List.of("type \"open\"", "click \"<space>\"", "type \"the\""),
                projection.semanticTransactions().stream().map(value -> value.label()).toList());
        assertEquals(List.of(4, 1, 3),
                projection.semanticTransactions().stream().map(value -> value.mutationIds().size()).toList());
        assertEquals(List.of(12, 3, 9),
                projection.semanticTransactions().stream().map(value -> value.rawEventIds().size()).toList());

        assertEquals(SFMDocumentHistorySession.HeadMoveStatus.APPLIED,
                session.undo("test", "undo-the", List.of()).status());
        assertEquals("open ", session.currentState().text());
        session.undo("test", "undo-space", List.of());
        assertEquals("open", session.currentState().text());
        session.undo("test", "undo-open", List.of());
        assertEquals("", session.currentState().text());
        assertEquals(SFMDocumentHistorySession.HeadMoveStatus.APPLIED,
                session.redo(Optional.empty(), "test", "redo-open", List.of()).status());
        assertEquals("open", session.currentState().text());
    }

    @Test
    void noOpKeepsRawEvidenceWithoutInventingARevisionOrGraphEdge() {
        SFMDocumentHistorySession session = session("no-op");
        List<String> raw = recordStroke(session, "backspace-at-root", "key:backspace", Optional.empty(), 1);
        int beforeRevisions = session.retainedRevisions().size();
        SFMDocumentHistorySession.AppendResult result = session.append(new MutationRequest(
                "mutation-no-op",
                MutationKind.NO_OP,
                EditDirection.BACKWARD,
                session.currentState(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                provenance("no-op", 1, raw),
                Optional.empty()
        ));

        assertEquals(SFMDocumentHistorySession.AppendStatus.NO_CHANGE, result.status());
        assertEquals(beforeRevisions, session.retainedRevisions().size());
        assertEquals(1, session.projection().mutations().size());
        assertEquals(SemanticTransactionKind.NO_OP,
                session.projection().semanticTransactions().get(0).kind());
        assertEquals(0, session.projection().graph().edges().size());
        assertEquals(SFMDocumentHistorySession.HeadMoveStatus.NO_CHANGE,
                session.undo("test", "undo-no-op", List.of()).status());
        assertThrows(IllegalArgumentException.class, () -> session.append(new MutationRequest(
                "invalid-no-op",
                MutationKind.NO_OP,
                EditDirection.NONE,
                DocumentState.withCaret("x", 1),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                provenance("invalid-no-op", 2, List.of()),
                Optional.empty()
        )));
    }

    @Test
    void deletionRunsGroupButPasteCompletionCaretFocusAndIdleCreateBoundaries() {
        SFMDocumentHistorySession deletion = SFMDocumentHistorySession.create(
                identity("deletion", "memory:deletion"),
                DocumentState.withCaret("abcd", 4)
        );
        deleteBackward(deletion, "d", 1);
        deleteBackward(deletion, "c", 2);
        assertEquals(1, deletion.semanticTransactions(GroupingPolicy.typingV1()).size());
        assertEquals(SemanticTransactionKind.DELETE_BACKWARD,
                deletion.semanticTransactions(GroupingPolicy.typingV1()).get(0).kind());

        SFMDocumentHistorySession boundaries = session("boundaries");
        typeGlyph(boundaries, "a", 1);
        typeGlyph(boundaries, "b", 100);
        appendText(boundaries, " pasted", MutationKind.PASTE, "paste", 101, List.of());
        appendText(boundaries, "Completion", MutationKind.COMPLETION, "completion", 102, List.of());
        changeCaret(boundaries, 0, "caret", 103);
        changeFocus(boundaries, "focus", 104);

        assertEquals(List.of(
                        SemanticTransactionKind.TYPE_WORD,
                        SemanticTransactionKind.TYPE_WORD,
                        SemanticTransactionKind.PASTE,
                        SemanticTransactionKind.COMPLETION,
                        SemanticTransactionKind.CARET_CHANGE,
                        SemanticTransactionKind.FOCUS_CHANGE
                ),
                boundaries.projection().semanticTransactions().stream().map(value -> value.kind()).toList());

        GroupingPolicy relaxed = new GroupingPolicy(
                GroupingPolicy.typingV1().id(),
                "typing-v2-relaxed-idle",
                200,
                true,
                true
        );
        assertEquals(5, boundaries.semanticTransactions(relaxed).size(),
                "a and b regroup under a second deterministic policy revision");
        assertEquals("ab pastedCompletion", boundaries.currentState().text());
    }

    @Test
    void focusRawEventSplitsOtherwiseMergeableTyping() {
        SFMDocumentHistorySession session = session("focus-boundary");
        typeGlyph(session, "a", 1);
        session.appendRawInput(new RawInput(
                "focus-lost",
                2,
                RawEventKind.FOCUS,
                "screen",
                "lost",
                Optional.empty(),
                0,
                false,
                true
        ));
        typeGlyph(session, "b", 3);

        assertEquals(2, session.projection().semanticTransactions().size());
    }

    @Test
    void undoUndoDoRetainsDepartedBranchAndExplicitRedoChoices() {
        SFMDocumentHistorySession session = session("branching");
        String root = session.currentRevisionId();
        String a = appendAction(session, "a", "a", 1).revision().id();
        String ab = appendAction(session, "ab", "ab", 2).revision().id();

        session.undo("test", "undo-ab", List.of());
        session.undo("test", "undo-a", List.of());
        String c = appendAction(session, "c", "c", 3).revision().id();

        assertEquals(List.of(a, c), session.childRevisionIds(root));
        assertTrue(session.revision(ab).isPresent(), "undo-undo-do must retain the departed grandchild");
        assertEquals("c", session.currentState().text());
        session.checkout(root, "test", "checkout-root", List.of());
        assertEquals(List.of(a, c), session.eligibleRedoChildren().stream().map(value -> value.id()).toList());
        assertEquals(SFMDocumentHistorySession.HeadMoveStatus.APPLIED,
                session.redo(Optional.of(c), "test", "redo-c", List.of()).status());
        assertEquals("c", session.currentState().text());
    }

    @Test
    void redoUsesOnlyChildOrReportsAmbiguityWithoutMovingHead() {
        SFMDocumentHistorySession single = session("single-redo");
        String child = appendAction(single, "a", "a", 1).revision().id();
        single.undo("test", "undo-a", List.of());
        assertEquals(SFMDocumentHistorySession.HeadMoveStatus.APPLIED,
                single.redo(Optional.empty(), "test", "redo-a", List.of()).status());
        assertEquals(child, single.currentRevisionId());

        SFMDocumentHistorySession ambiguous = session("ambiguous-redo");
        String root = ambiguous.currentRevisionId();
        String left = appendAction(ambiguous, "left", "left", 1).revision().id();
        ambiguous.checkout(root, "test", "checkout-before-right", List.of());
        String right = appendAction(ambiguous, "right", "right", 2).revision().id();
        ambiguous.checkout(root, "test", "checkout-before-choice", List.of());

        SFMDocumentHistorySession.HeadMoveResult result = ambiguous.redo(
                Optional.empty(),
                "test",
                "ambiguous-redo",
                List.of()
        );
        assertEquals(SFMDocumentHistorySession.HeadMoveStatus.AMBIGUOUS, result.status());
        assertEquals(root, ambiguous.currentRevisionId());
        assertEquals(List.of(left, right), result.candidateRevisionIds());
    }

    @Test
    void archiveProjectionAndListenerNotificationsAreDeterministic() {
        SFMDocumentHistorySession session = session("archive");
        ArrayList<SFMDocumentHistorySession.ChangeNotification> notifications = new ArrayList<>();
        SFMDocumentHistorySession.Subscription subscription = session.subscribe(notifications::add, true);
        typeGlyph(session, "x", 1);
        session.undo("test", "undo-x", List.of());
        subscription.close();
        appendAction(session, "after-close", "y", 2);

        assertEquals(List.of(
                        SFMDocumentHistorySession.ChangeKind.INITIAL,
                        SFMDocumentHistorySession.ChangeKind.RAW_INPUT,
                        SFMDocumentHistorySession.ChangeKind.RAW_INPUT,
                        SFMDocumentHistorySession.ChangeKind.RAW_INPUT,
                        SFMDocumentHistorySession.ChangeKind.MUTATION,
                        SFMDocumentHistorySession.ChangeKind.HEAD_MOVEMENT
                ),
                notifications.stream().map(value -> value.kind()).toList());
        assertTrue(notifications.stream().mapToLong(value -> value.generation()).reduce(0, Math::max)
                <= session.generation());

        var archive = session.exportArchive();
        var projection = session.projection();
        SFMDocumentHistorySession restored = SFMDocumentHistorySession.restore(archive);
        assertEquals(archive, restored.exportArchive());
        assertEquals(archive.digest(), restored.exportArchive().digest());
        assertEquals(projection, restored.projection());
        assertEquals(projection.digest(), restored.projection().digest());

        GroupingPolicy secondPolicy = new GroupingPolicy(
                GroupingPolicy.typingV1().id(),
                "typing-v2-no-word-grouping",
                20,
                false,
                true
        );
        assertEquals(restored.projection(secondPolicy), restored.projection(secondPolicy));
        assertEquals(archive, restored.exportArchive(), "reprojection must not mutate canonical history");
    }

    @Test
    void detachedMaterializationDoesNotMoveHeadAndDuplicateMutationIsIdempotent() {
        SFMDocumentHistorySession session = session("detached");
        String root = session.currentRevisionId();
        MutationRequest request = request(
                "detached-mutation",
                MutationKind.ACTION,
                EditDirection.NONE,
                DocumentState.withCaret("detached", 8),
                Optional.empty(),
                1,
                List.of()
        );
        SFMDocumentHistorySession.AppendResult first = session.appendDetached(root, request);

        assertEquals(root, session.currentRevisionId());
        assertEquals(SFMDocumentHistorySession.AppendStatus.DUPLICATE,
                session.appendDetached(root, request).status());
        assertEquals(2, session.retainedRevisions().size());
        session.checkout(first.revision().id(), "test", "checkout-detached", List.of());
        assertEquals("detached", session.currentState().text());
    }

    private static SFMDocumentHistorySession session(String id) {
        return SFMDocumentHistorySession.create(identity(id, "memory:" + id), DocumentState.withCaret("", 0));
    }

    private static SessionIdentity identity(String id, String address) {
        return new SessionIdentity("sfm:test/document-history/" + id, "document", Optional.of(address));
    }

    private static SFMDocumentHistorySession.AppendResult typeGlyph(
            SFMDocumentHistorySession session,
            String glyph,
            long tick
    ) {
        String eventPrefix = "stroke-" + tick + "-" + Integer.toHexString(glyph.codePointAt(0));
        List<String> raw = recordStroke(session, eventPrefix, "key:" + glyph, Optional.of(glyph), tick);
        return appendText(session, glyph, MutationKind.TYPE, "type-" + tick, tick, raw);
    }

    private static List<String> recordStroke(
            SFMDocumentHistorySession session,
            String prefix,
            String code,
            Optional<String> text,
            long tick
    ) {
        RawInput down = new RawInput(
                prefix + "-down", tick, RawEventKind.KEY_DOWN, "pre-dispatch", code,
                Optional.empty(), 0, false, true
        );
        RawInput character = new RawInput(
                prefix + "-char", tick, RawEventKind.CHARACTER, "char-typed", "unicode",
                text, 0, false, true
        );
        RawInput up = new RawInput(
                prefix + "-up", tick, RawEventKind.KEY_UP, "pre-dispatch", code,
                Optional.empty(), 0, false, true
        );
        session.appendRawInput(down);
        session.appendRawInput(character);
        session.appendRawInput(up);
        return List.of(down.id(), character.id(), up.id());
    }

    private static SFMDocumentHistorySession.AppendResult appendText(
            SFMDocumentHistorySession session,
            String inserted,
            MutationKind kind,
            String id,
            long tick,
            List<String> raw
    ) {
        String before = session.currentState().text();
        String after = before + inserted;
        return session.append(request(
                id,
                kind,
                EditDirection.FORWARD,
                DocumentState.withCaret(after, SFMDocumentHistoryContract.codePointLength(after)),
                Optional.of(inserted),
                tick,
                raw
        ));
    }

    private static SFMDocumentHistorySession.AppendResult appendAction(
            SFMDocumentHistorySession session,
            String id,
            String resultingText,
            long tick
    ) {
        return session.append(request(
                "action-" + id,
                MutationKind.ACTION,
                EditDirection.NONE,
                DocumentState.withCaret(resultingText, SFMDocumentHistoryContract.codePointLength(resultingText)),
                Optional.empty(),
                tick,
                List.of()
        ));
    }

    private static void deleteBackward(SFMDocumentHistorySession session, String expectedDeleted, long tick) {
        String before = session.currentState().text();
        int length = SFMDocumentHistoryContract.codePointLength(before);
        int start = SFMDocumentHistoryContract.charIndexAtCodePoint(before, length - 1);
        String deleted = before.substring(start);
        assertEquals(expectedDeleted, deleted);
        String after = before.substring(0, start);
        session.append(request(
                "delete-" + tick,
                MutationKind.DELETE_BACKWARD,
                EditDirection.BACKWARD,
                DocumentState.withCaret(after, length - 1),
                Optional.of(deleted),
                tick,
                List.of()
        ));
    }

    private static void changeCaret(
            SFMDocumentHistorySession session,
            int codePointOffset,
            String id,
            long tick
    ) {
        session.append(request(
                "caret-" + id,
                MutationKind.CARET_CHANGE,
                EditDirection.NONE,
                DocumentState.withCaret(session.currentState().text(), codePointOffset),
                Optional.empty(),
                tick,
                List.of()
        ));
    }

    private static void changeFocus(SFMDocumentHistorySession session, String id, long tick) {
        DocumentState before = session.currentState();
        List<LogicalSelection> selections = before.selections().stream()
                .map(selection -> new LogicalSelection(
                        selection.id() + "-focused",
                        selection.anchor(),
                        selection.active()
                ))
                .toList();
        session.append(new MutationRequest(
                "focus-" + id,
                MutationKind.FOCUS_CHANGE,
                EditDirection.NONE,
                new DocumentState(before.text(), selections, selections.isEmpty()
                        ? Optional.empty()
                        : Optional.of(selections.get(0).id())),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                new MutationProvenance("test", "focus-" + id, tick, "focus:" + id, List.of()),
                Optional.empty()
        ));
    }

    private static MutationRequest request(
            String id,
            MutationKind kind,
            EditDirection direction,
            DocumentState resultingState,
            Optional<String> changedText,
            long tick,
            List<String> raw
    ) {
        return new MutationRequest(
                "mutation-" + id,
                kind,
                direction,
                resultingState,
                Optional.empty(),
                Optional.empty(),
                changedText,
                provenance(id, tick, raw),
                Optional.empty()
        );
    }

    private static MutationProvenance provenance(String id, long tick, List<String> raw) {
        return new MutationProvenance("test", "request-" + id, tick, "focus:editor", raw);
    }
}
