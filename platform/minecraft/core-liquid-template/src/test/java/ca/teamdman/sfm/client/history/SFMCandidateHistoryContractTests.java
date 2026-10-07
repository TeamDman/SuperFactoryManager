package ca.teamdman.sfm.client.history;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SFMCandidateHistoryContractTests {
    @Test
    void candidateAddressPinsPlanRoutePositionHashStatusAndSortedEvaluatorEvidence() {
        SFMCandidateHistoryContract.CandidateFrameAddress address = address(
                1,
                SFMHistoryGraphContract.ProjectionStatus.MATERIALIZED,
                Optional.of("sha256:state"),
                List.of(
                        new SFMCandidateHistoryContract.EvaluatorEvidence("z", "last"),
                        new SFMCandidateHistoryContract.EvaluatorEvidence("a", "first")
                )
        );

        assertEquals(List.of("a", "z"), address.evaluatorEvidence().stream()
                .map(SFMCandidateHistoryContract.EvaluatorEvidence::key)
                .toList());
        assertTrue(address.canonical().contains("sfm:test/plan#sfm:test/route@1"));
        assertTrue(address.canonical().contains("status=materialized"));
    }

    @Test
    void unavailableAndBarrierFramesCannotFabricateDocumentBytes() {
        for (SFMHistoryGraphContract.ProjectionStatus status : List.of(
                SFMHistoryGraphContract.ProjectionStatus.QUEUED,
                SFMHistoryGraphContract.ProjectionStatus.CONFLICT,
                SFMHistoryGraphContract.ProjectionStatus.EXTERNAL_BARRIER,
                SFMHistoryGraphContract.ProjectionStatus.UNKNOWN
        )) {
            SFMCandidateHistoryContract.CandidateFrameAddress address = address(
                    1,
                    status,
                    Optional.of("sha256:predicted"),
                    List.of()
            );
            assertThrows(IllegalArgumentException.class, () -> new SFMCandidateHistoryContract.CandidateFrame(
                    address,
                    Optional.of(document("sha256:predicted")),
                    Optional.of("sfm:test/action"),
                    Optional.of("sfm:test/predecessor"),
                    "unavailable"
            ));
            SFMCandidateHistoryContract.CandidateFrame unavailable =
                    new SFMCandidateHistoryContract.CandidateFrame(
                            address,
                            Optional.empty(),
                            Optional.of("sfm:test/action"),
                            Optional.of("sfm:test/predecessor"),
                            "unavailable"
                    );
            assertTrue(unavailable.document().isEmpty());
        }
    }

    @Test
    void routeRequiresContiguousPinnedFrameAddresses() {
        SFMCandidateHistoryContract.CandidateFrame start = materialized(0, Optional.empty(), "sha256:start");
        SFMCandidateHistoryContract.CandidateFrame after = materialized(
                1,
                Optional.of("sfm:test/step-1"),
                "sha256:after"
        );
        SFMCandidateHistoryContract.CandidateRouteProjection route =
                new SFMCandidateHistoryContract.CandidateRouteProjection(
                        SFMCandidateHistoryContract.SCHEMA,
                        "sfm:test/machine",
                        4,
                        "sfm:test/head",
                        Optional.of("sfm:test/plan"),
                        Optional.empty(),
                        "sfm:test/plan",
                        "sfm:test/route",
                        List.of(start, after)
                );
        assertEquals(1, route.lastPosition());
        assertEquals(after, route.frame(1));

        SFMCandidateHistoryContract.CandidateFrame gap = materialized(
                2,
                Optional.of("sfm:test/step-2"),
                "sha256:gap"
        );
        assertThrows(IllegalArgumentException.class, () ->
                new SFMCandidateHistoryContract.CandidateRouteProjection(
                        SFMCandidateHistoryContract.SCHEMA,
                        "sfm:test/machine",
                        4,
                        "sfm:test/head",
                        Optional.empty(),
                        Optional.empty(),
                        "sfm:test/plan",
                        "sfm:test/route",
                        List.of(start, gap)
                ));
    }

    private static SFMCandidateHistoryContract.CandidateFrame materialized(
            int position,
            Optional<String> step,
            String hash
    ) {
        return new SFMCandidateHistoryContract.CandidateFrame(
                address(position, SFMHistoryGraphContract.ProjectionStatus.MATERIALIZED,
                        Optional.of(hash), List.of()),
                Optional.of(document(hash)),
                position == 0 ? Optional.empty() : Optional.of("sfm:test/action"),
                position == 0 ? Optional.empty() : Optional.of("sfm:test/start"),
                "materialized"
        );
    }

    private static SFMCandidateHistoryContract.CandidateFrameAddress address(
            int position,
            SFMHistoryGraphContract.ProjectionStatus status,
            Optional<String> hash,
            List<SFMCandidateHistoryContract.EvaluatorEvidence> evidence
    ) {
        return new SFMCandidateHistoryContract.CandidateFrameAddress(
                "sfm:test/plan",
                "sfm:test/route",
                position,
                position == 0 ? Optional.empty() : Optional.of("sfm:test/step-" + position),
                "sfm:test/state-" + position,
                hash,
                status,
                Optional.of("sfm:test/evaluator-v1"),
                evidence
        );
    }

    private static SFMCandidateHistoryContract.CandidateDocument document(String hash) {
        return new SFMCandidateHistoryContract.CandidateDocument(
                "sfm:test/document",
                "candidate",
                hash,
                0
        );
    }
}
