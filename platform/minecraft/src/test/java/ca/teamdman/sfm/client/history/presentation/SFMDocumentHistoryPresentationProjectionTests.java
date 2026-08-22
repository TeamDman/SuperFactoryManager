package ca.teamdman.sfm.client.history.presentation;

import ca.teamdman.sfm.client.history.canvas.SFMHistoryCanvasLayout;
import ca.teamdman.sfm.client.history.canvas.SFMHistoryCanvasLayoutEngine;
import ca.teamdman.sfm.client.history.document.SFMDocumentHistoryContract;
import ca.teamdman.sfm.client.history.document.SFMDocumentHistorySession;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SFMDocumentHistoryPresentationProjectionTests {
    @Test
    void semanticTransactionProducesPairedActionAndStateLanes() {
        SFMDocumentHistorySession history = history();
        append(history, "raw-hello", "mutation-hello", "hello", 1);

        var presentation = SFMDocumentHistoryPresentationProjection.project(history.projection());
        var layout = SFMHistoryCanvasLayoutEngine.layout(presentation);

        assertEquals(3, presentation.nodes().size());
        assertEquals(2, presentation.edges().size());
        assertEquals(1, presentation.markers().size());
        assertTrue(layout.snapshot().nodes().stream().anyMatch(node ->
                node.lane() == SFMHistoryCanvasLayout.Lane.ACTION
                        && node.source().label().equals("type \"hello\"")));
        assertTrue(layout.snapshot().nodes().stream().anyMatch(node ->
                node.lane() == SFMHistoryCanvasLayout.Lane.STATE
                        && node.source().label().contains("hello")));
    }

    @Test
    void undoIsChronologicalButJumpsToRetainedImmutableState() {
        SFMDocumentHistorySession history = history();
        String departed = append(history, "raw-hello", "mutation-hello", "hello", 1);
        history.appendRawInput(raw("raw-undo", "Z", 2));
        history.undo("text-editor-v3", "ctrl-z", List.of("raw-undo"));
        append(history, "raw-new", "mutation-new", "new content", 3);

        var presentation = SFMDocumentHistoryPresentationProjection.project(history.projection());
        var layout = SFMHistoryCanvasLayoutEngine.layout(presentation);

        var jump = layout.snapshot().edges().stream()
                .filter(edge -> edge.pathKind() == SFMHistoryCanvasLayout.EdgePathKind.HEAD_MOVEMENT_JUMP)
                .findFirst()
                .orElseThrow();
        assertTrue(jump.curved());
        assertFalse(jump.ranking());
        assertEquals(history.rootRevisionId(), jump.toNodeId());
        assertTrue(presentation.nodes().stream().anyMatch(node ->
                node.id().equals(departed)
                        && node.roles().contains(SFMHistoryGraphPresentationModel.LegendRole.RETAINED_ALTERNATIVE)));
        assertTrue(presentation.nodes().stream().anyMatch(node ->
                node.origins().contains(SFMHistoryGraphPresentationModel.NodeOrigin.HEAD_MOVEMENT)));
        assertTrue(layout.transcript().accessibleLines().stream().anyMatch(line ->
                line.toLowerCase(java.util.Locale.ROOT).contains("undo")));
    }

    private static SFMDocumentHistorySession history() {
        return SFMDocumentHistorySession.create(
                new SFMDocumentHistoryContract.SessionIdentity(
                        "sfm:test/session-1",
                        "document-1",
                        Optional.empty()
                ),
                SFMDocumentHistoryContract.DocumentState.withCaret("", 0)
        );
    }

    private static String append(
            SFMDocumentHistorySession history,
            String rawId,
            String mutationId,
            String text,
            long tick
    ) {
        history.appendRawInput(raw(rawId, text, tick));
        var result = history.append(new SFMDocumentHistoryContract.MutationRequest(
                mutationId,
                SFMDocumentHistoryContract.MutationKind.TYPE,
                SFMDocumentHistoryContract.EditDirection.FORWARD,
                SFMDocumentHistoryContract.DocumentState.withCaret(
                        text,
                        SFMDocumentHistoryContract.codePointLength(text)
                ),
                Optional.empty(),
                Optional.empty(),
                Optional.of(text),
                new SFMDocumentHistoryContract.MutationProvenance(
                        "text-editor-v3",
                        mutationId,
                        tick,
                        "editor",
                        List.of(rawId)
                ),
                Optional.empty()
        ));
        return result.revision().id();
    }

    private static SFMDocumentHistoryContract.RawInput raw(String id, String text, long tick) {
        return new SFMDocumentHistoryContract.RawInput(
                id,
                tick,
                SFMDocumentHistoryContract.RawEventKind.CHARACTER,
                "test",
                text,
                Optional.of(text),
                0,
                false,
                true
        );
    }
}
