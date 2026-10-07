package ca.teamdman.sfm.client.review.release_review;

import ca.teamdman.sfm.client.review.session.SFMReviewSessionV1;
import ca.teamdman.sfm.client.review.session.SFMReviewSessionV2;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Small durable authority. The state envelope must never contain resolved browsing data. */
public record SFMReleaseReviewLedgerV3(
        List<TargetLane> targets, SFMReleaseReviewV1 state, SFMReviewEvidenceTable evidence
) {
    public static final String SCHEMA = "sfm.release-review/3";

    public record TargetLane(String id, String repositoryId, String rootHint, String beforeCommit,
                             Optional<String> afterCommit, List<String> scopePaths,
                             List<String> excludedPaths, boolean includeUntracked) {
        public TargetLane {
            requireText(id); requireText(repositoryId); requireText(rootHint);
            requireCommit(beforeCommit);
            Objects.requireNonNull(afterCommit).ifPresent(SFMReleaseReviewLedgerV3::requireCommit);
            scopePaths = List.copyOf(scopePaths);
            excludedPaths = List.copyOf(excludedPaths);
            if (scopePaths.isEmpty()) throw new IllegalArgumentException("Review target requires a scope");
            scopePaths.forEach(SFMReleaseReviewLedgerV3::requireRelativePath);
            excludedPaths.forEach(SFMReleaseReviewLedgerV3::requireRelativePath);
        }

        public boolean live() { return afterCommit.isEmpty(); }
    }

    public SFMReleaseReviewLedgerV3 {
        targets = List.copyOf(targets);
        if (targets.isEmpty()) throw new IllegalArgumentException("Review ledger requires a target");
        var lanes = new HashSet<String>();
        for (var target : targets) if (!lanes.add(target.id()))
            throw new IllegalArgumentException("Duplicate target lane");
        Objects.requireNonNull(state);
        Objects.requireNonNull(evidence);
        if (!SFMReleaseReviewV1.SCHEMA.equals(state.schema()))
            throw new IllegalArgumentException("Durable ledger state requires the sparse v1 envelope");
        if (!state.repositoryBindings().isEmpty() || !state.corpusDocuments().isEmpty()
                || !state.reviewUnits().isEmpty() || !state.producerGenerations().isEmpty()
                || !state.reviewSession().revisionLanes().isEmpty())
            throw new IllegalArgumentException("Durable ledger cannot contain resolved browsing data");
        if (state.reviewSession().comments().stream().anyMatch(c -> "generated".equals(c.provenance().kind())))
            throw new IllegalArgumentException("Generated change markers are derived, not durable comments");
        var bindings = new java.util.HashMap<String, SFMReleaseReviewV1.CommentSelectorBinding>();
        for (var binding : state.selectorBindings())
            if (bindings.put(binding.commentId(), binding) != null)
                throw new IllegalArgumentException("Duplicate durable comment selector binding");
        for (var comment : state.reviewSession().comments()) {
            var binding = bindings.remove(comment.id());
            if (!(comment.target() instanceof SFMReviewSessionV2.CommittedReviewTarget target)
                    || binding == null || !binding.selectedProposal().selectionRule().equals(target.selectionRule())
                    || !binding.capturedSelection().equals(binding.selectedProposal().literalWitness()))
                throw new IllegalArgumentException("Durable source comment requires its exact selector binding");
            evidence.validateRetained(binding.selectedProposal());
        }
        if (!bindings.isEmpty()) throw new IllegalArgumentException("Orphan durable comment selector binding");
    }

    public static SFMReleaseReviewLedgerV3 create(String id, String title, List<TargetLane> targets) {
        var session = new SFMReviewSessionV2(SFMReviewSessionV2.SCHEMA, id, title,
                SFMReviewSessionV1.COORDINATE_SYSTEM, List.of(), List.of(), List.of(),
                new SFMReviewSessionV1.CompletionPolicy("changed_surface", "#approved", List.of("#needs-change")));
        var state = new SFMReleaseReviewV1(SFMReleaseReviewV1.SCHEMA, session, List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), SFMReleaseReviewV1.ResumeState.empty(), List.of(), List.of());
        return new SFMReleaseReviewLedgerV3(targets, state, SFMReviewEvidenceTable.EMPTY);
    }

    private static void requireCommit(String value) {
        if (value == null || !value.matches("[0-9a-f]{40}"))
            throw new IllegalArgumentException("Review Git target must be an immutable full commit");
    }
    private static void requireText(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Missing review target identity");
    }
    private static void requireRelativePath(String value) {
        requireText(value);
        if (value.equals(".")) return;
        if (value.startsWith("/") || value.contains("\\") || value.contains(":"))
            throw new IllegalArgumentException("Review scope must be repository-relative");
        for (String segment : value.split("/", -1))
            if (segment.isEmpty() || segment.equals(".") || segment.equals(".."))
                throw new IllegalArgumentException("Review scope must be normalized");
    }
}
