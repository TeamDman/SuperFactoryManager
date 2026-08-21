package ca.teamdman.sfm.client.review.session;

import ca.teamdman.sfm.client.history.SFMHistoryGraphContract;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SFMReviewSessionPromotionIdentitySeamTests {
    private static final String LOGICAL_DOCUMENT_ID = "document-shared";

    @Test
    void exactPromotionsOfDistinctSnapshotsOfOneLogicalDocumentDoNotCollide() {
        Snapshot first = snapshot("first", "alpha\n");
        Snapshot second = snapshot("second", "alpha\nbeta\n");
        SFMReviewSessionV2 session = session(first.candidate(), second.candidate());

        SFMReviewSessionV2Kernel.PromotionResult firstPromotion = SFMReviewSessionV2Kernel.promoteExact(
                session,
                first.candidate().id(),
                first.witness("decision-first")
        );
        assertEquals(SFMReviewSessionV2Kernel.PromotionStatus.PROMOTED_EXACTLY, firstPromotion.status());

        SFMReviewSessionV2Kernel.PromotionResult secondPromotion = SFMReviewSessionV2Kernel.promoteExact(
                firstPromotion.session(),
                second.candidate().id(),
                second.witness("decision-second")
        );
        assertEquals(SFMReviewSessionV2Kernel.PromotionStatus.PROMOTED_EXACTLY, secondPromotion.status());

        SFMReviewSessionV2.Comment firstChild = secondPromotion.session().comments().stream()
                .filter(comment -> comment.id().equals(firstPromotion.promotedCommentId().orElseThrow()))
                .findFirst()
                .orElseThrow();
        SFMReviewSessionV2.Comment secondChild = secondPromotion.session().comments().stream()
                .filter(comment -> comment.id().equals(secondPromotion.promotedCommentId().orElseThrow()))
                .findFirst()
                .orElseThrow();
        SFMReviewSessionV1.LiteralUtf8Range firstRule = literalRule(firstChild);
        SFMReviewSessionV1.LiteralUtf8Range secondRule = literalRule(secondChild);
        assertTrue(promotionLink(firstChild).correspondenceEvidence().isEmpty());
        assertTrue(promotionLink(secondChild).correspondenceEvidence().isEmpty());

        assertNotEquals(LOGICAL_DOCUMENT_ID, firstRule.documentRevisionId(),
                "logical document identity must not be reused as a snapshot revision id");
        assertNotEquals(LOGICAL_DOCUMENT_ID, secondRule.documentRevisionId());
        assertNotEquals(firstRule.documentRevisionId(), secondRule.documentRevisionId(),
                "distinct content snapshots require distinct committed revision identities");

        List<SFMReviewSessionV1.DocumentRevision> revisions = secondPromotion.session().revisionLanes().stream()
                .flatMap(lane -> lane.after().documents().stream())
                .toList();
        assertEquals(2, revisions.size());
        assertEquals(
                Set.of(first.revision().id(), second.revision().id()),
                revisions.stream().map(SFMReviewSessionV1.DocumentRevision::id).collect(Collectors.toSet())
        );
        assertEquals(
                SFMReviewSessionV2Kernel.Status.RESOLVED_EXACTLY,
                SFMReviewSessionV2Kernel.evaluateComment(secondPromotion.session(), firstChild).status()
        );
        assertEquals(
                SFMReviewSessionV2Kernel.Status.RESOLVED_EXACTLY,
                SFMReviewSessionV2Kernel.evaluateComment(secondPromotion.session(), secondChild).status()
        );
        assertEquals(
                secondPromotion.session(),
                SFMReviewSessionV2Codec.parse(SFMReviewSessionV2Codec.write(secondPromotion.session()))
        );
    }

    @Test
    void witnessedMigrationIsExplicitEvidenceBearingAdditiveAndNeverTransfersApproval() {
        Snapshot projected = snapshot("projected", "alpha\n");
        SFMReviewSessionV2.Comment approvalCandidate = new SFMReviewSessionV2.Comment(
                projected.candidate().id(),
                "#approved candidate discussion",
                projected.candidate().provenance(),
                projected.candidate().target()
        );
        SFMReviewSessionV2 session = session(approvalCandidate);
        String migratedText = "prefix alpha\n";
        byte[] migratedBytes = migratedText.getBytes(StandardCharsets.UTF_8);
        String migratedSha256 = SFMReviewSessionV1Kernel.sha256(migratedBytes);
        SFMReviewSessionV1.DocumentRevision migratedRevision = new SFMReviewSessionV1.DocumentRevision(
                "candidate-execution/sfm_test_shared-logical-document/"
                        + LOGICAL_DOCUMENT_ID + "/" + migratedSha256,
                "candidate-execution/sfm_test_shared-logical-document/"
                        + LOGICAL_DOCUMENT_ID + "/" + migratedSha256 + ".txt",
                "utf-8",
                migratedSha256,
                migratedText
        );
        int migratedStartByte = "prefix ".getBytes(StandardCharsets.UTF_8).length;
        int migratedEndByte = migratedStartByte + "alpha".getBytes(StandardCharsets.UTF_8).length;

        SFMReviewSessionV2Kernel.PromotionResult implicitExact = SFMReviewSessionV2Kernel.promoteExact(
                session,
                approvalCandidate.id(),
                new SFMReviewSessionV2Kernel.ExactExecutionWitness(
                        "decision-not-exact",
                        "head-migrated",
                        "state-migrated",
                        "state-hash-migrated",
                        LOGICAL_DOCUMENT_ID,
                        migratedRevision,
                        migratedStartByte,
                        migratedEndByte
                )
        );
        assertEquals(SFMReviewSessionV2Kernel.PromotionStatus.DIVERGED, implicitExact.status());
        assertEquals(session, implicitExact.session(),
                "divergence must not infer witnessed migration or mutate the candidate session");

        assertThrows(
                IllegalArgumentException.class,
                () -> new SFMReviewSessionV2Kernel.WitnessedMigrationDecision(
                        "decision-empty-evidence",
                        "head-migrated",
                        "state-migrated",
                        "state-hash-migrated",
                        LOGICAL_DOCUMENT_ID,
                        migratedRevision,
                        migratedStartByte,
                        migratedEndByte,
                        List.of()
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new SFMReviewSessionV2.CandidatePromotionLink(
                        approvalCandidate.id(),
                        SFMReviewSessionV2Kernel.candidateTargetSha256(
                                (SFMReviewSessionV2.CandidateTrajectoryTarget) approvalCandidate.target()),
                        "decision-empty-link-evidence",
                        "head-migrated",
                        "state-migrated",
                        "state-hash-migrated",
                        "witnessed_migration",
                        List.of()
                ),
                "the persisted promotion link must independently reject an unwitnessed migration"
        );
        SFMReviewSessionV2Kernel.MigrationResult wrongLogicalDocument =
                SFMReviewSessionV2Kernel.migrateWitnessed(
                        session,
                        approvalCandidate.id(),
                        new SFMReviewSessionV2Kernel.WitnessedMigrationDecision(
                                "decision-wrong-document",
                                "head-migrated",
                                "state-migrated",
                                "state-hash-migrated",
                                "another-logical-document",
                                migratedRevision,
                                migratedStartByte,
                                migratedEndByte,
                                List.of("human-confirmed relocation")
                        )
                );
        assertEquals(SFMReviewSessionV2Kernel.MigrationStatus.INVALID_WITNESS,
                wrongLogicalDocument.status());
        assertEquals(session, wrongLogicalDocument.session());
        assertTrue(wrongLogicalDocument.migratedCommentId().isEmpty());

        List<String> evidence = List.of(
                "human-confirmed same logical document",
                "selected text moved by seven UTF-8 bytes"
        );
        SFMReviewSessionV2Kernel.MigrationResult migrated = SFMReviewSessionV2Kernel.migrateWitnessed(
                session,
                approvalCandidate.id(),
                new SFMReviewSessionV2Kernel.WitnessedMigrationDecision(
                        "decision-witnessed",
                        "head-migrated",
                        "state-migrated",
                        "state-hash-migrated",
                        LOGICAL_DOCUMENT_ID,
                        migratedRevision,
                        migratedStartByte,
                        migratedEndByte,
                        evidence
                )
        );
        assertEquals(SFMReviewSessionV2Kernel.MigrationStatus.MIGRATED_WITH_WITNESS, migrated.status());
        assertEquals(2, migrated.session().comments().size());
        assertEquals(approvalCandidate, migrated.session().comments().get(0));
        SFMReviewSessionV2.Comment child = migrated.session().comments().get(1);
        assertEquals("candidate_migration", child.provenance().kind());
        assertEquals(List.of(approvalCandidate.id()), child.provenance().parentCommentIds());
        SFMReviewSessionV2.CandidatePromotionLink link = promotionLink(child);
        assertEquals("witnessed_migration", link.correspondence());
        assertEquals(evidence, link.correspondenceEvidence());
        SFMReviewSessionV2Kernel.Evaluation evaluation =
                SFMReviewSessionV2Kernel.evaluateComment(migrated.session(), child);
        assertEquals(SFMReviewSessionV2Kernel.Status.RESOLVED_EXACTLY, evaluation.status());
        assertFalse(SFMReviewSessionV2Kernel.isApprovalEffective(migrated.session(), child, evaluation));

        SFMReviewSessionV2 reopened = SFMReviewSessionV2Codec.parse(
                SFMReviewSessionV2Codec.write(migrated.session()));
        assertEquals(migrated.session(), reopened);
        assertEquals(evidence, promotionLink(reopened.comments().get(1)).correspondenceEvidence());
    }

    private static Snapshot snapshot(String suffix, String text) {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        String sha256 = SFMReviewSessionV1Kernel.sha256(bytes);
        String stateHash = "state-hash-" + suffix;
        SFMReviewSessionV2.ProjectedDocumentSelection selection =
                new SFMReviewSessionV2.ProjectedDocumentSelection(
                        LOGICAL_DOCUMENT_ID,
                        stateHash,
                        sha256,
                        0,
                        bytes.length,
                        sha256
                );
        SFMReviewSessionV2.Comment candidate = new SFMReviewSessionV2.Comment(
                "candidate-" + suffix,
                "review snapshot " + suffix,
                new SFMReviewSessionV1.Provenance("human", "test", "1", List.of()),
                new SFMReviewSessionV2.CandidateTrajectoryTarget(
                        "sfm:test/shared-logical-document",
                        1,
                        "plan-" + suffix,
                        "route-" + suffix,
                        1,
                        Optional.of("step-" + suffix),
                        "state-" + suffix,
                        Optional.of(stateHash),
                        SFMHistoryGraphContract.ProjectionStatus.MATERIALIZED,
                        SFMReviewSessionV2.CandidateTargetKind.DOCUMENT_REGION,
                        Optional.of("intent-" + suffix),
                        Optional.of(selection),
                        Optional.of("evaluator-1"),
                        List.of()
                )
        );
        String revisionId = "candidate-execution/sfm_test_shared-logical-document/"
                + LOGICAL_DOCUMENT_ID + "/" + sha256;
        SFMReviewSessionV1.DocumentRevision revision = new SFMReviewSessionV1.DocumentRevision(
                revisionId,
                revisionId + ".txt",
                "utf-8",
                sha256,
                text
        );
        return new Snapshot(candidate, stateHash, revision, bytes.length);
    }

    private static SFMReviewSessionV2 session(SFMReviewSessionV2.Comment... comments) {
        SFMReviewSessionV2 empty = SFMReviewSessionV2.empty(
                "sfm:test/shared-logical-document-comments",
                "Shared logical document comments"
        );
        return new SFMReviewSessionV2(
                empty.schema(),
                empty.id(),
                empty.title(),
                empty.coordinateSystem(),
                empty.revisionLanes(),
                List.of(comments),
                empty.styleRules(),
                empty.completionPolicy()
        );
    }

    private static SFMReviewSessionV1.LiteralUtf8Range literalRule(SFMReviewSessionV2.Comment comment) {
        SFMReviewSessionV2.CommittedReviewTarget target =
                (SFMReviewSessionV2.CommittedReviewTarget) comment.target();
        return (SFMReviewSessionV1.LiteralUtf8Range) target.selectionRule();
    }

    private static SFMReviewSessionV2.CandidatePromotionLink promotionLink(
            SFMReviewSessionV2.Comment comment
    ) {
        SFMReviewSessionV2.CommittedReviewTarget target =
                (SFMReviewSessionV2.CommittedReviewTarget) comment.target();
        return target.candidatePromotion().orElseThrow();
    }

    private record Snapshot(
            SFMReviewSessionV2.Comment candidate,
            String stateHash,
            SFMReviewSessionV1.DocumentRevision revision,
            int byteLength
    ) {
        private SFMReviewSessionV2Kernel.ExactExecutionWitness witness(String decisionId) {
            return new SFMReviewSessionV2Kernel.ExactExecutionWitness(
                    decisionId,
                    "head-" + decisionId,
                    "executed-" + stateHash,
                    stateHash,
                    LOGICAL_DOCUMENT_ID,
                    revision,
                    0,
                    byteLength
            );
        }
    }
}
