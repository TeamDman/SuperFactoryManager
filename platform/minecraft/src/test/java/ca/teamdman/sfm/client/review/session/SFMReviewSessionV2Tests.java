package ca.teamdman.sfm.client.review.session;

import ca.teamdman.sfm.client.history.SFMCandidateHistoryContract;
import ca.teamdman.sfm.client.history.SFMHistoryGraphContract;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SFMReviewSessionV2Tests {
    @Test
    void canonicalV2FixtureRoundTripsByteForByte() throws Exception {
        String canonical = Files.readString(fixtureV2Path()).replace("\r\n", "\n");
        SFMReviewSessionV2 parsed = SFMReviewSessionV2Codec.parse(canonical);
        assertEquals(canonical, SFMReviewSessionV2Codec.write(parsed));
        SFMReviewSessionV2.CommittedReviewTarget promoted = (SFMReviewSessionV2.CommittedReviewTarget)
                parsed.comments().get(parsed.comments().size() - 1).target();
        assertEquals("exact", promoted.candidatePromotion().orElseThrow().correspondence());
        assertTrue(promoted.candidatePromotion().orElseThrow().correspondenceEvidence().isEmpty());
        assertNotEquals(
                ((SFMReviewSessionV2.CandidateTrajectoryTarget) parsed.comments().get(3).target())
                        .projectedDocumentSelection().orElseThrow().documentId(),
                ((SFMReviewSessionV1.LiteralUtf8Range) promoted.selectionRule()).documentRevisionId(),
                "logical document and committed revision identities must remain distinct"
        );
    }

    @Test
    void everyCandidateGranularityRoundTripsWithImmutableAddressEvidence() {
        SFMReviewSessionV2 session = withComments(
                candidate("route", SFMReviewSessionV2.CandidateTargetKind.ROUTE, 0, Optional.empty(), Optional.empty(),
                        SFMHistoryGraphContract.ProjectionStatus.MATERIALIZED),
                candidate("step", SFMReviewSessionV2.CandidateTargetKind.STEP, 1, Optional.of("step-1"), Optional.empty(),
                        SFMHistoryGraphContract.ProjectionStatus.MATERIALIZED),
                candidate("action", SFMReviewSessionV2.CandidateTargetKind.ACTION, 1, Optional.of("step-1"), Optional.empty(),
                        SFMHistoryGraphContract.ProjectionStatus.MATERIALIZED),
                candidate("state", SFMReviewSessionV2.CandidateTargetKind.STATE, 1, Optional.of("step-1"), Optional.empty(),
                        SFMHistoryGraphContract.ProjectionStatus.MATERIALIZED),
                candidate("glyph", SFMReviewSessionV2.CandidateTargetKind.DOCUMENT_REGION, 1, Optional.of("step-1"),
                        Optional.of(selection("é")), SFMHistoryGraphContract.ProjectionStatus.MATERIALIZED)
        );

        String canonical = SFMReviewSessionV2Codec.write(session);
        assertEquals(session, SFMReviewSessionV2Codec.parse(canonical));
        assertEquals(canonical, SFMReviewSessionV2Codec.write(SFMReviewSessionV2Codec.parse(canonical)));
        assertTrue(canonical.contains("\"target_kind\": \"document_region\""));
        assertTrue(canonical.contains("\"start_byte\": 0"));
        assertTrue(canonical.contains("\"end_byte\": 2"), "UTF-8 witness must count é as two bytes");
    }

    @Test
    void frozenV1ImportsWithoutChangingItsCanonicalContract(@TempDir Path directory) throws Exception {
        String v1Json = Files.readString(fixturePath());
        SFMReviewSessionV1 v1 = SFMReviewSessionV1Codec.parse(v1Json);
        String unchangedV1 = SFMReviewSessionV1Codec.write(v1);

        SFMReviewSessionV2 migrated = SFMReviewSessionV2Codec.parseOrMigrate(v1Json);
        assertEquals(3, migrated.comments().size());
        assertTrue(migrated.comments().stream().allMatch(comment ->
                comment.target() instanceof SFMReviewSessionV2.CommittedReviewTarget));
        assertEquals(unchangedV1, SFMReviewSessionV1Codec.write(v1));

        Path storePath = directory.resolve("session.json");
        Files.writeString(storePath, v1Json);
        SFMReviewSessionStore store = new SFMReviewSessionStore(storePath);
        SFMReviewSessionStore.LoadResult loaded = store.load();
        assertTrue(loaded.migratedV1());
        store.save(loaded.session().orElseThrow());
        assertTrue(Files.readString(storePath).contains("\"schema\": \"sfm.review-session/2\""));
    }

    @Test
    void exactPromotionIsAdditivePinnedAndNeverTransfersApproval() {
        String text = "é\n";
        SFMReviewSessionV2.Comment candidate = candidate(
                "candidate-approved",
                SFMReviewSessionV2.CandidateTargetKind.DOCUMENT_REGION,
                1,
                Optional.of("step-1"),
                Optional.of(selection(text)),
                SFMHistoryGraphContract.ProjectionStatus.MATERIALIZED,
                "#approved Candidate discussion"
        );
        SFMReviewSessionV2 session = withComments(candidate);
        SFMReviewSessionV1.DocumentRevision committed = document("document", text);
        SFMReviewSessionV2Kernel.ExactExecutionWitness witness = new SFMReviewSessionV2Kernel.ExactExecutionWitness(
                "human-link-1", "head-1", "state-committed", "state-hash", "document", committed, 0,
                text.getBytes(StandardCharsets.UTF_8).length
        );

        SFMReviewSessionV2Kernel.PromotionResult result =
                SFMReviewSessionV2Kernel.promoteExact(session, candidate.id(), witness);
        assertEquals(SFMReviewSessionV2Kernel.PromotionStatus.PROMOTED_EXACTLY, result.status());
        assertEquals(2, result.session().comments().size());
        assertEquals(candidate, result.session().comments().get(0), "candidate origin must remain unchanged");
        SFMReviewSessionV2.Comment promoted = result.session().comments().get(1);
        assertTrue(promoted.target() instanceof SFMReviewSessionV2.CommittedReviewTarget);
        assertEquals(List.of(candidate.id()), promoted.provenance().parentCommentIds());
        SFMReviewSessionV2Kernel.Evaluation evaluation =
                SFMReviewSessionV2Kernel.evaluateComment(result.session(), promoted);
        assertEquals(SFMReviewSessionV2Kernel.Status.RESOLVED_EXACTLY, evaluation.status());
        assertFalse(SFMReviewSessionV2Kernel.isApprovalEffective(result.session(), promoted, evaluation),
                "promotion is not an approval decision even when candidate text contains #approved");

        String persisted = SFMReviewSessionV2Codec.write(result.session());
        SFMReviewSessionV2 reopened = SFMReviewSessionV2Codec.parse(persisted);
        assertEquals(result.session(), reopened);
        var link = ((SFMReviewSessionV2.CommittedReviewTarget) reopened.comments().get(1).target())
                .candidatePromotion().orElseThrow();
        assertEquals("exact", link.correspondence());
        assertEquals(SFMReviewSessionV2Kernel.candidateTargetSha256(
                (SFMReviewSessionV2.CandidateTrajectoryTarget) candidate.target()),
                link.sourceCandidateTargetSha256());
    }

    @Test
    void divergenceCreatesNothingAndUnavailableActionsRemainCommentable() {
        SFMReviewSessionV2.Comment glyph = candidate(
                "glyph", SFMReviewSessionV2.CandidateTargetKind.DOCUMENT_REGION, 1, Optional.of("step-1"),
                Optional.of(selection("hello")), SFMHistoryGraphContract.ProjectionStatus.MATERIALIZED);
        SFMReviewSessionV2.Comment unavailableAction = candidate(
                "barrier-action", SFMReviewSessionV2.CandidateTargetKind.ACTION, 1, Optional.of("step-1"),
                Optional.empty(), SFMHistoryGraphContract.ProjectionStatus.EXTERNAL_BARRIER);
        SFMReviewSessionV2 session = withComments(glyph, unavailableAction);

        SFMReviewSessionV2Kernel.PromotionResult divergent = SFMReviewSessionV2Kernel.promoteExact(
                session,
                glyph.id(),
                new SFMReviewSessionV2Kernel.ExactExecutionWitness(
                        "decision", "head", "state", "different-state-hash", "document",
                        document("document", "hello"), 0, 5)
        );
        assertEquals(SFMReviewSessionV2Kernel.PromotionStatus.DIVERGED, divergent.status());
        assertEquals(session, divergent.session());
        assertTrue(divergent.promotedCommentId().isEmpty());
        assertEquals(SFMReviewSessionV2Kernel.Status.CANDIDATE_PINNED_UNAVAILABLE,
                SFMReviewSessionV2Kernel.evaluateComment(session, unavailableAction).status());
        assertFalse(SFMReviewSessionV2Kernel.isApprovalEffective(
                session, unavailableAction,
                SFMReviewSessionV2Kernel.evaluateComment(session, unavailableAction)));
    }

    @Test
    void witnessedMigrationIsExplicitAdditiveAndNeverTransfersApproval() {
        SFMReviewSessionV2.Comment glyph = candidate(
                "glyph-migrate", SFMReviewSessionV2.CandidateTargetKind.DOCUMENT_REGION, 1,
                Optional.of("step-1"), Optional.of(selection("hello")),
                SFMHistoryGraphContract.ProjectionStatus.MATERIALIZED,
                "#approved Candidate discussion"
        );
        SFMReviewSessionV2 session = withComments(glyph);
        String changed = "well hello there";
        SFMReviewSessionV1.DocumentRevision committed = document("document@changed", changed);
        SFMReviewSessionV2Kernel.WitnessedMigrationDecision decision =
                new SFMReviewSessionV2Kernel.WitnessedMigrationDecision(
                        "human-migration-1", "head-changed", "state-changed", "changed-state-hash",
                        "document", committed, 5, 10,
                        List.of("Human confirmed that relocated bytes still denote the reviewed greeting")
                );

        SFMReviewSessionV2Kernel.MigrationResult result =
                SFMReviewSessionV2Kernel.migrateWitnessed(session, glyph.id(), decision);

        assertEquals(SFMReviewSessionV2Kernel.MigrationStatus.MIGRATED_WITH_WITNESS, result.status());
        assertEquals(glyph, result.session().comments().get(0));
        SFMReviewSessionV2.Comment migrated = result.session().comments().get(1);
        SFMReviewSessionV2.CommittedReviewTarget target =
                (SFMReviewSessionV2.CommittedReviewTarget) migrated.target();
        SFMReviewSessionV2.CandidatePromotionLink link = target.candidatePromotion().orElseThrow();
        assertEquals("witnessed_migration", link.correspondence());
        assertEquals(decision.correspondenceEvidence(), link.correspondenceEvidence());
        assertEquals("candidate_migration", migrated.provenance().kind());
        SFMReviewSessionV2Kernel.Evaluation evaluation =
                SFMReviewSessionV2Kernel.evaluateComment(result.session(), migrated);
        assertEquals(SFMReviewSessionV2Kernel.Status.RESOLVED_EXACTLY, evaluation.status());
        assertFalse(SFMReviewSessionV2Kernel.isApprovalEffective(result.session(), migrated, evaluation));
        assertEquals(result.session(), SFMReviewSessionV2Codec.parse(
                SFMReviewSessionV2Codec.write(result.session())));

        assertThrows(IllegalArgumentException.class, () ->
                new SFMReviewSessionV2Kernel.WitnessedMigrationDecision(
                        "missing-evidence", "head", "state", "hash", "document", committed,
                        5, 10, List.of()));
        SFMReviewSessionV2Kernel.MigrationResult wrongDocument =
                SFMReviewSessionV2Kernel.migrateWitnessed(
                        session,
                        glyph.id(),
                        new SFMReviewSessionV2Kernel.WitnessedMigrationDecision(
                                "wrong-document", "head", "state", "hash", "other-document", committed,
                                5, 10, List.of("Explicit but incorrect logical identity"))
                );
        assertEquals(SFMReviewSessionV2Kernel.MigrationStatus.INVALID_WITNESS, wrongDocument.status());
        assertEquals(session, wrongDocument.session());
    }

    @Test
    void replanIdentityDoesNotRetargetPersistedCandidate() {
        SFMReviewSessionV2.Comment old = candidate(
                "old", SFMReviewSessionV2.CandidateTargetKind.STATE, 1, Optional.of("step-1"), Optional.empty(),
                SFMHistoryGraphContract.ProjectionStatus.MATERIALIZED);
        SFMReviewSessionV2 session = withComments(old);
        String before = SFMReviewSessionV2Kernel.candidateTargetSha256(
                (SFMReviewSessionV2.CandidateTrajectoryTarget) old.target());
        SFMReviewSessionV2.Comment newer = candidate(
                "new", "plan-2", SFMReviewSessionV2.CandidateTargetKind.STATE, 1, Optional.of("step-2"),
                Optional.empty(), SFMHistoryGraphContract.ProjectionStatus.MATERIALIZED, "new plan");
        SFMReviewSessionV2 extended = withComments(old, newer);
        String after = SFMReviewSessionV2Kernel.candidateTargetSha256(
                (SFMReviewSessionV2.CandidateTrajectoryTarget) extended.comments().get(0).target());
        assertEquals(before, after);
        assertNotEquals(
                ((SFMReviewSessionV2.CandidateTrajectoryTarget) old.target()).trajectoryPlanRevisionId(),
                ((SFMReviewSessionV2.CandidateTrajectoryTarget) newer.target()).trajectoryPlanRevisionId());
    }

    private static SFMReviewSessionV2.Comment candidate(
            String id,
            SFMReviewSessionV2.CandidateTargetKind kind,
            int position,
            Optional<String> step,
            Optional<SFMReviewSessionV2.ProjectedDocumentSelection> selection,
            SFMHistoryGraphContract.ProjectionStatus status
    ) {
        return candidate(id, "plan-1", kind, position, step, selection, status, "comment " + id);
    }

    private static SFMReviewSessionV2.Comment candidate(
            String id,
            SFMReviewSessionV2.CandidateTargetKind kind,
            int position,
            Optional<String> step,
            Optional<SFMReviewSessionV2.ProjectedDocumentSelection> selection,
            SFMHistoryGraphContract.ProjectionStatus status,
            String text
    ) {
        return candidate(id, "plan-1", kind, position, step, selection, status, text);
    }

    private static SFMReviewSessionV2.Comment candidate(
            String id,
            String planId,
            SFMReviewSessionV2.CandidateTargetKind kind,
            int position,
            Optional<String> step,
            Optional<SFMReviewSessionV2.ProjectedDocumentSelection> selection,
            SFMHistoryGraphContract.ProjectionStatus status,
            String text
    ) {
        return new SFMReviewSessionV2.Comment(
                id,
                text,
                new SFMReviewSessionV1.Provenance("human", "test", "1", List.of()),
                new SFMReviewSessionV2.CandidateTrajectoryTarget(
                        "sfm:test/machine",
                        7,
                        planId,
                        "route-1",
                        position,
                        step,
                        position == 0 ? "state-start" : "state-" + position,
                        Optional.of("state-hash"),
                        status,
                        kind,
                        kind == SFMReviewSessionV2.CandidateTargetKind.ACTION
                                ? Optional.of("action-intent") : Optional.empty(),
                        selection,
                        Optional.of("evaluator-1"),
                        List.of(new SFMCandidateHistoryContract.EvaluatorEvidence("witness", "exact"))
                )
        );
    }

    private static SFMReviewSessionV2.ProjectedDocumentSelection selection(String text) {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        return new SFMReviewSessionV2.ProjectedDocumentSelection(
                "document",
                "state-hash",
                SFMReviewSessionV1Kernel.sha256(bytes),
                0,
                bytes.length,
                SFMReviewSessionV1Kernel.sha256(bytes)
        );
    }

    private static SFMReviewSessionV1.DocumentRevision document(String id, String text) {
        return new SFMReviewSessionV1.DocumentRevision(
                id, "candidate/Document.txt", "utf-8",
                SFMReviewSessionV1Kernel.sha256(text.getBytes(StandardCharsets.UTF_8)), text);
    }

    private static SFMReviewSessionV2 withComments(SFMReviewSessionV2.Comment... comments) {
        SFMReviewSessionV2 empty = SFMReviewSessionV2.empty("sfm:test/candidate-comments", "Candidate comments");
        return new SFMReviewSessionV2(
                empty.schema(), empty.id(), empty.title(), empty.coordinateSystem(), empty.revisionLanes(),
                List.of(comments), empty.styleRules(), empty.completionPolicy());
    }

    private static Path fixturePath() {
        Path cursor = Path.of("").toAbsolutePath();
        for (int depth = 0; depth < 8 && cursor != null; depth++, cursor = cursor.getParent()) {
            Path candidate = cursor.resolve("docs/architecture/fixtures/review-comment-session-v1.json");
            if (Files.isRegularFile(candidate)) return candidate;
        }
        throw new IllegalStateException("Unable to locate canonical review-comment fixture");
    }

    private static Path fixtureV2Path() {
        Path cursor = Path.of("").toAbsolutePath();
        for (int depth = 0; depth < 8 && cursor != null; depth++, cursor = cursor.getParent()) {
            Path candidate = cursor.resolve("docs/architecture/fixtures/review-comment-session-v2.json");
            if (Files.isRegularFile(candidate)) return candidate;
        }
        throw new IllegalStateException("Unable to locate canonical review-comment v2 fixture");
    }
}
