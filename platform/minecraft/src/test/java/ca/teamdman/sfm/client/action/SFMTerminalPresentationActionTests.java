package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.client.screen.workspace.SFMScreenMultiplexer;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspaceLayout;
import ca.teamdman.sfm.client.terminal.SFMTerminalError;
import ca.teamdman.sfm.client.terminal.SFMTerminalErrorCode;
import ca.teamdman.sfm.client.terminal.SFMTerminalPanel;
import ca.teamdman.sfm.client.terminal.SFMTerminalPresentationAdvertisedMode;
import ca.teamdman.sfm.client.terminal.SFMTerminalPresentationCatalog;
import ca.teamdman.sfm.client.terminal.SFMTerminalPresentationChangeResult;
import ca.teamdman.sfm.client.terminal.SFMTerminalPresentationSelection;
import ca.teamdman.sfm.client.terminal.SFMTerminalPresentationUnavailable;
import ca.teamdman.sfm.client.terminal.SFMTerminalPresentationTransitionState;
import ca.teamdman.sfm.client.terminal.SFMTerminalRasterAlphaMode;
import ca.teamdman.sfm.client.terminal.SFMTerminalRasterColorSpace;
import ca.teamdman.sfm.client.terminal.SFMTerminalRasterEncoding;
import ca.teamdman.sfm.client.terminal.SFMTerminalRasterFrameKind;
import ca.teamdman.sfm.client.terminal.SFMTerminalRasterOrigin;
import ca.teamdman.sfm.client.terminal.SFMTerminalRasterizationOwner;
import ca.teamdman.sfm.client.terminal.SFMTerminalRemoteService;
import ca.teamdman.sfm.client.terminal.SFMTerminalRendererId;
import ca.teamdman.sfm.client.terminal.SFMTerminalRendererOption;
import ca.teamdman.sfm.client.terminal.SFMTerminalResponse;
import ca.teamdman.sfm.client.terminal.SFMTerminalService;
import ca.teamdman.sfm.client.terminal.SFMTerminalTransportId;
import ca.teamdman.sfm.client.terminal.SFMTerminalTransportOption;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SFMTerminalPresentationActionTests {
    @Test
    void actionCandidatesReadOnlyTheFocusedPanelsCachedIndependentAxes() throws Exception {
        FakeRemoteService service = new FakeRemoteService("session-left");
        SFMTerminalPanel panel = new SFMTerminalPanel(service);
        SFMScreenMultiplexer workspace = headlessWorkspace(SFMWorkspaceLayout.single(panel));
        SFMClientActionCommandTree tree = actionTree();
        SFMClientActionSource source = source(workspace);
        int rendererReadsBefore = service.rendererOptionReads.get();
        int transportReadsBefore = service.transportOptionReads.get();

        List<String> rendererSuggestions = assertTimeoutPreemptively(
                Duration.ofMillis(250),
                () -> tree.getCompletionSuggestions(tree.parse(
                        "sfm action invoke sfm:terminal/renderer/set ", source))
                        .get()
                        .getList()
                        .stream()
                        .map(suggestion -> suggestion.getText())
                        .toList());
        List<String> transportSuggestions = assertTimeoutPreemptively(
                Duration.ofMillis(250),
                () -> tree.getCompletionSuggestions(tree.parse(
                        "sfm action invoke sfm:terminal/transport/set ", source))
                        .get()
                        .getList()
                        .stream()
                        .map(suggestion -> suggestion.getText())
                        .toList());

        assertEquals(List.of("rust-cpu-fontdue", "rust-gpu-slug"), rendererSuggestions);
        assertEquals(List.of("dirty-raw-rgba", "full-png", "full-raw-rgba"), transportSuggestions);
        assertEquals(rendererReadsBefore + 1, service.rendererOptionReads.get());
        assertEquals(transportReadsBefore + 1, service.transportOptionReads.get());
        assertEquals(0, service.connectRequests.get(),
                "candidate enumeration must not initiate remote discovery or connection work");
        assertEquals(0, service.presentationRequests.get());
    }

    @Test
    void actionCandidatesSurfaceCachedNegativeCapabilityReasonsWithoutProbing() throws Exception {
        SFMTerminalPresentationSelection gpuPng = new SFMTerminalPresentationSelection(
                SFMTerminalRendererId.RUST_GPU_SLUG,
                SFMTerminalTransportId.FULL_PNG);
        FakeRemoteService service = new FakeRemoteService(
                "session-left", FakeRemoteService.NEGATIVE_GPU_CATALOG, gpuPng);
        SFMTerminalPanel panel = new SFMTerminalPanel(service);
        SFMScreenMultiplexer workspace = headlessWorkspace(SFMWorkspaceLayout.single(panel));
        SFMClientActionCommandTree tree = actionTree();
        SFMClientActionSource source = source(workspace);
        int rendererReadsBefore = service.rendererOptionReads.get();
        int transportReadsBefore = service.transportOptionReads.get();

        var rendererSuggestions = assertTimeoutPreemptively(
                Duration.ofMillis(250),
                () -> tree.getCompletionSuggestions(tree.parse(
                        "sfm action invoke sfm:terminal/renderer/set ", source))
                        .get()
                        .getList());
        var transportSuggestions = assertTimeoutPreemptively(
                Duration.ofMillis(250),
                () -> tree.getCompletionSuggestions(tree.parse(
                        "sfm action invoke sfm:terminal/transport/set ", source))
                        .get()
                        .getList());

        assertEquals("Vulkan unavailable: no compatible compute device",
                rendererSuggestions.stream()
                        .filter(suggestion -> suggestion.getText().equals("rust-gpu-slug"))
                        .findFirst()
                        .orElseThrow()
                        .getTooltip()
                        .getString());
        assertTrue(transportSuggestions.stream().allMatch(suggestion ->
                suggestion.getTooltip() != null
                        && suggestion.getTooltip().getString().equals(
                        "Vulkan unavailable: no compatible compute device")));
        assertEquals(rendererReadsBefore + 1, service.rendererOptionReads.get());
        assertEquals(transportReadsBefore + 1, service.transportOptionReads.get());
        assertEquals(0, service.connectRequests.get());
        assertEquals(0, service.presentationRequests.get());
    }

    @Test
    void focusedActionCaptureAndPanelSelectorsPreserveIndependentSessionOwnership() throws Exception {
        FakeRemoteService leftService = new FakeRemoteService("session-left");
        FakeRemoteService rightService = new FakeRemoteService("session-right");
        SFMTerminalPanel left = new SFMTerminalPanel(leftService);
        SFMTerminalPanel right = new SFMTerminalPanel(rightService);
        SFMScreenMultiplexer workspace = headlessWorkspace(
                SFMWorkspaceLayout.sideBySide(left, right));
        var leftId = workspace.panelIds().get(0);
        var rightId = workspace.panelIds().get(1);
        workspace.focusPanel(leftId);
        leftService.duringRequest = () -> workspace.focusPanel(rightId);
        SFMClientActionContext context = SFMClientActionContext.create(workspace, () -> true);
        assertEquals(workspace, new SetTerminalRendererAction()
                .requirement()
                .resolve(context)
                .target());
        SFMTerminalPanel captured = (SFMTerminalPanel) workspace.focusedPanelInstance();
        assertTrue(captured.requestRenderer("rust-gpu-slug").accepted());

        assertEquals(new SFMTerminalPresentationSelection(
                        SFMTerminalRendererId.RUST_GPU_SLUG,
                        SFMTerminalTransportId.FULL_PNG),
                leftService.requested);
        assertEquals(SFMTerminalPresentationSelection.DEFAULT, rightService.requested,
                "a focus change during the request must not redirect it to another panel");
        assertEquals("session-left", leftService.sessionOwner);
        assertEquals("session-right", rightService.sessionOwner);

        assertEquals(right, workspace.focusedPanelInstance());
        assertEquals(workspace, new SetTerminalTransportAction()
                .requirement()
                .resolve(context)
                .target());
        assertEquals(1, actionTree().execute(
                "sfm action invoke sfm:terminal/transport/set dirty-raw-rgba",
                new SFMClientActionSource(context)));
        assertEquals(new SFMTerminalPresentationSelection(
                        SFMTerminalRendererId.RUST_GPU_SLUG,
                        SFMTerminalTransportId.DIRTY_RAW_RGBA),
                leftService.requested,
                "the captured action context must not retarget after workspace focus changes");
        assertEquals(SFMTerminalPresentationSelection.DEFAULT, rightService.requested);

        assertTrue(left.requestTransport("full-raw-rgba").accepted());
        assertEquals(new SFMTerminalPresentationSelection(
                        SFMTerminalRendererId.RUST_GPU_SLUG,
                        SFMTerminalTransportId.FULL_RAW_RGBA),
                leftService.requested,
                "changing transport must retain the renderer axis");
        assertTrue(left.requestRenderer("rust-cpu-fontdue").accepted());
        assertEquals(new SFMTerminalPresentationSelection(
                        SFMTerminalRendererId.RUST_CPU_FONTDUE,
                        SFMTerminalTransportId.FULL_RAW_RGBA),
                leftService.requested,
                "changing renderer must retain the transport axis");
        assertEquals(2, left.rendererOptions().size());
        assertEquals(3, left.transportOptions().size());
        assertEquals(new SFMTerminalPresentationSelection(
                        SFMTerminalRendererId.RUST_CPU_FONTDUE,
                        SFMTerminalTransportId.FULL_PNG),
                rightService.requested,
                "left-panel selector use must not mutate the right panel");
    }

    private static SFMClientActionCommandTree actionTree() {
        return SFMClientActionDispatcherCompiler.compileCommandTree(List.of(
                Map.entry(
                        new ResourceLocation("sfm", "terminal/renderer/set"),
                        new SetTerminalRendererAction()),
                Map.entry(
                        new ResourceLocation("sfm", "terminal/transport/set"),
                        new SetTerminalTransportAction())));
    }

    private static SFMClientActionSource source(SFMScreenMultiplexer workspace) {
        AtomicBoolean current = new AtomicBoolean(true);
        return new SFMClientActionSource(SFMClientActionContext.create(workspace, current::get));
    }

    /**
     * Unit tests need the workspace's pure layout/focus model, not Minecraft's
     * Screen constructor, which requires a bootstrapped game registry.
     */
    private static SFMScreenMultiplexer headlessWorkspace(SFMWorkspaceLayout layout)
            throws Exception {
        Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        Unsafe unsafe = (Unsafe) unsafeField.get(null);
        SFMScreenMultiplexer workspace =
                (SFMScreenMultiplexer) unsafe.allocateInstance(SFMScreenMultiplexer.class);
        Field layoutField = SFMScreenMultiplexer.class.getDeclaredField("layout");
        layoutField.setAccessible(true);
        layoutField.set(workspace, layout);
        return workspace;
    }

    private static final class FakeRemoteService implements SFMTerminalRemoteService {
        private static final SFMTerminalPresentationCatalog CATALOG =
                SFMTerminalPresentationCatalog.intersect(
                        "rust-cpu-fontdue",
                        "full-png",
                        Arrays.stream(SFMTerminalRendererId.values())
                                .flatMap(renderer -> Arrays.stream(SFMTerminalTransportId.values())
                                        .map(transport -> advertised(renderer, transport)))
                                .toList());
        private static final SFMTerminalPresentationCatalog NEGATIVE_GPU_CATALOG =
                SFMTerminalPresentationCatalog.intersect(
                        "rust-cpu-fontdue",
                        "full-png",
                        Arrays.stream(SFMTerminalTransportId.values())
                                .map(transport -> advertised(
                                        SFMTerminalRendererId.RUST_CPU_FONTDUE, transport))
                                .toList(),
                        Arrays.stream(SFMTerminalTransportId.values())
                                .map(FakeRemoteService::unavailableGpu)
                                .toList());

        private final String sessionOwner;
        private final SFMTerminalPresentationCatalog catalog;
        private final AtomicInteger rendererOptionReads = new AtomicInteger();
        private final AtomicInteger transportOptionReads = new AtomicInteger();
        private final AtomicInteger connectRequests = new AtomicInteger();
        private final AtomicInteger presentationRequests = new AtomicInteger();
        private SFMTerminalPresentationSelection requested;
        private SFMTerminalPresentationSelection active =
                SFMTerminalPresentationSelection.DEFAULT;
        private Runnable duringRequest = () -> {};

        private FakeRemoteService(String sessionOwner) {
            this(sessionOwner, CATALOG, SFMTerminalPresentationSelection.DEFAULT);
        }

        private FakeRemoteService(
                String sessionOwner,
                SFMTerminalPresentationCatalog catalog,
                SFMTerminalPresentationSelection requested
        ) {
            this.sessionOwner = sessionOwner;
            this.catalog = catalog;
            this.requested = requested;
        }

        @Override
        public SFMTerminalSession openSession() {
            return new SFMTerminalSession() {
                @Override
                public SFMTerminalResponse execute(String command) {
                    return SFMTerminalResponse.ok(List.of(), "/");
                }

                @Override
                public String workingDirectory() {
                    return "/";
                }
            };
        }

        @Override
        public List<SFMTerminalRendererOption> rendererOptions() {
            rendererOptionReads.incrementAndGet();
            return catalog.rendererOptions(requested.transportId());
        }

        @Override
        public List<SFMTerminalTransportOption> transportOptions() {
            transportOptionReads.incrementAndGet();
            return catalog.transportOptions(requested.rendererId());
        }

        @Override
        public SFMTerminalPresentationTransitionState presentationState() {
            return new SFMTerminalPresentationTransitionState(
                    requested, Optional.of(active), Optional.empty());
        }

        @Override
        public SFMTerminalPresentationChangeResult requestRenderer(String rendererId) {
            presentationRequests.incrementAndGet();
            SFMTerminalRendererId renderer = SFMTerminalRendererId.fromWireId(rendererId);
            requested = requested.withRenderer(renderer);
            duringRequest.run();
            return SFMTerminalPresentationChangeResult.accepted("renderer requested");
        }

        @Override
        public SFMTerminalPresentationChangeResult requestTransport(String transportId) {
            presentationRequests.incrementAndGet();
            SFMTerminalTransportId transport = SFMTerminalTransportId.fromWireId(transportId);
            requested = requested.withTransport(transport);
            duringRequest.run();
            return SFMTerminalPresentationChangeResult.accepted("transport requested");
        }

        @Override
        public String requestedRendererId() {
            return requested.rendererId().wireId();
        }

        @Override
        public String requestedTransportId() {
            return requested.transportId().wireId();
        }

        @Override
        public Optional<String> activeRendererId() {
            return Optional.of(active.rendererId().wireId());
        }

        @Override
        public Optional<String> activeTransportId() {
            return Optional.of(active.transportId().wireId());
        }

        @Override public void requestConnect() { connectRequests.incrementAndGet(); }
        @Override public boolean isConnected() { return true; }
        @Override public boolean isConnecting() { return false; }
        @Override public Optional<String> failureMessage() { return Optional.empty(); }
        @Override public boolean resize(int columns, int rows) { return true; }
        @Override public boolean sendKey(int keyCode, int modifiers, boolean pressed, boolean repeat) { return true; }
        @Override public boolean sendText(String text) { return true; }
        @Override public boolean sendMouse(int x, int y, int buttons, int button, boolean pressed,
                                           boolean motion, int wheelX, int wheelY) { return true; }
        @Override public Optional<ca.teamdman.sfm.client.terminal.SFMTerminalFrame> latestFrame() {
            return Optional.empty();
        }
        @Override public int logicalWidth() { return 80; }
        @Override public int logicalHeight() { return 24; }
        @Override public String contentForAutomation() { return ""; }
        @Override public boolean cancel() { return true; }
        @Override public void reconnect() {}
        @Override public void close() {}

        private static SFMTerminalPresentationAdvertisedMode advertised(
                SFMTerminalRendererId renderer,
                SFMTerminalTransportId transport) {
            boolean png = transport == SFMTerminalTransportId.FULL_PNG;
            boolean dirty = transport == SFMTerminalTransportId.DIRTY_RAW_RGBA;
            return new SFMTerminalPresentationAdvertisedMode(
                    renderer.wireId(),
                    "server",
                    dirty ? "dirty" : "full",
                    transport.wireId(),
                    1,
                    png ? SFMTerminalRasterEncoding.PNG : SFMTerminalRasterEncoding.RGBA8,
                    dirty ? SFMTerminalRasterFrameKind.DIRTY_REGIONS : SFMTerminalRasterFrameKind.FULL,
                    1,
                    SFMTerminalRasterOrigin.TOP_LEFT,
                    SFMTerminalRasterAlphaMode.STRAIGHT,
                    SFMTerminalRasterColorSpace.SRGB,
                    1280,
                    720,
                    4 * 1024 * 1024L,
                    64);
        }

        private static SFMTerminalPresentationUnavailable unavailableGpu(
                SFMTerminalTransportId transport
        ) {
            return new SFMTerminalPresentationUnavailable(
                    SFMTerminalRendererId.RUST_GPU_SLUG.wireId(),
                    SFMTerminalRasterizationOwner.SERVER,
                    transport.damageModeId(),
                    transport.wireId(),
                    SFMTerminalTransportId.SUPPORTED_VERSION,
                    new SFMTerminalError(
                            SFMTerminalErrorCode.UNSUPPORTED_CAPABILITY,
                            "Vulkan unavailable: no compatible compute device",
                            false,
                            7));
        }
    }
}
