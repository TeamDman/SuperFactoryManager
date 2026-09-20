package ca.teamdman.sfm.client.review.session;

import ca.teamdman.sfm.client.history.SFMCandidateHistoryContract;
import ca.teamdman.sfm.client.history.SFMHistoryGraphRuntime;
import ca.teamdman.sfm.client.history.SFMTrajectoryContract;
import ca.teamdman.sfm.client.history.chamber.SFMDecimalNumberingTrajectoryController;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SFMReviewSessionRuntimeCandidateSeamTests {
    @Test
    void canonicalRuntimePersistsReloadsAndPromotesOnlyExactExecutedBytes(@TempDir Path directory) throws Exception {
        PlannedChamber fixture = plannedChamber("exact-promotion");
        Path storePath = directory.resolve("candidate-comments.json");
        Function<String, SFMReviewSessionStore> stores = ignored -> new SFMReviewSessionStore(storePath);
        SFMReviewSessionRuntime runtime = new SFMReviewSessionRuntime(fixture.historyRuntime(), stores);
        SFMCandidateHistoryContract.CandidateFrame finalFrame =
                fixture.projection().frame(fixture.projection().lastPosition());
        String projectedText = finalFrame.document().orElseThrow().text();
        int startByte = projectedText.getBytes(StandardCharsets.UTF_8).length
                - "bananas\n".getBytes(StandardCharsets.UTF_8).length;
        int endByte = startByte + "bananas".getBytes(StandardCharsets.UTF_8).length;

        String candidateId = runtime.createCandidateComment(
                fixture.projection(),
                finalFrame,
                SFMReviewSessionV2.CandidateTargetKind.DOCUMENT_REGION,
                Optional.of(new SFMCandidateCommentTargetAdapter.Utf8Range(startByte, endByte)),
                "#approved inspect the projected fruit"
        );
        SFMReviewSessionV2 pinnedSession = runtime.session(fixture.machineId());
        String persistedBeforeDivergence = Files.readString(storePath);

        SFMReviewSessionV2Kernel.PromotionResult stateDivergence =
                runtime.promoteExact(fixture.machineId(), candidateId, "decision-too-early");
        assertEquals(SFMReviewSessionV2Kernel.PromotionStatus.DIVERGED, stateDivergence.status());
        assertEquals(pinnedSession, stateDivergence.session());
        assertEquals(persistedBeforeDivergence, Files.readString(storePath),
                "a rejected runtime promotion must not mutate canonical storage");

        SFMReviewSessionV2.Comment candidate = runtime.comment(fixture.machineId(), candidateId).orElseThrow();
        SFMReviewSessionV2.CandidateTrajectoryTarget target =
                (SFMReviewSessionV2.CandidateTrajectoryTarget) candidate.target();
        SFMReviewSessionV2.ProjectedDocumentSelection selection =
                target.projectedDocumentSelection().orElseThrow();
        SFMReviewSessionV1.DocumentRevision projectedRevision = new SFMReviewSessionV1.DocumentRevision(
                selection.documentId(),
                "candidate/projected-document.txt",
                "utf-8",
                selection.documentTextSha256(),
                projectedText
        );
        SFMReviewSessionV2Kernel.PromotionResult rangeDivergence = SFMReviewSessionV2Kernel.promoteExact(
                pinnedSession,
                candidateId,
                new SFMReviewSessionV2Kernel.ExactExecutionWitness(
                        "decision-wrong-range",
                        "projected-head",
                        target.predictedStateId(),
                        target.predictedStateHash().orElseThrow(),
                        selection.documentId(),
                        projectedRevision,
                        startByte + 1,
                        endByte
                )
        );
        assertEquals(SFMReviewSessionV2Kernel.PromotionStatus.DIVERGED, rangeDivergence.status());
        assertEquals(pinnedSession, rangeDivergence.session());
        assertTrue(rangeDivergence.promotedCommentId().isEmpty());

        assertEquals(
                SFMHistoryGraphRuntime.OperationStatus.APPLIED,
                fixture.controller().apply(new SFMHistoryGraphRuntime.Run(8)).status()
        );
        assertEquals(projectedText, fixture.controller().currentText());
        assertEquals(
                target.predictedStateHash().orElseThrow(),
                fixture.controller().currentState().stateHash()
        );

        SFMReviewSessionV2Kernel.PromotionResult promoted =
                runtime.promoteExact(fixture.machineId(), candidateId, "decision-exact");
        assertEquals(SFMReviewSessionV2Kernel.PromotionStatus.PROMOTED_EXACTLY, promoted.status());
        assertEquals(2, promoted.session().comments().size());
        assertEquals(candidate, promoted.session().comments().get(0),
                "explicit promotion must preserve the immutable candidate parent");

        SFMReviewSessionV2.Comment committedChild = promoted.session().comments().get(1);
        assertEquals(List.of(candidateId), committedChild.provenance().parentCommentIds());
        SFMReviewSessionV2.CommittedReviewTarget committedTarget =
                (SFMReviewSessionV2.CommittedReviewTarget) committedChild.target();
        SFMReviewSessionV2.CandidatePromotionLink link = committedTarget.candidatePromotion().orElseThrow();
        assertEquals(candidateId, link.sourceCandidateCommentId());
        assertEquals("decision-exact", link.decisionId());
        assertEquals("exact", link.correspondence());
        assertTrue(link.correspondenceEvidence().isEmpty());
        assertEquals(
                SFMReviewSessionV2Kernel.candidateTargetSha256(target),
                link.sourceCandidateTargetSha256()
        );
        SFMReviewSessionV2Kernel.Evaluation childEvaluation =
                SFMReviewSessionV2Kernel.evaluateComment(promoted.session(), committedChild);
        assertEquals(SFMReviewSessionV2Kernel.Status.RESOLVED_EXACTLY, childEvaluation.status());
        assertFalse(SFMReviewSessionV2Kernel.isApprovalEffective(
                promoted.session(), committedChild, childEvaluation),
                "candidate approval text must not become an approval through promotion"
        );

        SFMReviewSessionRuntime reopened = new SFMReviewSessionRuntime(fixture.historyRuntime(), stores);
        SFMReviewSessionStore.LoadResult loaded = reopened.reload(fixture.machineId());
        assertTrue(loaded.session().isPresent());
        assertFalse(loaded.recoveredLastValid());
        assertEquals(promoted.session(), reopened.session(fixture.machineId()));
        assertEquals(SFMReviewSessionV2Codec.write(promoted.session()), Files.readString(storePath));
    }

    @Test
    void witnessedMigrationUsesCurrentCommittedBytesAndPersistsItsExplicitEvidence(@TempDir Path directory)
            throws Exception {
        PlannedChamber fixture = plannedChamber("witnessed-migration");
        Path storePath = directory.resolve("candidate-comments.json");
        Function<String, SFMReviewSessionStore> stores = ignored -> new SFMReviewSessionStore(storePath);
        SFMReviewSessionRuntime runtime = new SFMReviewSessionRuntime(fixture.historyRuntime(), stores);
        SFMCandidateHistoryContract.CandidateFrame finalFrame =
                fixture.projection().frame(fixture.projection().lastPosition());
        String projectedText = finalFrame.document().orElseThrow().text();
        int projectedStartByte = projectedText.getBytes(StandardCharsets.UTF_8).length
                - "bananas\n".getBytes(StandardCharsets.UTF_8).length;
        int projectedEndByte = projectedStartByte + "bananas".getBytes(StandardCharsets.UTF_8).length;

        String candidateId = runtime.createCandidateComment(
                fixture.projection(),
                finalFrame,
                SFMReviewSessionV2.CandidateTargetKind.DOCUMENT_REGION,
                Optional.of(new SFMCandidateCommentTargetAdapter.Utf8Range(
                        projectedStartByte,
                        projectedEndByte
                )),
                "#approved inspect the projected fruit after witnessed migration"
        );
        SFMReviewSessionV2 before = runtime.session(fixture.machineId());
        SFMReviewSessionV2.CandidateTrajectoryTarget candidateTarget =
                (SFMReviewSessionV2.CandidateTrajectoryTarget) before.comments().get(0).target();
        SFMReviewSessionV2.ProjectedDocumentSelection projectedSelection =
                candidateTarget.projectedDocumentSelection().orElseThrow();
        String committedText = fixture.controller().currentText();
        byte[] committedBytes = committedText.getBytes(StandardCharsets.UTF_8);
        int committedStartByte = committedText.indexOf("bananas");
        int committedEndByte = committedStartByte + "bananas".getBytes(StandardCharsets.UTF_8).length;
        assertNotEquals(
                projectedSelection.documentTextSha256(),
                SFMReviewSessionV1Kernel.sha256(committedBytes),
                "the runtime seam must exercise explicit migration rather than exact promotion"
        );
        List<String> evidence = List.of(
                "human-confirmed same logical document",
                "projected fruit corresponds to the unnumbered committed fruit"
        );

        SFMReviewSessionV2Kernel.MigrationResult migrated = runtime.migrateWitnessed(
                fixture.machineId(),
                candidateId,
                committedStartByte,
                committedEndByte,
                "decision-runtime-witnessed",
                evidence
        );
        assertEquals(SFMReviewSessionV2Kernel.MigrationStatus.MIGRATED_WITH_WITNESS, migrated.status());
        assertEquals(2, migrated.session().comments().size());
        assertEquals(before.comments().get(0), migrated.session().comments().get(0),
                "migration must preserve the immutable candidate parent");

        SFMReviewSessionV2.Comment committedChild = migrated.session().comments().get(1);
        assertEquals(List.of(candidateId), committedChild.provenance().parentCommentIds());
        assertEquals("candidate_migration", committedChild.provenance().kind());
        SFMReviewSessionV2.CommittedReviewTarget committedTarget =
                (SFMReviewSessionV2.CommittedReviewTarget) committedChild.target();
        SFMReviewSessionV2.CandidatePromotionLink link = committedTarget.candidatePromotion().orElseThrow();
        assertEquals("witnessed_migration", link.correspondence());
        assertEquals(evidence, link.correspondenceEvidence());
        assertEquals("decision-runtime-witnessed", link.decisionId());
        assertEquals(
                SFMReviewSessionV2Kernel.Status.RESOLVED_EXACTLY,
                SFMReviewSessionV2Kernel.evaluateComment(migrated.session(), committedChild).status()
        );
        assertFalse(SFMReviewSessionV2Kernel.isApprovalEffective(
                migrated.session(),
                committedChild,
                SFMReviewSessionV2Kernel.evaluateComment(migrated.session(), committedChild)
        ), "witnessed migration must not transfer candidate approval");

        SFMReviewSessionRuntime reopened = new SFMReviewSessionRuntime(fixture.historyRuntime(), stores);
        SFMReviewSessionStore.LoadResult loaded = reopened.reload(fixture.machineId());
        assertTrue(loaded.session().isPresent());
        assertFalse(loaded.recoveredLastValid());
        assertEquals(migrated.session(), reopened.session(fixture.machineId()));
        SFMReviewSessionV2.Comment reopenedChild = reopened.session(fixture.machineId()).comments().get(1);
        SFMReviewSessionV2.CandidatePromotionLink reopenedLink =
                ((SFMReviewSessionV2.CommittedReviewTarget) reopenedChild.target())
                        .candidatePromotion()
                        .orElseThrow();
        assertEquals(evidence, reopenedLink.correspondenceEvidence());
        assertEquals(SFMReviewSessionV2Codec.write(migrated.session()), Files.readString(storePath));
    }

    @Test
    void replanPreservesTheOldCandidateTargetIdentityAndItsCanonicalStore(@TempDir Path directory) throws Exception {
        PlannedChamber fixture = plannedChamber("replan-retention");
        Path storePath = directory.resolve("candidate-comments.json");
        Function<String, SFMReviewSessionStore> stores = ignored -> new SFMReviewSessionStore(storePath);
        SFMReviewSessionRuntime runtime = new SFMReviewSessionRuntime(fixture.historyRuntime(), stores);
        SFMCandidateHistoryContract.CandidateFrame pinnedFrame = fixture.projection().frame(1);

        String commentId = runtime.createCandidateComment(
                fixture.projection(),
                pinnedFrame,
                SFMReviewSessionV2.CandidateTargetKind.STATE,
                Optional.empty(),
                "retain this old-plan state"
        );
        SFMReviewSessionV2.CandidateTrajectoryTarget before =
                (SFMReviewSessionV2.CandidateTrajectoryTarget) runtime.comment(
                        fixture.machineId(), commentId).orElseThrow().target();
        String targetHashBefore = SFMReviewSessionV2Kernel.candidateTargetSha256(before);
        String canonicalBefore = Files.readString(storePath);

        assertEquals(
                SFMHistoryGraphRuntime.OperationStatus.APPLIED,
                fixture.controller().apply(new SFMHistoryGraphRuntime.Replan()).status()
        );
        String currentPlan = fixture.controller().snapshot().machine()
                .selectedTrajectoryRevisionId().orElseThrow();
        assertNotEquals(fixture.planId(), currentPlan);
        assertEquals(
                fixture.projection().frames(),
                fixture.controller().projectCandidateRoute(fixture.planId(), fixture.routeId()).frames(),
                "the chamber must retain the old plan's candidate frames"
        );

        SFMReviewSessionRuntime reopened = new SFMReviewSessionRuntime(fixture.historyRuntime(), stores);
        SFMReviewSessionV2.CandidateTrajectoryTarget after =
                (SFMReviewSessionV2.CandidateTrajectoryTarget) reopened.comment(
                        fixture.machineId(), commentId).orElseThrow().target();
        assertEquals(before, after);
        assertEquals(targetHashBefore, SFMReviewSessionV2Kernel.candidateTargetSha256(after));
        assertEquals(fixture.planId(), after.trajectoryPlanRevisionId());
        assertEquals(fixture.routeId(), after.routeId());
        assertEquals(canonicalBefore, Files.readString(storePath),
                "replanning must not rewrite an unrelated persisted review session");
        assertEquals(
                List.of(commentId),
                reopened.commentsForFrame(
                                fixture.machineId(),
                                fixture.planId(),
                                fixture.routeId(),
                                pinnedFrame.address().routeStepPosition())
                        .stream()
                        .map(SFMReviewSessionV2.Comment::id)
                        .toList()
        );
    }

    private static PlannedChamber plannedChamber(String suffix) {
        String machineId = "sfm:test/candidate-comment-" + suffix;
        SFMHistoryGraphRuntime historyRuntime = new SFMHistoryGraphRuntime();
        SFMDecimalNumberingTrajectoryController controller = new SFMDecimalNumberingTrajectoryController(
                machineId,
                "document-1",
                SFMDecimalNumberingTrajectoryController.INITIAL_TEXT,
                () -> "sha256:test-owned-ambient-checkout"
        );
        historyRuntime.register(controller);
        assertEquals(
                SFMHistoryGraphRuntime.OperationStatus.APPLIED,
                controller.apply(new SFMHistoryGraphRuntime.Plan()).status()
        );
        String planId = controller.snapshot().machine().selectedTrajectoryRevisionId().orElseThrow();
        SFMTrajectoryContract.TrajectoryPlanRevision plan = controller.snapshot().planBook().plans().stream()
                .filter(value -> value.id().equals(planId))
                .findFirst()
                .orElseThrow();
        String routeId = plan.selectedRouteId().orElseThrow();
        SFMCandidateHistoryContract.CandidateRouteProjection projection =
                controller.projectCandidateRoute(planId, routeId);
        return new PlannedChamber(machineId, historyRuntime, controller, planId, routeId, projection);
    }

    private record PlannedChamber(
            String machineId,
            SFMHistoryGraphRuntime historyRuntime,
            SFMDecimalNumberingTrajectoryController controller,
            String planId,
            String routeId,
            SFMCandidateHistoryContract.CandidateRouteProjection projection
    ) {
    }
}
