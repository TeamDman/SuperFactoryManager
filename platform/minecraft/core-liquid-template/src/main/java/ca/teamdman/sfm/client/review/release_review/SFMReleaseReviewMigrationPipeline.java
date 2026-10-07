package ca.teamdman.sfm.client.review.release_review;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeSet;

/**
 * Production RCS-3/RCS-4 bridge from prepared evaluator evidence to durable
 * migration reports.
 *
 * <p>The bridge is deliberately pure. It evaluates every durable selector
 * binding against an explicit source/candidate transition, retains every
 * terminal result, and never selects an ambiguous candidate. Existing human
 * decisions survive only while all evidence that justified the decision is
 * byte-for-byte unchanged.</p>
 */
public final class SFMReleaseReviewMigrationPipeline {
    private static final Comparator<SFMReleaseReviewV1.AddressedRange> RANGE_ORDER = Comparator
            .comparing(SFMReleaseReviewV1.AddressedRange::documentRevisionId)
            .thenComparingInt(SFMReleaseReviewV1.AddressedRange::startByte)
            .thenComparingInt(SFMReleaseReviewV1.AddressedRange::endByte);

    private SFMReleaseReviewMigrationPipeline() {
    }

    public record Transition(
            SFMReleaseReviewV1.SnapshotSide sourceSide,
            SFMReleaseReviewV1.SnapshotSide candidateSide
    ) {
        public Transition {
            Objects.requireNonNull(sourceSide, "sourceSide");
            Objects.requireNonNull(candidateSide, "candidateSide");
            if (sourceSide == candidateSide) {
                throw new IllegalArgumentException("Migration source and candidate sides must differ");
            }
        }

        public static Transition beforeToAfter() {
            return new Transition(
                    SFMReleaseReviewV1.SnapshotSide.BEFORE,
                    SFMReleaseReviewV1.SnapshotSide.AFTER
            );
        }
    }

    public record Result(
            SFMReleaseReviewV1 document,
            SFMReleaseReviewEvaluator.Work work,
            List<String> diagnostics,
            List<CommentEvaluation> commentEvaluations,
            List<MigrationExplanation> explanations
    ) {
        public Result {
            Objects.requireNonNull(document, "document");
            Objects.requireNonNull(work, "work");
            diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics"));
            commentEvaluations = List.copyOf(Objects.requireNonNull(commentEvaluations, "commentEvaluations"));
            explanations = List.copyOf(Objects.requireNonNull(explanations, "explanations"));
        }
    }

    /** One source comment's inspectable terminal state on both sides of a migration transition. */
    public record CommentEvaluation(
            String commentId,
            String selectorId,
            String migrationReportId,
            SFMReleaseReviewV1.EvaluationResult sourceEvaluation,
            SFMReleaseReviewV1.EvaluationResult candidateEvaluation
    ) {
        public CommentEvaluation {
            commentId = requireText(commentId, "comment id");
            selectorId = requireText(selectorId, "selector id");
            migrationReportId = requireText(migrationReportId, "migration report id");
            Objects.requireNonNull(sourceEvaluation, "sourceEvaluation");
            Objects.requireNonNull(candidateEvaluation, "candidateEvaluation");
        }
    }

    /**
     * Display-ready migration evidence. The selector itself remains available so a
     * presentation cannot accidentally omit its literal witness, semantic authority,
     * confidence, or projection identity while showing old/new migration witnesses.
     */
    public record MigrationExplanation(
            String migrationId,
            String commentId,
            SFMReleaseReviewV1.SelectorProposal selector,
            List<SFMReleaseReviewV1.PinnedSelectionRange> oldWitnesses,
            List<SFMReleaseReviewV1.AddressedRange> newWitnesses,
            SFMReleaseReviewV1.EvaluationResult sourceEvaluation,
            SFMReleaseReviewV1.EvaluationResult candidateEvaluation,
            SFMReleaseReviewV1.MigrationDecision decision,
            List<String> displayLines
    ) {
        public MigrationExplanation {
            migrationId = requireText(migrationId, "migration id");
            commentId = requireText(commentId, "comment id");
            Objects.requireNonNull(selector, "selector");
            oldWitnesses = List.copyOf(Objects.requireNonNull(oldWitnesses, "oldWitnesses"));
            newWitnesses = List.copyOf(Objects.requireNonNull(newWitnesses, "newWitnesses"));
            Objects.requireNonNull(sourceEvaluation, "sourceEvaluation");
            Objects.requireNonNull(candidateEvaluation, "candidateEvaluation");
            Objects.requireNonNull(decision, "decision");
            displayLines = List.copyOf(Objects.requireNonNull(displayLines, "displayLines"));
        }
    }

    public static Result rebuild(
            SFMReleaseReviewV1 review,
            SFMReleaseReviewEvaluator.PreparedEvidence preparedEvidence,
            SFMReleaseReviewEvaluator.Limits limits,
            Transition transition
    ) {
        Objects.requireNonNull(review, "review");
        Objects.requireNonNull(preparedEvidence, "preparedEvidence");
        Objects.requireNonNull(limits, "limits");
        Objects.requireNonNull(transition, "transition");
        SFMReleaseReviewKernel.validate(review);

        SFMReleaseReviewEvaluator.Index index = SFMReleaseReviewEvaluator.Index.build(
                review, preparedEvidence, limits);
        Map<String, SFMReleaseReviewV1.CorpusDocument> corpusByRevision = new HashMap<>();
        review.corpusDocuments().forEach(value -> corpusByRevision.put(value.documentRevisionId(), value));
        Map<String, String> languagesByRevision = languagesByRevision(review);
        Map<String, SFMReleaseReviewV1.MigrationReport> previousById = new HashMap<>();
        review.migrationReports().forEach(value -> previousById.put(value.id(), value));

        ArrayList<SFMReleaseReviewV1.MigrationReport> reports = new ArrayList<>();
        ArrayList<String> diagnostics = new ArrayList<>();
        ArrayList<CommentEvaluation> commentEvaluations = new ArrayList<>();
        ArrayList<MigrationExplanation> explanations = new ArrayList<>();
        long documentsVisited = 0;
        long candidatesVisited = 0;
        long comparedBytes = 0;
        long operations = 0;

        for (SFMReleaseReviewV1.CommentSelectorBinding binding : review.selectorBindings()) {
            BoundScopes scopes = scopes(binding, corpusByRevision, languagesByRevision, transition);
            diagnostics.addAll(scopes.diagnostics());
            SFMReleaseReviewEvaluator.Evaluation source = index.evaluate(
                    binding.selectedProposal().id(), binding.selectedProposal(), scopes.source());
            SFMReleaseReviewEvaluator.Evaluation candidate = index.evaluate(
                    binding.selectedProposal().id(), binding.selectedProposal(), scopes.candidate());
            documentsVisited += source.work().documentsVisited() + candidate.work().documentsVisited();
            candidatesVisited += source.work().candidatesVisited() + candidate.work().candidatesVisited();
            comparedBytes += source.work().comparedBytes() + candidate.work().comparedBytes();
            operations += source.work().operations() + candidate.work().operations();

            String reportId = reportId(binding, transition);
            List<SFMReleaseReviewV1.AddressedRange> newCandidates = migrationCandidates(candidate.result());
            SFMReleaseReviewV1.MigrationReport rebuilt = new SFMReleaseReviewV1.MigrationReport(
                    reportId,
                    binding.selectedProposal().id(),
                    source.result(),
                    candidate.result(),
                    binding.capturedSelection().ranges(),
                    newCandidates,
                    SFMReleaseReviewV1.MigrationDecision.UNRESOLVED,
                    Optional.empty()
            );
            SFMReleaseReviewV1.MigrationReport previous = previousById.get(reportId);
            if (previous != null && sameEvidence(previous, rebuilt)) {
                rebuilt = new SFMReleaseReviewV1.MigrationReport(
                        rebuilt.id(), rebuilt.sourceSelectorId(), rebuilt.sourceEvaluation(),
                        rebuilt.candidateEvaluation(), rebuilt.oldWitnesses(), rebuilt.newCandidates(),
                        previous.decision(), previous.decisionCommentId()
                );
            } else if (previous != null && previous.decision()
                    != SFMReleaseReviewV1.MigrationDecision.UNRESOLVED) {
                diagnostics.add("review.migration-decision-invalidated: " + reportId);
            }
            reports.add(rebuilt);
            commentEvaluations.add(new CommentEvaluation(
                    binding.commentId(), binding.selectedProposal().id(), rebuilt.id(),
                    rebuilt.sourceEvaluation(), rebuilt.candidateEvaluation()
            ));
            explanations.add(explain(binding, rebuilt));
        }

        TreeSet<String> rebuiltIds = new TreeSet<>();
        reports.forEach(value -> rebuiltIds.add(value.id()));
        review.migrationReports().stream()
                .map(SFMReleaseReviewV1.MigrationReport::id)
                .filter(id -> !rebuiltIds.contains(id))
                .sorted()
                .forEach(id -> diagnostics.add("review.migration-report-retired: " + id));

        SFMReleaseReviewV1 rebuiltDocument = new SFMReleaseReviewV1(
                review.schema(), review.reviewSession(), review.repositoryBindings(), review.corpusDocuments(),
                review.reviewUnits(), review.selectorBindings(), reports, review.namedQueries(),
                review.resumeState(), review.producerGenerations(), review.completionAttestations()
        );
        return new Result(
                rebuiltDocument,
                new SFMReleaseReviewEvaluator.Work(
                        documentsVisited, candidatesVisited, comparedBytes, operations),
                diagnostics.stream().distinct().sorted().toList(),
                commentEvaluations,
                explanations
        );
    }

    private static MigrationExplanation explain(
            SFMReleaseReviewV1.CommentSelectorBinding binding,
            SFMReleaseReviewV1.MigrationReport report
    ) {
        SFMReleaseReviewV1.SelectorProposal selector = binding.selectedProposal();
        ArrayList<String> lines = new ArrayList<>();
        lines.add("selector-id=" + selector.id());
        lines.add("selector-kind=" + selector.kind().name().toLowerCase(java.util.Locale.ROOT));
        selector.semanticProvider().ifPresent(value -> lines.add("semantic-provider=" + value));
        selector.semanticKey().ifPresent(value -> lines.add("semantic-key=" + value));
        lines.add("confidence=" + selector.confidence().name().toLowerCase(java.util.Locale.ROOT));
        lines.add("projection-fingerprint=" + selector.projectionFingerprint());
        lines.add("source-snapshot=" + selector.sourceSnapshotId());
        for (int index = 0; index < selector.literalWitness().ranges().size(); index++) {
            SFMReleaseReviewV1.PinnedSelectionRange witness = selector.literalWitness().ranges().get(index);
            lines.add("literal-witness[" + index + "]=" + pinnedWitness(witness));
        }
        for (SFMReleaseReviewV1.Evidence evidence : selector.semanticProvenance()) {
            lines.add("provenance[" + evidence.key() + "]=" + evidence.value());
        }
        selector.diagnostics().forEach(value -> lines.add("selector-diagnostic=" + value));
        for (int index = 0; index < report.oldWitnesses().size(); index++) {
            lines.add("old-witness[" + index + "]=" + pinnedWitness(report.oldWitnesses().get(index)));
        }
        for (int index = 0; index < report.newCandidates().size(); index++) {
            lines.add("new-witness[" + index + "]=" + addressedWitness(report.newCandidates().get(index)));
        }
        appendEvaluation(lines, "source", report.sourceEvaluation());
        appendEvaluation(lines, "candidate", report.candidateEvaluation());
        lines.add("decision=" + report.decision().name().toLowerCase(java.util.Locale.ROOT));
        return new MigrationExplanation(
                report.id(), binding.commentId(), selector, report.oldWitnesses(), report.newCandidates(),
                report.sourceEvaluation(), report.candidateEvaluation(), report.decision(), lines
        );
    }

    private static void appendEvaluation(
            List<String> lines,
            String side,
            SFMReleaseReviewV1.EvaluationResult evaluation
    ) {
        lines.add(side + "-status=" + evaluation.status().name().toLowerCase(java.util.Locale.ROOT));
        for (SFMReleaseReviewV1.InvalidationKey key : evaluation.invalidationKeys()) {
            lines.add(side + "-provenance=" + key.owner() + "|" + key.generation() + "|" + key.fingerprint());
        }
        evaluation.diagnostics().forEach(value -> lines.add(side + "-diagnostic=" + value));
    }

    private static String pinnedWitness(SFMReleaseReviewV1.PinnedSelectionRange range) {
        return range.direction().name().toLowerCase(java.util.Locale.ROOT) + "|"
                + range.documentRevisionId() + "|" + range.documentSha256() + "|"
                + range.startByte() + "|" + range.endByte();
    }

    private static String addressedWitness(SFMReleaseReviewV1.AddressedRange range) {
        return range.documentRevisionId() + "|" + range.startByte() + "|" + range.endByte();
    }

    private static BoundScopes scopes(
            SFMReleaseReviewV1.CommentSelectorBinding binding,
            Map<String, SFMReleaseReviewV1.CorpusDocument> corpusByRevision,
            Map<String, String> languagesByRevision,
            Transition transition
    ) {
        LinkedHashSet<String> lanes = new LinkedHashSet<>();
        LinkedHashSet<String> sourcePaths = new LinkedHashSet<>();
        LinkedHashSet<String> languages = new LinkedHashSet<>();
        ArrayList<String> diagnostics = new ArrayList<>();
        for (SFMReleaseReviewV1.PinnedSelectionRange range : binding.capturedSelection().ranges()) {
            SFMReleaseReviewV1.CorpusDocument document = corpusByRevision.get(range.documentRevisionId());
            if (document == null) {
                diagnostics.add("review.migration-source-outside-corpus: " + range.documentRevisionId());
                continue;
            }
            lanes.add(document.laneId());
            sourcePaths.add(document.path());
            if (document.snapshotSide() != transition.sourceSide()) {
                diagnostics.add("review.migration-source-side-mismatch: " + range.documentRevisionId()
                        + " is " + document.snapshotSide().name().toLowerCase(java.util.Locale.ROOT)
                        + " but transition source is "
                        + transition.sourceSide().name().toLowerCase(java.util.Locale.ROOT));
            }
            String language = languagesByRevision.get(range.documentRevisionId());
            if (language != null && !language.equals("unknown")) languages.add(language);
        }
        List<String> laneList = lanes.stream().sorted().toList();
        List<String> languageList = languages.stream().sorted().toList();
        return new BoundScopes(
                new SFMReleaseReviewEvaluator.Scope(
                        laneList,
                        sourcePaths.stream().sorted().toList(),
                        languageList,
                        List.of(side(transition.sourceSide()))
                ),
                new SFMReleaseReviewEvaluator.Scope(
                        laneList,
                        List.of(),
                        languageList,
                        List.of(side(transition.candidateSide()))
                ),
                diagnostics
        );
    }

    private static SFMReleaseReviewEvaluator.DiffSide side(SFMReleaseReviewV1.SnapshotSide side) {
        return side == SFMReleaseReviewV1.SnapshotSide.BEFORE
                ? SFMReleaseReviewEvaluator.DiffSide.BEFORE
                : SFMReleaseReviewEvaluator.DiffSide.AFTER;
    }

    private static Map<String, String> languagesByRevision(SFMReleaseReviewV1 review) {
        HashMap<String, String> answer = new HashMap<>();
        for (SFMReleaseReviewV1.ReviewUnit unit : review.reviewUnits()) {
            unit.beforeDocumentRevisionId().ifPresent(id -> mergeLanguage(answer, id, unit.language()));
            unit.afterDocumentRevisionId().ifPresent(id -> mergeLanguage(answer, id, unit.language()));
        }
        return answer;
    }

    private static void mergeLanguage(Map<String, String> output, String revision, String language) {
        output.merge(revision, language, (left, right) -> left.equals(right) ? left : "unknown");
    }

    private static String reportId(
            SFMReleaseReviewV1.CommentSelectorBinding binding,
            Transition transition
    ) {
        String identity = binding.commentId() + "\n" + binding.selectedProposal().id() + "\n"
                + transition.sourceSide() + "\n" + transition.candidateSide();
        String hash = SFMReleaseReviewKernel.sha256(identity.getBytes(StandardCharsets.UTF_8));
        return "migration:" + hash.substring(0, 24);
    }

    private static List<SFMReleaseReviewV1.AddressedRange> migrationCandidates(
            SFMReleaseReviewV1.EvaluationResult evaluation
    ) {
        TreeSet<SFMReleaseReviewV1.AddressedRange> answer = new TreeSet<>(RANGE_ORDER);
        answer.addAll(evaluation.ranges());
        answer.addAll(evaluation.candidates());
        return List.copyOf(answer);
    }

    private static boolean sameEvidence(
            SFMReleaseReviewV1.MigrationReport previous,
            SFMReleaseReviewV1.MigrationReport rebuilt
    ) {
        return previous.sourceSelectorId().equals(rebuilt.sourceSelectorId())
                && previous.sourceEvaluation().equals(rebuilt.sourceEvaluation())
                && previous.candidateEvaluation().equals(rebuilt.candidateEvaluation())
                && previous.oldWitnesses().equals(rebuilt.oldWitnesses())
                && previous.newCandidates().equals(rebuilt.newCandidates());
    }

    private record BoundScopes(
            SFMReleaseReviewEvaluator.Scope source,
            SFMReleaseReviewEvaluator.Scope candidate,
            List<String> diagnostics
    ) {
        private BoundScopes {
            diagnostics = List.copyOf(diagnostics);
        }
    }

    private static String requireText(String value, String label) {
        Objects.requireNonNull(value, label);
        if (value.isBlank()) throw new IllegalArgumentException(label + " must not be blank");
        return value;
    }
}
