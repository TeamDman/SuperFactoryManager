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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.UnaryOperator;

/** Application-scoped explicit-path runtime with autosave and truthful dirty state. */
public final class SFMReleaseReviewRuntime implements AutoCloseable {
    private static final ExecutorService PERSISTENCE_EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "sfm-release-review-persistence");
        thread.setDaemon(true);
        return thread;
    });
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

    public record MutationResult(
            boolean saved,
            boolean dirty,
            Optional<String> failure,
            List<String> diagnostics
    ) {
        public MutationResult {
            failure = Objects.requireNonNull(failure, "failure");
            diagnostics = List.copyOf(diagnostics);
        }

        public MutationResult(boolean saved, boolean dirty, Optional<String> failure) {
            this(saved, dirty, failure, failure.map(List::of).orElseGet(List::of));
        }
    }

    /** One atomic identity capture for resolver-backed review projections. */
    public record Snapshot(
            Optional<Path> path,
            Optional<SFMReleaseReviewV1> document,
            Optional<SFMReleaseReviewStore.Access> access,
            long generation,
            long openEpoch,
            boolean dirty
    ) {
        public Snapshot {
            Objects.requireNonNull(path, "path");
            Objects.requireNonNull(document, "document");
            Objects.requireNonNull(access, "access");
            if (generation <= 0) throw new IllegalArgumentException("Review generation must be positive");
            if (path.isPresent() != document.isPresent() || path.isPresent() != access.isPresent()) {
                throw new IllegalArgumentException(
                        "Review path, document, and access must be published atomically");
            }
        }

        public boolean writable() {
            return access.filter(value -> value == SFMReleaseReviewStore.Access.WRITABLE).isPresent();
        }
    }

    public record CommentMutationResult(String commentId, MutationResult mutation) {
        public CommentMutationResult {
            Objects.requireNonNull(commentId, "commentId");
            Objects.requireNonNull(mutation, "mutation");
        }
    }

    public record OperationSnapshot(long id, String kind, Path path, SFMReleaseReviewOperation.Phase phase) { }

    private record PendingWork(long id, String kind, Path path, SFMReleaseReviewOperation control,
                               long submittedNanos) { }

    @FunctionalInterface
    interface StoreOpener {
        SFMReleaseReviewStore open(Path path, SFMReleaseReviewStore.Access access) throws IOException;
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
    private boolean persistencePending;
    private PendingWork pendingWork;
    private long nextOperationId = 1;
    private long generation = 1;
    /**
     * Identity of the currently opened review lease. Unlike {@link #generation}, this does not
     * advance for ordinary document mutations, so resolver-backed views can refresh in place while
     * still distinguishing a later reopen of the same durable path.
     */
    private long openEpoch = 1;
    private final Executor persistenceExecutor;
    private final StoreOpener storeOpener;

    public SFMReleaseReviewRuntime() {
        this(PERSISTENCE_EXECUTOR);
    }

    SFMReleaseReviewRuntime(Executor persistenceExecutor) {
        this(persistenceExecutor, SFMReleaseReviewStore::open);
    }

    SFMReleaseReviewRuntime(Executor persistenceExecutor, StoreOpener storeOpener) {
        this.persistenceExecutor = Objects.requireNonNull(persistenceExecutor, "persistenceExecutor");
        this.storeOpener = Objects.requireNonNull(storeOpener, "storeOpener");
    }

    public static SFMReleaseReviewRuntime get() {
        return INSTANCE;
    }

    /** Stage a replacement without withdrawing the currently committed document or holding its monitor. */
    public CompletableFuture<OpenResult> openAsync(Path path, boolean writable) {
        Path normalized = Objects.requireNonNull(path, "path").toAbsolutePath().normalize();
        Snapshot captured;
        SFMReleaseReviewStore previous;
        PendingWork work;
        synchronized (this) {
            if (persistencePending || dirty) {
                return CompletableFuture.failedFuture(new IllegalStateException(persistencePending
                        ? "A review operation is already pending"
                        : "Save or explicitly discard the current review before replacing it"));
            }
            captured = snapshot();
            previous = store;
            work = beginWork("open", normalized);
        }
        CompletableFuture<OpenResult> completion = new CompletableFuture<>();
        try {
            persistenceExecutor.execute(() -> stageOpen(normalized, writable, captured, previous, work, completion));
        } catch (RuntimeException failure) {
            finishWork(work);
            completion.completeExceptionally(failure);
        }
        return completion;
    }

    private void stageOpen(
            Path path, boolean writable, Snapshot captured, SFMReleaseReviewStore previous,
            PendingWork work, CompletableFuture<OpenResult> completion
    ) {
        SFMReleaseReviewStore replacement = null;
        boolean published = false;
        OpenResult result = null;
        Throwable failure = null;
        long started = operationStarted(work);
        try {
            work.control().checkCancelled();
            var access = writable ? SFMReleaseReviewStore.Access.WRITABLE : SFMReleaseReviewStore.Access.READ_ONLY;
            // Reopening the same writable path must not compete with our own live writer lease.
            replacement = previous != null && previous.path().equals(path) && previous.access() == access
                    ? previous : storeOpener.open(path, access);
            work.control().checkCancelled();
            var loaded = replacement.stageLoad();
            work.control().checkCancelled();
            var status = loaded.document().map(SFMReleaseReviewKernel::completion).orElse(null);
            work.control().checkCancelled();
            result = new OpenResult(loaded.document(), writable, loaded.recoveredMachineLocalCopy(), loaded.diagnostics());
            if (loaded.document().isPresent()) {
                synchronized (this) {
                    if (!workIsCurrent(work, captured, previous)) {
                        throw new CancellationException("Review open was superseded before publication");
                    }
                    long nextEpoch = Math.incrementExact(openEpoch);
                    long nextGeneration = Math.incrementExact(generation);
                    work.control().beginCommit();
                    replacement.acceptLoaded(loaded);
                    store = replacement;
                    document = loaded.document().orElseThrow();
                    openedHash = loaded.openedContentHash();
                    dirty = loaded.recoveredMachineLocalCopy();
                    cachedStatus = status;
                    openEpoch = nextEpoch;
                    generation = nextGeneration;
                    published = true;
                }
            }
        } catch (IOException | RuntimeException caught) {
            failure = caught;
        } finally {
            // File-lock/channel cleanup must not make render-thread snapshot reads wait.
            if (published && previous != null && previous != replacement) previous.close();
            if (!published && replacement != null && replacement != previous) replacement.close();
            finishWork(work);
        }
        operationCompleted(work, started, published ? "published" : "not_published", failure);
        if (failure != null) completion.completeExceptionally(failure);
        else completion.complete(result);
    }

    public synchronized Optional<OperationSnapshot> pendingOperation() {
        return pendingWork == null ? Optional.empty() : Optional.of(new OperationSnapshot(
                pendingWork.id(), pendingWork.kind(), pendingWork.path(), pendingWork.control().phase()));
    }

    /** A false result means absent/stale or already committing; it never claims to undo a write. */
    public synchronized boolean cancelOperation(long operationId) {
        return pendingWork != null && pendingWork.id() == operationId
                && pendingWork.control().requestCancellation();
    }

    private PendingWork beginWork(String kind, Path path) {
        PendingWork work = new PendingWork(nextOperationId, kind, path, new SFMReleaseReviewOperation(), System.nanoTime());
        nextOperationId = Math.incrementExact(nextOperationId);
        pendingWork = work;
        persistencePending = true;
        return work;
    }

    private static long operationStarted(PendingWork work) {
        long started = System.nanoTime();
        ca.teamdman.sfm.SFM.LOGGER.info(
                "SFM_RELEASE_REVIEW_OPERATION_STARTED operation={} kind={} path={} queued_micros={}",
                work.id(), work.kind(), work.path(), (started - work.submittedNanos()) / 1_000L);
        return started;
    }

    private static void operationCompleted(PendingWork work, long started, String outcome, Throwable failure) {
        long finished = System.nanoTime();
        ca.teamdman.sfm.SFM.LOGGER.info(
                "SFM_RELEASE_REVIEW_OPERATION_COMPLETED operation={} kind={} path={} outcome={} worker_micros={} total_micros={} failure_type={}",
                work.id(), work.kind(), work.path(), outcome, (finished - started) / 1_000L,
                (finished - work.submittedNanos()) / 1_000L, failure == null ? "none" : failure.getClass().getSimpleName());
    }

    private synchronized void finishWork(PendingWork work) {
        work.control().complete();
        if (pendingWork == work) {
            pendingWork = null;
            persistencePending = false;
        }
    }

    private boolean workIsCurrent(PendingWork work, Snapshot captured, SFMReleaseReviewStore capturedStore) {
        return pendingWork == work && store == capturedStore && generation == captured.generation()
                && openEpoch == captured.openEpoch()
                && document == captured.document().orElse(null);
    }

    public synchronized OpenResult open(Path path, boolean writable) throws IOException {
        requireNoPendingPersistence("open");
        closeForReplacement();
        SFMReleaseReviewStore replacement = SFMReleaseReviewStore.open(path,
                writable ? SFMReleaseReviewStore.Access.WRITABLE : SFMReleaseReviewStore.Access.READ_ONLY);
        try {
            SFMReleaseReviewStore.LoadResult loaded = replacement.load();
            if (loaded.document().isPresent()) {
                store = replacement;
                replacement = null;
                document = loaded.document().orElseThrow();
                openedHash = loaded.openedContentHash();
                dirty = loaded.recoveredMachineLocalCopy();
            } else {
                document = null;
                openedHash = Optional.empty();
                dirty = false;
            }
            cachedStatus = null;
            advanceOpenEpoch();
            advanceGeneration();
            return new OpenResult(
                    loaded.document(),
                    writable,
                    loaded.recoveredMachineLocalCopy(),
                    loaded.diagnostics()
            );
        } finally {
            if (replacement != null) replacement.close();
        }
    }

    public synchronized void create(Path path, SFMReleaseReviewV1 value) throws IOException {
        requireNoPendingPersistence("create");
        Objects.requireNonNull(value, "value");
        closeForReplacement();
        SFMReleaseReviewStore replacement = SFMReleaseReviewStore.open(
                path,
                SFMReleaseReviewStore.Access.WRITABLE
        );
        try {
            long nextOpenEpoch = Math.incrementExact(openEpoch);
            long nextGeneration = Math.incrementExact(generation);
            SFMReleaseReviewStore.SaveResult saved = replacement.save(value, Optional.empty());
            store = replacement;
            replacement = null;
            document = value;
            openedHash = Optional.of(saved.contentHash());
            cachedStatus = null;
            dirty = false;
            openEpoch = nextOpenEpoch;
            generation = nextGeneration;
        } finally {
            if (replacement != null) replacement.close();
        }
    }

    public synchronized MutationResult mutate(UnaryOperator<SFMReleaseReviewV1> mutation) {
        requireDocument();
        if (persistencePending) return persistencePending("mutate");
        if (store.access() != SFMReleaseReviewStore.Access.WRITABLE) {
            return mutationRejected(
                    "review.read-only",
                    "mutate",
                    "Writable access is required before applying a release-review mutation"
            );
        }
        SFMReleaseReviewV1 updated = Objects.requireNonNull(mutation.apply(document), "mutated document");
        SFMReleaseReviewKernel.validate(updated);
        long nextGeneration = Math.incrementExact(generation);
        try {
            SFMReleaseReviewStore.SaveResult saved = store.save(updated, openedHash);
            document = updated;
            openedHash = Optional.of(saved.contentHash());
            cachedStatus = null;
            dirty = false;
            generation = nextGeneration;
            return new MutationResult(true, false, Optional.empty(), saved.diagnostics());
        } catch (IOException | RuntimeException exception) {
            return mutationFailed("mutate", exception);
        }
    }

    /**
     * Persists one immutable mutation without holding the runtime monitor or
     * blocking the Minecraft client thread during validation/serialization/I/O.
     * The prior committed generation remains authoritative until the atomic
     * store save succeeds and the captured lease identity is still current.
     */
    public CompletableFuture<MutationResult> mutateAsync(
            UnaryOperator<SFMReleaseReviewV1> mutation
    ) {
        return mutateAsync(null, mutation);
    }

    private CompletableFuture<MutationResult> mutateAsync(
            Snapshot expected, UnaryOperator<SFMReleaseReviewV1> mutation
    ) {
        Objects.requireNonNull(mutation, "mutation");
        SFMReleaseReviewStore capturedStore;
        Snapshot captured;
        Optional<String> capturedOpenedHash;
        PendingWork work;
        synchronized (this) {
            requireDocument();
            if (expected != null && (document != expected.document().orElse(null)
                    || openEpoch != expected.openEpoch() || generation != expected.generation())) {
                return CompletableFuture.completedFuture(mutationRejected(
                        "review.stale-mutation", "mutate-async", "The captured review changed before submission"));
            }
            if (persistencePending) {
                return CompletableFuture.completedFuture(persistencePending("mutate-async"));
            }
            if (store.access() != SFMReleaseReviewStore.Access.WRITABLE) {
                return CompletableFuture.completedFuture(mutationRejected(
                        "review.read-only", "mutate-async", "Writable access is required before applying a release-review mutation"));
            }
            capturedStore = store;
            captured = snapshot();
            capturedOpenedHash = openedHash;
            work = beginWork("save", capturedStore.path());
        }
        CompletableFuture<MutationResult> result = new CompletableFuture<>();
        try {
            persistenceExecutor.execute(() -> persistAsyncMutation(
                    mutation, capturedStore, captured, capturedOpenedHash, work, result));
        } catch (RuntimeException failure) {
            finishWork(work);
            result.complete(asyncMutationFailed(captured, "schedule-mutate-async", failure));
        }
        return result;
    }

    public synchronized void saveAs(Path path) throws IOException {
        requireDocument();
        requireNoPendingPersistence("save-as");
        SFMReleaseReviewStore replacement = SFMReleaseReviewStore.open(path, SFMReleaseReviewStore.Access.WRITABLE);
        try {
            SFMReleaseReviewStore.LoadResult existing = replacement.load();
            SFMReleaseReviewStore.SaveResult saved = replacement.save(document, existing.openedContentHash());
            closeStore();
            store = replacement;
            replacement = null;
            openedHash = Optional.of(saved.contentHash());
            dirty = false;
            advanceGeneration();
        } finally {
            if (replacement != null) replacement.close();
        }
    }

    public synchronized MutationResult save() {
        requireDocument();
        if (persistencePending) return persistencePending("save");
        if (store.access() != SFMReleaseReviewStore.Access.WRITABLE) {
            return mutationRejected(
                    "review.read-only",
                    "save",
                    "Writable access is required to save the open release review"
            );
        }
        try {
            SFMReleaseReviewStore.SaveResult saved = store.save(document, openedHash);
            openedHash = Optional.of(saved.contentHash());
            dirty = false;
            return new MutationResult(true, false, Optional.empty(), saved.diagnostics());
        } catch (IOException | RuntimeException exception) {
            return mutationFailed("save", exception);
        }
    }

    public synchronized MutationResult activateQuery(Optional<String> queryId, String expression) {
        return mutate(activateQueryMutation(queryId, expression));
    }

    /** Query evaluation and durable resume-state persistence both run on the persistence worker. */
    public CompletableFuture<MutationResult> activateQueryAsync(Optional<String> queryId, String expression) {
        return mutateAsync(activateQueryMutation(queryId, expression));
    }

    private static UnaryOperator<SFMReleaseReviewV1> activateQueryMutation(
            Optional<String> queryId, String expression
    ) {
        Objects.requireNonNull(queryId, "queryId");
        Objects.requireNonNull(expression, "expression");
        SFMReleaseReviewQuery.parse(expression); // cheap grammar validation before starting an operation
        return current -> {
            SFMReleaseReviewKernel.QueryResult result = SFMReleaseReviewKernel.query(current, expression);
            Optional<String> first = result.reviewUnitIds().stream().findFirst();
            SFMReleaseReviewV1.ResumeState previous = current.resumeState();
            return withResume(current, new SFMReleaseReviewV1.ResumeState(
                    queryId, Optional.of(expression), first, previous.deferredUnitIds(), previous.generation() + 1));
        };
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
        if (delta != 1 && delta != -1) throw new IllegalArgumentException("Work queue movement is one unit at a time");
        return workQueue(delta > 0 ? SFMReleaseReviewWorkQueue.Operation.NEXT
                : SFMReleaseReviewWorkQueue.Operation.PREVIOUS, Optional.empty());
    }

    /** Selects one stable unit from the active query without depending on incidental list position. */
    public synchronized MutationResult selectUnit(String unitId) {
        return workQueue(SFMReleaseReviewWorkQueue.Operation.SELECT, Optional.of(unitId));
    }

    public synchronized MutationResult deferCurrent() {
        return workQueue(SFMReleaseReviewWorkQueue.Operation.DEFER, Optional.empty());
    }

    public synchronized MutationResult resumeDeferred() {
        return workQueue(SFMReleaseReviewWorkQueue.Operation.RESUME, Optional.empty());
    }

    private MutationResult workQueue(SFMReleaseReviewWorkQueue.Operation operation, Optional<String> selected) {
        try {
            return mutate(workQueueMutation(operation, selected));
        } catch (SFMReleaseReviewWorkQueue.Unavailable unavailable) {
            return new MutationResult(false, dirty, Optional.of(unavailable.getMessage()));
        }
    }

    /** Exact captured review identity; query evaluation and the portable save never run on the render thread. */
    public CompletableFuture<MutationResult> workQueueAsync(
            Snapshot expected, SFMReleaseReviewWorkQueue.Operation operation, Optional<String> selected
    ) {
        return mutateAsync(Objects.requireNonNull(expected, "expected"), workQueueMutation(operation, selected));
    }

    private static UnaryOperator<SFMReleaseReviewV1> workQueueMutation(
            SFMReleaseReviewWorkQueue.Operation operation, Optional<String> selected
    ) {
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(selected, "selected");
        return value -> withResume(value, SFMReleaseReviewWorkQueue.transition(value,
                SFMReleaseReviewKernel.query(value, SFMReleaseReviewWorkQueue.expression(value)).reviewUnitIds(),
                operation, selected));
    }

    public record MigrationContext(Snapshot lease, SFMReleaseReviewLedgerResolver.Resolved observation) { }

    /** Captures immutable inputs only; bounded matching belongs on a worker, not the render thread. */
    public synchronized MigrationContext captureMigrationContext() {
        requireDocument();
        if (persistencePending) throw new IllegalStateException("Wait for pending review persistence before previewing migration");
        var observation = store.migrationObservation();
        if (observation.document() != document)
            throw new IllegalStateException("Save or reload the current review before migration");
        return new MigrationContext(snapshot(), observation);
    }

    public CompletableFuture<CommentMutationResult> acceptMigrationSuccessorAsync(
            MigrationContext context, SFMReviewMigrationPlan shown, String note) {
        String successorId;
        synchronized (this) {
            requireDocument();
            successorId = nextHumanCommentId(document.reviewSession());
        }
        return mutateAsync(context.lease(), value -> {
            if (value != context.observation().document())
                throw new IllegalArgumentException("Migration observation changed");
            return SFMReviewMigrationSuccessor.accept(context.observation(), shown, successorId, note);
        }).thenApply(result -> new CommentMutationResult(successorId, result));
    }

    /** Adds one ordinary v2 comment and its exact selector provenance in the same atomic save. */
    public synchronized CommentMutationResult createComment(
            String text,
            SFMReleaseReviewV1.PinnedSelection capturedSelection,
            SFMReleaseReviewV1.SelectorProposal selectedProposal
    ) {
        PreparedComment prepared = prepareComment(text, capturedSelection, selectedProposal);
        return new CommentMutationResult(prepared.commentId(), mutate(prepared.mutation()));
    }

    /** Asynchronous counterpart used by render-thread comment-choice actions. */
    public CompletableFuture<CommentMutationResult> createCommentAsync(
            String text,
            SFMReleaseReviewV1.PinnedSelection capturedSelection,
            SFMReleaseReviewV1.SelectorProposal selectedProposal
    ) {
        PreparedComment prepared;
        Snapshot captured;
        synchronized (this) {
            prepared = prepareComment(text, capturedSelection, selectedProposal);
            captured = snapshot();
        }
        return mutateAsync(captured, prepared.mutation())
                .thenApply(mutation -> new CommentMutationResult(prepared.commentId(), mutation));
    }

    private PreparedComment prepareComment(
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
        // Captures intentionally produce the same proposal id for the same
        // selection. Durable selector bindings, however, are independently
        // addressable by migration reports: qualify this instance by comment.
        var boundProposal = new SFMReleaseReviewV1.SelectorProposal(
                "comment-selector:sha256:" + SFMReviewSessionV1Kernel.sha256(
                        (commentId + "\n" + selectedProposal.id()).getBytes(StandardCharsets.UTF_8)),
                selectedProposal.kind(), selectedProposal.selectionRule(), selectedProposal.literalWitness(),
                selectedProposal.semanticProvider(), selectedProposal.semanticKey(), selectedProposal.semanticProvenance(),
                selectedProposal.confidence(), selectedProposal.projectionFingerprint(),
                selectedProposal.sourceSnapshotId(), selectedProposal.diagnostics());
        UnaryOperator<SFMReleaseReviewV1> mutation = value -> {
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
                    boundProposal
            ));
            return new SFMReleaseReviewV1(
                    value.schema(), session, value.repositoryBindings(), value.corpusDocuments(),
                    value.reviewUnits(), bindings, value.migrationReports(), value.namedQueries(),
                    value.resumeState(), value.producerGenerations(), value.completionAttestations()
            );
        };
        return new PreparedComment(commentId, mutation);
    }

    private record PreparedComment(
            String commentId,
            UnaryOperator<SFMReleaseReviewV1> mutation
    ) {
    }

    /** Continue saving a freeform editor after its initial one-shot draft has become a durable comment. */
    public CompletableFuture<MutationResult> updateCommentAsync(
            Path reviewPath, long reviewEpoch, String commentId, String expectedText, String text
    ) {
        Snapshot captured = snapshot();
        if (captured.openEpoch() != reviewEpoch
                || !captured.path().filter(reviewPath.toAbsolutePath().normalize()::equals).isPresent()) {
            return CompletableFuture.failedFuture(new IllegalStateException(
                    "The review was replaced after this comment editor was opened"));
        }
        if (text.isBlank()) return CompletableFuture.failedFuture(new IllegalArgumentException("Comment text must not be blank"));
        return mutateAsync(captured, value -> {
            var comments = new ArrayList<>(value.reviewSession().comments());
            int found = -1;
            for (int index = 0; index < comments.size(); index++) {
                var comment = comments.get(index);
                if (!comment.id().equals(commentId)) continue;
                if (!comment.text().equals(expectedText)) throw new IllegalStateException(
                        "Comment changed outside this editor; reopen its current value before saving");
                comments.set(index, new SFMReviewSessionV2.Comment(
                        comment.id(), text, comment.provenance(), comment.target()));
                found = index;
                break;
            }
            if (found < 0) throw new IllegalStateException("The saved comment is no longer present: " + commentId);
            var previous = value.reviewSession();
            var session = new SFMReviewSessionV2(previous.schema(), previous.id(), previous.title(),
                    previous.coordinateSystem(), previous.revisionLanes(), comments, previous.styleRules(),
                    previous.completionPolicy());
            return new SFMReleaseReviewV1(value.schema(), session, value.repositoryBindings(), value.corpusDocuments(),
                    value.reviewUnits(), value.selectorBindings(), value.migrationReports(), value.namedQueries(),
                    value.resumeState(), value.producerGenerations(), value.completionAttestations());
        });
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

    public synchronized Snapshot snapshot() {
        return new Snapshot(
                store == null ? Optional.empty() : Optional.of(store.path()),
                Optional.ofNullable(document),
                store == null ? Optional.empty() : Optional.of(store.access()),
                generation,
                openEpoch,
                dirty
        );
    }

    /** Exact opened file witness paired atomically with the published observation. */
    public record AuthoritySnapshot(Snapshot observation, Optional<String> contentHash) { }

    public synchronized AuthoritySnapshot authoritySnapshot() {
        return new AuthoritySnapshot(snapshot(), openedHash);
    }

    public synchronized long generation() {
        return generation;
    }

    public synchronized Optional<Path> path() {
        return store == null ? Optional.empty() : Optional.of(store.path());
    }

    public synchronized boolean dirty() {
        return dirty;
    }

    public synchronized boolean persistencePending() {
        return persistencePending;
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
    public void close() {
        SFMReleaseReviewStore closedStore;
        synchronized (this) {
            if (dirty) {
                throw new IllegalStateException(
                        "Release-review document has unsaved changes; save or explicitly discard before closing");
            }
            closedStore = detachForClose();
        }
        if (closedStore != null) closedStore.close();
    }

    /** Explicitly abandons unsaved in-memory/recovery state and releases the writer lease. */
    public void discardAndClose() {
        SFMReleaseReviewStore closedStore;
        synchronized (this) {
            closedStore = detachForClose();
        }
        if (closedStore != null) closedStore.close();
    }

    private SFMReleaseReviewStore detachForClose() {
        if (pendingWork != null && !pendingWork.control().requestCancellation()) {
            throw new IllegalStateException(
                    "Release-review commit is in progress; wait for its durable outcome before closing"
            );
        }
        pendingWork = null;
        persistencePending = false;
        boolean changed = store != null || document != null;
        SFMReleaseReviewStore closedStore = store;
        store = null;
        document = null;
        openedHash = Optional.empty();
        cachedStatus = null;
        dirty = false;
        if (changed) {
            advanceOpenEpoch();
            advanceGeneration();
        }
        return closedStore;
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

    private void requireNoPendingPersistence(String operation) throws IOException {
        if (persistencePending) {
            throw new IOException("Release-review persistence is still pending; cannot " + operation);
        }
    }

    private MutationResult persistencePending(String operation) {
        return mutationRejected(
                "review.persistence-pending",
                operation,
                "Wait for the current atomic review save to finish"
        );
    }

    private void persistAsyncMutation(
            UnaryOperator<SFMReleaseReviewV1> mutation,
            SFMReleaseReviewStore capturedStore,
            Snapshot captured,
            Optional<String> capturedOpenedHash,
            PendingWork work,
            CompletableFuture<MutationResult> completion
    ) {
        SFMReleaseReviewV1 updated;
        SFMReleaseReviewStore.SaveResult saved;
        SFMReleaseReviewKernel.CompletionReport status;
        long started = operationStarted(work);
        try {
            work.control().checkCancelled();
            updated = Objects.requireNonNull(mutation.apply(captured.document().orElseThrow()), "mutated document");
            work.control().checkCancelled();
            SFMReleaseReviewKernel.validate(updated);
            status = SFMReleaseReviewKernel.completion(updated);
            work.control().checkCancelled();
            saved = capturedStore.save(updated, capturedOpenedHash, work.control()::beginCommit);
        } catch (IOException | RuntimeException failure) {
            finishWork(work);
            operationCompleted(work, started, "not_saved", failure);
            completion.complete(asyncMutationFailed(captured, "mutate-async", failure));
            return;
        }

        MutationResult result;
        synchronized (this) {
            boolean identityCurrent = workIsCurrent(work, captured, capturedStore)
                    && openedHash.equals(capturedOpenedHash);
            if (!identityCurrent) {
                // Commit authority blocks replacement. If that invariant is ever broken, the
                // completed write is still a fact: do not tell the user their bytes were unsaved.
                result = new MutationResult(true, false, Optional.empty(), List.of(
                        "Review authority saved at " + capturedStore.path()
                                + " but runtime identity changed before publication"));
            } else {
                document = updated;
                openedHash = Optional.of(saved.contentHash());
                cachedStatus = status;
                dirty = false;
                generation = Math.incrementExact(captured.generation());
                result = new MutationResult(true, false, Optional.empty(), saved.diagnostics());
            }
        }
        finishWork(work);
        operationCompleted(work, started, "saved", null);
        completion.complete(result);
    }

    private static MutationResult asyncMutationFailed(Snapshot captured, String operation, Throwable failure) {
        if (failure instanceof SFMReleaseReviewWorkQueue.Unavailable) {
            return new MutationResult(false, captured.dirty(), Optional.of(failure.getMessage()),
                    List.of("review.work-queue-unavailable"));
        }
        String code = failure instanceof CancellationException ? "review.cancelled"
                : failure instanceof SFMReleaseReviewStore.ExternalEditConflict ? "review.external-edit-conflict"
                : failure instanceof IOException ? "review.io-failure" : "review.serialization-failure";
        String diagnostic = "Release-review mutation was not published code=" + code
                + " operation=" + operation + " path=" + captured.path().orElseThrow()
                + " captured_generation=" + captured.generation()
                + " failure_type=" + failure.getClass().getSimpleName()
                + " message=" + failureMessage(failure);
        return new MutationResult(false, captured.dirty(), Optional.of(diagnostic), List.of(diagnostic));
    }

    private void advanceGeneration() {
        generation = Math.incrementExact(generation);
    }

    private void advanceOpenEpoch() {
        openEpoch = Math.incrementExact(openEpoch);
    }

    private MutationResult mutationFailed(String operation, Throwable failure) {
        String code = failure instanceof SFMReleaseReviewStore.ExternalEditConflict
                ? "review.external-edit-conflict"
                : failure instanceof IOException
                        ? "review.io-failure"
                        : "review.serialization-failure";
        return mutationRejected(
                code,
                operation,
                "failure_type=" + failure.getClass().getSimpleName()
                        + " message=" + failureMessage(failure)
        );
    }

    private MutationResult mutationRejected(String code, String operation, String detail) {
        String diagnostic = "Release-review mutation was not published"
                + " code=" + code
                + " operation=" + operation
                + " path=" + store.path()
                + " access=" + store.access()
                + " published_generation=" + generation
                + " published_dirty=" + dirty
                + " detail=" + detail;
        return new MutationResult(false, dirty, Optional.of(diagnostic), List.of(diagnostic));
    }

    private static String failureMessage(Throwable failure) {
        String message = failure.getMessage();
        return message == null || message.isBlank() ? "<no message>" : message;
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
