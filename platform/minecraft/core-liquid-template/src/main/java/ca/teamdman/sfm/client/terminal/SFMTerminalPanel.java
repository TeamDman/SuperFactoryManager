package ca.teamdman.sfm.client.terminal;

import ca.teamdman.sfm.client.screen.SFMFontUtils;
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
{% if features.terminal_remote or features.terminal_vox_runtime %}
{% if features.terminal_clipboard %}
{% if features.terminal_frame_metadata %}
import ca.teamdman.sfm.client.screen.SFMScreenRenderUtils;
{% endif %}
{% endif %}
{% endif %}
{% if features.terminal_remote or features.terminal_vox_runtime %}
{% if features.terminal_clipboard %}
{% if features.terminal_paste_confirmation %}
import ca.teamdman.sfm.client.screen.SFMTerminalPasteConfirmationScreen;
{% endif %}
{% endif %}
{% endif %}
{% endcase %}
import ca.teamdman.sfm.client.screen.workspace.SFMScreenPanel;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenPanelBounds;
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
import ca.teamdman.sfm.client.screen.workspace.SFMPanelActionButton;
import ca.teamdman.sfm.client.screen.workspace.SFMPanelActionExecution;
import ca.teamdman.sfm.client.screen.workspace.SFMPanelWidget;
import ca.teamdman.sfm.client.screen.workspace.SFMPanelWidgetHost;
{% endcase %}
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspacePanelContext;
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspacePanelMetrics;
import ca.teamdman.sfm.SFM;
{% endcase %}
import ca.teamdman.sfm.common.localization.LocalizationEntry;
import ca.teamdman.sfm.common.localization.SFMLocalizationDatagen;
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
{% case minecraft_version %}
{% when "1.19.4" %}
import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
{% endcase %}
import com.mojang.blaze3d.vertex.PoseStack;
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
{% case minecraft_version %}
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
import net.minecraft.client.gui.GuiGraphics;
{% when "26.1.2" %}
import net.minecraft.client.gui.GuiGraphicsExtractor;
{% endcase %}
{% endcase %}
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
{% endcase %}
import net.minecraft.network.chat.Component;
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
import net.minecraft.resources.ResourceLocation;
{% endcase %}
import org.lwjgl.glfw.GLFW;

import java.util.List;
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
import java.util.ArrayList;
import java.util.Objects;
import java.util.Optional;
{% endcase %}

/** Composable terminal leaf. Rust/Vox frames are presented when that backend is configured. */
public final class SFMTerminalPanel implements SFMScreenPanel {
    private static final String FOCUS_HINT_SECONDS = "1.5";
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
    private static final int CONTENT_HORIZONTAL_PADDING = 8;
    private static final int CONTENT_BOTTOM_PADDING = 4;
    private static final int TITLE_CONTENT_GAP = 12;
    private static final int VANILLA_CONTROL_HEIGHT = 20;
{% endcase %}

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
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
    private static final ResourceLocation DEFAULT_USAGE = new ResourceLocation(SFM.MOD_ID, "default");
    private static final ResourceLocation TERMINAL_USAGE = new ResourceLocation(SFM.MOD_ID, "terminal");
    private static final ResourceLocation VIEWPORT_ELEMENT = new ResourceLocation(SFM.MOD_ID, "terminal/viewport");
    private static final ResourceLocation START_ELEMENT = new ResourceLocation(SFM.MOD_ID, "terminal/server/start_control");
    private static final ResourceLocation PRESENTATION_ELEMENT = new ResourceLocation(SFM.MOD_ID, "terminal/presentation/select");
{% endcase %}
    private final SFMTerminalClient client;
{% if features.terminal_remote or features.terminal_vox_runtime %}
    private final SFMTerminalRemoteService remoteService;
{% endif %}
{% if features.terminal_remote or features.terminal_vox_runtime %}
    private final SFMTerminalPngRenderer pngRenderer = new SFMTerminalPngRenderer();
{% endif %}
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
{% if features.terminal_remote or features.terminal_vox_runtime %}
{% if features.terminal_frame_metadata %}
    private final SFMTerminalRgbaRenderer rgbaRenderer = new SFMTerminalRgbaRenderer();
{% endif %}
{% endif %}
{% endcase %}
    private final SFMTerminalScrollback scrollback = new SFMTerminalScrollback();
{% if features.terminal_focus_gestures %}
    private final SFMTerminalFocusSequence focusSequence = new SFMTerminalFocusSequence();
{% endif %}
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
    private final SFMPanelWidgetHost widgetHost = new SFMPanelWidgetHost();
    private final TerminalViewportWidget viewportWidget = new TerminalViewportWidget();
{% if features.terminal_server_lifecycle %}
    private final SFMPanelActionButton startButton;
{% endif %}
{% if features.terminal_presentation_actions %}
    private final SFMPanelActionButton presentationButton;
{% endif %}
{% if features.terminal_presentation_actions %}
    private final List<SFMPanelActionButton> presentationOptions = new ArrayList<>();
{% endif %}
{% if features.terminal_presentation_actions %}
    private String presentationOptionsSignature = "";
{% endif %}
{% endcase %}
    private String input = "";
    private SFMScreenPanelBounds bounds = new SFMScreenPanelBounds(0, 0, 1, 1);
    private SFMWorkspacePanelContext context;
    private Minecraft minecraft;
    private int renderLeft;
    private int renderTop;
    private int renderWidth;
    private int renderHeight;
    private int pressedMouseButtons;
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
{% if features.terminal_clipboard %}
    private boolean suppressCopyRelease;
{% endif %}
{% if features.terminal_clipboard %}
    private boolean suppressPasteRelease;
{% endif %}
    private boolean startRequested;
{% if features.terminal_remote or features.terminal_vox_runtime %}
{% if features.terminal_frame_metadata %}
    private String lastLoggedPresentationStreamIdentity;
{% endif %}
{% endif %}
{% if features.terminal_remote or features.terminal_vox_runtime %}
{% if features.terminal_frame_metadata %}
    private long lastLoggedPresentationSequence = Long.MIN_VALUE;
{% endif %}
{% endif %}
    private String connectionStatus;
{% if features.terminal_remote or features.terminal_vox_runtime %}
{% if features.terminal_server_lifecycle %}
    private final RustServerStarter rustServerStarter;
{% endif %}
{% endif %}
{% if features.client_actions %}
    private final PanelActionInvoker actionOverride;
{% endif %}
    private StartButtonHitState startButtonHitState = StartButtonHitState.inactive();
    private SFMScreenPanelBounds lastDisconnectedStartButtonBounds;
    private long startButtonAttemptCount;
    private long terminalMouseDispatchCount;
{% if features.terminal_presentation_actions %}
    private boolean presentationMenuOpen;
{% endif %}
{% if features.terminal_presentation_actions %}
    private PresentationAxis presentationAxis = PresentationAxis.RENDERER;
{% endif %}
{% if features.terminal_presentation_actions %}
    private int rendererSelectionIndex;
{% endif %}
{% if features.terminal_presentation_actions %}
    private int transportSelectionIndex;
{% endif %}
{% if features.terminal_presentation_actions %}
    private int presentationButtonLeft;
{% endif %}
{% if features.terminal_presentation_actions %}
    private int presentationButtonTop;
{% endif %}
{% if features.terminal_presentation_actions %}
    private int presentationButtonRight;
{% endif %}
{% if features.terminal_presentation_actions %}
    private int presentationButtonBottom;
{% endif %}
{% if features.terminal_remote or features.terminal_vox_runtime or features.terminal_properties %}
    private SFMTerminalTuningSettings tuning = SFMTerminalTuningSettings.automatic();
{% endif %}
{% if features.terminal_remote or features.terminal_vox_runtime or features.terminal_properties %}
    private SFMTerminalTuningSettings acceptedTuning = SFMTerminalTuningSettings.automatic();
{% endif %}
{% if features.terminal_remote or features.terminal_vox_runtime or features.terminal_properties %}
    private boolean tuningPending;
{% endif %}
{% if features.terminal_remote or features.terminal_vox_runtime or features.terminal_properties %}
    private SFMTerminalTuningRejection lastTuningRejection;
{% endif %}
    private int automaticSurfaceWidth = 1;
    private int automaticSurfaceHeight = 1;
    private int automaticColumns = 1;
    private int automaticRows = 1;
    private SFMScreenPanelBounds viewportLogicalBounds = new SFMScreenPanelBounds(0, 0, 1, 1);
    private Optional<SFMWorkspacePanelMetrics> viewportMetrics = Optional.empty();
{% if features.terminal_remote or features.terminal_vox_runtime or features.terminal_properties %}
    private SFMTerminalFrame lastAcceptedFrame;
{% endif %}
{% if features.terminal_remote or features.terminal_vox_runtime %}
{% if features.terminal_clipboard %}
{% if features.terminal_paste_confirmation %}
    private PendingPaste pendingPaste;
{% endif %}
{% endif %}
{% endif %}
    private String lastPanelActionFeedback = "";
    private TerminalScene terminalScene;
    private Boolean lastObservedConnection;
    private String lastObservedConnectionFailure = "";
    private final List<ConnectionEvent> connectionEvents = new ArrayList<>();

    /**
     * A terminal panel is one of three disjoint scenes.  In particular, the
     * Rust landing page does not merely hide terminal widgets: those widgets
     * are absent from its child tree, so stale focus, hit targets, and partial
     * rendering cannot leak across the connection boundary.
     */
    private sealed interface TerminalScene permits LocalReplScene, RustLandingScene, RustTerminalScene {
    }

    private record LocalReplScene() implements TerminalScene {
    }

    private record RustLandingScene(
            List<ConnectionEvent> events,
            boolean startPending,
            boolean startAvailable,
            boolean retryConnection
    ) implements TerminalScene {
        private RustLandingScene {
            events = List.copyOf(events);
        }
    }

    private record RustTerminalScene(boolean framePresented) implements TerminalScene {
    }

    private record ConnectionEvent(String message, boolean error) {
        private ConnectionEvent {
            message = Objects.requireNonNull(message).trim();
        }
    }

    public enum RustLifecycleRequest {
        STARTING_SERVER(true),
        RETRYING_CONNECTION(true),
        ALREADY_CONNECTED(false),
        PRESENTATION_PENDING(false),
        REQUEST_IN_PROGRESS(false),
        UNAVAILABLE(false);

        private final boolean accepted;

        RustLifecycleRequest(boolean accepted) {
            this.accepted = accepted;
        }

        public boolean accepted() {
            return accepted;
        }
    }

{% if features.terminal_remote or features.terminal_vox_runtime %}
{% if features.terminal_clipboard %}
{% if features.terminal_paste_confirmation %}
    private record PendingPaste(String text, String preview, String contentId, long interactionEpoch) {
    }
{% endif %}
{% endif %}
{% endif %}

{% if features.terminal_remote or features.terminal_vox_runtime %}
{% if features.terminal_server_lifecycle %}
    @FunctionalInterface
    interface RustServerStarter {
        void start() throws Exception;
    }
{% endif %}
{% endif %}

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
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
    private boolean suppressPasteRelease;
{% endcase %}

    public SFMTerminalPanel(SFMTerminalService service) {
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
        this(
                new SFMTerminalClient(service),
{% if features.terminal_remote or features.terminal_vox_runtime %}
                service instanceof SFMTerminalRemoteService remote ? remote : null,
{% endif %}
{% if features.terminal_remote or features.terminal_vox_runtime %}
{% if features.terminal_server_lifecycle %}
                () -> SFMTerminalServiceFactory.startRustServer(null),
{% endif %}
{% endif %}
                null);
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
{% if features.terminal_remote or features.terminal_vox_runtime %}
        this(new SFMTerminalClient(service), service instanceof SFMTerminalRemoteService remote ? remote : null);
{% else %}
        this(new SFMTerminalClient(service));
{% endif %}
{% endcase %}
    }

    public SFMTerminalPanel(SFMTerminalClient client) {
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
{% if features.terminal_remote or features.terminal_vox_runtime %}
{% if features.terminal_server_lifecycle %}
        this(client, null, () -> SFMTerminalServiceFactory.startRustServer(null), null);
{% else %}
        this(client, null, null);
{% endif %}
{% else %}
        this(client, null);
{% endif %}
    }

{% if features.terminal_remote or features.terminal_vox_runtime %}
{% if features.terminal_server_lifecycle %}
    SFMTerminalPanel(SFMTerminalRemoteService remoteService, RustServerStarter rustServerStarter) {
        this(new SFMTerminalClient(remoteService), remoteService, rustServerStarter, null);
    }
{% endif %}
{% endif %}

{% if features.terminal_remote or features.terminal_vox_runtime %}
{% if features.terminal_server_lifecycle %}
    SFMTerminalPanel(
            SFMTerminalRemoteService remoteService,
            RustServerStarter rustServerStarter,
            PanelActionInvoker actionOverride
    ) {
        this(new SFMTerminalClient(remoteService), remoteService, rustServerStarter, actionOverride);
    }
{% endif %}
{% endif %}

    private SFMTerminalPanel(
            SFMTerminalClient client,
{% if features.terminal_remote or features.terminal_vox_runtime %}
            SFMTerminalRemoteService remoteService,
{% endif %}
{% if features.terminal_remote or features.terminal_vox_runtime %}
{% if features.terminal_server_lifecycle %}
            RustServerStarter rustServerStarter,
{% endif %}
{% endif %}
            PanelActionInvoker actionOverride
    ) {
        this.client = client;
{% if features.terminal_remote or features.terminal_vox_runtime %}
        this.remoteService = remoteService;
{% endif %}
{% if features.terminal_remote or features.terminal_vox_runtime %}
{% if features.terminal_server_lifecycle %}
        this.rustServerStarter = Objects.requireNonNull(rustServerStarter);
{% endif %}
{% endif %}
        this.actionOverride = actionOverride;
{% if features.terminal_server_lifecycle %}
        this.startButton = new SFMPanelActionButton(
                START_ELEMENT,
                DEFAULT_USAGE,
                Component.literal("Start / Retry Rust server"),
                this::startControlNarration,
                () -> "sfm action invoke sfm:terminal/server/start",
                () -> executePanelAction("sfm action invoke sfm:terminal/server/start")
        );
{% endif %}
{% if features.terminal_presentation_actions %}
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
{% endif %}
{% if features.terminal_presentation_actions %}
        widgetHost.setFocusStateListener(this::terminalWidgetFocusChanged);
{% endif %}
{% if features.terminal_remote or features.terminal_vox_runtime %}
        this.connectionStatus = remoteService == null
                ? "Java-local terminal"
                : "Looking for the configured Rust terminal server.";
{% else %}
        this.connectionStatus = "Java-local terminal";
{% endif %}
{% if features.terminal_remote or features.terminal_vox_runtime %}
        if (remoteService != null) {
            recordConnectionEvent(connectionStatus, false);
        }
{% endif %}
        this.terminalScene = initialTerminalScene();
        rebuildTerminalWidgets();
{% if features.terminal_remote or features.terminal_vox_runtime %}
        if (remoteService == null) {
            scrollback.appendAll(List.of(
                    "Java-local terminal · explicit REPL mode",
                    "Type pwd, ls, cat <file>, echo <text>, or write <file> <text>"
            ));
        }
{% else %}
            scrollback.appendAll(List.of(
                    "Java-local terminal · explicit REPL mode",
                    "Type pwd, ls, cat <file>, echo <text>, or write <file> <text>"
            ));
{% endif %}
    }

    private void rebuildTerminalWidgets() {
{% if features.terminal_presentation_actions %}
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
{% endif %}
        installSceneWidgets();
{% if features.terminal_presentation_actions %}
        presentationOptionsSignature = presentationOptionsSignature();
{% endif %}
    }

    private TerminalScene initialTerminalScene() {
{% if features.terminal_remote or features.terminal_vox_runtime %}
        if (remoteService == null) return new LocalReplScene();
        return observedTerminalScene(remoteService.connectionSnapshot(), false);
{% else %}
        return new LocalReplScene();
{% endif %}
    }

{% if features.terminal_remote or features.terminal_vox_runtime %}
    private TerminalScene observedTerminalScene(
            SFMTerminalConnectionSnapshot connection,
            boolean framePresented
    ) {
        if (remoteService == null) return new LocalReplScene();
        if (connection.connected() && connection.presentationReady()) {
            return new RustTerminalScene(framePresented);
        }
        boolean retryConnection = connection.connected()
                && !connection.presentationReady()
                && connection.failure().isPresent();
        return new RustLandingScene(
                connectionEvents,
                startRequested,
                !connection.connected() || retryConnection,
                retryConnection);
    }
{% endif %}

    private void transitionTerminalScene(TerminalScene next) {
        boolean membershipChanged = !sameSceneFamily(terminalScene, next);
        terminalScene = Objects.requireNonNull(next);
        if (!membershipChanged) return;
{% if features.terminal_presentation_actions %}
        presentationMenuOpen = false;
{% endif %}
        installSceneWidgets();
{% if features.terminal_server_lifecycle %}
        if (next instanceof RustLandingScene) invalidateDisconnectedStartButtonPresentation();
{% endif %}
    }

    private static boolean sameSceneFamily(TerminalScene left, TerminalScene right) {
        return left != null && right != null && left.getClass().equals(right.getClass());
    }

    private void installSceneWidgets() {
        List<SFMPanelWidget> children = new ArrayList<>();
        if (terminalScene instanceof LocalReplScene) {
            children.add(viewportWidget);
        } else if (terminalScene instanceof RustLandingScene) {
{% if features.terminal_server_lifecycle %}
            children.add(startButton);
{% endif %}
        } else if (terminalScene instanceof RustTerminalScene) {
            children.add(viewportWidget);
{% if features.terminal_presentation_actions %}
            children.add(presentationButton);
            children.addAll(presentationOptions);
{% endif %}
        }
        widgetHost.setChildren(children);
{% if features.terminal_presentation_actions %}
        updatePresentationOptionVisibility();
{% endif %}
    }

{% if features.terminal_presentation_actions %}
    private void refreshPresentationOptions() {
        if (!presentationOptionsSignature.equals(presentationOptionsSignature())) {
            rebuildTerminalWidgets();
        }
    }
{% endif %}

{% if features.terminal_presentation_actions %}
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
{% endif %}

    private void synchronizeTerminalWidgets(TerminalScene scene, ViewportGeometry viewport) {
        boolean landing = scene instanceof RustLandingScene;
        boolean starting = scene instanceof RustLandingScene rustLanding && rustLanding.startPending();
        boolean startAvailable = scene instanceof RustLandingScene rustLanding && rustLanding.startAvailable();
        boolean retryConnection = scene instanceof RustLandingScene rustLanding && rustLanding.retryConnection();
        boolean terminal = scene instanceof LocalReplScene || scene instanceof RustTerminalScene;
        viewportWidget.visible = terminal;
        viewportWidget.active = viewportWidget.visible;
        viewportWidget.setPanelBounds(new SFMScreenPanelBounds(
                viewport.left(), viewport.top(), viewport.width(), viewport.height()));
{% if features.terminal_server_lifecycle %}
        startButton.visible = landing;
        startButton.active = landing && startAvailable && !starting;
        startButton.setMessage(Component.literal(starting
                ? "Starting..."
                : retryConnection
                ? "Retry terminal connection"
                : "Start / Retry Rust server"));
        if (!landing) invalidateDisconnectedStartButtonPresentation();
{% endif %}
{% if features.terminal_presentation_actions %}
        boolean remoteTerminal = scene instanceof RustTerminalScene;
        presentationButton.visible = remoteTerminal;
        presentationButton.active = remoteTerminal;
        int controlHeight = Math.max(VANILLA_CONTROL_HEIGHT, viewport.lineHeight() + 4);
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
{% endif %}
        if (widgetHost.focusedChild().isEmpty()) {
{% if features.terminal_server_lifecycle %}
            widgetHost.focus(landing ? START_ELEMENT : VIEWPORT_ELEMENT);
{% else %}
            widgetHost.focus(VIEWPORT_ELEMENT);
{% endif %}
        }
    }

{% if features.terminal_presentation_actions %}
    private void togglePresentationMenu() {
        presentationMenuOpen = !presentationMenuOpen;
        synchronizePresentationSelection();
        updatePresentationOptionVisibility();
    }
{% endif %}

    /** Invoked by the semantic command action for the visible selector control. */
{% if features.terminal_presentation_actions %}
    public boolean togglePresentationMenuForAction() {
        if (!(terminalScene instanceof RustTerminalScene)) return false;
        togglePresentationMenu();
        return true;
    }
{% endif %}

{% if features.terminal_presentation_actions %}
    private void updatePresentationOptionVisibility() {
        for (SFMPanelActionButton option : presentationOptions) {
            option.visible = terminalScene instanceof RustTerminalScene && presentationMenuOpen;
        }
    }
{% endif %}

{% if features.terminal_presentation_actions %}
    private void terminalWidgetFocusChanged(SFMPanelWidgetHost.FocusState state) {
        ResourceLocation focusedElementId = state.focusedElementId();
        if (!presentationMenuOpen || state.active() && isPresentationElement(focusedElementId)) return;
        presentationMenuOpen = false;
        updatePresentationOptionVisibility();
    }
{% endif %}

{% if features.terminal_presentation_actions %}
    private static boolean isPresentationElement(ResourceLocation elementId) {
        return elementId != null
                && elementId.getNamespace().equals(SFM.MOD_ID)
                && (elementId.equals(PRESENTATION_ELEMENT)
                || elementId.getPath().startsWith("terminal/presentation/"));
    }
{% endif %}

{% if features.terminal_presentation_actions %}
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
{% endif %}

{% if features.terminal_presentation_actions %}
    private boolean presentationOptionKeyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode != GLFW.GLFW_KEY_ESCAPE) return false;
        presentationMenuOpen = false;
        updatePresentationOptionVisibility();
        widgetHost.focus(PRESENTATION_ELEMENT);
        return true;
    }
{% endif %}

    private boolean executePanelAction(String draft) {
        if (actionOverride != null) return actionOverride.invoke(draft);
        if (context == null || minecraft == null) return false;
        return SFMPanelActionExecution.execute(context, minecraft, draft, feedback ->
                lastPanelActionFeedback = feedback.getString());
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
{% if features.terminal_remote or features.terminal_vox_runtime %}
        this(client, null);
{% else %}
        this.client = client;
        scrollback.appendAll(List.of(
                "Java-local terminal · Rust/Vox unavailable fallback",
                "Type pwd, ls, cat <file>, echo <text>, or write <file> <text>"
        ));
{% endif %}
{% endcase %}
    }

{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
{% if features.terminal_presentation_actions %}
    private Component presentationNarration() {
        String feedback = lastPanelActionFeedback.isBlank() ? "" : ". " + lastPanelActionFeedback;
        return Component.literal(presentationLabel() + feedback);
    }
{% endif %}

{% if features.terminal_server_lifecycle %}
    private Component startControlNarration() {
        if (startRequested) return Component.literal("Starting Rust terminal server");
        if (terminalScene instanceof RustLandingScene landing && landing.retryConnection()) {
            return Component.literal("Retry the unavailable Rust terminal presentation");
        }
        return Component.literal("Start or retry Rust terminal server");
    }
{% endif %}

{% if features.terminal_presentation_actions %}
    private String currentPresentationActionDraft() {
        if (remoteService == null) {
            return "sfm action invoke sfm:terminal/presentation/toggle";
        }
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
        return "sfm action invoke sfm:terminal/presentation/toggle";
    }
{% endif %}

{% if features.terminal_presentation_actions %}
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
{% endif %}
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
{% if features.terminal_remote or features.terminal_vox_runtime %}
    private SFMTerminalPanel(SFMTerminalClient client, SFMTerminalRemoteService remoteService) {
        this.client = client;
        this.remoteService = remoteService;
        scrollback.appendAll(List.of(
                remoteService == null
                        ? "Java-local terminal · Rust/Vox unavailable fallback"
                        : "Rust-authoritative terminal · full PNG Vox mode",
                "Type pwd, ls, cat <file>, echo <text>, or write <file> <text>"
        ));
    }
{% endif %}
{% endcase %}

    @Override
    public Component title() {
        return Component.literal("SFM Terminal");
    }

    @Override
    public Component narration() {
{% if features.terminal_remote or features.terminal_vox_runtime %}
        return Component.literal((remoteService == null ? "Java-local terminal at " : "Rust/Vox terminal at ")
                + client.workingDirectory());
{% else %}
        return Component.literal("Java-local terminal at " + client.workingDirectory());
{% endif %}
    }

    @Override
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
    public Optional<SFMPanelWidgetHost> widgetHost() {
        return Optional.of(widgetHost);
    }

    @Override
    public boolean widgetHostOwnsInput() {
        return true;
    }

    @Override
{% endcase %}
    public void opened(Minecraft minecraft, SFMScreenPanelBounds bounds, SFMWorkspacePanelContext context) {
        this.minecraft = minecraft;
        this.bounds = bounds;
        this.context = context;
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
{% if features.terminal_remote or features.terminal_vox_runtime %}
{% if features.terminal_frame_metadata %}
        this.lastLoggedPresentationStreamIdentity = null;
        this.lastLoggedPresentationSequence = Long.MIN_VALUE;
{% endif %}
{% endif %}
{% if features.terminal_remote or features.terminal_vox_runtime %}
        if (remoteService != null) {
            observeRemoteLifecycle(remoteService.connectionSnapshot());
            resized(minecraft, bounds);
            remoteService.requestConnect();
        }
{% endif %}
{% endcase %}
    }

    @Override
    public void resized(Minecraft minecraft, SFMScreenPanelBounds bounds) {
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
        resizeRemoteViewport(
                bounds,
                Math.max(1, minecraft.font.width("W")),
                Math.max(1, minecraft.font.lineHeight + 2)
        );
    }

    void resizeRemoteViewport(SFMScreenPanelBounds bounds, int cellWidth, int lineHeight) {
{% if features.terminal_remote or features.terminal_vox_runtime %}
{% if features.terminal_server_lifecycle %}
        invalidateDisconnectedStartButtonPresentation();
{% endif %}
        this.bounds = bounds;
        ViewportGeometry viewport = viewportGeometry(
                bounds,
                cellWidth,
                lineHeight,
                remoteService != null
        );
        applyViewport(viewport);
{% if features.terminal_presentation_actions %}
        layoutPresentationControl(Math.max(VANILLA_CONTROL_HEIGHT, viewport.lineHeight() + 4));
{% endif %}
        SFMTerminalConnectionSnapshot connection = remoteService == null
                ? null
                : remoteService.connectionSnapshot();
        if (connection != null) observeRemoteLifecycle(connection);
        TerminalScene scene = connection == null
                ? new LocalReplScene()
                : observedTerminalScene(connection, false);
        transitionTerminalScene(scene);
        synchronizeTerminalWidgets(scene, viewport);
        if (remoteService != null) {
            SFMScreenPanelBounds logicalViewport = new SFMScreenPanelBounds(
                    viewport.left(), viewport.top(), viewport.width(), viewport.height());
{% if features.workspace_panel_measurement %}
            Optional<SFMWorkspacePanelMetrics> measured = context == null
                    ? Optional.empty()
                    : context.measure(logicalViewport);
{% else %}
            Optional<SFMWorkspacePanelMetrics> measured = Optional.empty();
{% endif %}
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
{% else %}
        this.bounds = bounds;
        ViewportGeometry viewport = viewportGeometry(
                bounds,
                cellWidth,
                lineHeight,
                false
        );
        applyViewport(viewport);
        synchronizeTerminalWidgets(new LocalReplScene(), viewport);
{% endif %}
    }

{% if features.terminal_remote or features.terminal_vox_runtime %}
    static SFMScreenPanelBounds rasterTarget(
            SFMWorkspacePanelContext context,
            SFMScreenPanelBounds logicalViewport
    ) {
{% if features.workspace_panel_measurement %}
        SFMScreenPanelBounds measured = context == null
                ? logicalViewport
                : context.measure(logicalViewport)
                        .map(SFMWorkspacePanelMetrics::physicalPixelBounds)
                        .orElse(logicalViewport);
{% else %}
        SFMScreenPanelBounds measured = logicalViewport;
{% endif %}
        return boundedRasterTarget(measured);
    }
{% endif %}

{% if features.terminal_remote or features.terminal_vox_runtime %}
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
{% endif %}

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
{% if features.terminal_remote or features.terminal_vox_runtime %}
        if (remoteService == null) return;
{% if features.terminal_presentation_actions %}
        refreshPresentationOptions();
{% endif %}
        remoteService.requestConnect();
        SFMTerminalConnectionSnapshot connection = remoteService.connectionSnapshot();
        observeRemoteLifecycle(connection);
        transitionTerminalScene(observedTerminalScene(connection, false));
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
{% endif %}
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
        this.bounds = bounds;
{% if features.terminal_remote or features.terminal_vox_runtime %}
        if (remoteService != null) {
            int cellWidth = Math.max(1, minecraft.font.width("W"));
            int cellHeight = Math.max(1, minecraft.font.lineHeight + 2);
            remoteService.resize(bounds.width() / cellWidth, bounds.height() / cellHeight);
        }
{% endif %}
{% endcase %}
    }

    @Override
    public void closed() {
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
{% if features.terminal_server_lifecycle %}
        invalidateDisconnectedStartButtonPresentation();
{% endif %}
{% if features.terminal_remote or features.terminal_vox_runtime %}
        if (minecraft != null) {
            pngRenderer.close(minecraft);
{% if features.terminal_frame_metadata %}
            rgbaRenderer.close(minecraft);
{% endif %}
        }
{% endif %}
{% if features.terminal_remote or features.terminal_vox_runtime %}
        if (remoteService != null) remoteService.close();
{% endif %}
{% if features.terminal_remote or features.terminal_vox_runtime %}
{% if features.terminal_clipboard %}
{% if features.terminal_paste_confirmation %}
        pendingPaste = null;
{% endif %}
{% endif %}
{% endif %}
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
{% if features.terminal_remote or features.terminal_vox_runtime %}
        if (minecraft != null) pngRenderer.close(minecraft);
        if (remoteService != null) remoteService.close();
{% endif %}
{% endcase %}
        minecraft = null;
        context = null;
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
{% if features.terminal_remote or features.terminal_vox_runtime %}
{% if features.terminal_frame_metadata %}
        lastLoggedPresentationStreamIdentity = null;
        lastLoggedPresentationSequence = Long.MIN_VALUE;
{% endif %}
{% endif %}
{% endcase %}
    }

    @Override
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
    public void render(PoseStack poseStack, Minecraft minecraft, SFMScreenPanelBounds bounds,
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
    @ca.teamdman.sfm.common.util.MCVersionDependentBehaviour
{% case minecraft_version %}
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
    public void render(GuiGraphics graphics, Minecraft minecraft, SFMScreenPanelBounds bounds,
{% when "26.1.2" %}
    public void render(GuiGraphicsExtractor graphics, Minecraft minecraft, SFMScreenPanelBounds bounds,
{% endcase %}
{% endcase %}
                       int mouseX, int mouseY, float partialTick, boolean focused) {
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
        GuiComponent.fill(poseStack, bounds.x(), bounds.y(), bounds.x() + bounds.width(), bounds.y() + bounds.height(), PANEL);
        ViewportGeometry viewport = viewportGeometry(
                bounds,
                minecraft.font.width("W"),
                minecraft.font.lineHeight + 2,
{% if features.terminal_remote or features.terminal_vox_runtime %}
                remoteService != null
{% else %}
                false
{% endif %}
        );
        applyViewport(viewport);
        int left = viewport.left();
        int width = viewport.width();
        int lineHeight = viewport.lineHeight();
        int inputY = viewport.inputY();
        int contentBottom = viewport.contentBottom();
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
        graphics.fill(bounds.x(), bounds.y(), bounds.x() + bounds.width(), bounds.y() + bounds.height(), PANEL);
        int left = bounds.x() + 8;
        int width = Math.max(1, bounds.width() - 16);
        int lineHeight = minecraft.font.lineHeight + 2;
        int inputY = bounds.y() + bounds.height() - lineHeight - 8;
{% if features.terminal_remote or features.terminal_vox_runtime %}
        int contentBottom = remoteService == null ? inputY : bounds.y() + bounds.height() - 4;
{% else %}
        int contentBottom = inputY;
{% endif %}
{% endcase %}
        int visibleLines = Math.max(0, (contentBottom - bounds.y() - 22) / lineHeight);
        scrollback.setViewportLineCount(Math.max(1, visibleLines));
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
        int contentTop = viewport.top();
        SFMFontUtils.draw(poseStack, minecraft.font, title().copy().withStyle(ChatFormatting.BOLD), left,
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
        int contentTop = bounds.y() + lineHeight + 12;
        renderLeft = left;
        renderTop = contentTop;
        renderWidth = width;
        renderHeight = Math.max(1, contentBottom - contentTop - 4);
        SFMFontUtils.draw(graphics, minecraft.font, title().copy().withStyle(ChatFormatting.BOLD), left,
{% endcase %}
                bounds.y() + 8, TEXT, false);
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
{% if features.terminal_remote or features.terminal_vox_runtime %}
        if (remoteService != null) {
            SFMTerminalConnectionSnapshot connection = remoteService.connectionSnapshot();
            observeRemoteLifecycle(connection);
            if (!connection.connected() || !connection.presentationReady()) {
                TerminalScene scene = observedTerminalScene(connection, false);
                transitionTerminalScene(scene);
                synchronizeTerminalWidgets(scene, viewport);
                renderLanding(poseStack, minecraft, left, width, contentTop, contentBottom,
                        (RustLandingScene) scene);
                return;
            }
            Optional<SFMTerminalFrame> frame = remoteService.latestFrame();
            frame.ifPresent(accepted -> {
                lastAcceptedFrame = accepted;
            });
            boolean presented = false;
            if (frame.isPresent() || remoteService.canPresentRetainedFrame()) {
                boolean png = frame.map(SFMTerminalFrame::png)
                        .orElseGet(() -> remoteService.activeTransportId()
                                .map("full-png"::equals).orElse(true));
{% if features.terminal_frame_metadata %}
                presented = png
                        ? pngRenderer.render(poseStack, minecraft, left, contentTop, width,
                        renderHeight, localToPhysicalScaleX(), localToPhysicalScaleY(), frame)
                        : rgbaRenderer.render(poseStack, minecraft, left, contentTop, width,
                        renderHeight, localToPhysicalScaleX(), localToPhysicalScaleY(), frame);
{% else %}
                if (png) {
                    presented = pngRenderer.render(poseStack, minecraft, left, contentTop, width,
                            renderHeight, localToPhysicalScaleX(), localToPhysicalScaleY(), frame);
                }
{% endif %}
            }
            if (presented) {
{% if features.terminal_clipboard %}
{% if features.terminal_frame_metadata %}
                renderRemoteSelection(poseStack);
{% endif %}
{% endif %}
{% if features.terminal_frame_metadata %}
                if (frame.isPresent() && isNewPresentation(
                        lastLoggedPresentationStreamIdentity,
                        lastLoggedPresentationSequence,
                        frame.get())) {
                    lastLoggedPresentationStreamIdentity = frame.get().streamIdentity();
                    lastLoggedPresentationSequence = frame.get().sequence();
                    logPresentationTiming(frame.get(), pngRenderer.telemetry());
                }
{% endif %}
{% if features.terminal_focus_gestures %}
                renderFocusHint(poseStack, minecraft, left, width, contentBottom);
{% endif %}
            } else {
                renderConnectedWaitingForFrame(poseStack, minecraft, left, contentTop);
            }
            TerminalScene scene = observedTerminalScene(connection, presented);
            transitionTerminalScene(scene);
            synchronizeTerminalWidgets(scene, viewport);
            return;
        }
{% endif %}
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
{% if features.terminal_remote or features.terminal_vox_runtime %}
        if (remoteService != null && pngRenderer.render(graphics, minecraft, left, contentTop, width,
                renderHeight, remoteService.latestFrame())) {
{% if features.terminal_focus_gestures %}
            renderFocusHint(graphics, minecraft, left, width, contentBottom);
{% endif %}
            return;
        }
{% endif %}
{% endcase %}
        int y = contentTop;
        for (SFMTerminalLine terminalLine : scrollback.visibleLineEntries()) {
            if (y >= contentBottom) break;
            String line = terminalLine.text();
            int color = line.startsWith("error:") ? ERROR : terminalLine.color();
            String remaining = line;
            do {
                String rendered = minecraft.font.plainSubstrByWidth(remaining, width);
                if (rendered.isEmpty()) rendered = remaining.substring(0, 1);
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
                SFMFontUtils.draw(poseStack, minecraft.font, rendered, left, y, color, false);
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
                SFMFontUtils.draw(graphics, minecraft.font, rendered, left, y, color, false);
{% endcase %}
                y += lineHeight;
                remaining = remaining.substring(rendered.length());
            } while (!remaining.isEmpty() && y < contentBottom);
        }
{% if features.terminal_remote or features.terminal_vox_runtime %}
        if (remoteService == null) {
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
            renderInput(poseStack, minecraft, left, width, inputY, focused);
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
            renderInput(graphics, minecraft, left, width, inputY, focused);
{% endcase %}
        }
{% else %}
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
        renderInput(poseStack, minecraft, left, width, inputY, focused);
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
        renderInput(graphics, minecraft, left, width, inputY, focused);
{% endcase %}
{% endif %}
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
{% if features.terminal_focus_gestures %}
        renderFocusHint(poseStack, minecraft, left, width, contentBottom);
{% endif %}
        TerminalScene scene = new LocalReplScene();
        transitionTerminalScene(scene);
        synchronizeTerminalWidgets(scene, viewport);
    }

{% if features.terminal_remote or features.terminal_vox_runtime %}
{% if features.terminal_frame_metadata %}
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
{% endif %}
{% endif %}

{% if features.terminal_remote or features.terminal_vox_runtime %}
{% if features.terminal_frame_metadata %}
    static boolean isNewPresentation(
            String previousStreamIdentity,
            long previousSequence,
            SFMTerminalFrame frame) {
        return !Objects.equals(previousStreamIdentity, frame.streamIdentity())
                || previousSequence != frame.sequence();
    }
{% endif %}
{% endif %}

    private static long micros(long nanos) {
        return nanos <= 0 ? 0 : nanos / 1_000L;
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
{% if features.terminal_focus_gestures %}
        renderFocusHint(graphics, minecraft, left, width, contentBottom);
{% endif %}
{% endcase %}
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
{% if features.terminal_focus_gestures %}
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
{% if features.terminal_host_escape %}
            if (focusSequence.escape(System.nanoTime()) == SFMTerminalFocusSequence.Decision.HOST_ESCAPE) {
                // The first two presses belong to the PTY. The third is not
                // forwarded and deliberately falls through to the workspace's
                // constrained close palette instead of closing a panel directly.
                return false;
            }
{% else %}
            if (focusSequence.escape(System.nanoTime()) == SFMTerminalFocusSequence.Decision.EXIT) {
                if (context != null) context.submit(new ca.teamdman.sfm.client.screen.workspace.SFMWorkspacePanelIntent.Close());
                return true;
            }
{% endif %}
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
            if (focusSequence.escape(System.nanoTime()) == SFMTerminalFocusSequence.Decision.EXIT) {
                if (context != null) context.submit(new ca.teamdman.sfm.client.screen.workspace.SFMWorkspacePanelIntent.Close());
                return true;
            }
{% endcase %}
{% if features.terminal_remote or features.terminal_vox_runtime %}
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
{% if features.terminal_keyboard_input %}
            if (remoteService != null) remoteService.sendKey(keyCode, modifiers, true, false);
{% endif %}
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
            if (remoteService != null) remoteService.sendKey(keyCode, modifiers, true, false);
{% endcase %}
{% endif %}
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_TAB) {
            SFMTerminalFocusSequence.Decision decision = focusSequence.tab(System.nanoTime());
            if (decision == SFMTerminalFocusSequence.Decision.JAVA_FOCUS) {
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
{% if features.terminal_presentation_actions %}
                presentationMenuOpen = false;
                synchronizePresentationSelection();
                updatePresentationOptionVisibility();
{% endif %}
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
                // Returning false lets Minecraft's Screen focus traversal own
                // this third Tab instead of sending it to the PTY.
{% endcase %}
                return false;
            }
{% if features.terminal_remote or features.terminal_vox_runtime %}
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
{% if features.terminal_keyboard_input %}
            if (remoteService != null) remoteService.sendKey(keyCode, modifiers, true, false);
{% endif %}
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
            if (remoteService != null) remoteService.sendKey(keyCode, modifiers, true, false);
{% endcase %}
{% endif %}
{% if features.terminal_remote or features.terminal_vox_runtime %}
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
{% if features.terminal_keyboard_input %}
            else input += "\t";
{% endif %}
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
            else input += "\t";
{% endcase %}
{% else %}
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
{% if features.terminal_keyboard_input %}
            input += "\t";
{% endif %}
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
            input += "\t";
{% endcase %}
{% endif %}
            return true;
        }
{% endif %}
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
{% if features.terminal_remote or features.terminal_vox_runtime %}
{% if features.terminal_clipboard %}
        if (remoteService != null && isCopyShortcut(keyCode, modifiers)) {
            suppressCopyRelease = true;
            if (!requestCopy(false)) {
                suppressCopyRelease = false;
                return false;
            }
            return true;
        }
{% endif %}
{% endif %}
{% endcase %}
{% if features.terminal_remote or features.terminal_vox_runtime %}
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
{% if features.terminal_clipboard %}
        if (remoteService != null && isPasteShortcut(keyCode, modifiers)) {
            suppressPasteRelease = true;
            String clipboard = minecraft == null ? "" : minecraft.keyboardHandler.getClipboard();
            if (!clipboard.isEmpty() && !requestGuardedPaste(clipboard)) {
                suppressPasteRelease = false;
                return false;
            }
            return true;
        }
{% endif %}
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
        if (remoteService != null && isPasteShortcut(keyCode, modifiers)) {
            suppressPasteRelease = true;
            String clipboard = minecraft == null ? "" : minecraft.keyboardHandler.getClipboard();
            if (!clipboard.isEmpty() && !remoteService.sendText(clipboard)) {
                suppressPasteRelease = false;
                return false;
            }
            return true;
        }
{% endcase %}
{% endif %}
{% if features.terminal_focus_gestures %}
        focusSequence.reset();
{% endif %}
{% if features.terminal_remote or features.terminal_vox_runtime %}
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
{% if features.terminal_keyboard_input %}
        if (remoteService != null) {
            if (!isPrintableKey(keyCode)
                    || (modifiers & (GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_ALT | GLFW.GLFW_MOD_SUPER)) != 0) {
                remoteService.sendKey(normalizeTerminalKeyCode(keyCode), modifiers, true, false);
            }
            return true;
        }
{% endif %}
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
        if (remoteService != null) {
            if (!isPrintableKey(keyCode)
                    || (modifiers & (GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_ALT | GLFW.GLFW_MOD_SUPER)) != 0) {
                remoteService.sendKey(keyCode, modifiers, true, false);
            }
            return true;
        }
{% endcase %}
{% endif %}
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
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
{% if features.terminal_keyboard_input %}
        if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
            if (!input.isEmpty()) input = input.substring(0, input.length() - 1);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            submitInput();
            return true;
        }
{% endif %}
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
        if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
            if (!input.isEmpty()) input = input.substring(0, input.length() - 1);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            submitInput();
            return true;
        }
{% endcase %}
        return false;
    }

{% if features.terminal_remote or features.terminal_vox_runtime %}
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
{% if features.terminal_keyboard_input or features.terminal_clipboard %}
    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (remoteService == null) return false;
{% if features.terminal_clipboard %}
        if (suppressCopyRelease && keyCode == GLFW.GLFW_KEY_C) {
            suppressCopyRelease = false;
            return true;
        }
        if (suppressPasteRelease && keyCode == GLFW.GLFW_KEY_V) {
            suppressPasteRelease = false;
            return true;
        }
{% endif %}
{% if features.terminal_keyboard_input %}
        if (!isPrintableKey(keyCode)
                || (modifiers & (GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_ALT | GLFW.GLFW_MOD_SUPER)) != 0) {
            remoteService.sendKey(normalizeTerminalKeyCode(keyCode), modifiers, false, false);
        }
{% endif %}
        return true;
    }
{% endif %}
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (remoteService == null) return false;
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
{% endcase %}
{% endif %}

{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
{% if features.terminal_keyboard_input %}
    private static int normalizeTerminalKeyCode(int keyCode) {
        return keyCode == GLFW.GLFW_KEY_KP_ENTER ? GLFW.GLFW_KEY_ENTER : keyCode;
    }
{% endif %}

{% if features.terminal_keyboard_input %}
    @Override
    public boolean charTyped(char character, int modifiers) {
{% if features.terminal_remote or features.terminal_vox_runtime %}
        if (remoteService != null) {
            if (character >= 0x20 && character != 0x7F) {
                remoteService.sendText(String.valueOf(character));
            }
            return true;
        }
{% endif %}
        if (character >= 0x20 && character != 0x7F && input.length() < 512) {
            input += character;
            return true;
        }
        return false;
    }
{% endif %}
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
    @Override
    public boolean charTyped(char character, int modifiers) {
{% if features.terminal_remote or features.terminal_vox_runtime %}
        if (remoteService != null) {
            if (character >= 0x20 && character != 0x7F) {
                remoteService.sendText(String.valueOf(character));
            }
            return true;
        }
{% endif %}
        if (character >= 0x20 && character != 0x7F && input.length() < 512) {
            input += character;
            return true;
        }
        return false;
    }
{% endcase %}

{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
{% if features.terminal_keyboard_input or features.terminal_repl_actions %}
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
{% endif %}
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
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
{% endcase %}

    /** Deterministic hook for puppet proofs; normal users use keyboard input. */
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
{% if features.terminal_repl_actions %}
    public void executeForAutomation(String command) {
{% if features.terminal_remote or features.terminal_vox_runtime %}
        if (remoteService != null) {
            SFMTerminalResponse response = client.execute(command == null ? "" : command);
            if (!response.success()) {
                throw new IllegalStateException("Rust terminal automation command failed: "
                        + String.join("; ", response.lines()));
            }
            return;
        }
{% endif %}
        input = command == null ? "" : command;
        submitInput();
    }
{% endif %}
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
    public void executeForAutomation(String command) {
{% if features.terminal_remote or features.terminal_vox_runtime %}
        if (remoteService != null) {
            SFMTerminalResponse response = client.execute(command == null ? "" : command);
            if (!response.success()) {
                throw new IllegalStateException("Rust terminal automation command failed: "
                        + String.join("; ", response.lines()));
            }
            return;
        }
{% endif %}
        input = command == null ? "" : command;
        submitInput();
    }
{% endcase %}

    /** Deterministic hook for puppet proofs of the Vox cancellation RPC. */
{% if features.terminal_remote or features.terminal_vox_runtime %}
    public void cancelForAutomation() {
        if (remoteService == null || !remoteService.cancel()) {
            throw new IllegalStateException("Rust terminal cancellation failed");
        }
    }
{% endif %}

    /** Clears the current Vox transport so the next witness establishes a fresh session. */
{% if features.terminal_remote or features.terminal_vox_runtime %}
    public void reconnectForAutomation() {
        if (remoteService == null) {
            throw new IllegalStateException("Java-local terminal has no Rust connection to reconnect");
        }
        remoteService.reconnect();
    }
{% endif %}

    /** Deterministic hook for puppet proofs of the Rust logical resize path. */
{% if features.terminal_remote or features.terminal_vox_runtime %}
    public void resizeForAutomation(int columns, int rows) {
        if (remoteService == null || !remoteService.resize(columns, rows)) {
            throw new IllegalStateException("Rust terminal resize failed");
        }
    }
{% endif %}

    /** Sends a key directly to Rust for child-TUI proofs without consuming SFM focus gestures. */
{% if features.terminal_remote or features.terminal_vox_runtime %}
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
{% if features.terminal_keyboard_input %}
    public void pressKeyForAutomation(int keyCode, int modifiers) {
        if (remoteService == null
                || !remoteService.sendKey(keyCode, modifiers, true, false)
                || !remoteService.sendKey(keyCode, modifiers, false, false)) {
            throw new IllegalStateException("Rust terminal direct key delivery failed");
        }
    }
{% endif %}
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
    public void pressKeyForAutomation(int keyCode, int modifiers) {
        if (remoteService == null
                || !remoteService.sendKey(keyCode, modifiers, true, false)
                || !remoteService.sendKey(keyCode, modifiers, false, false)) {
            throw new IllegalStateException("Rust terminal direct key delivery failed");
        }
    }
{% endcase %}
{% endif %}

    /** Deterministic hook for puppet proofs of the real clipboard paste shortcut. */
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
{% if features.terminal_clipboard %}
    public void pasteForAutomation(String text) {
{% if features.terminal_remote or features.terminal_vox_runtime %}
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
{% else %}
        input += text == null ? "" : text;
{% endif %}
    }
{% endif %}
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
    public void pasteForAutomation(String text) {
{% if features.terminal_remote or features.terminal_vox_runtime %}
        if (remoteService == null) {
            input += text == null ? "" : text;
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
{% else %}
        input += text == null ? "" : text;
{% endif %}
    }
{% endcase %}

    /** Returns the Rust-owned visible terminal text for deterministic puppet assertions. */
    public String contentForAutomation() {
{% if features.terminal_remote or features.terminal_vox_runtime %}
        if (remoteService != null) return remoteService.contentForAutomation();
{% endif %}
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

{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
    /** Returns bounded timing/counter evidence for the Rust PNG presentation path. */
{% if features.terminal_remote or features.terminal_vox_runtime %}
    public SFMTerminalPngTelemetry.Snapshot pngTelemetry() {
        return pngRenderer.telemetry();
    }
{% endif %}

    /** Validates and returns machine-readable Vox push evidence for puppets. */
{% if features.terminal_remote or features.terminal_vox_runtime %}
    public String assertPushEvidenceForAutomation(boolean reconnectExpected) {
        if (remoteService == null) {
            throw new IllegalStateException("Terminal panel is not backed by a remote service");
        }
        return remoteService.assertPushEvidenceForAutomation(reconnectExpected);
    }
{% endif %}

{% endcase %}
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (mouseX < bounds.x() || mouseX >= bounds.x() + bounds.width()
                || mouseY < bounds.y() || mouseY >= bounds.y() + bounds.height()) return false;
{% if features.terminal_remote or features.terminal_vox_runtime %}
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
{% if features.terminal_keyboard_input %}
        if (remoteService != null) {
            if (remoteService.sendMouse(logicalX(mouseX), logicalY(mouseY), pressedMouseButtons, 0, false,
                    false, 0, (int) Math.round(delta))) terminalMouseDispatchCount++;
        } else if (delta > 0) scrollback.scrollOlder(Math.max(1, (int) Math.ceil(delta)));
        else if (delta < 0) scrollback.scrollNewer(Math.max(1, (int) Math.ceil(-delta)));
{% else %}
        if (remoteService == null) {
            if (delta > 0) scrollback.scrollOlder(Math.max(1, (int) Math.ceil(delta)));
            else if (delta < 0) scrollback.scrollNewer(Math.max(1, (int) Math.ceil(-delta)));
        }
{% endif %}
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
        if (remoteService != null) {
            remoteService.sendMouse(logicalX(mouseX), logicalY(mouseY), pressedMouseButtons, 0, false,
                    false, 0, (int) Math.round(delta));
        } else if (delta > 0) scrollback.scrollOlder(Math.max(1, (int) Math.ceil(delta)));
        else if (delta < 0) scrollback.scrollNewer(Math.max(1, (int) Math.ceil(-delta)));
{% endcase %}
{% else %}
        if (delta > 0) scrollback.scrollOlder(Math.max(1, (int) Math.ceil(delta)));
        else if (delta < 0) scrollback.scrollNewer(Math.max(1, (int) Math.ceil(-delta)));
{% endif %}
        return delta != 0;
    }

{% if features.terminal_remote or features.terminal_vox_runtime %}
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
{% if features.terminal_keyboard_input or features.terminal_clipboard %}
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
{% if features.terminal_keyboard_input %}
        if (!containsTerminalPoint(mouseX, mouseY)) return false;
        if (remoteService == null) return false;
        // A viewport click is terminal interaction even if the selector was
        // previously keyboard-focused. Restore terminal focus before the PTY
        // receives the click so subsequent keyboard input cannot be consumed
        // by a stale presentation menu.
        focusTerminalInput();
        int mask = mouseMask(button);
        pressedMouseButtons |= mask;
{% if features.terminal_clipboard %}
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
{% else %}
            if (remoteService.sendMouse(logicalX(mouseX), logicalY(mouseY), pressedMouseButtons, button, true,
                    false, 0, 0)) terminalMouseDispatchCount++;
{% endif %}
        return true;
{% else %}
        if (!containsTerminalPoint(mouseX, mouseY)) return false;
        if (remoteService == null || button != GLFW.GLFW_MOUSE_BUTTON_RIGHT) return false;
        focusTerminalInput();
        return requestCopy(true);
{% endif %}
    }
{% endif %}
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!containsTerminalPoint(mouseX, mouseY)) return false;
        if (remoteService == null) return false;
        int mask = mouseMask(button);
        pressedMouseButtons |= mask;
        remoteService.sendMouse(logicalX(mouseX), logicalY(mouseY), pressedMouseButtons, button, true,
                false, 0, 0);
        return true;
    }
{% endcase %}
{% endif %}

{% if features.terminal_remote or features.terminal_vox_runtime %}
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
{% if features.terminal_keyboard_input %}
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
{% endif %}
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (remoteService == null || !containsTerminalPoint(mouseX, mouseY)) return false;
        pressedMouseButtons &= ~mouseMask(button);
        remoteService.sendMouse(logicalX(mouseX), logicalY(mouseY), pressedMouseButtons, button, false,
                false, 0, 0);
        return true;
    }
{% endcase %}
{% endif %}

{% if features.terminal_remote or features.terminal_vox_runtime %}
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
{% if features.terminal_keyboard_input %}
    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (remoteService == null || pressedMouseButtons == 0) return false;
        if (remoteService.sendMouse(logicalX(mouseX), logicalY(mouseY), pressedMouseButtons,
                button, true, true, 0, 0)) terminalMouseDispatchCount++;
        return true;
    }
{% endif %}
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (remoteService == null || !containsTerminalPoint(mouseX, mouseY)) return false;
        remoteService.sendMouse(logicalX(mouseX), logicalY(mouseY), pressedMouseButtons,
                button, true, true, 0, 0);
        return true;
    }
{% endcase %}
{% endif %}

{% if features.terminal_remote or features.terminal_vox_runtime %}
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
{% if features.terminal_keyboard_input %}
    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        if (remoteService != null && pressedMouseButtons != 0) {
            if (remoteService.sendMouse(logicalX(mouseX), logicalY(mouseY), pressedMouseButtons,
                    0, true, true, 0, 0)) terminalMouseDispatchCount++;
        }
    }
{% endif %}
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        if (remoteService != null && pressedMouseButtons != 0 && containsTerminalPoint(mouseX, mouseY)) {
            remoteService.sendMouse(logicalX(mouseX), logicalY(mouseY), pressedMouseButtons,
                    0, true, true, 0, 0);
        }
    }
{% endcase %}
{% endif %}

    public SFMTerminalClient client() {
        return client;
    }

{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
    /** Panel-local action surface; callers must resolve this exact focused panel first. */
{% if features.terminal_presentation_actions %}
    public List<SFMTerminalRendererOption> rendererOptions() {
        return remoteService == null ? List.of() : remoteService.rendererOptions();
    }
{% endif %}

{% if features.terminal_presentation_actions %}
    public List<SFMTerminalTransportOption> transportOptions() {
        return remoteService == null ? List.of() : remoteService.transportOptions();
    }
{% endif %}

{% if features.terminal_presentation_actions %}
    public SFMTerminalPresentationChangeResult requestRenderer(String rendererId) {
        if (remoteService == null) {
            return SFMTerminalPresentationChangeResult.rejected(
                    "Focused panel is not a Rust terminal");
        }
        return remoteService.requestRenderer(rendererId);
    }
{% endif %}

    /** Panel-local action surface; callers must resolve this exact focused panel first. */
{% if features.terminal_presentation_actions %}
    public SFMTerminalPresentationChangeResult requestTransport(String transportId) {
        if (remoteService == null) {
            return SFMTerminalPresentationChangeResult.rejected(
                    "Focused panel is not a Rust terminal");
        }
        return remoteService.requestTransport(transportId);
    }
{% endif %}

    public boolean isRustBacked() {
{% if features.terminal_remote or features.terminal_vox_runtime %}
        return remoteService != null;
{% else %}
        return false;
{% endif %}
    }

    /** Immutable diagnostics for UI controls, automation, and live evidence. */
{% if features.terminal_properties %}
    public SFMTerminalPropertiesSnapshot propertiesSnapshot() {
        SFMTerminalTuningSettings.Effective effective = effectiveTuning();
{% if features.terminal_frame_metadata %}
        Optional<SFMTerminalPropertiesSnapshot.AcceptedFrame> acceptedFrame =
                Optional.ofNullable(lastAcceptedFrame).map(SFMTerminalPropertiesSnapshot::fromFrame);
{% else %}
        Optional<SFMTerminalPropertiesSnapshot.AcceptedFrame> acceptedFrame = Optional.empty();
{% endif %}
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
{% if features.workspace_panel_measurement %}
        Optional<SFMScreenPanelBounds> javaDrawPhysical = context == null
                ? Optional.empty()
                : javaDrawLogical.flatMap(context::measure)
                        .map(SFMWorkspacePanelMetrics::physicalPixelBounds);
{% else %}
        Optional<SFMScreenPanelBounds> javaDrawPhysical = Optional.empty();
{% endif %}
{% if features.workspace_panel_measurement %}
        Optional<SFMWorkspacePanelMetrics> panelMetrics = context == null
                ? Optional.empty()
                : context.measure(bounds);
{% else %}
        Optional<SFMWorkspacePanelMetrics> panelMetrics = Optional.empty();
{% endif %}
        Optional<SFMTerminalTuningRejection> rejection = Optional.ofNullable(lastTuningRejection);
{% if features.terminal_remote or features.terminal_vox_runtime %}
        if (rejection.isEmpty() && remoteService != null) rejection = remoteService.tuningFailure();
{% endif %}
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
{% if features.terminal_remote or features.terminal_vox_runtime %}
                remoteService == null ? Optional.empty() : remoteService.presentationDiagnostics(),
{% else %}
                Optional.empty(),
{% endif %}
{% if features.terminal_remote or features.terminal_vox_runtime %}
                remoteService == null ? "" : remoteService.requestedRendererId(),
{% else %}
                "",
{% endif %}
{% if features.terminal_remote or features.terminal_vox_runtime %}
                remoteService == null ? "" : remoteService.activeRendererId().orElse(""),
{% else %}
                "",
{% endif %}
{% if features.terminal_remote or features.terminal_vox_runtime %}
                remoteService == null ? "" : remoteService.requestedTransportId(),
{% else %}
                "",
{% endif %}
{% if features.terminal_remote or features.terminal_vox_runtime %}
                remoteService == null ? "" : remoteService.activeTransportId().orElse(""),
{% else %}
                "",
{% endif %}
                rejection
        );
    }
{% endif %}

{% if features.terminal_tuning_actions %}
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
{% endif %}

{% if features.terminal_remote or features.terminal_vox_runtime %}
    private boolean applyTuning() {
        if (remoteService == null) return false;
        SFMTerminalTuningSettings.Effective effective = effectiveTuning();
        return remoteService.resize(tuning, effective);
    }
{% endif %}

{% if features.terminal_remote or features.terminal_vox_runtime or features.terminal_properties %}
    private SFMTerminalTuningSettings.Effective effectiveTuning() {
        return tuning.resolve(
                automaticSurfaceWidth,
                automaticSurfaceHeight,
                automaticColumns,
                automaticRows
        );
    }
{% endif %}

{% if features.terminal_remote or features.terminal_vox_runtime or features.terminal_properties %}
    private int acceptedFontPixelSize() {
{% if features.terminal_frame_metadata %}
        return Optional.ofNullable(lastAcceptedFrame)
                .map(SFMTerminalFrame::metadata)
                .map(SFMTerminalFrameMetadata::fontPixelSize)
                .filter(value -> value > 0)
                .orElse(SFMTerminalTuningSettings.MIN_FONT_PIXEL_SIZE);
{% else %}
        return SFMTerminalTuningSettings.MIN_FONT_PIXEL_SIZE;
{% endif %}
    }
{% endif %}

    private double localToPhysicalScaleX() {
        return viewportMetrics.map(SFMWorkspacePanelMetrics::localToPhysicalScaleX).orElse(1.0D);
    }

    private double localToPhysicalScaleY() {
        return viewportMetrics.map(SFMWorkspacePanelMetrics::localToPhysicalScaleY).orElse(1.0D);
    }

{% if features.terminal_remote or features.terminal_vox_runtime or features.terminal_properties %}
    private static String describe(SFMTerminalTuningSettings settings) {
        return "surface=" + axis(settings.surfaceWidth()) + "x" + axis(settings.surfaceHeight())
                + ", font=" + axis(settings.fontPixelSize())
                + ", cells=" + axis(settings.columns()) + "x" + axis(settings.rows());
    }
{% endif %}

{% if features.terminal_tuning_actions %}
    private static String describeOperation(
            SFMTerminalTuningOperation operation,
            int first,
            int second
    ) {
        return operation + " first=" + first + " second=" + second;
    }
{% endif %}

{% if features.terminal_remote or features.terminal_vox_runtime or features.terminal_properties %}
    private static String axis(int value) {
        return value == 0 ? "auto" : Integer.toString(value);
    }
{% endif %}

{% if features.terminal_remote or features.terminal_vox_runtime %}
{% if features.terminal_clipboard %}
{% if features.terminal_frame_metadata %}
{% case minecraft_version %}
{% when "1.19.2" %}
{% if features.screen_fractional_highlights %}
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
{% else %}
    private void renderRemoteSelection(PoseStack poseStack) {
        for (SFMTerminalSelectionLayout.LogicalHighlight highlight : selectionHighlightsForAutomation()) {
            // The 1.19.4 highlight helper takes integer coordinates. Transform
            // a unit rectangle to retain fractional terminal cell boundaries.
            poseStack.pushPose();
            try {
                poseStack.translate(highlight.startX(), highlight.startY(), 0D);
                poseStack.scale(
                        (float) (highlight.endX() - highlight.startX()),
                        (float) (highlight.endY() - highlight.startY()),
                        1F);
                SFMScreenRenderUtils.renderHighlight(poseStack, 0, 0, 1, 1);
            } finally {
                poseStack.popPose();
            }
        }
    }
{% endif %}
{% when "1.19.4" %}
    @MCVersionDependentBehaviour
    private void renderRemoteSelection(PoseStack poseStack) {
        for (SFMTerminalSelectionLayout.LogicalHighlight highlight : selectionHighlightsForAutomation()) {
            // The 1.19.4 highlight helper takes integer coordinates. Transform
            // a unit rectangle to retain fractional terminal cell boundaries.
            poseStack.pushPose();
            try {
                poseStack.translate(highlight.startX(), highlight.startY(), 0D);
                poseStack.scale(
                        (float) (highlight.endX() - highlight.startX()),
                        (float) (highlight.endY() - highlight.startY()),
                        1F);
                SFMScreenRenderUtils.renderHighlight(poseStack, 0, 0, 1, 1);
            } finally {
                poseStack.popPose();
            }
        }
    }
{% endcase %}
{% endif %}
{% endif %}
{% endif %}

{% if features.terminal_remote or features.terminal_vox_runtime %}
{% if features.terminal_clipboard %}
{% if features.terminal_frame_metadata %}
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
{% endif %}
{% endif %}
{% endif %}

    record TerminalCellPoint(double x, double y) {
    }

{% if features.terminal_remote or features.terminal_vox_runtime %}
{% if features.terminal_frame_metadata %}
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
{% endif %}
{% endif %}

{% if features.terminal_remote or features.terminal_vox_runtime %}
{% if features.terminal_clipboard %}
    Optional<SFMTerminalSelection> selectionForAutomation() {
        return remoteService == null ? Optional.empty() : remoteService.selection();
    }
{% endif %}
{% endif %}

{% if features.terminal_remote or features.terminal_vox_runtime %}
    Optional<SFMTerminalFrame> acceptedFrameForAutomation() {
        return Optional.ofNullable(lastAcceptedFrame);
    }
{% endif %}

{% if features.terminal_remote or features.terminal_vox_runtime %}
    SFMTerminalPngTelemetry.Snapshot pngTelemetryForAutomation() {
        return pngRenderer.telemetry();
    }
{% endif %}

{% if features.terminal_remote or features.terminal_vox_runtime %}
{% if features.terminal_frame_metadata %}
    SFMTerminalRgbaRenderer.Snapshot rgbaTelemetryForAutomation() {
        return rgbaRenderer.telemetry();
    }
{% endif %}
{% endif %}

{% if features.terminal_remote or features.terminal_vox_runtime %}
{% if features.terminal_clipboard %}
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
{% if features.terminal_keyboard_input %}
                remoteService.sendKey(GLFW.GLFW_KEY_C, GLFW.GLFW_MOD_CONTROL, true, false);
                remoteService.sendKey(GLFW.GLFW_KEY_C, GLFW.GLFW_MOD_CONTROL, false, false);
{% endif %}
            }
            focusTerminalInput();
        }));
    }
{% endif %}
{% endif %}

{% if features.terminal_remote or features.terminal_vox_runtime %}
{% if features.terminal_clipboard %}
    private boolean requestGuardedPaste(String text) {
        if (remoteService == null || text == null || text.isEmpty()) return true;
        Minecraft requestMinecraft = minecraft;
        return remoteService.pasteWithGuard(text, result -> runOnMinecraftThread(requestMinecraft, () -> {
            if (result.disposition() == SFMTerminalPasteResult.Disposition.PASTED) {
{% if features.terminal_paste_confirmation %}
                pendingPaste = null;
{% endif %}
                focusTerminalInput();
                return;
            }
{% if features.terminal_paste_confirmation %}
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
{% endif %}
        }));
    }
{% endif %}
{% endif %}

{% if features.terminal_remote or features.terminal_vox_runtime %}
{% if features.terminal_clipboard %}
{% if features.terminal_paste_confirmation %}
    private void resolvePendingPaste(String expectedContentId, boolean approved) {
        PendingPaste pending = pendingPaste;
        if (pending == null || !pending.contentId().equals(expectedContentId)) return;
        pendingPaste = null;
        focusTerminalInput();
        if (!approved || remoteService == null) return;
        SFMTerminalConnectionSnapshot connection = remoteService.connectionSnapshot();
        if (!connection.connected()
                || connection.interactionEpoch() != pending.interactionEpoch()) return;
        Minecraft requestMinecraft = minecraft;
        remoteService.pasteWithoutGuard(
                pending.text(),
                pending.contentId(),
                ignored -> runOnMinecraftThread(requestMinecraft, this::focusTerminalInput));
    }
{% endif %}
{% endif %}
{% endif %}

{% if features.terminal_remote or features.terminal_vox_runtime %}
{% if features.terminal_clipboard %}
{% if features.terminal_paste_confirmation %}
    Optional<String> pendingPasteContentIdForAutomation() {
        return Optional.ofNullable(pendingPaste).map(PendingPaste::contentId);
    }
{% endif %}
{% endif %}
{% endif %}

{% if features.terminal_remote or features.terminal_vox_runtime %}
{% if features.terminal_clipboard %}
{% if features.terminal_paste_confirmation %}
    Optional<String> pendingPastePreviewForAutomation() {
        return Optional.ofNullable(pendingPaste).map(PendingPaste::preview);
    }
{% endif %}
{% endif %}
{% endif %}

{% if features.terminal_remote or features.terminal_vox_runtime %}
{% if features.terminal_clipboard %}
{% if features.terminal_paste_confirmation %}
    void resolvePendingPasteForAutomation(String contentId, boolean approved) {
        resolvePendingPaste(contentId, approved);
    }
{% endif %}
{% endif %}
{% endif %}

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

{% if features.terminal_remote or features.terminal_vox_runtime %}
    private SFMTerminalImageLayout terminalImageLayout() {
{% if features.terminal_frame_metadata %}
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
{% else %}
        return new SFMTerminalImageLayout(renderLeft, renderTop, renderWidth, renderHeight);
{% endif %}
    }
{% endif %}

{% endcase %}
{% if features.terminal_remote or features.terminal_vox_runtime %}
    private boolean containsTerminalPoint(double mouseX, double mouseY) {
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
        SFMTerminalImageLayout layout = terminalImageLayout();
        return mouseX >= layout.x() && mouseX < layout.x() + layout.width()
                && mouseY >= layout.y() && mouseY < layout.y() + layout.height();
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
        return mouseX >= renderLeft && mouseX < renderLeft + renderWidth
                && mouseY >= renderTop && mouseY < renderTop + renderHeight;
{% endcase %}
    }
{% endif %}

{% if features.terminal_remote or features.terminal_vox_runtime %}
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
{% if features.terminal_keyboard_input %}
    private int logicalX(double mouseX) {
        SFMTerminalImageLayout layout = terminalImageLayout();
{% if features.terminal_frame_metadata %}
        int columns = lastAcceptedFrame == null
                ? remoteService.logicalWidth()
                : lastAcceptedFrame.metadata().logicalColumns();
{% else %}
        int columns = remoteService.logicalWidth();
{% endif %}
        int cell = (int) Math.floor((mouseX - layout.x()) * Math.max(1, columns)
                / Math.max(1, layout.width()));
        return Math.max(0, Math.min(Math.max(1, columns) - 1, cell));
    }
{% endif %}
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
    private int logicalX(double mouseX) {
        return Math.max(0, (int) ((mouseX - renderLeft) * remoteService.logicalWidth() / Math.max(1, renderWidth)));
    }
{% endcase %}
{% endif %}

{% if features.terminal_remote or features.terminal_vox_runtime %}
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
{% if features.terminal_keyboard_input %}
    private int logicalY(double mouseY) {
        SFMTerminalImageLayout layout = terminalImageLayout();
{% if features.terminal_frame_metadata %}
        int rows = lastAcceptedFrame == null
                ? remoteService.logicalHeight()
                : lastAcceptedFrame.metadata().logicalRows();
{% else %}
        int rows = remoteService.logicalHeight();
{% endif %}
        int cell = (int) Math.floor((mouseY - layout.y()) * Math.max(1, rows)
                / Math.max(1, layout.height()));
        return Math.max(0, Math.min(Math.max(1, rows) - 1, cell));
    }
{% endif %}
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
    private int logicalY(double mouseY) {
        return Math.max(0, (int) ((mouseY - renderTop) * remoteService.logicalHeight() / Math.max(1, renderHeight)));
    }
{% endcase %}
{% endif %}

{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
{% if features.terminal_keyboard_input %}
    private static int mouseMask(int button) {
        return button >= 0 && button < 8 ? 1 << button : 0;
    }
{% endif %}

{% if features.terminal_keyboard_input %}
    private static boolean isPrintableKey(int keyCode) {
        return keyCode == GLFW.GLFW_KEY_SPACE
                || keyCode >= GLFW.GLFW_KEY_APOSTROPHE && keyCode <= GLFW.GLFW_KEY_GRAVE_ACCENT
                || keyCode >= GLFW.GLFW_KEY_0 && keyCode <= GLFW.GLFW_KEY_9
                || keyCode >= GLFW.GLFW_KEY_A && keyCode <= GLFW.GLFW_KEY_Z;
    }
{% endif %}

{% if features.terminal_clipboard %}
    private static boolean isPasteShortcut(int keyCode, int modifiers) {
        return keyCode == GLFW.GLFW_KEY_V
                && (modifiers & GLFW.GLFW_MOD_CONTROL) != 0
                && (modifiers & (GLFW.GLFW_MOD_ALT | GLFW.GLFW_MOD_SUPER)) == 0;
    }
{% endif %}
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
{% if features.terminal_remote or features.terminal_vox_runtime %}
    private static int mouseMask(int button) {
        return button >= 0 && button < 8 ? 1 << button : 0;
    }
{% endif %}
{% endcase %}

{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
{% if features.terminal_clipboard %}
    private static boolean isCopyShortcut(int keyCode, int modifiers) {
        return keyCode == GLFW.GLFW_KEY_C
                && (modifiers & GLFW.GLFW_MOD_CONTROL) != 0
                && (modifiers & (GLFW.GLFW_MOD_ALT | GLFW.GLFW_MOD_SUPER)) == 0;
    }
{% endif %}
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
    private static boolean isPrintableKey(int keyCode) {
        return keyCode == GLFW.GLFW_KEY_SPACE
                || keyCode >= GLFW.GLFW_KEY_APOSTROPHE && keyCode <= GLFW.GLFW_KEY_GRAVE_ACCENT
                || keyCode >= GLFW.GLFW_KEY_0 && keyCode <= GLFW.GLFW_KEY_9
                || keyCode >= GLFW.GLFW_KEY_A && keyCode <= GLFW.GLFW_KEY_Z;
    }
{% endcase %}

{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
    private void focusTerminalInput() {
{% if features.terminal_presentation_actions %}
        presentationMenuOpen = false;
        updatePresentationOptionVisibility();
{% endif %}
{% if features.terminal_focus_gestures %}
        focusSequence.reset();
{% endif %}
        widgetHost.focus(VIEWPORT_ELEMENT);
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
    private static boolean isPasteShortcut(int keyCode, int modifiers) {
        return keyCode == GLFW.GLFW_KEY_V
                && (modifiers & GLFW.GLFW_MOD_CONTROL) != 0
                && (modifiers & (GLFW.GLFW_MOD_ALT | GLFW.GLFW_MOD_SUPER)) == 0;
{% endcase %}
    }

{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
    private void renderInput(PoseStack poseStack, Minecraft minecraft, int left, int width, int inputY,
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
    @ca.teamdman.sfm.common.util.MCVersionDependentBehaviour
{% case minecraft_version %}
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
    private void renderInput(GuiGraphics graphics, Minecraft minecraft, int left, int width, int inputY,
{% when "26.1.2" %}
    private void renderInput(GuiGraphicsExtractor graphics, Minecraft minecraft, int left, int width, int inputY,
{% endcase %}
{% endcase %}
                             boolean focused) {
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
        GuiComponent.fill(poseStack, bounds.x() + 4, inputY - 4, bounds.x() + bounds.width() - 4,
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
        graphics.fill(bounds.x() + 4, inputY - 4, bounds.x() + bounds.width() - 4,
{% endcase %}
                bounds.y() + bounds.height() - 4, INPUT);
        String prompt = "> " + input + (focused ? "_" : "");
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
        SFMFontUtils.draw(poseStack, minecraft.font,
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
        SFMFontUtils.draw(graphics, minecraft.font,
{% endcase %}
                minecraft.font.plainSubstrByWidth(prompt, width), left, inputY, MUTED, false);
    }

{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
{% if features.terminal_remote or features.terminal_vox_runtime %}
    private void renderLanding(
            PoseStack poseStack,
            Minecraft minecraft,
            int left,
            int width,
            int contentTop,
            int contentBottom,
            RustLandingScene landing
    ) {
        SFMFontUtils.draw(poseStack, minecraft.font,
                landing.retryConnection()
                        ? "Rust terminal presentation is unavailable"
                        : "Rust terminal is disconnected",
                left, contentTop + 8, TEXT, false);
        int nextY = contentTop + 8 + minecraft.font.lineHeight + 8;
        for (ConnectionEvent event : landing.events()) {
            String message = minecraft.font.plainSubstrByWidth(event.message(), width);
            SFMFontUtils.draw(poseStack, minecraft.font, message, left, nextY,
                    event.error() ? ERROR : MUTED, false);
            nextY += minecraft.font.lineHeight + 8;
        }
{% if features.terminal_server_lifecycle %}
        SFMScreenPanelBounds startBounds = landingStartButtonBounds(left, width, nextY, contentBottom);
        recordDisconnectedStartButtonPresentation(startBounds);
        startButton.setPanelBounds(startBounds);
{% endif %}
    }
{% endif %}

{% if features.terminal_server_lifecycle %}
    static SFMScreenPanelBounds landingStartButtonBounds(
            int left,
            int availableWidth,
            int requestedTop,
            int contentBottom
    ) {
        int buttonWidth = Math.min(180, Math.max(120, availableWidth));
        int top = Math.min(requestedTop, contentBottom - VANILLA_CONTROL_HEIGHT - 4);
        int bottom = Math.min(contentBottom, top + VANILLA_CONTROL_HEIGHT);
        return new SFMScreenPanelBounds(
                left,
                top,
                buttonWidth,
                Math.max(0, bottom - top));
    }
{% endif %}

{% if features.terminal_remote or features.terminal_vox_runtime %}
    private void renderConnectedWaitingForFrame(
            PoseStack poseStack,
            Minecraft minecraft,
            int left,
            int contentTop
    ) {
        SFMFontUtils.draw(poseStack, minecraft.font,
                "Rust terminal is connected", left, contentTop + 8, TEXT, false);
        SFMFontUtils.draw(poseStack, minecraft.font,
                "Waiting for the first terminal frame...", left,
                contentTop + 8 + minecraft.font.lineHeight + 8, MUTED, false);
    }
{% endif %}

{% if features.terminal_remote or features.terminal_vox_runtime %}
    private void observeRemoteLifecycle(SFMTerminalConnectionSnapshot connection) {
        boolean connected = connection.connected();
        if (lastObservedConnection != null && lastObservedConnection != connected) {
            if (connected) {
                recordConnectionEvent(connection.presentationReady()
                        ? "Connected to the Rust terminal server."
                        : "Transport connected; preparing the terminal presentation.", false);
                lastObservedConnectionFailure = "";
            } else {
                recordConnectionEvent("Connection lost; automatic retry remains active.", true);
            }
        }
        lastObservedConnection = connected;
        connection.failure().ifPresent(failure -> {
            String normalized = failure == null ? "" : failure.trim();
            if (!normalized.isEmpty() && !normalized.equals(lastObservedConnectionFailure)) {
                lastObservedConnectionFailure = normalized;
                recordConnectionEvent(normalized, true);
            }
        });
    }
{% endif %}

{% if features.terminal_remote or features.terminal_vox_runtime %}
    private void recordConnectionEvent(String message, boolean error) {
        String normalized = message == null ? "" : message.trim();
        if (normalized.isEmpty()) return;
        if (!connectionEvents.isEmpty()) {
            ConnectionEvent latest = connectionEvents.get(connectionEvents.size() - 1);
            if (latest.message().equals(normalized) && latest.error() == error) return;
        }
        connectionEvents.add(new ConnectionEvent(normalized, error));
        while (connectionEvents.size() > 3) connectionEvents.remove(0);
        if (terminalScene instanceof RustLandingScene landing) {
            terminalScene = new RustLandingScene(
                    connectionEvents,
                    startRequested,
                    landing.startAvailable(),
                    landing.retryConnection());
        }
    }
{% endif %}

    public RustLifecycleRequest requestStartOrRetryRustServer() {
{% if features.terminal_remote or features.terminal_vox_runtime %}
{% if features.terminal_server_lifecycle %}
        if (remoteService == null) return RustLifecycleRequest.UNAVAILABLE;
        if (startRequested) return RustLifecycleRequest.REQUEST_IN_PROGRESS;
        SFMTerminalConnectionSnapshot connection = remoteService.connectionSnapshot();
        // requestConnect() publishes this flag synchronously before its
        // background driver runs. Keep the landing control visually stable,
        // but make repeated activation authoritative and idempotent.
        if (connection.connecting()) return RustLifecycleRequest.REQUEST_IN_PROGRESS;
        if (connection.connected()) {
            if (connection.presentationReady()) return RustLifecycleRequest.ALREADY_CONNECTED;
            if (connection.failure().isEmpty()) return RustLifecycleRequest.PRESENTATION_PENDING;
            connectionStatus = "Retrying the Rust terminal connection...";
            recordConnectionEvent(connectionStatus, false);
            remoteService.reconnect();
            remoteService.requestConnect();
            transitionTerminalScene(observedTerminalScene(
                    remoteService.connectionSnapshot(), false));
            return RustLifecycleRequest.RETRYING_CONNECTION;
        }
        if (connection.presentationReady()) return RustLifecycleRequest.ALREADY_CONNECTED;
        startButtonAttemptCount++;
        startRequested = true;
        connectionStatus = "Starting the Rust terminal server...";
        recordConnectionEvent(connectionStatus, false);
        transitionTerminalScene(observedTerminalScene(connection, false));
        Minecraft currentMinecraft = minecraft;
        Thread thread = new Thread(() -> {
            try {
                rustServerStarter.start();
                remoteService.reconnect();
                remoteService.requestConnect();
                runOnMinecraftThread(currentMinecraft, () -> {
                    startRequested = false;
                    connectionStatus = "Connecting to the Rust terminal server...";
                    recordConnectionEvent(connectionStatus, false);
                    transitionTerminalScene(observedTerminalScene(
                            remoteService.connectionSnapshot(), false));
                });
            } catch (Exception error) {
                runOnMinecraftThread(currentMinecraft, () -> {
                    startRequested = false;
                    connectionStatus = "Rust terminal server could not be started";
                    String detail = error.getMessage() == null || error.getMessage().isBlank()
                            ? connectionStatus
                            : connectionStatus + ": " + error.getMessage();
                    recordConnectionEvent(detail, true);
                    transitionTerminalScene(observedTerminalScene(
                            remoteService.connectionSnapshot(), false));
                });
            }
        }, "sfm-rust-terminal-start");
        thread.setDaemon(true);
        thread.start();
        return RustLifecycleRequest.STARTING_SERVER;
{% else %}
        return RustLifecycleRequest.UNAVAILABLE;
{% endif %}
{% else %}
        return RustLifecycleRequest.UNAVAILABLE;
{% endif %}
    }

    public boolean requestStartRustServer() {
        return requestStartOrRetryRustServer().accepted();
    }

{% if features.terminal_server_lifecycle %}
    void recordDisconnectedStartButtonPresentation(SFMScreenPanelBounds buttonBounds) {
        lastDisconnectedStartButtonBounds = buttonBounds;
        startButtonHitState = StartButtonHitState.active();
    }
{% endif %}

{% if features.terminal_server_lifecycle %}
    void invalidateDisconnectedStartButtonPresentation() {
        startButtonHitState = StartButtonHitState.inactive();
    }
{% endif %}

{% if features.terminal_server_lifecycle %}
    public boolean startRequestedForAutomation() {
        return startRequested;
    }
{% endif %}

{% if features.terminal_server_lifecycle %}
    public SFMScreenPanelBounds formerStartButtonBoundsForAutomation() {
        if (lastDisconnectedStartButtonBounds == null) {
            throw new IllegalStateException("Terminal never presented a disconnected Start/Retry target");
        }
        return lastDisconnectedStartButtonBounds;
    }
{% endif %}

{% if features.terminal_server_lifecycle %}
    public long startButtonAttemptCountForAutomation() {
        return startButtonAttemptCount;
    }
{% endif %}

    public long terminalMouseDispatchCountForAutomation() {
        return terminalMouseDispatchCount;
    }

    String sceneForAutomation() {
        if (terminalScene instanceof LocalReplScene) return "local-repl";
        if (terminalScene instanceof RustLandingScene) return "rust-landing";
        return "rust-terminal";
    }

{% if features.terminal_remote or features.terminal_vox_runtime %}
    List<String> connectionEventsForAutomation() {
        return connectionEvents.stream().map(ConnectionEvent::message).toList();
    }
{% endif %}

{% if features.terminal_remote or features.terminal_vox_runtime %}
{% if features.terminal_server_lifecycle %}
    public boolean formerStartButtonRoutingReadyForAutomation() {
        return lastAcceptedFrame != null
                && lastDisconnectedStartButtonBounds != null
                && !startButtonHitState.current();
    }
{% endif %}
{% endif %}

{% endcase %}
{% if features.terminal_focus_gestures %}
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
    private void renderFocusHint(PoseStack poseStack, Minecraft minecraft, int left, int width, int contentBottom) {
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
    @ca.teamdman.sfm.common.util.MCVersionDependentBehaviour
{% case minecraft_version %}
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
    private void renderFocusHint(GuiGraphics graphics, Minecraft minecraft, int left, int width, int contentBottom) {
{% when "26.1.2" %}
    private void renderFocusHint(GuiGraphicsExtractor graphics, Minecraft minecraft, int left, int width, int contentBottom) {
{% endcase %}
{% endcase %}
        SFMTerminalFocusSequence.Hint hint = focusSequence.hint(System.nanoTime());
        if (hint == null) return;
        LocalizationEntry entry = hint.kind() == SFMTerminalFocusSequence.HintKind.ESCAPE
                ? ESCAPE_FOCUS_HINT
                : TAB_FOCUS_HINT;
        Component message = entry.getComponent(Integer.toString(hint.remaining()), FOCUS_HINT_SECONDS);
        int lineHeight = minecraft.font.lineHeight + 2;
        int y = contentBottom - lineHeight - 2;
        int boxTop = y - 4;
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
        GuiComponent.fill(poseStack, bounds.x() + 4, boxTop,
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
        graphics.fill(bounds.x() + 4, boxTop,
{% endcase %}
                bounds.x() + bounds.width() - 4, contentBottom, 0xD0101218);
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
        SFMFontUtils.draw(poseStack, minecraft.font,
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
        SFMFontUtils.draw(graphics, minecraft.font,
{% endcase %}
                minecraft.font.plainSubstrByWidth(message.getString(), width), left, y, MUTED, false);
    }
{% endif %}
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}

{% if features.terminal_presentation_actions %}
    private void layoutPresentationControl(int height) {
        if (remoteService == null) return;
        int width = Math.min(380, Math.max(150, bounds.width() - 12));
        presentationButtonRight = bounds.x() + bounds.width() - 6;
        presentationButtonLeft = presentationButtonRight - width;
        presentationButtonTop = bounds.y() + 4;
        presentationButtonBottom = presentationButtonTop + Math.max(1, height);
    }
{% endif %}

{% if features.terminal_presentation_actions %}
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
{% endif %}

{% if features.terminal_presentation_actions %}
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
{% endif %}

{% if features.terminal_presentation_actions %}
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
{% endif %}

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
{% case minecraft_version %}
{% when "1.19.4" %}
        @MCVersionDependentBehaviour
{% endcase %}
        public void setPanelBounds(SFMScreenPanelBounds bounds) {
{% case minecraft_version %}
{% when "1.19.2" %}
            this.x = bounds.x();
            this.y = bounds.y();
{% when "1.19.4" %}
            setX(bounds.x());
            setY(bounds.y());
{% endcase %}
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
{% if features.terminal_focus_gestures %}
            if (focused) focusSequence.reset();
{% endif %}
        }

        @Override
{% case minecraft_version %}
{% when "1.19.2" %}
        public void renderButton(PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
{% when "1.19.4" %}
        @MCVersionDependentBehaviour
        public void renderWidget(PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
{% endcase %}
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
{% if features.terminal_remote or features.terminal_vox_runtime %}
            if (remoteService == null) {
                focusTerminalInput();
                return true;
            }
            return SFMTerminalPanel.this.mouseClicked(mouseX, mouseY, button);
{% else %}
                focusTerminalInput();
                return true;
{% endif %}
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
{% case minecraft_version %}
{% when "1.19.2" %}
        public void updateNarration(NarrationElementOutput output) {
{% when "1.19.4" %}
        @MCVersionDependentBehaviour
        protected void updateWidgetNarration(NarrationElementOutput output) {
{% endcase %}
            output.add(NarratedElementType.TITLE, narration());
            output.add(NarratedElementType.USAGE,
                    Component.literal("Terminal input. Press Tab three times to move to panel controls."));
        }
    }
{% endcase %}
}
