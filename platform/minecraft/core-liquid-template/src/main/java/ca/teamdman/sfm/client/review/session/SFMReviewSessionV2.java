package ca.teamdman.sfm.client.review.session;

import ca.teamdman.sfm.client.history.SFMCandidateHistoryContract;
import ca.teamdman.sfm.client.history.SFMHistoryGraphContract;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Canonical review-session model with an explicit committed-or-candidate target union.
 *
 * <p>V1 remains frozen and is migrated losslessly by {@link SFMReviewSessionV2Codec}.
 * Candidate targets retain only identities and witnesses; projected document bytes stay
 * authoritative in the candidate-history subsystem.</p>
 */
public record SFMReviewSessionV2(
        String schema,
        String id,
        String title,
        String coordinateSystem,
        List<SFMReviewSessionV1.RevisionLane> revisionLanes,
        List<Comment> comments,
        List<SFMReviewSessionV1.StyleRule> styleRules,
        SFMReviewSessionV1.CompletionPolicy completionPolicy
) {
    public static final String SCHEMA = "sfm.review-session/2";

    public SFMReviewSessionV2 {
        if (!SCHEMA.equals(schema)) throw new IllegalArgumentException("Unsupported review-session schema " + schema);
        id = requireText(id, "session.id");
        title = requireText(title, "session.title");
        if (!SFMReviewSessionV1.COORDINATE_SYSTEM.equals(coordinateSystem)) {
            throw new IllegalArgumentException("Unsupported coordinate system " + coordinateSystem);
        }
        revisionLanes = copy(revisionLanes, "revision lane");
        comments = copy(comments, "comment");
        styleRules = copy(styleRules, "style rule");
        Objects.requireNonNull(completionPolicy, "completionPolicy");
        ensureUnique(comments.stream().map(Comment::id).toList(), "comment id");
    }

    public record Comment(
            String id,
            String text,
            SFMReviewSessionV1.Provenance provenance,
            CommentTarget target
    ) {
        public Comment {
            id = requireText(id, "comment.id");
            text = requireText(text, "comment.text");
            Objects.requireNonNull(provenance, "provenance");
            Objects.requireNonNull(target, "target");
        }
    }

    public sealed interface CommentTarget permits CommittedReviewTarget, CandidateTrajectoryTarget {
    }

    /** A source-selection target, optionally linked to an explicitly promoted candidate comment. */
    public record CommittedReviewTarget(
            SFMReviewSessionV1.SelectionRule selectionRule,
            Optional<CandidatePromotionLink> candidatePromotion
    ) implements CommentTarget {
        public CommittedReviewTarget {
            Objects.requireNonNull(selectionRule, "selectionRule");
            Objects.requireNonNull(candidatePromotion, "candidatePromotion");
        }

        public CommittedReviewTarget(SFMReviewSessionV1.SelectionRule selectionRule) {
            this(selectionRule, Optional.empty());
        }
    }

    public enum CandidateTargetKind {
        ROUTE,
        STEP,
        ACTION,
        STATE,
        DOCUMENT_REGION
    }

    /**
     * An immutable address into a proposed future. It never means "the current plan".
     */
    public record CandidateTrajectoryTarget(
            String machineId,
            long machineRevision,
            String trajectoryPlanRevisionId,
            String routeId,
            int routeStepPosition,
            Optional<String> trajectoryStepId,
            String predictedStateId,
            Optional<String> predictedStateHash,
            SFMHistoryGraphContract.ProjectionStatus projectionStatus,
            CandidateTargetKind targetKind,
            Optional<String> actionIntentId,
            Optional<ProjectedDocumentSelection> projectedDocumentSelection,
            Optional<String> evaluatorRevision,
            List<SFMCandidateHistoryContract.EvaluatorEvidence> evaluatorEvidence
    ) implements CommentTarget {
        public CandidateTrajectoryTarget {
            machineId = requireText(machineId, "candidate.machineId");
            if (machineRevision < 0) throw new IllegalArgumentException("machineRevision must not be negative");
            trajectoryPlanRevisionId = requireText(trajectoryPlanRevisionId, "candidate.trajectoryPlanRevisionId");
            routeId = requireText(routeId, "candidate.routeId");
            if (routeStepPosition < 0) throw new IllegalArgumentException("routeStepPosition must not be negative");
            trajectoryStepId = optionalText(trajectoryStepId, "candidate.trajectoryStepId");
            predictedStateId = requireText(predictedStateId, "candidate.predictedStateId");
            predictedStateHash = optionalText(predictedStateHash, "candidate.predictedStateHash");
            Objects.requireNonNull(projectionStatus, "projectionStatus");
            Objects.requireNonNull(targetKind, "targetKind");
            actionIntentId = optionalText(actionIntentId, "candidate.actionIntentId");
            Objects.requireNonNull(projectedDocumentSelection, "projectedDocumentSelection");
            evaluatorRevision = optionalText(evaluatorRevision, "candidate.evaluatorRevision");
            evaluatorEvidence = copy(evaluatorEvidence, "evaluator evidence").stream()
                    .sorted(Comparator.comparing(SFMCandidateHistoryContract.EvaluatorEvidence::key))
                    .toList();
            ensureUnique(evaluatorEvidence.stream()
                    .map(SFMCandidateHistoryContract.EvaluatorEvidence::key).toList(), "evaluator-evidence key");

            if (routeStepPosition == 0 && trajectoryStepId.isPresent()) {
                throw new IllegalArgumentException("The route-start target must not name a trajectory step");
            }
            if (routeStepPosition > 0 && trajectoryStepId.isEmpty()) {
                throw new IllegalArgumentException("A post-step candidate target requires its trajectory step id");
            }
            if (targetKind == CandidateTargetKind.ACTION && actionIntentId.isEmpty()) {
                throw new IllegalArgumentException("An action target requires an action-intent id");
            }
            if ((targetKind == CandidateTargetKind.STEP || targetKind == CandidateTargetKind.ACTION)
                    && trajectoryStepId.isEmpty()) {
                throw new IllegalArgumentException("Step and action targets require a trajectory step");
            }
            if (targetKind == CandidateTargetKind.DOCUMENT_REGION) {
                if (projectionStatus != SFMHistoryGraphContract.ProjectionStatus.MATERIALIZED) {
                    throw new IllegalArgumentException("A candidate document region requires a materialized frame");
                }
                if (predictedStateHash.isEmpty() || projectedDocumentSelection.isEmpty()) {
                    throw new IllegalArgumentException("A candidate document region requires state and selection witnesses");
                }
            } else if (projectedDocumentSelection.isPresent()) {
                throw new IllegalArgumentException("Only a document-region target may carry a projected selection");
            }
        }

        public static CandidateTrajectoryTarget fromFrame(
                SFMCandidateHistoryContract.CandidateRouteProjection route,
                SFMCandidateHistoryContract.CandidateFrame frame,
                CandidateTargetKind targetKind,
                Optional<ProjectedDocumentSelection> projectedDocumentSelection
        ) {
            Objects.requireNonNull(route, "route");
            Objects.requireNonNull(frame, "frame");
            SFMCandidateHistoryContract.CandidateFrameAddress address = frame.address();
            if (!route.trajectoryPlanRevisionId().equals(address.trajectoryPlanRevisionId())
                    || !route.routeId().equals(address.routeId())) {
                throw new IllegalArgumentException("Candidate frame does not belong to the supplied route");
            }
            return new CandidateTrajectoryTarget(
                    route.machineId(),
                    route.machineRevision(),
                    address.trajectoryPlanRevisionId(),
                    address.routeId(),
                    address.routeStepPosition(),
                    address.trajectoryStepId(),
                    address.predictedStateId(),
                    address.predictedStateHash(),
                    address.projectionStatus(),
                    targetKind,
                    frame.actionIntentId(),
                    projectedDocumentSelection,
                    address.evaluatorRevision(),
                    address.evaluatorEvidence()
            );
        }

        public String canonicalAddress() {
            return trajectoryPlanRevisionId + "#" + routeId + "@" + routeStepPosition;
        }
    }

    /** Exact UTF-8 witness into projected bytes without duplicating those bytes in the comment store. */
    public record ProjectedDocumentSelection(
            String documentId,
            String documentStateHash,
            String documentTextSha256,
            int startByte,
            int endByte,
            String selectedTextSha256
    ) {
        public ProjectedDocumentSelection {
            documentId = requireText(documentId, "projectedSelection.documentId");
            documentStateHash = requireText(documentStateHash, "projectedSelection.documentStateHash");
            documentTextSha256 = requireSha256(documentTextSha256, "projectedSelection.documentTextSha256");
            if (startByte < 0 || endByte < startByte) {
                throw new IllegalArgumentException("Projected selection must be a forward half-open range");
            }
            selectedTextSha256 = requireSha256(selectedTextSha256, "projectedSelection.selectedTextSha256");
        }
    }

    /** Evidence retained on the committed child created by an explicit exact promotion. */
    public record CandidatePromotionLink(
            String sourceCandidateCommentId,
            String sourceCandidateTargetSha256,
            String decisionId,
            String executedHistoryHeadId,
            String executedStateId,
            String executedStateHash,
            String correspondence,
            List<String> correspondenceEvidence
    ) {
        public CandidatePromotionLink {
            sourceCandidateCommentId = requireText(sourceCandidateCommentId, "promotion.sourceCandidateCommentId");
            sourceCandidateTargetSha256 = requireSha256(
                    sourceCandidateTargetSha256,
                    "promotion.sourceCandidateTargetSha256"
            );
            decisionId = requireText(decisionId, "promotion.decisionId");
            executedHistoryHeadId = requireText(executedHistoryHeadId, "promotion.executedHistoryHeadId");
            executedStateId = requireText(executedStateId, "promotion.executedStateId");
            executedStateHash = requireText(executedStateHash, "promotion.executedStateHash");
            correspondence = requireText(correspondence, "promotion.correspondence");
            correspondenceEvidence = copy(correspondenceEvidence, "correspondence evidence").stream()
                    .map(value -> requireText(value, "promotion.correspondenceEvidence"))
                    .toList();
            if (!"exact".equals(correspondence) && !"witnessed_migration".equals(correspondence)) {
                throw new IllegalArgumentException("Unsupported candidate correspondence " + correspondence);
            }
            if ("witnessed_migration".equals(correspondence) && correspondenceEvidence.isEmpty()) {
                throw new IllegalArgumentException("Witnessed migration requires correspondence evidence");
            }
        }
    }

    public static SFMReviewSessionV2 empty(String id, String title) {
        return new SFMReviewSessionV2(
                SCHEMA,
                id,
                title,
                SFMReviewSessionV1.COORDINATE_SYSTEM,
                List.of(),
                List.of(),
                List.of(),
                new SFMReviewSessionV1.CompletionPolicy(
                        "changed_surface",
                        "#approved",
                        List.of("#problem", "#needs-change")
                )
        );
    }

    private static String requireText(String value, String label) {
        Objects.requireNonNull(value, label);
        if (value.isBlank()) throw new IllegalArgumentException(label + " must not be blank");
        return value;
    }

    private static String requireSha256(String value, String label) {
        value = requireText(value, label);
        if (!value.matches("[0-9a-f]{64}")) throw new IllegalArgumentException(label + " must be lowercase SHA-256");
        return value;
    }

    private static Optional<String> optionalText(Optional<String> value, String label) {
        Objects.requireNonNull(value, label);
        return value.map(item -> requireText(item, label));
    }

    private static <T> List<T> copy(List<T> values, String label) {
        Objects.requireNonNull(values, label + " list");
        return values.stream().map(value -> Objects.requireNonNull(value, label)).toList();
    }

    private static void ensureUnique(List<String> values, String label) {
        if (new HashSet<>(values).size() != values.size()) {
            throw new IllegalArgumentException("Duplicate " + label);
        }
    }
}
