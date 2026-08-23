package ca.teamdman.sfm.client.review.release_review;

import ca.teamdman.sfm.client.review.session.SFMReviewSessionV1;
import ca.teamdman.sfm.client.review.session.SFMReviewSessionV1Kernel;
import ca.teamdman.sfm.client.review.session.SFMReviewSessionV2;
import ca.teamdman.sfm.client.text_editor.SFMTextDocumentRange;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.UnaryOperator;

/** Application-scoped explicit-path runtime with autosave and truthful dirty state. */
public final class SFMReleaseReviewRuntime implements AutoCloseable {
    private static final SFMReleaseReviewRuntime INSTANCE = new SFMReleaseReviewRuntime();

    public record OpenResult(
            Optional<SFMReleaseReviewV1> document,
            boolean writable,
            boolean recoveredMachineLocalCopy,
            List<String> diagnostics
    ) {
        public OpenResult {
            diagnostics = List.copyOf(diagnostics);
        }
    }

    public record MutationResult(boolean saved, boolean dirty, Optional<String> failure) {}

    public record CommentMutationResult(String commentId, MutationResult mutation) {
        public CommentMutationResult {
            Objects.requireNonNull(commentId, "commentId");
            Objects.requireNonNull(mutation, "mutation");
        }
    }

    public record MigrationMutationResult(
            String migrationId,
            String decisionCommentId,
            MutationResult mutation
    ) {
        public MigrationMutationResult {
            Objects.requireNonNull(migrationId, "migrationId");
            Objects.requireNonNull(decisionCommentId, "decisionCommentId");
            Objects.requireNonNull(mutation, "mutation");
        }
    }

    private SFMReleaseReviewStore store;
    private SFMReleaseReviewV1 document;
    private Optional<String> openedHash = Optional.empty();
    private SFMReleaseReviewKernel.CompletionReport cachedStatus;
    private boolean dirty;

    public static SFMReleaseReviewRuntime get() {
        return INSTANCE;
    }

    public synchronized OpenResult open(Path path, boolean writable) throws IOException {
        closeForReplacement();
        store = SFMReleaseReviewStore.open(path,
                writable ? SFMReleaseReviewStore.Access.WRITABLE : SFMReleaseReviewStore.Access.READ_ONLY);
        SFMReleaseReviewStore.LoadResult loaded = store.load();
        document = loaded.document().orElse(null);
        openedHash = loaded.openedContentHash();
        cachedStatus = null;
        dirty = loaded.recoveredMachineLocalCopy();
        return new OpenResult(loaded.document(), writable, loaded.recoveredMachineLocalCopy(), loaded.diagnostics());
    }

    public synchronized void create(Path path, SFMReleaseReviewV1 value) throws IOException {
        Objects.requireNonNull(value, "value");
        closeForReplacement();
        store = SFMReleaseReviewStore.open(path, SFMReleaseReviewStore.Access.WRITABLE);
        document = value;
        openedHash = Optional.empty();
        cachedStatus = null;
        SFMReleaseReviewStore.SaveResult saved = store.save(value, openedHash);
        openedHash = Optional.of(saved.contentHash());
        dirty = false;
    }

    public synchronized MutationResult mutate(UnaryOperator<SFMReleaseReviewV1> mutation) {
        requireDocument();
        SFMReleaseReviewV1 updated = Objects.requireNonNull(mutation.apply(document), "mutated document");
        SFMReleaseReviewKernel.validate(updated);
        document = updated;
        cachedStatus = null;
        dirty = true;
        try {
            SFMReleaseReviewStore.SaveResult saved = store.save(updated, openedHash);
            openedHash = Optional.of(saved.contentHash());
            dirty = false;
            return new MutationResult(true, false, Optional.empty());
        } catch (IOException | RuntimeException exception) {
            return new MutationResult(false, true, Optional.ofNullable(exception.getMessage()));
        }
    }

    public synchronized void saveAs(Path path) throws IOException {
        requireDocument();
        SFMReleaseReviewStore replacement = SFMReleaseReviewStore.open(path, SFMReleaseReviewStore.Access.WRITABLE);
        try {
            SFMReleaseReviewStore.LoadResult existing = replacement.load();
            SFMReleaseReviewStore.SaveResult saved = replacement.save(document, existing.openedContentHash());
            closeStore();
            store = replacement;
            replacement = null;
            openedHash = Optional.of(saved.contentHash());
            dirty = false;
        } finally {
            if (replacement != null) replacement.close();
        }
    }

    public synchronized MutationResult save() {
        requireDocument();
        try {
            SFMReleaseReviewStore.SaveResult saved = store.save(document, openedHash);
            openedHash = Optional.of(saved.contentHash());
            dirty = false;
            return new MutationResult(true, false, Optional.empty());
        } catch (IOException | RuntimeException exception) {
            dirty = true;
            return new MutationResult(false, true, Optional.ofNullable(exception.getMessage()));
        }
    }

    public synchronized MutationResult activateQuery(Optional<String> queryId, String expression) {
        requireDocument();
        SFMReleaseReviewKernel.QueryResult result = SFMReleaseReviewKernel.query(document, expression);
        Optional<String> first = result.reviewUnitIds().stream().findFirst();
        SFMReleaseReviewV1.ResumeState previous = document.resumeState();
        return mutate(current -> withResume(current, new SFMReleaseReviewV1.ResumeState(
                queryId, Optional.of(expression), first, previous.deferredUnitIds(), previous.generation() + 1)));
    }

    public synchronized MutationResult saveNamedQuery(String id, String expression, boolean activate) {
        requireDocument();
        String queryId = Objects.requireNonNull(id, "id").trim();
        if (queryId.isEmpty()) throw new IllegalArgumentException("Named query id must not be blank");
        String queryExpression = Objects.requireNonNull(expression, "expression").trim();
        SFMReleaseReviewKernel.QueryResult result = SFMReleaseReviewKernel.query(document, queryExpression);
        return mutate(current -> {
            ArrayList<SFMReleaseReviewV1.NamedQuery> queries = new ArrayList<>(current.namedQueries());
            queries.removeIf(query -> query.id().equals(queryId));
            queries.add(new SFMReleaseReviewV1.NamedQuery(queryId, queryExpression));
            SFMReleaseReviewV1.ResumeState previous = current.resumeState();
            SFMReleaseReviewV1.ResumeState resume = activate
                    ? new SFMReleaseReviewV1.ResumeState(
                            Optional.of(queryId),
                            Optional.of(queryExpression),
                            result.reviewUnitIds().stream().findFirst(),
                            previous.deferredUnitIds(),
                            previous.generation() + 1)
                    : previous;
            return new SFMReleaseReviewV1(
                    current.schema(), current.reviewSession(), current.repositoryBindings(),
                    current.corpusDocuments(), current.reviewUnits(), current.selectorBindings(),
                    current.migrationReports(), queries, resume, current.producerGenerations(),
                    current.completionAttestations()
            );
        });
    }

    /** Rebuilds every durable migration report through the bounded production evaluator and autosaves it. */
    public synchronized MutationResult rebuildMigrationReports(
            SFMReleaseReviewEvaluator.PreparedEvidence preparedEvidence,
            SFMReleaseReviewEvaluator.Limits limits,
            SFMReleaseReviewMigrationPipeline.Transition transition
    ) {
        requireDocument();
        Objects.requireNonNull(preparedEvidence, "preparedEvidence");
        Objects.requireNonNull(limits, "limits");
        Objects.requireNonNull(transition, "transition");
        return mutate(current -> SFMReleaseReviewMigrationPipeline.rebuild(
                current, preparedEvidence, limits, transition).document());
    }

    public synchronized MutationResult move(int delta) {
        requireDocument();
        String expression = activeExpression();
        List<String> units = SFMReleaseReviewKernel.query(document, expression).reviewUnitIds();
        if (units.isEmpty()) return new MutationResult(false, dirty, Optional.of("The active work queue is empty"));
        int current = document.resumeState().currentUnitId().map(units::indexOf).orElse(-1);
        int next = current < 0 ? 0 : Math.max(0, Math.min(units.size() - 1, current + delta));
        SFMReleaseReviewV1.ResumeState previous = document.resumeState();
        return mutate(value -> withResume(value, new SFMReleaseReviewV1.ResumeState(
                previous.activeQueryId(), previous.activeQueryExpression(), Optional.of(units.get(next)),
                previous.deferredUnitIds(), previous.generation() + 1)));
    }

    /** Selects one stable unit from the active query without depending on incidental list position. */
    public synchronized MutationResult selectUnit(String unitId) {
        requireDocument();
        Objects.requireNonNull(unitId, "unitId");
        String expression = activeExpression();
        List<String> units = SFMReleaseReviewKernel.query(document, expression).reviewUnitIds();
        if (!units.contains(unitId)) {
            return new MutationResult(false, dirty, Optional.of(
                    "Review unit is not present in the active work queue: " + unitId));
        }
        SFMReleaseReviewV1.ResumeState previous = document.resumeState();
        return mutate(value -> withResume(value, new SFMReleaseReviewV1.ResumeState(
                previous.activeQueryId(), previous.activeQueryExpression(), Optional.of(unitId),
                previous.deferredUnitIds(), previous.generation() + 1)));
    }

    public synchronized MutationResult deferCurrent() {
        requireDocument();
        SFMReleaseReviewV1.ResumeState previous = document.resumeState();
        String current = previous.currentUnitId().orElseThrow(() ->
                new IllegalStateException("No current review unit is selected"));
        java.util.ArrayList<String> deferred = new java.util.ArrayList<>(previous.deferredUnitIds());
        if (!deferred.contains(current)) deferred.add(current);
        List<String> queue = SFMReleaseReviewKernel.query(document, activeExpression()).reviewUnitIds();
        int currentIndex = queue.indexOf(current);
        Optional<String> next = currentIndex >= 0 && currentIndex + 1 < queue.size()
                ? Optional.of(queue.get(currentIndex + 1))
                : queue.stream().filter(id -> !id.equals(current)).findFirst();
        Optional<String> finalNext = next;
        return mutate(value -> withResume(value, new SFMReleaseReviewV1.ResumeState(
                previous.activeQueryId(), previous.activeQueryExpression(), finalNext, deferred,
                previous.generation() + 1)));
    }

    public synchronized MutationResult resumeDeferred() {
        requireDocument();
        SFMReleaseReviewV1.ResumeState previous = document.resumeState();
        if (previous.deferredUnitIds().isEmpty()) {
            return new MutationResult(false, dirty, Optional.of("No deferred review unit is available"));
        }
        java.util.ArrayList<String> deferred = new java.util.ArrayList<>(previous.deferredUnitIds());
        String resumed = deferred.remove(0);
        return mutate(value -> withResume(value, new SFMReleaseReviewV1.ResumeState(
                previous.activeQueryId(), previous.activeQueryExpression(), Optional.of(resumed), deferred,
                previous.generation() + 1)));
    }

    /** Adds one ordinary v2 comment and its exact selector provenance in the same atomic save. */
    public synchronized CommentMutationResult createComment(
            String text,
            SFMReleaseReviewV1.PinnedSelection capturedSelection,
            SFMReleaseReviewV1.SelectorProposal selectedProposal
    ) {
        requireDocument();
        Objects.requireNonNull(text, "text");
        if (text.isBlank()) throw new IllegalArgumentException("Comment text must not be blank");
        Objects.requireNonNull(capturedSelection, "capturedSelection");
        Objects.requireNonNull(selectedProposal, "selectedProposal");
        if (!capturedSelection.equals(selectedProposal.literalWitness())) {
            throw new IllegalArgumentException("Selected proposal does not retain the captured literal witness");
        }
        String commentId = nextHumanCommentId(document.reviewSession());
        MutationResult mutation = mutate(value -> {
            java.util.ArrayList<SFMReviewSessionV2.Comment> comments =
                    new java.util.ArrayList<>(value.reviewSession().comments());
            comments.add(new SFMReviewSessionV2.Comment(
                    commentId,
                    text,
                    new SFMReviewSessionV1.Provenance(
                            "human",
                            "in-game-release-reviewer",
                            "1",
                            List.of()
                    ),
                    new SFMReviewSessionV2.CommittedReviewTarget(selectedProposal.selectionRule())
            ));
            SFMReviewSessionV2 session = new SFMReviewSessionV2(
                    value.reviewSession().schema(),
                    value.reviewSession().id(),
                    value.reviewSession().title(),
                    value.reviewSession().coordinateSystem(),
                    value.reviewSession().revisionLanes(),
                    comments,
                    value.reviewSession().styleRules(),
                    value.reviewSession().completionPolicy()
            );
            java.util.ArrayList<SFMReleaseReviewV1.CommentSelectorBinding> bindings =
                    new java.util.ArrayList<>(value.selectorBindings());
            bindings.add(new SFMReleaseReviewV1.CommentSelectorBinding(
                    commentId,
                    capturedSelection,
                    selectedProposal
            ));
            return new SFMReleaseReviewV1(
                    value.schema(), session, value.repositoryBindings(), value.corpusDocuments(),
                    value.reviewUnits(), bindings, value.migrationReports(), value.namedQueries(),
                    value.resumeState(), value.producerGenerations(), value.completionAttestations()
            );
        });
        return new CommentMutationResult(commentId, mutation);
    }

    /** Records a human migration decision and any explicit retargeting as one portable mutation. */
    public synchronized MigrationMutationResult decideMigration(
            String migrationId,
            SFMReleaseReviewV1.MigrationDecision decision,
            java.util.OptionalInt candidateIndex,
            String note
    ) {
        requireDocument();
        return decideMigration(
                migrationId,
                SFMReleaseReviewKernel.semanticStateHash(document),
                decision,
                candidateIndex,
                note
        );
    }

    /** Applies a migration decision only to the exact semantic state shown by its choice surface. */
    public synchronized MigrationMutationResult decideMigration(
            String migrationId,
            String expectedSemanticStateHash,
            SFMReleaseReviewV1.MigrationDecision decision,
            java.util.OptionalInt candidateIndex,
            String note
    ) {
        requireDocument();
        Objects.requireNonNull(migrationId, "migrationId");
        Objects.requireNonNull(expectedSemanticStateHash, "expectedSemanticStateHash");
        Objects.requireNonNull(decision, "decision");
        Objects.requireNonNull(candidateIndex, "candidateIndex");
        Objects.requireNonNull(note, "note");
        if (migrationId.isBlank()) throw new IllegalArgumentException("Migration id must not be blank");
        String currentSemanticStateHash = SFMReleaseReviewKernel.semanticStateHash(document);
        if (!currentSemanticStateHash.equals(expectedSemanticStateHash)) {
            throw new IllegalArgumentException("Migration choice is stale: expected semantic state "
                    + expectedSemanticStateHash + " but the open review is " + currentSemanticStateHash);
        }
        if (decision == SFMReleaseReviewV1.MigrationDecision.UNRESOLVED) {
            throw new IllegalArgumentException("UNRESOLVED is not a human migration decision");
        }
        if (note.isBlank()) throw new IllegalArgumentException("Migration decision note must not be blank");

        SFMReleaseReviewV1.MigrationReport report = document.migrationReports().stream()
                .filter(value -> value.id().equals(migrationId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown migration report " + migrationId));
        if (report.decision() != SFMReleaseReviewV1.MigrationDecision.UNRESOLVED
                && report.decision() != SFMReleaseReviewV1.MigrationDecision.DEFERRED) {
            throw new IllegalArgumentException("Migration report was already resolved as " + report.decision());
        }
        SFMReleaseReviewV1.CommentSelectorBinding binding = document.selectorBindings().stream()
                .filter(value -> value.selectedProposal().id().equals(report.sourceSelectorId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Migration source selector is unavailable: " + report.sourceSelectorId()));
        SFMReviewSessionV2.Comment sourceComment = document.reviewSession().comments().stream()
                .filter(value -> value.id().equals(binding.commentId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Migration source comment is unavailable: " + binding.commentId()));
        if (!(sourceComment.target() instanceof SFMReviewSessionV2.CommittedReviewTarget committed)) {
            throw new IllegalArgumentException("Only committed review comments can be migrated");
        }

        List<SFMReleaseReviewV1.AddressedRange> replacementRanges = switch (decision) {
            case RELOCATION_CONFIRMED -> {
                if (report.candidateEvaluation().status() != SFMReleaseReviewV1.EvaluationStatus.RELOCATED) {
                    throw new IllegalArgumentException("Witnessed relocation can be confirmed only for RELOCATED evidence");
                }
                List<SFMReleaseReviewV1.AddressedRange> ranges = report.candidateEvaluation().ranges().isEmpty()
                        ? report.newCandidates()
                        : report.candidateEvaluation().ranges();
                if (ranges.isEmpty()) throw new IllegalArgumentException("Relocation report has no candidate range");
                yield ranges;
            }
            case RETARGETED, SELECTOR_EDITED -> {
                if (candidateIndex.isEmpty()) {
                    throw new IllegalArgumentException(decision + " requires a candidate index");
                }
                int index = candidateIndex.getAsInt();
                if (index < 0 || index >= report.newCandidates().size()) {
                    throw new IllegalArgumentException("Migration candidate index is outside the report");
                }
                yield List.of(report.newCandidates().get(index));
            }
            case ARCHIVED, DISCARDED, DEFERRED -> List.of();
            case UNRESOLVED -> throw new IllegalStateException("UNRESOLVED was rejected above");
        };
        Optional<SFMReviewSessionV1.SelectionRule> replacementRule = replacementRanges.isEmpty()
                ? Optional.empty()
                : Optional.of(literalRule(document, replacementRanges));
        String decisionCommentId = nextHumanCommentId(document.reviewSession());
        MutationResult mutation = mutate(value -> {
            ArrayList<SFMReviewSessionV2.Comment> comments = new ArrayList<>(value.reviewSession().comments());
            for (int index = 0; index < comments.size(); index++) {
                SFMReviewSessionV2.Comment comment = comments.get(index);
                if (!comment.id().equals(sourceComment.id())) continue;
                String text = comment.text();
                if (decision == SFMReleaseReviewV1.MigrationDecision.ARCHIVED
                        || decision == SFMReleaseReviewV1.MigrationDecision.DISCARDED) {
                    if (!SFMReviewSessionV1Kernel.derivedHashtags(text).contains("#archived")) {
                        text = "#archived " + text;
                    }
                }
                comments.set(index, new SFMReviewSessionV2.Comment(
                        comment.id(), text, comment.provenance(), replacementRule
                        .<SFMReviewSessionV2.CommentTarget>map(SFMReviewSessionV2.CommittedReviewTarget::new)
                        .orElse(comment.target())));
                break;
            }
            SFMReviewSessionV1.SelectionRule decisionTarget = replacementRule.orElse(committed.selectionRule());
            comments.add(new SFMReviewSessionV2.Comment(
                    decisionCommentId,
                    "#migration-decision #" + decision.name().toLowerCase(java.util.Locale.ROOT)
                            .replace('_', '-') + " " + note,
                    new SFMReviewSessionV1.Provenance(
                            "human", "in-game-release-reviewer", "1", List.of(sourceComment.id())),
                    new SFMReviewSessionV2.CommittedReviewTarget(decisionTarget)
            ));
            SFMReviewSessionV2 session = new SFMReviewSessionV2(
                    value.reviewSession().schema(), value.reviewSession().id(), value.reviewSession().title(),
                    value.reviewSession().coordinateSystem(), value.reviewSession().revisionLanes(), comments,
                    value.reviewSession().styleRules(), value.reviewSession().completionPolicy());

            ArrayList<SFMReleaseReviewV1.CommentSelectorBinding> bindings =
                    new ArrayList<>(value.selectorBindings());
            if (replacementRule.isPresent()) {
                for (int index = 0; index < bindings.size(); index++) {
                    SFMReleaseReviewV1.CommentSelectorBinding current = bindings.get(index);
                    if (!current.commentId().equals(binding.commentId())) continue;
                    SFMReleaseReviewV1.SelectorProposal previous = current.selectedProposal();
                    ArrayList<SFMReleaseReviewV1.Evidence> provenance =
                            new ArrayList<>(previous.semanticProvenance());
                    provenance.removeIf(evidence -> evidence.key().equals("migration-report"));
                    provenance.add(new SFMReleaseReviewV1.Evidence("migration-report", report.id()));
                    String fingerprint = SFMReleaseReviewKernel.sha256((report.id() + "\n" + decision
                            + "\n" + replacementRanges).getBytes(StandardCharsets.UTF_8));
                    SFMReleaseReviewV1.SelectorProposal migrated = new SFMReleaseReviewV1.SelectorProposal(
                            previous.id(),
                            previous.kind(),
                            replacementRule.orElseThrow(),
                            previous.literalWitness(),
                            Optional.of("sfm:human-migration"),
                            previous.semanticKey(),
                            provenance,
                            SFMReleaseReviewV1.ProposalConfidence.EXACT,
                            fingerprint,
                            "migration:" + report.id(),
                            List.of("Explicit human migration decision " + decision)
                    );
                    bindings.set(index, new SFMReleaseReviewV1.CommentSelectorBinding(
                            current.commentId(), current.capturedSelection(), migrated));
                    break;
                }
            }

            ArrayList<SFMReleaseReviewV1.MigrationReport> migrations =
                    new ArrayList<>(value.migrationReports());
            for (int index = 0; index < migrations.size(); index++) {
                SFMReleaseReviewV1.MigrationReport current = migrations.get(index);
                if (!current.id().equals(report.id())) continue;
                migrations.set(index, new SFMReleaseReviewV1.MigrationReport(
                        current.id(), current.sourceSelectorId(), current.sourceEvaluation(),
                        current.candidateEvaluation(), current.oldWitnesses(), current.newCandidates(),
                        decision, Optional.of(decisionCommentId)));
                break;
            }
            return new SFMReleaseReviewV1(
                    value.schema(), session, value.repositoryBindings(), value.corpusDocuments(),
                    value.reviewUnits(), bindings, migrations, value.namedQueries(), value.resumeState(),
                    value.producerGenerations(), value.completionAttestations());
        });
        return new MigrationMutationResult(migrationId, decisionCommentId, mutation);
    }

    public synchronized MutationResult attest(String maintainer, String statement) {
        requireDocument();
        SFMReleaseReviewKernel.CompletionReport report = SFMReleaseReviewKernel.completion(document);
        if (report.status() != SFMReleaseReviewKernel.CompletionStatus.READY_FOR_MAINTAINER_ATTESTATION) {
            return new MutationResult(false, dirty, Optional.of(
                    "Maintainer attestation requires ready_for_maintainer_attestation; current status is "
                            + report.status().name().toLowerCase(java.util.Locale.ROOT)));
        }
        java.util.ArrayList<SFMReleaseReviewV1.CompletionAttestation> attestations =
                new java.util.ArrayList<>(document.completionAttestations());
        String id = "maintainer-attestation-" + (attestations.size() + 1);
        attestations.add(new SFMReleaseReviewV1.CompletionAttestation(
                id,
                report.reviewSemanticStateHash(),
                maintainer,
                java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC).toString(),
                statement
        ));
        return mutate(value -> new SFMReleaseReviewV1(
                value.schema(), value.reviewSession(), value.repositoryBindings(), value.corpusDocuments(),
                value.reviewUnits(), value.selectorBindings(), value.migrationReports(), value.namedQueries(),
                value.resumeState(), value.producerGenerations(), attestations
        ));
    }

    public synchronized Optional<SFMReleaseReviewV1> document() {
        return Optional.ofNullable(document);
    }

    public synchronized Optional<Path> path() {
        return store == null ? Optional.empty() : Optional.of(store.path());
    }

    public synchronized boolean dirty() {
        return dirty;
    }

    public synchronized SFMReleaseReviewKernel.QueryResult query(String expression) {
        requireDocument();
        return SFMReleaseReviewKernel.query(document, expression);
    }

    public synchronized SFMReleaseReviewKernel.CompletionReport status() {
        requireDocument();
        if (cachedStatus == null) cachedStatus = SFMReleaseReviewKernel.completion(document);
        return cachedStatus;
    }

    @Override
    public synchronized void close() {
        if (dirty) {
            throw new IllegalStateException(
                    "Release-review document has unsaved changes; save or explicitly discard before closing");
        }
        discardAndClose();
    }

    /** Explicitly abandons unsaved in-memory/recovery state and releases the writer lease. */
    public synchronized void discardAndClose() {
        closeStore();
        document = null;
        openedHash = Optional.empty();
        cachedStatus = null;
        dirty = false;
    }

    private void closeForReplacement() {
        if (store == null && document == null) return;
        close();
    }

    private void closeStore() {
        if (store != null) store.close();
        store = null;
    }

    private void requireDocument() {
        if (store == null || document == null) throw new IllegalStateException("No release-review document is open");
    }

    private String activeExpression() {
        if (SFMReleaseReviewKernel.activeQueryRevisionStale(document)) {
            throw new IllegalStateException(
                    "Active named-query revision is stale; reactivate the query before navigating its work queue");
        }
        return document.resumeState().activeQueryExpression().orElseGet(() ->
                document.resumeState().activeQueryId()
                        .flatMap(id -> document.namedQueries().stream().filter(query -> query.id().equals(id)).findFirst())
                        .map(SFMReleaseReviewV1.NamedQuery::expression)
                        .orElse("remaining"));
    }

    private static SFMReleaseReviewV1 withResume(
            SFMReleaseReviewV1 value,
            SFMReleaseReviewV1.ResumeState resume
    ) {
        return new SFMReleaseReviewV1(
                value.schema(), value.reviewSession(), value.repositoryBindings(), value.corpusDocuments(),
                value.reviewUnits(), value.selectorBindings(), value.migrationReports(), value.namedQueries(), resume,
                value.producerGenerations(), value.completionAttestations()
        );
    }

    private static String nextHumanCommentId(SFMReviewSessionV2 session) {
        java.util.Set<String> ids = session.comments().stream()
                .map(SFMReviewSessionV2.Comment::id)
                .collect(java.util.stream.Collectors.toSet());
        for (int candidate = 1; ; candidate++) {
            String id = "human:release-review:" + candidate;
            if (!ids.contains(id)) return id;
        }
    }

    private static SFMReviewSessionV1.SelectionRule literalRule(
            SFMReleaseReviewV1 review,
            List<SFMReleaseReviewV1.AddressedRange> ranges
    ) {
        java.util.Map<String, SFMReviewSessionV1.DocumentRevision> documents = new java.util.HashMap<>();
        review.reviewSession().revisionLanes().forEach(lane -> {
            lane.before().documents().forEach(value -> documents.put(value.id(), value));
            lane.after().documents().forEach(value -> documents.put(value.id(), value));
        });
        java.util.Map<String, SFMReleaseReviewV1.CorpusDocument> corpus = review.corpusDocuments().stream()
                .collect(java.util.stream.Collectors.toMap(
                        SFMReleaseReviewV1.CorpusDocument::documentRevisionId,
                        java.util.function.Function.identity()));
        ArrayList<SFMReviewSessionV1.SelectionRule> rules = new ArrayList<>();
        for (SFMReleaseReviewV1.AddressedRange range : ranges) {
            SFMReviewSessionV1.DocumentRevision document = Optional.ofNullable(
                    documents.get(range.documentRevisionId())).orElseThrow(() ->
                    new IllegalArgumentException("Migration candidate document is unavailable: "
                            + range.documentRevisionId()));
            SFMReleaseReviewV1.CorpusDocument binding = Optional.ofNullable(
                    corpus.get(range.documentRevisionId())).orElseThrow(() ->
                    new IllegalArgumentException("Migration candidate is outside the release corpus: "
                            + range.documentRevisionId()));
            byte[] bytes = document.text().getBytes(StandardCharsets.UTF_8);
            if (range.startByte() < 0 || range.endByte() < range.startByte() || range.endByte() > bytes.length) {
                throw new IllegalArgumentException("Migration candidate range is outside " + range.documentRevisionId());
            }
            SFMTextDocumentRange.positionAtByteOffset(document.text(), range.startByte());
            SFMTextDocumentRange.positionAtByteOffset(document.text(), range.endByte());
            byte[] selected = java.util.Arrays.copyOfRange(bytes, range.startByte(), range.endByte());
            rules.add(new SFMReviewSessionV1.LiteralUtf8Range(
                    range.documentRevisionId(), range.startByte(), range.endByte(), binding.sha256(),
                    SFMReviewSessionV1Kernel.sha256(selected)));
        }
        if (rules.isEmpty()) throw new IllegalArgumentException("Migration replacement requires a range");
        return rules.size() == 1 ? rules.get(0) : new SFMReviewSessionV1.Union(rules);
    }
}
