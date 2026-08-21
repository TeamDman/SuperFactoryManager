package ca.teamdman.sfm.client.screen.history;

import ca.teamdman.sfm.client.explorer.SFMEntitySelector;
import ca.teamdman.sfm.client.history.SFMCandidateHistoryContract;
import ca.teamdman.sfm.client.history.SFMEpisodeContext;
import ca.teamdman.sfm.client.history.SFMHistoryGraphContract;
import ca.teamdman.sfm.client.history.SFMHistoryGraphRuntime;
import ca.teamdman.sfm.client.history.SFMTrajectoryContract;
import ca.teamdman.sfm.client.screen.SFMFontUtils;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenPanelBounds;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspacePanelContext;
import ca.teamdman.sfm.client.screen.workspace.timeline.SFMSeekableTimelinePanel;
import ca.teamdman.sfm.client.screen.workspace.timeline.SFMTimelineBounds;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** Read-only, timeline-seekable preview of one immutable candidate route. */
public final class SFMCandidateHistoryPanel implements SFMSeekableTimelinePanel, SFMEpisodeContext {
    private static final int BACKGROUND = 0xF0101218;
    private static final int HEADER = 0xFFEDF6F7;
    private static final int MUTED = 0xFF93A7AC;
    private static final int MATERIALIZED = 0xFF72B7FF;
    private static final int UNAVAILABLE = 0xFFFFC35A;
    private static final int BARRIER = 0xFFFF7373;
    private static final int DOCUMENT = 0xFF71D790;
    private static final int PADDING = 7;
    private static final AtomicInteger NEXT_WORKER_ID = new AtomicInteger(1);
    private static final Executor PROJECTION_EXECUTOR = new ThreadPoolExecutor(
            1,
            Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors())),
            30,
            TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(32),
            runnable -> {
                Thread thread = new Thread(
                        runnable,
                        "sfm-candidate-history-projection-" + NEXT_WORKER_ID.getAndIncrement()
                );
                thread.setDaemon(true);
                return thread;
            },
            new ThreadPoolExecutor.AbortPolicy()
    );

    public enum LoadStatus {
        WAITING_FOR_ROUTE,
        QUEUED,
        RUNNING,
        READY,
        FAILED
    }

    private final SFMHistoryGraphRuntime runtime;
    private final SFMEntitySelector episodeSelector;
    private final Optional<String> requestedPlanRevisionId;
    private final Optional<String> requestedRouteId;
    private final Executor projectionExecutor;
    private final AtomicReference<SFMHistoryGraphRuntime.CatalogEvent> pendingCatalogEvent =
            new AtomicReference<>();
    private final ConcurrentLinkedQueue<ProjectionUpdate> pendingProjectionUpdates =
            new ConcurrentLinkedQueue<>();
    private final Object subscriptionLock = new Object();

    private @Nullable SFMHistoryGraphRuntime.Subscription subscription;
    private long subscriptionGeneration;
    private long projectionGeneration;
    private @Nullable String pinnedMachineId;
    private @Nullable String pinnedPlanRevisionId;
    private @Nullable String pinnedRouteId;
    private @Nullable SFMTrajectoryContract.TrajectoryRoute pinnedRoute;
    private @Nullable SFMHistoryGraphRuntime.MachineSnapshot latestMachineSnapshot;
    private @Nullable SFMCandidateHistoryContract.CandidateRouteProjection projection;
    private LoadStatus loadStatus = LoadStatus.WAITING_FOR_ROUTE;
    private String statusMessage = "Waiting for a selected candidate route";
    private int currentPosition;
    private int lastPosition;

    public SFMCandidateHistoryPanel(
            SFMEntitySelector episodeSelector,
            Optional<String> requestedPlanRevisionId,
            Optional<String> requestedRouteId
    ) {
        this(
                SFMHistoryGraphRuntime.get(),
                episodeSelector,
                requestedPlanRevisionId,
                requestedRouteId,
                PROJECTION_EXECUTOR
        );
    }

    SFMCandidateHistoryPanel(
            SFMHistoryGraphRuntime runtime,
            SFMEntitySelector episodeSelector,
            Optional<String> requestedPlanRevisionId,
            Optional<String> requestedRouteId,
            Executor projectionExecutor
    ) {
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        this.episodeSelector = Objects.requireNonNull(episodeSelector, "episodeSelector");
        if (episodeSelector.domain() != SFMEntitySelector.Domain.EPISODE) {
            throw new IllegalArgumentException("Candidate History requires an episode selector");
        }
        this.requestedPlanRevisionId = requireOptionalText(
                requestedPlanRevisionId,
                "requestedPlanRevisionId"
        );
        this.requestedRouteId = requireOptionalText(requestedRouteId, "requestedRouteId");
        if (this.requestedRouteId.isPresent() && this.requestedPlanRevisionId.isEmpty()) {
            throw new IllegalArgumentException("An explicit candidate route requires an explicit plan revision");
        }
        this.projectionExecutor = Objects.requireNonNull(projectionExecutor, "projectionExecutor");
    }

    @Override
    public Component title() {
        return Component.literal("Candidate History");
    }

    @Override
    public Component narration() {
        return Component.literal("Candidate History. " + statusMessage + ". Frame "
                + currentPosition + " of " + lastPosition + ". Actual history is unchanged.");
    }

    @Override
    public Optional<String> episodeId() {
        return Optional.ofNullable(pinnedMachineId);
    }

    @Override
    public SFMTimelineBounds timelineBounds() {
        return new SFMTimelineBounds(0, lastPosition);
    }

    @Override
    public void setTimelinePosition(int timestep) {
        currentPosition = timelineBounds().clamp(timestep);
    }

    @Override
    public void setTimelinePosition(double keyframePosition) {
        if (!Double.isFinite(keyframePosition)) {
            throw new IllegalArgumentException("Candidate-history keyframe position must be finite");
        }
        setTimelinePosition((int) Math.round(keyframePosition));
    }

    @Override
    public void opened(Minecraft minecraft, SFMScreenPanelBounds bounds, SFMWorkspacePanelContext context) {
        long generation;
        SFMHistoryGraphRuntime.Subscription previous;
        synchronized (subscriptionLock) {
            previous = subscription;
            subscription = null;
            generation = ++subscriptionGeneration;
            pendingCatalogEvent.set(null);
        }
        if (previous != null) previous.close();
        SFMHistoryGraphRuntime.Subscription opened = runtime.subscribe(event -> {
            synchronized (subscriptionLock) {
                if (generation != subscriptionGeneration) return;
                pendingCatalogEvent.accumulateAndGet(event, (oldValue, newValue) ->
                        oldValue == null || newValue.revision() >= oldValue.revision()
                                ? newValue
                                : oldValue);
            }
        });
        synchronized (subscriptionLock) {
            if (generation == subscriptionGeneration) subscription = opened;
            else opened.close();
        }
    }

    @Override
    public void closed() {
        SFMHistoryGraphRuntime.Subscription closing;
        synchronized (subscriptionLock) {
            subscriptionGeneration++;
            projectionGeneration++;
            closing = subscription;
            subscription = null;
            pendingCatalogEvent.set(null);
            pendingProjectionUpdates.clear();
        }
        if (closing != null) closing.close();
    }

    @Override
    public void tick() {
        SFMHistoryGraphRuntime.CatalogEvent event = pendingCatalogEvent.getAndSet(null);
        if (event != null) applyCatalog(event);
        ProjectionUpdate update = pendingProjectionUpdates.poll();
        if (update != null && update.generation() == projectionGeneration) applyProjectionUpdate(update);
    }

    @Override
    public void render(
            PoseStack poseStack,
            Minecraft minecraft,
            SFMScreenPanelBounds bounds,
            int mouseX,
            int mouseY,
            float partialTick,
            boolean focused
    ) {
        GuiComponent.fill(
                poseStack,
                bounds.x(),
                bounds.y(),
                bounds.x() + bounds.width(),
                bounds.y() + bounds.height(),
                BACKGROUND
        );
        int line = Math.max(10, minecraft.font.lineHeight + 2);
        int x = bounds.x() + PADDING;
        int y = bounds.y() + PADDING;
        drawClipped(poseStack, minecraft, bounds, x, y, "Candidate History · read-only", HEADER);
        y += line;
        drawClipped(poseStack, minecraft, bounds, x, y,
                "frame " + currentPosition + "/" + lastPosition + " · " + loadStatus, statusColour());
        y += line;
        drawClipped(poseStack, minecraft, bounds, x, y,
                "plan " + Optional.ofNullable(pinnedPlanRevisionId)
                        .map(SFMCandidateHistoryPanel::shortIdentity)
                        .orElse("unresolved"), MUTED);
        y += line;
        drawClipped(poseStack, minecraft, bounds, x, y,
                "route " + Optional.ofNullable(pinnedRouteId)
                        .map(SFMCandidateHistoryPanel::shortIdentity)
                        .orElse("unresolved"), MUTED);
        y += line;

        Optional<SFMCandidateHistoryContract.CandidateFrame> frame = currentFrame();
        if (frame.isEmpty()) {
            drawClipped(poseStack, minecraft, bounds, x, y, statusMessage, statusColour());
            y += line;
            drawLiveInvariants(poseStack, minecraft, bounds, x, y, line);
            return;
        }

        SFMCandidateHistoryContract.CandidateFrame value = frame.orElseThrow();
        drawClipped(poseStack, minecraft, bounds, x, y,
                "candidate " + shortIdentity(value.address().trajectoryPlanRevisionId())
                        + " · " + shortIdentity(value.address().routeId())
                        + " @" + value.address().routeStepPosition(), statusColour(value));
        y += line;
        y = drawWrapped(poseStack, minecraft, bounds, x, y, line,
                value.statusNarration(), statusColour(value), 2);
        if (value.document().isPresent()) {
            SFMCandidateHistoryContract.CandidateDocument document = value.document().orElseThrow();
            drawClipped(poseStack, minecraft, bounds, x, y,
                    "candidate bytes · " + document.stateHash(), MUTED);
            y += line + 2;
            for (String documentLine : document.text().split("\\n", -1)) {
                if (y + line > bounds.y() + bounds.height()) break;
                drawClipped(poseStack, minecraft, bounds, x + 8, y, documentLine, DOCUMENT);
                y += line;
            }
        } else {
            drawClipped(poseStack, minecraft, bounds, x, y,
                    "No candidate bytes were fabricated.", statusColour(value));
            y += line;
            if (value.lastTrustworthyPredecessorStateId().isPresent()) {
                drawClipped(poseStack, minecraft, bounds, x, y,
                        "trusted prior "
                                + shortIdentity(value.lastTrustworthyPredecessorStateId().orElseThrow()), MUTED);
            }
            y += line;
        }
        drawLiveInvariants(poseStack, minecraft, bounds, x, y + 2, line);
    }

    public LoadStatus loadStatus() {
        return loadStatus;
    }

    public int currentPosition() {
        return currentPosition;
    }

    public Optional<SFMCandidateHistoryContract.CandidateFrame> currentFrame() {
        SFMCandidateHistoryContract.CandidateRouteProjection current = projection;
        return current == null || currentPosition > current.lastPosition()
                ? Optional.empty()
                : Optional.of(current.frame(currentPosition));
    }

    public Optional<SFMCandidateHistoryContract.CandidateRouteProjection> projection() {
        return Optional.ofNullable(projection);
    }

    public Optional<String> pinnedPlanRevisionId() {
        return Optional.ofNullable(pinnedPlanRevisionId);
    }

    public Optional<String> pinnedRouteId() {
        return Optional.ofNullable(pinnedRouteId);
    }

    void applyCatalog(SFMHistoryGraphRuntime.CatalogEvent event) {
        SFMHistoryGraphRuntime.MachineSnapshot snapshot;
        if (pinnedMachineId == null) {
            List<SFMHistoryGraphRuntime.MachineSnapshot> matches = event.resolve(episodeSelector);
            if (matches.size() != 1) {
                loadStatus = LoadStatus.WAITING_FOR_ROUTE;
                statusMessage = matches.isEmpty()
                        ? "Episode selector matched no trajectory machine"
                        : "Episode selector matched more than one trajectory machine";
                return;
            }
            snapshot = matches.get(0);
            pinnedMachineId = snapshot.machineId();
        } else {
            Optional<SFMHistoryGraphRuntime.MachineSnapshot> retained = event.machine(pinnedMachineId);
            if (retained.isEmpty()) {
                statusMessage = "Pinned trajectory machine is no longer registered";
                return;
            }
            snapshot = retained.orElseThrow();
        }
        latestMachineSnapshot = snapshot;
        if (pinnedPlanRevisionId != null) return;

        Optional<String> selectedPlan = requestedPlanRevisionId.isPresent()
                ? requestedPlanRevisionId
                : snapshot.planBook().selectedPlanRevisionId();
        if (selectedPlan.isEmpty()) {
            loadStatus = LoadStatus.WAITING_FOR_ROUTE;
            statusMessage = "Waiting for a selected trajectory plan";
            return;
        }
        SFMTrajectoryContract.TrajectoryPlanRevision plan = snapshot.planBook().plans().stream()
                .filter(value -> value.id().equals(selectedPlan.orElseThrow()))
                .findFirst()
                .orElse(null);
        if (plan == null) {
            loadStatus = LoadStatus.FAILED;
            statusMessage = "Requested trajectory plan is not retained";
            return;
        }
        Optional<String> selectedRoute = requestedRouteId.isPresent()
                ? requestedRouteId
                : plan.selectedRouteId();
        if (selectedRoute.isEmpty()) {
            loadStatus = LoadStatus.WAITING_FOR_ROUTE;
            statusMessage = "Waiting for a selected candidate route";
            return;
        }
        SFMTrajectoryContract.TrajectoryRoute route = plan.routes().stream()
                .filter(value -> value.id().equals(selectedRoute.orElseThrow()))
                .findFirst()
                .orElse(null);
        if (route == null) {
            loadStatus = LoadStatus.FAILED;
            statusMessage = "Requested candidate route is not retained";
            return;
        }

        pinnedPlanRevisionId = plan.id();
        pinnedRouteId = route.id();
        pinnedRoute = route;
        lastPosition = route.steps().size();
        currentPosition = Math.min(currentPosition, lastPosition);
        startProjection();
    }

    private void startProjection() {
        String machineId = Objects.requireNonNull(pinnedMachineId, "pinnedMachineId");
        String planId = Objects.requireNonNull(pinnedPlanRevisionId, "pinnedPlanRevisionId");
        String routeId = Objects.requireNonNull(pinnedRouteId, "pinnedRouteId");
        long generation = ++projectionGeneration;
        loadStatus = LoadStatus.QUEUED;
        statusMessage = "Candidate projection queued";
        try {
            projectionExecutor.execute(() -> {
                pendingProjectionUpdates.add(ProjectionUpdate.running(generation));
                try {
                    Optional<SFMCandidateHistoryContract.CandidateRouteProjection> projected =
                            runtime.projectCandidateRoute(machineId, planId, routeId);
                    pendingProjectionUpdates.add(projected
                            .map(value -> ProjectionUpdate.ready(generation, value))
                            .orElseGet(() -> ProjectionUpdate.failed(
                                    generation,
                                    "Trajectory controller does not expose candidate frames"
                            )));
                } catch (RuntimeException failure) {
                    pendingProjectionUpdates.add(ProjectionUpdate.failed(
                            generation,
                            failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage()
                    ));
                }
            });
        } catch (RuntimeException rejected) {
            pendingProjectionUpdates.add(ProjectionUpdate.failed(
                    generation,
                    "Candidate projection queue is full"
            ));
        }
    }

    private void applyProjectionUpdate(ProjectionUpdate update) {
        loadStatus = update.status();
        statusMessage = update.message();
        if (update.projection().isEmpty()) return;
        SFMCandidateHistoryContract.CandidateRouteProjection value = update.projection().orElseThrow();
        if (!Objects.equals(pinnedMachineId, value.machineId())
                || !Objects.equals(pinnedPlanRevisionId, value.trajectoryPlanRevisionId())
                || !Objects.equals(pinnedRouteId, value.routeId())) {
            loadStatus = LoadStatus.FAILED;
            statusMessage = "Candidate projection identity changed while loading";
            return;
        }
        projection = value;
        lastPosition = value.lastPosition();
        currentPosition = Math.min(currentPosition, lastPosition);
    }

    private void drawLiveInvariants(
            PoseStack poseStack,
            Minecraft minecraft,
            SFMScreenPanelBounds bounds,
            int x,
            int y,
            int line
    ) {
        SFMHistoryGraphRuntime.MachineSnapshot current = latestMachineSnapshot;
        if (current == null) return;
        drawClipped(poseStack, minecraft, bounds, x, y,
                "actual state " + shortIdentity(currentStateId(current)), HEADER);
        y += line;
        drawClipped(poseStack, minecraft, bounds, x, y,
                "actual IP " + current.machine().instructionPointer()
                        .map(pointer -> shortIdentity(pointer.planRevisionId())
                                + " · " + shortIdentity(pointer.routeId())
                                + " @" + pointer.nextStepIndex())
                        .orElse("none"), HEADER);
        y += line;
        drawClipped(poseStack, minecraft, bounds, x, y,
                "Seeking changes neither value.", MATERIALIZED);
    }

    private static String currentStateId(SFMHistoryGraphRuntime.MachineSnapshot snapshot) {
        String headId = snapshot.machine().actualHistoryHeadId();
        return snapshot.history().heads().stream()
                .filter(head -> head.id().equals(headId))
                .map(SFMHistoryGraphContract.HistoryHead::stateRevisionId)
                .findFirst()
                .orElse(headId);
    }

    private static String shortIdentity(String value) {
        int separator = value.lastIndexOf('/');
        if (separator < 0) return value.length() <= 24 ? value : value.substring(0, 24) + "…";
        int previous = value.lastIndexOf('/', separator - 1);
        String tail = value.substring(Math.max(0, previous + 1));
        if (tail.length() <= 24) return tail;
        return tail.substring(0, 15) + "…" + tail.substring(tail.length() - 8);
    }

    private int statusColour() {
        return loadStatus == LoadStatus.FAILED ? BARRIER
                : loadStatus == LoadStatus.READY ? MATERIALIZED : UNAVAILABLE;
    }

    private static int statusColour(SFMCandidateHistoryContract.CandidateFrame frame) {
        return switch (frame.address().projectionStatus()) {
            case MATERIALIZED -> MATERIALIZED;
            case EXTERNAL_BARRIER -> BARRIER;
            default -> UNAVAILABLE;
        };
    }

    private static void drawClipped(
            PoseStack poseStack,
            Minecraft minecraft,
            SFMScreenPanelBounds bounds,
            int x,
            int y,
            String value,
            int colour
    ) {
        int available = Math.max(0, bounds.x() + bounds.width() - PADDING - x);
        SFMFontUtils.draw(
                poseStack,
                minecraft.font,
                minecraft.font.plainSubstrByWidth(value, available),
                x,
                y,
                colour,
                false
        );
    }

    private static int drawWrapped(
            PoseStack poseStack,
            Minecraft minecraft,
            SFMScreenPanelBounds bounds,
            int x,
            int y,
            int lineHeight,
            String value,
            int colour,
            int maxLines
    ) {
        int available = Math.max(0, bounds.x() + bounds.width() - PADDING - x);
        List<net.minecraft.util.FormattedCharSequence> lines = minecraft.font.split(
                Component.literal(value),
                available
        );
        int count = Math.min(maxLines, lines.size());
        for (int index = 0; index < count; index++) {
            SFMFontUtils.draw(poseStack, minecraft.font, lines.get(index), x, y, colour, false);
            y += lineHeight;
        }
        return y;
    }

    private static Optional<String> requireOptionalText(Optional<String> value, String label) {
        Objects.requireNonNull(value, label);
        return value.map(item -> {
            if (item.isBlank()) throw new IllegalArgumentException(label + " must not be blank");
            return item;
        });
    }

    private record ProjectionUpdate(
            long generation,
            LoadStatus status,
            Optional<SFMCandidateHistoryContract.CandidateRouteProjection> projection,
            String message
    ) {
        private ProjectionUpdate {
            if (generation < 0) throw new IllegalArgumentException("generation must not be negative");
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(projection, "projection");
            if (message == null || message.isBlank()) throw new IllegalArgumentException("message must not be blank");
        }

        static ProjectionUpdate running(long generation) {
            return new ProjectionUpdate(generation, LoadStatus.RUNNING, Optional.empty(),
                    "Candidate projection running off the render thread");
        }

        static ProjectionUpdate ready(
                long generation,
                SFMCandidateHistoryContract.CandidateRouteProjection projection
        ) {
            return new ProjectionUpdate(generation, LoadStatus.READY, Optional.of(projection),
                    "Candidate projection ready");
        }

        static ProjectionUpdate failed(long generation, String message) {
            return new ProjectionUpdate(generation, LoadStatus.FAILED, Optional.empty(), message);
        }
    }
}
