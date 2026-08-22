package ca.teamdman.sfm.client.screen.history.document;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.client.history.canvas.SFMHistoryCanvasInteractionState;
import ca.teamdman.sfm.client.history.canvas.SFMHistoryCanvasLayout;
import ca.teamdman.sfm.client.history.canvas.SFMHistoryCanvasLayoutEngine;
import ca.teamdman.sfm.client.history.canvas.SFMHistoryCanvasSpatialIndex;
import ca.teamdman.sfm.client.history.document.SFMDocumentHistoryContract;
import ca.teamdman.sfm.client.history.document.runtime.SFMDocumentHistoryRuntime;
import ca.teamdman.sfm.client.history.document.runtime.SFMDocumentHistorySelector;
import ca.teamdman.sfm.client.history.presentation.SFMDocumentHistoryPresentationProjection;
import ca.teamdman.sfm.client.history.presentation.SFMHistoryGraphPresentationModel;
import ca.teamdman.sfm.client.screen.SFMFontUtils;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenPanel;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenPanelBounds;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspacePanelContext;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** Push-fed, renderer-backed view of one ordinary document-history session. */
public final class SFMDocumentHistoryPanel implements SFMScreenPanel {
    public enum LoadStatus {
        WAITING_FOR_SESSION,
        QUEUED,
        READY,
        FAILED
    }

    public enum PresentationMode {
        CANVAS,
        TRANSCRIPT
    }

    private static final int BACKGROUND = 0xF0101218;
    private static final int CANVAS_BACKGROUND = 0xFF151921;
    private static final int DETAILS_BACKGROUND = 0xF021252D;
    private static final int HEADER = 0xFFEDF6F7;
    private static final int MUTED = 0xFF93A7AC;
    private static final int ACTION_BLUE = 0xFF1F4F73;
    private static final int STATE_AMBER = 0xFF9A5A00;
    private static final int CURRENT_HEAD = 0xFF63D7D0;
    private static final int RETAINED = 0xFFC39BFF;
    private static final int SELECTED = 0xFFFFD166;
    private static final int HOVERED = 0xFFFFFFFF;
    private static final int EDGE = 0xFF829398;
    private static final int JUMP_EDGE = 0xFFC39BFF;
    private static final int ERROR = 0xFFFF7373;
    private static final int PADDING = 6;
    private static final int HEADER_HEIGHT = 26;
    private static final int PREFERRED_DETAILS_HEIGHT = 66;
    private static final int HIT_TOLERANCE = 4;
    private static final int CURVE_SEGMENTS = 24;
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
                        "sfm-document-history-layout-" + NEXT_WORKER_ID.getAndIncrement()
                );
                thread.setDaemon(true);
                return thread;
            },
            new ThreadPoolExecutor.AbortPolicy()
    );
    private static final ResourceLocation USAGE = new ResourceLocation(SFM.MOD_ID, "default");

    private final SFMDocumentHistoryRuntime runtime;
    private final SFMDocumentHistorySelector selector;
    private final Executor projectionExecutor;
    private final AtomicReference<SFMDocumentHistoryRuntime.CatalogEvent> pendingCatalogEvent =
            new AtomicReference<>();
    private final ConcurrentLinkedQueue<ProjectionUpdate> pendingProjectionUpdates =
            new ConcurrentLinkedQueue<>();
    private final Object subscriptionLock = new Object();

    private @Nullable SFMDocumentHistoryRuntime.Subscription subscription;
    private long subscriptionGeneration;
    private long projectionRequestGeneration;
    private long catalogRevision = -1;
    private long publicationGeneration = -1;
    private LoadStatus loadStatus = LoadStatus.WAITING_FOR_SESSION;
    private String statusMessage;
    private @Nullable String resolvedSessionId;
    private @Nullable SFMDocumentHistoryContract.Projection documentProjection;
    private @Nullable SFMHistoryCanvasLayoutEngine.Result canvas;
    private SFMHistoryCanvasLayout.Orientation orientation = SFMHistoryCanvasLayout.Orientation.TOP_DOWN;
    private PresentationMode presentationMode = PresentationMode.CANVAS;
    private SFMHistoryCanvasInteractionState interaction = SFMHistoryCanvasInteractionState.empty();
    private SFMDocumentHistoryViewport viewport = SFMDocumentHistoryViewport.identity();
    private boolean viewportTouched;
    private boolean detailsExpanded;
    private int transcriptScroll;
    private boolean draggingCanvas;
    private SFMScreenPanelBounds lastBounds = new SFMScreenPanelBounds(0, 0, 1, 1);
    private SFMScreenPanelBounds canvasBounds = new SFMScreenPanelBounds(0, HEADER_HEIGHT, 1, 1);
    private SFMScreenPanelBounds transposeButtonBounds = new SFMScreenPanelBounds(0, 0, 1, 1);
    private SFMScreenPanelBounds viewButtonBounds = new SFMScreenPanelBounds(0, 0, 1, 1);

    public SFMDocumentHistoryPanel(SFMDocumentHistorySelector selector) {
        this(SFMDocumentHistoryRuntime.get(), selector, PROJECTION_EXECUTOR);
    }

    SFMDocumentHistoryPanel(
            SFMDocumentHistoryRuntime runtime,
            SFMDocumentHistorySelector selector,
            Executor projectionExecutor
    ) {
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        this.selector = Objects.requireNonNull(selector, "selector");
        this.projectionExecutor = Objects.requireNonNull(projectionExecutor, "projectionExecutor");
        statusMessage = waitingMessage(SFMDocumentHistoryRuntime.ResolutionStatus.NOT_REGISTERED);
    }

    @Override
    public Component title() {
        return Component.literal("Document History");
    }

    @Override
    public ResourceLocation keyboardUsageSituationId() {
        return USAGE;
    }

    @Override
    public Component narration() {
        String selected = selectedNarration().orElse("No canvas entity selected.");
        return Component.literal("Document History. " + statusMessage + ". " + selected);
    }

    @Override
    public void opened(Minecraft minecraft, SFMScreenPanelBounds bounds, SFMWorkspacePanelContext context) {
        lastBounds = Objects.requireNonNull(bounds, "bounds");
        updateGeometry(bounds);
        SFMDocumentHistoryRuntime.Subscription previous;
        long generation;
        synchronized (subscriptionLock) {
            previous = subscription;
            subscription = null;
            generation = ++subscriptionGeneration;
            projectionRequestGeneration++;
            pendingCatalogEvent.set(null);
            pendingProjectionUpdates.clear();
        }
        if (previous != null) previous.close();
        SFMDocumentHistoryRuntime.Subscription opened = runtime.subscribe(
                event -> acceptCatalog(generation, event)
        );
        synchronized (subscriptionLock) {
            if (generation == subscriptionGeneration) subscription = opened;
            else opened.close();
        }
    }

    @Override
    public void resized(Minecraft minecraft, SFMScreenPanelBounds bounds) {
        lastBounds = Objects.requireNonNull(bounds, "bounds");
        updateGeometry(bounds);
        if (!viewportTouched && canvas != null) fitCanvas();
    }

    @Override
    public void closed() {
        SFMDocumentHistoryRuntime.Subscription closing;
        synchronized (subscriptionLock) {
            subscriptionGeneration++;
            projectionRequestGeneration++;
            closing = subscription;
            subscription = null;
            pendingCatalogEvent.set(null);
            pendingProjectionUpdates.clear();
        }
        if (closing != null) closing.close();
        clearPresentation();
        catalogRevision = -1;
        publicationGeneration = -1;
        statusMessage = waitingMessage(SFMDocumentHistoryRuntime.ResolutionStatus.NOT_REGISTERED);
    }

    @Override
    public void tick() {
        SFMDocumentHistoryRuntime.CatalogEvent event = pendingCatalogEvent.getAndSet(null);
        if (event != null && event.revision() >= catalogRevision) applyCatalog(event);

        ProjectionUpdate newest = null;
        ProjectionUpdate update;
        while ((update = pendingProjectionUpdates.poll()) != null) {
            if (newest == null || update.requestGeneration() >= newest.requestGeneration()) newest = update;
        }
        if (newest != null && newest.requestGeneration() == projectionRequestGeneration) {
            applyProjectionUpdate(newest);
        }
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
        if (!bounds.equals(lastBounds)) {
            lastBounds = bounds;
            updateGeometry(bounds);
            if (!viewportTouched && canvas != null) fitCanvas();
        }
        GuiComponent.fill(poseStack, bounds.x(), bounds.y(), bounds.x() + bounds.width(),
                bounds.y() + bounds.height(), BACKGROUND);
        drawHeader(poseStack, minecraft, bounds);
        GuiComponent.fill(poseStack, canvasBounds.x(), canvasBounds.y(),
                canvasBounds.x() + canvasBounds.width(), canvasBounds.y() + canvasBounds.height(),
                CANVAS_BACKGROUND);

        SFMHistoryCanvasLayoutEngine.Result current = canvas;
        if (current == null) {
            drawClipped(poseStack, minecraft, canvasBounds, canvasBounds.x() + PADDING,
                    canvasBounds.y() + PADDING, statusMessage, loadStatus == LoadStatus.FAILED ? ERROR : MUTED);
            return;
        }

        if (presentationMode == PresentationMode.TRANSCRIPT) {
            drawTranscript(poseStack, minecraft);
        } else {
            SFMHistoryCanvasLayout.Rect visibleCanvas = viewport.visibleCanvasBounds(
                    canvasBounds.width(),
                    canvasBounds.height()
            );
            SFMHistoryCanvasSpatialIndex.CullingResult visible = current.visible(visibleCanvas);
            for (SFMHistoryCanvasLayout.Edge edge : visible.edges()) drawEdge(poseStack, edge);
            for (SFMHistoryCanvasLayout.Node node : visible.nodes()) drawNode(poseStack, minecraft, node);
        }
        drawDetails(poseStack, minecraft, bounds);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return switch (keyCode) {
            case GLFW.GLFW_KEY_T -> {
                transpose();
                yield true;
            }
            case GLFW.GLFW_KEY_V -> {
                togglePresentationMode();
                yield true;
            }
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                if (interaction.selected().isEmpty()) yield false;
                detailsExpanded = !detailsExpanded;
                updateGeometry(lastBounds);
                if (!viewportTouched && canvas != null) fitCanvas();
                yield true;
            }
            case GLFW.GLFW_KEY_R, GLFW.GLFW_KEY_HOME -> {
                fitCanvas();
                yield canvas != null;
            }
            case GLFW.GLFW_KEY_UP, GLFW.GLFW_KEY_LEFT -> selectRelative(-1);
            case GLFW.GLFW_KEY_DOWN, GLFW.GLFW_KEY_RIGHT -> selectRelative(1);
            default -> false;
        };
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && contains(transposeButtonBounds, mouseX, mouseY)) {
            transpose();
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && contains(viewButtonBounds, mouseX, mouseY)) {
            togglePresentationMode();
            return true;
        }
        if (!contains(canvasBounds, mouseX, mouseY)) return false;
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT && button != GLFW.GLFW_MOUSE_BUTTON_MIDDLE) return false;
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            Optional<SFMHistoryCanvasSpatialIndex.Hit> hit = hitTest(mouseX, mouseY);
            interaction = interaction.select(hit);
            draggingCanvas = hit.isEmpty();
        } else {
            draggingCanvas = true;
        }
        return true;
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        if (!contains(canvasBounds, mouseX, mouseY)) {
            interaction = interaction.clearHover();
            return;
        }
        interaction = interaction.hover(hitTest(mouseX, mouseY));
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if ((button == GLFW.GLFW_MOUSE_BUTTON_LEFT || button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE)
                && draggingCanvas) {
            draggingCanvas = false;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (!draggingCanvas
                || (button != GLFW.GLFW_MOUSE_BUTTON_LEFT && button != GLFW.GLFW_MOUSE_BUTTON_MIDDLE)) return false;
        viewport = viewport.panBy(dragX, dragY);
        viewportTouched = true;
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (delta == 0.0D || !contains(canvasBounds, mouseX, mouseY) || canvas == null) return false;
        if (presentationMode == PresentationMode.TRANSCRIPT) {
            int visibleLines = Math.max(1, canvasBounds.height() / 12);
            int maximum = Math.max(0, accessibleTranscript().size() - visibleLines);
            transcriptScroll = Math.max(0, Math.min(maximum,
                    transcriptScroll + (delta < 0.0D ? 3 : -3)));
            return true;
        }
        double factor = Math.pow(1.15D, delta);
        viewport = viewport.zoomAt(mouseX - canvasBounds.x(), mouseY - canvasBounds.y(), factor);
        viewportTouched = true;
        return true;
    }

    public LoadStatus loadStatus() {
        return loadStatus;
    }

    public String statusMessage() {
        return statusMessage;
    }

    public long catalogRevision() {
        return catalogRevision;
    }

    public long publicationGeneration() {
        return publicationGeneration;
    }

    public Optional<String> resolvedSessionId() {
        return Optional.ofNullable(resolvedSessionId);
    }

    public SFMHistoryCanvasLayout.Orientation orientation() {
        return orientation;
    }

    public PresentationMode presentationMode() {
        return presentationMode;
    }

    public boolean detailsExpanded() {
        return detailsExpanded;
    }

    public SFMDocumentHistoryViewport viewport() {
        return viewport;
    }

    public Optional<SFMDocumentHistoryContract.Projection> documentProjection() {
        return Optional.ofNullable(documentProjection);
    }

    public Optional<SFMHistoryCanvasLayout.Snapshot> layoutSnapshot() {
        return Optional.ofNullable(canvas).map(SFMHistoryCanvasLayoutEngine.Result::snapshot);
    }

    public List<String> accessibleTranscript() {
        return canvas == null ? List.of() : canvas.transcript().accessibleLines();
    }

    public Optional<SFMHistoryCanvasSpatialIndex.Subject> selectedSubject() {
        return interaction.selected();
    }

    public List<SFMHistoryGraphPresentationModel.Detail> selectedDetails() {
        if (canvas == null || interaction.selected().isEmpty()) return List.of();
        SFMHistoryCanvasSpatialIndex.Subject selected = interaction.selected().orElseThrow();
        return selected.kind() == SFMHistoryCanvasLayout.SubjectKind.NODE
                ? canvas.spatialIndex().node(selected.stableId()).map(node -> node.source().details()).orElse(List.of())
                : canvas.spatialIndex().edge(selected.stableId()).map(edge -> edge.source().details()).orElse(List.of());
    }

    public boolean selectSubject(SFMHistoryCanvasSpatialIndex.Subject subject) {
        Objects.requireNonNull(subject, "subject");
        if (canvas == null || !canvas.spatialIndex().contains(subject)) return false;
        interaction = interaction.select(subject);
        return true;
    }

    public SFMDocumentHistoryPanelArtifact artifact() {
        return new SFMDocumentHistoryPanelArtifact(
                SFMDocumentHistoryPanelArtifact.SCHEMA,
                selector.canonical(),
                loadStatus,
                statusMessage,
                catalogRevision,
                publicationGeneration,
                Optional.ofNullable(resolvedSessionId),
                Optional.ofNullable(documentProjection),
                layoutSnapshot(),
                accessibleTranscript(),
                selectedSubject(),
                viewport
        );
    }

    public void transpose() {
        orientation = orientation.transposed();
        if (canvas != null) {
            canvas = canvas.transpose();
            interaction = canvas.retainInteraction(interaction);
            if (!viewportTouched) fitCanvas();
        }
    }

    public void togglePresentationMode() {
        presentationMode = presentationMode == PresentationMode.CANVAS
                ? PresentationMode.TRANSCRIPT
                : PresentationMode.CANVAS;
        transcriptScroll = Math.max(0, transcriptScroll);
    }

    public void fitCanvas() {
        if (canvas == null) return;
        viewport = SFMDocumentHistoryViewport.fit(
                canvas.snapshot().contentBounds(),
                canvasBounds.width(),
                canvasBounds.height(),
                PADDING
        );
        viewportTouched = false;
    }

    private void acceptCatalog(long generation, SFMDocumentHistoryRuntime.CatalogEvent event) {
        synchronized (subscriptionLock) {
            if (generation != subscriptionGeneration) return;
            pendingCatalogEvent.accumulateAndGet(event, (previous, incoming) ->
                    previous == null || incoming.revision() >= previous.revision() ? incoming : previous);
        }
    }

    private void applyCatalog(SFMDocumentHistoryRuntime.CatalogEvent event) {
        catalogRevision = event.revision();
        SFMDocumentHistoryRuntime.Resolution resolution = event.resolve(selector);
        if (resolution.status() != SFMDocumentHistoryRuntime.ResolutionStatus.RESOLVED) {
            projectionRequestGeneration++;
            clearPresentation();
            loadStatus = LoadStatus.WAITING_FOR_SESSION;
            statusMessage = waitingMessage(resolution.status());
            return;
        }
        SFMDocumentHistoryRuntime.SessionEntry entry = resolution.session().orElseThrow();
        scheduleProjection(entry);
    }

    private void scheduleProjection(SFMDocumentHistoryRuntime.SessionEntry entry) {
        long requestGeneration = ++projectionRequestGeneration;
        SFMHistoryCanvasLayout.Orientation requestOrientation = orientation;
        resolvedSessionId = entry.sessionId();
        loadStatus = LoadStatus.QUEUED;
        statusMessage = "Preparing history for " + entry.identity().documentId();
        try {
            projectionExecutor.execute(() -> {
                try {
                    SFMDocumentHistoryContract.Projection projection = entry.session().projection();
                    SFMHistoryGraphPresentationModel.Presentation presentation =
                            SFMDocumentHistoryPresentationProjection.project(projection);
                    SFMHistoryCanvasLayoutEngine.Request request = SFMHistoryCanvasLayoutEngine.Request
                            .defaults()
                            .withOrientation(requestOrientation);
                    SFMHistoryCanvasLayoutEngine.Result result =
                            SFMHistoryCanvasLayoutEngine.layout(presentation, request);
                    pendingProjectionUpdates.add(new ProjectionReady(
                            requestGeneration,
                            entry.sessionId(),
                            projection,
                            result,
                            requestOrientation
                    ));
                } catch (RuntimeException exception) {
                    pendingProjectionUpdates.add(new ProjectionFailed(
                            requestGeneration,
                            entry.sessionId(),
                            describe(exception)
                    ));
                }
            });
        } catch (RejectedExecutionException exception) {
            pendingProjectionUpdates.add(new ProjectionFailed(
                    requestGeneration,
                    entry.sessionId(),
                    "History layout queue is full; the next pushed change will retry"
            ));
        }
    }

    private void applyProjectionUpdate(ProjectionUpdate update) {
        if (update instanceof ProjectionFailed failed) {
            loadStatus = LoadStatus.FAILED;
            statusMessage = failed.message();
            return;
        }
        ProjectionReady ready = (ProjectionReady) update;
        boolean hadCanvas = canvas != null;
        documentProjection = ready.projection();
        canvas = ready.orientation() == orientation ? ready.canvas() : ready.canvas().transpose();
        resolvedSessionId = ready.sessionId();
        publicationGeneration = ready.projection().generation();
        interaction = canvas.retainInteraction(interaction);
        if (interaction.selected().isEmpty()) interaction = selectCurrentHead(canvas, interaction);
        loadStatus = LoadStatus.READY;
        statusMessage = ready.projection().identity().documentId()
                + " · revision " + shortIdentity(ready.projection().currentRevisionId());
        if (!hadCanvas && !viewportTouched) fitCanvas();
    }

    private void clearPresentation() {
        resolvedSessionId = null;
        documentProjection = null;
        canvas = null;
        interaction = SFMHistoryCanvasInteractionState.empty();
        transcriptScroll = 0;
        draggingCanvas = false;
    }

    private void updateGeometry(SFMScreenPanelBounds bounds) {
        int detailsHeight = bounds.height() >= 120
                ? detailsExpanded
                ? Math.max(PREFERRED_DETAILS_HEIGHT, bounds.height() / 2)
                : Math.min(PREFERRED_DETAILS_HEIGHT, Math.max(0, bounds.height() / 3))
                : 0;
        int bodyY = bounds.y() + Math.min(HEADER_HEIGHT, bounds.height());
        int bodyHeight = Math.max(0, bounds.height() - (bodyY - bounds.y()) - detailsHeight);
        canvasBounds = new SFMScreenPanelBounds(bounds.x(), bodyY, Math.max(0, bounds.width()), bodyHeight);
        int buttonWidth = Math.min(86, Math.max(1, bounds.width() / 4));
        transposeButtonBounds = new SFMScreenPanelBounds(
                bounds.x() + Math.max(0, bounds.width() - buttonWidth - 4),
                bounds.y() + 4,
                buttonWidth,
                Math.min(18, Math.max(1, bounds.height() - 4))
        );
        viewButtonBounds = new SFMScreenPanelBounds(
                Math.max(bounds.x(), transposeButtonBounds.x() - buttonWidth - 4),
                transposeButtonBounds.y(),
                buttonWidth,
                transposeButtonBounds.height()
        );
    }

    private void drawHeader(PoseStack poseStack, Minecraft minecraft, SFMScreenPanelBounds bounds) {
        drawClipped(poseStack, minecraft, bounds, bounds.x() + PADDING, bounds.y() + PADDING,
                "Document History · " + loadStatus, loadStatus == LoadStatus.FAILED ? ERROR : HEADER);
        int buttonColour = 0xFF2B3440;
        GuiComponent.fill(poseStack, transposeButtonBounds.x(), transposeButtonBounds.y(),
                transposeButtonBounds.x() + transposeButtonBounds.width(),
                transposeButtonBounds.y() + transposeButtonBounds.height(), buttonColour);
        drawClipped(poseStack, minecraft, transposeButtonBounds,
                transposeButtonBounds.x() + 4, transposeButtonBounds.y() + 5,
                orientation == SFMHistoryCanvasLayout.Orientation.TOP_DOWN ? "T: top-down" : "T: left-right",
                HEADER);
        GuiComponent.fill(poseStack, viewButtonBounds.x(), viewButtonBounds.y(),
                viewButtonBounds.x() + viewButtonBounds.width(),
                viewButtonBounds.y() + viewButtonBounds.height(), buttonColour);
        drawClipped(poseStack, minecraft, viewButtonBounds,
                viewButtonBounds.x() + 4, viewButtonBounds.y() + 5,
                presentationMode == PresentationMode.CANVAS ? "V: canvas" : "V: transcript",
                HEADER);
    }

    private void drawEdge(PoseStack poseStack, SFMHistoryCanvasLayout.Edge edge) {
        List<ScreenPoint> points = edge.points().stream().map(this::toScreen).toList();
        int colour = edge.curved() ? JUMP_EDGE : EDGE;
        ScreenPoint previous;
        ScreenPoint arrowFrom;
        ScreenPoint end;
        if (edge.curved()) {
            ScreenPoint start = points.get(0);
            ScreenPoint control = points.get(1);
            end = points.get(2);
            previous = start;
            arrowFrom = start;
            for (int index = 1; index <= CURVE_SEGMENTS; index++) {
                double t = index / (double) CURVE_SEGMENTS;
                double oneMinus = 1.0D - t;
                ScreenPoint current = new ScreenPoint(
                        oneMinus * oneMinus * start.x() + 2.0D * oneMinus * t * control.x() + t * t * end.x(),
                        oneMinus * oneMinus * start.y() + 2.0D * oneMinus * t * control.y() + t * t * end.y()
                );
                drawLine(poseStack, previous, current, colour);
                arrowFrom = previous;
                previous = current;
            }
        } else {
            previous = points.get(0);
            arrowFrom = previous;
            end = points.get(1);
            drawLine(poseStack, previous, end, colour);
        }
        drawArrowHead(poseStack, arrowFrom, end, colour);
    }

    private void drawNode(PoseStack poseStack, Minecraft minecraft, SFMHistoryCanvasLayout.Node node) {
        ScreenRect screen = toScreen(node.bounds());
        ScreenRect marker = toScreen(node.markerBounds());
        ScreenRect label = toScreen(node.labelBounds());
        int fill = node.lane() == SFMHistoryCanvasLayout.Lane.ACTION ? ACTION_BLUE : STATE_AMBER;
        fillCircleClipped(poseStack, marker, fill);
        int outline = node.source().roles().contains(SFMHistoryGraphPresentationModel.LegendRole.ACTUAL_HEAD)
                ? CURRENT_HEAD
                : node.source().roles().contains(SFMHistoryGraphPresentationModel.LegendRole.RETAINED_ALTERNATIVE)
                ? RETAINED
                : 0xFFB7C4C8;
        SFMHistoryCanvasSpatialIndex.Subject subject = new SFMHistoryCanvasSpatialIndex.Subject(
                SFMHistoryCanvasLayout.SubjectKind.NODE,
                node.stableId()
        );
        if (interaction.selected().filter(subject::equals).isPresent()) outline = SELECTED;
        else if (interaction.hovered().filter(subject::equals).isPresent()) outline = HOVERED;
        outlineCircle(poseStack, marker, outline);

        if (screen.width() < 8 || screen.height() < 8 || label.width() < 4 || label.height() < 4) return;
        int labelX = Math.max(canvasBounds.x() + 2, label.x());
        int labelY = Math.max(canvasBounds.y() + 2, label.y());
        int available = Math.max(0, Math.min(label.right(), canvasBounds.x() + canvasBounds.width()) - labelX - 2);
        if (available <= 0) return;
        String prefix = node.source().roles().contains(SFMHistoryGraphPresentationModel.LegendRole.ACTUAL_HEAD)
                ? "H "
                : node.source().roles().contains(SFMHistoryGraphPresentationModel.LegendRole.RETAINED_ALTERNATIVE)
                ? "R "
                : "";
        int lineHeight = Math.max(1, safeRound(10.0D * viewport.zoom()));
        for (int index = 0; index < node.label().lines().size(); index++) {
            int y = labelY + index * lineHeight;
            if (y >= label.bottom() || y >= canvasBounds.y() + canvasBounds.height()) break;
            String text = (index == 0 ? prefix : "") + node.label().lines().get(index);
            drawClipped(poseStack, minecraft, canvasBounds, labelX, y,
                    minecraft.font.plainSubstrByWidth(text, available), HEADER);
        }
    }

    private void drawTranscript(PoseStack poseStack, Minecraft minecraft) {
        List<String> lines = accessibleTranscript();
        int lineHeight = Math.max(10, minecraft.font.lineHeight + 2);
        int visible = Math.max(1, (canvasBounds.height() - PADDING * 2) / lineHeight);
        transcriptScroll = Math.max(0, Math.min(Math.max(0, lines.size() - visible), transcriptScroll));
        int y = canvasBounds.y() + PADDING;
        for (String line : lines.stream().skip(transcriptScroll).limit(visible).toList()) {
            drawClipped(poseStack, minecraft, canvasBounds, canvasBounds.x() + PADDING, y, line, HEADER);
            y += lineHeight;
        }
        if (lines.isEmpty()) {
            drawClipped(poseStack, minecraft, canvasBounds, canvasBounds.x() + PADDING, y,
                    "No chronological entries yet", MUTED);
        }
    }

    private void drawDetails(PoseStack poseStack, Minecraft minecraft, SFMScreenPanelBounds bounds) {
        int y = canvasBounds.y() + canvasBounds.height();
        int height = bounds.y() + bounds.height() - y;
        if (height <= 0) return;
        SFMScreenPanelBounds details = new SFMScreenPanelBounds(bounds.x(), y, bounds.width(), height);
        GuiComponent.fill(poseStack, details.x(), details.y(), details.x() + details.width(),
                details.y() + details.height(), DETAILS_BACKGROUND);
        int line = Math.max(10, minecraft.font.lineHeight + 2);
        int textY = y + 4;
        Optional<String> narration = selectedNarration();
        drawClipped(poseStack, minecraft, details, details.x() + PADDING, textY,
                narration.orElse("Select a node or edge to inspect its evidence"), narration.isPresent() ? HEADER : MUTED);
        textY += line;
        int detailLimit = detailsExpanded ? Integer.MAX_VALUE : 4;
        for (SFMHistoryGraphPresentationModel.Detail detail : selectedDetails().stream().limit(detailLimit).toList()) {
            if (textY + line > details.y() + details.height()) break;
            drawClipped(poseStack, minecraft, details, details.x() + PADDING, textY,
                    detail.key() + ": " + detail.value(), MUTED);
            textY += line;
        }
    }

    private Optional<String> selectedNarration() {
        if (canvas == null || interaction.selected().isEmpty()) return Optional.empty();
        SFMHistoryCanvasSpatialIndex.Subject selected = interaction.selected().orElseThrow();
        return selected.kind() == SFMHistoryCanvasLayout.SubjectKind.NODE
                ? canvas.spatialIndex().node(selected.stableId()).map(node -> node.source().narration())
                : canvas.spatialIndex().edge(selected.stableId()).map(edge -> edge.source().narration());
    }

    private Optional<SFMHistoryCanvasSpatialIndex.Hit> hitTest(double mouseX, double mouseY) {
        if (canvas == null) return Optional.empty();
        SFMHistoryCanvasLayout.Point point = viewport.screenToCanvas(
                mouseX - canvasBounds.x(),
                mouseY - canvasBounds.y()
        );
        int tolerance = Math.min(64, Math.max(1, (int) Math.ceil(HIT_TOLERANCE / viewport.zoom())));
        return canvas.hitTest(point, tolerance);
    }

    private boolean selectRelative(int delta) {
        if (canvas == null || canvas.transcript().entries().isEmpty()) return false;
        List<SFMHistoryCanvasSpatialIndex.Subject> subjects = canvas.transcript().entries().stream()
                .map(entry -> new SFMHistoryCanvasSpatialIndex.Subject(entry.subjectKind(),
                        entry.key().substring(entry.key().indexOf(':') + 1)))
                .toList();
        int current = interaction.selected().map(subjects::indexOf).orElse(-1);
        int next = current < 0
                ? delta < 0 ? subjects.size() - 1 : 0
                : Math.max(0, Math.min(subjects.size() - 1, current + delta));
        interaction = interaction.select(subjects.get(next));
        return next != current;
    }

    private ScreenPoint toScreen(SFMHistoryCanvasLayout.Point point) {
        return new ScreenPoint(
                canvasBounds.x() + viewport.canvasToScreenX(point.x()),
                canvasBounds.y() + viewport.canvasToScreenY(point.y())
        );
    }

    private ScreenRect toScreen(SFMHistoryCanvasLayout.Rect rect) {
        int x = safeRound(canvasBounds.x() + viewport.canvasToScreenX(rect.x()));
        int y = safeRound(canvasBounds.y() + viewport.canvasToScreenY(rect.y()));
        int right = safeRound(canvasBounds.x() + viewport.canvasToScreenX(rect.right()));
        int bottom = safeRound(canvasBounds.y() + viewport.canvasToScreenY(rect.bottom()));
        return new ScreenRect(x, y, Math.max(1, right - x), Math.max(1, bottom - y));
    }

    private void drawArrowHead(PoseStack poseStack, ScreenPoint previous, ScreenPoint end, int colour) {
        double dx = end.x() - previous.x();
        double dy = end.y() - previous.y();
        double length = Math.hypot(dx, dy);
        if (length < 0.001D) return;
        double unitX = dx / length;
        double unitY = dy / length;
        double backX = end.x() - unitX * 6.0D;
        double backY = end.y() - unitY * 6.0D;
        drawLine(poseStack, end, new ScreenPoint(backX - unitY * 3.0D, backY + unitX * 3.0D), colour);
        drawLine(poseStack, end, new ScreenPoint(backX + unitY * 3.0D, backY - unitX * 3.0D), colour);
    }

    private void drawLine(PoseStack poseStack, ScreenPoint first, ScreenPoint second, int colour) {
        Optional<LineSegment> clipped = clip(first, second, canvasBounds);
        if (clipped.isEmpty()) return;
        ScreenPoint start = clipped.orElseThrow().start();
        ScreenPoint end = clipped.orElseThrow().end();
        int steps = Math.max(1, (int) Math.ceil(Math.max(Math.abs(end.x() - start.x()),
                Math.abs(end.y() - start.y()))));
        for (int index = 0; index <= steps; index++) {
            double t = index / (double) steps;
            int x = safeRound(start.x() + (end.x() - start.x()) * t);
            int y = safeRound(start.y() + (end.y() - start.y()) * t);
            GuiComponent.fill(poseStack, x, y, x + 1, y + 1, colour);
        }
    }

    private void fillClipped(PoseStack poseStack, ScreenRect rect, int colour) {
        int left = Math.max(rect.x(), canvasBounds.x());
        int top = Math.max(rect.y(), canvasBounds.y());
        int right = Math.min(rect.right(), canvasBounds.x() + canvasBounds.width());
        int bottom = Math.min(rect.bottom(), canvasBounds.y() + canvasBounds.height());
        if (left < right && top < bottom) GuiComponent.fill(poseStack, left, top, right, bottom, colour);
    }

    private void fillCircleClipped(PoseStack poseStack, ScreenRect rect, int colour) {
        double radiusX = Math.max(0.5D, rect.width() / 2.0D);
        double radiusY = Math.max(0.5D, rect.height() / 2.0D);
        double centerX = rect.x() + radiusX;
        double centerY = rect.y() + radiusY;
        int top = Math.max(rect.y(), canvasBounds.y());
        int bottom = Math.min(rect.bottom(), canvasBounds.y() + canvasBounds.height());
        for (int y = top; y < bottom; y++) {
            double normalizedY = ((y + 0.5D) - centerY) / radiusY;
            double widthFactor = Math.sqrt(Math.max(0.0D, 1.0D - normalizedY * normalizedY));
            int left = Math.max(canvasBounds.x(), safeRound(centerX - radiusX * widthFactor));
            int right = Math.min(canvasBounds.x() + canvasBounds.width(),
                    safeRound(centerX + radiusX * widthFactor));
            if (left < right) GuiComponent.fill(poseStack, left, y, right, y + 1, colour);
        }
    }

    private void outlineCircle(PoseStack poseStack, ScreenRect rect, int colour) {
        double radiusX = Math.max(0.5D, rect.width() / 2.0D);
        double radiusY = Math.max(0.5D, rect.height() / 2.0D);
        double centerX = rect.x() + radiusX;
        double centerY = rect.y() + radiusY;
        ScreenPoint previous = null;
        int segments = Math.max(12, Math.min(64, (rect.width() + rect.height()) * 2));
        for (int index = 0; index <= segments; index++) {
            double angle = Math.PI * 2.0D * index / segments;
            ScreenPoint point = new ScreenPoint(
                    centerX + Math.cos(angle) * radiusX,
                    centerY + Math.sin(angle) * radiusY
            );
            if (previous != null) drawLine(poseStack, previous, point, colour);
            previous = point;
        }
    }

    private void outline(PoseStack poseStack, ScreenRect rect, int colour) {
        drawLine(poseStack, new ScreenPoint(rect.x(), rect.y()), new ScreenPoint(rect.right() - 1, rect.y()), colour);
        drawLine(poseStack, new ScreenPoint(rect.x(), rect.bottom() - 1),
                new ScreenPoint(rect.right() - 1, rect.bottom() - 1), colour);
        drawLine(poseStack, new ScreenPoint(rect.x(), rect.y()), new ScreenPoint(rect.x(), rect.bottom() - 1), colour);
        drawLine(poseStack, new ScreenPoint(rect.right() - 1, rect.y()),
                new ScreenPoint(rect.right() - 1, rect.bottom() - 1), colour);
    }

    private static Optional<LineSegment> clip(
            ScreenPoint start,
            ScreenPoint end,
            SFMScreenPanelBounds bounds
    ) {
        if (bounds.width() <= 0 || bounds.height() <= 0) return Optional.empty();
        double left = bounds.x();
        double right = bounds.x() + bounds.width() - 1.0D;
        double top = bounds.y();
        double bottom = bounds.y() + bounds.height() - 1.0D;
        double dx = end.x() - start.x();
        double dy = end.y() - start.y();
        double[] range = {0.0D, 1.0D};
        if (!clipBoundary(-dx, start.x() - left, range)
                || !clipBoundary(dx, right - start.x(), range)
                || !clipBoundary(-dy, start.y() - top, range)
                || !clipBoundary(dy, bottom - start.y(), range)) return Optional.empty();
        return Optional.of(new LineSegment(
                new ScreenPoint(start.x() + range[0] * dx, start.y() + range[0] * dy),
                new ScreenPoint(start.x() + range[1] * dx, start.y() + range[1] * dy)
        ));
    }

    private static boolean clipBoundary(double p, double q, double[] range) {
        if (p == 0.0D) return q >= 0.0D;
        double ratio = q / p;
        if (p < 0.0D) {
            if (ratio > range[1]) return false;
            if (ratio > range[0]) range[0] = ratio;
        } else {
            if (ratio < range[0]) return false;
            if (ratio < range[1]) range[1] = ratio;
        }
        return true;
    }

    private static SFMHistoryCanvasInteractionState selectCurrentHead(
            SFMHistoryCanvasLayoutEngine.Result canvas,
            SFMHistoryCanvasInteractionState previous
    ) {
        return canvas.snapshot().markers().stream()
                .filter(marker -> marker.kind() == SFMHistoryGraphPresentationModel.MarkerKind.ACTUAL_HEAD)
                .filter(marker -> marker.subjectKind() == SFMHistoryGraphPresentationModel.MarkerSubjectKind.NODE)
                .findFirst()
                .map(marker -> previous.select(new SFMHistoryCanvasSpatialIndex.Subject(
                        SFMHistoryCanvasLayout.SubjectKind.NODE,
                        marker.subjectId()
                )))
                .orElse(previous);
    }

    private static void drawClipped(
            PoseStack poseStack,
            Minecraft minecraft,
            SFMScreenPanelBounds bounds,
            int x,
            int y,
            String text,
            int colour
    ) {
        int available = Math.max(0, bounds.x() + bounds.width() - x - 2);
        if (available <= 0 || y < bounds.y() || y >= bounds.y() + bounds.height()) return;
        SFMFontUtils.draw(poseStack, minecraft.font,
                minecraft.font.plainSubstrByWidth(text, available), x, y, colour, false);
    }

    private static String waitingMessage(SFMDocumentHistoryRuntime.ResolutionStatus status) {
        return status == SFMDocumentHistoryRuntime.ResolutionStatus.NO_FOCUSED_SESSION
                ? "No document-history session is focused; waiting for a push"
                : "Document-history session is not registered; waiting for a push";
    }

    private static String shortIdentity(String value) {
        int slash = value.lastIndexOf('/');
        String tail = slash < 0 ? value : value.substring(slash + 1);
        return tail.length() <= 20 ? tail : tail.substring(0, 8) + "…" + tail.substring(tail.length() - 8);
    }

    private static String describe(RuntimeException exception) {
        String message = exception.getMessage();
        return "History presentation failed: " + (message == null || message.isBlank()
                ? exception.getClass().getSimpleName()
                : message);
    }

    private static boolean contains(SFMScreenPanelBounds bounds, double x, double y) {
        return x >= bounds.x() && x < bounds.x() + bounds.width()
                && y >= bounds.y() && y < bounds.y() + bounds.height();
    }

    private static int safeRound(double value) {
        if (value <= Integer.MIN_VALUE) return Integer.MIN_VALUE;
        if (value >= Integer.MAX_VALUE) return Integer.MAX_VALUE;
        return (int) Math.round(value);
    }

    private sealed interface ProjectionUpdate permits ProjectionReady, ProjectionFailed {
        long requestGeneration();
    }

    private record ProjectionReady(
            long requestGeneration,
            String sessionId,
            SFMDocumentHistoryContract.Projection projection,
            SFMHistoryCanvasLayoutEngine.Result canvas,
            SFMHistoryCanvasLayout.Orientation orientation
    ) implements ProjectionUpdate {
        private ProjectionReady {
            Objects.requireNonNull(sessionId, "sessionId");
            Objects.requireNonNull(projection, "projection");
            Objects.requireNonNull(canvas, "canvas");
            Objects.requireNonNull(orientation, "orientation");
        }
    }

    private record ProjectionFailed(
            long requestGeneration,
            String sessionId,
            String message
    ) implements ProjectionUpdate {
        private ProjectionFailed {
            Objects.requireNonNull(sessionId, "sessionId");
            Objects.requireNonNull(message, "message");
        }
    }

    private record ScreenPoint(double x, double y) {
    }

    private record ScreenRect(int x, int y, int width, int height) {
        private int right() {
            return x + width;
        }

        private int bottom() {
            return y + height;
        }
    }

    private record LineSegment(ScreenPoint start, ScreenPoint end) {
    }
}
