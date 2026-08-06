package ca.teamdman.sfm.client.terminal;

import ca.teamdman.sfm.client.screen.SFMFontUtils;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenPanel;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenPanelBounds;
import ca.teamdman.sfm.client.screen.workspace.SFMPanelActionButton;
import ca.teamdman.sfm.client.screen.workspace.SFMPanelActionExecution;
import ca.teamdman.sfm.client.screen.workspace.SFMPanelWidgetHost;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspacePanelContext;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspacePanelId;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspacePanelMetrics;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import ca.teamdman.sfm.SFM;
import org.lwjgl.glfw.GLFW;

import java.util.EnumMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

/** Diagnostics and direct controls for one exact terminal panel identity. */
public final class SFMTerminalPropertiesPanel implements SFMScreenPanel {
    private static final int BACKGROUND = 0xF0101218;
    private static final int TEXT = 0xFFE8F0F2;
    private static final int MUTED = 0xFF8AA0A8;
    private static final int ERROR = 0xFFFF7777;
    private static final ResourceLocation DEFAULT_USAGE = new ResourceLocation(SFM.MOD_ID, "default");
    private static final int PADDING = 8;
    private final SFMWorkspacePanelId ownerPanelId;
    private final SFMPanelWidgetHost widgetHost = new SFMPanelWidgetHost();
    private final Map<SFMTerminalTuningOperation, SFMPanelActionButton> controls =
            new EnumMap<>(SFMTerminalTuningOperation.class);
    private final List<SFMPanelActionButton> orderedControls = new ArrayList<>();
    private final Map<SFMTerminalTuningOperation, SFMScreenPanelBounds> controlBounds =
            new EnumMap<>(SFMTerminalTuningOperation.class);
    private final Consumer<String> actionOverride;
    private SFMWorkspacePanelContext context;
    private Minecraft minecraft;
    private int scrollOffset;
    private int contentHeight;
    private int viewportHeight;

    public SFMTerminalPropertiesPanel(SFMWorkspacePanelId ownerPanelId) {
        this(ownerPanelId, null);
    }

    SFMTerminalPropertiesPanel(SFMWorkspacePanelId ownerPanelId, Consumer<String> actionOverride) {
        this.ownerPanelId = Objects.requireNonNull(ownerPanelId);
        this.actionOverride = actionOverride;
        for (SFMTerminalTuningOperation operation : List.of(
                SFMTerminalTuningOperation.SURFACE_WIDTH_DECREASE,
                SFMTerminalTuningOperation.SURFACE_WIDTH_INCREASE,
                SFMTerminalTuningOperation.SURFACE_HEIGHT_DECREASE,
                SFMTerminalTuningOperation.SURFACE_HEIGHT_INCREASE,
                SFMTerminalTuningOperation.SURFACE_AUTO,
                SFMTerminalTuningOperation.FONT_DECREASE,
                SFMTerminalTuningOperation.FONT_INCREASE,
                SFMTerminalTuningOperation.FONT_AUTO,
                SFMTerminalTuningOperation.COLUMNS_DECREASE,
                SFMTerminalTuningOperation.COLUMNS_INCREASE,
                SFMTerminalTuningOperation.ROWS_DECREASE,
                SFMTerminalTuningOperation.ROWS_INCREASE,
                SFMTerminalTuningOperation.CELLS_AUTO
        )) {
            String draft = actionDraft(operation);
            SFMPanelActionButton button = new SFMPanelActionButton(
                    new ResourceLocation(SFM.MOD_ID,
                            "terminal/properties/" + operation.name().toLowerCase(java.util.Locale.ROOT)),
                    DEFAULT_USAGE,
                    Component.literal(operation.name()),
                    () -> Component.literal(actionTitle(operation)),
                    () -> draft,
                    () -> executePanelAction(draft),
                    (keyCode, scanCode, modifiers) -> false,
                    focused -> {
                        if (focused) reveal(operation);
                    }
            );
            controls.put(operation, button);
            orderedControls.add(button);
        }
        widgetHost.setChildren(orderedControls);
    }

    public SFMWorkspacePanelId ownerPanelId() {
        return ownerPanelId;
    }

    @Override
    public Component title() {
        return Component.literal("Terminal properties");
    }

    @Override
    public Optional<SFMPanelWidgetHost> widgetHost() {
        return Optional.of(widgetHost);
    }

    @Override
    public void opened(Minecraft minecraft, SFMScreenPanelBounds bounds, SFMWorkspacePanelContext context) {
        this.minecraft = minecraft;
        this.context = context;
    }

    @Override
    public void closed() {
        context = null;
        minecraft = null;
        controlBounds.clear();
    }

    public Optional<SFMTerminalPanel> ownerTerminal() {
        return context == null
                ? Optional.empty()
                : context.panel(ownerPanelId).filter(SFMTerminalPanel.class::isInstance)
                        .map(SFMTerminalPanel.class::cast);
    }

    public SFMTerminalTuningChangeResult requestTuning(
            SFMTerminalTuningOperation operation,
            int first,
            int second
    ) {
        return ownerTerminal()
                .map(terminal -> terminal.requestTuning(operation, first, second))
                .orElseGet(() -> SFMTerminalTuningChangeResult.rejected(
                        "The terminal owned by this properties panel is no longer available"));
    }

    /** Exposes one rendered control rectangle so puppets can use the real mouse path. */
    public Optional<SFMScreenPanelBounds> controlBoundsForAutomation(
            SFMTerminalTuningOperation operation
    ) {
        return Optional.ofNullable(controlBounds.get(operation));
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
        GuiComponent.fill(poseStack, bounds.x(), bounds.y(),
                bounds.x() + bounds.width(), bounds.y() + bounds.height(), BACKGROUND);
        controls.values().forEach(control -> control.visible = false);
        controlBounds.clear();
        int line = Math.max(10, minecraft.font.lineHeight + 2);
        int x = bounds.x() + PADDING;
        int y = bounds.y() + PADDING - scrollOffset;
        SFMFontUtils.draw(poseStack, minecraft.font, "Terminal properties · owner " + ownerPanelId.value(),
                x, y, TEXT, false);
        y += line + 2;

        Optional<SFMTerminalPanel> terminal = ownerTerminal();
        if (terminal.isEmpty()) {
            SFMFontUtils.draw(poseStack, minecraft.font,
                    "Owner terminal closed; controls remain intentionally detached.", x, y, ERROR, false);
            return;
        }

        SFMTerminalPropertiesSnapshot snapshot = terminal.get().propertiesSnapshot();
        SFMTerminalTuningSettings requested = snapshot.requested();
        SFMTerminalTuningSettings.Effective effective = snapshot.effective();
        y = drawLine(poseStack, minecraft, x, y, "GUI scale",
                snapshot.configuredGuiScale() + " configured · "
                        + snapshot.effectiveGuiScale() + " effective", MUTED, line);
        y = drawLine(poseStack, minecraft, x, y, "Panel logical", bounds(snapshot.panelLogicalBounds()), MUTED, line);
        y = drawLine(poseStack, minecraft, x, y, "Viewport logical", bounds(snapshot.viewportLogicalBounds()), MUTED, line);
        if (snapshot.panelMetrics().isPresent()) {
            SFMWorkspacePanelMetrics metrics = snapshot.panelMetrics().get();
            y = drawLine(poseStack, minecraft, x, y, "Panel global GUI",
                    bounds(metrics.globalGuiLogicalBounds()), MUTED, line);
            y = drawLine(poseStack, minecraft, x, y, "Panel physical",
                    bounds(metrics.physicalPixelBounds()), MUTED, line);
            y = drawLine(poseStack, minecraft, x, y, "Panel scale",
                    decimal(metrics.panelRenderScale()) + " · override "
                            + (metrics.panelGuiScaleOverride() == 0
                            ? "none" : metrics.panelGuiScaleOverride()), MUTED, line);
        }
        if (snapshot.workspaceMetrics().isPresent()) {
            SFMWorkspacePanelMetrics metrics = snapshot.workspaceMetrics().get();
            y = drawLine(poseStack, minecraft, x, y, "Viewport global GUI",
                    bounds(metrics.globalGuiLogicalBounds()), MUTED, line);
            y = drawLine(poseStack, minecraft, x, y, "Viewport physical",
                    bounds(metrics.physicalPixelBounds()), TEXT, line);
            y = drawLine(poseStack, minecraft, x, y, "GUI/framebuffer",
                    metrics.guiWidth() + " × " + metrics.guiHeight() + " → "
                            + metrics.framebufferWidth() + " × " + metrics.framebufferHeight(),
                    MUTED, line);
            y = drawLine(poseStack, minecraft, x, y, "GUI/physical scale",
                    decimal(metrics.guiToPhysicalScaleX()) + " × " + decimal(metrics.guiToPhysicalScaleY()),
                    MUTED, line);
        }
        y = drawLine(poseStack, minecraft, x, y, "Renderer",
                snapshot.requestedRenderer() + " → " + snapshot.activeRenderer(), TEXT, line);
        y = drawLine(poseStack, minecraft, x, y, "Transport",
                snapshot.requestedTransport() + " → " + snapshot.activeTransport(), TEXT, line);
        y = drawLine(poseStack, minecraft, x, y, "Tuning",
                snapshot.tuningPending() ? "pending" : "accepted",
                snapshot.tuningPending() ? MUTED : TEXT, line);
        y = drawLine(poseStack, minecraft, x, y, "Auto allocation",
                snapshot.automatic().surfaceWidth() + " × " + snapshot.automatic().surfaceHeight()
                        + " px · " + snapshot.automatic().columns() + " × "
                        + snapshot.automatic().rows() + " cells",
                MUTED, line);
        y += 2;

        y = drawTuningRow(poseStack, minecraft, bounds, mouseX, mouseY, x, y, line,
                "Surface width (±" + SFMTerminalTuningSettings.SURFACE_STEP + ")",
                requested.surfaceWidth(), effective.surfaceWidth(),
                SFMTerminalTuningOperation.SURFACE_WIDTH_DECREASE,
                SFMTerminalTuningOperation.SURFACE_WIDTH_INCREASE);
        y = drawTuningRow(poseStack, minecraft, bounds, mouseX, mouseY, x, y, line,
                "Surface height (±" + SFMTerminalTuningSettings.SURFACE_STEP + ")",
                requested.surfaceHeight(), effective.surfaceHeight(),
                SFMTerminalTuningOperation.SURFACE_HEIGHT_DECREASE,
                SFMTerminalTuningOperation.SURFACE_HEIGHT_INCREASE);
        y = drawAutoButton(poseStack, minecraft, bounds, mouseX, mouseY, x, y, line,
                "Use allocated surface", SFMTerminalTuningOperation.SURFACE_AUTO);
        y = drawTuningRow(poseStack, minecraft, bounds, mouseX, mouseY, x, y, line,
                "Font pixels (±" + SFMTerminalTuningSettings.FONT_STEP + ")",
                requested.fontPixelSize(), acceptedFont(snapshot),
                SFMTerminalTuningOperation.FONT_DECREASE,
                SFMTerminalTuningOperation.FONT_INCREASE);
        y = drawAutoButton(poseStack, minecraft, bounds, mouseX, mouseY, x, y, line,
                "Fit font automatically", SFMTerminalTuningOperation.FONT_AUTO);
        y = drawTuningRow(poseStack, minecraft, bounds, mouseX, mouseY, x, y, line,
                "Columns (±" + SFMTerminalTuningSettings.COLUMN_STEP + ")",
                requested.columns(), effective.columns(),
                SFMTerminalTuningOperation.COLUMNS_DECREASE,
                SFMTerminalTuningOperation.COLUMNS_INCREASE);
        y = drawTuningRow(poseStack, minecraft, bounds, mouseX, mouseY, x, y, line,
                "Rows (±" + SFMTerminalTuningSettings.ROW_STEP + ")",
                requested.rows(), effective.rows(),
                SFMTerminalTuningOperation.ROWS_DECREASE,
                SFMTerminalTuningOperation.ROWS_INCREASE);
        y = drawAutoButton(poseStack, minecraft, bounds, mouseX, mouseY, x, y, line,
                "Use allocated cell grid", SFMTerminalTuningOperation.CELLS_AUTO);

        if (snapshot.acceptedFrame().isPresent()) {
            SFMTerminalPropertiesSnapshot.AcceptedFrame frame = snapshot.acceptedFrame().get();
            y += 2;
            y = drawLine(poseStack, minecraft, x, y, "Accepted grid",
                    frame.columns() + " × " + frame.rows(), TEXT, line);
            y = drawLine(poseStack, minecraft, x, y, "Accepted target",
                    frame.targetWidth() + " × " + frame.targetHeight(), TEXT, line);
            y = drawLine(poseStack, minecraft, x, y, "Native raster",
                    frame.nativeWidth() + " × " + frame.nativeHeight()
                            + " · remainder " + frame.remainderX() + " × " + frame.remainderY(), TEXT, line);
            if (snapshot.javaDrawLogicalBounds().isPresent()) {
                y = drawLine(poseStack, minecraft, x, y, "Java draw logical",
                        bounds(snapshot.javaDrawLogicalBounds().get()), MUTED, line);
            }
            if (snapshot.javaDrawPhysicalBounds().isPresent()) {
                SFMScreenPanelBounds physicalDraw = snapshot.javaDrawPhysicalBounds().get();
                int widthDelta = physicalDraw.width() - frame.nativeWidth();
                int heightDelta = physicalDraw.height() - frame.nativeHeight();
                y = drawLine(poseStack, minecraft, x, y, "Java draw physical",
                        bounds(physicalDraw) + " · delta " + widthDelta + " × " + heightDelta,
                        widthDelta == 0 && heightDelta == 0 ? TEXT : MUTED, line);
            }
            y = drawLine(poseStack, minecraft, x, y, "Accepted font/cell",
                    frame.fontPixelSize() + " px · " + frame.cellWidth() + " × " + frame.cellHeight(),
                    TEXT, line);
            y = drawLine(poseStack, minecraft, x, y, "Frame",
                    frame.sequence() + " · " + frame.payloadBytes() + " bytes", MUTED, line);
        }
        if (snapshot.presentation().isPresent()) {
            SFMTerminalPresentationDiagnostics presentation = snapshot.presentation().get();
            y = drawLine(poseStack, minecraft, x, y, "Generation",
                    presentation.presentationGeneration() + " · frame " + presentation.frameSequence(), MUTED, line);
            y = drawLine(poseStack, minecraft, x, y, "Frame contract",
                    presentation.frameKind() + " · base " + presentation.baseFrameSequence()
                            + " · resync " + presentation.fullResync(), MUTED, line);
            y = drawLine(poseStack, minecraft, x, y, "Delivery",
                    presentation.framesAccepted() + " accepted · "
                            + presentation.framesRejected() + " rejected · "
                            + presentation.receiverFailures() + " receiver failures",
                    presentation.framesRejected() == 0 && presentation.receiverFailures() == 0 ? TEXT : ERROR,
                    line);
            y = drawLine(poseStack, minecraft, x, y, "Payload",
                    presentation.payloadBytes() + " latest · "
                            + presentation.maximumPayloadBytes() + " maximum", MUTED, line);
        }
        if (!snapshot.lastRejection().isBlank()) {
            y = drawLine(poseStack, minecraft, x, y, "Last rejection", snapshot.lastRejection(), ERROR, line);
            if (snapshot.lastTypedRejection().isPresent()) {
                SFMTerminalTuningRejection rejection = snapshot.lastTypedRejection().get();
                y = drawLine(poseStack, minecraft, x, y, "Rejected request",
                        rejection.request(), ERROR, line);
                y = drawLine(poseStack, minecraft, x, y, "Retry/server sequence",
                        rejection.error().retryable() + " / " + rejection.error().serverSequence(), ERROR, line);
            }
        }
        viewportHeight = Math.max(1, bounds.height());
        contentHeight = Math.max(viewportHeight, y + scrollOffset - bounds.y() + PADDING);
        scrollOffset = Math.max(0, Math.min(scrollOffset, Math.max(0, contentHeight - viewportHeight)));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (contentHeight <= viewportHeight) return false;
        scrollOffset = Math.max(0, Math.min(
                Math.max(0, contentHeight - viewportHeight),
                scrollOffset - (int) Math.signum(delta) * 30));
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        int maximum = Math.max(0, contentHeight - viewportHeight);
        if (keyCode == GLFW.GLFW_KEY_HOME) {
            scrollOffset = 0;
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_END) {
            scrollOffset = maximum;
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_PAGE_UP || keyCode == GLFW.GLFW_KEY_PAGE_DOWN) {
            int direction = keyCode == GLFW.GLFW_KEY_PAGE_UP ? -1 : 1;
            scrollOffset = Math.max(0, Math.min(maximum,
                    scrollOffset + direction * Math.max(30, viewportHeight - 30)));
            return true;
        }
        return false;
    }

    private int drawTuningRow(
            PoseStack poseStack,
            Minecraft minecraft,
            SFMScreenPanelBounds bounds,
            int mouseX,
            int mouseY,
            int x,
            int y,
            int line,
            String label,
            int requested,
            int effective,
            SFMTerminalTuningOperation decrease,
            SFMTerminalTuningOperation increase
    ) {
        String value = requested == 0 ? "auto → " + effective : Integer.toString(requested);
        SFMFontUtils.draw(poseStack, minecraft.font, label + ": " + value, x, y + 2, TEXT, false);
        int plusRight = bounds.x() + bounds.width() - PADDING;
        int plusLeft = plusRight - 18;
        int minusRight = plusLeft - 3;
        int minusLeft = minusRight - 18;
        addButton(poseStack, minecraft, mouseX, mouseY, minusLeft, y, minusRight, y + line + 2,
                "−", decrease);
        addButton(poseStack, minecraft, mouseX, mouseY, plusLeft, y, plusRight, y + line + 2,
                "+", increase);
        return y + line + 4;
    }

    private int drawAutoButton(
            PoseStack poseStack,
            Minecraft minecraft,
            SFMScreenPanelBounds bounds,
            int mouseX,
            int mouseY,
            int x,
            int y,
            int line,
            String label,
            SFMTerminalTuningOperation operation
    ) {
        int width = Math.min(bounds.width() - PADDING * 2, minecraft.font.width(label) + 10);
        addButton(poseStack, minecraft, mouseX, mouseY, x, y, x + Math.max(1, width), y + line + 2,
                label, operation);
        return y + line + 4;
    }

    private void addButton(
            PoseStack poseStack,
            Minecraft minecraft,
            int mouseX,
            int mouseY,
            int left,
            int top,
            int right,
            int bottom,
            String label,
            SFMTerminalTuningOperation operation
    ) {
        SFMPanelActionButton control = controls.get(operation);
        if (control == null) throw new IllegalStateException("Missing terminal tuning control " + operation);
        SFMScreenPanelBounds buttonBounds = new SFMScreenPanelBounds(
                left,
                top,
                Math.max(1, right - left),
                Math.max(1, bottom - top));
        control.setMessage(Component.literal(label));
        control.setPanelBounds(buttonBounds);
        control.visible = true;
        control.active = ownerTerminal().isPresent();
        controlBounds.put(operation, buttonBounds);
    }

    private void executePanelAction(String draft) {
        if (actionOverride != null) {
            actionOverride.accept(draft);
            return;
        }
        if (context == null || minecraft == null) return;
        SFMPanelActionExecution.execute(context, minecraft, draft, ignored -> { });
    }

    private void reveal(SFMTerminalTuningOperation operation) {
        SFMScreenPanelBounds control = controlBounds.get(operation);
        if (control == null || viewportHeight <= 0) return;
        if (control.y() < PADDING) {
            scrollOffset = Math.max(0, scrollOffset - (PADDING - control.y()));
        } else if (control.y() + control.height() > viewportHeight - PADDING) {
            int maximum = Math.max(0, contentHeight - viewportHeight);
            scrollOffset = Math.min(maximum,
                    scrollOffset + control.y() + control.height() - (viewportHeight - PADDING));
        }
    }

    static String actionDraft(SFMTerminalTuningOperation operation) {
        return "sfm action invoke sfm:terminal/properties/" + switch (operation) {
            case SURFACE_AUTO -> "surface/auto";
            case SURFACE_WIDTH_INCREASE -> "surface/width/increase";
            case SURFACE_WIDTH_DECREASE -> "surface/width/decrease";
            case SURFACE_HEIGHT_INCREASE -> "surface/height/increase";
            case SURFACE_HEIGHT_DECREASE -> "surface/height/decrease";
            case FONT_AUTO -> "font/auto";
            case FONT_INCREASE -> "font/increase";
            case FONT_DECREASE -> "font/decrease";
            case CELLS_AUTO -> "cells/auto";
            case COLUMNS_INCREASE -> "cells/columns/increase";
            case COLUMNS_DECREASE -> "cells/columns/decrease";
            case ROWS_INCREASE -> "cells/rows/increase";
            case ROWS_DECREASE -> "cells/rows/decrease";
            case SURFACE_SET, FONT_SET, CELLS_SET -> throw new IllegalArgumentException(
                    "Set operations require parameters and are not direct property buttons: " + operation);
        };
    }

    private static String actionTitle(SFMTerminalTuningOperation operation) {
        return switch (operation) {
            case SURFACE_AUTO -> "Use allocated terminal surface";
            case SURFACE_WIDTH_INCREASE -> "Increase terminal surface width";
            case SURFACE_WIDTH_DECREASE -> "Decrease terminal surface width";
            case SURFACE_HEIGHT_INCREASE -> "Increase terminal surface height";
            case SURFACE_HEIGHT_DECREASE -> "Decrease terminal surface height";
            case FONT_AUTO -> "Fit terminal font automatically";
            case FONT_INCREASE -> "Increase terminal font pixels";
            case FONT_DECREASE -> "Decrease terminal font pixels";
            case CELLS_AUTO -> "Use allocated terminal cell grid";
            case COLUMNS_INCREASE -> "Increase terminal columns";
            case COLUMNS_DECREASE -> "Decrease terminal columns";
            case ROWS_INCREASE -> "Increase terminal rows";
            case ROWS_DECREASE -> "Decrease terminal rows";
            case SURFACE_SET, FONT_SET, CELLS_SET -> throw new IllegalArgumentException(
                    "Set operations require parameters and are not direct property buttons: " + operation);
        };
    }

    private static int drawLine(
            PoseStack poseStack,
            Minecraft minecraft,
            int x,
            int y,
            String label,
            String value,
            int colour,
            int line
    ) {
        SFMFontUtils.draw(poseStack, minecraft.font, label + ": " + value, x, y, colour, false);
        return y + line;
    }

    private static String bounds(SFMScreenPanelBounds bounds) {
        return bounds.x() + "," + bounds.y() + " · " + bounds.width() + " × " + bounds.height();
    }

    private static String decimal(double value) {
        return String.format(java.util.Locale.ROOT, "%.3f", value);
    }

    private static int acceptedFont(SFMTerminalPropertiesSnapshot snapshot) {
        return snapshot.acceptedFrame()
                .map(SFMTerminalPropertiesSnapshot.AcceptedFrame::fontPixelSize)
                .filter(value -> value > 0)
                .orElse(SFMTerminalTuningSettings.MIN_FONT_PIXEL_SIZE);
    }
}
