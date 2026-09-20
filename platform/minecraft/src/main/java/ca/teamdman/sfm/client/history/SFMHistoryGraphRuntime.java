package ca.teamdman.sfm.client.history;

import ca.teamdman.sfm.client.explorer.SFMEntitySelector;
import ca.teamdman.sfm.client.history.presentation.SFMHistoryGraphPresentationModel;
import ca.teamdman.sfm.client.history.presentation.SFMHistoryGraphPresentationProjection;
import ca.teamdman.sfm.client.history.replay.SFMTemporalReplayArchive;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Push-fed catalog of independently addressable trajectory machines.
 *
 * <p>The runtime owns neither document state nor planning policy. Controllers
 * remain authoritative and publish immutable contract snapshots through this
 * seam. Panels subscribe to catalog events, so opening a History Graph after
 * recording began replays the latest state without polling.</p>
 */
public final class SFMHistoryGraphRuntime {
    private static final SFMHistoryGraphRuntime INSTANCE = new SFMHistoryGraphRuntime();
    private static final Logger LOGGER = LogManager.getLogger(SFMHistoryGraphRuntime.class);
    private static final Executor PRESENTATION_EXECUTOR = new LatestProjectionExecutor(
            Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors())),
            64,
            daemonThreadFactory("sfm-history-projection-")
    );
    private static final ExecutorService LISTENER_EXECUTOR = Executors.newCachedThreadPool(
            daemonThreadFactory("sfm-history-listener-")
    );

    private final TreeMap<String, Controller> controllers = new TreeMap<>();
    private final TreeMap<Long, ListenerSubscription> listeners = new TreeMap<>();
    private long nextListenerId = 1;
    private long catalogRevision;
    private Optional<String> activeMachineId = Optional.empty();

    public SFMHistoryGraphRuntime() {
    }

    public static SFMHistoryGraphRuntime get() {
        return INSTANCE;
    }

    public sealed interface Operation permits Plan, Step, Run, Pause, Replan, SelectRoute, InspectCost,
            InvokeSemanticAction, ExactReplay, SemanticRebase, InspectCausalArchive {
    }

    public record Plan() implements Operation {
    }

    public record Step() implements Operation {
    }

    public record Run(int maxSteps) implements Operation {
        public Run {
            if (maxSteps <= 0 || maxSteps > 1_024) {
                throw new IllegalArgumentException("Run maxSteps must be between 1 and 1024");
            }
        }
    }

    public record Pause() implements Operation {
    }

    public record Replan() implements Operation {
    }

    public record SelectRoute(String planRevisionId, String routeId) implements Operation {
        public SelectRoute {
            planRevisionId = requireText(planRevisionId, "planRevisionId");
            routeId = requireText(routeId, "routeId");
        }
    }

    public record InspectCost() implements Operation {
    }

    /** Executes one domain-owned semantic action through the authoritative selected episode. */
    public record InvokeSemanticAction(String actionId) implements Operation {
        public InvokeSemanticAction {
            actionId = requireText(actionId, "actionId");
        }
    }

    /** Replays one recorded two-action chamber route only against its exact recorded parent. */
    public record ExactReplay(String sourceBoundaryStateId, String targetParentStateId) implements Operation {
        public ExactReplay {
            sourceBoundaryStateId = requireText(sourceBoundaryStateId, "sourceBoundaryStateId");
            targetParentStateId = requireText(targetParentStateId, "targetParentStateId");
        }
    }

    /** Re-evaluates one eligible recorded two-action route against an explicit retained parent. */
    public record SemanticRebase(String sourceBoundaryStateId, String targetParentStateId) implements Operation {
        public SemanticRebase {
            sourceBoundaryStateId = requireText(sourceBoundaryStateId, "sourceBoundaryStateId");
            targetParentStateId = requireText(targetParentStateId, "targetParentStateId");
        }
    }

    /** Reports the current bounded causal archive without changing the document head. */
    public record InspectCausalArchive() implements Operation {
    }

    public enum OperationStatus {
        APPLIED,
        NO_CHANGE,
        REJECTED
    }

    public record OperationResult(OperationStatus status, String message) {
        public OperationResult {
            Objects.requireNonNull(status, "status");
            message = requireText(message, "message");
        }

        public static OperationResult applied(String message) {
            return new OperationResult(OperationStatus.APPLIED, message);
        }

        public static OperationResult noChange(String message) {
            return new OperationResult(OperationStatus.NO_CHANGE, message);
        }

        public static OperationResult rejected(String message) {
            return new OperationResult(OperationStatus.REJECTED, message);
        }
    }

    public record TargetResult(String machineId, OperationResult result) {
        public TargetResult {
            machineId = requireText(machineId, "machineId");
            Objects.requireNonNull(result, "result");
        }
    }

    public record BatchResult(List<TargetResult> targets, List<String> diagnostics) {
        public BatchResult {
            targets = List.copyOf(targets);
            diagnostics = List.copyOf(diagnostics);
        }

        public boolean appliedAny() {
            return targets.stream().anyMatch(target -> target.result().status() == OperationStatus.APPLIED);
        }
    }

    public sealed interface PresentationState permits PresentationPending, PresentationReady, PresentationFailed {
    }

    public enum PresentationPending implements PresentationState {
        INSTANCE
    }

    public record PresentationReady(SFMHistoryGraphPresentationModel.Presentation presentation)
            implements PresentationState {
        public PresentationReady {
            Objects.requireNonNull(presentation, "presentation");
        }
    }

    public record PresentationFailed(String message, Throwable failure) implements PresentationState {
        public PresentationFailed {
            message = requireText(message, "message");
            Objects.requireNonNull(failure, "failure");
        }
    }

    /**
     * Immutable authoritative contracts plus one lazily prepared presentation.
     *
     * <p>Projection can be materially more expensive than catalog delivery, so
     * construction and publication only capture contracts. The first consumer
     * requests projection on a bounded daemon executor, and all consumers share
     * the same future. {@link #presentationState()} never waits; the blocking
     * {@link #presentation()} method exists for tests and explicitly off-thread
     * callers.</p>
     */
    public static final class MachineSnapshot {
        private final String machineId;
        private final long revision;
        private final SFMHistoryGraphContract.Graph history;
        private final SFMTrajectoryContract.PlanBook planBook;
        private final SFMTrajectoryContract.TrajectoryMachineState machine;
        private final Optional<SFMTrajectoryContract.SupervisionContract> supervision;
        private final Optional<SFMTemporalReplayArchive.Archive> replayArchive;
        private final String summary;
        private final Executor presentationExecutor;
        private final Supplier<SFMHistoryGraphPresentationModel.Presentation> presentationProjection;
        private volatile CompletableFuture<PresentationState> presentationFuture;

        public MachineSnapshot(
                String machineId,
                long revision,
                SFMHistoryGraphContract.Graph history,
                SFMTrajectoryContract.PlanBook planBook,
                SFMTrajectoryContract.TrajectoryMachineState machine,
                Optional<SFMTrajectoryContract.SupervisionContract> supervision,
                String summary
        ) {
            this(machineId, revision, history, planBook, machine, supervision, Optional.empty(), summary);
        }

        public MachineSnapshot(
                String machineId,
                long revision,
                SFMHistoryGraphContract.Graph history,
                SFMTrajectoryContract.PlanBook planBook,
                SFMTrajectoryContract.TrajectoryMachineState machine,
                Optional<SFMTrajectoryContract.SupervisionContract> supervision,
                Optional<SFMTemporalReplayArchive.Archive> replayArchive,
                String summary
        ) {
            this(
                    machineId,
                    revision,
                    history,
                    planBook,
                    machine,
                    supervision,
                    replayArchive,
                    summary,
                    PRESENTATION_EXECUTOR,
                    () -> SFMHistoryGraphPresentationProjection.project(history, planBook, machine, replayArchive)
            );
        }

        MachineSnapshot(
                String machineId,
                long revision,
                SFMHistoryGraphContract.Graph history,
                SFMTrajectoryContract.PlanBook planBook,
                SFMTrajectoryContract.TrajectoryMachineState machine,
                Optional<SFMTrajectoryContract.SupervisionContract> supervision,
                String summary,
                Executor presentationExecutor,
                Supplier<SFMHistoryGraphPresentationModel.Presentation> presentationProjection
        ) {
            this(
                    machineId,
                    revision,
                    history,
                    planBook,
                    machine,
                    supervision,
                    Optional.empty(),
                    summary,
                    presentationExecutor,
                    presentationProjection
            );
        }

        MachineSnapshot(
                String machineId,
                long revision,
                SFMHistoryGraphContract.Graph history,
                SFMTrajectoryContract.PlanBook planBook,
                SFMTrajectoryContract.TrajectoryMachineState machine,
                Optional<SFMTrajectoryContract.SupervisionContract> supervision,
                Optional<SFMTemporalReplayArchive.Archive> replayArchive,
                String summary,
                Executor presentationExecutor,
                Supplier<SFMHistoryGraphPresentationModel.Presentation> presentationProjection
        ) {
            this.machineId = requireText(machineId, "machineId");
            if (revision < 0) throw new IllegalArgumentException("revision must not be negative");
            this.revision = revision;
            this.history = Objects.requireNonNull(history, "history");
            this.planBook = Objects.requireNonNull(planBook, "planBook");
            this.machine = Objects.requireNonNull(machine, "machine");
            this.supervision = Objects.requireNonNull(supervision, "supervision");
            this.replayArchive = Objects.requireNonNull(replayArchive, "replayArchive");
            this.summary = requireText(summary, "summary");
            this.presentationExecutor = Objects.requireNonNull(presentationExecutor, "presentationExecutor");
            this.presentationProjection = Objects.requireNonNull(
                    presentationProjection,
                    "presentationProjection"
            );
            supervision.ifPresent(contract -> {
                if (!contract.revision().equals(machine.supervisionContractRevision())) {
                    throw new IllegalArgumentException(
                            "Snapshot supervision revision must match the trajectory machine");
                }
            });
            replayArchive.ifPresent(archive -> {
                if (!archive.episodeId().equals(this.machineId)) {
                    throw new IllegalArgumentException("Snapshot replay archive must belong to its machine");
                }
            });
        }

        public String machineId() {
            return machineId;
        }

        public long revision() {
            return revision;
        }

        public SFMHistoryGraphContract.Graph history() {
            return history;
        }

        public SFMTrajectoryContract.PlanBook planBook() {
            return planBook;
        }

        public SFMTrajectoryContract.TrajectoryMachineState machine() {
            return machine;
        }

        public Optional<SFMTrajectoryContract.SupervisionContract> supervision() {
            return supervision;
        }

        public Optional<SFMTemporalReplayArchive.Archive> replayArchive() {
            return replayArchive;
        }

        public String summary() {
            return summary;
        }

        /** Starts projection if necessary and returns immediately with its current state. */
        public PresentationState presentationState() {
            return presentationFuture().getNow(PresentationPending.INSTANCE);
        }

        /** Blocking compatibility seam for tests and explicitly off-thread callers. */
        public SFMHistoryGraphPresentationModel.Presentation presentation() {
            PresentationState state = presentationFuture().join();
            if (state instanceof PresentationReady ready) return ready.presentation();
            PresentationFailed failed = (PresentationFailed) state;
            throw new IllegalStateException(
                    "History Graph presentation failed: " + failed.message(),
                    failed.failure()
            );
        }

        private CompletableFuture<PresentationState> presentationFuture() {
            CompletableFuture<PresentationState> current = presentationFuture;
            if (current != null) return current;
            synchronized (this) {
                current = presentationFuture;
                if (current != null) return current;
                current = new CompletableFuture<>();
                presentationFuture = current;
                ProjectionTask task = new ProjectionTask(
                        machineId,
                        revision,
                        presentationProjection,
                        current
                );
                try {
                    presentationExecutor.execute(task);
                } catch (RuntimeException failure) {
                    task.fail(failure);
                }
                return current;
            }
        }
    }

    public interface Controller {
        String machineId();

        MachineSnapshot snapshot();

        OperationResult apply(Operation operation);
    }

    /** Optional read-only extension for controllers that can materialize projected route frames. */
    public interface CandidateFrameController extends Controller {
        SFMCandidateHistoryContract.CandidateRouteProjection projectCandidateRoute(
                String planRevisionId,
                String routeId
        );
    }

    /** Optional exact committed document projection used by explicit candidate-comment promotion. */
    public interface CommittedDocumentController extends Controller {
        CommittedDocument committedDocument();
    }

    public record CommittedDocument(
            String machineId,
            long machineRevision,
            String actualHistoryHeadId,
            String stateId,
            String stateHash,
            String documentId,
            String text
    ) {
        public CommittedDocument {
            machineId = requireText(machineId, "committedDocument.machineId");
            if (machineRevision < 0) throw new IllegalArgumentException("machineRevision must not be negative");
            actualHistoryHeadId = requireText(actualHistoryHeadId, "committedDocument.actualHistoryHeadId");
            stateId = requireText(stateId, "committedDocument.stateId");
            stateHash = requireText(stateHash, "committedDocument.stateHash");
            documentId = requireText(documentId, "committedDocument.documentId");
            Objects.requireNonNull(text, "text");
        }
    }

    public record CatalogEvent(
            long revision,
            Optional<String> activeMachineId,
            List<MachineSnapshot> machines
    ) {
        public CatalogEvent {
            if (revision < 0) throw new IllegalArgumentException("revision must not be negative");
            Objects.requireNonNull(activeMachineId, "activeMachineId");
            machines = machines.stream()
                    .sorted(Comparator.comparing(MachineSnapshot::machineId))
                    .toList();
        }

        public Optional<MachineSnapshot> machine(String id) {
            return machines.stream().filter(machine -> machine.machineId().equals(id)).findFirst();
        }

        public Optional<MachineSnapshot> activeMachine() {
            return activeMachineId.flatMap(this::machine);
        }

        public List<MachineSnapshot> resolve(SFMEntitySelector selector) {
            Objects.requireNonNull(selector, "selector");
            if (selector.domain() != SFMEntitySelector.Domain.EPISODE) {
                throw new IllegalArgumentException("History Graph event requires an episode selector");
            }
            Set<String> available = machines.stream()
                    .map(MachineSnapshot::machineId)
                    .collect(java.util.stream.Collectors.toSet());
            return resolveNode(selector.node(), available, activeMachineId).stream()
                    .map(this::machine)
                    .flatMap(Optional::stream)
                    .toList();
        }
    }

    @FunctionalInterface
    public interface Subscription extends AutoCloseable {
        @Override
        void close();
    }

    @FunctionalInterface
    public interface Registration extends AutoCloseable {
        @Override
        void close();
    }

    public Registration register(Controller controller) {
        Objects.requireNonNull(controller, "controller");
        final String id = requireText(controller.machineId(), "controller.machineId");
        MachineSnapshot initial = Objects.requireNonNull(controller.snapshot(), "controller.snapshot");
        if (!id.equals(initial.machineId())) {
            throw new IllegalArgumentException("Controller and snapshot machine ids disagree");
        }
        List<ListenerSubscription> scheduled;
        synchronized (this) {
            if (controllers.putIfAbsent(id, controller) != null) {
                throw new IllegalArgumentException("Trajectory machine is already registered: " + id);
            }
            Optional<String> previousActiveMachineId = activeMachineId;
            if (previousActiveMachineId.isEmpty()) activeMachineId = Optional.of(id);
            try {
                scheduled = enqueueCatalogChangeLocked();
            } catch (RuntimeException failure) {
                controllers.remove(id, controller);
                activeMachineId = previousActiveMachineId;
                throw failure;
            }
        }
        drainScheduled(scheduled);
        return new Registration() {
            private boolean closed;

            @Override
            public void close() {
                List<ListenerSubscription> scheduled = List.of();
                synchronized (SFMHistoryGraphRuntime.this) {
                    if (closed) return;
                    closed = true;
                    if (controllers.get(id) != controller) return;
                    controllers.remove(id);
                    if (activeMachineId.filter(id::equals).isPresent()) {
                        activeMachineId = controllers.isEmpty()
                                ? Optional.empty()
                                : Optional.of(controllers.firstKey());
                    }
                    scheduled = enqueueCatalogChangeLocked();
                }
                drainScheduled(scheduled);
            }
        };
    }

    public void setActiveMachine(String machineId) {
        final String requestedMachineId = requireText(machineId, "machineId");
        List<ListenerSubscription> scheduled;
        synchronized (this) {
            if (!controllers.containsKey(requestedMachineId)) {
                throw new IllegalArgumentException("Unknown trajectory machine: " + requestedMachineId);
            }
            if (activeMachineId.filter(requestedMachineId::equals).isPresent()) return;
            Optional<String> previousActiveMachineId = activeMachineId;
            activeMachineId = Optional.of(requestedMachineId);
            try {
                scheduled = enqueueCatalogChangeLocked();
            } catch (RuntimeException failure) {
                activeMachineId = previousActiveMachineId;
                throw failure;
            }
        }
        drainScheduled(scheduled);
    }

    /** Publish an authoritative controller change that occurred outside an action invocation. */
    public void publish(String machineId) {
        final String requestedMachineId = requireText(machineId, "machineId");
        List<ListenerSubscription> scheduled;
        synchronized (this) {
            if (!controllers.containsKey(requestedMachineId)) {
                throw new IllegalArgumentException("Unknown trajectory machine: " + requestedMachineId);
            }
            scheduled = enqueueCatalogChangeLocked();
        }
        drainScheduled(scheduled);
    }

    /**
     * Subscribes to a FIFO baseline-plus-live stream.
     *
     * <p>Callbacks run serially per subscription on isolated daemon workers,
     * never on the publishing or Minecraft client thread. UI consumers must
     * only capture immutable events here and apply them from their client
     * tick.</p>
     */
    public Subscription subscribe(Consumer<CatalogEvent> listener) {
        Objects.requireNonNull(listener, "listener");
        long id;
        ListenerSubscription subscription;
        boolean scheduled;
        synchronized (this) {
            id = nextListenerId++;
            subscription = new ListenerSubscription(id, listener);
            // Queue the baseline while catalog publication is serialized. A
            // concurrent live event can only be appended behind it, while the
            // callback itself runs after the runtime monitor is released.
            CatalogEvent baseline = snapshotEventLocked(catalogRevision);
            listeners.put(id, subscription);
            scheduled = subscription.enqueue(baseline);
        }
        if (scheduled) scheduleDrain(subscription);
        ListenerSubscription registeredSubscription = subscription;
        return () -> {
            synchronized (SFMHistoryGraphRuntime.this) {
                if (listeners.get(id) != registeredSubscription) return;
                listeners.remove(id);
            }
            registeredSubscription.closeQueue();
        };
    }

    public CatalogEvent snapshotEvent() {
        synchronized (this) {
            return snapshotEventLocked(catalogRevision);
        }
    }

    private CatalogEvent snapshotEventLocked(long revision) {
        ArrayList<MachineSnapshot> snapshots = new ArrayList<>();
        for (Controller controller : controllers.values()) {
            MachineSnapshot snapshot = Objects.requireNonNull(controller.snapshot(), "controller.snapshot");
            if (!controller.machineId().equals(snapshot.machineId())) {
                throw new IllegalStateException("Controller and snapshot machine ids disagree");
            }
            snapshots.add(snapshot);
        }
        return new CatalogEvent(revision, activeMachineId, snapshots);
    }

    public synchronized List<String> machineIds() {
        return List.copyOf(controllers.keySet());
    }

    /**
     * Captures one immutable candidate route without holding the runtime catalog lock while
     * domain projection runs. Callers are expected to invoke this from a bounded worker.
     */
    public Optional<SFMCandidateHistoryContract.CandidateRouteProjection> projectCandidateRoute(
            String machineId,
            String planRevisionId,
            String routeId
    ) {
        machineId = requireText(machineId, "machineId");
        planRevisionId = requireText(planRevisionId, "planRevisionId");
        routeId = requireText(routeId, "routeId");
        Controller controller;
        synchronized (this) {
            controller = controllers.get(machineId);
        }
        if (!(controller instanceof CandidateFrameController source)) return Optional.empty();
        return Optional.of(Objects.requireNonNull(
                source.projectCandidateRoute(planRevisionId, routeId),
                "candidate route projection"
        ));
    }

    public Optional<CommittedDocument> committedDocument(String machineId) {
        machineId = requireText(machineId, "machineId");
        Controller controller;
        synchronized (this) {
            controller = controllers.get(machineId);
        }
        if (!(controller instanceof CommittedDocumentController source)) return Optional.empty();
        CommittedDocument document = Objects.requireNonNull(source.committedDocument(), "committed document");
        if (!machineId.equals(document.machineId())) {
            throw new IllegalArgumentException("Committed document belongs to another trajectory machine");
        }
        return Optional.of(document);
    }

    public synchronized List<MachineSnapshot> resolveSnapshots(
            SFMEntitySelector selector,
            Optional<String> focusedMachineId
    ) {
        Objects.requireNonNull(selector, "selector");
        Objects.requireNonNull(focusedMachineId, "focusedMachineId");
        if (selector.domain() != SFMEntitySelector.Domain.EPISODE) {
            throw new IllegalArgumentException("History Graph runtime requires an episode selector");
        }
        return resolveNode(selector.node(), controllers.keySet(), focusedMachineId).stream()
                .map(controllers::get)
                .filter(Objects::nonNull)
                .map(Controller::snapshot)
                .toList();
    }

    public BatchResult execute(
            SFMEntitySelector selector,
            Optional<String> focusedMachineId,
            Operation operation
    ) {
        Objects.requireNonNull(selector, "selector");
        Objects.requireNonNull(focusedMachineId, "focusedMachineId");
        Objects.requireNonNull(operation, "operation");
        if (selector.domain() != SFMEntitySelector.Domain.EPISODE) {
            throw new IllegalArgumentException("History Graph runtime requires an episode selector");
        }
        List<Controller> targets;
        synchronized (this) {
            targets = resolveNode(selector.node(), controllers.keySet(), focusedMachineId).stream()
                    .map(controllers::get)
                    .filter(Objects::nonNull)
                    .toList();
        }
        if (targets.isEmpty()) {
            return new BatchResult(List.of(), List.of(
                    "No trajectory machine matched selector " + selector.canonical()));
        }

        ArrayList<TargetResult> results = new ArrayList<>();
        ArrayList<String> diagnostics = new ArrayList<>();
        for (Controller target : targets) {
            String targetMachineId = target.machineId();
            OperationResult result;
            try {
                result = Objects.requireNonNull(target.apply(operation), "operation result");
            } catch (RuntimeException failure) {
                String message = failureMessage(failure);
                diagnostics.add(targetMachineId + ": " + message);
                results.add(new TargetResult(targetMachineId, OperationResult.rejected(message)));
                continue;
            }

            // The authoritative operation result is final once apply returns.
            // Publication is best-effort follow-up work and may diagnose a
            // snapshot failure, but it must never duplicate or reclassify the
            // already-applied target result.
            results.add(new TargetResult(targetMachineId, result));
            try {
                publish(targetMachineId);
            } catch (RuntimeException failure) {
                diagnostics.add(targetMachineId
                        + ": publication failed after "
                        + result.status()
                        + ": "
                        + failureMessage(failure));
            }
        }
        return new BatchResult(results, diagnostics);
    }

    /** Test-only lifecycle reset; production callers unregister their own controllers instead. */
    void clearForTests() {
        List<ListenerSubscription> subscriptions;
        synchronized (this) {
            controllers.clear();
            subscriptions = List.copyOf(listeners.values());
            listeners.clear();
            activeMachineId = Optional.empty();
            nextListenerId = 1;
            catalogRevision = 0;
        }
        subscriptions.forEach(ListenerSubscription::closeQueue);
    }

    private static Set<String> resolveNode(
            SFMEntitySelector.Node node,
            Collection<String> availableMachineIds,
            Optional<String> focusedMachineId
    ) {
        Set<String> available = Set.copyOf(availableMachineIds);
        if (node instanceof SFMEntitySelector.Id id) {
            return available.contains(id.value()) ? Set.of(id.value()) : Set.of();
        }
        if (node instanceof SFMEntitySelector.All) return sorted(available);
        if (node instanceof SFMEntitySelector.Focused) {
            return focusedMachineId.filter(available::contains).map(Set::of).orElseGet(Set::of);
        }
        if (node instanceof SFMEntitySelector.Union union) {
            LinkedHashSet<String> result = new LinkedHashSet<>();
            union.selectors().forEach(child -> result.addAll(
                    resolveNode(child, available, focusedMachineId)));
            return sorted(result);
        }
        if (node instanceof SFMEntitySelector.Intersection intersection) {
            List<SFMEntitySelector.Node> selectors = intersection.selectors();
            LinkedHashSet<String> result = new LinkedHashSet<>(
                    resolveNode(selectors.get(0), available, focusedMachineId));
            selectors.subList(1, selectors.size()).forEach(child ->
                    result.retainAll(resolveNode(child, available, focusedMachineId)));
            return sorted(result);
        }
        if (node instanceof SFMEntitySelector.Difference difference) {
            LinkedHashSet<String> result = new LinkedHashSet<>(
                    resolveNode(difference.include(), available, focusedMachineId));
            difference.exclude().forEach(child ->
                    result.removeAll(resolveNode(child, available, focusedMachineId)));
            return sorted(result);
        }
        if (node instanceof SFMEntitySelector.Name) {
            throw new IllegalArgumentException("Named trajectory-machine selectors are not supported");
        }
        throw new AssertionError("Unhandled trajectory-machine selector " + node);
    }

    private static Set<String> sorted(Collection<String> values) {
        return java.util.Collections.unmodifiableSet(new java.util.TreeSet<>(values));
    }

    /**
     * Captures and queues one catalog revision while the caller holds the
     * runtime monitor. No listener code is invoked from this method.
     */
    private List<ListenerSubscription> enqueueCatalogChangeLocked() {
        long nextRevision = catalogRevision + 1;
        CatalogEvent event = snapshotEventLocked(nextRevision);
        ArrayList<ListenerSubscription> scheduled = new ArrayList<>();
        for (ListenerSubscription subscription : listeners.values()) {
            if (subscription.enqueue(event)) scheduled.add(subscription);
        }
        catalogRevision = nextRevision;
        return List.copyOf(scheduled);
    }

    private static void drainScheduled(List<ListenerSubscription> scheduled) {
        scheduled.forEach(SFMHistoryGraphRuntime::scheduleDrain);
    }

    private static void scheduleDrain(ListenerSubscription subscription) {
        LISTENER_EXECUTOR.execute(subscription::drain);
    }

    /**
     * One projection request. The default scheduler may merge an equivalent
     * request or discard a stale queued revision, but every returned future is
     * completed exactly once with an observable state.
     */
    private static final class ProjectionTask implements Runnable {
        private final String machineId;
        private final long revision;
        private final Supplier<SFMHistoryGraphPresentationModel.Presentation> projection;
        private final CompletableFuture<PresentationState> result;

        private ProjectionTask(
                String machineId,
                long revision,
                Supplier<SFMHistoryGraphPresentationModel.Presentation> projection,
                CompletableFuture<PresentationState> result
        ) {
            this.machineId = machineId;
            this.revision = revision;
            this.projection = projection;
            this.result = result;
        }

        @Override
        public void run() {
            if (result.isDone()) return;
            try {
                result.complete(new PresentationReady(projection.get()));
            } catch (Throwable failure) {
                fail(failure);
            }
        }

        private void shareResultWith(ProjectionTask follower) {
            result.whenComplete((state, failure) -> {
                if (failure == null) follower.result.complete(state);
                else follower.fail(failure);
            });
        }

        private void discard(String reason) {
            fail(new java.util.concurrent.CancellationException(reason));
        }

        private void fail(Throwable failure) {
            Throwable unwrapped = unwrapCompletionFailure(failure);
            result.complete(new PresentationFailed(throwableMessage(unwrapped), unwrapped));
        }
    }

    /**
     * Bounded latest-per-machine scheduler. At most one running and one queued
     * revision exist for a machine. A newer revision supersedes its stale
     * queued predecessor, equivalent revisions share one computation, and a
     * global pending bound prevents a catalog with many machines from growing
     * memory without limit.
     */
    static final class LatestProjectionExecutor implements Executor {
        private final int maxPendingMachines;
        private final LinkedHashMap<String, ProjectionTask> pending = new LinkedHashMap<>();
        private final Map<String, ProjectionTask> running = new HashMap<>();

        LatestProjectionExecutor(
                int workerCount,
                int maxPendingMachines,
                ThreadFactory threadFactory
        ) {
            if (workerCount <= 0) throw new IllegalArgumentException("workerCount must be positive");
            if (maxPendingMachines <= 0) {
                throw new IllegalArgumentException("maxPendingMachines must be positive");
            }
            this.maxPendingMachines = maxPendingMachines;
            for (int index = 0; index < workerCount; index++) {
                Thread worker = threadFactory.newThread(this::workerLoop);
                worker.start();
            }
        }

        @Override
        public void execute(Runnable command) {
            if (!(command instanceof ProjectionTask incoming)) {
                throw new IllegalArgumentException("LatestProjectionExecutor accepts projection tasks only");
            }
            synchronized (this) {
                ProjectionTask active = running.get(incoming.machineId);
                if (active != null) {
                    if (active.revision == incoming.revision) {
                        active.shareResultWith(incoming);
                        return;
                    }
                    if (active.revision > incoming.revision) {
                        incoming.discard("newer machine projection is already running");
                        return;
                    }
                }

                ProjectionTask queued = pending.get(incoming.machineId);
                if (queued != null) {
                    if (queued.revision == incoming.revision) {
                        queued.shareResultWith(incoming);
                        return;
                    }
                    if (queued.revision > incoming.revision) {
                        incoming.discard("newer machine projection is already queued");
                        return;
                    }
                    pending.remove(incoming.machineId);
                    queued.discard("superseded by machine revision " + incoming.revision);
                }

                while (pending.size() >= maxPendingMachines) {
                    Iterator<Map.Entry<String, ProjectionTask>> iterator = pending.entrySet().iterator();
                    Map.Entry<String, ProjectionTask> oldest = iterator.next();
                    iterator.remove();
                    oldest.getValue().discard("projection queue capacity was reclaimed for newer work");
                }
                pending.put(incoming.machineId, incoming);
                notifyAll();
            }
        }

        private void workerLoop() {
            while (!Thread.currentThread().isInterrupted()) {
                ProjectionTask task;
                synchronized (this) {
                    Map.Entry<String, ProjectionTask> next;
                    while ((next = nextRunnableProjection()) == null) {
                        try {
                            wait();
                        } catch (InterruptedException interruption) {
                            Thread.currentThread().interrupt();
                            return;
                        }
                    }
                    pending.remove(next.getKey(), next.getValue());
                    task = next.getValue();
                    running.put(task.machineId, task);
                }
                try {
                    task.run();
                } finally {
                    synchronized (this) {
                        running.remove(task.machineId, task);
                        notifyAll();
                    }
                }
            }
        }

        private Map.Entry<String, ProjectionTask> nextRunnableProjection() {
            return pending.entrySet().stream()
                    .filter(entry -> !running.containsKey(entry.getKey()))
                    .findFirst()
                    .orElse(null);
        }
    }

    /**
     * Per-listener FIFO. The runtime monitor serializes enqueue order, while
     * the subscription monitor elects exactly one drainer. Callbacks execute
     * with neither monitor held so listeners may safely call back into the
     * runtime and one listener cannot break delivery to another.
     */
    private static final class ListenerSubscription {
        private final long id;
        private final Consumer<CatalogEvent> listener;
        private final ArrayDeque<CatalogEvent> pending = new ArrayDeque<>();
        private boolean draining;
        private boolean closed;

        private ListenerSubscription(long id, Consumer<CatalogEvent> listener) {
            this.id = id;
            this.listener = listener;
        }

        private synchronized boolean enqueue(CatalogEvent event) {
            if (closed) return false;
            pending.addLast(event);
            if (draining) return false;
            draining = true;
            return true;
        }

        private void drain() {
            while (true) {
                CatalogEvent event;
                synchronized (this) {
                    if (closed) {
                        pending.clear();
                        draining = false;
                        return;
                    }
                    event = pending.pollFirst();
                    if (event == null) {
                        draining = false;
                        return;
                    }
                }
                try {
                    listener.accept(event);
                } catch (RuntimeException failure) {
                    LOGGER.warn(
                            "SFM_HISTORY_GRAPH_LISTENER_FAILED listener={} catalog_revision={}",
                            id,
                            event.revision(),
                            failure
                    );
                }
            }
        }

        private synchronized void closeQueue() {
            closed = true;
            pending.clear();
        }
    }

    private static String failureMessage(RuntimeException failure) {
        return throwableMessage(failure);
    }

    private static Throwable unwrapCompletionFailure(Throwable failure) {
        if (failure instanceof java.util.concurrent.CompletionException && failure.getCause() != null) {
            return failure.getCause();
        }
        return failure;
    }

    private static String throwableMessage(Throwable failure) {
        Throwable unwrapped = unwrapCompletionFailure(failure);
        return unwrapped.getMessage() == null
                ? unwrapped.getClass().getSimpleName()
                : unwrapped.getMessage();
    }

    private static ThreadFactory daemonThreadFactory(String prefix) {
        AtomicInteger nextId = new AtomicInteger(1);
        return runnable -> {
            Thread thread = new Thread(runnable, prefix + nextId.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        };
    }

    private static String requireText(String value, String label) {
        Objects.requireNonNull(value, label);
        if (value.isBlank()) throw new IllegalArgumentException(label + " must not be blank");
        return value;
    }
}
