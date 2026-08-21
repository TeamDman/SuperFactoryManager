package ca.teamdman.sfm.client.review.session;

import ca.teamdman.sfm.client.history.SFMHistoryGraphContract;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Evaluation, exact promotion, and conservative approval rules for review-session v2. */
public final class SFMReviewSessionV2Kernel {
    public static final String EVALUATOR_VERSION = "sfm-review-v2/1";

    public enum Status {
        RESOLVED_EXACTLY,
        RESOLVED_WITH_RELOCATION,
        AMBIGUOUS,
        NO_MATCH,
        INVALID_RULE,
        SCOPE_MISSING,
        CONTENT_CHANGED,
        CANDIDATE_PINNED,
        CANDIDATE_PINNED_UNAVAILABLE
    }

    public record Evaluation(
            String commentId,
            String evaluatorVersion,
            Status status,
            List<SFMReviewSessionV1Kernel.Range> ranges,
            List<String> diagnostics
    ) {
        public Evaluation {
            ranges = List.copyOf(ranges);
            diagnostics = List.copyOf(diagnostics);
        }
    }

    public enum PromotionStatus {
        PROMOTED_EXACTLY,
        DIVERGED,
        MISSING,
        AMBIGUOUS,
        UNSUPPORTED
    }

    public record ExactExecutionWitness(
            String decisionId,
            String executedHistoryHeadId,
            String executedStateId,
            String executedStateHash,
            String executedDocumentId,
            SFMReviewSessionV1.DocumentRevision document,
            int startByte,
            int endByte
    ) {
        public ExactExecutionWitness {
            decisionId = requireText(decisionId, "decisionId");
            executedHistoryHeadId = requireText(executedHistoryHeadId, "executedHistoryHeadId");
            executedStateId = requireText(executedStateId, "executedStateId");
            executedStateHash = requireText(executedStateHash, "executedStateHash");
            executedDocumentId = requireText(executedDocumentId, "executedDocumentId");
            Objects.requireNonNull(document, "document");
            if (startByte < 0 || endByte < startByte) {
                throw new IllegalArgumentException("Execution witness range must be forward and half-open");
            }
        }
    }

    public record PromotionResult(
            PromotionStatus status,
            SFMReviewSessionV2 session,
            Optional<String> promotedCommentId,
            String diagnostic
    ) {
        public PromotionResult {
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(session, "session");
            Objects.requireNonNull(promotedCommentId, "promotedCommentId");
            diagnostic = requireText(diagnostic, "diagnostic");
            if ((status == PromotionStatus.PROMOTED_EXACTLY) != promotedCommentId.isPresent()) {
                throw new IllegalArgumentException("Only exact promotion returns a committed comment id");
            }
        }
    }

    public enum MigrationStatus {
        MIGRATED_WITH_WITNESS,
        MISSING,
        INVALID_WITNESS,
        UNSUPPORTED
    }

    /** Explicit human/tool correspondence for a changed or relocated execution result. */
    public record WitnessedMigrationDecision(
            String decisionId,
            String executedHistoryHeadId,
            String executedStateId,
            String executedStateHash,
            String executedDocumentId,
            SFMReviewSessionV1.DocumentRevision document,
            int startByte,
            int endByte,
            List<String> correspondenceEvidence
    ) {
        public WitnessedMigrationDecision {
            decisionId = requireText(decisionId, "decisionId");
            executedHistoryHeadId = requireText(executedHistoryHeadId, "executedHistoryHeadId");
            executedStateId = requireText(executedStateId, "executedStateId");
            executedStateHash = requireText(executedStateHash, "executedStateHash");
            executedDocumentId = requireText(executedDocumentId, "executedDocumentId");
            Objects.requireNonNull(document, "document");
            if (startByte < 0 || endByte < startByte) {
                throw new IllegalArgumentException("Migration range must be forward and half-open");
            }
            correspondenceEvidence = List.copyOf(correspondenceEvidence).stream()
                    .map(value -> requireText(value, "correspondenceEvidence"))
                    .toList();
            if (correspondenceEvidence.isEmpty()) {
                throw new IllegalArgumentException("Witnessed migration requires explicit correspondence evidence");
            }
        }
    }

    public record MigrationResult(
            MigrationStatus status,
            SFMReviewSessionV2 session,
            Optional<String> migratedCommentId,
            String diagnostic
    ) {
        public MigrationResult {
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(session, "session");
            Objects.requireNonNull(migratedCommentId, "migratedCommentId");
            diagnostic = requireText(diagnostic, "diagnostic");
            if ((status == MigrationStatus.MIGRATED_WITH_WITNESS) != migratedCommentId.isPresent()) {
                throw new IllegalArgumentException("Only witnessed migration returns a committed comment id");
            }
        }
    }

    private SFMReviewSessionV2Kernel() {
    }

    public static List<Evaluation> evaluateAll(SFMReviewSessionV2 session) {
        validateSession(session);
        return session.comments().stream().map(comment -> evaluateComment(session, comment)).toList();
    }

    public static Evaluation evaluateComment(SFMReviewSessionV2 session, SFMReviewSessionV2.Comment comment) {
        validateSession(session);
        Objects.requireNonNull(comment, "comment");
        if (comment.target() instanceof SFMReviewSessionV2.CandidateTrajectoryTarget candidate) {
            Status status = candidate.projectionStatus() == SFMHistoryGraphContract.ProjectionStatus.MATERIALIZED
                    ? Status.CANDIDATE_PINNED
                    : Status.CANDIDATE_PINNED_UNAVAILABLE;
            String diagnostic = candidate.targetKind() == SFMReviewSessionV2.CandidateTargetKind.DOCUMENT_REGION
                    ? "Pinned candidate document witness at " + candidate.canonicalAddress()
                    : "Pinned candidate " + candidate.targetKind().name().toLowerCase(java.util.Locale.ROOT)
                    + " at " + candidate.canonicalAddress();
            return new Evaluation(comment.id(), EVALUATOR_VERSION, status, List.of(), List.of(diagnostic));
        }
        SFMReviewSessionV2.CommittedReviewTarget committed =
                (SFMReviewSessionV2.CommittedReviewTarget) comment.target();
        SFMReviewSessionV1 v1 = asV1(session, comment, committed.selectionRule());
        SFMReviewSessionV1Kernel.Evaluation evaluation = SFMReviewSessionV1Kernel.evaluateAll(v1).get(0);
        return new Evaluation(
                comment.id(),
                EVALUATOR_VERSION,
                Status.valueOf(evaluation.status().name()),
                evaluation.ranges(),
                evaluation.diagnostics()
        );
    }

    /** Candidate and candidate-promoted discussion is never itself effective approval. */
    public static boolean isApprovalEffective(
            SFMReviewSessionV2 session,
            SFMReviewSessionV2.Comment comment,
            Evaluation evaluation
    ) {
        if (!(comment.target() instanceof SFMReviewSessionV2.CommittedReviewTarget committed)) return false;
        if (committed.candidatePromotion().isPresent()) return false;
        SFMReviewSessionV1 v1 = asV1(session, comment, committed.selectionRule());
        SFMReviewSessionV1Kernel.Evaluation v1Evaluation = SFMReviewSessionV1Kernel.evaluateAll(v1).get(0);
        if (!Status.valueOf(v1Evaluation.status().name()).equals(evaluation.status())) return false;
        return SFMReviewSessionV1Kernel.isApprovalEffective(v1, v1.comments().get(0), v1Evaluation);
    }

    public static PromotionResult promoteExact(
            SFMReviewSessionV2 session,
            String candidateCommentId,
            ExactExecutionWitness witness
    ) {
        validateSession(session);
        Objects.requireNonNull(witness, "witness");
        SFMReviewSessionV2.Comment candidateComment = session.comments().stream()
                .filter(comment -> comment.id().equals(candidateCommentId))
                .findFirst()
                .orElse(null);
        if (candidateComment == null) {
            return unchanged(PromotionStatus.MISSING, session, "Candidate comment was not found");
        }
        if (!(candidateComment.target() instanceof SFMReviewSessionV2.CandidateTrajectoryTarget candidate)) {
            return unchanged(PromotionStatus.UNSUPPORTED, session, "Only candidate comments can be promoted");
        }
        SFMReviewSessionV2.ProjectedDocumentSelection projected =
                candidate.projectedDocumentSelection().orElse(null);
        if (projected == null) {
            return unchanged(PromotionStatus.UNSUPPORTED, session,
                    "Route, step, action, and state comments remain candidate discussion");
        }
        if (candidate.predictedStateHash().isEmpty()
                || !candidate.predictedStateHash().orElseThrow().equals(witness.executedStateHash())) {
            return unchanged(PromotionStatus.DIVERGED, session,
                    "Executed state hash does not equal the pinned candidate prediction");
        }
        if (!projected.documentId().equals(witness.executedDocumentId())) {
            return unchanged(PromotionStatus.DIVERGED, session,
                    "Executed document identity does not equal the candidate document identity");
        }

        byte[] bytes = witness.document().text().getBytes(StandardCharsets.UTF_8);
        if (witness.endByte() > bytes.length
                || !isUtf8Boundary(bytes, witness.startByte())
                || !isUtf8Boundary(bytes, witness.endByte())) {
            return unchanged(PromotionStatus.MISSING, session,
                    "Executed selection is outside the UTF-8 document or splits a glyph");
        }
        String actualDocumentHash = SFMReviewSessionV1Kernel.sha256(bytes);
        String actualSelectedHash = SFMReviewSessionV1Kernel.sha256(
                Arrays.copyOfRange(bytes, witness.startByte(), witness.endByte()));
        if (!actualDocumentHash.equals(witness.document().sha256())
                || !actualDocumentHash.equals(projected.documentTextSha256())
                || witness.startByte() != projected.startByte()
                || witness.endByte() != projected.endByte()
                || !actualSelectedHash.equals(projected.selectedTextSha256())) {
            return unchanged(PromotionStatus.DIVERGED, session,
                    "Executed document or selected-byte witness diverged from the candidate");
        }

        String id = uniquePromotionId(session, candidateComment.id(), witness.decisionId());
        SFMReviewSessionV1.LiteralUtf8Range rule = new SFMReviewSessionV1.LiteralUtf8Range(
                witness.document().id(),
                witness.startByte(),
                witness.endByte(),
                actualDocumentHash,
                actualSelectedHash
        );
        SFMReviewSessionV2.CandidatePromotionLink link = new SFMReviewSessionV2.CandidatePromotionLink(
                candidateComment.id(),
                candidateTargetSha256(candidate),
                witness.decisionId(),
                witness.executedHistoryHeadId(),
                witness.executedStateId(),
                witness.executedStateHash(),
                "exact",
                List.of()
        );
        SFMReviewSessionV2.Comment promoted = new SFMReviewSessionV2.Comment(
                id,
                candidateComment.text(),
                new SFMReviewSessionV1.Provenance(
                        "candidate_promotion",
                        "sfm-review-v2",
                        "1",
                        List.of(candidateComment.id())
                ),
                new SFMReviewSessionV2.CommittedReviewTarget(rule, Optional.of(link))
        );
        ArrayList<SFMReviewSessionV2.Comment> comments = new ArrayList<>(session.comments());
        comments.add(promoted);
        SFMReviewSessionV2 updated = new SFMReviewSessionV2(
                session.schema(), session.id(), session.title(), session.coordinateSystem(),
                withCommittedDocument(session.revisionLanes(), candidate.machineId(), witness.document()),
                comments, session.styleRules(), session.completionPolicy()
        );
        return new PromotionResult(
                PromotionStatus.PROMOTED_EXACTLY,
                updated,
                Optional.of(id),
                "Candidate target linked to exact committed bytes; approval remains separate"
        );
    }

    /**
     * Creates an additive committed child only after an explicit witnessed decision.
     * No candidate hash correspondence is inferred, and approval remains ineffective.
     */
    public static MigrationResult migrateWitnessed(
            SFMReviewSessionV2 session,
            String candidateCommentId,
            WitnessedMigrationDecision decision
    ) {
        validateSession(session);
        Objects.requireNonNull(decision, "decision");
        SFMReviewSessionV2.Comment candidateComment = session.comments().stream()
                .filter(comment -> comment.id().equals(candidateCommentId))
                .findFirst()
                .orElse(null);
        if (candidateComment == null) {
            return migrationUnchanged(MigrationStatus.MISSING, session, "Candidate comment was not found");
        }
        if (!(candidateComment.target() instanceof SFMReviewSessionV2.CandidateTrajectoryTarget candidate)
                || candidate.projectedDocumentSelection().isEmpty()) {
            return migrationUnchanged(MigrationStatus.UNSUPPORTED, session,
                    "Only candidate document-region comments can migrate to committed source");
        }
        if (!candidate.projectedDocumentSelection().orElseThrow().documentId()
                .equals(decision.executedDocumentId())) {
            return migrationUnchanged(MigrationStatus.INVALID_WITNESS, session,
                    "Migration decision names a different logical document");
        }
        byte[] bytes = decision.document().text().getBytes(StandardCharsets.UTF_8);
        String actualDocumentHash = SFMReviewSessionV1Kernel.sha256(bytes);
        if (!actualDocumentHash.equals(decision.document().sha256())
                || decision.endByte() > bytes.length
                || !isUtf8Boundary(bytes, decision.startByte())
                || !isUtf8Boundary(bytes, decision.endByte())) {
            return migrationUnchanged(MigrationStatus.INVALID_WITNESS, session,
                    "Migration document hash or UTF-8 range is invalid");
        }
        String selectedHash = SFMReviewSessionV1Kernel.sha256(
                Arrays.copyOfRange(bytes, decision.startByte(), decision.endByte()));
        SFMReviewSessionV1.LiteralUtf8Range rule = new SFMReviewSessionV1.LiteralUtf8Range(
                decision.document().id(),
                decision.startByte(),
                decision.endByte(),
                actualDocumentHash,
                selectedHash
        );
        SFMReviewSessionV2.CandidatePromotionLink link = new SFMReviewSessionV2.CandidatePromotionLink(
                candidateComment.id(),
                candidateTargetSha256(candidate),
                decision.decisionId(),
                decision.executedHistoryHeadId(),
                decision.executedStateId(),
                decision.executedStateHash(),
                "witnessed_migration",
                decision.correspondenceEvidence()
        );
        String id = uniquePromotionId(session, candidateComment.id(), decision.decisionId());
        SFMReviewSessionV2.Comment migrated = new SFMReviewSessionV2.Comment(
                id,
                candidateComment.text(),
                new SFMReviewSessionV1.Provenance(
                        "candidate_migration", "sfm-review-v2", "1", List.of(candidateComment.id())),
                new SFMReviewSessionV2.CommittedReviewTarget(rule, Optional.of(link))
        );
        ArrayList<SFMReviewSessionV2.Comment> comments = new ArrayList<>(session.comments());
        comments.add(migrated);
        SFMReviewSessionV2 updated = new SFMReviewSessionV2(
                session.schema(), session.id(), session.title(), session.coordinateSystem(),
                withCommittedDocument(session.revisionLanes(), candidate.machineId(), decision.document()),
                comments, session.styleRules(), session.completionPolicy()
        );
        return new MigrationResult(
                MigrationStatus.MIGRATED_WITH_WITNESS,
                updated,
                Optional.of(id),
                "Candidate target migrated by explicit witness; approval remains separate"
        );
    }

    public static String candidateTargetSha256(SFMReviewSessionV2.CandidateTrajectoryTarget target) {
        StringBuilder value = new StringBuilder("sfm.candidate-comment-target/1");
        append(value, target.machineId());
        append(value, Long.toString(target.machineRevision()));
        append(value, target.trajectoryPlanRevisionId());
        append(value, target.routeId());
        append(value, Integer.toString(target.routeStepPosition()));
        append(value, target.trajectoryStepId().orElse(""));
        append(value, target.predictedStateId());
        append(value, target.predictedStateHash().orElse(""));
        append(value, target.projectionStatus().name());
        append(value, target.targetKind().name());
        append(value, target.actionIntentId().orElse(""));
        target.projectedDocumentSelection().ifPresent(selection -> {
            append(value, selection.documentId());
            append(value, selection.documentStateHash());
            append(value, selection.documentTextSha256());
            append(value, Integer.toString(selection.startByte()));
            append(value, Integer.toString(selection.endByte()));
            append(value, selection.selectedTextSha256());
        });
        append(value, target.evaluatorRevision().orElse(""));
        target.evaluatorEvidence().forEach(evidence -> {
            append(value, evidence.key());
            append(value, evidence.value());
        });
        return SFMReviewSessionV1Kernel.sha256(value.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static PromotionResult unchanged(
            PromotionStatus status,
            SFMReviewSessionV2 session,
            String diagnostic
    ) {
        return new PromotionResult(status, session, Optional.empty(), diagnostic);
    }

    private static MigrationResult migrationUnchanged(
            MigrationStatus status,
            SFMReviewSessionV2 session,
            String diagnostic
    ) {
        return new MigrationResult(status, session, Optional.empty(), diagnostic);
    }

    private static SFMReviewSessionV1 asV1(
            SFMReviewSessionV2 session,
            SFMReviewSessionV2.Comment comment,
            SFMReviewSessionV1.SelectionRule rule
    ) {
        return new SFMReviewSessionV1(
                SFMReviewSessionV1.SCHEMA,
                session.id(),
                session.title(),
                session.coordinateSystem(),
                session.revisionLanes(),
                List.of(new SFMReviewSessionV1.Comment(comment.id(), comment.text(), comment.provenance(), rule)),
                session.styleRules(),
                session.completionPolicy()
        );
    }

    private static List<SFMReviewSessionV1.RevisionLane> withCommittedDocument(
            List<SFMReviewSessionV1.RevisionLane> lanes,
            String machineId,
            SFMReviewSessionV1.DocumentRevision document
    ) {
        Map<String, SFMReviewSessionV1.DocumentRevision> documents = new LinkedHashMap<>();
        lanes.forEach(lane -> {
            lane.before().documents().forEach(value -> documents.put(value.id(), value));
            lane.after().documents().forEach(value -> documents.put(value.id(), value));
        });
        SFMReviewSessionV1.DocumentRevision existing = documents.get(document.id());
        if (existing != null && !existing.equals(document)) {
            throw new IllegalArgumentException("Committed document id already names different bytes");
        }
        if (existing != null) return lanes;
        ArrayList<SFMReviewSessionV1.RevisionLane> updated = new ArrayList<>(lanes);
        String laneId = "candidate-execution/" + machineId;
        for (int index = 0; index < updated.size(); index++) {
            SFMReviewSessionV1.RevisionLane lane = updated.get(index);
            if (!lane.id().equals(laneId)) continue;
            ArrayList<SFMReviewSessionV1.DocumentRevision> after = new ArrayList<>(lane.after().documents());
            after.add(document);
            updated.set(index, new SFMReviewSessionV1.RevisionLane(
                    lane.id(), lane.repository(), lane.versionLabel(), lane.before(),
                    new SFMReviewSessionV1.Snapshot(lane.after().id(), after)
            ));
            return List.copyOf(updated);
        }
        updated.add(new SFMReviewSessionV1.RevisionLane(
                laneId,
                new SFMReviewSessionV1.Repository("candidate-execution", "candidate-history"),
                null,
                new SFMReviewSessionV1.Snapshot("before", List.of()),
                new SFMReviewSessionV1.Snapshot("after", List.of(document))
        ));
        return List.copyOf(updated);
    }

    private static void validateSession(SFMReviewSessionV2 session) {
        Objects.requireNonNull(session, "session");
        if (!SFMReviewSessionV2.SCHEMA.equals(session.schema())) {
            throw new IllegalArgumentException("Unsupported review-session schema");
        }
        Set<String> documentIds = new java.util.HashSet<>();
        for (SFMReviewSessionV1.RevisionLane lane : session.revisionLanes()) {
            for (SFMReviewSessionV1.DocumentRevision document : lane.before().documents()) {
                validateDocument(document, documentIds);
            }
            for (SFMReviewSessionV1.DocumentRevision document : lane.after().documents()) {
                validateDocument(document, documentIds);
            }
        }
    }

    private static void validateDocument(
            SFMReviewSessionV1.DocumentRevision document,
            Set<String> documentIds
    ) {
        if (!documentIds.add(document.id())) throw new IllegalArgumentException("Duplicate document id " + document.id());
        if (!"utf-8".equals(document.encoding())) throw new IllegalArgumentException("Unsupported encoding " + document.encoding());
        if (document.path().isBlank() || document.path().startsWith("/") || document.path().contains("\\")
                || Arrays.asList(document.path().split("/", -1)).contains("..")) {
            throw new IllegalArgumentException("Document path is not normalized repository-relative: " + document.path());
        }
    }

    private static boolean isUtf8Boundary(byte[] bytes, int offset) {
        return offset >= 0 && offset <= bytes.length
                && (offset == bytes.length || (bytes[offset] & 0xC0) != 0x80);
    }

    private static String uniquePromotionId(SFMReviewSessionV2 session, String sourceId, String decisionId) {
        String base = sourceId + "/committed/" + decisionId;
        Set<String> ids = session.comments().stream().map(SFMReviewSessionV2.Comment::id)
                .collect(java.util.stream.Collectors.toSet());
        if (!ids.contains(base)) return base;
        for (int suffix = 2; ; suffix++) {
            String value = base + "-" + suffix;
            if (!ids.contains(value)) return value;
        }
    }

    private static void append(StringBuilder target, String value) {
        target.append('\n').append(value.length()).append(':').append(value);
    }

    private static String requireText(String value, String label) {
        Objects.requireNonNull(value, label);
        if (value.isBlank()) throw new IllegalArgumentException(label + " must not be blank");
        return value;
    }
}
