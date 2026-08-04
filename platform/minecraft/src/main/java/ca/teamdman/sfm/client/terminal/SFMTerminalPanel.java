package ca.teamdman.sfm.client.terminal;

import ca.teamdman.sfm.client.screen.SFMFontUtils;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenPanel;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenPanelBounds;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspacePanelContext;
import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.localization.LocalizationEntry;
import ca.teamdman.sfm.common.localization.SFMLocalizationDatagen;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.List;
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
    private final SFMTerminalClient client;
    private final SFMTerminalRemoteService remoteService;
    private final SFMTerminalPngRenderer pngRenderer = new SFMTerminalPngRenderer();
    private final SFMTerminalRgbaRenderer rgbaRenderer = new SFMTerminalRgbaRenderer();
    private final SFMTerminalScrollback scrollback = new SFMTerminalScrollback();
    private final SFMTerminalFocusSequence focusSequence = new SFMTerminalFocusSequence();
    private String input = "";
    private SFMScreenPanelBounds bounds = new SFMScreenPanelBounds(0, 0, 1, 1);
    private SFMWorkspacePanelContext context;
    private Minecraft minecraft;
    private int renderLeft;
    private int renderTop;
    private int renderWidth;
    private int renderHeight;
    private int pressedMouseButtons;
    private boolean suppressPasteRelease;
    private boolean startRequested;
    private String lastLoggedPresentationStreamIdentity;
    private long lastLoggedPresentationSequence = Long.MIN_VALUE;
    private String connectionStatus;
    private int startButtonLeft;
    private int startButtonTop;
    private int startButtonRight;
    private int startButtonBottom;
    private boolean presentationControlFocused;
    private boolean presentationMenuOpen;
    private PresentationAxis presentationAxis = PresentationAxis.RENDERER;
    private int rendererSelectionIndex;
    private int transportSelectionIndex;
    private int presentationButtonLeft;
    private int presentationButtonTop;
    private int presentationButtonRight;
    private int presentationButtonBottom;

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
        this(new SFMTerminalClient(service), service instanceof SFMTerminalRemoteService remote ? remote : null);
    }

    public SFMTerminalPanel(SFMTerminalClient client) {
        this(client, null);
    }

    private SFMTerminalPanel(SFMTerminalClient client, SFMTerminalRemoteService remoteService) {
        this.client = client;
        this.remoteService = remoteService;
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
        this.bounds = bounds;
        ViewportGeometry viewport = viewportGeometry(
                bounds,
                cellWidth,
                lineHeight,
                remoteService != null
        );
        applyViewport(viewport);
        layoutPresentationControl(viewport.lineHeight() + 4);
        if (remoteService != null) {
            remoteService.resize(
                    viewport.columns(),
                    viewport.rows(),
                    viewport.width(),
                    viewport.height()
            );
            remoteService.requestConnect();
        }
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
        if (remoteService != null) remoteService.requestConnect();
    }

    @Override
    public void closed() {
        if (minecraft != null) {
            pngRenderer.close(minecraft);
            rgbaRenderer.close(minecraft);
        }
        if (remoteService != null) remoteService.close();
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
            Optional<SFMTerminalFrame> frame = remoteService.latestFrame();
            boolean presented = false;
            if (frame.isPresent() || remoteService.canPresentRetainedFrame()) {
                boolean png = frame.map(SFMTerminalFrame::png)
                        .orElseGet(() -> remoteService.activeTransportId()
                                .map("full-png"::equals).orElse(true));
                presented = png
                        ? pngRenderer.render(poseStack, minecraft, left, contentTop, width,
                        renderHeight, frame)
                        : rgbaRenderer.render(poseStack, minecraft, left, contentTop, width,
                        renderHeight, frame);
            }
            if (presented) {
                if (frame.isPresent() && isNewPresentation(
                        lastLoggedPresentationStreamIdentity,
                        lastLoggedPresentationSequence,
                        frame.get())) {
                    lastLoggedPresentationStreamIdentity = frame.get().streamIdentity();
                    lastLoggedPresentationSequence = frame.get().sequence();
                    logPresentationTiming(frame.get(), pngRenderer.telemetry());
                }
                renderFocusHint(poseStack, minecraft, left, width, contentBottom);
                renderPresentationControl(poseStack, minecraft);
                return;
            }
            renderDisconnected(poseStack, minecraft, left, width, contentTop, contentBottom);
            renderPresentationControl(poseStack, minecraft);
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
        if (remoteService != null && presentationControlFocused) {
            return presentationControlKeyPressed(keyCode);
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (focusSequence.escape(System.nanoTime()) == SFMTerminalFocusSequence.Decision.EXIT) {
                if (context != null) context.submit(new ca.teamdman.sfm.client.screen.workspace.SFMWorkspacePanelIntent.Close());
                return true;
            }
            if (remoteService != null) remoteService.sendKey(keyCode, modifiers, true, false);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_TAB) {
            SFMTerminalFocusSequence.Decision decision = focusSequence.tab(System.nanoTime());
            if (decision == SFMTerminalFocusSequence.Decision.JAVA_FOCUS) {
                presentationControlFocused = true;
                presentationMenuOpen = false;
                synchronizePresentationSelection();
                return true;
            }
            if (remoteService != null) remoteService.sendKey(keyCode, modifiers, true, false);
            else input += "\t";
            return true;
        }
        if (remoteService != null && isPasteShortcut(keyCode, modifiers)) {
            suppressPasteRelease = true;
            String clipboard = minecraft == null ? "" : minecraft.keyboardHandler.getClipboard();
            if (!clipboard.isEmpty() && !remoteService.sendText(clipboard)) {
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
        if (presentationControlFocused) return true;
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
            if (presentationControlFocused) return true;
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
            remoteService.sendMouse(logicalX(mouseX), logicalY(mouseY), pressedMouseButtons, 0, false,
                    false, 0, (int) Math.round(delta));
        } else if (delta > 0) scrollback.scrollOlder(Math.max(1, (int) Math.ceil(delta)));
        else if (delta < 0) scrollback.scrollNewer(Math.max(1, (int) Math.ceil(-delta)));
        return delta != 0;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (remoteService != null && button == 0
                && mouseX >= presentationButtonLeft && mouseX < presentationButtonRight
                && mouseY >= presentationButtonTop && mouseY < presentationButtonBottom) {
            presentationControlFocused = true;
            presentationMenuOpen = !presentationMenuOpen;
            synchronizePresentationSelection();
            return true;
        }
        if (remoteService != null && button == 0 && presentationMenuOpen) {
            List<SFMTerminalRendererOption> rendererOptions = remoteService.rendererOptions();
            List<SFMTerminalTransportOption> transportOptions = remoteService.transportOptions();
            int rowHeight = minecraft == null ? 14 : minecraft.font.lineHeight + 6;
            int row = (int) ((mouseY - presentationButtonBottom) / rowHeight);
            if (mouseX >= presentationButtonLeft && mouseX < presentationButtonRight
                    && row >= 0) {
                if (row == 0) {
                    presentationAxis = PresentationAxis.RENDERER;
                    return true;
                }
                if (row <= rendererOptions.size()) {
                    presentationAxis = PresentationAxis.RENDERER;
                    rendererSelectionIndex = row - 1;
                    selectCurrentPresentationChoice();
                    return true;
                }
                int transportHeader = rendererOptions.size() + 1;
                if (row == transportHeader) {
                    presentationAxis = PresentationAxis.TRANSPORT;
                    return true;
                }
                int transportIndex = row - transportHeader - 1;
                if (transportIndex >= 0 && transportIndex < transportOptions.size()) {
                    presentationAxis = PresentationAxis.TRANSPORT;
                    transportSelectionIndex = transportIndex;
                    selectCurrentPresentationChoice();
                    return true;
                }
                return true;
            }
        }
        if (remoteService != null && mouseX >= startButtonLeft && mouseX < startButtonRight
                && mouseY >= startButtonTop && mouseY < startButtonBottom) {
            focusTerminalInput();
            startRustServer();
            return true;
        }
        // Presentation controls and their menu rows return above so they keep
        // intentional Java control focus. Panel chrome/outside clicks do not
        // synthesize PTY input or silently change the current control focus.
        if (!containsTerminalPoint(mouseX, mouseY)) return false;
        if (remoteService == null) return false;
        // A viewport click is terminal interaction even if the selector was
        // previously keyboard-focused. Restore terminal focus before the PTY
        // receives the click so subsequent keyboard input cannot be consumed
        // by a stale presentation menu.
        focusTerminalInput();
        int mask = mouseMask(button);
        pressedMouseButtons |= mask;
        remoteService.sendMouse(logicalX(mouseX), logicalY(mouseY), pressedMouseButtons, button, true,
                false, 0, 0);
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (remoteService == null || !containsTerminalPoint(mouseX, mouseY)) return false;
        pressedMouseButtons &= ~mouseMask(button);
        remoteService.sendMouse(logicalX(mouseX), logicalY(mouseY), pressedMouseButtons, button, false,
                false, 0, 0);
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (remoteService == null || !containsTerminalPoint(mouseX, mouseY)) return false;
        remoteService.sendMouse(logicalX(mouseX), logicalY(mouseY), pressedMouseButtons,
                button, true, true, 0, 0);
        return true;
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        if (remoteService != null && pressedMouseButtons != 0 && containsTerminalPoint(mouseX, mouseY)) {
            remoteService.sendMouse(logicalX(mouseX), logicalY(mouseY), pressedMouseButtons,
                    0, true, true, 0, 0);
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

    private boolean containsTerminalPoint(double mouseX, double mouseY) {
        return mouseX >= renderLeft && mouseX < renderLeft + renderWidth
                && mouseY >= renderTop && mouseY < renderTop + renderHeight;
    }

    private int logicalX(double mouseX) {
        return Math.max(0, (int) ((mouseX - renderLeft) * remoteService.logicalWidth() / Math.max(1, renderWidth)));
    }

    private int logicalY(double mouseY) {
        return Math.max(0, (int) ((mouseY - renderTop) * remoteService.logicalHeight() / Math.max(1, renderHeight)));
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

    private void focusTerminalInput() {
        presentationControlFocused = false;
        presentationMenuOpen = false;
        focusSequence.reset();
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
        startButtonLeft = left;
        startButtonTop = Math.min(nextY, contentBottom - 26);
        startButtonRight = startButtonLeft + buttonWidth;
        startButtonBottom = Math.min(contentBottom, startButtonTop + 22);
        GuiComponent.fill(poseStack, startButtonLeft, startButtonTop,
                startButtonRight, startButtonBottom, 0xFF28506A);
        String label = startRequested ? "Starting..." : "Start / Retry Rust server";
        SFMFontUtils.draw(poseStack, minecraft.font, label,
                startButtonLeft + 8, startButtonTop + 6, TEXT, false);
    }

    private void startRustServer() {
        if (startRequested || remoteService == null) return;
        startRequested = true;
        connectionStatus = "Starting the Rust terminal server...";
        Minecraft currentMinecraft = minecraft;
        Thread thread = new Thread(() -> {
            try {
                SFMTerminalServiceFactory.startRustServer(null);
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

    private void renderPresentationControl(PoseStack poseStack, Minecraft minecraft) {
        if (remoteService == null) return;
        int height = minecraft.font.lineHeight + 6;
        layoutPresentationControl(height);
        int width = presentationButtonRight - presentationButtonLeft;
        int background = presentationControlFocused ? 0xFF315568 : 0xE0223038;
        GuiComponent.fill(poseStack, presentationButtonLeft, presentationButtonTop,
                presentationButtonRight, presentationButtonBottom, background);
        SFMTerminalPresentationTransitionState state = remoteService.presentationState();
        String value = state.requested().label();
        if (state.active().isEmpty()) {
            value += " (pending)";
        } else if (!state.active().get().equals(state.requested())) {
            value = state.active().get().label() + " -> " + value + " (pending)";
        }
        if (state.failure().isPresent()) value += " !";
        String label = "Presentation: " + value + (presentationMenuOpen ? " ^" : " v");
        SFMFontUtils.draw(poseStack, minecraft.font,
                minecraft.font.plainSubstrByWidth(label, width - 8),
                presentationButtonLeft + 4, presentationButtonTop + 3, TEXT, false);
        if (!presentationMenuOpen) return;

        List<SFMTerminalRendererOption> rendererOptions = remoteService.rendererOptions();
        List<SFMTerminalTransportOption> transportOptions = remoteService.transportOptions();
        int rowTop = presentationButtonBottom;
        GuiComponent.fill(poseStack, presentationButtonLeft, rowTop,
                presentationButtonRight, rowTop + height,
                presentationAxis == PresentationAxis.RENDERER ? 0xFF315568 : 0xF018252D);
        SFMFontUtils.draw(poseStack, minecraft.font, "Renderer", presentationButtonLeft + 4,
                rowTop + 3, MUTED, false);
        rowTop += height;
        for (int index = 0; index < rendererOptions.size(); index++) {
            SFMTerminalRendererOption option = rendererOptions.get(index);
            int rowBottom = rowTop + height;
            int rowColor = presentationAxis == PresentationAxis.RENDERER
                    && index == rendererSelectionIndex ? 0xFF315568 : 0xF018252D;
            GuiComponent.fill(poseStack, presentationButtonLeft, rowTop,
                    presentationButtonRight, rowBottom, rowColor);
            int color = option.supported() ? TEXT : ERROR;
            SFMFontUtils.draw(poseStack, minecraft.font,
                    minecraft.font.plainSubstrByWidth(option.label(), width - 8),
                    presentationButtonLeft + 4, rowTop + 3, color, false);
            rowTop = rowBottom;
        }
        GuiComponent.fill(poseStack, presentationButtonLeft, rowTop,
                presentationButtonRight, rowTop + height,
                presentationAxis == PresentationAxis.TRANSPORT ? 0xFF315568 : 0xF018252D);
        SFMFontUtils.draw(poseStack, minecraft.font, "Transport", presentationButtonLeft + 4,
                rowTop + 3, MUTED, false);
        rowTop += height;
        for (int index = 0; index < transportOptions.size(); index++) {
            SFMTerminalTransportOption option = transportOptions.get(index);
            int rowBottom = rowTop + height;
            int rowColor = presentationAxis == PresentationAxis.TRANSPORT
                    && index == transportSelectionIndex ? 0xFF315568 : 0xF018252D;
            GuiComponent.fill(poseStack, presentationButtonLeft, rowTop,
                    presentationButtonRight, rowBottom, rowColor);
            int color = option.supported() ? TEXT : ERROR;
            SFMFontUtils.draw(poseStack, minecraft.font,
                    minecraft.font.plainSubstrByWidth(option.label(), width - 8),
                    presentationButtonLeft + 4, rowTop + 3, color, false);
            rowTop = rowBottom;
        }
        if (state.failure().isPresent()) {
            SFMFontUtils.draw(poseStack, minecraft.font,
                    minecraft.font.plainSubstrByWidth(state.failure().get(), width - 8),
                    presentationButtonLeft + 4, rowTop + 3, ERROR, false);
        }
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
            presentationControlFocused = false;
            presentationMenuOpen = false;
            focusSequence.reset();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            presentationMenuOpen = false;
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_LEFT || keyCode == GLFW.GLFW_KEY_RIGHT) {
            presentationAxis = keyCode == GLFW.GLFW_KEY_LEFT
                    ? PresentationAxis.RENDERER : PresentationAxis.TRANSPORT;
            presentationMenuOpen = true;
            synchronizePresentationSelection();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER
                || keyCode == GLFW.GLFW_KEY_SPACE) {
            if (!presentationMenuOpen) {
                presentationMenuOpen = true;
                synchronizePresentationSelection();
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
        SFMTerminalPresentationChangeResult result;
        if (presentationAxis == PresentationAxis.RENDERER) {
            List<SFMTerminalRendererOption> options = remoteService.rendererOptions();
            if (rendererSelectionIndex < 0 || rendererSelectionIndex >= options.size()) return;
            result = requestRenderer(options.get(rendererSelectionIndex).id().wireId());
        } else {
            List<SFMTerminalTransportOption> options = remoteService.transportOptions();
            if (transportSelectionIndex < 0 || transportSelectionIndex >= options.size()) return;
            result = requestTransport(options.get(transportSelectionIndex).id().wireId());
        }
        if (result.accepted()) presentationMenuOpen = false;
    }
}
