package ca.teamdman.sfm.client.explorer.action;

import ca.teamdman.sfm.client.explorer.SFMChildRelationRepository;
import ca.teamdman.sfm.client.explorer.SFMChildRelationRevision;
import ca.teamdman.sfm.client.explorer.SFMEntitySelectorResolver;
import ca.teamdman.sfm.client.explorer.SFMExplorerId;
import ca.teamdman.sfm.client.explorer.SFMPath;
import ca.teamdman.sfm.client.explorer.SFMPathHierarchy;
import ca.teamdman.sfm.client.explorer.SFMPathExpressionResolution;
import ca.teamdman.sfm.client.explorer.SFMPathExpressionResolver;
import ca.teamdman.sfm.client.explorer.SFMSelection;
import ca.teamdman.sfm.client.explorer.SFMSelectionId;
import ca.teamdman.sfm.client.explorer.SFMSelectionRepository;
import ca.teamdman.sfm.client.explorer.SFMSelectorDomains;
import ca.teamdman.sfm.client.explorer.SFMSelectorResolution;
import ca.teamdman.sfm.client.explorer.action.SFMExplorerActionRequest.Operation;
import ca.teamdman.sfm.client.explorer.lazy.SFMExplorerPathReveal;
import ca.teamdman.sfm.client.explorer.lazy.SFMExplorerProjection;
import ca.teamdman.sfm.client.explorer.lazy.SFMExplorerSession;
import ca.teamdman.sfm.client.explorer.lazy.SFMLazyExplorerLoader;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Supplier;

/**
 * Pure-preflight, revalidated, all-or-none transaction engine for explorers.
 *
 * <p>Preparation evaluates one immutable selector snapshot and never invokes
 * the explorer factory. Publication rechecks the registry and every captured
 * session revision under deterministic locks before changing any target.</p>
 */
public final class SFMExplorerActionEngine {
    @FunctionalInterface
    public interface ExplorerFactory {
        SFMExplorerRepository.Explorer create(SFMPath initialRoot);
    }

    @FunctionalInterface
    public interface CommittedOperationHook {
        void beforeSideEffects(Operation operation);
    }

    @FunctionalInterface
    public interface LocationAuthorityPolicy {
        Optional<String> incompatibility(SFMPath path);
    }

    public final class PreparedAction {
        private final SFMExplorerActionRequest request;
        private final SFMSelectorResolution<SFMExplorerId> selectorResolution;
        private final long registryGeneration;
        private final List<TargetPlan> targets;
        private final boolean createOnPublish;
        private final SFMExplorerActionResult.Status terminalStatus;
        private final List<String> diagnostics;
        private SFMExplorerActionResult published;

        private PreparedAction(
                SFMExplorerActionRequest request,
                SFMSelectorResolution<SFMExplorerId> selectorResolution,
                long registryGeneration,
                List<TargetPlan> targets,
                boolean createOnPublish,
                SFMExplorerActionResult.Status terminalStatus,
                List<String> diagnostics
        ) {
            this.request = request;
            this.selectorResolution = selectorResolution;
            this.registryGeneration = registryGeneration;
            this.targets = List.copyOf(targets);
            this.createOnPublish = createOnPublish;
            this.terminalStatus = terminalStatus;
            this.diagnostics = List.copyOf(diagnostics);
        }

        public SFMExplorerActionRequest request() { return request; }

        public SFMSelectorResolution<SFMExplorerId> selectorResolution() { return selectorResolution; }

        public long registryGeneration() { return registryGeneration; }

        public List<SFMExplorerId> targetIds() {
            return targets.stream().map(TargetPlan::id).toList();
        }

        /** Immutable pre-publication evidence used by bounded transports. */
        public List<PreparedTarget> preparedTargets() {
            return targets.stream()
                    .map(target -> new PreparedTarget(target.id(), target.snapshot()))
                    .toList();
        }

        public boolean createsExplorerOnPublish() { return createOnPublish; }

        public List<String> diagnostics() { return diagnostics; }
    }

    public record PreparedTarget(SFMExplorerId id, SFMExplorerSession.Snapshot snapshot) {
        public PreparedTarget {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(snapshot, "snapshot");
        }
    }

    private record TargetPlan(
            SFMExplorerId id,
            SFMExplorerRepository.Explorer explorer,
            SFMExplorerSession.Snapshot snapshot,
            SFMExplorerActionResult.RevisionEvidence evidence
    ) {
    }

    private record AppliedTarget(
            TargetPlan plan,
            SFMExplorerActionResult.TargetOutcome outcome,
            SFMExplorerActionResult.RevisionEvidence after,
            SFMExplorerSession.Snapshot snapshot,
            Optional<SFMLazyExplorerLoader.RequestEvidence> loadRequest,
            List<String> diagnostics
    ) {
    }

    private record StateAppliedTarget(TargetPlan plan, boolean changed) {
    }

    private final SFMExplorerRepository repository;
    private final ExplorerFactory factory;
    private final CommittedOperationHook committedOperationHook;
    private final LocationAuthorityPolicy locationAuthorityPolicy;

    public SFMExplorerActionEngine(SFMExplorerRepository repository, ExplorerFactory factory) {
        this(repository, factory, ignored -> { }, ignored -> Optional.empty());
    }

    public SFMExplorerActionEngine(
            SFMExplorerRepository repository,
            ExplorerFactory factory,
            CommittedOperationHook committedOperationHook
    ) {
        this(repository, factory, committedOperationHook, ignored -> Optional.empty());
    }

    public SFMExplorerActionEngine(
            SFMExplorerRepository repository,
            ExplorerFactory factory,
            CommittedOperationHook committedOperationHook,
            LocationAuthorityPolicy locationAuthorityPolicy
    ) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.factory = Objects.requireNonNull(factory, "factory");
        this.committedOperationHook = Objects.requireNonNull(committedOperationHook, "committedOperationHook");
        this.locationAuthorityPolicy = Objects.requireNonNull(locationAuthorityPolicy, "locationAuthorityPolicy");
    }

    public SFMExplorerActionResult execute(SFMExplorerActionRequest request) {
        return publish(prepare(request));
    }

    /** Captures targets and performs compatibility checks without mutation. */
    public PreparedAction prepare(SFMExplorerActionRequest request) {
        Objects.requireNonNull(request, "request");
        synchronized (repository) {
            SFMSelectorResolution<SFMExplorerId> resolution = SFMEntitySelectorResolver.resolve(
                    request.selector(),
                    SFMSelectorDomains.explorers(repository)
            );
            long generation = resolution.repositoryGeneration();
            ArrayList<String> diagnostics = new ArrayList<>();
            resolution.diagnostics().forEach(diagnostic -> diagnostics.add(
                    diagnostic.code() + ": " + diagnostic.message()
            ));
            if (!resolution.complete()) {
                return prepared(
                        request, resolution, generation, List.of(), false,
                        SFMExplorerActionResult.Status.REJECTED, diagnostics
                );
            }

            ArrayList<TargetPlan> plans = new ArrayList<>();
            boolean incompatible = false;
            for (SFMExplorerId id : resolution.identities()) {
                SFMExplorerRepository.Explorer explorer = repository.find(id).orElse(null);
                if (explorer == null) {
                    diagnostics.add("explorer.stale-registry: captured explorer disappeared");
                    return prepared(
                            request, resolution, generation, plans, false,
                            SFMExplorerActionResult.Status.STALE, diagnostics
                    );
                }
                SFMExplorerSession.Snapshot snapshot = explorer.session().snapshot();
                Optional<String> incompatibility = preflight(explorer, snapshot, request.operation());
                plans.add(new TargetPlan(id, explorer, snapshot, evidence(explorer, snapshot)));
                if (incompatibility.isPresent()) {
                    diagnostics.add(id.value() + ": " + incompatibility.orElseThrow());
                    incompatible = true;
                }
            }

            if (incompatible) {
                return prepared(
                        request, resolution, generation, plans, false,
                        SFMExplorerActionResult.Status.REJECTED, diagnostics
                );
            }

            if (plans.isEmpty()) {
                if (request.ifNoMatch() == SFMExplorerActionRequest.IfNoMatch.OPEN_NEW) {
                    return prepared(request, resolution, generation, plans, true, null, diagnostics);
                }
                return prepared(
                        request, resolution, generation, plans, false,
                        SFMExplorerActionResult.Status.NO_TARGETS, diagnostics
                );
            }
            return prepared(request, resolution, generation, plans, false, null, diagnostics);
        }
    }

    /** Publishes a prepared action at most once; repeated calls return the same evidence. */
    public SFMExplorerActionResult publish(PreparedAction prepared) {
        Objects.requireNonNull(prepared, "prepared");
        synchronized (prepared) {
            if (prepared.published != null) return prepared.published;
            SFMExplorerActionResult result = publishOnce(prepared);
            prepared.published = result;
            return result;
        }
    }

    private SFMExplorerActionResult publishOnce(PreparedAction prepared) {
        if (prepared.terminalStatus != null) {
            return terminal(prepared, prepared.terminalStatus, prepared.diagnostics);
        }
        if (prepared.createOnPublish) return publishCreate(prepared);

        synchronized (repository) {
            if (repository.generation() != prepared.registryGeneration) {
                return stale(prepared, "explorer.stale-registry: registry generation changed before publication");
            }
            for (TargetPlan plan : prepared.targets) {
                if (!repository.isCurrent(plan.id(), plan.explorer())) {
                    return stale(prepared, "explorer.stale-registry: captured explorer binding changed");
                }
            }
            List<SFMExplorerSession> sessions = prepared.targets.stream()
                    .map(plan -> plan.explorer().session())
                    .sorted(Comparator.comparing(session -> session.snapshot().id().value()))
                    .toList();
            return withLocks(sessions, 0, () -> withSelectionLocks(
                    selectionRepositories(prepared.targets),
                    0,
                    () -> publishLocked(prepared)
            ));
        }
    }

    private SFMExplorerActionResult publishLocked(PreparedAction prepared) {
        ArrayList<String> incompatibilities = new ArrayList<>();
        for (TargetPlan plan : prepared.targets) {
            if (!plan.explorer().session().hasRevision(plan.snapshot().revision())) {
                return stale(prepared, "explorer.stale-session: session revision changed before publication");
            }
            if (touchesSelections(prepared.request.operation())
                    && plan.explorer().session().selectionRepository().stateSnapshot().generation()
                    != plan.evidence().selectionRepositoryGeneration()) {
                return stale(prepared, "explorer.stale-selection: selection repository changed before publication");
            }
            Optional<String> incompatibility = preflight(
                    plan.explorer(),
                    plan.explorer().session().snapshot(),
                    prepared.request.operation()
            );
            if (incompatibility.isPresent()) {
                incompatibilities.add(plan.id().value() + ": " + incompatibility.orElseThrow());
            }
        }
        if (!incompatibilities.isEmpty()) {
            ArrayList<String> diagnostics = new ArrayList<>(prepared.diagnostics);
            diagnostics.addAll(incompatibilities);
            return terminal(prepared, SFMExplorerActionResult.Status.REJECTED, diagnostics);
        }

        try {
            for (TargetPlan plan : prepared.targets) {
                preflightCommittedSideEffects(plan, prepared.request.operation());
            }
        } catch (RuntimeException failure) {
            String message = failure.getMessage() == null
                    ? failure.getClass().getSimpleName()
                    : failure.getMessage();
            return rejected(prepared, "explorer.side-effect-preflight-failed: " + message);
        }

        IdentityHashMap<SFMExplorerSession, SFMExplorerSession.TransactionSnapshot> sessionSnapshots =
                new IdentityHashMap<>();
        IdentityHashMap<SFMSelectionRepository, SFMSelectionRepository.TransactionSnapshot> selectionSnapshots =
                new IdentityHashMap<>();
        for (TargetPlan plan : prepared.targets) {
            SFMExplorerSession session = plan.explorer().session();
            sessionSnapshots.put(session, session.transactionSnapshot());
            SFMSelectionRepository selectionRepository = session.selectionRepository();
            selectionSnapshots.computeIfAbsent(
                    selectionRepository,
                    ignored -> selectionRepository.transactionSnapshot()
            );
        }

        ArrayList<StateAppliedTarget> stateApplied = new ArrayList<>();
        try {
            for (TargetPlan plan : prepared.targets) {
                stateApplied.add(applyState(plan, prepared.request.operation()));
            }
            committedOperationHook.beforeSideEffects(prepared.request.operation());
        } catch (RuntimeException failure) {
            selectionSnapshots.forEach(SFMSelectionRepository::restoreTransactionSnapshot);
            sessionSnapshots.forEach(SFMExplorerSession::restoreTransactionSnapshot);
            String message = failure.getMessage() == null
                    ? failure.getClass().getSimpleName()
                    : failure.getMessage();
            return rejected(prepared, "explorer.transaction-aborted: " + message);
        }

        ArrayList<AppliedTarget> applied = new ArrayList<>();
        for (StateAppliedTarget target : stateApplied) {
            applied.add(performCommittedSideEffects(target, prepared.request.operation()));
        }
        ArrayList<SFMExplorerActionResult.TargetResult> targets = new ArrayList<>();
        ArrayList<String> diagnostics = new ArrayList<>(prepared.diagnostics);
        for (AppliedTarget target : applied) {
            diagnostics.addAll(target.diagnostics());
            targets.add(new SFMExplorerActionResult.TargetResult(
                    target.plan().id(),
                    target.outcome(),
                    Optional.of(target.plan().evidence()),
                    target.after(),
                    target.snapshot(),
                    target.loadRequest(),
                    target.diagnostics()
            ));
        }
        return result(
                prepared,
                SFMExplorerActionResult.Status.SUCCEEDED,
                repository.generation(),
                targets,
                diagnostics
        );
    }

    private SFMExplorerActionResult publishCreate(PreparedAction prepared) {
        SFMExplorerActionRequest.RootAdd rootAdd = (SFMExplorerActionRequest.RootAdd) prepared.request.operation();
        synchronized (repository) {
            if (repository.generation() != prepared.registryGeneration) {
                return stale(prepared, "explorer.stale-registry: registry generation changed before creation");
            }
            SFMExplorerRepository.Explorer created;
            try {
                created = Objects.requireNonNull(factory.create(rootAdd.path()), "explorer factory result");
                SFMExplorerSession.Snapshot snapshot = created.session().snapshot();
                if (snapshot.closed()) throw new IllegalArgumentException("Factory returned a closed explorer");
                if (!snapshot.roots().equals(Set.of(rootAdd.path()))) {
                    throw new IllegalArgumentException("Factory must return an explorer with exactly the requested root");
                }
                Optional<String> incompatibility = created.pathPolicy().incompatibility(rootAdd.path());
                if (incompatibility.isPresent()) throw new IllegalArgumentException(incompatibility.orElseThrow());
                if (repository.find(snapshot.id()).isPresent()) {
                    throw new IllegalArgumentException("Factory returned an already registered explorer id");
                }
                committedOperationHook.beforeSideEffects(prepared.request.operation());
            } catch (RuntimeException failure) {
                return rejected(prepared, "explorer.factory-failed: " + failure.getMessage());
            }

            repository.register(created, true);
            ArrayList<String> diagnostics = new ArrayList<>(prepared.diagnostics);
            try {
                created.loader().openRoot(rootAdd.path());
            } catch (RuntimeException failure) {
                diagnostics.add("explorer.root-describe-failed: " + failure.getMessage());
            }
            SFMExplorerSession.Snapshot snapshot = created.session().snapshot();
            SFMExplorerActionResult.RevisionEvidence after = evidence(created, snapshot);
            SFMExplorerActionResult.TargetResult target = new SFMExplorerActionResult.TargetResult(
                    snapshot.id(),
                    SFMExplorerActionResult.TargetOutcome.CREATED,
                    Optional.empty(),
                    after,
                    snapshot,
                    Optional.empty(),
                    diagnostics
            );
            return result(
                    prepared,
                    SFMExplorerActionResult.Status.SUCCEEDED,
                    repository.generation(),
                    List.of(target),
                    diagnostics
            );
        }
    }

    /** Changes only reversible session/selection state; no resolver work starts here. */
    private StateAppliedTarget applyState(TargetPlan plan, Operation operation) {
        SFMExplorerRepository.Explorer explorer = plan.explorer();
        SFMExplorerSession session = explorer.session();
        boolean changed = false;

        if (operation instanceof SFMExplorerActionRequest.NodeExpand expand) {
            changed = session.expand(expand.path());
        } else if (operation instanceof SFMExplorerActionRequest.NodeCollapse collapse) {
            changed = session.collapseWithoutCancelling(collapse.path());
        } else if (operation instanceof SFMExplorerActionRequest.NodeToggle toggle) {
            if (plan.snapshot().expanded().contains(toggle.path())) {
                changed = session.collapseWithoutCancelling(toggle.path());
            } else {
                changed = session.expand(toggle.path());
            }
        } else if (operation instanceof SFMExplorerActionRequest.RootAdd add) {
            changed = session.addRoot(add.path());
        } else if (operation instanceof SFMExplorerActionRequest.RootRemove remove) {
            changed = session.removeRootWithoutCancelling(remove.path());
        } else if (operation instanceof SFMExplorerActionRequest.LocationSet set) {
            changed = session.replaceLocation(set.expression(), resolveLocation(explorer, set).paths());
        } else if (operation instanceof SFMExplorerActionRequest.ViewSet set) {
            changed = session.snapshot().settings().view() != set.view();
            session.setView(set.view());
        } else if (operation instanceof SFMExplorerActionRequest.SortSet set) {
            changed = session.snapshot().settings().sort() != set.sort();
            session.setSort(set.sort());
        } else if (operation instanceof SFMExplorerActionRequest.GroupSet set) {
            changed = session.snapshot().settings().group() != set.group();
            session.setGroup(set.group());
        } else if (operation instanceof SFMExplorerActionRequest.HoistSet set) {
            changed = session.snapshot().settings().hoist() != set.hoist();
            session.setHoist(set.hoist());
        } else if (operation instanceof SFMExplorerActionRequest.PathDisplaySet set) {
            changed = session.snapshot().settings().pathDisplay() != set.pathDisplay();
            session.setPathDisplay(set.pathDisplay());
        } else if (operation instanceof SFMExplorerActionRequest.FilterSet set) {
            changed = !session.snapshot().settings().filterQuery().equals(set.query())
                    || !session.snapshot().settings().filterOptions().equals(set.options());
            session.setFilterOptions(set.options());
            session.setFilterQuery(set.query());
        } else if (operation instanceof SFMExplorerActionRequest.FilterClear) {
            changed = !session.snapshot().settings().filterQuery().isEmpty();
            session.setFilterQuery("");
        } else if (operation instanceof SFMExplorerActionRequest.FindSet set) {
            session.beginFinderQuery(set.query(), set.options());
            changed = true;
        } else if (operation instanceof SFMExplorerActionRequest.FindNext) {
            changed = session.advanceFinder(1);
        } else if (operation instanceof SFMExplorerActionRequest.FindPrevious) {
            changed = session.advanceFinder(-1);
        } else if (operation instanceof SFMExplorerActionRequest.FindClear) {
            changed = session.clearFinder();
        }

        return new StateAppliedTarget(plan, changed);
    }

    /** Starts irreversible resolver work only after every target's state committed. */
    private AppliedTarget performCommittedSideEffects(StateAppliedTarget state, Operation operation) {
        TargetPlan plan = state.plan();
        SFMExplorerRepository.Explorer explorer = plan.explorer();
        SFMExplorerSession session = explorer.session();
        Optional<SFMLazyExplorerLoader.RequestEvidence> loadRequest = Optional.empty();
        ArrayList<String> diagnostics = new ArrayList<>();

        try {
            if (operation instanceof SFMExplorerActionRequest.NodeExpand expand) {
                if (!materialized(explorer, expand.path())) {
                    loadRequest = Optional.of(session.requestChildren(
                            expand.path(), explorer.loader(), expand.pageSize()
                    ).evidence());
                }
            } else if (operation instanceof SFMExplorerActionRequest.NodeCollapse collapse) {
                session.cancelChildrenRequest(collapse.path());
            } else if (operation instanceof SFMExplorerActionRequest.NodeToggle toggle) {
                if (plan.snapshot().expanded().contains(toggle.path())) {
                    session.cancelChildrenRequest(toggle.path());
                } else if (!materialized(explorer, toggle.path())) {
                    loadRequest = Optional.of(session.requestChildren(
                            toggle.path(), explorer.loader(), toggle.pageSize()
                    ).evidence());
                }
            } else if (operation instanceof SFMExplorerActionRequest.NodeRefresh refresh) {
                loadRequest = Optional.of(session.requestChildren(
                        refresh.path(), explorer.loader(), refresh.pageSize()
                ).evidence());
            } else if (operation instanceof SFMExplorerActionRequest.RootAdd add && state.changed()) {
                explorer.loader().openRoot(add.path());
            } else if (operation instanceof SFMExplorerActionRequest.RootRemove remove && state.changed()) {
                session.cancelChildrenRequest(remove.path());
            } else if (operation instanceof SFMExplorerActionRequest.LocationSet && state.changed()) {
                Set<SFMPath> before = plan.snapshot().roots();
                Set<SFMPath> after = session.snapshot().roots();
                before.stream().filter(path -> !after.contains(path)).forEach(session::cancelChildrenRequest);
                after.stream().filter(path -> !before.contains(path)).forEach(explorer.loader()::openRoot);
            } else if (operation instanceof SFMExplorerActionRequest.FindSet set) {
                startFinderQuery(explorer, set);
            } else if (operation instanceof SFMExplorerActionRequest.FindNext
                    || operation instanceof SFMExplorerActionRequest.FindPrevious) {
                revealFinderMatch(explorer, diagnostics);
            }
        } catch (RuntimeException failure) {
            String message = failure.getMessage() == null
                    ? failure.getClass().getSimpleName()
                    : failure.getMessage();
            diagnostics.add("explorer.committed-side-effect-failed: " + message);
        }

        SFMExplorerSession.Snapshot afterSnapshot = session.snapshot();
        SFMExplorerActionResult.TargetOutcome outcome;
        if (loadRequest.isPresent()) {
            outcome = SFMExplorerActionResult.TargetOutcome.REFRESH_REQUESTED;
        } else if (!operation.mutatesSession()) {
            outcome = SFMExplorerActionResult.TargetOutcome.DESCRIBED;
        } else {
            outcome = state.changed()
                    ? SFMExplorerActionResult.TargetOutcome.APPLIED
                    : SFMExplorerActionResult.TargetOutcome.UNCHANGED;
        }
        return new AppliedTarget(
                plan,
                outcome,
                evidence(explorer, afterSnapshot),
                afterSnapshot,
                loadRequest,
                diagnostics
        );
    }

    /** Starts query-specific work without publishing anything into the ordinary Explorer relation. */
    private static void startFinderQuery(
            SFMExplorerRepository.Explorer explorer,
            SFMExplorerActionRequest.FindSet operation
    ) {
        SFMExplorerSession session = explorer.session();
        SFMExplorerSession.Snapshot snapshot = session.snapshot();
        long finderGeneration = snapshot.finder().generation();
        List<SFMPath> roots = snapshot.roots().stream().sorted().toList();
        ArrayList<CompletableFuture<SFMLazyExplorerLoader.LoadDisposition>> completions = new ArrayList<>();
        for (SFMPath root : roots) {
            Optional<CompletableFuture<SFMLazyExplorerLoader.LoadDisposition>> completion =
                    explorer.loader().ensureFinderDomain(root, operation.query(), operation.options());
            if (completion.isEmpty()) {
                session.failFinderQuery(
                        finderGeneration,
                        operation.query(),
                        "Explorer resolver for " + root.scheme() + " does not support asynchronous finding"
                );
                return;
            }
            completions.add(completion.orElseThrow());
        }

        CompletableFuture.allOf(completions.toArray(CompletableFuture[]::new)).whenComplete((ignored, failure) -> {
            if (failure != null) {
                session.failFinderQuery(
                        finderGeneration,
                        operation.query(),
                        "Explorer find failed: " + failureMessage(failure)
                );
                return;
            }
            Optional<SFMLazyExplorerLoader.LoadDisposition> unsuccessful = completions.stream()
                    .map(completion -> completion.getNow(SFMLazyExplorerLoader.LoadDisposition.FAILED))
                    .filter(disposition -> disposition != SFMLazyExplorerLoader.LoadDisposition.PUBLISHED)
                    .findFirst();
            if (unsuccessful.isPresent()) {
                String detail = snapshot.roots().stream()
                        .map(root -> explorer.loader().filterDomainFailure(root, operation.query(), operation.options()))
                        .flatMap(Optional::stream).findFirst().map(message -> ": " + message).orElse("");
                session.failFinderQuery(
                        finderGeneration,
                        operation.query(),
                        "Explorer find domain was "
                                + unsuccessful.orElseThrow().name().toLowerCase(java.util.Locale.ROOT) + detail
                );
                return;
            }

            Optional<SFMLazyExplorerLoader.FilterProjection> projection = explorer.loader().filterProjection(
                    snapshot.roots(),
                    operation.query(),
                    Set.of(),
                    operation.options()
            );
            if (projection.isEmpty()) {
                session.failFinderQuery(
                        finderGeneration,
                        operation.query(),
                        "Explorer find completed without publishing an exact query projection"
                );
                return;
            }
            SFMLazyExplorerLoader.FilterProjection published = projection.orElseThrow();
            session.publishFinderResultsInDisplayOrder(
                    finderGeneration,
                    operation.query(),
                    ca.teamdman.sfm.client.explorer.lazy.SFMExplorerMatchTraversal.order(
                            published.matchedPaths(), snapshot, published.relations(), published.entries()),
                    published.complete(),
                    published.diagnostics()
            );
        });
    }

    /** Reveals the current finder match while preserving the independent projection filter verbatim. */
    private static void revealFinderMatch(
            SFMExplorerRepository.Explorer explorer,
            List<String> immediateDiagnostics
    ) {
        SFMExplorerSession session = explorer.session();
        SFMExplorerSession.Snapshot snapshot = session.snapshot();
        Optional<SFMPath> target = snapshot.finder().currentMatch();
        if (target.isEmpty()) return;
        SFMPath matchedPath = target.orElseThrow();
        Optional<SFMPath> containingRoot = SFMPathHierarchy.deepestContainingRoot(
                snapshot.roots(),
                matchedPath
        );
        if (containingRoot.isEmpty()) {
            String diagnostic = "Finder match is outside the Explorer's current roots: " + matchedPath.canonical();
            session.recordFinderRevealFailure(snapshot.finder().generation(), matchedPath, diagnostic);
            immediateDiagnostics.add("explorer.find-reveal-failed: " + diagnostic);
            return;
        }

        String preservedFilter = snapshot.settings().filterQuery();
        java.util.concurrent.CompletionStage<SFMExplorerPathReveal.Result> reveal;
        try {
            reveal = SFMExplorerPathReveal.reveal(
                    session,
                    explorer.loader(),
                    containingRoot.orElseThrow(),
                    matchedPath,
                    explorer.defaultPageSize(),
                    () -> { }
            );
        } catch (RuntimeException failure) {
            restoreFilter(session, preservedFilter);
            String diagnostic = "Could not start Explorer finder reveal: " + failureMessage(failure);
            session.recordFinderRevealFailure(snapshot.finder().generation(), matchedPath, diagnostic);
            immediateDiagnostics.add("explorer.find-reveal-failed: " + diagnostic);
            return;
        }
        // SFMExplorerPathReveal historically clears a projection filter so a
        // direct reveal is visible. Finder is a separate mechanism, therefore
        // restore the exact filter before releasing the transaction lock.
        restoreFilter(session, preservedFilter);
        reveal.whenComplete((ignored, failure) -> {
            if (failure == null) return;
            session.recordFinderRevealFailure(
                    snapshot.finder().generation(),
                    matchedPath,
                    "Explorer finder reveal failed: " + failureMessage(failure)
            );
        });
    }

    private static void restoreFilter(SFMExplorerSession session, String preservedFilter) {
        if (!session.snapshot().settings().filterQuery().equals(preservedFilter)) {
            session.setFilterQuery(preservedFilter);
        }
    }

    private static String failureMessage(Throwable failure) {
        Throwable current = failure;
        while ((current instanceof CompletionException || current instanceof java.util.concurrent.ExecutionException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    /** Validates every resolver-dependent side effect without starting work or allocating a relation ticket. */
    private static void preflightCommittedSideEffects(TargetPlan plan, Operation operation) {
        SFMExplorerRepository.Explorer explorer = plan.explorer();
        if (operation instanceof SFMExplorerActionRequest.NodeExpand expand) {
            if (!materialized(explorer, expand.path())) {
                explorer.loader().preflightRefresh(expand.path(), expand.pageSize());
            }
        } else if (operation instanceof SFMExplorerActionRequest.NodeToggle toggle) {
            boolean collapsing = plan.snapshot().expanded().contains(toggle.path());
            if (!collapsing && !materialized(explorer, toggle.path())) {
                explorer.loader().preflightRefresh(toggle.path(), toggle.pageSize());
            }
        } else if (operation instanceof SFMExplorerActionRequest.NodeRefresh refresh) {
            explorer.loader().preflightRefresh(refresh.path(), refresh.pageSize());
        }
    }

    private Optional<String> preflight(
            SFMExplorerRepository.Explorer explorer,
            SFMExplorerSession.Snapshot snapshot,
            Operation operation
    ) {
        if (snapshot.closed()) return Optional.of("Explorer session is closed");
        Optional<SFMPath> path = operationPath(operation);
        if (path.isPresent() && !(operation instanceof SFMExplorerActionRequest.RootRemove)) {
            Optional<String> incompatibility = explorer.pathPolicy().incompatibility(path.orElseThrow());
            if (incompatibility.isPresent()) return incompatibility;
        }
        if (isNodeOperation(operation) && !knownPath(explorer, snapshot, path.orElseThrow())) {
            return Optional.of("Node path is not materialized in this explorer");
        }
        if (operation instanceof SFMExplorerActionRequest.RootRemove remove
                && snapshot.roots().contains(remove.path())
                && snapshot.roots().size() == 1) {
            return Optional.of("An explorer must retain at least one root");
        }
        if (operation instanceof SFMExplorerActionRequest.RootAdd add
                && !snapshot.roots().contains(add.path())
                && snapshot.roots().size() >= SFMExplorerSession.MAXIMUM_ROOTS) {
            return Optional.of("An explorer cannot contain more than "
                    + SFMExplorerSession.MAXIMUM_ROOTS + " roots");
        }
        if (operation instanceof SFMExplorerActionRequest.LocationSet set) {
            if (snapshot.revision() != set.expectedRevision()) {
                return Optional.of(
                        "Explorer revision is " + snapshot.revision()
                                + " but the location document expected " + set.expectedRevision()
                );
            }
            SFMPathExpressionResolution resolution = resolveLocation(explorer, set);
            if (!resolution.complete()) {
                String diagnostic = resolution.diagnostics().isEmpty()
                        ? "Path expression did not resolve completely"
                        : resolution.diagnostics().get(0).code() + ": "
                                + resolution.diagnostics().get(0).message();
                return Optional.of(diagnostic);
            }
            if (resolution.paths().isEmpty()) {
                return Optional.of("An explorer location must resolve to at least one root");
            }
            if (resolution.paths().size() > SFMExplorerSession.MAXIMUM_ROOTS) {
                return Optional.of("Explorer location resolves to more than "
                        + SFMExplorerSession.MAXIMUM_ROOTS + " roots");
            }
            for (SFMPath root : resolution.paths()) {
                Optional<String> incompatibility = explorer.pathPolicy().incompatibility(root);
                if (incompatibility.isPresent()) return incompatibility;
                incompatibility = locationAuthorityPolicy.incompatibility(root);
                if (incompatibility.isPresent()) return incompatibility;
            }
        }
        return Optional.empty();
    }

    private static SFMPathExpressionResolution resolveLocation(
            SFMExplorerRepository.Explorer explorer,
            SFMExplorerActionRequest.LocationSet operation
    ) {
        return SFMPathExpressionResolver.resolve(
                operation.expression(),
                explorer.session().selectionRepository(),
                explorer.loader().relationRepository()
        );
    }

    private static boolean knownPath(
            SFMExplorerRepository.Explorer explorer,
            SFMExplorerSession.Snapshot snapshot,
            SFMPath path
    ) {
        // Filter rows can be outside the first ordinary child page. Validate against
        // the exact current query/root/resolver-generation projection, not an unrelated
        // finder lane or a stale query. Its merged relation includes explicitly opened
        // children without promoting the whole filter index into the ordinary tree.
        SFMChildRelationRevision relation = explorer.loader().filterProjection(
                        snapshot.roots(), snapshot.settings().filterQuery(), snapshot.expanded(),
                        snapshot.settings().filterOptions())
                .map(projection -> projection.relations().relation())
                .orElseGet(() -> explorer.loader().relationSnapshot().relation());
        ArrayDeque<SFMPath> frontier = new ArrayDeque<>(snapshot.roots());
        HashSet<SFMPath> visited = new HashSet<>();
        while (!frontier.isEmpty()) {
            SFMPath current = frontier.removeFirst();
            if (!visited.add(current)) continue;
            if (current.equals(path)) return true;
            frontier.addAll(relation.childrenOf(current));
        }
        return false;
    }

    private static boolean materialized(SFMExplorerRepository.Explorer explorer, SFMPath path) {
        SFMChildRelationRepository.PageState state = explorer.loader().relationSnapshot().pageStates().get(path);
        return state != null
                && state.materialization() == SFMChildRelationRepository.PageState.Materialization.MATERIALIZED;
    }

    private static Optional<SFMPath> operationPath(Operation operation) {
        if (operation instanceof SFMExplorerActionRequest.NodeExpand value) return Optional.of(value.path());
        if (operation instanceof SFMExplorerActionRequest.NodeCollapse value) return Optional.of(value.path());
        if (operation instanceof SFMExplorerActionRequest.NodeToggle value) return Optional.of(value.path());
        if (operation instanceof SFMExplorerActionRequest.NodeRefresh value) return Optional.of(value.path());
        if (operation instanceof SFMExplorerActionRequest.RootAdd value) return Optional.of(value.path());
        if (operation instanceof SFMExplorerActionRequest.RootRemove value) return Optional.of(value.path());
        return Optional.empty();
    }

    private static boolean isNodeOperation(Operation operation) {
        return operation instanceof SFMExplorerActionRequest.NodeExpand
                || operation instanceof SFMExplorerActionRequest.NodeCollapse
                || operation instanceof SFMExplorerActionRequest.NodeToggle
                || operation instanceof SFMExplorerActionRequest.NodeRefresh;
    }

    private static boolean touchesSelections(Operation operation) {
        return operation instanceof SFMExplorerActionRequest.RootAdd
                || operation instanceof SFMExplorerActionRequest.RootRemove
                || operation instanceof SFMExplorerActionRequest.LocationSet;
    }

    private static SFMExplorerActionResult.RevisionEvidence evidence(
            SFMExplorerRepository.Explorer explorer,
            SFMExplorerSession.Snapshot snapshot
    ) {
        SFMSelectionRepository.StateSnapshot selectionSnapshot = explorer.session()
                .selectionRepository()
                .stateSnapshot();
        Optional<Long> locationSelectionRevision = snapshot.ephemeralLocationSelection()
                .map(selectionSnapshot.selections()::get)
                .map(SFMSelection::headRevisionId);
        SFMChildRelationRepository.Snapshot relation = explorer.loader().relationSnapshot();
        return new SFMExplorerActionResult.RevisionEvidence(
                snapshot.revision(),
                selectionSnapshot.generation(),
                locationSelectionRevision,
                relation.relation().id(),
                relation.statusGeneration()
        );
    }

    private PreparedAction prepared(
            SFMExplorerActionRequest request,
            SFMSelectorResolution<SFMExplorerId> resolution,
            long generation,
            List<TargetPlan> targets,
            boolean create,
            SFMExplorerActionResult.Status terminalStatus,
            List<String> diagnostics
    ) {
        return new PreparedAction(
                request, resolution, generation, targets, create, terminalStatus, diagnostics
        );
    }

    private SFMExplorerActionResult terminal(
            PreparedAction prepared,
            SFMExplorerActionResult.Status status,
            List<String> diagnostics
    ) {
        SFMExplorerActionResult.TargetOutcome outcome = status == SFMExplorerActionResult.Status.STALE
                ? SFMExplorerActionResult.TargetOutcome.STALE
                : SFMExplorerActionResult.TargetOutcome.REJECTED;
        ArrayList<SFMExplorerActionResult.TargetResult> targets = new ArrayList<>();
        for (TargetPlan plan : prepared.targets) {
            SFMExplorerSession.Snapshot snapshot = plan.explorer().session().snapshot();
            targets.add(new SFMExplorerActionResult.TargetResult(
                    plan.id(),
                    outcome,
                    Optional.of(plan.evidence()),
                    evidence(plan.explorer(), snapshot),
                    snapshot,
                    Optional.empty(),
                    diagnostics
            ));
        }
        return result(prepared, status, repository.generation(), targets, diagnostics);
    }

    private SFMExplorerActionResult stale(PreparedAction prepared, String diagnostic) {
        ArrayList<String> diagnostics = new ArrayList<>(prepared.diagnostics);
        diagnostics.add(diagnostic);
        return terminal(prepared, SFMExplorerActionResult.Status.STALE, diagnostics);
    }

    private SFMExplorerActionResult rejected(PreparedAction prepared, String diagnostic) {
        ArrayList<String> diagnostics = new ArrayList<>(prepared.diagnostics);
        diagnostics.add(diagnostic);
        return terminal(prepared, SFMExplorerActionResult.Status.REJECTED, diagnostics);
    }

    private SFMExplorerActionResult result(
            PreparedAction prepared,
            SFMExplorerActionResult.Status status,
            long resultingGeneration,
            List<SFMExplorerActionResult.TargetResult> targets,
            List<String> diagnostics
    ) {
        return new SFMExplorerActionResult(
                status,
                prepared.request.operation().id(),
                prepared.selectorResolution,
                prepared.registryGeneration,
                resultingGeneration,
                targets,
                diagnostics
        );
    }

    private static List<SFMSelectionRepository> selectionRepositories(List<TargetPlan> targets) {
        IdentityHashMap<SFMSelectionRepository, Boolean> seen = new IdentityHashMap<>();
        ArrayList<SFMSelectionRepository> repositories = new ArrayList<>();
        for (TargetPlan target : targets) {
            SFMSelectionRepository selectionRepository = target.explorer().session().selectionRepository();
            if (seen.put(selectionRepository, Boolean.TRUE) == null) repositories.add(selectionRepository);
        }
        repositories.sort(Comparator.comparingInt(System::identityHashCode));
        return List.copyOf(repositories);
    }

    private static <T> T withLocks(List<SFMExplorerSession> sessions, int index, Supplier<T> body) {
        if (index == sessions.size()) return body.get();
        synchronized (sessions.get(index)) {
            return withLocks(sessions, index + 1, body);
        }
    }

    private static <T> T withSelectionLocks(
            List<SFMSelectionRepository> selections,
            int index,
            Supplier<T> body
    ) {
        if (index == selections.size()) return body.get();
        synchronized (selections.get(index)) {
            return withSelectionLocks(selections, index + 1, body);
        }
    }
}
