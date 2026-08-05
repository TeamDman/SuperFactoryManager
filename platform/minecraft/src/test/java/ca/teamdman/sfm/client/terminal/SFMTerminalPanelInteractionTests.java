package ca.teamdman.sfm.client.terminal;

import ca.teamdman.sfm.client.screen.workspace.SFMScreenPanelBounds;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspacePanelContext;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspacePanelId;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspacePanelIntentResult;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspacePanelMetrics;
import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SFMTerminalPanelInteractionTests {
    private static final int CELL_WIDTH = 6;
    private static final int LINE_HEIGHT = 11;

    @Test
    void viewportClickLeavesPresentationControlBeforeForwardingMouseAndKeyboard() {
        RecordingRemoteService service = new RecordingRemoteService();
        SFMTerminalPanel panel = new SFMTerminalPanel(service);
        SFMScreenPanelBounds bounds = new SFMScreenPanelBounds(0, 0, 960, 540);
        panel.resizeRemoteViewport(bounds, CELL_WIDTH, LINE_HEIGHT);
        SFMTerminalPanel.ViewportGeometry viewport = remoteViewport(bounds);

        assertTrue(panel.mouseClicked(700, 10, 0), "presentation button should open the menu");
        service.clearInput();

        assertTrue(panel.mouseClicked(viewport.left() + 2, viewport.top() + 2, 0));
        assertEquals(List.of(new MouseInput(0, 0, 1, 0, true)), service.mouseInputs,
                "the viewport click must be forwarded after leaving the Java presentation control");

        assertTrue(panel.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0));
        assertEquals(List.of(new KeyInput(GLFW.GLFW_KEY_ENTER, true)), service.keyInputs,
                "keyboard input after a viewport click must return to the Rust terminal");
    }

    @Test
    void terminalPixelsInvalidateTheOldDisconnectedStartButtonBeforeMouseRouting() {
        RecordingRemoteService service = new RecordingRemoteService();
        SFMTerminalPanel panel = new SFMTerminalPanel(service, () -> {
            throw new AssertionError("stale Start/Retry hit target launched the Rust server");
        });
        SFMScreenPanelBounds bounds = new SFMScreenPanelBounds(0, 0, 960, 540);
        panel.resizeRemoteViewport(bounds, CELL_WIDTH, LINE_HEIGHT);
        SFMTerminalPanel.ViewportGeometry viewport = remoteViewport(bounds);
        SFMScreenPanelBounds oldButton = new SFMScreenPanelBounds(
                viewport.left(), viewport.top() + 30, 180, 22);
        panel.recordDisconnectedStartButtonPresentation(oldButton);

        panel.invalidateDisconnectedStartButtonPresentation();
        assertTrue(panel.mouseClicked(oldButton.x() + 2, oldButton.y() + 2,
                GLFW.GLFW_MOUSE_BUTTON_LEFT));

        assertFalse(panel.startRequestedForAutomation());
        assertEquals(List.of(new MouseInput(0, 1, 1, GLFW.GLFW_MOUSE_BUTTON_LEFT, true)),
                service.mouseInputs,
                "old Start/Retry coordinates must become ordinary terminal input");
    }

    @Test
    void disconnectedStartButtonDoesNotAcceptNonPrimaryClicks() {
        RecordingRemoteService service = new RecordingRemoteService();
        SFMTerminalPanel panel = new SFMTerminalPanel(service, () -> {
            throw new AssertionError("non-primary click launched the Rust server");
        });
        SFMScreenPanelBounds bounds = new SFMScreenPanelBounds(0, 0, 960, 540);
        panel.resizeRemoteViewport(bounds, CELL_WIDTH, LINE_HEIGHT);
        SFMTerminalPanel.ViewportGeometry viewport = remoteViewport(bounds);
        SFMScreenPanelBounds button = new SFMScreenPanelBounds(
                viewport.left(), viewport.top() + 30, 180, 22);
        panel.recordDisconnectedStartButtonPresentation(button);

        assertTrue(panel.mouseClicked(button.x() + 2, button.y() + 2,
                GLFW.GLFW_MOUSE_BUTTON_RIGHT));

        assertFalse(panel.startRequestedForAutomation());
        assertEquals(1, service.mouseInputs.size(),
                "a non-primary click must bypass Start/Retry and retain terminal routing");
        assertEquals(GLFW.GLFW_MOUSE_BUTTON_RIGHT, service.mouseInputs.get(0).button());
    }

    @Test
    void presentationHeadersAndPanelChromeDoNotBecomeTerminalMouseInput() {
        RecordingRemoteService service = new RecordingRemoteService();
        SFMTerminalPanel panel = new SFMTerminalPanel(service);
        SFMScreenPanelBounds bounds = new SFMScreenPanelBounds(0, 0, 960, 540);
        panel.resizeRemoteViewport(bounds, CELL_WIDTH, LINE_HEIGHT);

        assertTrue(panel.mouseClicked(700, 10, 0), "presentation button should open the menu");
        service.clearInput();

        assertTrue(panel.mouseClicked(700, 22, 0), "renderer menu header should remain a Java control");
        assertFalse(panel.mouseClicked(8, 20, 0), "title chrome is outside the terminal viewport");
        assertTrue(service.mouseInputs.isEmpty(), "menu headers and panel chrome must not reach the PTY");

        assertTrue(panel.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0));
        assertTrue(service.keyInputs.isEmpty(),
                "non-viewport clicks must not silently transfer presentation-control focus");
    }

    @Test
    void firstTwoEscapesReachThePtyAndTheThirdFallsThroughToTheWorkspace() {
        RecordingRemoteService service = new RecordingRemoteService();
        SFMTerminalPanel panel = new SFMTerminalPanel(service);

        assertTrue(panel.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0));
        assertTrue(panel.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0));
        assertFalse(panel.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0));

        assertEquals(List.of(
                new KeyInput(GLFW.GLFW_KEY_ESCAPE, true),
                new KeyInput(GLFW.GLFW_KEY_ESCAPE, true)
        ), service.keyInputs);
    }

    @Test
    void ctrlCCopiesSelectionAndSuppressesThePhysicalKeyRelease() {
        RecordingRemoteService service = new RecordingRemoteService();
        service.copyResult = new SFMTerminalCopyResult(
                SFMTerminalCopyResult.Disposition.COPIED, "selected");
        SFMTerminalPanel panel = new SFMTerminalPanel(service);

        assertTrue(panel.keyPressed(GLFW.GLFW_KEY_C, 0, GLFW.GLFW_MOD_CONTROL));
        assertTrue(panel.keyReleased(GLFW.GLFW_KEY_C, 0, GLFW.GLFW_MOD_CONTROL));

        assertEquals(1, service.copyRequests);
        assertTrue(service.keyInputs.isEmpty(),
                "copying a selection must not also send Ctrl+C to the PTY");
    }

    @Test
    void ctrlCWithoutSelectionForwardsOneCompleteInterruptChord() {
        RecordingRemoteService service = new RecordingRemoteService();
        SFMTerminalPanel panel = new SFMTerminalPanel(service);

        assertTrue(panel.keyPressed(GLFW.GLFW_KEY_C, 0, GLFW.GLFW_MOD_CONTROL));
        assertTrue(panel.keyReleased(GLFW.GLFW_KEY_C, 0, GLFW.GLFW_MOD_CONTROL));

        assertEquals(List.of(
                new KeyInput(GLFW.GLFW_KEY_C, true),
                new KeyInput(GLFW.GLFW_KEY_C, false)
        ), service.keyInputs);
    }

    @Test
    void rightClickRespectsChildMouseReportingBeforeCopying() {
        RecordingRemoteService service = new RecordingRemoteService();
        service.mouseDisposition = SFMTerminalInputDisposition.FORWARDED;
        SFMTerminalPanel panel = new SFMTerminalPanel(service);
        SFMScreenPanelBounds bounds = new SFMScreenPanelBounds(0, 0, 960, 540);
        panel.resizeRemoteViewport(bounds, CELL_WIDTH, LINE_HEIGHT);
        SFMTerminalPanel.ViewportGeometry viewport = remoteViewport(bounds);

        assertTrue(panel.mouseClicked(viewport.left() + 2, viewport.top() + 2,
                GLFW.GLFW_MOUSE_BUTTON_RIGHT));
        assertEquals(0, service.copyRequests);
        assertEquals(1, service.mouseInputs.size());
    }

    @Test
    void ordinaryShellRightClickUsesTheAuthoritativeCopyPath() {
        RecordingRemoteService service = new RecordingRemoteService();
        service.copyResult = new SFMTerminalCopyResult(
                SFMTerminalCopyResult.Disposition.COPIED, "selected");
        SFMTerminalPanel panel = new SFMTerminalPanel(service);
        SFMScreenPanelBounds bounds = new SFMScreenPanelBounds(0, 0, 960, 540);
        panel.resizeRemoteViewport(bounds, CELL_WIDTH, LINE_HEIGHT);
        SFMTerminalPanel.ViewportGeometry viewport = remoteViewport(bounds);

        assertTrue(panel.mouseClicked(viewport.left() + 2, viewport.top() + 2,
                GLFW.GLFW_MOUSE_BUTTON_RIGHT));
        assertEquals(1, service.copyRequests);
    }

    @Test
    void automationPasteUsesTheGuardedPasteRpcInsteadOfRawTextInput() {
        RecordingRemoteService service = new RecordingRemoteService();
        SFMTerminalPanel panel = new SFMTerminalPanel(service);

        panel.pasteForAutomation("safe single line");

        assertEquals(List.of("safe single line"), service.guardedPastes);
    }

    @Test
    void multilinePasteWritesNothingUntilTheMatchingConfirmationIsApprovedOnce() {
        RecordingRemoteService service = new RecordingRemoteService();
        service.pasteResult = new SFMTerminalPasteResult(
                SFMTerminalPasteResult.Disposition.CONFIRMATION_REQUIRED,
                "99\n100",
                "paste-1");
        SFMTerminalPanel panel = new SFMTerminalPanel(service);

        panel.pasteForAutomation("99\n100");

        assertEquals(Optional.of("paste-1"), panel.pendingPasteContentIdForAutomation());
        assertEquals(Optional.of("99\n100"), panel.pendingPastePreviewForAutomation());
        assertTrue(service.approvedPastes.isEmpty(), "guarded paste must not write before approval");

        panel.resolvePendingPasteForAutomation("paste-1", true);
        panel.resolvePendingPasteForAutomation("paste-1", true);

        assertEquals(List.of(new ApprovedPaste("99\n100", "paste-1")), service.approvedPastes);
        assertTrue(panel.pendingPasteContentIdForAutomation().isEmpty());
    }

    @Test
    void cancelAndStaleConfirmationNeverReleaseMultilinePaste() {
        RecordingRemoteService service = new RecordingRemoteService();
        service.pasteResult = new SFMTerminalPasteResult(
                SFMTerminalPasteResult.Disposition.CONFIRMATION_REQUIRED,
                "99\n100",
                "paste-1");
        SFMTerminalPanel panel = new SFMTerminalPanel(service);

        panel.pasteForAutomation("99\n100");
        panel.resolvePendingPasteForAutomation("wrong-id", true);
        assertEquals(Optional.of("paste-1"), panel.pendingPasteContentIdForAutomation());
        assertTrue(service.approvedPastes.isEmpty());

        panel.resolvePendingPasteForAutomation("paste-1", false);
        assertTrue(service.approvedPastes.isEmpty());
        assertTrue(panel.pendingPasteContentIdForAutomation().isEmpty());
    }

    @Test
    void reconnectOrDisconnectInvalidatesPendingConfirmation() {
        RecordingRemoteService service = new RecordingRemoteService();
        service.pasteResult = new SFMTerminalPasteResult(
                SFMTerminalPasteResult.Disposition.CONFIRMATION_REQUIRED,
                "99\n100",
                "paste-1");
        SFMTerminalPanel panel = new SFMTerminalPanel(service);

        panel.pasteForAutomation("99\n100");
        service.interactionEpoch++;
        panel.resolvePendingPasteForAutomation("paste-1", true);
        assertTrue(service.approvedPastes.isEmpty());

        panel.pasteForAutomation("99\n100");
        service.connected = false;
        panel.resolvePendingPasteForAutomation("paste-1", true);
        assertTrue(service.approvedPastes.isEmpty());
    }

    @Test
    void normalGuiScaleResizeUsesTheExactPaddedTitleAdjustedNativeViewport() {
        SFMScreenPanelBounds bounds = new SFMScreenPanelBounds(0, 0, 960, 540);
        SFMTerminalPanel.ViewportGeometry viewport = remoteViewport(bounds);
        RecordingRemoteService service = resizePanel(bounds);

        assertEquals(new SFMTerminalPanel.ViewportGeometry(
                8, 23, 944, 509, 536, 521, 11, 157, 46), viewport);
        assertEquals(new ResizeRequest(
                viewport.columns(), viewport.rows(), viewport.width(), viewport.height()),
                service.resizeRequests.get(0));
    }

    @Test
    void unhostedResizeFallsBackToTheLogicalDrawViewport() {
        SFMScreenPanelBounds bounds = new SFMScreenPanelBounds(0, 0, 547, 303);
        SFMTerminalPanel.ViewportGeometry viewport = remoteViewport(bounds);
        RecordingRemoteService service = resizePanel(bounds);

        assertEquals(new SFMTerminalPanel.ViewportGeometry(
                8, 23, 531, 272, 299, 284, 11, 88, 24), viewport);
        ResizeRequest request = service.resizeRequests.get(0);
        assertEquals(viewport.width(), request.pixelWidth());
        assertEquals(viewport.height(), request.pixelHeight());
        assertEquals(viewport.columns(), request.columns());
        assertEquals(viewport.rows(), request.rows());
    }

    @Test
    void hostedHighGuiScaleUsesTheMeasuredPhysicalViewport() {
        SFMScreenPanelBounds logicalViewport = new SFMScreenPanelBounds(8, 23, 766, 409);
        SFMScreenPanelBounds physicalViewport = new SFMScreenPanelBounds(40, 115, 3830, 2045);
        SFMWorkspacePanelContext context = new SFMWorkspacePanelContext(
                new SFMWorkspacePanelId(1),
                new ca.teamdman.sfm.client.screen.workspace.SFMWorkspacePanelHost() {
                    @Override
                    public SFMWorkspacePanelIntentResult submit(
                            SFMWorkspacePanelId source,
                            ca.teamdman.sfm.client.screen.workspace.SFMWorkspacePanelIntent intent
                    ) {
                        return SFMWorkspacePanelIntentResult.APPLIED;
                    }

                    @Override
                    public Optional<SFMWorkspacePanelMetrics> measure(
                            SFMWorkspacePanelId source,
                            SFMScreenPanelBounds requested
                    ) {
                        assertEquals(logicalViewport, requested);
                        return Optional.of(new SFMWorkspacePanelMetrics(
                                requested,
                                logicalViewport,
                                physicalViewport,
                                1.0D,
                                0,
                                5.0D,
                                5.0D,
                                3840,
                                2045,
                                768,
                                409
                        ));
                    }
                }
        );

        assertEquals(physicalViewport, SFMTerminalPanel.rasterTarget(context, logicalViewport));
    }

    @Test
    void physicalTargetClampPreservesAspectRatio() {
        assertEquals(
                new SFMScreenPanelBounds(10, 20, 4096, 1024),
                SFMTerminalPanel.boundedRasterTarget(new SFMScreenPanelBounds(10, 20, 8192, 2048))
        );
    }

    @Test
    void typedTuningActionsShareOnePanelLocalRequestPath() {
        RecordingRemoteService service = new RecordingRemoteService();
        SFMTerminalPanel panel = new SFMTerminalPanel(service);
        panel.resizeRemoteViewport(new SFMScreenPanelBounds(0, 0, 960, 540), CELL_WIDTH, LINE_HEIGHT);

        assertTrue(panel.requestTuning(SFMTerminalTuningOperation.SURFACE_SET, 800, 600).accepted());
        assertTrue(panel.requestTuning(SFMTerminalTuningOperation.FONT_SET, 24, 0).accepted());
        assertTrue(panel.requestTuning(SFMTerminalTuningOperation.CELLS_SET, 80, 25).accepted());

        ResizeRequest latest = service.resizeRequests.get(service.resizeRequests.size() - 1);
        assertEquals(new ResizeRequest(80, 25, 800, 600), latest);
        assertEquals(24, service.lastFontPixelSize);
        assertEquals(new SFMTerminalTuningSettings(800, 600, 24, 80, 25),
                panel.propertiesSnapshot().requested());
    }

    @Test
    void invalidLocalTuningRetainsThePreviousRequest() {
        RecordingRemoteService service = new RecordingRemoteService();
        SFMTerminalPanel panel = new SFMTerminalPanel(service);
        panel.resizeRemoteViewport(new SFMScreenPanelBounds(0, 0, 960, 540), CELL_WIDTH, LINE_HEIGHT);
        assertTrue(panel.requestTuning(SFMTerminalTuningOperation.FONT_SET, 24, 0).accepted());

        SFMTerminalTuningChangeResult rejected =
                panel.requestTuning(SFMTerminalTuningOperation.FONT_SET, 7, 0);

        assertFalse(rejected.accepted());
        assertEquals(24, panel.propertiesSnapshot().requested().fontPixelSize());
        assertEquals(24, service.lastFontPixelSize);
    }

    @Test
    void asynchronousRustRejectionRollsBackWithoutClosingTheTerminal() {
        RecordingRemoteService service = new RecordingRemoteService();
        SFMTerminalPanel panel = new SFMTerminalPanel(service);
        panel.resizeRemoteViewport(new SFMScreenPanelBounds(0, 0, 960, 540), CELL_WIDTH, LINE_HEIGHT);
        assertTrue(panel.requestTuning(SFMTerminalTuningOperation.SURFACE_SET, 100, 100).accepted());
        service.tuningFailure = SFMTerminalTuningRejection.localInvalid(
                "exact font cannot fit requested surface",
                "surface=100x100, font=24, cells=auto");

        panel.tick();

        assertEquals(SFMTerminalTuningSettings.automatic(), panel.propertiesSnapshot().requested());
        assertTrue(panel.propertiesSnapshot().lastRejection().contains("cannot fit"));
        ResizeRequest rollback = service.resizeRequests.get(service.resizeRequests.size() - 1);
        SFMTerminalPanel.ViewportGeometry viewport = remoteViewport(new SFMScreenPanelBounds(0, 0, 960, 540));
        assertEquals(new ResizeRequest(viewport.columns(), viewport.rows(), viewport.width(), viewport.height()), rollback);
    }

    @Test
    void successfulTypedTuningRemainsPendingUntilTheRemoteResizeIsAccepted() {
        RecordingRemoteService service = new RecordingRemoteService();
        SFMTerminalPanel panel = new SFMTerminalPanel(service);
        panel.resizeRemoteViewport(new SFMScreenPanelBounds(0, 0, 960, 540), CELL_WIDTH, LINE_HEIGHT);
        service.tuningPending = true;

        assertTrue(panel.requestTuning(SFMTerminalTuningOperation.SURFACE_SET, 800, 500).accepted());
        panel.tick();
        assertTrue(panel.propertiesSnapshot().tuningPending());
        assertEquals(SFMTerminalTuningSettings.automatic(), panel.propertiesSnapshot().accepted());

        service.tuningPending = false;
        panel.tick();
        assertFalse(panel.propertiesSnapshot().tuningPending());
        assertEquals(new SFMTerminalTuningSettings(800, 500, 0, 0, 0),
                panel.propertiesSnapshot().accepted());
    }

    @Test
    void propertiesPanelKeepsItsStableOwnerIdentity() {
        SFMTerminalPanel owner = new SFMTerminalPanel(new RecordingRemoteService());
        SFMTerminalPanel unrelated = new SFMTerminalPanel(new RecordingRemoteService());
        SFMWorkspacePanelId ownerId = new SFMWorkspacePanelId(11);
        SFMTerminalPropertiesPanel properties = new SFMTerminalPropertiesPanel(ownerId);
        SFMWorkspacePanelContext context = new SFMWorkspacePanelContext(
                new SFMWorkspacePanelId(12),
                new ca.teamdman.sfm.client.screen.workspace.SFMWorkspacePanelHost() {
                    @Override
                    public SFMWorkspacePanelIntentResult submit(
                            SFMWorkspacePanelId source,
                            ca.teamdman.sfm.client.screen.workspace.SFMWorkspacePanelIntent intent
                    ) {
                        return SFMWorkspacePanelIntentResult.APPLIED;
                    }

                    @Override
                    public Optional<ca.teamdman.sfm.client.screen.workspace.SFMScreenPanel> panel(
                            SFMWorkspacePanelId requested
                    ) {
                        return Optional.of(requested.equals(ownerId) ? owner : unrelated);
                    }
                }
        );
        properties.opened(null, new SFMScreenPanelBounds(0, 0, 100, 100), context);

        assertEquals(Optional.of(owner), properties.ownerTerminal());
    }

    private static RecordingRemoteService resizePanel(SFMScreenPanelBounds bounds) {
        RecordingRemoteService service = new RecordingRemoteService();
        new SFMTerminalPanel(service).resizeRemoteViewport(bounds, CELL_WIDTH, LINE_HEIGHT);
        return service;
    }

    private static SFMTerminalPanel.ViewportGeometry remoteViewport(SFMScreenPanelBounds bounds) {
        return SFMTerminalPanel.viewportGeometry(bounds, CELL_WIDTH, LINE_HEIGHT, true);
    }

    private record ResizeRequest(int columns, int rows, int pixelWidth, int pixelHeight) {
    }

    private record KeyInput(int keyCode, boolean pressed) {
    }

    private record MouseInput(int x, int y, int buttons, int button, boolean pressed) {
    }

    private record ApprovedPaste(String text, String contentId) {
    }

    private static final class RecordingRemoteService implements SFMTerminalRemoteService {
        private final List<ResizeRequest> resizeRequests = new ArrayList<>();
        private final List<KeyInput> keyInputs = new ArrayList<>();
        private final List<MouseInput> mouseInputs = new ArrayList<>();
        private int lastFontPixelSize;
        private SFMTerminalTuningRejection tuningFailure;
        private boolean tuningPending;
        private int copyRequests;
        private SFMTerminalCopyResult copyResult = new SFMTerminalCopyResult(
                SFMTerminalCopyResult.Disposition.NO_SELECTION, "");
        private SFMTerminalInputDisposition mouseDisposition = SFMTerminalInputDisposition.NO_CHANGE;
        private final List<String> guardedPastes = new ArrayList<>();
        private final List<ApprovedPaste> approvedPastes = new ArrayList<>();
        private SFMTerminalPasteResult pasteResult = new SFMTerminalPasteResult(
                SFMTerminalPasteResult.Disposition.PASTED, "", "");
        private boolean connected = true;
        private long interactionEpoch;

        @Override
        public SFMTerminalSession openSession() {
            return new SFMTerminalSession() {
                @Override
                public SFMTerminalResponse execute(String command) {
                    return SFMTerminalResponse.ok(List.of(), "test");
                }

                @Override
                public String workingDirectory() {
                    return "test";
                }
            };
        }

        @Override
        public void requestConnect() {
        }

        @Override
        public boolean isConnected() {
            return connected;
        }

        @Override
        public long interactionEpoch() {
            return interactionEpoch;
        }

        @Override
        public boolean isConnecting() {
            return false;
        }

        @Override
        public Optional<String> failureMessage() {
            return Optional.empty();
        }

        @Override
        public boolean resize(int columns, int rows) {
            return resize(columns, rows, 0, 0);
        }

        @Override
        public boolean resize(int columns, int rows, int panelWidth, int panelHeight) {
            resizeRequests.add(new ResizeRequest(columns, rows, panelWidth, panelHeight));
            return true;
        }

        @Override
        public boolean resize(int columns, int rows, int panelWidth, int panelHeight, int fontPixelSize) {
            lastFontPixelSize = fontPixelSize;
            return resize(columns, rows, panelWidth, panelHeight);
        }

        @Override
        public Optional<SFMTerminalTuningRejection> tuningFailure() {
            return Optional.ofNullable(tuningFailure);
        }

        @Override
        public boolean tuningPending() {
            return tuningPending;
        }

        @Override
        public boolean sendKey(int keyCode, int modifiers, boolean pressed, boolean repeat) {
            keyInputs.add(new KeyInput(keyCode, pressed));
            return true;
        }

        @Override
        public boolean sendText(String text) {
            return true;
        }

        @Override
        public boolean sendMouse(int x, int y, int buttons, int button, boolean pressed,
                                 boolean motion, int wheelX, int wheelY) {
            mouseInputs.add(new MouseInput(x, y, buttons, button, pressed));
            return true;
        }

        @Override
        public boolean sendMouse(
                int x,
                int y,
                int buttons,
                int button,
                boolean pressed,
                boolean motion,
                int wheelX,
                int wheelY,
                java.util.function.Consumer<SFMTerminalInputDisposition> completion
        ) {
            boolean accepted = sendMouse(x, y, buttons, button, pressed, motion, wheelX, wheelY);
            if (accepted) completion.accept(mouseDisposition);
            return accepted;
        }

        @Override
        public boolean copySelection(java.util.function.Consumer<SFMTerminalCopyResult> completion) {
            copyRequests++;
            completion.accept(copyResult);
            return true;
        }

        @Override
        public boolean pasteWithGuard(
                String text,
                java.util.function.Consumer<SFMTerminalPasteResult> completion
        ) {
            guardedPastes.add(text);
            completion.accept(pasteResult);
            return true;
        }

        @Override
        public boolean pasteWithoutGuard(
                String text,
                String approvedContentId,
                java.util.function.Consumer<SFMTerminalPasteResult> completion
        ) {
            approvedPastes.add(new ApprovedPaste(text, approvedContentId));
            completion.accept(new SFMTerminalPasteResult(
                    SFMTerminalPasteResult.Disposition.PASTED, "", ""));
            return true;
        }

        @Override
        public Optional<SFMTerminalFrame> latestFrame() {
            return Optional.empty();
        }

        @Override
        public int logicalWidth() {
            return 80;
        }

        @Override
        public int logicalHeight() {
            return 24;
        }

        @Override
        public String contentForAutomation() {
            return "";
        }

        @Override
        public boolean cancel() {
            return true;
        }

        @Override
        public void reconnect() {
        }

        @Override
        public void close() {
        }

        private void clearInput() {
            keyInputs.clear();
            mouseInputs.clear();
        }
    }
}
