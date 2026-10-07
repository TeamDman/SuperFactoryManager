package ca.teamdman.sfm.client.review.session;

import ca.teamdman.sfm.client.history.SFMCandidateHistoryContract;
import ca.teamdman.sfm.client.history.SFMHistoryGraphContract;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SFMCandidateCommentTargetAdapterSeamTests {
    @Test
    void unicodeGlyphWitnessUsesExactHalfOpenUtf8Boundaries() {
        String text = "Aé🙂Z";
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        SFMCandidateHistoryContract.CandidateRouteProjection route = materializedRoute(text);
        SFMCandidateHistoryContract.CandidateFrame frame = route.frame(0);

        SFMReviewSessionV2.CandidateTrajectoryTarget target = SFMCandidateCommentTargetAdapter.capture(
                route,
                frame,
                SFMReviewSessionV2.CandidateTargetKind.DOCUMENT_REGION,
                Optional.of(new SFMCandidateCommentTargetAdapter.Utf8Range(1, 7))
        );

        SFMReviewSessionV2.ProjectedDocumentSelection witness =
                target.projectedDocumentSelection().orElseThrow();
        assertEquals(1, witness.startByte());
        assertEquals(7, witness.endByte());
        assertEquals("é🙂", new String(Arrays.copyOfRange(bytes, 1, 7), StandardCharsets.UTF_8));
        assertEquals(SFMReviewSessionV1Kernel.sha256(bytes), witness.documentTextSha256());
        assertEquals(
                SFMReviewSessionV1Kernel.sha256(Arrays.copyOfRange(bytes, 1, 7)),
                witness.selectedTextSha256()
        );

        IllegalArgumentException splitGlyph = assertThrows(
                IllegalArgumentException.class,
                () -> SFMCandidateCommentTargetAdapter.capture(
                        route,
                        frame,
                        SFMReviewSessionV2.CandidateTargetKind.DOCUMENT_REGION,
                        Optional.of(new SFMCandidateCommentTargetAdapter.Utf8Range(2, 7))
                )
        );
        assertTrue(splitGlyph.getMessage().contains("splits UTF-8"));
        assertThrows(
                IllegalArgumentException.class,
                () -> SFMCandidateCommentTargetAdapter.capture(
                        route,
                        frame,
                        SFMReviewSessionV2.CandidateTargetKind.DOCUMENT_REGION,
                        Optional.of(new SFMCandidateCommentTargetAdapter.Utf8Range(1, bytes.length + 1))
                )
        );
    }

    @Test
    void unavailableProjectionPermitsRouteAndActionTargetsButRejectsGlyphTargets() {
        SFMCandidateHistoryContract.CandidateRouteProjection route = unavailableRoute();
        SFMCandidateHistoryContract.CandidateFrame unavailable = route.frame(1);

        SFMReviewSessionV2.CandidateTrajectoryTarget routeTarget = SFMCandidateCommentTargetAdapter.capture(
                route,
                unavailable,
                SFMReviewSessionV2.CandidateTargetKind.ROUTE,
                Optional.empty()
        );
        SFMReviewSessionV2.CandidateTrajectoryTarget actionTarget = SFMCandidateCommentTargetAdapter.capture(
                route,
                unavailable,
                SFMReviewSessionV2.CandidateTargetKind.ACTION,
                Optional.empty()
        );

        assertEquals(SFMHistoryGraphContract.ProjectionStatus.EXTERNAL_BARRIER, routeTarget.projectionStatus());
        assertEquals(SFMReviewSessionV2.CandidateTargetKind.ROUTE, routeTarget.targetKind());
        assertEquals("intent-step-1", actionTarget.actionIntentId().orElseThrow());
        assertEquals(SFMReviewSessionV2.CandidateTargetKind.ACTION, actionTarget.targetKind());

        IllegalArgumentException unavailableGlyph = assertThrows(
                IllegalArgumentException.class,
                () -> SFMCandidateCommentTargetAdapter.capture(
                        route,
                        unavailable,
                        SFMReviewSessionV2.CandidateTargetKind.DOCUMENT_REGION,
                        Optional.of(new SFMCandidateCommentTargetAdapter.Utf8Range(0, 1))
                )
        );
        assertTrue(unavailableGlyph.getMessage().contains("materialized document bytes"));
    }

    private static SFMCandidateHistoryContract.CandidateRouteProjection materializedRoute(String text) {
        String planId = "plan-unicode";
        String routeId = "route-unicode";
        String stateHash = "state-hash-unicode";
        SFMCandidateHistoryContract.CandidateFrame frame = new SFMCandidateHistoryContract.CandidateFrame(
                new SFMCandidateHistoryContract.CandidateFrameAddress(
                        planId,
                        routeId,
                        0,
                        Optional.empty(),
                        "state-unicode",
                        Optional.of(stateHash),
                        SFMHistoryGraphContract.ProjectionStatus.MATERIALIZED,
                        Optional.of("evaluator-1"),
                        List.of(new SFMCandidateHistoryContract.EvaluatorEvidence("source", "test"))
                ),
                Optional.of(new SFMCandidateHistoryContract.CandidateDocument(
                        "document-unicode",
                        text,
                        stateHash,
                        0
                )),
                Optional.empty(),
                Optional.empty(),
                "Materialized Unicode frame"
        );
        return route(planId, routeId, List.of(frame));
    }

    private static SFMCandidateHistoryContract.CandidateRouteProjection unavailableRoute() {
        String planId = "plan-barrier";
        String routeId = "route-barrier";
        String startHash = "state-hash-start";
        SFMCandidateHistoryContract.CandidateFrame start = new SFMCandidateHistoryContract.CandidateFrame(
                new SFMCandidateHistoryContract.CandidateFrameAddress(
                        planId,
                        routeId,
                        0,
                        Optional.empty(),
                        "state-start",
                        Optional.of(startHash),
                        SFMHistoryGraphContract.ProjectionStatus.MATERIALIZED,
                        Optional.of("evaluator-1"),
                        List.of()
                ),
                Optional.of(new SFMCandidateHistoryContract.CandidateDocument(
                        "document-barrier",
                        "start",
                        startHash,
                        0
                )),
                Optional.empty(),
                Optional.empty(),
                "Materialized route start"
        );
        SFMCandidateHistoryContract.CandidateFrame barrier = new SFMCandidateHistoryContract.CandidateFrame(
                new SFMCandidateHistoryContract.CandidateFrameAddress(
                        planId,
                        routeId,
                        1,
                        Optional.of("step-1"),
                        "state-after-barrier",
                        Optional.empty(),
                        SFMHistoryGraphContract.ProjectionStatus.EXTERNAL_BARRIER,
                        Optional.of("evaluator-1"),
                        List.of(new SFMCandidateHistoryContract.EvaluatorEvidence("barrier", "external"))
                ),
                Optional.empty(),
                Optional.of("intent-step-1"),
                Optional.of("state-start"),
                "Projection stopped at an external barrier"
        );
        return route(planId, routeId, List.of(start, barrier));
    }

    private static SFMCandidateHistoryContract.CandidateRouteProjection route(
            String planId,
            String routeId,
            List<SFMCandidateHistoryContract.CandidateFrame> frames
    ) {
        return new SFMCandidateHistoryContract.CandidateRouteProjection(
                SFMCandidateHistoryContract.SCHEMA,
                "sfm:test/candidate-comment-adapter",
                4,
                "history-head-1",
                Optional.of(planId),
                Optional.empty(),
                planId,
                routeId,
                frames
        );
    }
}
