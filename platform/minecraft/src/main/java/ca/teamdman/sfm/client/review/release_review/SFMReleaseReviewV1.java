package ca.teamdman.sfm.client.review.release_review;

import ca.teamdman.sfm.client.review.session.SFMReviewSessionV1;
import ca.teamdman.sfm.client.review.session.SFMReviewSessionV2;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Portable {@code sfm.release-review/1} envelope.
 *
 * <p>The embedded v2 session remains the sole comment and approval authority.
 * Review units describe the completion domain; they never carry approval.</p>
 */
public record SFMReleaseReviewV1(
        String schema,
        SFMReviewSessionV2 reviewSession,
        List<RepositoryBinding> repositoryBindings,
        List<CorpusDocument> corpusDocuments,
        List<ReviewUnit> reviewUnits,
        List<CommentSelectorBinding> selectorBindings,
        List<MigrationReport> migrationReports,
        List<NamedQuery> namedQueries,
        ResumeState resumeState,
        List<ProducerGeneration> producerGenerations,
        List<CompletionAttestation> completionAttestations
) {
    public static final String SCHEMA = "sfm.release-review/1";
    public static final String HASH_DOMAIN = "sfm.release-review/1:semantic-state\n";

    public SFMReleaseReviewV1 {
        if (!SCHEMA.equals(schema)) throw new IllegalArgumentException("Unsupported release-review schema " + schema);
        Objects.requireNonNull(reviewSession, "reviewSession");
        repositoryBindings = sortedUnique(copy(repositoryBindings, "repository binding"),
                RepositoryBinding::laneId, "repository-binding lane id");
        corpusDocuments = copy(corpusDocuments, "corpus document").stream()
                .sorted(Comparator.comparing(CorpusDocument::laneId)
                        .thenComparingInt(value -> value.snapshotSide().ordinal())
                        .thenComparing(CorpusDocument::path)
                        .thenComparing(CorpusDocument::id))
                .toList();
        ensureUnique(corpusDocuments.stream().map(CorpusDocument::id).toList(), "corpus-document id");
        ensureUnique(corpusDocuments.stream().map(CorpusDocument::canonicalAddress).toList(),
                "corpus-document address");
        reviewUnits = copy(reviewUnits, "review unit").stream()
                .sorted(Comparator.comparing(ReviewUnit::laneId)
                        .thenComparing(ReviewUnit::canonicalPath)
                        .thenComparing(value -> value.operation().name())
                        .thenComparing(ReviewUnit::firstRangeStart)
                        .thenComparing(ReviewUnit::id))
                .toList();
        ensureUnique(reviewUnits.stream().map(ReviewUnit::id).toList(), "review-unit id");
        selectorBindings = sortedUnique(copy(selectorBindings, "comment selector binding"),
                CommentSelectorBinding::commentId, "selector-binding comment id");
        migrationReports = sortedUnique(copy(migrationReports, "migration report"),
                MigrationReport::id, "migration-report id");
        namedQueries = sortedUnique(copy(namedQueries, "named query"), NamedQuery::id, "named-query id");
        Objects.requireNonNull(resumeState, "resumeState");
        producerGenerations = sortedUnique(copy(producerGenerations, "producer generation"),
                ProducerGeneration::producerId, "producer id");
        completionAttestations = sortedUnique(copy(completionAttestations, "completion attestation"),
                CompletionAttestation::id, "completion-attestation id");
    }

    public record RepositoryBinding(
            String laneId,
            String repositoryId,
            String rootHint,
            String beforeLabel,
            String beforeCommit,
            String beforeTree,
            String afterLabel,
            String candidateCommit,
            String candidateTree,
            List<String> reviewEvidencePaths
    ) {
        public RepositoryBinding {
            laneId = requireText(laneId, "repositoryBinding.laneId");
            repositoryId = requireText(repositoryId, "repositoryBinding.repositoryId");
            rootHint = requireText(rootHint, "repositoryBinding.rootHint");
            beforeLabel = requireText(beforeLabel, "repositoryBinding.beforeLabel");
            beforeCommit = requireGitSha1(beforeCommit, "repositoryBinding.beforeCommit");
            beforeTree = requireGitSha1(beforeTree, "repositoryBinding.beforeTree");
            afterLabel = requireText(afterLabel, "repositoryBinding.afterLabel");
            candidateCommit = requireGitSha1(candidateCommit, "repositoryBinding.candidateCommit");
            candidateTree = requireGitSha1(candidateTree, "repositoryBinding.candidateTree");
            reviewEvidencePaths = copy(reviewEvidencePaths, "review evidence path").stream()
                    .map(value -> requireRelativePath(value, "review evidence path"))
                    .sorted()
                    .toList();
            ensureUnique(reviewEvidencePaths, "review evidence path");
        }
    }

    public enum SnapshotSide { BEFORE, AFTER }
    public enum Materialization { COMPLETE, PARTIAL, MISSING }

    public record CorpusDocument(
            String id,
            String laneId,
            SnapshotSide snapshotSide,
            String path,
            String documentRevisionId,
            String sha256,
            String sourceOwner,
            String sourceLocator,
            Materialization materialization
    ) {
        public CorpusDocument {
            id = requireText(id, "corpusDocument.id");
            laneId = requireText(laneId, "corpusDocument.laneId");
            Objects.requireNonNull(snapshotSide, "snapshotSide");
            path = requireRelativePath(path, "corpusDocument.path");
            documentRevisionId = requireText(documentRevisionId, "corpusDocument.documentRevisionId");
            sha256 = requireSha256(sha256, "corpusDocument.sha256");
            sourceOwner = requireText(sourceOwner, "corpusDocument.sourceOwner");
            sourceLocator = requireText(sourceLocator, "corpusDocument.sourceLocator");
            Objects.requireNonNull(materialization, "materialization");
        }

        String canonicalAddress() {
            return laneId + "\u0000" + snapshotSide + "\u0000" + path;
        }
    }

    public enum ChangeOperation { ADDED, DELETED, MODIFIED, RENAMED, COPIED, TYPE_CHANGED }
    public enum SurfaceKind { DECLARATION, SIGNATURE, BODY, FIELD, IMPORT, DIFF_HUNK, FILE, BINARY, UNSUPPORTED }

    public record Utf8Range(int startByte, int endByte) {
        public Utf8Range {
            if (startByte < 0 || endByte < startByte) {
                throw new IllegalArgumentException("UTF-8 range must be forward and non-negative");
            }
        }
    }

    public record ReviewUnit(
            String id,
            String laneId,
            ChangeOperation operation,
            Optional<String> pathBefore,
            Optional<String> pathAfter,
            Optional<String> beforeDocumentRevisionId,
            Optional<String> afterDocumentRevisionId,
            List<Utf8Range> beforeRanges,
            List<Utf8Range> afterRanges,
            String language,
            SurfaceKind surfaceKind,
            Optional<String> semanticKey,
            Optional<String> limitation,
            String producerId,
            String producerGeneration
    ) {
        public ReviewUnit {
            id = requireText(id, "reviewUnit.id");
            laneId = requireText(laneId, "reviewUnit.laneId");
            Objects.requireNonNull(operation, "operation");
            pathBefore = optionalPath(pathBefore, "reviewUnit.pathBefore");
            pathAfter = optionalPath(pathAfter, "reviewUnit.pathAfter");
            if (pathBefore.isEmpty() && pathAfter.isEmpty()) {
                throw new IllegalArgumentException("A review unit requires a before or after path");
            }
            beforeDocumentRevisionId = optionalText(beforeDocumentRevisionId,
                    "reviewUnit.beforeDocumentRevisionId");
            afterDocumentRevisionId = optionalText(afterDocumentRevisionId,
                    "reviewUnit.afterDocumentRevisionId");
            beforeRanges = sortedRanges(beforeRanges, "before range");
            afterRanges = sortedRanges(afterRanges, "after range");
            language = requireText(language, "reviewUnit.language");
            Objects.requireNonNull(surfaceKind, "surfaceKind");
            semanticKey = optionalText(semanticKey, "reviewUnit.semanticKey");
            limitation = optionalText(limitation, "reviewUnit.limitation");
            producerId = requireText(producerId, "reviewUnit.producerId");
            producerGeneration = requireText(producerGeneration, "reviewUnit.producerGeneration");
            if (surfaceKind == SurfaceKind.UNSUPPORTED && limitation.isEmpty()) {
                throw new IllegalArgumentException("An unsupported review unit requires a limitation");
            }
        }

        String canonicalPath() {
            return pathAfter.orElseGet(() -> pathBefore.orElse(""));
        }

        int firstRangeStart() {
            if (!afterRanges.isEmpty()) return afterRanges.get(0).startByte();
            if (!beforeRanges.isEmpty()) return beforeRanges.get(0).startByte();
            return 0;
        }
    }

    public record NamedQuery(String id, String expression) {
        public NamedQuery {
            id = requireText(id, "namedQuery.id");
            expression = requireText(expression, "namedQuery.expression");
        }
    }

    public record ResumeState(
            Optional<String> activeQueryId,
            Optional<String> activeQueryExpression,
            Optional<String> currentUnitId,
            List<String> deferredUnitIds,
            long generation
    ) {
        public ResumeState {
            activeQueryId = optionalText(activeQueryId, "resumeState.activeQueryId");
            activeQueryExpression = optionalText(activeQueryExpression, "resumeState.activeQueryExpression");
            currentUnitId = optionalText(currentUnitId, "resumeState.currentUnitId");
            deferredUnitIds = copy(deferredUnitIds, "deferred unit id").stream()
                    .map(value -> requireText(value, "deferred unit id"))
                    .sorted()
                    .toList();
            ensureUnique(deferredUnitIds, "deferred unit id");
            if (generation < 0) throw new IllegalArgumentException("Resume generation must not be negative");
        }

        public static ResumeState empty() {
            return new ResumeState(Optional.empty(), Optional.empty(), Optional.empty(), List.of(), 0);
        }
    }

    public record ProducerGeneration(
            String producerId,
            String generation,
            String inputFingerprint,
            String outputFingerprint
    ) {
        public ProducerGeneration {
            producerId = requireText(producerId, "producerGeneration.producerId");
            generation = requireText(generation, "producerGeneration.generation");
            inputFingerprint = requireSha256(inputFingerprint, "producerGeneration.inputFingerprint");
            outputFingerprint = requireSha256(outputFingerprint, "producerGeneration.outputFingerprint");
        }
    }

    public record CompletionAttestation(
            String id,
            String reviewSemanticStateHash,
            String maintainer,
            String attestedAt,
            String statement
    ) {
        public CompletionAttestation {
            id = requireText(id, "attestation.id");
            reviewSemanticStateHash = requireSha256(reviewSemanticStateHash,
                    "attestation.reviewSemanticStateHash");
            maintainer = requireText(maintainer, "attestation.maintainer");
            attestedAt = requireText(attestedAt, "attestation.attestedAt");
            try {
                java.time.OffsetDateTime.parse(attestedAt);
            } catch (java.time.format.DateTimeParseException exception) {
                throw new IllegalArgumentException("attestation.attestedAt must be RFC 3339", exception);
            }
            statement = requireText(statement, "attestation.statement");
        }
    }

    public enum SelectionDirection { FORWARD, BACKWARD }

    public record PinnedSelection(
            String selectionRevision,
            String sourceExpression,
            int primaryRangeIndex,
            List<PinnedSelectionRange> ranges
    ) {
        public PinnedSelection {
            selectionRevision = requireText(selectionRevision, "selection.selectionRevision");
            sourceExpression = requireText(sourceExpression, "selection.sourceExpression");
            ranges = copy(ranges, "pinned selection range");
            if (ranges.isEmpty()) throw new IllegalArgumentException("A pinned selection requires at least one range");
            if (primaryRangeIndex < 0 || primaryRangeIndex >= ranges.size()) {
                throw new IllegalArgumentException("Primary range index is outside the selection");
            }
        }
    }

    public record PinnedSelectionRange(
            SelectionDirection direction,
            String documentRevisionId,
            String documentSha256,
            int startByte,
            int endByte
    ) {
        public PinnedSelectionRange {
            Objects.requireNonNull(direction, "direction");
            documentRevisionId = requireText(documentRevisionId, "selectionRange.documentRevisionId");
            documentSha256 = requireSha256(documentSha256, "selectionRange.documentSha256");
            if (startByte < 0 || endByte < startByte) {
                throw new IllegalArgumentException("Pinned selection range must be forward and non-negative");
            }
        }
    }

    public enum SelectorKind { LITERAL, DECLARATION, SIGNATURE, BODY, RETURN_TYPE, SYMBOL, BOUNDED_MULTI_REGION }
    public enum ProposalConfidence { EXACT, CONSERVATIVE, UNAVAILABLE }

    public record Evidence(String key, String value) {
        public Evidence {
            key = requireText(key, "evidence.key");
            value = requireText(value, "evidence.value");
        }
    }

    public record SelectorProposal(
            String id,
            SelectorKind kind,
            SFMReviewSessionV1.SelectionRule selectionRule,
            PinnedSelection literalWitness,
            Optional<String> semanticProvider,
            Optional<String> semanticKey,
            List<Evidence> semanticProvenance,
            ProposalConfidence confidence,
            String projectionFingerprint,
            String sourceSnapshotId,
            List<String> diagnostics
    ) {
        public SelectorProposal {
            id = requireText(id, "selectorProposal.id");
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(selectionRule, "selectionRule");
            Objects.requireNonNull(literalWitness, "literalWitness");
            semanticProvider = optionalText(semanticProvider, "selectorProposal.semanticProvider");
            semanticKey = optionalText(semanticKey, "selectorProposal.semanticKey");
            semanticProvenance = sortedUnique(copy(semanticProvenance, "semantic provenance"),
                    Evidence::key, "semantic-provenance key");
            Objects.requireNonNull(confidence, "confidence");
            projectionFingerprint = requireSha256(projectionFingerprint,
                    "selectorProposal.projectionFingerprint");
            sourceSnapshotId = requireText(sourceSnapshotId, "selectorProposal.sourceSnapshotId");
            diagnostics = copy(diagnostics, "selector diagnostic");
        }
    }

    /** Durable provenance for the selector chosen by one ordinary v2 comment. */
    public record CommentSelectorBinding(
            String commentId,
            PinnedSelection capturedSelection,
            SelectorProposal selectedProposal
    ) {
        public CommentSelectorBinding {
            commentId = requireText(commentId, "selectorBinding.commentId");
            Objects.requireNonNull(capturedSelection, "capturedSelection");
            Objects.requireNonNull(selectedProposal, "selectedProposal");
            if (!capturedSelection.equals(selectedProposal.literalWitness())) {
                throw new IllegalArgumentException("Selected proposal must retain the exact captured literal witness");
            }
        }
    }

    public enum EvaluationStatus { EXACT, RELOCATED, CONTENT_CHANGED, AMBIGUOUS, MISSING, INVALID, SCOPE_MISSING }

    public record AddressedRange(String documentRevisionId, int startByte, int endByte) {
        public AddressedRange {
            documentRevisionId = requireText(documentRevisionId, "addressedRange.documentRevisionId");
            if (startByte < 0 || endByte < startByte) {
                throw new IllegalArgumentException("Addressed range must be forward and non-negative");
            }
        }
    }

    public record InvalidationKey(String owner, String generation, String fingerprint) {
        public InvalidationKey {
            owner = requireText(owner, "invalidationKey.owner");
            generation = requireText(generation, "invalidationKey.generation");
            fingerprint = requireSha256(fingerprint, "invalidationKey.fingerprint");
        }
    }

    public record EvaluationResult(
            String selectorId,
            EvaluationStatus status,
            List<AddressedRange> ranges,
            List<AddressedRange> candidates,
            List<InvalidationKey> invalidationKeys,
            List<String> diagnostics
    ) {
        public EvaluationResult {
            selectorId = requireText(selectorId, "evaluation.selectorId");
            Objects.requireNonNull(status, "status");
            ranges = copy(ranges, "evaluation range");
            candidates = copy(candidates, "evaluation candidate");
            invalidationKeys = copy(invalidationKeys, "invalidation key");
            diagnostics = copy(diagnostics, "evaluation diagnostic");
        }
    }

    public enum MigrationDecision {
        UNRESOLVED, RETARGETED, RELOCATION_CONFIRMED, SELECTOR_EDITED, ARCHIVED, DISCARDED, DEFERRED
    }

    public record MigrationReport(
            String id,
            String sourceSelectorId,
            EvaluationResult sourceEvaluation,
            EvaluationResult candidateEvaluation,
            List<PinnedSelectionRange> oldWitnesses,
            List<AddressedRange> newCandidates,
            MigrationDecision decision,
            Optional<String> decisionCommentId
    ) {
        public MigrationReport {
            id = requireText(id, "migration.id");
            sourceSelectorId = requireText(sourceSelectorId, "migration.sourceSelectorId");
            Objects.requireNonNull(sourceEvaluation, "sourceEvaluation");
            Objects.requireNonNull(candidateEvaluation, "candidateEvaluation");
            oldWitnesses = copy(oldWitnesses, "old witness");
            newCandidates = copy(newCandidates, "new candidate");
            Objects.requireNonNull(decision, "decision");
            decisionCommentId = optionalText(decisionCommentId, "migration.decisionCommentId");
            if (decision != MigrationDecision.UNRESOLVED && decisionCommentId.isEmpty()) {
                throw new IllegalArgumentException("A migration decision requires an ordinary decision comment id");
            }
        }
    }

    private static List<Utf8Range> sortedRanges(List<Utf8Range> ranges, String label) {
        return copy(ranges, label).stream()
                .sorted(Comparator.comparingInt(Utf8Range::startByte).thenComparingInt(Utf8Range::endByte))
                .toList();
    }

    private static Optional<String> optionalText(Optional<String> value, String label) {
        Objects.requireNonNull(value, label);
        return value.map(item -> requireText(item, label));
    }

    private static Optional<String> optionalPath(Optional<String> value, String label) {
        Objects.requireNonNull(value, label);
        return value.map(item -> requireRelativePath(item, label));
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

    private static String requireGitSha1(String value, String label) {
        value = requireText(value, label);
        if (!value.matches("[0-9a-f]{40}")) throw new IllegalArgumentException(label + " must be lowercase Git SHA-1");
        return value;
    }

    private static String requireRelativePath(String value, String label) {
        value = requireText(value, label).replace('\\', '/');
        if (value.startsWith("/") || value.matches("^[A-Za-z]:/.*") || value.contains("../") || value.equals("..")) {
            throw new IllegalArgumentException(label + " must be a repository-relative path");
        }
        return value;
    }

    private static <T> List<T> copy(List<T> values, String label) {
        Objects.requireNonNull(values, label + " list");
        return values.stream().map(value -> Objects.requireNonNull(value, label)).toList();
    }

    private static <T> List<T> sortedUnique(List<T> values,
                                             java.util.function.Function<T, String> identity,
                                             String label) {
        List<T> answer = values.stream().sorted(Comparator.comparing(identity)).toList();
        ensureUnique(answer.stream().map(identity).toList(), label);
        return answer;
    }

    private static void ensureUnique(List<String> values, String label) {
        if (new HashSet<>(values).size() != values.size()) {
            throw new IllegalArgumentException("Duplicate " + label);
        }
    }
}
