package ca.teamdman.sfm.client.history.canvas;

import ca.teamdman.sfm.client.history.presentation.SFMHistoryGraphPresentationModel;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SFMHistoryCanvasLayoutEngineTests {
    @Test
    void emptyAndSingleHistoriesProduceBoundedMachineReadableSnapshots() {
        SFMHistoryCanvasLayoutEngine.Result empty = SFMHistoryCanvasLayoutEngine.layout(presentation(List.of(), List.of()));

        assertEquals(SFMHistoryCanvasLayout.SCHEMA, empty.snapshot().schema());
        assertEquals(SFMHistoryCanvasLayout.Orientation.TOP_DOWN, empty.snapshot().orientation());
        assertTrue(empty.snapshot().contentBounds().isEmpty());
        assertTrue(empty.snapshot().nodes().isEmpty());
        assertTrue(empty.snapshot().edges().isEmpty());
        assertTrue(empty.transcript().entries().isEmpty());
        assertEquals(0, empty.spatialIndex().stats().subjects());

        SFMHistoryGraphPresentationModel.Node source = state("state-root", "Empty document");
        SFMHistoryCanvasLayoutEngine.Result first = SFMHistoryCanvasLayoutEngine.layout(
                presentation(List.of(source), List.of())
        );
        SFMHistoryCanvasLayoutEngine.Result second = SFMHistoryCanvasLayoutEngine.layout(
                presentation(List.of(source), List.of())
        );

        assertEquals(first.snapshot(), second.snapshot());
        assertEquals(first.transcript(), second.transcript());
        SFMHistoryCanvasLayout.Node node = first.snapshot().nodes().get(0);
        assertEquals("state-root", node.stableId());
        assertEquals(source, node.source());
        assertEquals(SFMHistoryCanvasLayout.Lane.STATE, node.lane());
        assertEquals("history-state-amber", node.styleId());
        assertTrue(first.snapshot().contentBounds().contains(node.bounds()));
        assertEquals(
                "state-root",
                first.hitTest(center(node.bounds()), 0).orElseThrow().subject().stableId()
        );
    }

    @Test
    void forkUsesDisjointPairedLanesAndPreservesSemanticAuthority() {
        var root = state("state-root", "Empty");
        var firstAction = action(
                "intent-a",
                SFMHistoryGraphPresentationModel.NodeOrigin.ACTION_INTENT,
                "Type alpha",
                new SFMHistoryGraphPresentationModel.Detail("intent.action-id", "sfm:keyboard/type")
        );
        var secondAction = action(
                "intent-b",
                SFMHistoryGraphPresentationModel.NodeOrigin.ACTION_INTENT,
                "Type beta",
                new SFMHistoryGraphPresentationModel.Detail("intent.action-id", "sfm:keyboard/type")
        );
        var alpha = state("state-alpha", "alpha");
        var beta = state("state-beta", "beta");
        var result = SFMHistoryCanvasLayoutEngine.layout(presentation(
                List.of(root, firstAction, secondAction, alpha, beta),
                List.of(
                        edge("edge-root-a", root, firstAction),
                        edge("edge-a-alpha", firstAction, alpha),
                        edge("edge-root-b", root, secondAction),
                        edge("edge-b-beta", secondAction, beta)
                )
        ));

        var laidOutRoot = node(result, root.id());
        var laidOutFirstAction = node(result, firstAction.id());
        var laidOutSecondAction = node(result, secondAction.id());
        var laidOutAlpha = node(result, alpha.id());
        var laidOutBeta = node(result, beta.id());

        assertEquals(0, laidOutRoot.rank());
        assertEquals(1, laidOutFirstAction.rank());
        assertEquals(1, laidOutSecondAction.rank());
        assertEquals(2, laidOutAlpha.rank());
        assertEquals(2, laidOutBeta.rank());
        assertEquals(SFMHistoryCanvasLayout.Lane.ACTION, laidOutFirstAction.lane());
        assertEquals("history-action-blue", laidOutFirstAction.styleId());
        assertEquals(SFMHistoryCanvasLayout.Lane.STATE, laidOutAlpha.lane());
        assertEquals("history-state-amber", laidOutAlpha.styleId());
        assertFalse(laidOutFirstAction.bounds().intersects(laidOutSecondAction.bounds()));
        assertFalse(laidOutAlpha.bounds().intersects(laidOutBeta.bounds()));
        assertEquals(firstAction, laidOutFirstAction.source());
        assertEquals("sfm:keyboard/type", laidOutFirstAction.source().details().get(0).value());
        assertTrue(result.snapshot().edges().stream().allMatch(SFMHistoryCanvasLayout.Edge::ranking));
        assertTrue(result.snapshot().edges().stream().allMatch(edge ->
                edge.points().get(0).y() < edge.points().get(edge.points().size() - 1).y()));
    }

    @Test
    void intentEvaluationAndOutcomeRemainIndependentInspectableNodes() {
        var root = state("state-root", "Empty");
        var intent = action(
                "intent-1",
                SFMHistoryGraphPresentationModel.NodeOrigin.ACTION_INTENT,
                "Intent type hello",
                new SFMHistoryGraphPresentationModel.Detail("intent.query", "sfm:keyboard/type hello")
        );
        var evaluation = action(
                "evaluation-1",
                SFMHistoryGraphPresentationModel.NodeOrigin.ACTION_EVALUATION,
                "Evaluation accepted",
                new SFMHistoryGraphPresentationModel.Detail("evaluation.policy", "frozen")
        );
        var outcome = action(
                "outcome-1",
                SFMHistoryGraphPresentationModel.NodeOrigin.ACTION_OUTCOME,
                "Outcome succeeded",
                new SFMHistoryGraphPresentationModel.Detail("outcome.status", "SUCCEEDED")
        );
        var hello = state("state-hello", "hello");
        var result = SFMHistoryCanvasLayoutEngine.layout(presentation(
                List.of(root, intent, evaluation, outcome, hello),
                List.of(
                        edge("semantic-1", root, intent),
                        edge("semantic-2", intent, evaluation),
                        edge("semantic-3", evaluation, outcome),
                        edge("semantic-4", outcome, hello)
                )
        ));

        assertEquals(5, result.snapshot().nodes().size());
        assertEquals(intent, node(result, intent.id()).source());
        assertEquals(evaluation, node(result, evaluation.id()).source());
        assertEquals(outcome, node(result, outcome.id()).source());
        assertTrue(result.transcript().entries().stream().anyMatch(entry ->
                entry.key().equals("node:intent-1") && entry.details().contains(intent.details().get(0))));
        assertTrue(result.transcript().entries().stream().anyMatch(entry ->
                entry.key().equals("node:evaluation-1") && entry.details().contains(evaluation.details().get(0))));
        assertTrue(result.transcript().entries().stream().anyMatch(entry ->
                entry.key().equals("node:outcome-1") && entry.details().contains(outcome.details().get(0))));
        assertTrue(result.transcript().accessibleLines().stream().anyMatch(line ->
                line.contains("intent.query sfm:keyboard/type hello")));
    }

    @Test
    void headMovementJumpIsCurvedNonRankingAndCannotDestabilizeRanks() {
        var root = state("state-root", "Empty");
        var type = action("intent-type", SFMHistoryGraphPresentationModel.NodeOrigin.ACTION_INTENT, "Type hello");
        var hello = state("state-hello", "hello");
        var undo = action("head-undo", SFMHistoryGraphPresentationModel.NodeOrigin.HEAD_MOVEMENT, "Undo");
        var result = SFMHistoryCanvasLayoutEngine.layout(presentation(
                List.of(root, type, hello, undo),
                List.of(
                        edge("edge-root-type", root, type),
                        edge("edge-type-hello", type, hello),
                        edge("edge-undo-enter", hello, undo),
                        edge("edge-undo-jump", undo, root)
                )
        ));

        SFMHistoryCanvasLayout.Edge jump = edge(result, "edge-undo-jump");
        assertEquals(SFMHistoryCanvasLayout.EdgePathKind.HEAD_MOVEMENT_JUMP, jump.pathKind());
        assertFalse(jump.ranking());
        assertTrue(jump.curved());
        assertEquals(3, jump.points().size());
        assertEquals(3, node(result, undo.id()).rank());
        assertEquals(0, node(result, root.id()).rank());
        assertTrue(jump.sourceRank() > jump.targetRank());
        assertTrue(result.transcript().entries().stream()
                .filter(entry -> entry.key().equals("edge:edge-undo-jump"))
                .findFirst().orElseThrow().accessibleText().contains("Curved non-ranking relation"));
    }

    @Test
    void ordinaryCyclesBecomeCurvedNonRankingOverlays() {
        var first = state("state-a", "A");
        var second = state("state-b", "B");
        var result = SFMHistoryCanvasLayoutEngine.layout(presentation(
                List.of(first, second),
                List.of(edge("edge-a-b", first, second), edge("edge-b-a", second, first))
        ));

        assertEquals(0, node(result, first.id()).rank());
        assertEquals(0, node(result, second.id()).rank());
        assertTrue(result.snapshot().edges().stream().allMatch(edge ->
                edge.pathKind() == SFMHistoryCanvasLayout.EdgePathKind.CYCLE_OVERLAY));
        assertTrue(result.snapshot().edges().stream().allMatch(SFMHistoryCanvasLayout.Edge::curved));
    }

    @Test
    void leftRightOrientationIsAnExactStableIdentityTranspose() {
        SFMHistoryCanvasLayoutEngine.Result topDown = forkResult();
        SFMHistoryCanvasLayoutEngine.Result leftRight = topDown.transpose();

        assertEquals(SFMHistoryCanvasLayout.Orientation.LEFT_RIGHT, leftRight.snapshot().orientation());
        assertEquals(topDown.snapshot().contentBounds().transpose(), leftRight.snapshot().contentBounds());
        for (SFMHistoryCanvasLayout.Node node : topDown.snapshot().nodes()) {
            SFMHistoryCanvasLayout.Node transposed = node(leftRight, node.stableId());
            assertEquals(node.bounds().transpose(), transposed.bounds());
            assertEquals(node.markerBounds().transpose(), transposed.markerBounds());
            assertEquals(node.labelBounds().transpose(), transposed.labelBounds());
            assertEquals(node.source(), transposed.source());
        }
        for (SFMHistoryCanvasLayout.Edge edge : topDown.snapshot().edges()) {
            SFMHistoryCanvasLayout.Edge transposed = edge(leftRight, edge.stableId());
            assertEquals(edge.points().stream().map(SFMHistoryCanvasLayout.Point::transpose).toList(),
                    transposed.points());
            assertEquals(edge.bounds().transpose(), transposed.bounds());
            assertEquals(edge.source(), transposed.source());
        }
        assertEquals(topDown.snapshot(), leftRight.transpose().snapshot());
        assertEquals(topDown.transcript(), leftRight.transcript());
    }

    @Test
    void longUnicodeLabelsAreBoundedWithoutLosingFullSourceText() {
        String label = "🙂".repeat(180) + " complete semantic label";
        var source = state("state-long", label);
        var result = SFMHistoryCanvasLayoutEngine.layout(presentation(List.of(source), List.of()));
        var node = node(result, source.id());
        var config = SFMHistoryCanvasLayoutEngine.Config.defaults();

        assertTrue(node.label().truncated());
        assertEquals(label, node.label().fullText());
        assertTrue(node.label().lines().size() <= config.maxLabelLines());
        assertTrue(node.label().lines().stream().allMatch(line ->
                line.codePointCount(0, line.length()) <= config.maxLabelCodePointsPerLine()));
        assertTrue(node.bounds().width() <= config.maximumNodeWidth());
        assertTrue(node.bounds().height() <= config.maximumNodeHeight());
        assertEquals(label, node.source().label());
    }

    @Test
    void spatialIndexCullsHitsAndEnforcesExplicitBounds() {
        SFMHistoryCanvasLayoutEngine.Result result = forkResult();
        SFMHistoryCanvasLayout.Node action = node(result, "intent-a");
        var visible = result.visible(action.bounds());

        assertTrue(visible.nodes().stream().anyMatch(node -> node.stableId().equals(action.stableId())));
        assertEquals(action.stableId(), result.hitTest(center(action.bounds()), 0).orElseThrow().subject().stableId());

        SFMHistoryCanvasLayout.Edge edge = edge(result, "edge-root-a");
        SFMHistoryCanvasLayout.Point edgePoint = midpoint(edge.points().get(0), edge.points().get(1));
        assertEquals(edge.stableId(), result.hitTest(edgePoint, 3).orElseThrow().subject().stableId());

        var oneResultLimits = new SFMHistoryCanvasSpatialIndex.Limits(
                96, 64, 1_024, 8_192, 256, 16, 1_024, 1, 24, 64
        );
        var bounded = SFMHistoryCanvasSpatialIndex.build(result.snapshot(), oneResultLimits)
                .query(result.snapshot().contentBounds());
        assertTrue(bounded.truncated());
        assertEquals(1, bounded.nodes().size() + bounded.edges().size());

        var oneSubjectLimits = new SFMHistoryCanvasSpatialIndex.Limits(
                96, 1, 1_024, 8_192, 256, 16, 1_024, 64, 24, 64
        );
        assertThrows(IllegalArgumentException.class,
                () -> SFMHistoryCanvasSpatialIndex.build(result.snapshot(), oneSubjectLimits));
    }

    @Test
    void hoverAndSelectionSurviveTransposeAndPushedRevisionsByStableId() {
        SFMHistoryCanvasLayoutEngine.Result initial = forkResult();
        var hovered = new SFMHistoryCanvasSpatialIndex.Subject(
                SFMHistoryCanvasLayout.SubjectKind.NODE,
                "intent-a"
        );
        var selected = new SFMHistoryCanvasSpatialIndex.Subject(
                SFMHistoryCanvasLayout.SubjectKind.NODE,
                "state-root"
        );
        var state = new SFMHistoryCanvasInteractionState(Optional.of(hovered), Optional.of(selected));

        assertEquals(state, initial.transpose().retainInteraction(state));

        var rootOnly = SFMHistoryCanvasLayoutEngine.layout(presentation(
                List.of(state("state-root", "Empty, refreshed")),
                List.of()
        ));
        var retained = rootOnly.retainInteraction(state);
        assertTrue(retained.hovered().isEmpty());
        assertEquals(Optional.of(selected), retained.selected());
    }

    @Test
    void boundedThousandNodeHistoryUsesIterativeGraphAnalysis() {
        ArrayList<SFMHistoryGraphPresentationModel.Node> nodes = new ArrayList<>();
        ArrayList<SFMHistoryGraphPresentationModel.Edge> edges = new ArrayList<>();
        for (int index = 0; index < 1_000; index++) {
            var node = state(String.format(java.util.Locale.ROOT, "state-%04d", index), "revision " + index);
            nodes.add(node);
            if (index > 0) {
                edges.add(edge(String.format(java.util.Locale.ROOT, "edge-%04d", index), nodes.get(index - 1), node));
            }
        }

        var result = SFMHistoryCanvasLayoutEngine.layout(presentation(nodes, edges));

        assertEquals(999, node(result, "state-0999").rank());
        assertEquals(1_999, result.spatialIndex().stats().subjects());
        assertEquals(1_999, result.transcript().entries().size());
    }

    private static SFMHistoryCanvasLayoutEngine.Result forkResult() {
        var root = state("state-root", "Empty");
        var firstAction = action("intent-a", SFMHistoryGraphPresentationModel.NodeOrigin.ACTION_INTENT, "Type alpha");
        var secondAction = action("intent-b", SFMHistoryGraphPresentationModel.NodeOrigin.ACTION_INTENT, "Type beta");
        var alpha = state("state-alpha", "alpha");
        var beta = state("state-beta", "beta");
        return SFMHistoryCanvasLayoutEngine.layout(presentation(
                List.of(root, firstAction, secondAction, alpha, beta),
                List.of(
                        edge("edge-root-a", root, firstAction),
                        edge("edge-a-alpha", firstAction, alpha),
                        edge("edge-root-b", root, secondAction),
                        edge("edge-b-beta", secondAction, beta)
                )
        ));
    }

    private static SFMHistoryGraphPresentationModel.Presentation presentation(
            List<SFMHistoryGraphPresentationModel.Node> nodes,
            List<SFMHistoryGraphPresentationModel.Edge> edges
    ) {
        return new SFMHistoryGraphPresentationModel.Presentation(
                SFMHistoryGraphPresentationModel.LegendRole.stableLegend(),
                nodes,
                edges,
                List.of(),
                new SFMHistoryGraphPresentationModel.ProjectionSummary(
                        nodes.size(), nodes.size(), edges.size(), edges.size(), 0, 0
                )
        );
    }

    private static SFMHistoryGraphPresentationModel.Node state(String id, String label) {
        return new SFMHistoryGraphPresentationModel.Node(
                id,
                List.of(SFMHistoryGraphPresentationModel.NodeOrigin.HISTORY),
                List.of(SFMHistoryGraphPresentationModel.LegendRole.COMMITTED_EXECUTED),
                label,
                "State " + id + " contains " + label + ".",
                List.of(new SFMHistoryGraphPresentationModel.Detail("history.state-hash", "sha256:" + id))
        );
    }

    private static SFMHistoryGraphPresentationModel.Node action(
            String id,
            SFMHistoryGraphPresentationModel.NodeOrigin origin,
            String label,
            SFMHistoryGraphPresentationModel.Detail... details
    ) {
        return new SFMHistoryGraphPresentationModel.Node(
                id,
                List.of(origin),
                List.of(SFMHistoryGraphPresentationModel.LegendRole.COMMITTED_EXECUTED),
                label,
                "Action evidence for " + id + ".",
                List.of(details)
        );
    }

    private static SFMHistoryGraphPresentationModel.Edge edge(
            String id,
            SFMHistoryGraphPresentationModel.Node from,
            SFMHistoryGraphPresentationModel.Node to
    ) {
        return new SFMHistoryGraphPresentationModel.Edge(
                id,
                "contract:" + id,
                from.id(),
                to.id(),
                SFMHistoryGraphPresentationModel.EdgeOrigin.SEMANTIC,
                SFMHistoryGraphPresentationModel.EdgeCommitment.SEMANTIC_RELATION,
                List.of(),
                "Relation " + id,
                "Directed relation from " + from.id() + " to " + to.id() + ".",
                List.of(new SFMHistoryGraphPresentationModel.Detail("semantic.provenance", "fixture/" + id))
        );
    }

    private static SFMHistoryCanvasLayout.Node node(SFMHistoryCanvasLayoutEngine.Result result, String id) {
        return result.snapshot().nodes().stream().filter(node -> node.stableId().equals(id)).findFirst().orElseThrow();
    }

    private static SFMHistoryCanvasLayout.Edge edge(SFMHistoryCanvasLayoutEngine.Result result, String id) {
        return result.snapshot().edges().stream().filter(edge -> edge.stableId().equals(id)).findFirst().orElseThrow();
    }

    private static SFMHistoryCanvasLayout.Point center(SFMHistoryCanvasLayout.Rect bounds) {
        return new SFMHistoryCanvasLayout.Point(
                bounds.x() + bounds.width() / 2,
                bounds.y() + bounds.height() / 2
        );
    }

    private static SFMHistoryCanvasLayout.Point midpoint(
            SFMHistoryCanvasLayout.Point first,
            SFMHistoryCanvasLayout.Point second
    ) {
        return new SFMHistoryCanvasLayout.Point((first.x() + second.x()) / 2, (first.y() + second.y()) / 2);
    }
}
