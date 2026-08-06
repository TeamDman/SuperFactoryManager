package ca.teamdman.sfm.client.terminal;

import ca.teamdman.sfm.client.screen.SFMFontUtils;
import ca.teamdman.sfm.client.screen.SFMScreenRenderUtils;
import ca.teamdman.sfm.client.screen.SFMTerminalPasteConfirmationScreen;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenPanel;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenPanelBounds;
import ca.teamdman.sfm.client.screen.workspace.SFMPanelActionButton;
import ca.teamdman.sfm.client.screen.workspace.SFMPanelActionExecution;
import ca.teamdman.sfm.client.screen.workspace.SFMPanelWidget;
import ca.teamdman.sfm.client.screen.workspace.SFMPanelWidgetHost;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspacePanelContext;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspacePanelMetrics;
import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.localization.LocalizationEntry;
import ca.teamdman.sfm.common.localization.SFMLocalizationDatagen;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.ArrayList;
import java.util.Objects;
import java.util.Optional;

/** Composable terminal leaf. Rust/Vox frames are presented when that backend is configured. */
public final class SFMTerminalPanel implements SFMScreenPanel {
    private static final String FOCUS_HINT_SECONDS = "1.5";
    private static final int CONTENT_HORIZONTAL_PADDING = 8;
    private static final int CONTENT_BOTTOM_PADDING = 4;
    private static final int TITLE_CONTENT_GAP = 12;

    @SFMLocalizationDatagen
    public static final LocalizationEntry ESCAPE_FOCUS_HINT = new LocalizationEntry(
            "gui.sfm.terminal.escape_focus_hint",
            "Press Esc %s more times within %s seconds to close terminal"
    );

    @SFMLocalizationDatagen
    public static final LocalizationEntry TAB_FOCUS_HINT = new LocalizationEntry(
            "gui.sfm.terminal.tab_focus_hint",
            "Press Tab %s more times within %s seconds to return focus to Minecraft"
    );

    private static final int PANEL = 0xF0101218;
    private static final int TEXT = 0xFFE8F0F2;
    private static final int MUTED = 0xFF8AA0A8;
    private static final int ERROR = 0xFFFF7777;
    private static final int INPUT = 0xFF162530;
    private static final ResourceLocation DEFAULT_USAGE = new ResourceLocation(SFM.MOD_ID, "default");
    private static final ResourceLocation TERMINAL_USAGE = new ResourceLocation(SFM.MOD_ID, "terminal");
    private static final ResourceLocation VIEWPORT_ELEMENT = new ResourceLocation(SFM.MOD_ID, "terminal/viewport");
    private static final ResourceLocation START_ELEMENT = new ResourceLocation(SFM.MOD_ID, "terminal/server/start_control");
    private static final ResourceLocation PRESENTATION_ELEMENT = new ResourceLocation(SFM.MOD_ID, "terminal/presentation/select");
    private final SFMTerminalClient client;
    private final SFMTerminalRemoteService remoteService;
    private final SFMTerminalPngRenderer pngRenderer = new SFMTerminalPngRenderer();
    private final SFMTerminalRgbaRenderer rgbaRenderer = new SFMTerminalRgbaRenderer();
    private final SFMTerminalScrollback scrollback = new SFMTerminalScrollback();
    private final SFMTerminalFocusSequence focusSequence = new SFMTerminalFocusSequence();
    private final SFMPanelWidgetHost widgetHost = new SFMPanelWidgetHost();
    private final TerminalViewportWidget viewportWidget = new TerminalViewportWidget();
    private final SFMPanelActionButton startButton;
    private final SFMPanelActionButton presentationButton;
    private final List<SFMPanelActionButton> presentationOptions = new ArrayList<>();
    private String presentationOptionsSignature = "";
    private String input = "";
    private SFMScreenPanelBounds bounds = new SFMScreenPanelBounds(0, 0, 1, 1);
    private SFMWorkspacePanelContext context;
    private Minecraft minecraft;
    private int renderLeft;
    private int renderTop;
    private int renderWidth;
    private int renderHeight;
    private int pressedMouseButtons;
    private boolean suppressCopyRelease;
    private boolean suppressPasteRelease;
    private boolean startRequested;
    private String lastLoggedPresentationStreamIdentity;
    private long lastLoggedPresentationSequence = Long.MIN_VALUE;
    private String connectionStatus;
    private final RustServerStarter rustServerStarter;
    private final PanelActionInvoker actionOverride;
    private StartButtonHitState startButtonHitState = StartButtonHitState.inactive();
    private SFMScreenPanelBounds lastDisconnectedStartButtonBounds;
    private long startButtonAttemptCount;
    private long terminalMouseDispatchCount;
    private boolean presentationMenuOpen;
    private PresentationAxis presentationAxis = PresentationAxis.RENDERER;
    private int rendererSelectionIndex;
    private int transportSelectionIndex;
    private int presentationButtonLeft;
    private int presentationButtonTop;
    private int presentationButtonRight;
    private int presentationButtonBottom;
    private SFMTerminalTuningSettings tuning = SFMTerminalTuningSettings.automatic();
    private SFMTerminalTuningSettings acceptedTuning = SFMTerminalTuningSettings.automatic();
    private boolean tuningPending;
    private SFMTerminalTuningRejection lastTuningRejection;
    private int automaticSurfaceWidth = 1;
    private int automaticSurfaceHeight = 1;
    private int automaticColumns = 1;
    private int automaticRows = 1;
    private SFMScreenPanelBounds viewportLogicalBounds = new SFMScreenPanelBounds(0, 0, 1, 1);
    private Optional<SFMWorkspacePanelMetrics> viewportMetrics = Optional.empty();
    private SFMTerminalFrame lastAcceptedFrame;
    private PendingPaste pendingPaste;
    private boolean disconnectedControlsVisible;
    private String lastPanelActionFeedback = "";

    private record PendingPaste(String text, String preview, String contentId, long interactionEpoch) {
    }

    @FunctionalInterface
    interface RustServerStarter {
        void start() throws Exception;
    }

    @FunctionalInterface
    interface PanelActionInvoker {
        boolean invoke(String draft);
    }

    private record StartButtonHitState(boolean current) {
        private static StartButtonHitState inactive() {
            return new StartButtonHitState(false);
        }

        private static StartButtonHitState active() {
            return new StartButtonHitState(true);
        }
    }

    record ViewportGeometry(
            int left,
            int top,
            int width,
            int height,
            int contentBottom,
            int inputY,
            int lineHeight,
            int columns,
            int rows
    ) {
    }

    private enum PresentationAxis {
        RENDERER,
        TRANSPORT
    }

    public SFMTerminalPanel(SFMTerminalService service) {
        this(
                new SFMTerminalClient(service),
                service instanceof SFMTerminalRemoteService remote ? remote : null,
                () -> SFMTerminalServiceFactory.startRustServer(null),
                null);
    }

    public SFMTerminalPanel(SFMTerminalClient client) {
        this(client, null, () -> SFMTerminalServiceFactory.startRustServer(null), null);
    }

    SFMTerminalPanel(SFMTerminalRemoteService remoteService, RustServerStarter rustServerStarter) {
        this(new SFMTerminalClient(remoteService), remoteService, rustServerStarter, null);
    }

    SFMTerminalPanel(
            SFMTerminalRemoteService remoteService,
            RustServerStarter rustServerStarter,
            PanelActionInvoker actionOverride
    ) {
        this(new SFMTerminalClient(remoteService), remoteService, rustServerStarter, actionOverride);
    }

    private SFMTerminalPanel(
            SFMTerminalClient client,
            SFMTerminalRemoteService remoteService,
            RustServerStarter rustServerStarter,
            PanelActionInvoker actionOverride
    ) {
        this.client = client;
        this.remoteService = remoteService;
        this.rustServerStarter = Objects.requireNonNull(rustServerStarter);
        this.actionOverride = actionOverride;
        this.startButton = new SFMPanelActionButton(
                START_ELEMENT,
                DEFAULT_USAGE,
                Component.literal("Start / Retry Rust server"),
                () -> Component.literal(startRequested
                        ? "Starting Rust terminal server"
                        : "Start or retry Rust terminal server"),
                () -> "sfm action invoke sfm:terminal/server/start",
                () -> executePanelAction("sfm action invoke sfm:terminal/server/start")
        );
        this.presentationButton = new SFMPanelActionButton(
                PRESENTATION_ELEMENT,
                DEFAULT_USAGE,
                Component.literal("Presentation"),
                this::presentationNarration,
                this::currentPresentationActionDraft,
                this::togglePresentationMenu,
                (keyCode, scanCode, modifiers) -> presentationControlKeyPressed(keyCode),
                ignored -> { }
        );
        rebuildTerminalWidgets();
        this.connectionStatus = remoteService == null
                ? "Java-local terminal"
                : "Rust terminal is disconnected";
        if (remoteService == null) {
            scrollback.appendAll(List.of(
                    "Java-local terminal · explicit REPL mode",
                    "Type pwd, ls, cat <file>, echo <text>, or write <file> <text>"
            ));
        }
    }

    private void rebuildTerminalWidgets() {
        presentationOptions.clear();
        if (remoteService != null) {
            List<SFMTerminalRendererOption> rendererOptions = remoteService.rendererOptions();
            for (int index = 0; index < rendererOptions.size(); index++) {
                int optionIndex = index;
                SFMTerminalRendererOption option = rendererOptions.get(index);
                String draft = "sfm action invoke sfm:terminal/renderer/set " + option.id().wireId();
                SFMPanelActionButton button = new SFMPanelActionButton(
                        new ResourceLocation(SFM.MOD_ID,
                                "terminal/presentation/renderer/" + option.id().wireId()),
                        DEFAULT_USAGE,
                        Component.literal("Renderer: " + option.label()),
                        () -> Component.literal(option.supported()
                                ? "Use terminal renderer " + option.label()
                                : "Terminal renderer " + option.label() + " unavailable: "
                                + option.unavailableReason()),
                        () -> draft,
                        () -> selectPresentationOption(PresentationAxis.RENDERER, optionIndex, draft),
                        this::presentationOptionKeyPressed,
                        ignored -> { }
                );
                button.active = option.supported();
                presentationOptions.add(button);
            }
            List<SFMTerminalTransportOption> transportOptions = remoteService.transportOptions();
            for (int index = 0; index < transportOptions.size(); index++) {
                int optionIndex = index;
                SFMTerminalTransportOption option = transportOptions.get(index);
                String draft = "sfm action invoke sfm:terminal/transport/set " + option.id().wireId();
                SFMPanelActionButton button = new SFMPanelActionButton(
                        new ResourceLocation(SFM.MOD_ID,
                                "terminal/presentation/transport/" + option.id().wireId()),
                        DEFAULT_USAGE,
                        Component.literal("Transport: " + option.label()),
                        () -> Component.literal(option.supported()
                                ? "Use terminal transport " + option.label()
                                : "Terminal transport " + option.label() + " unavailable: "
                                + option.unavailableReason()),
                        () -> draft,
                        () -> selectPresentationOption(PresentationAxis.TRANSPORT, optionIndex, draft),
                        this::presentationOptionKeyPressed,
                        ignored -> { }
                );
                button.active = option.supported();
                presentationOptions.add(button);
            }
        }
        List<SFMPanelWidget> children = new ArrayList<>();
        children.add(viewportWidget);
        children.add(startButton);
        children.add(presentationButton);
        children.addAll(presentationOptions);
        widgetHost.setChildren(children);
        updatePresentationOptionVisibility();
        presentationOptionsSignature = presentationOptionsSignature();
    }

    private void refreshPresentationOptions() {
        if (!presentationOptionsSignature.equals(presentationOptionsSignature())) {
            rebuildTerminalWidgets();
        }
    }

    private String presentationOptionsSignature() {
        if (remoteService == null) return "local";
        return remoteService.rendererOptions().stream()
                .map(option -> "r:" + option.id().wireId() + ":" + option.supported()
                        + ":" + option.label() + ":" + option.unavailableReason())
                .collect(java.util.stream.Collectors.joining("|"))
                + "||"
                + remoteService.transportOptions().stream()
                .map(option -> "t:" + option.id().wireId() + ":" + option.supported()
                        + ":" + option.label() + ":" + option.unavailableReason())
                .collect(java.util.stream.Collectors.joining("|"));
    }

    private void synchronizeTerminalWidgets(boolean presented, ViewportGeometry viewport) {
        disconnectedControlsVisible = remoteService != null && !presented;
        viewportWidget.visible = remoteService == null || presented;
        viewportWidget.active = viewportWidget.visible;
        viewportWidget.setPanelBounds(new SFMScreenPanelBounds(
                viewport.left(), viewport.top(), viewport.width(), viewport.height()));
        startButton.visible = disconnectedControlsVisible;
        startButton.active = disconnectedControlsVisible && !startRequested;
        startButton.setMessage(Component.literal(startRequested
                ? "Starting..."
                : "Start / Retry Rust server"));
        if (!disconnectedControlsVisible) invalidateDisconnectedStartButtonPresentation();
        presentationButton.visible = remoteService != null;
        presentationButton.active = remoteService != null;
        int controlHeight = Math.max(1, viewport.lineHeight() + 4);
        layoutPresentationControl(controlHeight);
        presentationButton.setPanelBounds(new SFMScreenPanelBounds(
                presentationButtonLeft,
                presentationButtonTop,
                Math.max(1, presentationButtonRight - presentationButtonLeft),
                Math.max(1, presentationButtonBottom - presentationButtonTop)));
        presentationButton.setMessage(Component.literal(presentationLabel()));
        int rowTop = presentationButtonBottom;
        for (SFMPanelActionButton option : presentationOptions) {
            option.setPanelBounds(new SFMScreenPanelBounds(
                    presentationButtonLeft,
                    rowTop,
                    Math.max(1, presentationButtonRight - presentationButtonLeft),
                    controlHeight));
            rowTop += controlHeight;
        }
        updatePresentationOptionVisibility();
        if (widgetHost.focusedChild().isEmpty()) {
            widgetHost.focus(disconnectedControlsVisible ? START_ELEMENT : VIEWPORT_ELEMENT);
        }
    }

    private void togglePresentationMenu() {
        presentationMenuOpen = !presentationMenuOpen;
        synchronizePresentationSelection();
        updatePresentationOptionVisibility();
    }

    private void updatePresentationOptionVisibility() {
        for (SFMPanelActionButton option : presentationOptions) {
            option.visible = remoteService != null && presentationMenuOpen;
        }
    }

    private void selectPresentationOption(PresentationAxis axis, int index, String draft) {
        presentationAxis = axis;
        if (axis == PresentationAxis.RENDERER) rendererSelectionIndex = index;
        else transportSelectionIndex = index;
        if (executePanelAction(draft)) {
            presentationMenuOpen = false;
            widgetHost.focus(PRESENTATION_ELEMENT);
        }
        updatePresentationOptionVisibility();
    }

    private boolean presentationOptionKeyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode != GLFW.GLFW_KEY_ESCAPE) return false;
        presentationMenuOpen = false;
        updatePresentationOptionVisibility();
        widgetHost.focus(PRESENTATION_ELEMENT);
        return true;
    }

    private boolean executePanelAction(String draft) {
        if (actionOverride != null) return actionOverride.invoke(draft);
        if (context == null || minecraft == null) return false;
        return SFMPanelActionExecution.execute(context, minecraft, draft, feedback ->
                lastPanelActionFeedback = feedback.getString());
    }

    private Component presentationNarration() {
        String feedback = lastPanelActionFeedback.isBlank() ? "" : ". " + lastPanelActionFeedback;
        return Component.literal(presentationLabel() + feedback);
    }

    private String currentPresentationActionDraft() {
        if (remoteService == null) return "";
        if (presentationAxis == PresentationAxis.RENDERER) {
            List<SFMTerminalRendererOption> options = remoteService.rendererOptions();
            if (rendererSelectionIndex >= 0 && rendererSelectionIndex < options.size()) {
                return "sfm action invoke sfm:terminal/renderer/set "
                        + options.get(rendererSelectionIndex).id().wireId();
            }
        } else {
            List<SFMTerminalTransportOption> options = remoteService.transportOptions();
            if (transportSelectionIndex >= 0 && transportSelectionIndex < options.size()) {
                return "sfm action invoke sfm:terminal/transport/set "
                        + options.get(transportSelectionIndex).id().wireId();
            }
        }
        return "";
    }

    private String presentationLabel() {
        if (remoteService == null) return "Presentation unavailable";
        SFMTerminalPresentationTransitionState state = remoteService.presentationState();
        String value = state.requested().label();
        if (state.active().isEmpty()) {
            value += " (pending)";
        } else if (!state.active().get().equals(state.requested())) {
            value = state.active().get().label() + " -> " + value + " (pending)";
        }
        if (state.failure().isPresent()) value += " !";
        return "Presentation: " + value + (presentationMenuOpen ? " ^" : " v");
    }

    @Override
    public Component title() {
        return Component.literal("SFM Terminal");
    }

    @Override
    public Component narration() {
        return Component.literal((remoteService == null ? "Java-local terminal at " : "Rust/Vox terminal at ")
                + client.workingDirectory());
    }

    @Override
    public Optional<SFMPanelWidgetHost> widgetHost() {
        return Optional.of(widgetHost);
    }

    @Override
    public boolean widgetHostOwnsInput() {
        return true;
    }

    @Override
    public void opened(Minecraft minecraft, SFMScreenPanelBounds bounds, SFMWorkspacePanelContext context) {
        this.minecraft = minecraft;
        this.bounds = bounds;
        this.context = context;
        this.lastLoggedPresentationStreamIdentity = null;
        this.lastLoggedPresentationSequence = Long.MIN_VALUE;
        if (remoteService != null) {
            resized(minecraft, bounds);
            remoteService.requestConnect();
        }
    }

    @Override
    public void resized(Minecraft minecraft, SFMScreenPanelBounds bounds) {
        resizeRemoteViewport(
                bounds,
                Math.max(1, minecraft.font.width("W")),
                Math.max(1, minecraft.font.lineHeight + 2)
        );
    }

    void resizeRemoteViewport(SFMScreenPanelBounds bounds, int cellWidth, int lineHeight) {
        invalidateDisconnectedStartButtonPresentation();
        this.bounds = bounds;
        ViewportGeometry viewport = viewportGeometry(
                bounds,
                cellWidth,
                lineHeight,
                remoteService != null
        );
        applyViewport(viewport);
        layoutPresentationControl(viewport.lineHeight() + 4);
        // Establish valid child rectangles immediately. The next render refines
        // disconnected/presented visibility from the actual retained frame.
        synchronizeTerminalWidgets(remoteService == null || remoteService.isConnected(), viewport);
        if (remoteService != null) {
            SFMScreenPanelBounds logicalViewport = new SFMScreenPanelBounds(
                    viewport.left(), viewport.top(), viewport.width(), viewport.height());
            Optional<SFMWorkspacePanelMetrics> measured = context == null
                    ? Optional.empty()
                    : context.measure(logicalViewport);
            SFMScreenPanelBounds rasterTarget = boundedRasterTarget(measured
                    .map(SFMWorkspacePanelMetrics::physicalPixelBounds)
                    .orElse(logicalViewport));
            viewportLogicalBounds = logicalViewport;
            viewportMetrics = measured;
            automaticSurfaceWidth = rasterTarget.width();
            automaticSurfaceHeight = rasterTarget.height();
            automaticColumns = viewport.columns();
            automaticRows = viewport.rows();
            SFMTerminalTuningSettings.Effective effective = effectiveTuning();
            remoteService.resize(tuning, effective);
            remoteService.requestConnect();
        }
    }

    static SFMScreenPanelBounds rasterTarget(
            SFMWorkspacePanelContext context,
            SFMScreenPanelBounds logicalViewport
    ) {
        SFMScreenPanelBounds measured = context == null
                ? logicalViewport
                : context.measure(logicalViewport)
                        .map(SFMWorkspacePanelMetrics::physicalPixelBounds)
                        .orElse(logicalViewport);
        return boundedRasterTarget(measured);
    }

    static SFMScreenPanelBounds boundedRasterTarget(SFMScreenPanelBounds requested) {
        double shrink = Math.min(
                1.0D,
                Math.min(
                        SFMTerminalRasterLimits.RGBA8_V1_MAX_WIDTH / (double) Math.max(1, requested.width()),
                        SFMTerminalRasterLimits.RGBA8_V1_MAX_HEIGHT / (double) Math.max(1, requested.height())
                )
        );
        return new SFMScreenPanelBounds(
                requested.x(),
                requested.y(),
                Math.max(1, (int) Math.floor(requested.width() * shrink)),
                Math.max(1, (int) Math.floor(requested.height() * shrink))
        );
    }

    static ViewportGeometry viewportGeometry(
            SFMScreenPanelBounds bounds,
            int cellWidth,
            int lineHeight,
            boolean remote
    ) {
        int boundedCellWidth = Math.max(1, cellWidth);
        int boundedLineHeight = Math.max(1, lineHeight);
        int left = bounds.x() + CONTENT_HORIZONTAL_PADDING;
        int width = Math.max(1, bounds.width() - CONTENT_HORIZONTAL_PADDING * 2);
        int inputY = bounds.y() + bounds.height() - boundedLineHeight - 8;
        int contentBottom = remote
                ? bounds.y() + bounds.height() - CONTENT_BOTTOM_PADDING
                : inputY;
        int top = bounds.y() + boundedLineHeight + TITLE_CONTENT_GAP;
        int height = Math.max(1, contentBottom - top - CONTENT_BOTTOM_PADDING);
        return new ViewportGeometry(
                left,
                top,
                width,
                height,
                contentBottom,
                inputY,
                boundedLineHeight,
                Math.max(1, width / boundedCellWidth),
                Math.max(1, height / boundedLineHeight)
        );
    }

    private void applyViewport(ViewportGeometry viewport) {
        renderLeft = viewport.left();
        renderTop = viewport.top();
        renderWidth = viewport.width();
        renderHeight = viewport.height();
    }

    @Override
    public void tick() {
        if (remoteService == null) return;
        refreshPresentationOptions();
        remoteService.requestConnect();
        Optional<SFMTerminalTuningRejection> rejection = remoteService.tuningFailure();
        if (tuningPending && rejection.isPresent()) {
            lastTuningRejection = rejection.get();
            tuning = acceptedTuning;
            tuningPending = false;
            applyTuning();
        } else if (tuningPending && !remoteService.tuningPending()) {
            acceptedTuning = tuning;
            tuningPending = false;
            lastTuningRejection = null;
        }
    }

    @Override
    public void closed() {
        invalidateDisconnectedStartButtonPresentation();
        if (minecraft != null) {
            pngRenderer.close(minecraft);
            rgbaRenderer.close(minecraft);
        }
        if (remoteService != null) remoteService.close();
        pendingPaste = null;
        minecraft = null;
        context = null;
        lastLoggedPresentationStreamIdentity = null;
        lastLoggedPresentationSequence = Long.MIN_VALUE;
    }

    @Override
    public void render(PoseStack poseStack, Minecraft minecraft, SFMScreenPanelBounds bounds,
                       int mouseX, int mouseY, float partialTick, boolean focused) {
        GuiComponent.fill(poseStack, bounds.x(), bounds.y(), bounds.x() + bounds.width(), bounds.y() + bounds.height(), PANEL);
        ViewportGeometry viewport = viewportGeometry(
                bounds,
                minecraft.font.width("W"),
                minecraft.font.lineHeight + 2,
                remoteService != null
        );
        applyViewport(viewport);
        int left = viewport.left();
        int width = viewport.width();
        int lineHeight = viewport.lineHeight();
        int inputY = viewport.inputY();
        int contentBottom = viewport.contentBottom();
        int visibleLines = Math.max(0, (contentBottom - bounds.y() - 22) / lineHeight);
        scrollback.setViewportLineCount(Math.max(1, visibleLines));
        int contentTop = viewport.top();
        SFMFontUtils.draw(poseStack, minecraft.font, title().copy().withStyle(ChatFormatting.BOLD), left,
                bounds.y() + 8, TEXT, false);
        if (remoteService != null) {
            // The old disconnected controls stop existing before either a new
            // frame or a retained texture can become the current presentation.
            invalidateDisconnectedStartButtonPresentation();
            Optional<SFMTerminalFrame> frame = remoteService.latestFrame();
            frame.ifPresent(accepted -> {
                lastAcceptedFrame = accepted;
            });
            boolean presented = false;
            if (frame.isPresent() || remoteService.canPresentRetainedFrame()) {
                boolean png = frame.map(SFMTerminalFrame::png)
                        .orElseGet(() -> remoteService.activeTransportId()
                                .map("full-png"::equals).orElse(true));
                presented = png
                        ? pngRenderer.render(poseStack, minecraft, left, contentTop, width,
                        renderHeight, localToPhysicalScaleX(), localToPhysicalScaleY(), frame)
                        : rgbaRenderer.render(poseStack, minecraft, left, contentTop, width,
                        renderHeight, localToPhysicalScaleX(), localToPhysicalScaleY(), frame);
            }
            if (presented) {
                renderRemoteSelection(poseStack);
                if (frame.isPresent() && isNewPresentation(
                        lastLoggedPresentationStreamIdentity,
                        lastLoggedPresentationSequence,
                        frame.get())) {
                    lastLoggedPresentationStreamIdentity = frame.get().streamIdentity();
                    lastLoggedPresentationSequence = frame.get().sequence();
                    logPresentationTiming(frame.get(), pngRenderer.telemetry());
                }
                renderFocusHint(poseStack, minecraft, left, width, contentBottom);
                synchronizeTerminalWidgets(true, viewport);
                return;
            }
            renderDisconnected(poseStack, minecraft, left, width, contentTop, contentBottom);
            synchronizeTerminalWidgets(false, viewport);
            return;
        }
        int y = contentTop;
        for (SFMTerminalLine terminalLine : scrollback.visibleLineEntries()) {
            if (y >= contentBottom) break;
            String line = terminalLine.text();
            int color = line.startsWith("error:") ? ERROR : terminalLine.color();
            String remaining = line;
            do {
                String rendered = minecraft.font.plainSubstrByWidth(remaining, width);
                if (rendered.isEmpty()) rendered = remaining.substring(0, 1);
                SFMFontUtils.draw(poseStack, minecraft.font, rendered, left, y, color, false);
                y += lineHeight;
                remaining = remaining.substring(rendered.length());
            } while (!remaining.isEmpty() && y < contentBottom);
        }
        if (remoteService == null) {
            renderInput(poseStack, minecraft, left, width, inputY, focused);
        }
        renderFocusHint(poseStack, minecraft, left, width, contentBottom);
        synchronizeTerminalWidgets(true, viewport);
    }

    private void logPresentationTiming(
            SFMTerminalFrame frame,
            SFMTerminalPngTelemetry.Snapshot telemetry) {
        SFMTerminalFrameMetadata metadata = frame.metadata();
        String message = "SFM_TERMINAL_PRESENTATION_TIMING correlation_id={} stream_identity={} request_sequence={} "
                + "frame_sequence={} java_render_calls={} java_render_successes={} "
                + "java_render_total_us={} java_render_max_us={} java_upload_attempts={} "
                + "java_upload_failures={} java_upload_total_us={} java_upload_max_us={} "
                + "java_png_decode_attempts={} java_png_decode_total_us={} java_png_decode_max_us={} "
                + "java_decoded_image_allocations={} java_decoded_image_closes={} "
                + "java_encoded_buffer_allocations={} java_encoded_buffer_reuses={} "
                + "java_encoded_buffer_replacements={} java_encoded_buffer_closes={} "
                + "java_encoded_buffer_capacity={} java_encoded_buffer_capacity_max={} "
                + "java_texture_allocations={} java_texture_allocation_total_us={} "
                + "java_texture_allocation_max_us={} java_texture_reuses={} "
                + "java_texture_replacements={} java_texture_closes={} java_texture_registrations={} "
                + "java_texture_registration_total_us={} java_texture_registration_max_us={} "
                + "java_texture_uploads={} java_texture_upload_total_us={} java_texture_upload_max_us={} "
                + "java_stale_frames={} java_dropped_frames={} java_coalesced_frames={} "
                + "java_frames_presented={} java_sequence_presented={} payload_bytes={} "
                + "rust_total_us={} rust_pty_drain_us={} rust_snapshot_us={} rust_font_load_us={} "
                + "rust_raster_us={} rust_encode_us={} renderer_id={} transport_id={} "
                + "panel_width={} panel_height={} "
                + "cell_width={} cell_height={} font_pixel_size={}";
        Object[] fields = {
                metadata.correlationId(), frame.streamIdentity(), metadata.requestSequence(), frame.sequence(),
                telemetry.renderCalls(), telemetry.renderSuccesses(), micros(telemetry.renderNanosTotal()),
                micros(telemetry.renderNanosMax()), telemetry.uploadAttempts(), telemetry.uploadFailures(),
                micros(telemetry.uploadNanosTotal()), micros(telemetry.uploadNanosMax()),
                telemetry.pngDecodeAttempts(), micros(telemetry.pngDecodeNanosTotal()),
                micros(telemetry.pngDecodeNanosMax()), telemetry.decodedImageAllocations(),
                telemetry.decodedImageCloses(), telemetry.encodedBufferAllocations(),
                telemetry.encodedBufferReuses(), telemetry.encodedBufferReplacements(),
                telemetry.encodedBufferCloses(), telemetry.encodedBufferCapacity(),
                telemetry.encodedBufferCapacityMax(), telemetry.dynamicTextureAllocations(),
                micros(telemetry.dynamicTextureAllocationNanosTotal()),
                micros(telemetry.dynamicTextureAllocationNanosMax()), telemetry.dynamicTextureReuses(),
                telemetry.dynamicTextureReplacements(), telemetry.dynamicTextureCloses(),
                telemetry.dynamicTextureRegistrations(),
                micros(telemetry.dynamicTextureRegistrationNanosTotal()),
                micros(telemetry.dynamicTextureRegistrationNanosMax()), telemetry.dynamicTextureUploads(),
                micros(telemetry.dynamicTextureUploadNanosTotal()),
                micros(telemetry.dynamicTextureUploadNanosMax()), telemetry.staleFrames(),
                telemetry.droppedFrames(), telemetry.coalescedFrames(), telemetry.framesPresented(),
                telemetry.sequencePresented(), frame.payload().length, metadata.rustTotalUs(),
                metadata.ptyDrainUs(), metadata.snapshotUs(), metadata.fontLoadUs(),
                metadata.rasterUs(), metadata.encodeUs(),
                metadata.rendererId(), metadata.transportId(), metadata.panelWidth(), metadata.panelHeight(),
                metadata.cellWidth(), metadata.cellHeight(), metadata.fontPixelSize()
        };
        SFM.LOGGER.info(message, fields);
    }

    static boolean isNewPresentation(
            String previousStreamIdentity,
            long previousSequence,
            SFMTerminalFrame frame) {
        return !Objects.equals(previousStreamIdentity, frame.streamIdentity())
                || previousSequence != frame.sequence();
    }

    private static long micros(long nanos) {
        return nanos <= 0 ? 0 : nanos / 1_000L;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (focusSequence.escape(System.nanoTime()) == SFMTerminalFocusSequence.Decision.HOST_ESCAPE) {
                // The first two presses belong to the PTY. The third is not
                // forwarded and deliberately falls through to the workspace's
                // constrained close palette instead of closing a panel directly.
                return false;
            }
            if (remoteService != null) remoteService.sendKey(keyCode, modifiers, true, false);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_TAB) {
            SFMTerminalFocusSequence.Decision decision = focusSequence.tab(System.nanoTime());
            if (decision == SFMTerminalFocusSequence.Decision.JAVA_FOCUS) {
                presentationMenuOpen = false;
                synchronizePresentationSelection();
                updatePresentationOptionVisibility();
                return false;
            }
            if (remoteService != null) remoteService.sendKey(keyCode, modifiers, true, false);
            else input += "\t";
            return true;
        }
        if (remoteService != null && isCopyShortcut(keyCode, modifiers)) {
            suppressCopyRelease = true;
            if (!requestCopy(false)) {
                suppressCopyRelease = false;
                return false;
            }
            return true;
        }
        if (remoteService != null && isPasteShortcut(keyCode, modifiers)) {
            suppressPasteRelease = true;
            String clipboard = minecraft == null ? "" : minecraft.keyboardHandler.getClipboard();
            if (!clipboard.isEmpty() && !requestGuardedPaste(clipboard)) {
                suppressPasteRelease = false;
                return false;
            }
            return true;
        }
        focusSequence.reset();
        if (remoteService != null) {
            if (!isPrintableKey(keyCode)
                    || (modifiers & (GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_ALT | GLFW.GLFW_MOD_SUPER)) != 0) {
                remoteService.sendKey(keyCode, modifiers, true, false);
            }
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_PAGE_UP) {
            scrollback.pageUp();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_PAGE_DOWN) {
            scrollback.pageDown();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_UP) {
            scrollback.scrollOlder(1);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_DOWN) {
            scrollback.scrollNewer(1);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_HOME) {
            scrollback.scrollToTop();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_END) {
            scrollback.followOutput();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
            if (!input.isEmpty()) input = input.substring(0, input.length() - 1);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            submitInput();
            return true;
        }
        return false;
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (remoteService == null) return false;
        if (suppressCopyRelease && keyCode == GLFW.GLFW_KEY_C) {
            suppressCopyRelease = false;
            return true;
        }
        if (suppressPasteRelease && keyCode == GLFW.GLFW_KEY_V) {
            suppressPasteRelease = false;
            return true;
        }
        if (!isPrintableKey(keyCode)
                || (modifiers & (GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_ALT | GLFW.GLFW_MOD_SUPER)) != 0) {
            remoteService.sendKey(keyCode, modifiers, false, false);
        }
        return true;
    }

    @Override
    public boolean charTyped(char character, int modifiers) {
        if (remoteService != null) {
            if (character >= 0x20 && character != 0x7F) {
                remoteService.sendText(String.valueOf(character));
            }
            return true;
        }
        if (character >= 0x20 && character != 0x7F && input.length() < 512) {
            input += character;
            return true;
        }
        return false;
    }

    private void submitInput() {
        String command = input.trim();
        if (command.isEmpty()) return;
        scrollback.append("> " + command);
        SFMTerminalResponse response = client.execute(command);
        if (response.success()) {
            scrollback.appendStyledAll(response.styledLines());
        } else {
            scrollback.appendStyledAll(response.styledLines().stream()
                    .map(line -> new SFMTerminalLine("error: " + line.text(), ERROR))
                    .toList());
        }
        input = "";
    }

    /** Deterministic hook for puppet proofs; normal users use keyboard input. */
    public void executeForAutomation(String command) {
        if (remoteService != null) {
            SFMTerminalResponse response = client.execute(command == null ? "" : command);
            if (!response.success()) {
                throw new IllegalStateException("Rust terminal automation command failed: "
                        + String.join("; ", response.lines()));
            }
            return;
        }
        input = command == null ? "" : command;
        submitInput();
    }

    /** Deterministic hook for puppet proofs of the Vox cancellation RPC. */
    public void cancelForAutomation() {
        if (remoteService == null || !remoteService.cancel()) {
            throw new IllegalStateException("Rust terminal cancellation failed");
        }
    }

    /** Clears the current Vox transport so the next witness establishes a fresh session. */
    public void reconnectForAutomation() {
        if (remoteService == null) {
            throw new IllegalStateException("Java-local terminal has no Rust connection to reconnect");
        }
        remoteService.reconnect();
    }

    /** Deterministic hook for puppet proofs of the Rust logical resize path. */
    public void resizeForAutomation(int columns, int rows) {
        if (remoteService == null || !remoteService.resize(columns, rows)) {
            throw new IllegalStateException("Rust terminal resize failed");
        }
    }

    /** Sends a key directly to Rust for child-TUI proofs without consuming SFM focus gestures. */
    public void pressKeyForAutomation(int keyCode, int modifiers) {
        if (remoteService == null
                || !remoteService.sendKey(keyCode, modifiers, true, false)
                || !remoteService.sendKey(keyCode, modifiers, false, false)) {
            throw new IllegalStateException("Rust terminal direct key delivery failed");
        }
    }

    /** Deterministic hook for puppet proofs of the real clipboard paste shortcut. */
    public void pasteForAutomation(String text) {
        if (remoteService == null) {
            input += text == null ? "" : text;
            return;
        }
        if (minecraft == null) {
            if (!requestGuardedPaste(text == null ? "" : text)) {
                throw new IllegalStateException("Rust terminal rejected automation paste");
            }
            return;
        }
        String previous = minecraft == null ? "" : minecraft.keyboardHandler.getClipboard();
        try {
            minecraft.keyboardHandler.setClipboard(text == null ? "" : text);
            if (!keyPressed(GLFW.GLFW_KEY_V, 0, GLFW.GLFW_MOD_CONTROL)) {
                throw new IllegalStateException("Rust terminal rejected Ctrl+V paste");
            }
            keyReleased(GLFW.GLFW_KEY_V, 0, GLFW.GLFW_MOD_CONTROL);
        } finally {
            if (minecraft != null) minecraft.keyboardHandler.setClipboard(previous);
        }
    }

    /** Returns the Rust-owned visible terminal text for deterministic puppet assertions. */
    public String contentForAutomation() {
        if (remoteService != null) return remoteService.contentForAutomation();
        return String.join("\n", scrollback.lines());
    }

    public List<String> transcript() {
        return scrollback.lines();
    }

    public List<String> visibleTranscript() {
        return scrollback.visibleLines();
    }

    public SFMTerminalScrollback scrollback() {
        return scrollback;
    }

    /** Returns bounded timing/counter evidence for the Rust PNG presentation path. */
    public SFMTerminalPngTelemetry.Snapshot pngTelemetry() {
        return pngRenderer.telemetry();
    }

    /** Validates and returns machine-readable Vox push evidence for puppets. */
    public String assertPushEvidenceForAutomation(boolean reconnectExpected) {
        if (remoteService == null) {
            throw new IllegalStateException("Terminal panel is not backed by a remote service");
        }
        return remoteService.assertPushEvidenceForAutomation(reconnectExpected);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (mouseX < bounds.x() || mouseX >= bounds.x() + bounds.width()
                || mouseY < bounds.y() || mouseY >= bounds.y() + bounds.height()) return false;
        if (remoteService != null) {
            if (remoteService.sendMouse(logicalX(mouseX), logicalY(mouseY), pressedMouseButtons, 0, false,
                    false, 0, (int) Math.round(delta))) terminalMouseDispatchCount++;
        } else if (delta > 0) scrollback.scrollOlder(Math.max(1, (int) Math.ceil(delta)));
        else if (delta < 0) scrollback.scrollNewer(Math.max(1, (int) Math.ceil(-delta)));
        return delta != 0;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!containsTerminalPoint(mouseX, mouseY)) return false;
        if (remoteService == null) return false;
        // A viewport click is terminal interaction even if the selector was
        // previously keyboard-focused. Restore terminal focus before the PTY
        // receives the click so subsequent keyboard input cannot be consumed
        // by a stale presentation menu.
        focusTerminalInput();
        int mask = mouseMask(button);
        pressedMouseButtons |= mask;
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            Minecraft requestMinecraft = minecraft;
            if (remoteService.sendMouse(
                    logicalX(mouseX),
                    logicalY(mouseY),
                    pressedMouseButtons,
                    button,
                    true,
                    false,
                    0,
                    0,
                    disposition -> runOnMinecraftThread(requestMinecraft, () -> {
                        if (disposition != SFMTerminalInputDisposition.FORWARDED) requestCopy(true);
                    }))) terminalMouseDispatchCount++;
        } else {
            if (remoteService.sendMouse(logicalX(mouseX), logicalY(mouseY), pressedMouseButtons, button, true,
                    false, 0, 0)) terminalMouseDispatchCount++;
        }
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (remoteService == null) return false;
        int mask = mouseMask(button);
        boolean wasPressed = (pressedMouseButtons & mask) != 0;
        pressedMouseButtons &= ~mouseMask(button);
        if (!wasPressed) return false;
        if (remoteService.sendMouse(logicalX(mouseX), logicalY(mouseY), pressedMouseButtons, button, false,
                false, 0, 0)) terminalMouseDispatchCount++;
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (remoteService == null || pressedMouseButtons == 0) return false;
        if (remoteService.sendMouse(logicalX(mouseX), logicalY(mouseY), pressedMouseButtons,
                button, true, true, 0, 0)) terminalMouseDispatchCount++;
        return true;
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        if (remoteService != null && pressedMouseButtons != 0) {
            if (remoteService.sendMouse(logicalX(mouseX), logicalY(mouseY), pressedMouseButtons,
                    0, true, true, 0, 0)) terminalMouseDispatchCount++;
        }
    }

    public SFMTerminalClient client() {
        return client;
    }

    /** Panel-local action surface; callers must resolve this exact focused panel first. */
    public List<SFMTerminalRendererOption> rendererOptions() {
        return remoteService == null ? List.of() : remoteService.rendererOptions();
    }

    public List<SFMTerminalTransportOption> transportOptions() {
        return remoteService == null ? List.of() : remoteService.transportOptions();
    }

    public SFMTerminalPresentationChangeResult requestRenderer(String rendererId) {
        if (remoteService == null) {
            return SFMTerminalPresentationChangeResult.rejected(
                    "Focused panel is not a Rust terminal");
        }
        return remoteService.requestRenderer(rendererId);
    }

    /** Panel-local action surface; callers must resolve this exact focused panel first. */
    public SFMTerminalPresentationChangeResult requestTransport(String transportId) {
        if (remoteService == null) {
            return SFMTerminalPresentationChangeResult.rejected(
                    "Focused panel is not a Rust terminal");
        }
        return remoteService.requestTransport(transportId);
    }

    public boolean isRustBacked() {
        return remoteService != null;
    }

    /** Immutable diagnostics for UI controls, automation, and live evidence. */
    public SFMTerminalPropertiesSnapshot propertiesSnapshot() {
        SFMTerminalTuningSettings.Effective effective = effectiveTuning();
        Optional<SFMTerminalPropertiesSnapshot.AcceptedFrame> acceptedFrame =
                Optional.ofNullable(lastAcceptedFrame).map(SFMTerminalPropertiesSnapshot::fromFrame);
        Optional<SFMScreenPanelBounds> javaDrawLogical = acceptedFrame.map(frame -> SFMTerminalImageLayout.fitPhysical(
                viewportLogicalBounds.x(),
                viewportLogicalBounds.y(),
                viewportLogicalBounds.width(),
                viewportLogicalBounds.height(),
                frame.nativeWidth(),
                frame.nativeHeight(),
                localToPhysicalScaleX(),
                localToPhysicalScaleY()
        ).bounds());
        Optional<SFMScreenPanelBounds> javaDrawPhysical = context == null
                ? Optional.empty()
                : javaDrawLogical.flatMap(context::measure)
                        .map(SFMWorkspacePanelMetrics::physicalPixelBounds);
        Optional<SFMWorkspacePanelMetrics> panelMetrics = context == null
                ? Optional.empty()
                : context.measure(bounds);
        Optional<SFMTerminalTuningRejection> rejection = Optional.ofNullable(lastTuningRejection);
        if (rejection.isEmpty() && remoteService != null) rejection = remoteService.tuningFailure();
        return new SFMTerminalPropertiesSnapshot(
                tuning,
                acceptedTuning,
                effective,
                new SFMTerminalTuningSettings.Effective(
                        automaticSurfaceWidth,
                        automaticSurfaceHeight,
                        0,
                        automaticColumns,
                        automaticRows
                ),
                tuningPending,
                bounds,
                viewportLogicalBounds,
                panelMetrics,
                viewportMetrics,
                minecraft == null ? 0 : minecraft.options.guiScale().get(),
                minecraft == null ? 0 : (int) Math.round(minecraft.getWindow().getGuiScale()),
                javaDrawLogical,
                javaDrawPhysical,
                acceptedFrame,
                remoteService == null ? Optional.empty() : remoteService.presentationDiagnostics(),
                remoteService == null ? "" : remoteService.requestedRendererId(),
                remoteService == null ? "" : remoteService.activeRendererId().orElse(""),
                remoteService == null ? "" : remoteService.requestedTransportId(),
                remoteService == null ? "" : remoteService.activeTransportId().orElse(""),
                rejection
        );
    }

    public SFMTerminalTuningChangeResult requestTuning(
            SFMTerminalTuningOperation operation,
            int first,
            int second
    ) {
        Objects.requireNonNull(operation);
        if (remoteService == null) {
            return SFMTerminalTuningChangeResult.rejected(
                    "Focused panel is not a Rust terminal");
        }
        SFMTerminalTuningSettings candidate;
        try {
            candidate = operation.apply(
                    tuning,
                    effectiveTuning(),
                    acceptedFontPixelSize(),
                    first,
                    second
            );
        } catch (ArithmeticException | IllegalArgumentException error) {
            lastTuningRejection = SFMTerminalTuningRejection.localInvalid(
                    error.getMessage() == null
                            ? "Terminal tuning value is out of range"
                            : error.getMessage(),
                    describeOperation(operation, first, second));
            return SFMTerminalTuningChangeResult.rejected(lastTuningRejection);
        }
        SFMTerminalTuningSettings previous = tuning;
        tuning = candidate;
        tuningPending = true;
        lastTuningRejection = null;
        if (!applyTuning()) {
            tuning = previous;
            tuningPending = false;
            lastTuningRejection = remoteService.tuningFailure().orElseGet(() ->
                    new SFMTerminalTuningRejection(
                            new SFMTerminalError(
                                    SFMTerminalErrorCode.INTERNAL,
                                    "Terminal resize request could not be queued",
                                    true,
                                    0),
                            describe(candidate)));
            return SFMTerminalTuningChangeResult.rejected(lastTuningRejection);
        }
        return SFMTerminalTuningChangeResult.accepted("Terminal tuning requested: " + describe(candidate));
    }

    private boolean applyTuning() {
        if (remoteService == null) return false;
        SFMTerminalTuningSettings.Effective effective = effectiveTuning();
        return remoteService.resize(tuning, effective);
    }

    private SFMTerminalTuningSettings.Effective effectiveTuning() {
        return tuning.resolve(
                automaticSurfaceWidth,
                automaticSurfaceHeight,
                automaticColumns,
                automaticRows
        );
    }

    private int acceptedFontPixelSize() {
        return Optional.ofNullable(lastAcceptedFrame)
                .map(SFMTerminalFrame::metadata)
                .map(SFMTerminalFrameMetadata::fontPixelSize)
                .filter(value -> value > 0)
                .orElse(SFMTerminalTuningSettings.MIN_FONT_PIXEL_SIZE);
    }

    private double localToPhysicalScaleX() {
        return viewportMetrics.map(SFMWorkspacePanelMetrics::localToPhysicalScaleX).orElse(1.0D);
    }

    private double localToPhysicalScaleY() {
        return viewportMetrics.map(SFMWorkspacePanelMetrics::localToPhysicalScaleY).orElse(1.0D);
    }

    private static String describe(SFMTerminalTuningSettings settings) {
        return "surface=" + axis(settings.surfaceWidth()) + "x" + axis(settings.surfaceHeight())
                + ", font=" + axis(settings.fontPixelSize())
                + ", cells=" + axis(settings.columns()) + "x" + axis(settings.rows());
    }

    private static String describeOperation(
            SFMTerminalTuningOperation operation,
            int first,
            int second
    ) {
        return operation + " first=" + first + " second=" + second;
    }

    private static String axis(int value) {
        return value == 0 ? "auto" : Integer.toString(value);
    }

    private void renderRemoteSelection(PoseStack poseStack) {
        for (SFMTerminalSelectionLayout.LogicalHighlight highlight : selectionHighlightsForAutomation()) {
            SFMScreenRenderUtils.renderHighlight(
                    poseStack,
                    highlight.startX(),
                    highlight.startY(),
                    highlight.endX(),
                    highlight.endY());
        }
    }

    List<SFMTerminalSelectionLayout.LogicalHighlight> selectionHighlightsForAutomation() {
        if (remoteService == null || lastAcceptedFrame == null) return List.of();
        Optional<SFMTerminalSelection> selected = remoteService.selection();
        if (selected.isEmpty()) return List.of();
        SFMTerminalFrameMetadata metadata = lastAcceptedFrame.metadata();
        if (metadata.logicalColumns() < 1 || metadata.logicalRows() < 1
                || metadata.cellWidth() < 1 || metadata.cellHeight() < 1
                || metadata.panelWidth() < 1 || metadata.panelHeight() < 1) return List.of();
        SFMTerminalImageLayout layout = terminalImageLayout();
        SFMTerminalSelection selection = selected.get();
        SFMTerminalSelectionLayout.NativeGrid grid;
        try {
            grid = new SFMTerminalSelectionLayout.NativeGrid(
                    metadata.logicalColumns(),
                    metadata.logicalRows(),
                    metadata.cellWidth(),
                    metadata.cellHeight(),
                    metadata.panelWidth(),
                    metadata.panelHeight());
        } catch (IllegalArgumentException ignored) {
            return List.of();
        }
        return SFMTerminalSelectionLayout.logicalHighlights(
                new SFMTerminalSelectionLayout.Cell(selection.anchorX(), selection.anchorY()),
                new SFMTerminalSelectionLayout.Cell(selection.focusX(), selection.focusY()),
                grid,
                layout,
                viewportLogicalBounds);
    }

    record TerminalCellPoint(double x, double y) {
    }

    TerminalCellPoint terminalCellCenterForAutomation(int column, int row) {
        if (remoteService == null || lastAcceptedFrame == null) {
            throw new IllegalStateException("Terminal has no accepted remote frame");
        }
        SFMTerminalFrameMetadata metadata = lastAcceptedFrame.metadata();
        int columns = Math.max(1, metadata.logicalColumns());
        int rows = Math.max(1, metadata.logicalRows());
        int boundedColumn = Math.max(0, Math.min(columns - 1, column));
        int boundedRow = Math.max(0, Math.min(rows - 1, row));
        SFMTerminalImageLayout layout = terminalImageLayout();
        return new TerminalCellPoint(
                layout.x() + (boundedColumn + 0.5D) * layout.width() / columns,
                layout.y() + (boundedRow + 0.5D) * layout.height() / rows);
    }

    Optional<SFMTerminalSelection> selectionForAutomation() {
        return remoteService == null ? Optional.empty() : remoteService.selection();
    }

    Optional<SFMTerminalFrame> acceptedFrameForAutomation() {
        return Optional.ofNullable(lastAcceptedFrame);
    }

    SFMTerminalPngTelemetry.Snapshot pngTelemetryForAutomation() {
        return pngRenderer.telemetry();
    }

    SFMTerminalRgbaRenderer.Snapshot rgbaTelemetryForAutomation() {
        return rgbaRenderer.telemetry();
    }

    private boolean requestCopy(boolean pasteClipboardWhenEmpty) {
        if (remoteService == null) return false;
        Minecraft requestMinecraft = minecraft;
        return remoteService.copySelection(result -> runOnMinecraftThread(requestMinecraft, () -> {
            if (result.disposition() == SFMTerminalCopyResult.Disposition.COPIED) {
                if (requestMinecraft != null) requestMinecraft.keyboardHandler.setClipboard(result.text());
                focusTerminalInput();
                return;
            }
            if (pasteClipboardWhenEmpty) {
                String clipboard = requestMinecraft == null
                        ? ""
                        : requestMinecraft.keyboardHandler.getClipboard();
                if (!clipboard.isEmpty()) requestGuardedPaste(clipboard);
            } else {
                remoteService.sendKey(GLFW.GLFW_KEY_C, GLFW.GLFW_MOD_CONTROL, true, false);
                remoteService.sendKey(GLFW.GLFW_KEY_C, GLFW.GLFW_MOD_CONTROL, false, false);
            }
            focusTerminalInput();
        }));
    }

    private boolean requestGuardedPaste(String text) {
        if (remoteService == null || text == null || text.isEmpty()) return true;
        Minecraft requestMinecraft = minecraft;
        return remoteService.pasteWithGuard(text, result -> runOnMinecraftThread(requestMinecraft, () -> {
            if (result.disposition() == SFMTerminalPasteResult.Disposition.PASTED) {
                pendingPaste = null;
                focusTerminalInput();
                return;
            }
            PendingPaste pending = new PendingPaste(
                    text,
                    result.preview(),
                    result.contentId(),
                    remoteService.interactionEpoch());
            pendingPaste = pending;
            if (requestMinecraft != null) {
                SFMTerminalPasteConfirmationScreen.open(
                        result.preview(),
                        approved -> resolvePendingPaste(pending.contentId(), approved));
            }
        }));
    }

    private void resolvePendingPaste(String expectedContentId, boolean approved) {
        PendingPaste pending = pendingPaste;
        if (pending == null || !pending.contentId().equals(expectedContentId)) return;
        pendingPaste = null;
        focusTerminalInput();
        if (!approved || remoteService == null
                || !remoteService.isConnected()
                || remoteService.interactionEpoch() != pending.interactionEpoch()) return;
        Minecraft requestMinecraft = minecraft;
        remoteService.pasteWithoutGuard(
                pending.text(),
                pending.contentId(),
                ignored -> runOnMinecraftThread(requestMinecraft, this::focusTerminalInput));
    }

    Optional<String> pendingPasteContentIdForAutomation() {
        return Optional.ofNullable(pendingPaste).map(PendingPaste::contentId);
    }

    Optional<String> pendingPastePreviewForAutomation() {
        return Optional.ofNullable(pendingPaste).map(PendingPaste::preview);
    }

    void resolvePendingPasteForAutomation(String contentId, boolean approved) {
        resolvePendingPaste(contentId, approved);
    }

    private void runOnMinecraftThread(Minecraft requestMinecraft, Runnable operation) {
        if (requestMinecraft == null) {
            operation.run();
            return;
        }
        if (minecraft != requestMinecraft) return;
        requestMinecraft.execute(() -> {
            if (minecraft == requestMinecraft) operation.run();
        });
    }

    private SFMTerminalImageLayout terminalImageLayout() {
        if (lastAcceptedFrame == null) {
            return new SFMTerminalImageLayout(renderLeft, renderTop, renderWidth, renderHeight);
        }
        SFMTerminalFrameMetadata metadata = lastAcceptedFrame.metadata();
        return SFMTerminalImageLayout.fitPhysical(
                renderLeft,
                renderTop,
                renderWidth,
                renderHeight,
                Math.max(1, metadata.panelWidth()),
                Math.max(1, metadata.panelHeight()),
                localToPhysicalScaleX(),
                localToPhysicalScaleY());
    }

    private boolean containsTerminalPoint(double mouseX, double mouseY) {
        SFMTerminalImageLayout layout = terminalImageLayout();
        return mouseX >= layout.x() && mouseX < layout.x() + layout.width()
                && mouseY >= layout.y() && mouseY < layout.y() + layout.height();
    }

    private int logicalX(double mouseX) {
        SFMTerminalImageLayout layout = terminalImageLayout();
        int columns = lastAcceptedFrame == null
                ? remoteService.logicalWidth()
                : lastAcceptedFrame.metadata().logicalColumns();
        int cell = (int) Math.floor((mouseX - layout.x()) * Math.max(1, columns)
                / Math.max(1, layout.width()));
        return Math.max(0, Math.min(Math.max(1, columns) - 1, cell));
    }

    private int logicalY(double mouseY) {
        SFMTerminalImageLayout layout = terminalImageLayout();
        int rows = lastAcceptedFrame == null
                ? remoteService.logicalHeight()
                : lastAcceptedFrame.metadata().logicalRows();
        int cell = (int) Math.floor((mouseY - layout.y()) * Math.max(1, rows)
                / Math.max(1, layout.height()));
        return Math.max(0, Math.min(Math.max(1, rows) - 1, cell));
    }

    private static int mouseMask(int button) {
        return button >= 0 && button < 8 ? 1 << button : 0;
    }

    private static boolean isPrintableKey(int keyCode) {
        return keyCode == GLFW.GLFW_KEY_SPACE
                || keyCode >= GLFW.GLFW_KEY_APOSTROPHE && keyCode <= GLFW.GLFW_KEY_GRAVE_ACCENT
                || keyCode >= GLFW.GLFW_KEY_0 && keyCode <= GLFW.GLFW_KEY_9
                || keyCode >= GLFW.GLFW_KEY_A && keyCode <= GLFW.GLFW_KEY_Z;
    }

    private static boolean isPasteShortcut(int keyCode, int modifiers) {
        return keyCode == GLFW.GLFW_KEY_V
                && (modifiers & GLFW.GLFW_MOD_CONTROL) != 0
                && (modifiers & (GLFW.GLFW_MOD_ALT | GLFW.GLFW_MOD_SUPER)) == 0;
    }

    private static boolean isCopyShortcut(int keyCode, int modifiers) {
        return keyCode == GLFW.GLFW_KEY_C
                && (modifiers & GLFW.GLFW_MOD_CONTROL) != 0
                && (modifiers & (GLFW.GLFW_MOD_ALT | GLFW.GLFW_MOD_SUPER)) == 0;
    }

    private void focusTerminalInput() {
        presentationMenuOpen = false;
        updatePresentationOptionVisibility();
        focusSequence.reset();
        widgetHost.focus(VIEWPORT_ELEMENT);
    }

    private void renderInput(PoseStack poseStack, Minecraft minecraft, int left, int width, int inputY,
                             boolean focused) {
        GuiComponent.fill(poseStack, bounds.x() + 4, inputY - 4, bounds.x() + bounds.width() - 4,
                bounds.y() + bounds.height() - 4, INPUT);
        String prompt = "> " + input + (focused ? "_" : "");
        SFMFontUtils.draw(poseStack, minecraft.font,
                minecraft.font.plainSubstrByWidth(prompt, width), left, inputY, MUTED, false);
    }

    private void renderDisconnected(
            PoseStack poseStack,
            Minecraft minecraft,
            int left,
            int width,
            int contentTop,
            int contentBottom
    ) {
        String state = remoteService.isConnected()
                ? "Rust terminal connected; waiting for its first frame"
                : remoteService.isConnecting()
                ? "Connecting to the Rust terminal server..."
                : connectionStatus;
        SFMFontUtils.draw(poseStack, minecraft.font, state, left, contentTop + 8, TEXT, false);
        int nextY = contentTop + 8 + minecraft.font.lineHeight + 8;
        Optional<String> failure = remoteService.failureMessage();
        if (failure.isPresent()) {
            String message = minecraft.font.plainSubstrByWidth(failure.get(), width);
            SFMFontUtils.draw(poseStack, minecraft.font, message, left, nextY, ERROR, false);
            nextY += minecraft.font.lineHeight + 8;
        } else {
            SFMFontUtils.draw(poseStack, minecraft.font,
                    "Start teamy-terminal or retry the configured endpoint.", left, nextY, MUTED, false);
            nextY += minecraft.font.lineHeight + 8;
        }
        int buttonWidth = Math.min(180, Math.max(120, width));
        int startButtonLeft = left;
        int startButtonTop = Math.min(nextY, contentBottom - 26);
        int startButtonRight = startButtonLeft + buttonWidth;
        int startButtonBottom = Math.min(contentBottom, startButtonTop + 22);
        recordDisconnectedStartButtonPresentation(new SFMScreenPanelBounds(
                startButtonLeft,
                startButtonTop,
                Math.max(0, startButtonRight - startButtonLeft),
                Math.max(0, startButtonBottom - startButtonTop)));
        startButton.setPanelBounds(new SFMScreenPanelBounds(
                startButtonLeft,
                startButtonTop,
                Math.max(0, startButtonRight - startButtonLeft),
                Math.max(0, startButtonBottom - startButtonTop)));
    }

    public boolean requestStartRustServer() {
        if (startRequested || remoteService == null) return false;
        startButtonAttemptCount++;
        startRequested = true;
        connectionStatus = "Starting the Rust terminal server...";
        Minecraft currentMinecraft = minecraft;
        Thread thread = new Thread(() -> {
            try {
                rustServerStarter.start();
                remoteService.reconnect();
                remoteService.requestConnect();
                if (currentMinecraft != null) currentMinecraft.execute(() -> {
                    startRequested = false;
                    connectionStatus = "Connecting to the Rust terminal server...";
                });
            } catch (Exception error) {
                if (currentMinecraft != null) currentMinecraft.execute(() -> {
                    startRequested = false;
                    connectionStatus = "Rust terminal server could not be started";
                });
            }
        }, "sfm-rust-terminal-start");
        thread.setDaemon(true);
        thread.start();
        return true;
    }

    void recordDisconnectedStartButtonPresentation(SFMScreenPanelBounds buttonBounds) {
        lastDisconnectedStartButtonBounds = buttonBounds;
        startButtonHitState = StartButtonHitState.active();
    }

    void invalidateDisconnectedStartButtonPresentation() {
        startButtonHitState = StartButtonHitState.inactive();
    }

    public boolean startRequestedForAutomation() {
        return startRequested;
    }

    public SFMScreenPanelBounds formerStartButtonBoundsForAutomation() {
        if (lastDisconnectedStartButtonBounds == null) {
            throw new IllegalStateException("Terminal never presented a disconnected Start/Retry target");
        }
        return lastDisconnectedStartButtonBounds;
    }

    public long startButtonAttemptCountForAutomation() {
        return startButtonAttemptCount;
    }

    public long terminalMouseDispatchCountForAutomation() {
        return terminalMouseDispatchCount;
    }

    public boolean formerStartButtonRoutingReadyForAutomation() {
        return lastAcceptedFrame != null
                && lastDisconnectedStartButtonBounds != null
                && !startButtonHitState.current();
    }

    private void renderFocusHint(PoseStack poseStack, Minecraft minecraft, int left, int width, int contentBottom) {
        SFMTerminalFocusSequence.Hint hint = focusSequence.hint(System.nanoTime());
        if (hint == null) return;
        LocalizationEntry entry = hint.kind() == SFMTerminalFocusSequence.HintKind.ESCAPE
                ? ESCAPE_FOCUS_HINT
                : TAB_FOCUS_HINT;
        Component message = entry.getComponent(Integer.toString(hint.remaining()), FOCUS_HINT_SECONDS);
        int lineHeight = minecraft.font.lineHeight + 2;
        int y = contentBottom - lineHeight - 2;
        int boxTop = y - 4;
        GuiComponent.fill(poseStack, bounds.x() + 4, boxTop,
                bounds.x() + bounds.width() - 4, contentBottom, 0xD0101218);
        SFMFontUtils.draw(poseStack, minecraft.font,
                minecraft.font.plainSubstrByWidth(message.getString(), width), left, y, MUTED, false);
    }

    private void layoutPresentationControl(int height) {
        if (remoteService == null) return;
        int width = Math.min(380, Math.max(150, bounds.width() - 12));
        presentationButtonRight = bounds.x() + bounds.width() - 6;
        presentationButtonLeft = presentationButtonRight - width;
        presentationButtonTop = bounds.y() + 4;
        presentationButtonBottom = presentationButtonTop + Math.max(1, height);
    }

    private boolean presentationControlKeyPressed(int keyCode) {
        if (keyCode == GLFW.GLFW_KEY_TAB) {
            // Leave an open list visible so the host's normal Tab traversal
            // can move into its individually narrated action buttons.
            return false;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            presentationMenuOpen = false;
            updatePresentationOptionVisibility();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_LEFT || keyCode == GLFW.GLFW_KEY_RIGHT) {
            presentationAxis = keyCode == GLFW.GLFW_KEY_LEFT
                    ? PresentationAxis.RENDERER : PresentationAxis.TRANSPORT;
            presentationMenuOpen = true;
            synchronizePresentationSelection();
            updatePresentationOptionVisibility();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER
                || keyCode == GLFW.GLFW_KEY_SPACE) {
            if (!presentationMenuOpen) {
                presentationMenuOpen = true;
                synchronizePresentationSelection();
                updatePresentationOptionVisibility();
            } else {
                selectCurrentPresentationChoice();
            }
            return true;
        }
        int optionCount = presentationAxis == PresentationAxis.RENDERER
                ? remoteService.rendererOptions().size()
                : remoteService.transportOptions().size();
        if (optionCount == 0) return true;
        int selected = presentationAxis == PresentationAxis.RENDERER
                ? rendererSelectionIndex : transportSelectionIndex;
        if (keyCode == GLFW.GLFW_KEY_HOME) selected = 0;
        else if (keyCode == GLFW.GLFW_KEY_END) selected = optionCount - 1;
        else if (keyCode == GLFW.GLFW_KEY_UP) selected = Math.floorMod(selected - 1, optionCount);
        else if (keyCode == GLFW.GLFW_KEY_DOWN) selected = (selected + 1) % optionCount;
        else return true;
        if (presentationAxis == PresentationAxis.RENDERER) rendererSelectionIndex = selected;
        else transportSelectionIndex = selected;
        presentationMenuOpen = true;
        updatePresentationOptionVisibility();
        return true;
    }

    private void synchronizePresentationSelection() {
        List<SFMTerminalRendererOption> rendererOptions = remoteService.rendererOptions();
        String requestedRenderer = remoteService.requestedRendererId();
        for (int index = 0; index < rendererOptions.size(); index++) {
            if (rendererOptions.get(index).id().wireId().equals(requestedRenderer)) {
                rendererSelectionIndex = index;
                break;
            }
        }
        rendererSelectionIndex = Math.min(rendererSelectionIndex,
                Math.max(0, rendererOptions.size() - 1));

        List<SFMTerminalTransportOption> transportOptions = remoteService.transportOptions();
        String requestedTransport = remoteService.requestedTransportId();
        for (int index = 0; index < transportOptions.size(); index++) {
            if (transportOptions.get(index).id().wireId().equals(requestedTransport)) {
                transportSelectionIndex = index;
                break;
            }
        }
        transportSelectionIndex = Math.min(transportSelectionIndex,
                Math.max(0, transportOptions.size() - 1));
    }

    private void selectCurrentPresentationChoice() {
        if (presentationAxis == PresentationAxis.RENDERER) {
            List<SFMTerminalRendererOption> options = remoteService.rendererOptions();
            if (rendererSelectionIndex < 0 || rendererSelectionIndex >= options.size()) return;
            String rendererId = options.get(rendererSelectionIndex).id().wireId();
            selectPresentationOption(
                    PresentationAxis.RENDERER,
                    rendererSelectionIndex,
                    "sfm action invoke sfm:terminal/renderer/set " + rendererId);
        } else {
            List<SFMTerminalTransportOption> options = remoteService.transportOptions();
            if (transportSelectionIndex < 0 || transportSelectionIndex >= options.size()) return;
            String transportId = options.get(transportSelectionIndex).id().wireId();
            selectPresentationOption(
                    PresentationAxis.TRANSPORT,
                    transportSelectionIndex,
                    "sfm action invoke sfm:terminal/transport/set " + transportId);
        }
    }

    private final class TerminalViewportWidget extends AbstractWidget implements SFMPanelWidget {
        private TerminalViewportWidget() {
            super(0, 0, 1, 1, Component.literal("Rust terminal viewport"));
        }

        @Override
        public ResourceLocation elementId() {
            return VIEWPORT_ELEMENT;
        }

        @Override
        public ResourceLocation keyboardUsageSituationId() {
            return TERMINAL_USAGE;
        }

        @Override
        public Component narration() {
            return SFMTerminalPanel.this.narration();
        }

        @Override
        public Optional<String> actionDraft() {
            return Optional.empty();
        }

        @Override
        public void setPanelBounds(SFMScreenPanelBounds bounds) {
            this.x = bounds.x();
            this.y = bounds.y();
            this.width = Math.max(0, bounds.width());
            this.height = Math.max(0, bounds.height());
        }

        @Override
        public boolean isPanelVisible() {
            return visible;
        }

        @Override
        public boolean isPanelEnabled() {
            return active;
        }

        @Override
        public boolean isPanelFocused() {
            return isFocused();
        }

        @Override
        public void setPanelFocused(boolean focused) {
            setFocused(focused);
            if (focused) focusSequence.reset();
        }

        @Override
        public void renderButton(PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
            // The Rust/Java terminal presentation is rendered by the panel;
            // this child owns its focus, narration, and event rectangle only.
        }

        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            return SFMTerminalPanel.this.keyPressed(keyCode, scanCode, modifiers);
        }

        @Override
        public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
            return SFMTerminalPanel.this.keyReleased(keyCode, scanCode, modifiers);
        }

        @Override
        public boolean charTyped(char character, int modifiers) {
            return SFMTerminalPanel.this.charTyped(character, modifiers);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (!isMouseOver(mouseX, mouseY)) return false;
            if (remoteService == null) {
                focusTerminalInput();
                return true;
            }
            return SFMTerminalPanel.this.mouseClicked(mouseX, mouseY, button);
        }

        @Override
        public boolean mouseReleased(double mouseX, double mouseY, int button) {
            return SFMTerminalPanel.this.mouseReleased(mouseX, mouseY, button);
        }

        @Override
        public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
            return SFMTerminalPanel.this.mouseDragged(mouseX, mouseY, button, dragX, dragY);
        }

        @Override
        public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
            return SFMTerminalPanel.this.mouseScrolled(mouseX, mouseY, delta);
        }

        @Override
        public void mouseMoved(double mouseX, double mouseY) {
            SFMTerminalPanel.this.mouseMoved(mouseX, mouseY);
        }

        @Override
        public void updateNarration(NarrationElementOutput output) {
            output.add(NarratedElementType.TITLE, narration());
            output.add(NarratedElementType.USAGE,
                    Component.literal("Terminal input. Press Tab three times to move to panel controls."));
        }
    }
}
