package ca.teamdman.sfm.client.screen;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.localization.IdeLocalizationKeys;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Matrix4f;
import net.minecraft.Util;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

// r[impl draw.screen.main]
public class SfmDrawScreen extends Screen {
    private static final double MIN_ZOOM = 0.01D;
    private static final double MAX_ZOOM = 10.0D;
    private static final double ZOOM_STEP = 1.15D;
    private static final double FREEHAND_STEP_DISTANCE_SQUARED = 36.0D;
    private static final long DOUBLE_CLICK_WINDOW_MS = 250L;
    private static final double DOUBLE_CLICK_DISTANCE_SQUARED = 25.0D;
    private static final int HOTBAR_WIDTH = 182;
    private static final int HOTBAR_HEIGHT = 22;
    private static final int HOTBAR_HEADER_HEIGHT = 12;
    private static final double HOTBAR_MIN_SCALE = 0.75D;
    private static final double HOTBAR_MAX_SCALE = 2.0D;
    private static final int HOTBAR_RESIZE_HANDLE_SIZE = 10;
    private static final int HOTBAR_SLOT_COUNT = 9;
    private static final int TOOL_COUNT = 9;
    private static final int AUXILIARY_HOTBAR_WIDTH = 22;
    private static final int AUXILIARY_HOTBAR_HEIGHT = 22;
    private static final double TEXT_WIDGET_MIN_SCALE = 0.65D;
    private static final double TEXT_WIDGET_MAX_SCALE = 3.0D;
    private static final int MINIMAP_WIDTH = 150;
    private static final int MINIMAP_HEIGHT = 110;
    private static final int MINIMAP_MIN_WIDTH = 110;
    private static final int MINIMAP_MIN_HEIGHT = 80;
    private static final int MINIMAP_RESIZE_HANDLE_SIZE = 10;
    private static final int LAYER_WINDOW_DEFAULT_WIDTH = 160;
    private static final int LAYER_WINDOW_DEFAULT_HEIGHT = 118;
    private static final int LAYER_WINDOW_MIN_WIDTH = 132;
    private static final int LAYER_WINDOW_MIN_HEIGHT = 88;
    private static final int LAYER_WINDOW_HEADER_HEIGHT = 16;
    private static final int LAYER_WINDOW_ENTRY_HEIGHT = 20;
    private static final int LAYER_WINDOW_PADDING = 6;
    private static final int LAYER_WINDOW_RESIZE_HANDLE_SIZE = 10;
    private static final int HANDLE_HALF_SIZE = 4;
    private static final int TEXT_EDIT_HANDLE_SIZE = 10;
    private static final int CHROME_EDGE_MARGIN = 2;
    private static final float ARROW_STROKE_WIDTH = 2.5F;
    private static final int DEFAULT_MOVE_SNAP = 8;
    private static final int LARGE_MOVE_SNAP = 32;
    private static final int SMALL_MOVE_SNAP = 1;

        private final List<DrawElement> elements = new ArrayList<>();
        private final Set<Integer> selectedElementIds = new LinkedHashSet<>();
        private final Set<ArrowAnchorReference> selectedArrowAnchors = new LinkedHashSet<>();

        private DrawTool activeTool = DrawTool.CURSOR;
        private DrawLayer activeLayer = DrawLayer.ELEMENTS;
        private boolean elementsLayerMuted = false;
        private boolean shellLayerMuted = false;
        private boolean chromeLayerMuted = false;
        private int nextElementId = 1;
        private int nextGroupId = 1;

        private double cameraX = 0.0D;
        private double cameraY = 0.0D;
        private double zoom = 1.0D;
        private boolean cameraOverlayVisible = false;
        private ChromeWidgetState minimapWidget = new ChromeWidgetState(Integer.MIN_VALUE, Integer.MIN_VALUE, MINIMAP_WIDTH, MINIMAP_HEIGHT, 1.0D);

        private ChromeWidgetState screenTitleWidget = new ChromeWidgetState(Integer.MIN_VALUE, Integer.MIN_VALUE, 0, 0, 1.0D);
        private ChromeWidgetState screenSubtitleWidget = new ChromeWidgetState(Integer.MIN_VALUE, Integer.MIN_VALUE, 0, 0, 1.0D);
        private ChromeWidgetState layerLabelWidget = new ChromeWidgetState(Integer.MIN_VALUE, Integer.MIN_VALUE, 0, 0, 1.0D);
        private ChromeWidgetState layerValueWidget = new ChromeWidgetState(Integer.MIN_VALUE, Integer.MIN_VALUE, 0, 0, 1.0D);
        private ChromeWidgetState cameraLabelWidget = new ChromeWidgetState(Integer.MIN_VALUE, Integer.MIN_VALUE, 0, 0, 1.0D);
        private ChromeWidgetState cameraXWidget = new ChromeWidgetState(Integer.MIN_VALUE, Integer.MIN_VALUE, 0, 0, 1.0D);
        private ChromeWidgetState cameraSeparatorWidget = new ChromeWidgetState(Integer.MIN_VALUE, Integer.MIN_VALUE, 0, 0, 1.0D);
        private ChromeWidgetState cameraYWidget = new ChromeWidgetState(Integer.MIN_VALUE, Integer.MIN_VALUE, 0, 0, 1.0D);
        private ChromeWidgetState zoomLabelWidget = new ChromeWidgetState(Integer.MIN_VALUE, Integer.MIN_VALUE, 0, 0, 1.0D);
        private ChromeWidgetState zoomValueWidget = new ChromeWidgetState(Integer.MIN_VALUE, Integer.MIN_VALUE, 0, 0, 1.0D);
        private ChromeWidgetState cursorLabelWidget = new ChromeWidgetState(Integer.MIN_VALUE, Integer.MIN_VALUE, 0, 0, 1.0D);
        private ChromeWidgetState cursorXWidget = new ChromeWidgetState(Integer.MIN_VALUE, Integer.MIN_VALUE, 0, 0, 1.0D);
        private ChromeWidgetState cursorSeparatorWidget = new ChromeWidgetState(Integer.MIN_VALUE, Integer.MIN_VALUE, 0, 0, 1.0D);
        private ChromeWidgetState cursorYWidget = new ChromeWidgetState(Integer.MIN_VALUE, Integer.MIN_VALUE, 0, 0, 1.0D);

        private ChromeWidgetState hotbarWidget = new ChromeWidgetState(Integer.MIN_VALUE, Integer.MIN_VALUE, HOTBAR_WIDTH, HOTBAR_HEIGHT, 1.0D);
        private ChromeWidgetState hotbarTitleWidget = new ChromeWidgetState(Integer.MIN_VALUE, Integer.MIN_VALUE, 0, 0, 1.0D);
        private ChromeWidgetState hotbarSubtitleWidget = new ChromeWidgetState(Integer.MIN_VALUE, Integer.MIN_VALUE, 0, 0, 1.0D);
        private ChromeWidgetState auxiliaryHotbarWidget = new ChromeWidgetState(Integer.MIN_VALUE, Integer.MIN_VALUE, AUXILIARY_HOTBAR_WIDTH, AUXILIARY_HOTBAR_HEIGHT, 1.0D);

        private boolean layerWindowVisible = false;
        private ChromeWidgetState layerWindowWidget = new ChromeWidgetState(Integer.MIN_VALUE, Integer.MIN_VALUE, LAYER_WINDOW_DEFAULT_WIDTH, LAYER_WINDOW_DEFAULT_HEIGHT, 1.0D);
        private final Set<ChromeWidget> selectedChromeWidgets = new LinkedHashSet<>();
        private @Nullable ChromeWidget selectedChromeWidget = null;
        private @Nullable ChromeWidget draggingChromeWidget = null;
        private int chromeWidgetDragOffsetX = 0;
        private int chromeWidgetDragOffsetY = 0;
        private @Nullable ChromeWidget resizingChromeWidget = null;
        private @Nullable SelectionHandle chromeWidgetResizeHandle = null;
        private @Nullable Rect chromeWidgetResizeOriginalBounds = null;
        private int chromeWidgetResizeAnchorX = 0;
        private int chromeWidgetResizeAnchorY = 0;
        private int chromeWidgetResizeStartWidth = 0;
        private int chromeWidgetResizeStartHeight = 0;
        private double chromeWidgetResizeStartScale = 1.0D;
        private boolean revealHiddenElements = false;
        private boolean canSelectLockedElements = false;

        private boolean panning = false;
        private int panButton = -1;
        private double panAnchorMouseX = 0.0D;
        private double panAnchorMouseY = 0.0D;
        private double panAnchorCameraX = 0.0D;
        private double panAnchorCameraY = 0.0D;

        private int textEditingElementId = -1;
        private int textEditingCaretIndex = 0;
        private boolean stickyToolMode = false;

        private final List<CanvasPoint> pendingArrowAnchors = new ArrayList<>();
        private @Nullable CameraOverlayProjection pendingArrowProjection = null;

        private @Nullable DraftInteraction draftInteraction = null;
        private @Nullable MoveSelectionDrag moveSelectionDrag = null;
        private @Nullable ResizeSelectionDrag resizeSelectionDrag = null;
        private @Nullable MarqueeSelectionDrag marqueeSelectionDrag = null;
        private @Nullable ChromeMarqueeSelectionDrag chromeMarqueeSelectionDrag = null;
        private @Nullable MoveChromeSelectionDrag moveChromeSelectionDrag = null;
        private @Nullable CameraFrameDrag cameraFrameDrag = null;
        private boolean duplicateSelectionPendingOnDrag = false;

        private long lastCursorClickAtMs = 0L;
        private double lastCursorClickX = 0.0D;
        private double lastCursorClickY = 0.0D;
        private long handCursorHandle = 0L;
        private long moveCursorHandle = 0L;
        private long horizontalResizeCursorHandle = 0L;
        private long verticalResizeCursorHandle = 0L;
        private long diagonalNorthWestSouthEastCursorHandle = 0L;
        private long diagonalNorthEastSouthWestCursorHandle = 0L;
        private ChromeCursor activeChromeCursor = ChromeCursor.DEFAULT;
        private double chromeMouseX = 0.0D;
        private double chromeMouseY = 0.0D;

        public SfmDrawScreen() {
        super(IdeLocalizationKeys.IDE_DRAW_TITLE.getComponent());
        seedShellElements();
        }

    @Override
    protected void init() {
        super.init();
        initChromeCursors();
        if (hotbarWidget.x() == Integer.MIN_VALUE || hotbarWidget.y() == Integer.MIN_VALUE) {
            hotbarWidget = hotbarWidget.withPosition((width - HOTBAR_WIDTH) / 2, Math.max(32, height - HOTBAR_HEIGHT - 42));
        }
        if (auxiliaryHotbarWidget.x() == Integer.MIN_VALUE || auxiliaryHotbarWidget.y() == Integer.MIN_VALUE) {
            auxiliaryHotbarWidget = auxiliaryHotbarWidget.withPosition(hotbarWidget.x() + hotbarWidth() + 6, hotbarWidget.y());
        }
        if (minimapWidget.x() == Integer.MIN_VALUE || minimapWidget.y() == Integer.MIN_VALUE) {
            minimapWidget = minimapWidget.withPosition(width - MINIMAP_WIDTH - 12, 30);
        }
        if (layerWindowWidget.x() == Integer.MIN_VALUE || layerWindowWidget.y() == Integer.MIN_VALUE) {
            layerWindowWidget = layerWindowWidget.withPosition(12, 42);
        }
        initializeStatusChromeWidgets();
        clampHotbarToScreen();
        clampAuxiliaryHotbarToScreen();
        clampAllTextChromeWidgetsToScreen();
        clampCameraOverlayToScreen();
        clampLayerWindowToScreen();
    }

    @Override
    public void onClose() {
        releaseChromeCursors();
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {

        return false;
    }

    @Override
    public boolean keyPressed(
            int keyCode,
            int scanCode,
            int modifiers
    ) {

        // r[impl draw.layer.switch.hotkeys]
        // r[impl draw.layer.switch.chrome]
        @Nullable DrawLayer layerFromHotkey = hasAltDown() ? layerForHotkey(keyCode) : null;
        if (layerFromHotkey != null) {
            switchActiveLayer(layerFromHotkey);
            return true;
        }

        if (textEditingElementId >= 0) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                finishTextEditing();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                mutateEditingTextBackspace();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_DELETE) {
                mutateEditingTextDelete();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_LEFT) {
                textEditingCaretIndex = Math.max(0, textEditingCaretIndex - 1);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_RIGHT) {
                TextElement textElement = editingTextElement();
                textEditingCaretIndex = Math.min(textElement != null ? textElement.text.length() : 0, textEditingCaretIndex + 1);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                if (hasShiftDown()) {
                    appendEditingText("\n");
                } else {
                    finishTextEditing();
                }
                return true;
            }
            if (hasControlDown() && keyCode == GLFW.GLFW_KEY_V) {
                appendEditingText(minecraft != null ? minecraft.keyboardHandler.getClipboard() : "");
                return true;
            }
            return true;
        }

        // r[impl draw.tool.cursor.select_all]
        if (isCanvasLayerActive() && hasControlDown() && keyCode == GLFW.GLFW_KEY_A) {
            selectAllElements();
            return true;
        }

        // r[impl draw.element.hidden.select-revealed]
        if (isCanvasLayerActive() && revealHiddenElements && hasShiftDown() && keyCode == GLFW.GLFW_KEY_X) {
            selectAllHiddenElements();
            return true;
        }

        // r[impl draw.tool.cursor.delete_selection]
        if (isCanvasLayerActive() && (keyCode == GLFW.GLFW_KEY_DELETE || keyCode == GLFW.GLFW_KEY_BACKSPACE)) {
            if (deleteSelectedElements()) {
                return true;
            }
        }

        // r[impl draw.tool.cursor.duplicate_selection]
        if (isCanvasLayerActive() && hasAltDown() && keyCode == GLFW.GLFW_KEY_D) {
            duplicateSelection();
            return true;
        }

        if (isCanvasLayerActive() && activeTool == DrawTool.CURSOR && keyCode == GLFW.GLFW_KEY_G && groupActiveSelection()) {
            return true;
        }

        if (handleCanvasArrangementShortcut(keyCode)) {
            return true;
        }

        // r[impl draw.tool.cursor.transform_selection.keyboard-nudge]
        if (activeTool == DrawTool.CURSOR && nudgeActiveSelection(keyCode)) {
            return true;
        }

        // r[impl draw.element.hidden.toggle-selection]
        // r[impl draw.element.hidden.reveal-toggle]
        if (keyCode == GLFW.GLFW_KEY_X) {
            if (toggleHiddennessForActiveSelection()) {
                return true;
            }
            revealHiddenElements = !revealHiddenElements;
            if (!revealHiddenElements) {
                selectedElementIds.removeIf(id -> {
                    DrawElement element = findElementById(id);
                    return element != null && element.hidden();
                });
                if (selectedChromeWidget != null && chromeWidgetState(selectedChromeWidget).hidden()) {
                    clearChromeSelection();
                }
            }
            return true;
        }

        // r[impl draw.tool.zen.shortcut]
        if (keyCode == GLFW.GLFW_KEY_Z) {
            handleHotbarToolClick(DrawTool.ZEN);
            return true;
        }

        // r[impl draw.chrome.hotbar.shortcuts]
        int hotbarIndex = hotbarIndexForKeyCode(keyCode);
        if (hotbarIndex >= 0 && hotbarIndex < TOOL_COUNT) {
            handleToolShortcut(DrawTool.VALUES[hotbarIndex]);
            return true;
        }

        if (activeTool == DrawTool.ARROW && (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER || keyCode == GLFW.GLFW_KEY_ESCAPE)) {
            finalizePendingArrowAnchors();
            return true;
        }

        @Nullable DrawTool shortcutTool = DrawTool.byKeyCode(keyCode);
        if (shortcutTool != null) {
            handleToolShortcut(shortcutTool);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(
            char codePoint,
            int modifiers
    ) {

        if (textEditingElementId >= 0) {
            if (!Character.isISOControl(codePoint)) {
                appendEditingText(String.valueOf(codePoint));
            }
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean mouseClicked(
            double mouseX,
            double mouseY,
            int button
    ) {
        chromeMouseX = mouseX;
        chromeMouseY = mouseY;

        Rect layerWindowRect = layerWindowBounds();
        Rect layerWindowCloseBounds = layerWindowCloseButtonBounds();
        if (isChromeWidgetOperational(ChromeWidget.LAYER_WINDOW) && button == GLFW.GLFW_MOUSE_BUTTON_LEFT && layerWindowCloseBounds.contains(mouseX, mouseY)) {
            // r[impl draw.layer-window.close-priority]
            layerWindowVisible = false;
            if (activeTool == DrawTool.LAYER) {
                activeTool = DrawTool.CURSOR;
            }
            return true;
        }

        int hoveredLayerIndex = hoveredLayerIndex(mouseX, mouseY);
        if (isChromeWidgetOperational(ChromeWidget.LAYER_WINDOW) && button == GLFW.GLFW_MOUSE_BUTTON_LEFT && hoveredLayerIndex >= 0) {
            if (layerEntryMuteToggleBounds(hoveredLayerIndex).contains(mouseX, mouseY)) {
                // r[impl draw.layer-window.layer-entry-mute-toggle]
                toggleLayerMuted(DrawLayer.VALUES[hoveredLayerIndex]);
                return true;
            }
            switchActiveLayer(DrawLayer.VALUES[hoveredLayerIndex]);
            return true;
        }

        // r[impl draw.layer-window.interact-any-layer]
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && isChromeWidgetOperational(ChromeWidget.LAYER_WINDOW)) {
            if (beginChromeWidgetResize(ChromeWidget.LAYER_WINDOW, mouseX, mouseY)) {
                selectOnlyChromeWidget(ChromeWidget.LAYER_WINDOW);
                return true;
            }
            if (beginChromeWidgetDrag(ChromeWidget.LAYER_WINDOW, mouseX, mouseY)) {
                selectOnlyChromeWidget(ChromeWidget.LAYER_WINDOW);
                return true;
            }
            if (layerWindowRect.contains(mouseX, mouseY)) {
                return true;
            }
        }

        if (isChromeLayerActive() && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            if (handleChromeWidgetPress(mouseX, mouseY)) {
                return true;
            }
        }

        @Nullable DrawTool hoveredToolbarTool = hoveredToolbarTool(mouseX, mouseY);
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && hoveredToolbarTool != null) {
            handleHotbarToolClick(hoveredToolbarTool);
            return true;
        }

        // r[impl draw.camera.pan.middle_drag]
        if (button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE) {
            beginPan(mouseX, mouseY, button);
            return true;
        }

        Rect overlayBounds = cameraOverlayBounds();
        // r[impl draw.camera.hidden-minimap-not-interactive]
        @Nullable CameraOverlayProjection overlayProjection = isChromeLayerActive() ? null : cameraOverlayInteractionProjection();
        @Nullable CameraOverlayProjection projectionUnderMouse = overlayProjection != null && overlayProjection.mapBounds().contains(mouseX, mouseY) ? overlayProjection : null;
        if (isChromeWidgetOperational(ChromeWidget.MINIMAP) && overlayBounds.contains(mouseX, mouseY) && projectionUnderMouse == null) {
            return true;
        }

        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return super.mouseClicked(mouseX, mouseY, button);
        }

        if (!isCanvasLayerActive()) {
            return true;
        }

        // r[impl draw.tool.layer.exists]
        if (activeTool == DrawTool.LAYER) {
            return true;
        }

        // r[impl draw.camera.pan.hand_tool]
        if (activeTool == DrawTool.HAND) {
            beginPan(mouseX, mouseY, button);
            return true;
        }

        CanvasPoint canvasPoint = screenToCanvas(mouseX, mouseY, projectionUnderMouse);
        CanvasPoint snappedCanvasPoint = snapCanvasPointToCurrentIncrement(canvasPoint);

        if (activeTool == DrawTool.CAMERA) {
            if (projectionUnderMouse != null) {
                cameraFrameDrag = new CameraFrameDrag(canvasPoint, canvasPoint, projectionUnderMouse);
            }
            return true;
        }

        if (textEditingElementId >= 0) {
            TextElement editingTextElement = editingTextElement();
            if (editingTextElement == null) {
                finishTextEditing();
                return true;
            }
            if (textEditingHandleBounds(editingTextElement).contains(mouseX, mouseY)) {
                selectOnly(editingTextElement.id());
                CanvasBounds textSelectionBounds = currentSelectionBounds();
                if (textSelectionBounds != null) {
                    moveSelectionDrag = new MoveSelectionDrag(selectionSnapshot(), arrowAnchorSelectionSnapshot(), canvasPoint, projectionUnderMouse, textSelectionBounds, selectionSnapOrigin());
                }
                duplicateSelectionPendingOnDrag = false;
            } else {
                TextElement hitTextElement = editableTextElementAt(canvasPoint);
                if (hitTextElement != null) {
                    if (hitTextElement.id() != editingTextElement.id()) {
                        selectOnly(hitTextElement.id());
                        textEditingElementId = hitTextElement.id();
                    }
                    placeTextCaretFromScreen(hitTextElement, mouseX, mouseY);
                } else {
                    finishTextEditing();
                }
            }
            return true;
        }

        if (activeTool == DrawTool.CURSOR) {
            boolean doubleClick = isCursorDoubleClick(mouseX, mouseY);
            if (doubleClick && handleCursorDoubleClick(canvasPoint)) {
                return true;
            }
            handleCursorPress(mouseX, mouseY, canvasPoint, projectionUnderMouse);
            return true;
        }

        // r[impl draw.tool.text.create]
        if (activeTool == DrawTool.TEXT) {
            TextElement hitTextElement = editableTextElementAt(canvasPoint);
            if (hitTextElement != null) {
                selectOnly(hitTextElement.id());
                textEditingElementId = hitTextElement.id();
                placeTextCaretFromScreen(hitTextElement, mouseX, mouseY);
            } else {
                TextElement textElement = createTextElement(snappedCanvasPoint);
                textEditingElementId = textElement.id();
                textEditingCaretIndex = textElement.text.length();
            }
            return true;
        }

        if (activeTool == DrawTool.ARROW && pendingArrowAnchors.isEmpty()) {
            // r[impl draw.tool.creation.snap]
            draftInteraction = new DraftInteraction(activeTool, snappedCanvasPoint, snappedCanvasPoint, projectionUnderMouse);
            return true;
        }

        if (activeTool == DrawTool.ARROW) {
            if (pendingArrowProjection == projectionUnderMouse || (pendingArrowProjection == null && projectionUnderMouse == null)) {
                // r[impl draw.tool.creation.snap]
                pendingArrowAnchors.add(snappedCanvasPoint);
                pendingArrowProjection = projectionUnderMouse;
            }
            return true;
        }

        // r[impl draw.tool.creation.snap]
        draftInteraction = new DraftInteraction(activeTool, snappedCanvasPoint, snappedCanvasPoint, projectionUnderMouse);
        if (activeTool == DrawTool.FREEHAND) {
            draftInteraction.points().add(canvasPoint);
        }
        return true;
    }

    @Override
    public boolean mouseDragged(
            double mouseX,
            double mouseY,
            int button,
            double dragX,
            double dragY
    ) {
        chromeMouseX = mouseX;
        chromeMouseY = mouseY;

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            if (dragActiveChromeWidget(mouseX, mouseY)) {
                return true;
            }
            if (resizeActiveChromeWidget(mouseX, mouseY)) {
                return true;
            }
            if (chromeMarqueeSelectionDrag != null) {
                chromeMarqueeSelectionDrag.currentX = mouseX;
                chromeMarqueeSelectionDrag.currentY = mouseY;
                return true;
            }
        }

        // r[impl draw.camera.pan.middle_drag]
        // r[impl draw.camera.pan.hand_tool]
        if (panning && button == panButton) {
            cameraX = panAnchorCameraX - (mouseX - panAnchorMouseX) / zoom;
            cameraY = panAnchorCameraY - (mouseY - panAnchorMouseY) / zoom;
            return true;
        }

        if (cameraFrameDrag != null && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            cameraFrameDrag.currentPoint = screenToCanvas(mouseX, mouseY, cameraFrameDrag.projection());
            return true;
        }

        // r[impl draw.tool.cursor.transform_selection]
        if (moveSelectionDrag != null && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            CanvasPoint canvasPoint = screenToCanvas(mouseX, mouseY, moveSelectionDrag.projection());
            applyMoveSelectionDrag(moveSelectionDrag, canvasPoint);
            return true;
        }

        // r[impl draw.tool.cursor.transform_selection]
        if (resizeSelectionDrag != null && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            CanvasPoint canvasPoint = screenToCanvas(mouseX, mouseY, resizeSelectionDrag.projection());
            applyResizeSelectionDrag(resizeSelectionDrag, canvasPoint);
            return true;
        }

        if (marqueeSelectionDrag != null && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            marqueeSelectionDrag.currentPoint = screenToCanvas(mouseX, mouseY, marqueeSelectionDrag.projection());
            return true;
        }

        // r[impl draw.tool.freehand.create]
        if (draftInteraction != null && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            CanvasPoint canvasPoint = screenToCanvas(mouseX, mouseY, draftInteraction.projection());
            draftInteraction.currentPoint = draftInteraction.tool() == DrawTool.FREEHAND
                    ? canvasPoint
                    : snapCanvasPointToCurrentIncrement(canvasPoint);
            if (draftInteraction.tool() == DrawTool.FREEHAND) {
                CanvasPoint lastPoint = draftInteraction.points().get(draftInteraction.points().size() - 1);
                if (distanceSquared(lastPoint, canvasPoint) >= FREEHAND_STEP_DISTANCE_SQUARED / Math.max(0.0001D, zoom * zoom)) {
                    draftInteraction.points().add(canvasPoint);
                }
            }
            return true;
        }

        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(
            double mouseX,
            double mouseY,
            int button
    ) {
        chromeMouseX = mouseX;
        chromeMouseY = mouseY;

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && releaseChromeWidgetInteractions()) {
            return true;
        }

        if (panning && button == panButton) {
            panning = false;
            panButton = -1;
            return true;
        }

        // r[impl draw.camera.frame_tool]
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && cameraFrameDrag != null) {
            cameraFrameDrag.currentPoint = screenToCanvas(mouseX, mouseY, cameraFrameDrag.projection());
            applyCameraFrame(cameraFrameDrag);
            cameraFrameDrag = null;
            return true;
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && draftInteraction != null) {
            CanvasPoint canvasPoint = screenToCanvas(mouseX, mouseY, draftInteraction.projection());
            draftInteraction.currentPoint = draftInteraction.tool() == DrawTool.FREEHAND
                    ? canvasPoint
                    : snapCanvasPointToCurrentIncrement(canvasPoint);
            commitDraft();
            return true;
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && moveSelectionDrag != null) {
            moveSelectionDrag = null;
            duplicateSelectionPendingOnDrag = false;
            return true;
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && moveChromeSelectionDrag != null) {
            moveChromeSelectionDrag = null;
            draggingChromeWidget = null;
            for (ChromeWidget widget : selectedChromeWidgets) {
                clampChromeWidgetToScreen(widget);
            }
            return true;
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && resizeSelectionDrag != null) {
            resizeSelectionDrag = null;
            return true;
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && marqueeSelectionDrag != null) {
            finalizeMarqueeSelection();
            return true;
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && chromeMarqueeSelectionDrag != null) {
            finalizeChromeMarqueeSelection();
            return true;
        }

        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(
            double mouseX,
            double mouseY,
            double delta
    ) {
        chromeMouseX = mouseX;
        chromeMouseY = mouseY;

        if (!hasControlDown() || delta == 0.0D) {
            return super.mouseScrolled(mouseX, mouseY, delta);
        }

        // r[impl draw.camera.zoom.cursor]
        @Nullable CameraOverlayProjection projection = cameraOverlayInteractionProjection();
        CanvasPoint focusPoint = projection != null && projection.mapBounds().contains(mouseX, mouseY)
                                ? projection.screenToCanvas(mouseX, mouseY)
                                : screenToCanvas(mouseX, mouseY, null);

        double scaleFactor = delta > 0.0D ? ZOOM_STEP : 1.0D / ZOOM_STEP;
        double oldZoom = zoom;
        double newZoom = Mth.clamp(oldZoom * scaleFactor, MIN_ZOOM, MAX_ZOOM);
        if (newZoom == oldZoom) {
            return true;
        }

        zoom = newZoom;
        cameraX = focusPoint.x() - (mouseX - screenCenterX()) / zoom;
        cameraY = focusPoint.y() - (mouseY - screenCenterY()) / zoom;
        return true;
    }

    @Override
    public void render(
            PoseStack poseStack,
            int mouseX,
            int mouseY,
            float partialTick
    ) {
        chromeMouseX = mouseX;
        chromeMouseY = mouseY;

        renderBackground(poseStack);
        fill(poseStack, 0, 0, width, height, 0xFF111318);
        refreshShellElements();
        drawGrid(poseStack);
        drawOriginAxes(poseStack);
        drawElements(poseStack);
        drawDraft(poseStack);
        drawSelectionOverlay(poseStack);
        drawOverlayChrome(poseStack, mouseX, mouseY);
        drawCursorSelectionModifierIndicator(poseStack, mouseX, mouseY);
        updateChromeCursor(mouseX, mouseY);
        super.render(poseStack, mouseX, mouseY, partialTick);
    }

    private void drawOverlayChrome(
            PoseStack poseStack,
            int mouseX,
            int mouseY
    ) {
        // r[impl draw.chrome.status.parts.independent]
        // r[impl draw.chrome.status.labels.separate-from-values]
        drawStatusTextWidget(poseStack, ChromeWidget.SCREEN_TITLE, chromeWidgetText(ChromeWidget.SCREEN_TITLE), 0x00000000, 0xF0F3F7);
        drawStatusTextWidget(poseStack, ChromeWidget.SCREEN_SUBTITLE, chromeWidgetText(ChromeWidget.SCREEN_SUBTITLE), 0x00000000, 0x9AA3B2);
        drawStatusTextWidget(poseStack, ChromeWidget.ACTIVE_LAYER_LABEL, chromeWidgetText(ChromeWidget.ACTIVE_LAYER_LABEL), 0x00000000, 0xC4CBD6);
        // r[impl draw.layer.visibility.feedback]
        drawStatusTextWidget(poseStack, ChromeWidget.ACTIVE_LAYER_VALUE, chromeWidgetText(ChromeWidget.ACTIVE_LAYER_VALUE), 0x00000000, activeLayer.color());
        drawStatusTextWidget(poseStack, ChromeWidget.CAMERA_POSITION_LABEL, chromeWidgetText(ChromeWidget.CAMERA_POSITION_LABEL), 0x00000000, 0xC4CBD6);
        drawStatusTextWidget(poseStack, ChromeWidget.CAMERA_POSITION_X, chromeWidgetText(ChromeWidget.CAMERA_POSITION_X), 0x00000000, 0xC4CBD6);
        drawStatusTextWidget(poseStack, ChromeWidget.CAMERA_POSITION_SEPARATOR, chromeWidgetText(ChromeWidget.CAMERA_POSITION_SEPARATOR), 0x00000000, 0x8E99A9);
        drawStatusTextWidget(poseStack, ChromeWidget.CAMERA_POSITION_Y, chromeWidgetText(ChromeWidget.CAMERA_POSITION_Y), 0x00000000, 0xC4CBD6);
        drawStatusTextWidget(poseStack, ChromeWidget.ZOOM_LABEL, chromeWidgetText(ChromeWidget.ZOOM_LABEL), 0x00000000, 0xC4CBD6);
        drawStatusTextWidget(poseStack, ChromeWidget.ZOOM_VALUE, chromeWidgetText(ChromeWidget.ZOOM_VALUE), 0x00000000, 0xC4CBD6);
        drawStatusTextWidget(poseStack, ChromeWidget.CURSOR_LABEL, chromeWidgetText(ChromeWidget.CURSOR_LABEL), 0x00000000, 0x9AA3B2);
        drawStatusTextWidget(poseStack, ChromeWidget.CURSOR_X, chromeWidgetText(ChromeWidget.CURSOR_X), 0x00000000, 0x9AA3B2);
        drawStatusTextWidget(poseStack, ChromeWidget.CURSOR_SEPARATOR, chromeWidgetText(ChromeWidget.CURSOR_SEPARATOR), 0x00000000, 0x7A8594);
        drawStatusTextWidget(poseStack, ChromeWidget.CURSOR_Y, chromeWidgetText(ChromeWidget.CURSOR_Y), 0x00000000, 0x9AA3B2);

        if (isChromeWidgetRendered(ChromeWidget.MINIMAP)) {
            drawMinimap(poseStack);
        }
        drawToolHotbar(poseStack, mouseX, mouseY);
        if (isChromeWidgetRendered(ChromeWidget.LAYER_WINDOW)) {
            drawLayerWindow(poseStack, mouseX, mouseY);
        }
        if (isChromeLayerActive()) {
            drawChromeWidgetEditingOverlay(poseStack, mouseX, mouseY);
        }
    }

    // r[impl draw.layer.chrome-customization-mode]
    // r[impl draw.layer.widgets-owned-by-layer]
    private void drawChromeWidgetEditingOverlay(
            PoseStack poseStack,
            int mouseX,
            int mouseY
    ) {
        @Nullable ChromeWidget hoveredWidget = hoveredChromeWidget(mouseX, mouseY);
        for (ChromeWidget widget : ChromeWidget.VALUES) {
            if (!isChromeWidgetRendered(widget)) {
                continue;
            }
            Rect bounds = chromeWidgetBounds(widget);
            boolean hidden = chromeWidgetState(widget).hidden();
            int outlineColor = isChromeWidgetSelected(widget)
                    ? 0xFFF6E27F
                    : (widget == hoveredWidget ? 0xFF9BD1FF : (hidden ? 0x55606975 : 0x66596C82));
            drawScreenRectOutline(poseStack, new ScreenRect(bounds.left(), bounds.top(), bounds.right(), bounds.bottom()), outlineColor);
        }

        if (hoveredWidget != null) {
            Component label = hoveredWidget.label();
            int labelWidth = font.width(label);
            int left = mouseX + 12;
            int top = mouseY + 10;
            fill(poseStack, left - 4, top - 2, left + labelWidth + 4, top + 10, 0xD010141A);
            drawString(poseStack, font, label, left, top, 0xFFF6FAFF);
        }

        // r[impl draw.layer.chrome.selection-handles]
        if (selectedChromeWidget != null && selectedChromeWidgets.size() == 1 && isChromeWidgetRendered(selectedChromeWidget)) {
            ScreenRect screenBounds = toScreenRect(chromeWidgetBounds(selectedChromeWidget));
            for (SelectionHandle handle : SelectionHandle.VALUES) {
                ScreenPoint handlePoint = selectionHandlePoint(screenBounds, handle);
                fill(
                        poseStack,
                        (int) Math.round(handlePoint.x()) - HANDLE_HALF_SIZE,
                        (int) Math.round(handlePoint.y()) - HANDLE_HALF_SIZE,
                        (int) Math.round(handlePoint.x()) + HANDLE_HALF_SIZE + 1,
                        (int) Math.round(handlePoint.y()) + HANDLE_HALF_SIZE + 1,
                        0xFFF6E27F
                );
            }
        }

        if (chromeMarqueeSelectionDrag != null) {
            ScreenRect rect = chromeMarqueeSelectionDrag.screenRect();
            fill(poseStack, rect.left(), rect.top(), rect.right(), rect.bottom(), 0x2288C0FF);
            drawScreenRectOutline(poseStack, rect, 0xAA88C0FF);
        }
    }

    // r[impl draw.layer-window.exists]
    // r[impl draw.layer-window.title]
    // r[impl draw.layer-window.layer-list]
    // r[impl draw.layer-window.border]
    // r[impl draw.layer-window.active-highlight]
    private void drawLayerWindow(
            PoseStack poseStack,
            int mouseX,
            int mouseY
    ) {
        Rect bounds = layerWindowBounds();
        Rect headerBounds = layerWindowHeaderBounds();
        Rect closeBounds = layerWindowCloseButtonBounds();
        boolean hidden = layerWindowWidget.hidden();
        fill(poseStack, bounds.left(), bounds.top(), bounds.right(), bounds.bottom(), renderColor(0xD9141820, hidden));
        fill(poseStack, headerBounds.left(), headerBounds.top(), headerBounds.right(), headerBounds.bottom(), renderColor(0xE0212630, hidden));
        drawScreenRectOutline(poseStack, new ScreenRect(bounds.left(), bounds.top(), bounds.right(), bounds.bottom()), renderColor(0xFF606975, hidden));
        drawString(poseStack, font, IdeLocalizationKeys.IDE_DRAW_LAYER_WINDOW_TITLE.getComponent(), bounds.left() + LAYER_WINDOW_PADDING, bounds.top() + 4, renderColor(0xEDF1F7, hidden));
        fill(poseStack, closeBounds.left(), closeBounds.top(), closeBounds.right(), closeBounds.bottom(), renderColor(closeBounds.contains(mouseX, mouseY) ? 0xAA8F3540 : 0x66462C31, hidden));
        drawString(poseStack, font, Component.literal("x"), closeBounds.left() + 3, closeBounds.top() + 1, renderColor(0xFFF1F5FB, hidden));

        int listTop = headerBounds.bottom() + 4;
        int listBottom = bounds.bottom() - LAYER_WINDOW_PADDING - 2;
        fill(poseStack, bounds.left() + 4, listTop, bounds.right() - 4, listBottom, renderColor(0x7710141A, hidden));
        for (int index = 0; index < DrawLayer.VALUES.length; index++) {
            Rect entryBounds = layerEntryBounds(index);
            DrawLayer layer = DrawLayer.VALUES[index];
            boolean muted = isLayerMuted(layer);
            Rect muteToggleBounds = layerEntryMuteToggleBounds(index);
            boolean muteHovered = muteToggleBounds.contains(mouseX, mouseY);
            int backgroundColor = renderColor(layer == activeLayer ? 0x663B6EA8 : (entryBounds.contains(mouseX, mouseY) ? 0x33445B73 : 0x22202831), hidden || muted);
            fill(poseStack, entryBounds.left(), entryBounds.top(), entryBounds.right(), entryBounds.bottom(), backgroundColor);
            // r[impl draw.layer-window.layer-entry-thumbnail]
            fill(poseStack, entryBounds.left() + 4, entryBounds.top() + 4, entryBounds.left() + 16, entryBounds.top() + 16, renderColor(layer.color(), hidden || muted));
            // r[impl draw.layer-window.layer-entry-name]
            drawString(poseStack, font, layer.labelComponent(), entryBounds.left() + 22, entryBounds.top() + 6, renderColor(layer == activeLayer ? 0xFFF6FAFF : 0xCBD3DF, hidden || muted));

            // r[impl draw.layer-window.layer-entry-mute-toggle.states]
            // r[impl draw.layer-window.layer-entry-mute-toggle.hover]
            int muteBackgroundColor = muted ? 0x885B2C31 : 0x55304048;
            if (muteHovered) {
                muteBackgroundColor = muted ? 0xAA8F3540 : 0x88606975;
            }
            fill(poseStack, muteToggleBounds.left(), muteToggleBounds.top(), muteToggleBounds.right(), muteToggleBounds.bottom(), renderColor(muteBackgroundColor, hidden));
            drawScreenRectOutline(poseStack, new ScreenRect(muteToggleBounds.left(), muteToggleBounds.top(), muteToggleBounds.right(), muteToggleBounds.bottom()), renderColor(muteHovered ? 0xFFE8EEF7 : 0xAA8E99A9, hidden));
            Component muteLabel = muted
                    ? IdeLocalizationKeys.IDE_DRAW_LAYER_UNMUTE.getComponent()
                    : IdeLocalizationKeys.IDE_DRAW_LAYER_MUTE.getComponent();
            drawString(poseStack, font, Component.literal(muted ? "M" : "U"), muteToggleBounds.left() + 3, muteToggleBounds.top() + 2, renderColor(muted ? 0xFFFFD6D8 : 0xFFE5EBF2, hidden));
            if (muteHovered) {
                int tooltipLeft = mouseX + 12;
                int tooltipTop = mouseY + 10;
                int tooltipWidth = font.width(muteLabel);
                fill(poseStack, tooltipLeft - 4, tooltipTop - 2, tooltipLeft + tooltipWidth + 4, tooltipTop + 10, 0xD010141A);
                drawString(poseStack, font, muteLabel, tooltipLeft, tooltipTop, 0xFFF6FAFF);
            }
        }

        Rect resizeHandleBounds = layerWindowResizeHandleBounds();
        fill(poseStack, resizeHandleBounds.left(), resizeHandleBounds.top(), resizeHandleBounds.right(), resizeHandleBounds.bottom(), renderColor(0x88606975, hidden));
    }

    // r[impl draw.canvas.grid]
    private void drawGrid(PoseStack poseStack) {
        double step = 32.0D;
        while (step * zoom < 18.0D) {
            step *= 2.0D;
        }
        while (step * zoom > 72.0D && step > 4.0D) {
            step /= 2.0D;
        }

        CanvasBounds visibleBounds = visibleCanvasBounds();
        int minorColor = 0x182C3440;
        int majorColor = 0x243E4B60;

        long firstVertical = (long) Math.floor(visibleBounds.minX() / step) - 1L;
        long lastVertical = (long) Math.ceil(visibleBounds.maxX() / step) + 1L;
        for (long index = firstVertical; index <= lastVertical; index++) {
            double canvasX = index * step;
            int screenX = (int) Math.round(canvasToScreenX(canvasX));
            int color = index % 4L == 0L ? majorColor : minorColor;
            fill(poseStack, screenX, 0, screenX + 1, height, color);
        }

        long firstHorizontal = (long) Math.floor(visibleBounds.minY() / step) - 1L;
        long lastHorizontal = (long) Math.ceil(visibleBounds.maxY() / step) + 1L;
        for (long index = firstHorizontal; index <= lastHorizontal; index++) {
            double canvasY = index * step;
            int screenY = (int) Math.round(canvasToScreenY(canvasY));
            int color = index % 4L == 0L ? majorColor : minorColor;
            fill(poseStack, 0, screenY, width, screenY + 1, color);
        }
    }

    // r[impl draw.canvas.origin]
    private void drawOriginAxes(PoseStack poseStack) {
        int axisColor = 0x4C8AC6FF;
        int originX = (int) Math.round(canvasToScreenX(0.0D));
        int originY = (int) Math.round(canvasToScreenY(0.0D));
        fill(poseStack, originX, 0, originX + 1, height, axisColor);
        fill(poseStack, 0, originY, width, originY + 1, axisColor);
        fill(poseStack, originX - 3, originY - 3, originX + 4, originY + 4, 0xAA8AC6FF);
    }

    private void drawElements(PoseStack poseStack) {
        // r[impl draw.element.hidden.dim-when-revealed]
        for (DrawElement element : elements) {
            if (!shouldRenderElement(element)) {
                continue;
            }
            boolean selected = isElementSelected(element);
            boolean hidden = element.hidden();
            if (element instanceof RectangleElement rectangleElement) {
                drawRectangleElement(poseStack, rectangleElement, selected, hidden);
            } else if (element instanceof ArrowElement arrowElement) {
                drawArrowElement(poseStack, arrowElement, selected, hidden);
            } else if (element instanceof TextElement textElement) {
                drawTextElement(poseStack, textElement, selected, hidden);
            } else if (element instanceof FreehandElement freehandElement) {
                drawFreehandElement(poseStack, freehandElement, selected, hidden);
            }
        }
    }

    private void drawDraft(PoseStack poseStack) {
        if (draftInteraction != null) {
            switch (draftInteraction.tool()) {
                case RECTANGLE -> drawRectangleElement(
                        poseStack,
                    new RectangleElement(-1, activeCanvasLayer(), draftInteraction.startPoint().x(), draftInteraction.startPoint().y(), draftInteraction.currentPoint().x(), draftInteraction.currentPoint().y(), 0x332FB5FF, 0xFF7FD7FF),
                        true,
                        false
                );
                case ARROW -> drawArrowElement(poseStack, new ArrowElement(-1, activeCanvasLayer(), List.of(draftInteraction.startPoint(), draftInteraction.currentPoint()), 0xFFE8A652), true, false);
                case FREEHAND -> {
                    if (draftInteraction.points().size() >= 2) {
                        drawFreehandElement(
                                poseStack,
                        new FreehandElement(-1, activeCanvasLayer(), List.copyOf(draftInteraction.points()), 0xFF88D498),
                                true,
                                false
                        );
                    }
                }
                default -> {
                }
            }
        }

        if (!pendingArrowAnchors.isEmpty()) {
            // r[impl draw.tool.arrow.multisegment.preview]
            drawPendingArrowPreview(poseStack);
        }

        if (cameraFrameDrag != null) {
            drawBoundsInProjection(
                    poseStack,
                    CanvasBounds.of(cameraFrameDrag.startPoint.x(), cameraFrameDrag.startPoint.y(), cameraFrameDrag.currentPoint.x(), cameraFrameDrag.currentPoint.y()),
                    cameraFrameDrag.projection(),
                    0xFFE8A652,
                    0x22E8A652
            );
        }
    }

    private void drawSelectionOverlay(PoseStack poseStack) {
        CanvasBounds selectionBounds = currentSelectionBounds();
        if (selectionBounds == null) {
            drawMarqueeOverlay(poseStack);
            return;
        }

        ScreenRect screenBounds = canvasBoundsToScreenRect(selectionBounds);
        drawScreenRectOutline(poseStack, screenBounds, 0xFFF6E27F);

        if (activeTool == DrawTool.CURSOR && moveSelectionDrag == null && canResizeCurrentSelection()) {
            // r[impl draw.tool.cursor.transform_selection.handles-hidden-during-move]
            for (SelectionHandle handle : SelectionHandle.VALUES) {
                ScreenPoint handlePoint = selectionHandlePoint(screenBounds, handle);
                fill(
                        poseStack,
                        (int) Math.round(handlePoint.x()) - HANDLE_HALF_SIZE,
                        (int) Math.round(handlePoint.y()) - HANDLE_HALF_SIZE,
                        (int) Math.round(handlePoint.x()) + HANDLE_HALF_SIZE + 1,
                        (int) Math.round(handlePoint.y()) + HANDLE_HALF_SIZE + 1,
                        0xFFF6E27F
                );
            }
        }

        if (activeTool == DrawTool.TEXT && textEditingElementId >= 0 && moveSelectionDrag == null) {
            drawTextEditingHandle(poseStack);
        }

        drawSelectedArrowAnchors(poseStack);

        drawMarqueeOverlay(poseStack);
    }

    private void drawSelectedArrowAnchors(PoseStack poseStack) {
        // r[impl draw.tool.arrow.anchors.render-when-selected]
        for (DrawElement element : elements) {
            if (!(element instanceof ArrowElement arrowElement) || !arrowHasSelectedAnchors(arrowElement)) {
                continue;
            }
            for (int index = 0; index < arrowElement.points.size(); index++) {
                if (arrowElement.isAnchorHidden(index) && !revealHiddenElements) {
                    continue;
                }
                CanvasPoint point = arrowElement.points.get(index);
                ScreenPoint screenPoint = canvasToScreen(point);
                boolean selected = isArrowAnchorSelected(arrowElement.id(), index);
                int color = selected ? 0xFFF6E27F : renderColor(0xCC9BD1FF, arrowElement.isAnchorHidden(index));
                fill(
                        poseStack,
                        (int) Math.round(screenPoint.x()) - HANDLE_HALF_SIZE,
                        (int) Math.round(screenPoint.y()) - HANDLE_HALF_SIZE,
                        (int) Math.round(screenPoint.x()) + HANDLE_HALF_SIZE + 1,
                        (int) Math.round(screenPoint.y()) + HANDLE_HALF_SIZE + 1,
                        color
                );
            }
        }
    }

    private void drawCursorSelectionModifierIndicator(
            PoseStack poseStack,
            int mouseX,
            int mouseY
    ) {
        // r[impl draw.tool.cursor.selection.modifier-indicator]
        String indicator = selectionModifierIndicator();
        if (indicator == null) {
            return;
        }
        int left = mouseX + 10;
        int top = mouseY - 14;
        fill(poseStack, left - 3, top - 2, left + 8, top + 9, 0xD010141A);
        drawString(poseStack, font, indicator, left, top, "+".equals(indicator) ? 0xFF88D498 : 0xFFF08B8B);
    }

    private void drawMarqueeOverlay(PoseStack poseStack) {
        if (marqueeSelectionDrag == null) {
            return;
        }
        drawBoundsInProjection(
                poseStack,
                CanvasBounds.of(
                        marqueeSelectionDrag.startPoint.x(),
                        marqueeSelectionDrag.startPoint.y(),
                        marqueeSelectionDrag.currentPoint.x(),
                        marqueeSelectionDrag.currentPoint.y()
                ),
                marqueeSelectionDrag.projection(),
                0xAA88C0FF,
                0x2288C0FF
        );
    }

    private void drawRectangleElement(
            PoseStack poseStack,
            RectangleElement element,
            boolean selected,
            boolean hidden
    ) {
        ScreenRect screenBounds = canvasBoundsToScreenRect(CanvasBounds.of(element.x1, element.y1, element.x2, element.y2));
        fill(poseStack, screenBounds.left(), screenBounds.top(), screenBounds.right(), screenBounds.bottom(), renderColor(element.fillColor, hidden));
        int strokeColor = renderColor(selected ? 0xFFF6E27F : element.strokeColor, hidden);
        drawScreenRectOutline(poseStack, screenBounds, strokeColor);
    }

    private void drawArrowElement(
            PoseStack poseStack,
            ArrowElement element,
            boolean selected,
            boolean hidden
    ) {
        List<CanvasPoint> renderPoints = renderableArrowPoints(element);
        if (renderPoints.size() < 2) {
            return;
        }
        List<ScreenPoint> linePoints = new ArrayList<>(renderPoints.size());
        for (CanvasPoint point : renderPoints) {
            linePoints.add(canvasToScreen(point));
        }
        int color = renderColor(selected ? 0xFFF6E27F : element.color, hidden);
        drawLineStrip(poseStack, linePoints, color, ARROW_STROKE_WIDTH);

        ScreenPoint start = linePoints.get(linePoints.size() - 2);
        ScreenPoint end = linePoints.get(linePoints.size() - 1);
        double dx = end.x() - start.x();
        double dy = end.y() - start.y();
        double length = Math.sqrt(dx * dx + dy * dy);
        if (length < 0.001D) {
            return;
        }
        double ux = dx / length;
        double uy = dy / length;
        double px = -uy;
        double py = ux;
        double headLength = 10.0D;
        double headWidth = 5.0D;
        ScreenPoint headA = new ScreenPoint(end.x() - ux * headLength + px * headWidth, end.y() - uy * headLength + py * headWidth);
        ScreenPoint headB = new ScreenPoint(end.x() - ux * headLength - px * headWidth, end.y() - uy * headLength - py * headWidth);
        drawLineStrip(poseStack, List.of(end, headA), color, ARROW_STROKE_WIDTH);
        drawLineStrip(poseStack, List.of(end, headB), color, ARROW_STROKE_WIDTH);
    }

    private void drawTextElement(
            PoseStack poseStack,
            TextElement element,
            boolean selected,
            boolean hidden
    ) {
        ScreenPoint point = canvasToScreen(new CanvasPoint(element.x, element.y));
        CanvasBounds textBounds = element.bounds(this);
        ScreenRect screenBounds = canvasBoundsToScreenRect(textBounds);
        int left = screenBounds.left() - 3;
        int top = screenBounds.top() - 2;
        int right = screenBounds.right() + 3;
        int bottom = screenBounds.bottom() + 2;
        if (isCommandTextElement(element)) {
            fill(poseStack, left, top, right, bottom, renderColor(0xD9000000, hidden));
            drawScreenRectOutline(poseStack, new ScreenRect(left, top, right, bottom), renderColor(0xFFF1F5FB, hidden));
        }
        if (selected) {
            fill(poseStack, left, top, right, bottom, renderColor(0x33F6E27F, hidden));
        }
        String[] lines = textLines(element);
        poseStack.pushPose();
        poseStack.translate(point.x(), point.y(), 0.0D);
        float renderScale = (float) (element.textScale * zoom);
        poseStack.scale(renderScale, renderScale, 1.0F);
        for (int lineIndex = 0; lineIndex < lines.length; lineIndex++) {
            drawString(poseStack, font, lines[lineIndex], 0, lineIndex * font.lineHeight, renderColor(element.color, hidden));
        }

        if (element.id() == textEditingElementId && (Util.getMillis() / 400L) % 2L == 0L) {
            CaretPlacement caretPlacement = caretPlacement(element, textEditingCaretIndex);
            fill(poseStack, caretPlacement.x(), caretPlacement.y() - 1, caretPlacement.x() + 1, caretPlacement.y() + font.lineHeight + 1, renderColor(0xFFF1F5FB, hidden));
        }
        poseStack.popPose();
    }

    private void drawFreehandElement(
            PoseStack poseStack,
            FreehandElement element,
            boolean selected,
            boolean hidden
    ) {
        if (element.points.size() < 2) {
            return;
        }
        List<ScreenPoint> screenPoints = new ArrayList<>(element.points.size());
        for (CanvasPoint point : element.points) {
            screenPoints.add(canvasToScreen(point));
        }
        drawLineStrip(poseStack, screenPoints, renderColor(selected ? 0xFFF6E27F : element.color, hidden));
    }

    private void drawPendingArrowPreview(PoseStack poseStack) {
        List<CanvasPoint> previewPoints = new ArrayList<>(pendingArrowAnchors.size() + 1);
        previewPoints.addAll(pendingArrowAnchors);
        previewPoints.add(screenToCanvas(chromeMouseX, chromeMouseY, pendingArrowProjection));
        if (previewPoints.size() < 2) {
            return;
        }

        List<ScreenPoint> linePoints = new ArrayList<>(previewPoints.size());
        for (CanvasPoint point : previewPoints) {
            linePoints.add(projectCanvasPointToScreen(point, pendingArrowProjection));
        }
        drawDashedLineStrip(poseStack, linePoints, 0xFFE8A652, ARROW_STROKE_WIDTH);
    }

    private void drawLineStrip(
            PoseStack poseStack,
            List<ScreenPoint> points,
            int color
    ) {
        drawLineStrip(poseStack, points, color, 1.0F);
    }

    private void drawLineStrip(
            PoseStack poseStack,
            List<ScreenPoint> points,
            int color,
            float lineWidth
    ) {
        if (points.size() < 2) {
            return;
        }

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.lineWidth(lineWidth);
        Matrix4f pose = poseStack.last().pose();
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder bufferBuilder = tesselator.getBuilder();
        bufferBuilder.begin(VertexFormat.Mode.DEBUG_LINE_STRIP, DefaultVertexFormat.POSITION_COLOR);
        float red = ((color >> 16) & 0xFF) / 255.0F;
        float green = ((color >> 8) & 0xFF) / 255.0F;
        float blue = (color & 0xFF) / 255.0F;
        float alpha = ((color >> 24) & 0xFF) / 255.0F;
        for (ScreenPoint point : points) {
            bufferBuilder.vertex(pose, (float) point.x(), (float) point.y(), 0.0F).color(red, green, blue, alpha).endVertex();
        }
        tesselator.end();
        RenderSystem.lineWidth(1.0F);
        RenderSystem.disableBlend();
    }

    private void drawDashedLineStrip(
            PoseStack poseStack,
            List<ScreenPoint> points,
            int color,
            float lineWidth
    ) {
        if (points.size() < 2) {
            return;
        }

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.lineWidth(lineWidth);
        Matrix4f pose = poseStack.last().pose();
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder bufferBuilder = tesselator.getBuilder();
        bufferBuilder.begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);
        float red = ((color >> 16) & 0xFF) / 255.0F;
        float green = ((color >> 8) & 0xFF) / 255.0F;
        float blue = (color & 0xFF) / 255.0F;
        float alpha = ((color >> 24) & 0xFF) / 255.0F;
        final double dashLength = 10.0D;
        final double gapLength = 6.0D;
        for (int i = 1; i < points.size(); i++) {
            ScreenPoint start = points.get(i - 1);
            ScreenPoint end = points.get(i);
            double dx = end.x() - start.x();
            double dy = end.y() - start.y();
            double length = Math.sqrt(dx * dx + dy * dy);
            if (length <= 0.000001D) {
                continue;
            }
            double ux = dx / length;
            double uy = dy / length;
            for (double offset = 0.0D; offset < length; offset += dashLength + gapLength) {
                double dashEnd = Math.min(length, offset + dashLength);
                float ax = (float) (start.x() + ux * offset);
                float ay = (float) (start.y() + uy * offset);
                float bx = (float) (start.x() + ux * dashEnd);
                float by = (float) (start.y() + uy * dashEnd);
                bufferBuilder.vertex(pose, ax, ay, 0.0F).color(red, green, blue, alpha).endVertex();
                bufferBuilder.vertex(pose, bx, by, 0.0F).color(red, green, blue, alpha).endVertex();
            }
        }
        tesselator.end();
        RenderSystem.lineWidth(1.0F);
        RenderSystem.disableBlend();
    }

    // r[impl draw.chrome.minimap]
    private void drawMinimap(PoseStack poseStack) {
        Rect overlayBounds = cameraOverlayBounds();
        boolean hidden = minimapWidget.hidden();
        fill(poseStack, overlayBounds.left(), overlayBounds.top(), overlayBounds.right(), overlayBounds.bottom(), renderColor(0xCC10141A, hidden));
        drawScreenRectOutline(poseStack, new ScreenRect(overlayBounds.left(), overlayBounds.top(), overlayBounds.right(), overlayBounds.bottom()), renderColor(0xFF38404B, hidden));
        drawString(poseStack, font, IdeLocalizationKeys.IDE_DRAW_MINIMAP_TITLE.getComponent(), overlayBounds.left() + 6, overlayBounds.top() + 6, renderColor(0xD5D9E0, hidden));

        @Nullable CameraOverlayProjection projection = cameraOverlayRenderProjection();
        if (projection == null) {
            return;
        }

        Rect mapBounds = projection.mapBounds();
        fill(poseStack, mapBounds.left(), mapBounds.top(), mapBounds.right(), mapBounds.bottom(), renderColor(0x55182229, hidden));

        for (DrawElement element : elements) {
            if (!shouldRenderElement(element)) {
                continue;
            }
            CanvasBounds elementBounds = element.bounds(this);
            int elementLeft = mapBounds.left() + (int) Math.round((elementBounds.minX() - projection.sceneBounds().minX()) * projection.scale());
            int elementRight = mapBounds.left() + (int) Math.round((elementBounds.maxX() - projection.sceneBounds().minX()) * projection.scale());
            int elementTop = mapBounds.top() + (int) Math.round((elementBounds.minY() - projection.sceneBounds().minY()) * projection.scale());
            int elementBottom = mapBounds.top() + (int) Math.round((elementBounds.maxY() - projection.sceneBounds().minY()) * projection.scale());
            if (elementRight <= elementLeft) {
                elementRight = elementLeft + 2;
            }
            if (elementBottom <= elementTop) {
                elementBottom = elementTop + 2;
            }
            fill(poseStack, elementLeft, elementTop, elementRight, elementBottom, renderColor(isElementSelected(element) ? 0xAAF6E27F : 0x8877C8FF, hidden || element.hidden()));
        }

        CanvasBounds visibleBounds = visibleCanvasBounds();
        int viewportLeft = mapBounds.left() + (int) Math.round((visibleBounds.minX() - projection.sceneBounds().minX()) * projection.scale());
        int viewportRight = mapBounds.left() + (int) Math.round((visibleBounds.maxX() - projection.sceneBounds().minX()) * projection.scale());
        int viewportTop = mapBounds.top() + (int) Math.round((visibleBounds.minY() - projection.sceneBounds().minY()) * projection.scale());
        int viewportBottom = mapBounds.top() + (int) Math.round((visibleBounds.maxY() - projection.sceneBounds().minY()) * projection.scale());
        drawScreenRectOutline(poseStack, new ScreenRect(viewportLeft, viewportTop, viewportRight, viewportBottom), renderColor(0xFFE8A652, hidden));

        int originX = mapBounds.left() + (int) Math.round((0.0D - projection.sceneBounds().minX()) * projection.scale());
        int originY = mapBounds.top() + (int) Math.round((0.0D - projection.sceneBounds().minY()) * projection.scale());
        fill(poseStack, originX - 1, mapBounds.top(), originX + 1, mapBounds.bottom(), renderColor(0x558AC6FF, hidden));
        fill(poseStack, mapBounds.left(), originY - 1, mapBounds.right(), originY + 1, renderColor(0x558AC6FF, hidden));

        if (draftInteraction != null && draftInteraction.projection() != null) {
            drawBoundsOrPathInProjection(poseStack, draftInteraction, projection);
        }
        if (marqueeSelectionDrag != null && marqueeSelectionDrag.projection() != null) {
            drawBoundsInProjection(
                    poseStack,
                    CanvasBounds.of(marqueeSelectionDrag.startPoint.x(), marqueeSelectionDrag.startPoint.y(), marqueeSelectionDrag.currentPoint.x(), marqueeSelectionDrag.currentPoint.y()),
                    projection,
                    0xAA88C0FF,
                    0x2288C0FF
            );
        }
        if (cameraFrameDrag != null) {
            drawBoundsInProjection(
                    poseStack,
                    CanvasBounds.of(cameraFrameDrag.startPoint.x(), cameraFrameDrag.startPoint.y(), cameraFrameDrag.currentPoint.x(), cameraFrameDrag.currentPoint.y()),
                    cameraFrameDrag.projection(),
                    0xFFE8A652,
                    0x22E8A652
            );
        }

    }

    private void drawBoundsOrPathInProjection(
            PoseStack poseStack,
            DraftInteraction interaction,
            CameraOverlayProjection projection
    ) {
        switch (interaction.tool()) {
            case RECTANGLE -> drawBoundsInProjection(
                    poseStack,
                    CanvasBounds.of(interaction.startPoint().x(), interaction.startPoint().y(), interaction.currentPoint().x(), interaction.currentPoint().y()),
                    projection,
                    0xFF7FD7FF,
                    0x222FB5FF
            );
            case ARROW -> drawLineStrip(poseStack, List.of(projection.canvasToScreen(interaction.startPoint()), projection.canvasToScreen(interaction.currentPoint())), 0xFFE8A652, ARROW_STROKE_WIDTH);
            case FREEHAND -> {
                if (interaction.points().size() >= 2) {
                    List<ScreenPoint> points = new ArrayList<>(interaction.points().size());
                    for (CanvasPoint point : interaction.points()) {
                        points.add(projection.canvasToScreen(point));
                    }
                    drawLineStrip(poseStack, points, 0xFF88D498);
                }
            }
            default -> {
            }
        }
    }

    private void drawBoundsInProjection(
            PoseStack poseStack,
            CanvasBounds bounds,
            @Nullable CameraOverlayProjection projection,
            int strokeColor,
            int fillColor
    ) {
        ScreenPoint topLeft = projectCanvasPointToScreen(new CanvasPoint(bounds.minX(), bounds.minY()), projection);
        ScreenPoint bottomRight = projectCanvasPointToScreen(new CanvasPoint(bounds.maxX(), bounds.maxY()), projection);
        ScreenRect rect = new ScreenRect(
                (int) Math.round(Math.min(topLeft.x(), bottomRight.x())),
                (int) Math.round(Math.min(topLeft.y(), bottomRight.y())),
                (int) Math.round(Math.max(topLeft.x(), bottomRight.x())),
                (int) Math.round(Math.max(topLeft.y(), bottomRight.y()))
        );
        fill(poseStack, rect.left(), rect.top(), rect.right(), rect.bottom(), fillColor);
        drawScreenRectOutline(poseStack, rect, strokeColor);
    }

    // r[impl draw.chrome.hotbar]
    // r[impl draw.chrome.hotbar.parts.independent]
    private void drawToolHotbar(
            PoseStack poseStack,
            int mouseX,
            int mouseY
    ) {
        if (isChromeWidgetRendered(ChromeWidget.HOTBAR_TITLE)) {
            drawTextChromeWidget(
                    poseStack,
                    hotbarTitleBounds(),
                    hotbarTitleWidget.scale(),
                    IdeLocalizationKeys.IDE_DRAW_HOTBAR_TITLE.getComponent(),
                    renderColor(0xCC11161E, hotbarTitleWidget.hidden()),
                    renderColor(0xE4E8EF, hotbarTitleWidget.hidden())
            );
        }

        if (isChromeWidgetRendered(ChromeWidget.HOTBAR_BAR)) {
            poseStack.pushPose();
            poseStack.translate(hotbarWidget.x(), hotbarWidget.y(), 0.0D);
            poseStack.scale((float) hotbarWidget.scale(), (float) hotbarWidget.scale(), 1.0F);
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, hotbarWidget.hidden() ? 0.35F : 1.0F);
            RenderSystem.setShader(GameRenderer::getPositionTexShader);
            RenderSystem.setShaderTexture(0, AbstractWidget.WIDGETS_LOCATION);
            blit(poseStack, 0, 0, 0, 0, HOTBAR_WIDTH, HOTBAR_HEIGHT);

            if (activeTool.selectable() && activeTool.ordinal() < TOOL_COUNT) {
                blit(poseStack, -1 + activeTool.ordinal() * 20, -1, 0, 22, 24, HOTBAR_HEIGHT);
            }

            for (int index = 0; index < TOOL_COUNT; index++) {
                DrawTool tool = DrawTool.VALUES[index];
                drawToolIcon(poseStack, tool, 3 + index * 20, 3);
            }
            poseStack.popPose();
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

            if (cameraOverlayVisible) {
                Rect cameraSlotBounds = hotbarSlotBounds(DrawTool.CAMERA.ordinal());
                drawScreenRectOutline(
                        poseStack,
                        new ScreenRect(cameraSlotBounds.left() - 2, cameraSlotBounds.top() - 2, cameraSlotBounds.right() + 2, cameraSlotBounds.bottom() + 2),
                        renderColor(0xFFE8A652, hotbarWidget.hidden())
                );
            }
            if (layerWindowVisible) {
                Rect layerSlotBounds = hotbarSlotBounds(DrawTool.LAYER.ordinal());
                drawScreenRectOutline(
                        poseStack,
                        new ScreenRect(layerSlotBounds.left() - 2, layerSlotBounds.top() - 2, layerSlotBounds.right() + 2, layerSlotBounds.bottom() + 2),
                        renderColor(activeLayer.color(), hotbarWidget.hidden())
                );
            }

            int hoveredToolIndex = hoveredToolIndex(mouseX, mouseY);
            if (hoveredToolIndex >= 0) {
                Rect slotBounds = hotbarSlotBounds(hoveredToolIndex);
                fill(poseStack, slotBounds.left(), slotBounds.top(), slotBounds.right(), slotBounds.bottom(), renderColor(0x2238A3FF, hotbarWidget.hidden()));
            }

        }

        drawAuxiliaryHotbar(poseStack, mouseX, mouseY);

        @Nullable DrawTool describedTool = hoveredAuxiliaryTool(mouseX, mouseY);
        if (describedTool == null) {
            int hoveredToolIndex = hoveredToolIndex(mouseX, mouseY);
            describedTool = hoveredToolIndex >= 0 ? DrawTool.VALUES[hoveredToolIndex] : activeTool;
        }
        if (isChromeWidgetRendered(ChromeWidget.HOTBAR_SUBTITLE)) {
            drawHotbarSubtitle(poseStack, describedTool);
        }
    }

    private void drawAuxiliaryHotbar(
            PoseStack poseStack,
            int mouseX,
            int mouseY
    ) {
        if (!isChromeWidgetRendered(ChromeWidget.AUXILIARY_HOTBAR_BAR)) {
            return;
        }

        Rect bounds = auxiliaryHotbarBounds();
        boolean hidden = auxiliaryHotbarWidget.hidden();
        fill(poseStack, bounds.left(), bounds.top(), bounds.right(), bounds.bottom(), renderColor(0xCC11161E, hidden));
        drawScreenRectOutline(poseStack, new ScreenRect(bounds.left(), bounds.top(), bounds.right(), bounds.bottom()), renderColor(0xFF606975, hidden));

        Rect slotBounds = auxiliaryHotbarSlotBounds();
        fill(poseStack, slotBounds.left(), slotBounds.top(), slotBounds.right(), slotBounds.bottom(), renderColor(0x7710141A, hidden));
        if (hoveredAuxiliaryTool(mouseX, mouseY) != null) {
            fill(poseStack, slotBounds.left(), slotBounds.top(), slotBounds.right(), slotBounds.bottom(), renderColor(0x2238A3FF, hidden));
        }

        poseStack.pushPose();
        poseStack.translate(auxiliaryHotbarWidget.x(), auxiliaryHotbarWidget.y(), 0.0D);
        poseStack.scale((float) auxiliaryHotbarWidget.scale(), (float) auxiliaryHotbarWidget.scale(), 1.0F);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, auxiliaryHotbarWidget.hidden() ? 0.35F : 1.0F);
        drawToolIcon(poseStack, DrawTool.LOCK, 3, 3);
        poseStack.popPose();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private void drawHotbarSubtitle(
            PoseStack poseStack,
            DrawTool describedTool
    ) {
        Component label = Component.literal(describeTool(describedTool));
        drawTextChromeWidget(
                poseStack,
                hotbarSubtitleBounds(label),
                hotbarSubtitleWidget.scale(),
                label,
                renderColor(0xCC10141A, hotbarSubtitleWidget.hidden()),
                renderColor(0xDCE2EC, hotbarSubtitleWidget.hidden())
        );
    }

    private void drawStatusTextWidget(
            PoseStack poseStack,
            ChromeWidget widget,
            Component label,
            int backgroundColor,
            int textColor
    ) {
        if (!isChromeWidgetRendered(widget)) {
            return;
        }
        ChromeWidgetState state = chromeWidgetState(widget);
        drawTextChromeWidget(
                poseStack,
                textChromeWidgetBounds(state, label),
                state.scale(),
                label,
            backgroundColor == 0 ? 0 : renderColor(backgroundColor, state.hidden()),
                renderColor(textColor, state.hidden())
        );
    }

    private void drawTextChromeWidget(
            PoseStack poseStack,
            Rect bounds,
            double scale,
            Component label,
            int backgroundColor,
            int textColor
    ) {
        fill(poseStack, bounds.left(), bounds.top(), bounds.right(), bounds.bottom(), backgroundColor);
        poseStack.pushPose();
        poseStack.translate(bounds.left() + scaledPadding(scale), bounds.top() + Math.max(1, scaledPadding(scale) / 2), 0.0D);
        poseStack.scale((float) scale, (float) scale, 1.0F);
        drawString(poseStack, font, label, 0, 0, textColor);
        poseStack.popPose();
    }

    private void drawToolIcon(
            PoseStack poseStack,
            DrawTool tool,
            int left,
            int top
    ) {
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, tool.iconLocation());
        blit(poseStack, left, top, 0.0F, 0.0F, 16, 16, 16, 16);
    }

    private String describeTool(DrawTool tool) {
        if (tool == DrawTool.ARROW && !pendingArrowAnchors.isEmpty()) {
            // r[impl draw.tool.arrow.multisegment.status]
            return tool.label() + " [a] placing " + pendingArrowAnchors.size() + (pendingArrowAnchors.size() == 1 ? " anchor" : " anchors");
        }
        if (tool == DrawTool.CURSOR) {
            return tool.label() + " [" + tool.shortcutGlyph().toLowerCase(Locale.ROOT) + "] " + (canSelectLockedElements ? "select locked on" : "select locked off");
        }
        if (tool == DrawTool.LOCK) {
            List<DrawElement> selectedElements = selectedLockableElements();
            if (!selectedElements.isEmpty()) {
                return tool.label() + " [" + tool.shortcutGlyph().toLowerCase(Locale.ROOT) + "] " + (nextLockedValueForElements(selectedElements) ? "lock selected" : "unlock selected");
            }
            return tool.label() + " [" + tool.shortcutGlyph().toLowerCase(Locale.ROOT) + "] " + (canSelectLockedElements ? "select locked on" : "select locked off");
        }
        if (tool == DrawTool.CAMERA) {
            return tool.label() + " [c] " + (cameraOverlayVisible ? "on" : "off");
        }
        if (tool == DrawTool.LAYER) {
            return tool.label() + " [" + tool.shortcutGlyph().toLowerCase() + "] " + activeLayer.label() + (layerWindowVisible ? " open" : " closed");
        }
        if (tool == DrawTool.ZEN) {
            return tool.label() + " [" + tool.shortcutGlyph().toLowerCase() + "] " + (isZenSoloActiveFor(activeLayer) ? "solo" : "all");
        }
        String stickySuffix = stickyToolMode ? " sticky" : " one-shot";
        return tool.label() + " [" + tool.shortcutGlyph().toLowerCase() + "]" + (tool == DrawTool.CURSOR || tool == DrawTool.HAND ? "" : stickySuffix);
    }

    private boolean supportsStickyMode(DrawTool tool) {
        return switch (tool) {
            case RECTANGLE, ARROW, TEXT, FREEHAND -> true;
            default -> false;
        };
    }

    private Component chromeWidgetText(ChromeWidget widget) {
        CanvasPoint cursorPoint = currentChromeCursorPoint();
        return switch (widget) {
            case SCREEN_TITLE -> IdeLocalizationKeys.IDE_DRAW_TITLE.getComponent();
            case SCREEN_SUBTITLE -> IdeLocalizationKeys.IDE_DRAW_SUBTITLE.getComponent();
            case ACTIVE_LAYER_LABEL -> IdeLocalizationKeys.IDE_DRAW_LAYER_LABEL_TEXT.getComponent();
            case ACTIVE_LAYER_VALUE -> activeLayer.labelComponent();
            case CAMERA_POSITION_LABEL -> IdeLocalizationKeys.IDE_DRAW_CAMERA_POSITION_LABEL.getComponent();
            case CAMERA_POSITION_X -> Component.literal(String.format("%.0f", cameraX));
            case CAMERA_POSITION_SEPARATOR, CURSOR_SEPARATOR -> Component.literal(",");
            case CAMERA_POSITION_Y -> Component.literal(String.format("%.0f", cameraY));
            case ZOOM_LABEL -> IdeLocalizationKeys.IDE_DRAW_ZOOM_LABEL.getComponent();
            case ZOOM_VALUE -> Component.literal(String.format("%.2fx", zoom));
            case CURSOR_LABEL -> IdeLocalizationKeys.IDE_DRAW_CURSOR_LABEL.getComponent();
            case CURSOR_X -> Component.literal(String.format("%.0f", cursorPoint.x()));
            case CURSOR_Y -> Component.literal(String.format("%.0f", cursorPoint.y()));
            case HOTBAR_TITLE -> IdeLocalizationKeys.IDE_DRAW_HOTBAR_TITLE.getComponent();
            case HOTBAR_SUBTITLE -> Component.literal(describeTool(activeTool));
            case MINIMAP -> IdeLocalizationKeys.IDE_DRAW_MINIMAP_TITLE.getComponent();
            case LAYER_WINDOW -> IdeLocalizationKeys.IDE_DRAW_LAYER_WINDOW_TITLE.getComponent();
            case HOTBAR_BAR -> IdeLocalizationKeys.IDE_DRAW_HOTBAR_TITLE.getComponent();
            case AUXILIARY_HOTBAR_BAR -> IdeLocalizationKeys.IDE_DRAW_HOTBAR_AUXILIARY_TITLE.getComponent();
        };
    }

    private CanvasPoint currentChromeCursorPoint() {
        @Nullable CameraOverlayProjection overlayProjection = cameraOverlayInteractionProjection();
        if (overlayProjection != null && overlayProjection.mapBounds().contains(chromeMouseX, chromeMouseY)) {
            return overlayProjection.screenToCanvas(chromeMouseX, chromeMouseY);
        }
        return screenToCanvas(chromeMouseX, chromeMouseY, null);
    }

    private void initializeStatusChromeWidgets() {
        ensureChromeWidgetPosition(ChromeWidget.SCREEN_TITLE, 12, 12);
        ensureChromeWidgetPosition(ChromeWidget.SCREEN_SUBTITLE, 12, 24);
        ensureChromeWidgetPosition(ChromeWidget.ACTIVE_LAYER_LABEL, 12, 36);
        ensureChromeWidgetPosition(ChromeWidget.ACTIVE_LAYER_VALUE, chromeWidgetBounds(ChromeWidget.ACTIVE_LAYER_LABEL).right() + 4, 36);

        int topRowY = 12;
        int secondRowY = 24;
        int rightEdge = width - 12;

        rightEdge = initializeRightAlignedTextWidget(ChromeWidget.ZOOM_VALUE, rightEdge, topRowY, 0);
        rightEdge = initializeRightAlignedTextWidget(ChromeWidget.ZOOM_LABEL, rightEdge, topRowY, 4);
        rightEdge = initializeRightAlignedTextWidget(ChromeWidget.CAMERA_POSITION_Y, rightEdge, topRowY, 12);
        rightEdge = initializeRightAlignedTextWidget(ChromeWidget.CAMERA_POSITION_SEPARATOR, rightEdge, topRowY, 2);
        rightEdge = initializeRightAlignedTextWidget(ChromeWidget.CAMERA_POSITION_X, rightEdge, topRowY, 2);
        initializeRightAlignedTextWidget(ChromeWidget.CAMERA_POSITION_LABEL, rightEdge, topRowY, 4);

        rightEdge = width - 12;
        rightEdge = initializeRightAlignedTextWidget(ChromeWidget.CURSOR_Y, rightEdge, secondRowY, 0);
        rightEdge = initializeRightAlignedTextWidget(ChromeWidget.CURSOR_SEPARATOR, rightEdge, secondRowY, 2);
        rightEdge = initializeRightAlignedTextWidget(ChromeWidget.CURSOR_X, rightEdge, secondRowY, 2);
        initializeRightAlignedTextWidget(ChromeWidget.CURSOR_LABEL, rightEdge, secondRowY, 4);

        ensureChromeWidgetPosition(ChromeWidget.HOTBAR_TITLE, hotbarWidget.x(), Math.max(8, hotbarWidget.y() - font.lineHeight - 10));
        ensureChromeWidgetPosition(ChromeWidget.HOTBAR_SUBTITLE, hotbarWidget.x(), Math.min(height - font.lineHeight - 12, hotbarWidget.y() + hotbarHeight() + 6));
    }

    private int initializeRightAlignedTextWidget(
            ChromeWidget widget,
            int rightEdge,
            int y,
            int gapAfter
    ) {
        ChromeWidgetState state = chromeWidgetState(widget);
        Rect bounds = textChromeWidgetBounds(state, chromeWidgetText(widget));
        int x = rightEdge - bounds.width();
        ensureChromeWidgetPosition(widget, x, y);
        return x - gapAfter;
    }

    private void ensureChromeWidgetPosition(
            ChromeWidget widget,
            int x,
            int y
    ) {
        ChromeWidgetState state = chromeWidgetState(widget);
        if (state.x() == Integer.MIN_VALUE || state.y() == Integer.MIN_VALUE) {
            setChromeWidgetState(widget, state.withPosition(x, y));
        }
    }

    private void beginPan(
            double mouseX,
            double mouseY,
            int button
    ) {
        panning = true;
        panButton = button;
        panAnchorMouseX = mouseX;
        panAnchorMouseY = mouseY;
        panAnchorCameraX = cameraX;
        panAnchorCameraY = cameraY;
    }

    private void handleHotbarToolClick(DrawTool tool) {
        if (textEditingElementId >= 0 && tool != DrawTool.TEXT) {
            finishTextEditing();
        }
        if (tool == DrawTool.LOCK) {
            handleLockAction();
            return;
        }
        // r[impl draw.tool.layer.opens-layer-window]
        // r[impl draw.tool.layer.toggles-layer-window]
        if (tool == DrawTool.LAYER) {
            layerWindowVisible = !layerWindowVisible;
            activeTool = layerWindowVisible ? DrawTool.LAYER : DrawTool.CURSOR;
            return;
        }
        // r[impl draw.camera.tool.selection-toggles-minimap]
        if (tool == DrawTool.CAMERA) {
            cameraOverlayVisible = !cameraOverlayVisible;
            activeTool = DrawTool.CAMERA;
            return;
        }
        // r[impl draw.tool.zen.exists]
        // r[impl draw.layer.zen.solo-current]
        // r[impl draw.layer.zen.toggle-restores]
        if (tool == DrawTool.ZEN) {
            toggleZenSoloFor(activeLayer);
            return;
        }
        if (tool.selectable()) {
            if (tool == activeTool && supportsStickyMode(tool)) {
                stickyToolMode = !stickyToolMode;
                return;
            }
            activeTool = tool;
        }
    }

    // r[impl draw.tool.text.create]
    // r[impl draw.tool.text.edit]
    private boolean handleCursorDoubleClick(CanvasPoint canvasPoint) {
        int hitElementId = findTopElementAt(canvasPoint);
        if (hitElementId >= 0) {
            DrawElement element = findElementById(hitElementId);
            if (element instanceof TextElement textElement && textElement.shellBinding == null) {
                selectOnly(hitElementId);
                activeTool = DrawTool.TEXT;
                textEditingElementId = hitElementId;
                textEditingCaretIndex = textElement.text.length();
                return true;
            }
            return false;
        }

        TextElement textElement = createTextElement(snapCanvasPointToCurrentIncrement(canvasPoint));
        activeTool = DrawTool.TEXT;
        textEditingElementId = textElement.id();
        return true;
    }

    // r[impl draw.tool.cursor.selection]
    // r[impl draw.tool.cursor.marquee]
    // r[impl draw.tool.cursor.transform_selection]
    // r[impl draw.tool.cursor.duplicate_selection]
    // r[impl draw.tool.cursor.selection.additive]
    // r[impl draw.tool.cursor.selection.subtractive]
    private void handleCursorPress(
            double mouseX,
            double mouseY,
            CanvasPoint canvasPoint,
            @Nullable CameraOverlayProjection projection
    ) {
        SelectionMode selectionMode = selectionMode();
        CanvasBounds selectionBounds = currentSelectionBounds();
        @Nullable ArrowAnchorReference hitArrowAnchor = findVisibleArrowAnchorAt(canvasPoint);
        if (hitArrowAnchor != null) {
            applyArrowAnchorSelection(hitArrowAnchor, selectionMode);
            if (selectionMode == SelectionMode.ADD) {
                return;
            }
            if (selectionMode == SelectionMode.SUBTRACT) {
                return;
            }
            // r[impl draw.tool.arrow.anchors.selectable]
            if (!selectedArrowAnchors.contains(hitArrowAnchor)) {
                return;
            }
            CanvasBounds anchorSelectionBounds = currentSelectionBounds();
            if (anchorSelectionBounds != null) {
                moveSelectionDrag = new MoveSelectionDrag(selectionSnapshot(), arrowAnchorSelectionSnapshot(), canvasPoint, projection, anchorSelectionBounds, selectionSnapOrigin());
            }
            duplicateSelectionPendingOnDrag = false;
            return;
        }

        if (selectionMode == SelectionMode.REPLACE && projection == null && selectionBounds != null && canResizeCurrentSelection()) {
            SelectionHandle handle = findSelectionHandle(mouseX, mouseY, selectionBounds);
            if (handle != null) {
                resizeSelectionDrag = new ResizeSelectionDrag(selectionSnapshot(), selectionBounds, handle, projection);
                return;
            }
        }

        if (selectionMode == SelectionMode.REPLACE && selectionBounds != null && canTransformCurrentSelection() && selectionBounds.contains(canvasPoint)) {
            // r[impl draw.tool.cursor.duplicate_selection.alt-drag]
            duplicateSelectionPendingOnDrag = hasAltDown();
            moveSelectionDrag = new MoveSelectionDrag(selectionSnapshot(), arrowAnchorSelectionSnapshot(), canvasPoint, projection, selectionBounds, selectionSnapOrigin());
            return;
        }

        int hitElementId = findTopElementAt(canvasPoint);
        if (hitElementId >= 0) {
            DrawElement hitElement = findElementById(hitElementId);
            if (hitElement instanceof ArrowElement arrowElement) {
                if (selectionMode == SelectionMode.ADD) {
                    addElementComponentToSelection(groupConnectedElementIds(arrowElement.id()));
                    return;
                }
                if (selectionMode == SelectionMode.SUBTRACT) {
                    removeElementComponentFromSelection(groupConnectedElementIds(arrowElement.id()));
                    return;
                }
                // r[impl draw.tool.arrow.line-selects-anchors]
                selectOnlyArrow(arrowElement);
                CanvasBounds arrowSelectionBounds = currentSelectionBounds();
                if (arrowSelectionBounds != null && canTransformCurrentSelection()) {
                    moveSelectionDrag = new MoveSelectionDrag(selectionSnapshot(), arrowAnchorSelectionSnapshot(), canvasPoint, projection, arrowSelectionBounds, selectionSnapOrigin());
                }
                duplicateSelectionPendingOnDrag = false;
                return;
            }
            if (selectionMode == SelectionMode.ADD) {
                addElementComponentToSelection(groupConnectedElementIds(hitElementId));
                return;
            }
            if (selectionMode == SelectionMode.SUBTRACT) {
                removeElementComponentFromSelection(groupConnectedElementIds(hitElementId));
                return;
            }
            if (!selectedElementIds.contains(hitElementId)) {
                selectOnly(hitElementId);
            }
            // r[impl draw.tool.cursor.duplicate_selection.alt-drag]
            duplicateSelectionPendingOnDrag = hasAltDown();
            CanvasBounds moveBounds = currentSelectionBounds();
            if (moveBounds != null && canTransformCurrentSelection()) {
                moveSelectionDrag = new MoveSelectionDrag(selectionSnapshot(), arrowAnchorSelectionSnapshot(), canvasPoint, projection, moveBounds, selectionSnapOrigin());
            }
            return;
        }

        duplicateSelectionPendingOnDrag = false;
        marqueeSelectionDrag = new MarqueeSelectionDrag(canvasPoint, canvasPoint, projection, selectionMode);
    }

    private void applyMoveSelectionDrag(
            MoveSelectionDrag drag,
            CanvasPoint currentPoint
    ) {
        if (duplicateSelectionPendingOnDrag) {
            duplicateSelection();
            CanvasBounds duplicatedSelectionBounds = currentSelectionBounds();
            moveSelectionDrag = duplicatedSelectionBounds == null
                    ? null
                    : new MoveSelectionDrag(selectionSnapshot(), arrowAnchorSelectionSnapshot(), drag.startPoint(), drag.projection(), duplicatedSelectionBounds, selectionSnapOrigin());
            duplicateSelectionPendingOnDrag = false;
            drag = moveSelectionDrag;
            if (drag == null) {
                return;
            }
        }
        // r[impl draw.tool.cursor.transform_selection.drag-snap]
        CanvasPoint snappedDelta = snappedMoveDelta(drag, currentPoint);
        double dx = snappedDelta.x();
        double dy = snappedDelta.y();
        for (ElementSnapshot snapshot : drag.snapshots()) {
            DrawElement element = findElementById(snapshot.id());
            if (element == null) {
                continue;
            }
            element.copyFrom(snapshot.elementCopy());
            element.translate(dx, dy);
        }
        // r[impl draw.tool.cursor.transform_selection.mixed-elements-and-arrow-anchors]
        for (ArrowAnchorSnapshot anchorSnapshot : drag.anchorSnapshots()) {
            DrawElement element = findElementById(anchorSnapshot.arrowId());
            if (!(element instanceof ArrowElement arrowElement) || !arrowElement.hasPointIndex(anchorSnapshot.anchorIndex())) {
                continue;
            }
            arrowElement.points.set(anchorSnapshot.anchorIndex(), new CanvasPoint(anchorSnapshot.originalPoint().x() + dx, anchorSnapshot.originalPoint().y() + dy));
        }
    }

    private void applyResizeSelectionDrag(
            ResizeSelectionDrag drag,
            CanvasPoint currentPoint
    ) {
        // r[impl draw.tool.cursor.transform_selection.resize-snap]
        CanvasBounds newBounds = resizedBounds(
            drag.originalBounds(),
            drag.handle(),
            currentPoint,
            8.0D / Math.max(zoom, 0.01D),
            movementSnapIncrement()
        );
        for (ElementSnapshot snapshot : drag.snapshots()) {
            DrawElement element = findElementById(snapshot.id());
            if (element == null) {
                continue;
            }
            element.copyFrom(snapshot.elementCopy());
            element.transform(drag.originalBounds(), newBounds, this);
        }
    }

    private void finalizeMarqueeSelection() {
        if (marqueeSelectionDrag == null) {
            return;
        }

        SelectionMode mode = marqueeSelectionDrag.mode();

        CanvasBounds marqueeBounds = CanvasBounds.of(
                marqueeSelectionDrag.startPoint.x(),
                marqueeSelectionDrag.startPoint.y(),
                marqueeSelectionDrag.currentPoint.x(),
                marqueeSelectionDrag.currentPoint.y()
        );
        marqueeSelectionDrag = null;

        if (marqueeBounds.width() < 1.0D && marqueeBounds.height() < 1.0D) {
            if (mode == SelectionMode.REPLACE) {
                clearCanvasSelection();
            }
            return;
        }

        Set<Integer> hits = new LinkedHashSet<>();
        Set<ArrowAnchorReference> arrowHits = new LinkedHashSet<>();
        for (DrawElement element : elements) {
            if (!isElementSelectable(element)) {
                continue;
            }
            if (element instanceof ArrowElement arrowElement) {
                // r[impl draw.tool.cursor.marquee.partial-arrow-anchors]
                boolean anchorHit = false;
                for (int index = 0; index < arrowElement.points.size(); index++) {
                    if (marqueeBounds.contains(arrowElement.points.get(index))) {
                        arrowHits.add(new ArrowAnchorReference(arrowElement.id(), index));
                        anchorHit = true;
                    }
                }
                if (!anchorHit && arrowIntersectsSelection(arrowElement, marqueeBounds)) {
                    for (int index = 0; index < arrowElement.points.size(); index++) {
                        arrowHits.add(new ArrowAnchorReference(arrowElement.id(), index));
                    }
                }
                continue;
            }
            if (elementIntersectsSelection(element, marqueeBounds)) {
                hits.add(element.id());
            }
        }

        Set<Integer> expandedHits = expandSelectionToGroupedElements(hits, arrowHits);
        Set<Integer> explicitArrowIds = new LinkedHashSet<>();
        for (ArrowAnchorReference arrowHit : arrowHits) {
            explicitArrowIds.add(arrowHit.arrowId());
        }

        if (mode == SelectionMode.REPLACE) {
            clearCanvasSelection();
            selectedArrowAnchors.addAll(arrowHits);
            addExpandedGroupedSelections(expandedHits, explicitArrowIds);
        } else if (mode == SelectionMode.ADD) {
            selectedArrowAnchors.addAll(arrowHits);
            addExpandedGroupedSelections(expandedHits, explicitArrowIds);
        } else {
            selectedArrowAnchors.removeAll(arrowHits);
            removeExpandedGroupedSelections(expandedHits, explicitArrowIds);
        }
    }

    private void finalizeChromeMarqueeSelection() {
        if (chromeMarqueeSelectionDrag == null) {
            return;
        }

        SelectionMode mode = chromeMarqueeSelectionDrag.mode();
        ScreenRect marqueeRect = chromeMarqueeSelectionDrag.screenRect();
        chromeMarqueeSelectionDrag = null;

        if (marqueeRect.right() - marqueeRect.left() < 1 && marqueeRect.bottom() - marqueeRect.top() < 1) {
            if (mode == SelectionMode.REPLACE) {
                clearChromeSelection();
            }
            return;
        }

        Set<ChromeWidget> hits = new LinkedHashSet<>();
        for (ChromeWidget widget : ChromeWidget.VALUES) {
            if (isChromeWidgetRendered(widget) && rectsIntersect(chromeWidgetBounds(widget), marqueeRect)) {
                hits.add(widget);
            }
        }

        if (mode == SelectionMode.REPLACE) {
            selectedChromeWidgets.clear();
            selectedChromeWidgets.addAll(hits);
        } else if (mode == SelectionMode.ADD) {
            selectedChromeWidgets.addAll(hits);
        } else {
            selectedChromeWidgets.removeAll(hits);
        }

        selectedChromeWidget = selectedChromeWidgets.isEmpty() ? null : selectedChromeWidgets.iterator().next();
    }

    // r[impl draw.tool.rectangle.create]
    // r[impl draw.tool.arrow.create]
    // r[impl draw.tool.freehand.create]
    private void commitDraft() {
        if (draftInteraction == null) {
            return;
        }

        switch (draftInteraction.tool()) {
            case RECTANGLE -> {
                if (distanceSquared(draftInteraction.startPoint(), draftInteraction.currentPoint()) > 4.0D) {
                    RectangleElement element = new RectangleElement(nextElementId++, activeCanvasLayer(), draftInteraction.startPoint().x(), draftInteraction.startPoint().y(), draftInteraction.currentPoint().x(), draftInteraction.currentPoint().y(), 0x332FB5FF, 0xFF7FD7FF);
                    elements.add(element);
                    selectOnly(element.id());
                    resetToolAfterCreation(DrawTool.RECTANGLE);
                }
            }
            case ARROW -> {
                if (distanceSquared(draftInteraction.startPoint(), draftInteraction.currentPoint()) > 4.0D) {
                    ArrowElement element = new ArrowElement(nextElementId++, activeCanvasLayer(), List.of(draftInteraction.startPoint(), draftInteraction.currentPoint()), 0xFFE8A652);
                    elements.add(element);
                    selectOnly(element.id());
                    resetToolAfterCreation(DrawTool.ARROW);
                } else {
                    pendingArrowAnchors.clear();
                    pendingArrowAnchors.add(draftInteraction.startPoint());
                    pendingArrowProjection = draftInteraction.projection();
                }
            }
            case FREEHAND -> {
                if (draftInteraction.points().size() >= 2) {
                    FreehandElement element = new FreehandElement(nextElementId++, activeCanvasLayer(), List.copyOf(draftInteraction.points()), 0xFF88D498);
                    elements.add(element);
                    clearCanvasSelection();
                    resetToolAfterCreation(DrawTool.FREEHAND);
                }
            }
            default -> {
            }
        }
        draftInteraction = null;
    }

    // r[impl draw.tool.arrow.multisegment]
    private void finalizePendingArrowAnchors() {
        if (pendingArrowAnchors.size() >= 2) {
            ArrowElement element = new ArrowElement(nextElementId++, activeCanvasLayer(), List.copyOf(pendingArrowAnchors), 0xFFE8A652);
            elements.add(element);
            selectOnly(element.id());
            resetToolAfterCreation(DrawTool.ARROW);
        }
        pendingArrowAnchors.clear();
        pendingArrowProjection = null;
    }

    // r[impl draw.tool.text.create]
    private TextElement createTextElement(CanvasPoint point) {
        TextElement element = new TextElement(nextElementId++, activeCanvasLayer(), point.x(), point.y(), "", 0xFFF1F5FB, 1.0D, null);
        elements.add(element);
        selectOnly(element.id());
        return element;
    }

    // r[impl draw.tool.text.edit]
    private void finishTextEditing() {
        TextElement textElement = editingTextElement();
        if (textElement != null && textElement.shellBinding == null && textElement.text.isEmpty()) {
            elements.removeIf(element -> element.id() == textElement.id());
            selectedElementIds.remove(textElement.id());
        }
        textEditingElementId = -1;
        textEditingCaretIndex = 0;
        clearCanvasSelection();
        resetToolAfterCreation(DrawTool.TEXT);
    }

    // r[impl draw.camera.frame_tool]
    private void applyCameraFrame(CameraFrameDrag drag) {
        CanvasBounds bounds = CanvasBounds.of(drag.startPoint.x(), drag.startPoint.y(), drag.currentPoint.x(), drag.currentPoint.y());
        if (bounds.width() <= 1.0D || bounds.height() <= 1.0D) {
            return;
        }
        cameraX = (bounds.minX() + bounds.maxX()) / 2.0D;
        cameraY = (bounds.minY() + bounds.maxY()) / 2.0D;
        double zoomX = width / bounds.width();
        double zoomY = height / bounds.height();
        zoom = Mth.clamp(Math.min(zoomX, zoomY), MIN_ZOOM, MAX_ZOOM);
    }

    private void resetToolAfterCreation(DrawTool createdTool) {
        if (!stickyToolMode && activeTool == createdTool) {
            activeTool = DrawTool.CURSOR;
        }
    }

    private void handleToolShortcut(DrawTool tool) {
        if (tool == activeTool && supportsStickyMode(tool)) {
            // r[impl draw.tool.creation.sticky_toggle.hotbar-shortcut]
            stickyToolMode = !stickyToolMode;
            return;
        }
        handleHotbarToolClick(tool);
    }

    private boolean handleLockAction() {
        if (!isCanvasLayerActive()) {
            return false;
        }
        List<DrawElement> selectedElements = selectedLockableElements();
        if (selectedElements.isEmpty()) {
            canSelectLockedElements = !canSelectLockedElements;
            return true;
        }
        boolean newLocked = nextLockedValueForElements(selectedElements);
        for (DrawElement element : selectedElements) {
            element.setLocked(newLocked);
        }
        return true;
    }

    private List<DrawElement> selectedLockableElements() {
        List<DrawElement> selectedElements = new ArrayList<>();
        for (Integer selectedId : selectedOwningElementIds()) {
            DrawElement element = findElementById(selectedId);
            if (element != null && element.layer() == activeLayer) {
                selectedElements.add(element);
            }
        }
        return selectedElements;
    }

    private boolean nextLockedValueForElements(List<DrawElement> selectedElements) {
        int lockedCount = 0;
        for (DrawElement element : selectedElements) {
            if (element.locked()) {
                lockedCount++;
            }
        }
        return lockedCount * 2 <= selectedElements.size();
    }

    // r[impl draw.tool.cursor.select_all]
    private void selectAllElements() {
        clearCanvasSelection();
        for (DrawElement element : elements) {
            // r[impl draw.tool.cursor.hidden-omitted-unless-revealed]
            if (isElementSelectable(element)) {
                addElementToSelection(element);
            }
        }
    }

    private void selectAllHiddenElements() {
        clearCanvasSelection();
        for (DrawElement element : elements) {
            if (isElementSelectable(element) && element.hidden()) {
                addElementToSelection(element);
            }
        }
    }

    private boolean handleCanvasArrangementShortcut(int keyCode) {
        if (!isCanvasLayerActive() || activeTool != DrawTool.CURSOR || !isArrowKey(keyCode) || !hasControlDown() || !hasShiftDown()) {
            return false;
        }
        return hasAltDown() ? distributeSelection(keyCode) : alignSelection(keyCode);
    }

    private boolean nudgeActiveSelection(int keyCode) {
        if (!isArrowKey(keyCode)) {
            return false;
        }
        int step = movementSnapIncrement();
        int dx = 0;
        int dy = 0;
        switch (keyCode) {
            case GLFW.GLFW_KEY_LEFT -> dx = -step;
            case GLFW.GLFW_KEY_RIGHT -> dx = step;
            case GLFW.GLFW_KEY_UP -> dy = -step;
            case GLFW.GLFW_KEY_DOWN -> dy = step;
            default -> {
                return false;
            }
        }
        if (isCanvasLayerActive()) {
            return nudgeSelectedElements(dx, dy);
        }
        if (activeLayer == DrawLayer.CHROME) {
            return nudgeSelectedChromeWidgets(dx, dy);
        }
        return false;
    }

    private boolean isArrowKey(int keyCode) {
        return keyCode == GLFW.GLFW_KEY_LEFT
               || keyCode == GLFW.GLFW_KEY_RIGHT
               || keyCode == GLFW.GLFW_KEY_UP
               || keyCode == GLFW.GLFW_KEY_DOWN;
    }

    private boolean nudgeSelectedElements(int dx, int dy) {
        Set<Integer> selectedIds = selectedOwningElementIds();
        if (selectedIds.isEmpty()) {
            return false;
        }
        for (Integer selectedElementId : selectedIds) {
            DrawElement element = findElementById(selectedElementId);
            if (element != null) {
                element.translate(dx, dy);
            }
        }
        return true;
    }

    private boolean groupActiveSelection() {
        Set<Integer> selectedIds = selectedOwningElementIds();
        if (selectedIds.size() < 2) {
            return false;
        }
        int groupId = nextGroupId++;
        for (Integer selectedId : selectedIds) {
            DrawElement element = findElementById(selectedId);
            if (element != null) {
                element.groupIds().add(groupId);
            }
        }
        return true;
    }

    private boolean alignSelection(int keyCode) {
        List<SelectionComponent> components = selectedArrangementComponents();
        if (components.size() < 2) {
            return false;
        }
        CanvasBounds selectionBounds = boundsForComponents(components);
        for (SelectionComponent component : components) {
            double dx = 0.0D;
            double dy = 0.0D;
            switch (keyCode) {
                case GLFW.GLFW_KEY_LEFT -> dx = selectionBounds.minX() - component.bounds().minX();
                case GLFW.GLFW_KEY_RIGHT -> dx = selectionBounds.maxX() - component.bounds().maxX();
                case GLFW.GLFW_KEY_UP -> dy = selectionBounds.minY() - component.bounds().minY();
                case GLFW.GLFW_KEY_DOWN -> dy = selectionBounds.maxY() - component.bounds().maxY();
                default -> {
                    return false;
                }
            }
            translateComponent(component, dx, dy);
        }
        return true;
    }

    private boolean distributeSelection(int keyCode) {
        List<SelectionComponent> components = selectedArrangementComponents();
        if (components.size() < 3) {
            return false;
        }
        boolean horizontal = keyCode == GLFW.GLFW_KEY_LEFT || keyCode == GLFW.GLFW_KEY_RIGHT;
        boolean vertical = keyCode == GLFW.GLFW_KEY_UP || keyCode == GLFW.GLFW_KEY_DOWN;
        if (!horizontal && !vertical) {
            return false;
        }

        List<SelectionComponent> ordered = new ArrayList<>(components);
        ordered.sort(horizontal
                ? Comparator.comparingDouble(component -> component.bounds().minX())
                : Comparator.comparingDouble(component -> component.bounds().minY()));

        if (horizontal) {
            double span = ordered.get(ordered.size() - 1).bounds().maxX() - ordered.get(0).bounds().minX();
            double occupied = 0.0D;
            for (SelectionComponent component : ordered) {
                occupied += component.bounds().width();
            }
            double gap = (span - occupied) / (ordered.size() - 1);
            double cursor = ordered.get(0).bounds().maxX() + gap;
            for (int index = 1; index < ordered.size() - 1; index++) {
                SelectionComponent component = ordered.get(index);
                double dx = cursor - component.bounds().minX();
                translateComponent(component, dx, 0.0D);
                cursor += component.bounds().width() + gap;
            }
            return true;
        }

        double span = ordered.get(ordered.size() - 1).bounds().maxY() - ordered.get(0).bounds().minY();
        double occupied = 0.0D;
        for (SelectionComponent component : ordered) {
            occupied += component.bounds().height();
        }
        double gap = (span - occupied) / (ordered.size() - 1);
        double cursor = ordered.get(0).bounds().maxY() + gap;
        for (int index = 1; index < ordered.size() - 1; index++) {
            SelectionComponent component = ordered.get(index);
            double dy = cursor - component.bounds().minY();
            translateComponent(component, 0.0D, dy);
            cursor += component.bounds().height() + gap;
        }
        return true;
    }

    private boolean nudgeSelectedChromeWidgets(int dx, int dy) {
        if (selectedChromeWidgets.isEmpty()) {
            return false;
        }
        for (ChromeWidget widget : selectedChromeWidgets) {
            ChromeWidgetState state = chromeWidgetState(widget);
            moveChromeWidget(widget, state.x() + dx, state.y() + dy);
            clampChromeWidgetToScreen(widget);
        }
        return true;
    }

    // r[impl draw.tool.cursor.duplicate_selection]
    private void duplicateSelection() {
        if (selectedElementIds.isEmpty()) {
            return;
        }
        List<DrawElement> duplicates = new ArrayList<>(selectedElementIds.size());
        Set<Integer> newSelection = new LinkedHashSet<>();
        for (Integer selectedElementId : selectedElementIds) {
            DrawElement element = findElementById(selectedElementId);
            if (element == null) {
                continue;
            }
            DrawElement duplicate = element.copyWithId(nextElementId++);
            duplicates.add(duplicate);
            newSelection.add(duplicate.id());
        }
        elements.addAll(duplicates);
        selectedElementIds.clear();
        selectedArrowAnchors.clear();
        selectedElementIds.addAll(newSelection);
    }

    private boolean elementIntersectsSelection(
            DrawElement element,
            CanvasBounds selectionBounds
    ) {
        if (element instanceof ArrowElement arrowElement) {
            return arrowIntersectsSelection(arrowElement, selectionBounds);
        }
        return element.bounds(this).intersects(selectionBounds);
    }

    private boolean arrowIntersectsSelection(
            ArrowElement arrowElement,
            CanvasBounds selectionBounds
    ) {
        List<CanvasPoint> points = arrowElement.points;
        for (int i = 1; i < points.size(); i++) {
            if (segmentIntersectsRect(points.get(i - 1), points.get(i), selectionBounds)) {
                return true;
            }
        }
        return false;
    }

    private boolean segmentIntersectsRect(
            CanvasPoint start,
            CanvasPoint end,
            CanvasBounds rect
    ) {
        if (rect.contains(start) || rect.contains(end)) {
            return true;
        }
        CanvasPoint topLeft = new CanvasPoint(rect.minX(), rect.minY());
        CanvasPoint topRight = new CanvasPoint(rect.maxX(), rect.minY());
        CanvasPoint bottomLeft = new CanvasPoint(rect.minX(), rect.maxY());
        CanvasPoint bottomRight = new CanvasPoint(rect.maxX(), rect.maxY());
        return segmentsIntersect(start, end, topLeft, topRight)
               || segmentsIntersect(start, end, topRight, bottomRight)
               || segmentsIntersect(start, end, bottomRight, bottomLeft)
               || segmentsIntersect(start, end, bottomLeft, topLeft);
    }

    private boolean segmentsIntersect(
            CanvasPoint a,
            CanvasPoint b,
            CanvasPoint c,
            CanvasPoint d
    ) {
        double o1 = orientation(a, b, c);
        double o2 = orientation(a, b, d);
        double o3 = orientation(c, d, a);
        double o4 = orientation(c, d, b);
        return o1 * o2 <= 0.0D && o3 * o4 <= 0.0D;
    }

    private double orientation(
            CanvasPoint a,
            CanvasPoint b,
            CanvasPoint c
    ) {
        return (b.x() - a.x()) * (c.y() - a.y()) - (b.y() - a.y()) * (c.x() - a.x());
    }

    private void appendEditingText(String value) {
        if (value == null || value.isEmpty()) {
            return;
        }
        DrawElement element = findElementById(textEditingElementId);
        if (element instanceof TextElement textElement) {
            textEditingCaretIndex = Mth.clamp(textEditingCaretIndex, 0, textElement.text.length());
            textElement.text = textElement.text.substring(0, textEditingCaretIndex) + value + textElement.text.substring(textEditingCaretIndex);
            textEditingCaretIndex += value.length();
        }
    }

    private void mutateEditingTextBackspace() {
        DrawElement element = findElementById(textEditingElementId);
        if (element instanceof TextElement textElement && !textElement.text.isEmpty() && textEditingCaretIndex > 0) {
            textElement.text = textElement.text.substring(0, textEditingCaretIndex - 1) + textElement.text.substring(textEditingCaretIndex);
            textEditingCaretIndex--;
        }
    }

    private void mutateEditingTextDelete() {
        DrawElement element = findElementById(textEditingElementId);
        if (element instanceof TextElement textElement && textEditingCaretIndex >= 0 && textEditingCaretIndex < textElement.text.length()) {
            textElement.text = textElement.text.substring(0, textEditingCaretIndex) + textElement.text.substring(textEditingCaretIndex + 1);
        }
    }

    private @Nullable TextElement editingTextElement() {
        DrawElement element = findElementById(textEditingElementId);
        return element instanceof TextElement textElement ? textElement : null;
    }

    // r[impl draw.tool.cursor.delete_selection]
    private boolean deleteSelectedElements() {
        if (selectedElementIds.isEmpty() && selectedArrowAnchors.isEmpty()) {
            return false;
        }

        Set<Integer> removedElementIds = new LinkedHashSet<>(selectedElementIds);
        // r[impl draw.tool.arrow.anchors.delete]
        for (DrawElement element : elements) {
            if (!(element instanceof ArrowElement arrowElement)) {
                continue;
            }
            List<Integer> selectedAnchorIndexes = new ArrayList<>();
            for (ArrowAnchorReference selectedArrowAnchor : selectedArrowAnchors) {
                if (selectedArrowAnchor.arrowId() == arrowElement.id() && arrowElement.hasPointIndex(selectedArrowAnchor.anchorIndex())) {
                    selectedAnchorIndexes.add(selectedArrowAnchor.anchorIndex());
                }
            }
            if (selectedAnchorIndexes.isEmpty()) {
                continue;
            }
            selectedAnchorIndexes.sort(Integer::compareTo);
            for (int index = selectedAnchorIndexes.size() - 1; index >= 0; index--) {
                arrowElement.removePoint(selectedAnchorIndexes.get(index));
            }
            // r[impl draw.tool.arrow.destroy-when-one-anchor-remains]
            if (arrowElement.points.size() <= 1) {
                removedElementIds.add(arrowElement.id());
            }
        }

        elements.removeIf(element -> removedElementIds.contains(element.id()));
        if (selectedElementIds.contains(textEditingElementId)) {
            textEditingElementId = -1;
            textEditingCaretIndex = 0;
        }
        selectedArrowAnchors.removeIf(reference -> removedElementIds.contains(reference.arrowId()));
        clearCanvasSelection();
        return true;
    }

    private @Nullable ArrowAnchorReference findVisibleArrowAnchorAt(CanvasPoint point) {
        for (int elementIndex = elements.size() - 1; elementIndex >= 0; elementIndex--) {
            DrawElement element = elements.get(elementIndex);
            if (!(element instanceof ArrowElement arrowElement) || !arrowHasSelectedAnchors(arrowElement) || !isElementSelectable(arrowElement)) {
                continue;
            }
            for (int anchorIndex = arrowElement.points.size() - 1; anchorIndex >= 0; anchorIndex--) {
                if (arrowElement.isAnchorHidden(anchorIndex) && !revealHiddenElements) {
                    continue;
                }
                if (distanceSquared(point, arrowElement.points.get(anchorIndex)) <= Math.pow(8.0D / Math.max(zoom, 0.01D), 2.0D)) {
                    return new ArrowAnchorReference(arrowElement.id(), anchorIndex);
                }
            }
        }
        return null;
    }

    private int findTopElementAt(CanvasPoint point) {
        for (int index = elements.size() - 1; index >= 0; index--) {
            DrawElement element = elements.get(index);
            // r[impl draw.tool.cursor.hidden-omitted-unless-revealed]
            if (isElementSelectable(element) && elementContains(element, point)) {
                return element.id();
            }
        }
        return -1;
    }

    private boolean shouldRenderElement(DrawElement element) {
        // r[impl draw.element.hidden.dim-when-revealed]
        return !isLayerMuted(element.layer()) && (!element.hidden() || revealHiddenElements);
    }

    private boolean isElementSelectable(DrawElement element) {
        // r[impl draw.tool.cursor.hidden-omitted-unless-revealed]
        return element.layer() == activeLayer
               && !isLayerMuted(element.layer())
               && (!element.hidden() || revealHiddenElements)
               && (!element.locked() || canSelectLockedElement(element));
    }

    private boolean canSelectLockedElement(DrawElement element) {
        if (!element.locked()) {
            return true;
        }
        return activeTool == DrawTool.CURSOR && canSelectLockedElements;
    }

    private SelectionMode selectionMode() {
        if (hasControlDown()) {
            return SelectionMode.SUBTRACT;
        }
        if (hasShiftDown()) {
            return SelectionMode.ADD;
        }
        return SelectionMode.REPLACE;
    }

    private @Nullable String selectionModifierIndicator() {
        if (activeTool != DrawTool.CURSOR || !isCanvasLayerActive()) {
            return null;
        }
        return switch (selectionMode()) {
            case ADD -> "+";
            case SUBTRACT -> "-";
            case REPLACE -> null;
        };
    }

    private int movementSnapIncrement() {
        if (hasControlDown()) {
            return SMALL_MOVE_SNAP;
        }
        if (hasShiftDown()) {
            return LARGE_MOVE_SNAP;
        }
        return DEFAULT_MOVE_SNAP;
    }

    private CanvasPoint snapCanvasPointToCurrentIncrement(CanvasPoint point) {
        double increment = movementSnapIncrement();
        return new CanvasPoint(
                snapToIncrement(point.x(), increment),
                snapToIncrement(point.y(), increment)
        );
    }

    private CanvasPoint snappedMoveDelta(
            MoveSelectionDrag drag,
            CanvasPoint currentPoint
    ) {
        double rawDx = currentPoint.x() - drag.startPoint().x();
        double rawDy = currentPoint.y() - drag.startPoint().y();
        double increment = movementSnapIncrement();
        double snappedDx = snapToIncrement(drag.snapOrigin().x() + rawDx, increment) - drag.snapOrigin().x();
        double snappedDy = snapToIncrement(drag.snapOrigin().y() + rawDy, increment) - drag.snapOrigin().y();
        return new CanvasPoint(snappedDx, snappedDy);
    }

    private double snapToIncrement(
            double value,
            double increment
    ) {
        if (increment <= 1.0D) {
            return Math.rint(value);
        }
        return Math.rint(value / increment) * increment;
    }

    private double snapDistance(
            double distance,
            double increment,
            double minimumSize
    ) {
        double clampedDistance = Math.max(minimumSize, distance);
        if (increment <= 1.0D) {
            return Math.max(minimumSize, Math.rint(clampedDistance));
        }
        return Math.max(minimumSize, Math.rint(clampedDistance / increment) * increment);
    }

    private boolean toggleHiddennessForActiveSelection() {
        if (isCanvasLayerActive()) {
            if (selectedElementIds.isEmpty() && selectedArrowAnchors.isEmpty()) {
                return false;
            }
            int hiddenCount = 0;
            int selectionCount = 0;
            List<DrawElement> selection = new ArrayList<>(selectedElementIds.size());
            for (Integer selectedElementId : selectedElementIds) {
                DrawElement element = findElementById(selectedElementId);
                if (element != null) {
                    selection.add(element);
                    selectionCount++;
                    if (element.hidden()) {
                        hiddenCount++;
                    }
                }
            }

            List<ArrowAnchorReference> anchorSelection = new ArrayList<>(selectedArrowAnchors.size());
            Set<Integer> fullySelectedArrowIds = new LinkedHashSet<>();
            for (ArrowAnchorReference selectedArrowAnchor : selectedArrowAnchors) {
                DrawElement element = findElementById(selectedArrowAnchor.arrowId());
                if (!(element instanceof ArrowElement arrowElement) || !arrowElement.hasPointIndex(selectedArrowAnchor.anchorIndex())) {
                    continue;
                }
                anchorSelection.add(selectedArrowAnchor);
            }
            for (ArrowAnchorReference selectedArrowAnchor : anchorSelection) {
                DrawElement element = findElementById(selectedArrowAnchor.arrowId());
                if (!(element instanceof ArrowElement arrowElement)) {
                    continue;
                }
                boolean allAnchorsSelected = true;
                for (int index = 0; index < arrowElement.points.size(); index++) {
                    if (!selectedArrowAnchors.contains(new ArrowAnchorReference(arrowElement.id(), index))) {
                        allAnchorsSelected = false;
                        break;
                    }
                }
                if (allAnchorsSelected) {
                    fullySelectedArrowIds.add(arrowElement.id());
                }
            }
            for (Integer fullySelectedArrowId : fullySelectedArrowIds) {
                DrawElement element = findElementById(fullySelectedArrowId);
                if (element instanceof ArrowElement arrowElement) {
                    selectionCount++;
                    if (arrowElement.hidden()) {
                        hiddenCount++;
                    }
                }
            }
            for (ArrowAnchorReference selectedArrowAnchor : anchorSelection) {
                if (fullySelectedArrowIds.contains(selectedArrowAnchor.arrowId())) {
                    continue;
                }
                DrawElement element = findElementById(selectedArrowAnchor.arrowId());
                if (element instanceof ArrowElement arrowElement) {
                    selectionCount++;
                    if (arrowElement.isAnchorHidden(selectedArrowAnchor.anchorIndex())) {
                        hiddenCount++;
                    }
                }
            }

            boolean newHidden = hiddenCount * 2 <= selectionCount;
            for (DrawElement element : selection) {
                element.setHidden(newHidden);
            }
            for (Integer fullySelectedArrowId : fullySelectedArrowIds) {
                DrawElement element = findElementById(fullySelectedArrowId);
                if (element instanceof ArrowElement arrowElement) {
                    arrowElement.setHidden(newHidden);
                }
            }
            // r[impl draw.tool.arrow.anchors.hide]
            for (ArrowAnchorReference selectedArrowAnchor : anchorSelection) {
                if (fullySelectedArrowIds.contains(selectedArrowAnchor.arrowId())) {
                    continue;
                }
                DrawElement element = findElementById(selectedArrowAnchor.arrowId());
                if (element instanceof ArrowElement arrowElement) {
                    arrowElement.setAnchorHidden(selectedArrowAnchor.anchorIndex(), newHidden);
                }
            }
            if (newHidden && !revealHiddenElements) {
                clearCanvasSelection();
            }
            return selectionCount > 0;
        }

        if (selectedChromeWidgets.isEmpty()) {
            return false;
        }
        int hiddenCount = 0;
        for (ChromeWidget widget : selectedChromeWidgets) {
            if (chromeWidgetState(widget).hidden()) {
                hiddenCount++;
            }
        }
        boolean newHidden = hiddenCount * 2 <= selectedChromeWidgets.size();
        for (ChromeWidget widget : List.copyOf(selectedChromeWidgets)) {
            setChromeWidgetState(widget, chromeWidgetState(widget).withHidden(newHidden));
            if (newHidden && !revealHiddenElements) {
                removeChromeWidgetFromSelection(widget);
            }
        }
        return true;
    }

    private boolean elementContains(
            DrawElement element,
            CanvasPoint point
    ) {
        if (element instanceof RectangleElement rectangleElement) {
            double minX = Math.min(rectangleElement.x1, rectangleElement.x2) - 6.0D / zoom;
            double maxX = Math.max(rectangleElement.x1, rectangleElement.x2) + 6.0D / zoom;
            double minY = Math.min(rectangleElement.y1, rectangleElement.y2) - 6.0D / zoom;
            double maxY = Math.max(rectangleElement.y1, rectangleElement.y2) + 6.0D / zoom;
            return point.x() >= minX && point.x() <= maxX && point.y() >= minY && point.y() <= maxY;
        }
        if (element instanceof ArrowElement arrowElement) {
            List<CanvasPoint> renderPoints = renderableArrowPoints(arrowElement);
            for (int i = 1; i < renderPoints.size(); i++) {
                if (distancePointToSegment(point, renderPoints.get(i - 1), renderPoints.get(i)) <= 8.0D / zoom) {
                    return true;
                }
            }
            return false;
        }
        if (element instanceof TextElement textElement) {
            return textElementContains(textElement, point);
        }
        if (element instanceof FreehandElement freehandElement) {
            List<CanvasPoint> points = freehandElement.points;
            for (int i = 1; i < points.size(); i++) {
                if (distancePointToSegment(point, points.get(i - 1), points.get(i)) <= 8.0D / zoom) {
                    return true;
                }
            }
            return false;
        }
        return false;
    }

    private boolean textElementContains(
            TextElement textElement,
            CanvasPoint point
    ) {
        return textElement.bounds(this).contains(point);
    }

    private @Nullable TextElement editableTextElementAt(CanvasPoint point) {
        int hitElementId = findTopElementAt(point);
        if (hitElementId < 0) {
            return null;
        }
        DrawElement element = findElementById(hitElementId);
        if (element instanceof TextElement textElement && textElement.shellBinding == null) {
            return textElement;
        }
        return null;
    }

    private boolean isCommandTextElement(TextElement textElement) {
        return textElement.shellBinding == null && textElement.text.startsWith("/");
    }

    private void placeTextCaretFromScreen(
            TextElement textElement,
            double mouseX,
            double mouseY
    ) {
        ScreenPoint anchor = canvasToScreen(new CanvasPoint(textElement.x, textElement.y));
        double renderScale = textElement.textScale * zoom;
        double localX = (mouseX - anchor.x()) / renderScale;
        double localY = (mouseY - anchor.y()) / renderScale;
        String[] lines = textLines(textElement);
        int[] lineStarts = textLineStarts(textElement);
        int lineIndex = Mth.clamp((int) Math.floor(localY / font.lineHeight), 0, Math.max(0, lines.length - 1));
        String line = lines[lineIndex];
        int column = 0;
        for (int i = 0; i <= line.length(); i++) {
            int widthAtIndex = font.width(line.substring(0, i));
            if (localX < widthAtIndex + 3 || i == line.length()) {
                column = i;
                break;
            }
        }
        textEditingCaretIndex = lineStarts[lineIndex] + column;
    }

    private CaretPlacement caretPlacement(
            TextElement textElement,
            int caretIndex
    ) {
        String[] lines = textLines(textElement);
        int[] lineStarts = textLineStarts(textElement);
        int clampedIndex = Mth.clamp(caretIndex, 0, textElement.text.length());
        for (int lineIndex = lines.length - 1; lineIndex >= 0; lineIndex--) {
            if (clampedIndex >= lineStarts[lineIndex]) {
                int column = Math.min(lines[lineIndex].length(), clampedIndex - lineStarts[lineIndex]);
                return new CaretPlacement(font.width(lines[lineIndex].substring(0, column)), lineIndex * font.lineHeight);
            }
        }
        return new CaretPlacement(0, 0);
    }

    private String[] textLines(TextElement textElement) {
        return textElement.text.isEmpty() ? new String[]{""} : textElement.text.split("\\n", -1);
    }

    private int[] textLineStarts(TextElement textElement) {
        String[] lines = textLines(textElement);
        int[] result = new int[lines.length];
        int index = 0;
        for (int i = 0; i < lines.length; i++) {
            result[i] = index;
            index += lines[i].length();
            if (i < lines.length - 1) {
                index += 1;
            }
        }
        return result;
    }

    private @Nullable DrawElement findElementById(int id) {
        for (DrawElement element : elements) {
            if (element.id() == id) {
                return element;
            }
        }
        return null;
    }

    private void clearCanvasSelection() {
        selectedElementIds.clear();
        selectedArrowAnchors.clear();
    }

    // r[impl draw.tool.cursor.selection]
    private void selectOnly(int id) {
        clearCanvasSelection();
        addElementComponentToSelection(groupConnectedElementIds(id));
    }

    private void selectOnlyArrow(ArrowElement arrowElement) {
        selectOnly(arrowElement.id());
    }

    private void selectOnlyArrowAnchor(ArrowAnchorReference anchorReference) {
        clearCanvasSelection();
        selectedArrowAnchors.add(anchorReference);
    }

    private void addArrowAnchorsToSelection(ArrowElement arrowElement) {
        for (int index = 0; index < arrowElement.points.size(); index++) {
            selectedArrowAnchors.add(new ArrowAnchorReference(arrowElement.id(), index));
        }
    }

    private void applyArrowAnchorSelection(
            ArrowAnchorReference anchorReference,
            SelectionMode selectionMode
    ) {
        Set<Integer> connectedIds = groupConnectedElementIds(anchorReference.arrowId());
        connectedIds.remove(anchorReference.arrowId());
        switch (selectionMode) {
            case REPLACE -> {
                clearCanvasSelection();
                selectedArrowAnchors.add(anchorReference);
                addElementComponentToSelection(connectedIds);
            }
            case ADD -> {
                selectedArrowAnchors.add(anchorReference);
                addElementComponentToSelection(connectedIds);
            }
            case SUBTRACT -> {
                selectedArrowAnchors.remove(anchorReference);
                removeElementComponentFromSelection(connectedIds);
            }
        }
    }

    private void addElementComponentToSelection(Set<Integer> elementIds) {
        for (Integer elementId : elementIds) {
            DrawElement element = findElementById(elementId);
            if (element == null || element.layer() != activeLayer || !isElementSelectable(element)) {
                continue;
            }
            addElementToSelection(element);
        }
    }

    private void addElementToSelection(DrawElement element) {
        if (element instanceof ArrowElement arrowElement) {
            addArrowAnchorsToSelection(arrowElement);
            return;
        }
        selectedElementIds.add(element.id());
    }

    private void removeElementComponentFromSelection(Set<Integer> elementIds) {
        for (Integer elementId : elementIds) {
            DrawElement element = findElementById(elementId);
            if (element == null) {
                continue;
            }
            selectedElementIds.remove(element.id());
            if (element instanceof ArrowElement arrowElement) {
                removeArrowAnchorsFromSelection(arrowElement);
            }
        }
    }

    private Set<Integer> expandSelectionToGroupedElements(
            Set<Integer> elementHits,
            Set<ArrowAnchorReference> arrowHits
    ) {
        Set<Integer> expanded = new LinkedHashSet<>(elementHits);
        for (ArrowAnchorReference arrowHit : arrowHits) {
            expanded.add(arrowHit.arrowId());
        }
        for (Integer elementId : List.copyOf(expanded)) {
            expanded.addAll(groupConnectedElementIds(elementId));
        }
        return expanded;
    }

    private void addExpandedGroupedSelections(
            Set<Integer> expandedHits,
            Set<Integer> explicitArrowIds
    ) {
        for (Integer elementId : expandedHits) {
            if (explicitArrowIds.contains(elementId)) {
                continue;
            }
            DrawElement element = findElementById(elementId);
            if (element != null && element.layer() == activeLayer && isElementSelectable(element)) {
                addElementToSelection(element);
            }
        }
    }

    private void removeExpandedGroupedSelections(
            Set<Integer> expandedHits,
            Set<Integer> explicitArrowIds
    ) {
        for (Integer elementId : expandedHits) {
            if (explicitArrowIds.contains(elementId)) {
                continue;
            }
            DrawElement element = findElementById(elementId);
            if (element == null) {
                continue;
            }
            selectedElementIds.remove(element.id());
            if (element instanceof ArrowElement arrowElement) {
                removeArrowAnchorsFromSelection(arrowElement);
            }
        }
    }

    private Set<Integer> selectedOwningElementIds() {
        Set<Integer> selectedIds = new LinkedHashSet<>(selectedElementIds);
        for (ArrowAnchorReference selectedArrowAnchor : selectedArrowAnchors) {
            DrawElement element = findElementById(selectedArrowAnchor.arrowId());
            if (element != null) {
                selectedIds.add(element.id());
            }
        }
        return selectedIds;
    }

    private Set<Integer> groupConnectedElementIds(int elementId) {
        DrawElement seed = findElementById(elementId);
        Set<Integer> result = new LinkedHashSet<>();
        if (seed == null) {
            return result;
        }
        result.add(seed.id());
        boolean changed = true;
        while (changed) {
            changed = false;
            for (DrawElement candidate : elements) {
                if (candidate.layer() != seed.layer() || result.contains(candidate.id())) {
                    continue;
                }
                for (Integer existingId : List.copyOf(result)) {
                    DrawElement existing = findElementById(existingId);
                    if (existing != null && elementsShareGroup(existing, candidate)) {
                        result.add(candidate.id());
                        changed = true;
                        break;
                    }
                }
            }
        }
        return result;
    }

    private boolean elementsShareGroup(
            DrawElement a,
            DrawElement b
    ) {
        for (Integer groupId : a.groupIds()) {
            if (b.groupIds().contains(groupId)) {
                return true;
            }
        }
        return false;
    }

    private void removeArrowAnchorsFromSelection(ArrowElement arrowElement) {
        selectedArrowAnchors.removeIf(reference -> reference.arrowId() == arrowElement.id());
    }

    private boolean isArrowAnchorSelected(
            int arrowId,
            int anchorIndex
    ) {
        return selectedArrowAnchors.contains(new ArrowAnchorReference(arrowId, anchorIndex));
    }

    private boolean arrowHasSelectedAnchors(ArrowElement arrowElement) {
        return selectedArrowAnchors.stream().anyMatch(reference -> reference.arrowId() == arrowElement.id());
    }

    private List<CanvasPoint> renderableArrowPoints(ArrowElement arrowElement) {
        List<CanvasPoint> renderPoints = new ArrayList<>(arrowElement.points.size());
        for (int index = 0; index < arrowElement.points.size(); index++) {
            if (!arrowElement.isAnchorHidden(index)) {
                renderPoints.add(arrowElement.points.get(index));
            }
        }
        return renderPoints;
    }

    private CanvasPoint selectionSnapOrigin() {
        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        boolean found = false;

        for (Integer selectedElementId : selectedElementIds) {
            DrawElement element = findElementById(selectedElementId);
            if (element == null) {
                continue;
            }
            CanvasBounds bounds = element.bounds(this);
            minX = Math.min(minX, bounds.minX());
            minY = Math.min(minY, bounds.minY());
            found = true;
        }

        for (ArrowAnchorReference selectedArrowAnchor : selectedArrowAnchors) {
            DrawElement element = findElementById(selectedArrowAnchor.arrowId());
            if (!(element instanceof ArrowElement arrowElement) || !arrowElement.hasPointIndex(selectedArrowAnchor.anchorIndex())) {
                continue;
            }
            CanvasPoint point = arrowElement.points.get(selectedArrowAnchor.anchorIndex());
            minX = Math.min(minX, point.x());
            minY = Math.min(minY, point.y());
            found = true;
        }

        return found ? new CanvasPoint(minX, minY) : new CanvasPoint(0.0D, 0.0D);
    }

    private boolean isElementSelected(DrawElement element) {
        return selectedElementIds.contains(element.id()) || (element instanceof ArrowElement arrowElement && arrowHasSelectedAnchors(arrowElement));
    }

    private void clearChromeSelection() {
        selectedChromeWidget = null;
        selectedChromeWidgets.clear();
    }

    private void selectOnlyChromeWidget(ChromeWidget widget) {
        selectedChromeWidget = widget;
        selectedChromeWidgets.clear();
        selectedChromeWidgets.add(widget);
    }

    private boolean isChromeWidgetSelected(ChromeWidget widget) {
        return selectedChromeWidgets.contains(widget);
    }

    private void removeChromeWidgetFromSelection(ChromeWidget widget) {
        selectedChromeWidgets.remove(widget);
        if (selectedChromeWidget == widget) {
            selectedChromeWidget = selectedChromeWidgets.isEmpty() ? null : selectedChromeWidgets.iterator().next();
        }
    }

    private @Nullable CanvasBounds currentSelectionBounds() {
        CanvasBounds bounds = null;
        for (Integer selectedElementId : selectedElementIds) {
            DrawElement element = findElementById(selectedElementId);
            if (element == null) {
                continue;
            }
            bounds = bounds == null ? element.bounds(this) : bounds.expandToInclude(element.bounds(this));
        }
        for (ArrowAnchorReference selectedArrowAnchor : selectedArrowAnchors) {
            DrawElement element = findElementById(selectedArrowAnchor.arrowId());
            if (!(element instanceof ArrowElement arrowElement) || !arrowElement.hasPointIndex(selectedArrowAnchor.anchorIndex())) {
                continue;
            }
            CanvasPoint point = arrowElement.points.get(selectedArrowAnchor.anchorIndex());
            CanvasBounds pointBounds = CanvasBounds.of(point.x(), point.y(), point.x(), point.y()).pad(6.0D / Math.max(zoom, 0.01D));
            bounds = bounds == null ? pointBounds : bounds.expandToInclude(pointBounds);
        }
        return bounds;
    }

    private List<ElementSnapshot> selectionSnapshot() {
        List<ElementSnapshot> snapshots = new ArrayList<>(selectedElementIds.size());
        for (Integer selectedElementId : selectedElementIds) {
            DrawElement element = findElementById(selectedElementId);
            if (element != null) {
                snapshots.add(new ElementSnapshot(selectedElementId, element.copy()));
            }
        }
        return snapshots;
    }

    private List<ArrowAnchorSnapshot> arrowAnchorSelectionSnapshot() {
        List<ArrowAnchorSnapshot> snapshots = new ArrayList<>(selectedArrowAnchors.size());
        for (ArrowAnchorReference selectedArrowAnchor : selectedArrowAnchors) {
            DrawElement element = findElementById(selectedArrowAnchor.arrowId());
            if (element instanceof ArrowElement arrowElement && arrowElement.hasPointIndex(selectedArrowAnchor.anchorIndex())) {
                snapshots.add(new ArrowAnchorSnapshot(selectedArrowAnchor.arrowId(), selectedArrowAnchor.anchorIndex(), arrowElement.points.get(selectedArrowAnchor.anchorIndex())));
            }
        }
        return snapshots;
    }

    private boolean canResizeCurrentSelection() {
        return canTransformCurrentSelection() && selectedArrowAnchors.isEmpty() && !selectedElementIds.isEmpty();
    }

    private boolean canTransformCurrentSelection() {
        Set<Integer> selectedIds = selectedOwningElementIds();
        if (selectedIds.isEmpty()) {
            return false;
        }
        for (Integer selectedId : selectedIds) {
            DrawElement element = findElementById(selectedId);
            if (element == null || element.locked()) {
                return false;
            }
        }
        return true;
    }

    private List<SelectionComponent> selectedArrangementComponents() {
        Set<Integer> selectedIds = selectedOwningElementIds();
        List<SelectionComponent> components = new ArrayList<>();
        Set<Integer> remaining = new LinkedHashSet<>(selectedIds);
        while (!remaining.isEmpty()) {
            Integer seedId = remaining.iterator().next();
            Set<Integer> componentIds = new LinkedHashSet<>(groupConnectedElementIds(seedId));
            componentIds.retainAll(selectedIds);
            remaining.removeAll(componentIds);
            components.add(new SelectionComponent(componentIds, boundsForElementIds(componentIds)));
        }
        return components;
    }

    private CanvasBounds boundsForComponents(List<SelectionComponent> components) {
        CanvasBounds bounds = components.get(0).bounds();
        for (int index = 1; index < components.size(); index++) {
            bounds = bounds.expandToInclude(components.get(index).bounds());
        }
        return bounds;
    }

    private CanvasBounds boundsForElementIds(Set<Integer> elementIds) {
        CanvasBounds bounds = null;
        for (Integer elementId : elementIds) {
            DrawElement element = findElementById(elementId);
            if (element == null) {
                continue;
            }
            bounds = bounds == null ? element.bounds(this) : bounds.expandToInclude(element.bounds(this));
        }
        return bounds != null ? bounds : CanvasBounds.of(0.0D, 0.0D, 0.0D, 0.0D);
    }

    private void translateComponent(
            SelectionComponent component,
            double dx,
            double dy
    ) {
        if (Math.abs(dx) <= 0.000001D && Math.abs(dy) <= 0.000001D) {
            return;
        }
        for (Integer elementId : component.elementIds()) {
            DrawElement element = findElementById(elementId);
            if (element != null) {
                element.translate(dx, dy);
            }
        }
    }

    private boolean isCanvasLayerActive() {
        return activeLayer.canvasLayer();
    }

    private DrawLayer activeCanvasLayer() {
        return activeLayer.canvasLayer() ? activeLayer : DrawLayer.ELEMENTS;
    }

    private boolean isCursorDoubleClick(
            double mouseX,
            double mouseY
    ) {
        long now = Util.getMillis();
        double dx = mouseX - lastCursorClickX;
        double dy = mouseY - lastCursorClickY;
        boolean doubleClick = now - lastCursorClickAtMs <= DOUBLE_CLICK_WINDOW_MS && dx * dx + dy * dy <= DOUBLE_CLICK_DISTANCE_SQUARED;
        lastCursorClickAtMs = now;
        lastCursorClickX = mouseX;
        lastCursorClickY = mouseY;
        return doubleClick;
    }

    private int hoveredToolIndex(
            double mouseX,
            double mouseY
    ) {
        if (!isChromeWidgetOperational(ChromeWidget.HOTBAR_BAR)) {
            return -1;
        }
        for (int index = 0; index < TOOL_COUNT; index++) {
            if (hotbarSlotBounds(index).contains(mouseX, mouseY)) {
                return index;
            }
        }
        return -1;
    }

    private @Nullable DrawTool hoveredAuxiliaryTool(
            double mouseX,
            double mouseY
    ) {
        if (!isChromeWidgetOperational(ChromeWidget.AUXILIARY_HOTBAR_BAR)) {
            return null;
        }
        return auxiliaryHotbarSlotBounds().contains(mouseX, mouseY) ? DrawTool.LOCK : null;
    }

    private @Nullable DrawTool hoveredToolbarTool(
            double mouseX,
            double mouseY
    ) {
        DrawTool auxiliaryTool = hoveredAuxiliaryTool(mouseX, mouseY);
        if (auxiliaryTool != null) {
            return auxiliaryTool;
        }
        int hoveredToolIndex = hoveredToolIndex(mouseX, mouseY);
        return hoveredToolIndex >= 0 ? DrawTool.VALUES[hoveredToolIndex] : null;
    }

    private boolean isChromeLayerActive() {
        return activeLayer == DrawLayer.CHROME;
    }

    private boolean isChromeWidgetPresent(ChromeWidget widget) {
        if (chromeLayerMuted) {
            return false;
        }
        return switch (widget) {
            case SCREEN_TITLE, SCREEN_SUBTITLE,
                 ACTIVE_LAYER_LABEL, ACTIVE_LAYER_VALUE,
                 CAMERA_POSITION_LABEL, CAMERA_POSITION_X, CAMERA_POSITION_SEPARATOR, CAMERA_POSITION_Y,
                 ZOOM_LABEL, ZOOM_VALUE,
                 CURSOR_LABEL, CURSOR_X, CURSOR_SEPARATOR, CURSOR_Y,
                 HOTBAR_BAR, AUXILIARY_HOTBAR_BAR, HOTBAR_TITLE, HOTBAR_SUBTITLE -> true;
            case MINIMAP -> cameraOverlayVisible;
            case LAYER_WINDOW -> layerWindowVisible;
        };
    }

    private boolean isChromeWidgetRendered(ChromeWidget widget) {
        return isChromeWidgetPresent(widget) && (!chromeWidgetState(widget).hidden() || revealHiddenElements);
    }

    private boolean isChromeWidgetOperational(ChromeWidget widget) {
        return isChromeWidgetPresent(widget) && !chromeWidgetState(widget).hidden();
    }

    private boolean handleChromeWidgetPress(
            double mouseX,
            double mouseY
    ) {
        SelectionMode selectionMode = selectionMode();
        @Nullable ChromeWidget hoveredWidget = hoveredChromeWidget(mouseX, mouseY);
        if (selectionMode == SelectionMode.REPLACE
            && selectedChromeWidget != null
            && selectedChromeWidgets.size() == 1
            && beginChromeWidgetResize(selectedChromeWidget, mouseX, mouseY)) {
            return true;
        }

        if (hoveredWidget == null) {
            if (selectionMode == SelectionMode.REPLACE) {
                clearChromeSelection();
            }
            // r[impl draw.layer.chrome-selection.marquee]
            chromeMarqueeSelectionDrag = new ChromeMarqueeSelectionDrag(mouseX, mouseY, mouseX, mouseY, selectionMode);
            return true;
        }

        if (selectionMode == SelectionMode.ADD) {
            selectedChromeWidget = hoveredWidget;
            selectedChromeWidgets.add(hoveredWidget);
            return true;
        }
        if (selectionMode == SelectionMode.SUBTRACT) {
            removeChromeWidgetFromSelection(hoveredWidget);
            return true;
        }

        if (selectionMode == SelectionMode.REPLACE && isChromeWidgetSelected(hoveredWidget)) {
            return beginChromeSelectionMove(hoveredWidget, mouseX, mouseY) || chromeWidgetBounds(hoveredWidget).contains(mouseX, mouseY);
        }

        selectOnlyChromeWidget(hoveredWidget);
        return beginChromeSelectionMove(hoveredWidget, mouseX, mouseY) || chromeWidgetBounds(hoveredWidget).contains(mouseX, mouseY);
    }

    private @Nullable ChromeWidget hoveredChromeWidget(
            double mouseX,
            double mouseY
    ) {
        for (int index = ChromeWidget.VALUES.length - 1; index >= 0; index--) {
            ChromeWidget widget = ChromeWidget.VALUES[index];
            if (isChromeWidgetRendered(widget) && chromeWidgetBounds(widget).contains(mouseX, mouseY)) {
                return widget;
            }
        }
        return null;
    }

    private Rect chromeWidgetBounds(ChromeWidget widget) {
        ChromeWidgetState state = chromeWidgetState(widget);
        return switch (widget) {
            case HOTBAR_BAR -> new Rect(state.x(), state.y(), hotbarWidth(), hotbarHeight());
            case AUXILIARY_HOTBAR_BAR -> new Rect(state.x(), state.y(), auxiliaryHotbarWidth(), auxiliaryHotbarHeight());
            case MINIMAP, LAYER_WINDOW -> new Rect(state.x(), state.y(), state.width(), state.height());
            default -> textChromeWidgetBounds(state, chromeWidgetText(widget));
        };
    }

    private boolean beginChromeWidgetDrag(
            ChromeWidget widget,
            double mouseX,
            double mouseY
    ) {
        if (!isChromeWidgetRendered(widget) || !chromeWidgetDragBounds(widget).contains(mouseX, mouseY)) {
            return false;
        }
        ChromeWidgetState state = chromeWidgetState(widget);
        draggingChromeWidget = widget;
        selectOnlyChromeWidget(widget);
        switch (widget) {
            case HOTBAR_BAR -> {
                // r[impl draw.chrome.hotbar.draggable]
                chromeWidgetDragOffsetX = (int) Math.round(mouseX) - state.x();
                chromeWidgetDragOffsetY = (int) Math.round(mouseY) - state.y();
                return true;
            }
            case AUXILIARY_HOTBAR_BAR -> {
                chromeWidgetDragOffsetX = (int) Math.round(mouseX) - state.x();
                chromeWidgetDragOffsetY = (int) Math.round(mouseY) - state.y();
                return true;
            }
            case MINIMAP -> {
                // r[impl draw.chrome.minimap.draggable]
                chromeWidgetDragOffsetX = (int) Math.round(mouseX) - state.x();
                chromeWidgetDragOffsetY = (int) Math.round(mouseY) - state.y();
                return true;
            }
            case LAYER_WINDOW -> {
                // r[impl draw.layer-window.draggable]
                chromeWidgetDragOffsetX = (int) Math.round(mouseX) - state.x();
                chromeWidgetDragOffsetY = (int) Math.round(mouseY) - state.y();
                return true;
            }
            default -> {
                chromeWidgetDragOffsetX = (int) Math.round(mouseX) - state.x();
                chromeWidgetDragOffsetY = (int) Math.round(mouseY) - state.y();
                return true;
            }
        }
    }

    private boolean beginChromeSelectionMove(
            ChromeWidget widget,
            double mouseX,
            double mouseY
    ) {
        if (!isChromeWidgetRendered(widget) || !chromeWidgetDragBounds(widget).contains(mouseX, mouseY)) {
            return false;
        }
        draggingChromeWidget = widget;
        moveChromeSelectionDrag = new MoveChromeSelectionDrag(chromeSelectionSnapshot(), mouseX, mouseY);
        return true;
    }

    private boolean beginChromeWidgetResize(
            ChromeWidget widget,
            double mouseX,
            double mouseY
    ) {
        if (!isChromeWidgetRendered(widget)) {
            return false;
        }
        SelectionHandle handle = null;
        if (selectedChromeWidget == widget && isChromeLayerActive()) {
            handle = findChromeWidgetSelectionHandle(widget, mouseX, mouseY);
        }
        if (handle == null && !chromeWidgetUsesBodyResizeHandle(widget)) {
            return false;
        }
        if (handle == null && !chromeWidgetResizeBounds(widget).contains(mouseX, mouseY)) {
            return false;
        }
        if (handle == null) {
            handle = SelectionHandle.SOUTH_EAST;
        }
        ChromeWidgetState state = chromeWidgetState(widget);
        resizingChromeWidget = widget;
        selectOnlyChromeWidget(widget);
        chromeWidgetResizeHandle = handle;
        chromeWidgetResizeOriginalBounds = chromeWidgetBounds(widget);
        chromeWidgetResizeAnchorX = (int) Math.round(mouseX);
        chromeWidgetResizeAnchorY = (int) Math.round(mouseY);
        switch (widget) {
            case HOTBAR_BAR -> {
                // r[impl draw.layer.chrome-customization-mode]
                chromeWidgetResizeStartScale = state.scale();
                return true;
            }
            case AUXILIARY_HOTBAR_BAR -> {
                chromeWidgetResizeStartScale = state.scale();
                return true;
            }
            case MINIMAP -> {
                // r[impl draw.layer.chrome-customization-mode]
                chromeWidgetResizeStartWidth = state.width();
                chromeWidgetResizeStartHeight = state.height();
                return true;
            }
            case LAYER_WINDOW -> {
                // r[impl draw.layer-window.resize-handle]
                // r[impl draw.layer-window.resizable]
                chromeWidgetResizeStartWidth = state.width();
                chromeWidgetResizeStartHeight = state.height();
                return true;
            }
            default -> {
                chromeWidgetResizeStartScale = state.scale();
                return true;
            }
        }
    }

    private boolean dragActiveChromeWidget(
            double mouseX,
            double mouseY
    ) {
        if (moveChromeSelectionDrag != null) {
            applyChromeSelectionMove(moveChromeSelectionDrag, mouseX, mouseY);
            return true;
        }
        if (draggingChromeWidget == null) {
            return false;
        }
        switch (draggingChromeWidget) {
            case HOTBAR_BAR -> {
                // r[impl draw.chrome.hotbar.draggable]
                moveChromeWidget(draggingChromeWidget, (int) Math.round(mouseX) - chromeWidgetDragOffsetX, (int) Math.round(mouseY) - chromeWidgetDragOffsetY);
                return true;
            }
            case AUXILIARY_HOTBAR_BAR -> {
                moveChromeWidget(draggingChromeWidget, (int) Math.round(mouseX) - chromeWidgetDragOffsetX, (int) Math.round(mouseY) - chromeWidgetDragOffsetY);
                return true;
            }
            case MINIMAP -> {
                // r[impl draw.chrome.minimap.draggable]
                moveChromeWidget(draggingChromeWidget, (int) Math.round(mouseX) - chromeWidgetDragOffsetX, (int) Math.round(mouseY) - chromeWidgetDragOffsetY);
                return true;
            }
            case LAYER_WINDOW -> {
                // r[impl draw.layer-window.draggable]
                moveChromeWidget(draggingChromeWidget, (int) Math.round(mouseX) - chromeWidgetDragOffsetX, (int) Math.round(mouseY) - chromeWidgetDragOffsetY);
                return true;
            }
            default -> {
                moveChromeWidget(draggingChromeWidget, (int) Math.round(mouseX) - chromeWidgetDragOffsetX, (int) Math.round(mouseY) - chromeWidgetDragOffsetY);
                return true;
            }
        }
    }

    private boolean resizeActiveChromeWidget(
            double mouseX,
            double mouseY
    ) {
        if (resizingChromeWidget == null || chromeWidgetResizeHandle == null || chromeWidgetResizeOriginalBounds == null) {
            return false;
        }
        Rect resizedBounds = resizedRect(
                chromeWidgetResizeOriginalBounds,
                chromeWidgetResizeHandle,
                (int) Math.round(mouseX),
                (int) Math.round(mouseY),
                chromeWidgetMinimumWidth(resizingChromeWidget),
                chromeWidgetMinimumHeight(resizingChromeWidget)
        );
        resizeChromeWidget(resizingChromeWidget, resizedBounds.width(), resizedBounds.height(), chromeWidgetScaleForBounds(resizingChromeWidget, resizedBounds));
        moveChromeWidget(resizingChromeWidget, resizedBounds.left(), resizedBounds.top());
        return true;
    }

    private boolean releaseChromeWidgetInteractions() {
        if (moveChromeSelectionDrag != null) {
            for (ChromeWidget widget : selectedChromeWidgets) {
                clampChromeWidgetToScreen(widget);
            }
            moveChromeSelectionDrag = null;
            draggingChromeWidget = null;
            return true;
        }
        if (draggingChromeWidget != null) {
            clampChromeWidgetToScreen(draggingChromeWidget);
            draggingChromeWidget = null;
            return true;
        }
        if (resizingChromeWidget != null) {
            clampChromeWidgetToScreen(resizingChromeWidget);
            resizingChromeWidget = null;
            chromeWidgetResizeHandle = null;
            chromeWidgetResizeOriginalBounds = null;
            return true;
        }
        return false;
    }

    private List<ChromeWidgetSnapshot> chromeSelectionSnapshot() {
        List<ChromeWidgetSnapshot> snapshots = new ArrayList<>(selectedChromeWidgets.size());
        for (ChromeWidget widget : selectedChromeWidgets) {
            snapshots.add(new ChromeWidgetSnapshot(widget, chromeWidgetState(widget)));
        }
        return snapshots;
    }

    private void applyChromeSelectionMove(
            MoveChromeSelectionDrag drag,
            double mouseX,
            double mouseY
    ) {
        int dx = (int) Math.round(mouseX - drag.startMouseX());
        int dy = (int) Math.round(mouseY - drag.startMouseY());
        for (ChromeWidgetSnapshot snapshot : drag.snapshots()) {
            setChromeWidgetState(snapshot.widget(), snapshot.state().withPosition(snapshot.state().x() + dx, snapshot.state().y() + dy));
            clampChromeWidgetToScreen(snapshot.widget());
        }
    }

    private ChromeWidgetState chromeWidgetState(ChromeWidget widget) {
        return switch (widget) {
            case SCREEN_TITLE -> screenTitleWidget;
            case SCREEN_SUBTITLE -> screenSubtitleWidget;
            case ACTIVE_LAYER_LABEL -> layerLabelWidget;
            case ACTIVE_LAYER_VALUE -> layerValueWidget;
            case CAMERA_POSITION_LABEL -> cameraLabelWidget;
            case CAMERA_POSITION_X -> cameraXWidget;
            case CAMERA_POSITION_SEPARATOR -> cameraSeparatorWidget;
            case CAMERA_POSITION_Y -> cameraYWidget;
            case ZOOM_LABEL -> zoomLabelWidget;
            case ZOOM_VALUE -> zoomValueWidget;
            case CURSOR_LABEL -> cursorLabelWidget;
            case CURSOR_X -> cursorXWidget;
            case CURSOR_SEPARATOR -> cursorSeparatorWidget;
            case CURSOR_Y -> cursorYWidget;
            case HOTBAR_BAR -> hotbarWidget;
            case AUXILIARY_HOTBAR_BAR -> auxiliaryHotbarWidget;
            case HOTBAR_TITLE -> hotbarTitleWidget;
            case HOTBAR_SUBTITLE -> hotbarSubtitleWidget;
            case MINIMAP -> minimapWidget;
            case LAYER_WINDOW -> layerWindowWidget;
        };
    }

    private void setChromeWidgetState(
            ChromeWidget widget,
            ChromeWidgetState state
    ) {
        switch (widget) {
            case SCREEN_TITLE -> screenTitleWidget = state;
            case SCREEN_SUBTITLE -> screenSubtitleWidget = state;
            case ACTIVE_LAYER_LABEL -> layerLabelWidget = state;
            case ACTIVE_LAYER_VALUE -> layerValueWidget = state;
            case CAMERA_POSITION_LABEL -> cameraLabelWidget = state;
            case CAMERA_POSITION_X -> cameraXWidget = state;
            case CAMERA_POSITION_SEPARATOR -> cameraSeparatorWidget = state;
            case CAMERA_POSITION_Y -> cameraYWidget = state;
            case ZOOM_LABEL -> zoomLabelWidget = state;
            case ZOOM_VALUE -> zoomValueWidget = state;
            case CURSOR_LABEL -> cursorLabelWidget = state;
            case CURSOR_X -> cursorXWidget = state;
            case CURSOR_SEPARATOR -> cursorSeparatorWidget = state;
            case CURSOR_Y -> cursorYWidget = state;
            case HOTBAR_BAR -> hotbarWidget = state;
            case AUXILIARY_HOTBAR_BAR -> auxiliaryHotbarWidget = state;
            case HOTBAR_TITLE -> hotbarTitleWidget = state;
            case HOTBAR_SUBTITLE -> hotbarSubtitleWidget = state;
            case MINIMAP -> minimapWidget = state;
            case LAYER_WINDOW -> layerWindowWidget = state;
        }
    }

    private void moveChromeWidget(
            ChromeWidget widget,
            int x,
            int y
    ) {
        switch (widget) {
            case HOTBAR_BAR -> {
                hotbarWidget = hotbarWidget.withPosition(x, y);
                clampHotbarToScreen();
            }
            case AUXILIARY_HOTBAR_BAR -> {
                auxiliaryHotbarWidget = auxiliaryHotbarWidget.withPosition(x, y);
                clampAuxiliaryHotbarToScreen();
            }
            case MINIMAP -> {
                minimapWidget = minimapWidget.withPosition(x, y);
                clampCameraOverlayToScreen();
            }
            case LAYER_WINDOW -> {
                layerWindowWidget = layerWindowWidget.withPosition(x, y);
                clampLayerWindowToScreen();
            }
            default -> {
                setChromeWidgetState(widget, chromeWidgetState(widget).withPosition(x, y));
                clampTextChromeWidgetToScreen(widget);
            }
        }
    }

    private void resizeChromeWidget(
            ChromeWidget widget,
            int width,
            int height,
            double scale
    ) {
        switch (widget) {
            case HOTBAR_BAR -> {
                hotbarWidget = hotbarWidget.withScale(scale);
                clampHotbarToScreen();
            }
            case AUXILIARY_HOTBAR_BAR -> {
                auxiliaryHotbarWidget = auxiliaryHotbarWidget.withScale(scale);
                clampAuxiliaryHotbarToScreen();
            }
            case MINIMAP -> {
                minimapWidget = minimapWidget.withSize(width, height);
                clampCameraOverlayToScreen();
            }
            case LAYER_WINDOW -> {
                layerWindowWidget = layerWindowWidget.withSize(width, height);
                clampLayerWindowToScreen();
            }
            default -> {
                setChromeWidgetState(widget, chromeWidgetState(widget).withScale(scale));
                clampTextChromeWidgetToScreen(widget);
            }
        }
    }

    private Rect chromeWidgetDragBounds(ChromeWidget widget) {
        return switch (widget) {
            case HOTBAR_BAR -> chromeWidgetBounds(widget);
            case AUXILIARY_HOTBAR_BAR -> chromeWidgetBounds(widget);
            case MINIMAP -> cameraOverlayHeaderBounds();
            case LAYER_WINDOW -> layerWindowHeaderBounds();
            default -> chromeWidgetBounds(widget);
        };
    }

    private Rect chromeWidgetResizeBounds(ChromeWidget widget) {
        return switch (widget) {
            case HOTBAR_BAR, AUXILIARY_HOTBAR_BAR, HOTBAR_TITLE, HOTBAR_SUBTITLE, MINIMAP -> chromeWidgetBounds(widget);
            case LAYER_WINDOW -> layerWindowResizeHandleBounds();
            default -> chromeWidgetBounds(widget);
        };
    }

    private boolean chromeWidgetUsesBodyResizeHandle(ChromeWidget widget) {
        return widget == ChromeWidget.LAYER_WINDOW;
    }

    private @Nullable DrawLayer layerForHotkey(int keyCode) {
        return switch (keyCode) {
            case GLFW.GLFW_KEY_1 -> DrawLayer.ELEMENTS;
            case GLFW.GLFW_KEY_2 -> DrawLayer.CHROME;
            case GLFW.GLFW_KEY_3 -> DrawLayer.SHELL;
            default -> null;
        };
    }

    // r[impl draw.layer.muting.exists]
    // r[impl draw.layer.muting.affects-interaction]
    private boolean isLayerMuted(DrawLayer layer) {
        return switch (layer) {
            case ELEMENTS -> elementsLayerMuted;
            case SHELL -> shellLayerMuted;
            case CHROME -> chromeLayerMuted;
        };
    }

    private void setLayerMuted(
            DrawLayer layer,
            boolean muted
    ) {
        switch (layer) {
            case ELEMENTS -> elementsLayerMuted = muted;
            case SHELL -> shellLayerMuted = muted;
            case CHROME -> chromeLayerMuted = muted;
        }

        if (muted) {
            if (layer.canvasLayer()) {
                clearCanvasSelection();
                if (textEditingElementId >= 0) {
                    finishTextEditing();
                }
                moveSelectionDrag = null;
                resizeSelectionDrag = null;
                marqueeSelectionDrag = null;
                draftInteraction = null;
                pendingArrowAnchors.clear();
                pendingArrowProjection = null;
            } else {
                clearChromeSelection();
                draggingChromeWidget = null;
                moveChromeSelectionDrag = null;
                resizingChromeWidget = null;
                chromeMarqueeSelectionDrag = null;
                chromeWidgetResizeHandle = null;
                chromeWidgetResizeOriginalBounds = null;
            }
        }
    }

    private void toggleLayerMuted(DrawLayer layer) {
        setLayerMuted(layer, !isLayerMuted(layer));
    }

    private boolean isZenSoloActiveFor(DrawLayer layer) {
        for (DrawLayer candidate : DrawLayer.VALUES) {
            if (candidate == layer) {
                if (isLayerMuted(candidate)) {
                    return false;
                }
                continue;
            }
            if (!isLayerMuted(candidate)) {
                return false;
            }
        }
        return true;
    }

    private void toggleZenSoloFor(DrawLayer layer) {
        if (isZenSoloActiveFor(layer)) {
            for (DrawLayer candidate : DrawLayer.VALUES) {
                setLayerMuted(candidate, false);
            }
            return;
        }
        for (DrawLayer candidate : DrawLayer.VALUES) {
            setLayerMuted(candidate, candidate != layer);
        }
    }

    // r[impl draw.layer.active.exists]
    // r[impl draw.layer.edit-routing]
    private void switchActiveLayer(DrawLayer layer) {
        if (activeLayer == layer) {
            return;
        }
        if (textEditingElementId >= 0) {
            finishTextEditing();
        }
        draftInteraction = null;
        pendingArrowAnchors.clear();
        pendingArrowProjection = null;
        moveSelectionDrag = null;
        resizeSelectionDrag = null;
        marqueeSelectionDrag = null;
        chromeMarqueeSelectionDrag = null;
        duplicateSelectionPendingOnDrag = false;
        cameraFrameDrag = null;
        draggingChromeWidget = null;
        moveChromeSelectionDrag = null;
        resizingChromeWidget = null;
        chromeWidgetResizeHandle = null;
        chromeWidgetResizeOriginalBounds = null;
        clearChromeSelection();
        clearCanvasSelection();
        activeLayer = layer;
    }

    private Rect hotbarHeaderBounds() {
        return chromeWidgetBounds(ChromeWidget.HOTBAR_BAR);
    }

    private Rect hotbarSlotBounds(int slotIndex) {
        int left = hotbarWidget.x() + scaledHotbarUnit(slotIndex * 20 + 3);
        int top = hotbarWidget.y() + scaledHotbarUnit(3);
        int size = Math.max(8, scaledHotbarUnit(16));
        return new Rect(left, top, size, size);
    }

    private void clampHotbarToScreen() {
        hotbarWidget = hotbarWidget.withScale(Mth.clamp(hotbarWidget.scale(), HOTBAR_MIN_SCALE, HOTBAR_MAX_SCALE));
        hotbarWidget = hotbarWidget.withPosition(
                Mth.clamp(hotbarWidget.x(), CHROME_EDGE_MARGIN, Math.max(CHROME_EDGE_MARGIN, width - hotbarWidth() - CHROME_EDGE_MARGIN)),
                Mth.clamp(hotbarWidget.y(), CHROME_EDGE_MARGIN, Math.max(CHROME_EDGE_MARGIN, height - hotbarHeight() - CHROME_EDGE_MARGIN))
        );
    }

    private Rect auxiliaryHotbarBounds() {
        return chromeWidgetBounds(ChromeWidget.AUXILIARY_HOTBAR_BAR);
    }

    private Rect auxiliaryHotbarSlotBounds() {
        int left = auxiliaryHotbarWidget.x() + scaledAuxiliaryHotbarUnit(3);
        int top = auxiliaryHotbarWidget.y() + scaledAuxiliaryHotbarUnit(3);
        int size = Math.max(8, scaledAuxiliaryHotbarUnit(16));
        return new Rect(left, top, size, size);
    }

    private void clampAuxiliaryHotbarToScreen() {
        auxiliaryHotbarWidget = auxiliaryHotbarWidget.withScale(Mth.clamp(auxiliaryHotbarWidget.scale(), HOTBAR_MIN_SCALE, HOTBAR_MAX_SCALE));
        auxiliaryHotbarWidget = auxiliaryHotbarWidget.withPosition(
                Mth.clamp(auxiliaryHotbarWidget.x(), CHROME_EDGE_MARGIN, Math.max(CHROME_EDGE_MARGIN, width - auxiliaryHotbarWidth() - CHROME_EDGE_MARGIN)),
                Mth.clamp(auxiliaryHotbarWidget.y(), CHROME_EDGE_MARGIN, Math.max(CHROME_EDGE_MARGIN, height - auxiliaryHotbarHeight() - CHROME_EDGE_MARGIN))
        );
    }

    private int hotbarWidth() {
        return scaledHotbarUnit(HOTBAR_WIDTH);
    }

    private int hotbarHeight() {
        return scaledHotbarUnit(HOTBAR_HEIGHT);
    }

    private int hotbarHeaderHeight() {
        return scaledHotbarUnit(HOTBAR_HEADER_HEIGHT);
    }

    private int scaledHotbarUnit(int value) {
        return Math.max(1, (int) Math.round(value * hotbarWidget.scale()));
    }

    private int auxiliaryHotbarWidth() {
        return scaledAuxiliaryHotbarUnit(AUXILIARY_HOTBAR_WIDTH);
    }

    private int auxiliaryHotbarHeight() {
        return scaledAuxiliaryHotbarUnit(AUXILIARY_HOTBAR_HEIGHT);
    }

    private int scaledAuxiliaryHotbarUnit(int value) {
        return Math.max(1, (int) Math.round(value * auxiliaryHotbarWidget.scale()));
    }

    private Rect hotbarTitleBounds() {
        return textChromeWidgetBounds(hotbarTitleWidget, IdeLocalizationKeys.IDE_DRAW_HOTBAR_TITLE.getComponent());
    }

    private Rect hotbarSubtitleBounds(Component label) {
        return textChromeWidgetBounds(hotbarSubtitleWidget, label);
    }

    private Rect textChromeWidgetBounds(
            ChromeWidgetState state,
            Component label
    ) {
        int width = Math.max(18, (int) Math.round((font.width(label) + scaledPadding(state.scale()) * 2.0D) * state.scale()));
        int height = Math.max(10, (int) Math.round((font.lineHeight + scaledPadding(state.scale())) * state.scale()));
        return new Rect(state.x(), state.y(), width, height);
    }

    private boolean isTextChromeWidget(ChromeWidget widget) {
        return switch (widget) {
            case SCREEN_TITLE, SCREEN_SUBTITLE,
                 ACTIVE_LAYER_LABEL, ACTIVE_LAYER_VALUE,
                 CAMERA_POSITION_LABEL, CAMERA_POSITION_X, CAMERA_POSITION_SEPARATOR, CAMERA_POSITION_Y,
                 ZOOM_LABEL, ZOOM_VALUE,
                 CURSOR_LABEL, CURSOR_X, CURSOR_SEPARATOR, CURSOR_Y,
                 HOTBAR_TITLE, HOTBAR_SUBTITLE -> true;
            default -> false;
        };
    }

    private void clampTextChromeWidgetToScreen(ChromeWidget widget) {
        if (!isTextChromeWidget(widget)) {
            return;
        }
        ChromeWidgetState state = chromeWidgetState(widget).withScale(Mth.clamp(chromeWidgetState(widget).scale(), TEXT_WIDGET_MIN_SCALE, TEXT_WIDGET_MAX_SCALE));
        setChromeWidgetState(widget, state);
        Rect bounds = chromeWidgetBounds(widget);
        setChromeWidgetState(
                widget,
                chromeWidgetState(widget).withPosition(
                        Mth.clamp(bounds.left(), CHROME_EDGE_MARGIN, Math.max(CHROME_EDGE_MARGIN, width - bounds.width() - CHROME_EDGE_MARGIN)),
                        Mth.clamp(bounds.top(), CHROME_EDGE_MARGIN, Math.max(CHROME_EDGE_MARGIN, height - bounds.height() - CHROME_EDGE_MARGIN))
                )
        );
    }

    private void clampAllTextChromeWidgetsToScreen() {
        for (ChromeWidget widget : ChromeWidget.VALUES) {
            clampTextChromeWidgetToScreen(widget);
        }
    }

    private void clampChromeWidgetToScreen(ChromeWidget widget) {
        switch (widget) {
            case HOTBAR_BAR -> clampHotbarToScreen();
            case AUXILIARY_HOTBAR_BAR -> clampAuxiliaryHotbarToScreen();
            case MINIMAP -> clampCameraOverlayToScreen();
            case LAYER_WINDOW -> clampLayerWindowToScreen();
            default -> clampTextChromeWidgetToScreen(widget);
        }
    }

    private void clampHotbarTitleToScreen() {
        clampTextChromeWidgetToScreen(ChromeWidget.HOTBAR_TITLE);
    }

    private void clampHotbarSubtitleToScreen() {
        clampTextChromeWidgetToScreen(ChromeWidget.HOTBAR_SUBTITLE);
    }

    private Rect layerWindowBounds() {
        return new Rect(layerWindowWidget.x(), layerWindowWidget.y(), layerWindowWidget.width(), layerWindowWidget.height());
    }

    private Rect layerWindowHeaderBounds() {
        Rect bounds = layerWindowBounds();
        return new Rect(bounds.left(), bounds.top(), bounds.width(), LAYER_WINDOW_HEADER_HEIGHT);
    }

    private Rect layerWindowCloseButtonBounds() {
        Rect headerBounds = layerWindowHeaderBounds();
        return new Rect(headerBounds.right() - 14, headerBounds.top() + 2, 10, 10);
    }

    private Rect layerWindowResizeHandleBounds() {
        Rect bounds = layerWindowBounds();
        return new Rect(bounds.right() - LAYER_WINDOW_RESIZE_HANDLE_SIZE, bounds.bottom() - LAYER_WINDOW_RESIZE_HANDLE_SIZE, LAYER_WINDOW_RESIZE_HANDLE_SIZE, LAYER_WINDOW_RESIZE_HANDLE_SIZE);
    }

    private Rect layerEntryBounds(int index) {
        Rect bounds = layerWindowBounds();
        int top = bounds.top() + LAYER_WINDOW_HEADER_HEIGHT + 8 + index * (LAYER_WINDOW_ENTRY_HEIGHT + 2);
        return new Rect(bounds.left() + 8, top, bounds.width() - 16, LAYER_WINDOW_ENTRY_HEIGHT);
    }

    private Rect layerEntryMuteToggleBounds(int index) {
        Rect entryBounds = layerEntryBounds(index);
        return new Rect(entryBounds.right() - 18, entryBounds.top() + 3, 14, 14);
    }

    private int hoveredLayerIndex(
            double mouseX,
            double mouseY
    ) {
        for (int index = 0; index < DrawLayer.VALUES.length; index++) {
            if (layerEntryBounds(index).contains(mouseX, mouseY)) {
                return index;
            }
        }
        return -1;
    }

    private void clampLayerWindowToScreen() {
        layerWindowWidget = layerWindowWidget.withSize(
            Mth.clamp(layerWindowWidget.width(), LAYER_WINDOW_MIN_WIDTH, Math.max(LAYER_WINDOW_MIN_WIDTH, width - CHROME_EDGE_MARGIN * 2)),
            Mth.clamp(layerWindowWidget.height(), LAYER_WINDOW_MIN_HEIGHT, Math.max(LAYER_WINDOW_MIN_HEIGHT, height - CHROME_EDGE_MARGIN * 2))
        );
        layerWindowWidget = layerWindowWidget.withPosition(
            Mth.clamp(layerWindowWidget.x(), CHROME_EDGE_MARGIN, Math.max(CHROME_EDGE_MARGIN, width - layerWindowWidget.width() - CHROME_EDGE_MARGIN)),
            Mth.clamp(layerWindowWidget.y(), CHROME_EDGE_MARGIN, Math.max(CHROME_EDGE_MARGIN, height - layerWindowWidget.height() - CHROME_EDGE_MARGIN))
        );
    }

    private void clampCameraOverlayToScreen() {
        minimapWidget = minimapWidget.withSize(
            Mth.clamp(minimapWidget.width(), MINIMAP_MIN_WIDTH, Math.max(MINIMAP_MIN_WIDTH, width - CHROME_EDGE_MARGIN * 2)),
            Mth.clamp(minimapWidget.height(), MINIMAP_MIN_HEIGHT, Math.max(MINIMAP_MIN_HEIGHT, height - CHROME_EDGE_MARGIN * 2))
        );
        minimapWidget = minimapWidget.withPosition(
            Mth.clamp(minimapWidget.x(), CHROME_EDGE_MARGIN, Math.max(CHROME_EDGE_MARGIN, width - minimapWidget.width() - CHROME_EDGE_MARGIN)),
            Mth.clamp(minimapWidget.y(), CHROME_EDGE_MARGIN, Math.max(CHROME_EDGE_MARGIN, height - minimapWidget.height() - CHROME_EDGE_MARGIN))
        );
    }

    private int chromeWidgetMinimumWidth(ChromeWidget widget) {
        if (isTextChromeWidget(widget)) {
            return 18;
        }
        return switch (widget) {
            case HOTBAR_BAR -> Math.max(60, (int) Math.round(HOTBAR_WIDTH * HOTBAR_MIN_SCALE));
            case AUXILIARY_HOTBAR_BAR -> Math.max(16, (int) Math.round(AUXILIARY_HOTBAR_WIDTH * HOTBAR_MIN_SCALE));
            case MINIMAP -> MINIMAP_MIN_WIDTH;
            case LAYER_WINDOW -> LAYER_WINDOW_MIN_WIDTH;
            default -> 18;
        };
    }

    private int chromeWidgetMinimumHeight(ChromeWidget widget) {
        if (isTextChromeWidget(widget)) {
            return 10;
        }
        return switch (widget) {
            case HOTBAR_BAR -> Math.max(16, (int) Math.round(HOTBAR_HEIGHT * HOTBAR_MIN_SCALE));
            case AUXILIARY_HOTBAR_BAR -> Math.max(16, (int) Math.round(AUXILIARY_HOTBAR_HEIGHT * HOTBAR_MIN_SCALE));
            case MINIMAP -> MINIMAP_MIN_HEIGHT;
            case LAYER_WINDOW -> LAYER_WINDOW_MIN_HEIGHT;
            default -> 10;
        };
    }

    private double chromeWidgetScaleForBounds(
            ChromeWidget widget,
            Rect bounds
    ) {
        if (isTextChromeWidget(widget)) {
            return Mth.clamp(
                    Math.max(bounds.width() / (double) Math.max(1, chromeWidgetBounds(widget).width()), bounds.height() / (double) Math.max(1, chromeWidgetBounds(widget).height())) * chromeWidgetState(widget).scale(),
                    TEXT_WIDGET_MIN_SCALE,
                    TEXT_WIDGET_MAX_SCALE
            );
        }
        return switch (widget) {
            case HOTBAR_BAR -> Mth.clamp(Math.max(bounds.width() / (double) HOTBAR_WIDTH, bounds.height() / (double) HOTBAR_HEIGHT), HOTBAR_MIN_SCALE, HOTBAR_MAX_SCALE);
            case AUXILIARY_HOTBAR_BAR -> Mth.clamp(Math.max(bounds.width() / (double) AUXILIARY_HOTBAR_WIDTH, bounds.height() / (double) AUXILIARY_HOTBAR_HEIGHT), HOTBAR_MIN_SCALE, HOTBAR_MAX_SCALE);
            case MINIMAP, LAYER_WINDOW -> 1.0D;
            default -> 1.0D;
        };
    }

    private void drawTextEditingHandle(PoseStack poseStack) {
        TextElement textElement = editingTextElement();
        if (textElement == null) {
            return;
        }
        Rect bounds = textEditingHandleBounds(textElement);
        fill(poseStack, bounds.left(), bounds.top(), bounds.right(), bounds.bottom(), 0xFF88D498);
        drawScreenRectOutline(poseStack, new ScreenRect(bounds.left(), bounds.top(), bounds.right(), bounds.bottom()), 0xFFF6FAFF);
        fill(poseStack, bounds.left() + 2, bounds.top() + 4, bounds.right() - 2, bounds.top() + 6, 0xFF0F141A);
        fill(poseStack, bounds.left() + 4, bounds.top() + 2, bounds.left() + 6, bounds.bottom() - 2, 0xFF0F141A);
    }

    private Rect textEditingHandleBounds(TextElement textElement) {
        ScreenRect screenBounds = canvasBoundsToScreenRect(textElement.bounds(this));
        return new Rect(
                screenBounds.left() - TEXT_EDIT_HANDLE_SIZE - 2,
                screenBounds.top() - TEXT_EDIT_HANDLE_SIZE - 2,
                TEXT_EDIT_HANDLE_SIZE,
                TEXT_EDIT_HANDLE_SIZE
        );
    }

    private @Nullable SelectionHandle findChromeWidgetSelectionHandle(
            ChromeWidget widget,
            double mouseX,
            double mouseY
    ) {
        ScreenRect screenBounds = toScreenRect(chromeWidgetBounds(widget));
        for (SelectionHandle handle : SelectionHandle.VALUES) {
            ScreenPoint handlePoint = selectionHandlePoint(screenBounds, handle);
            if (Math.abs(mouseX - handlePoint.x()) <= HANDLE_HALF_SIZE + 2 && Math.abs(mouseY - handlePoint.y()) <= HANDLE_HALF_SIZE + 2) {
                return handle;
            }
        }
        return null;
    }

    private boolean isSelectionMoveHover(
            int mouseX,
            int mouseY,
            CanvasBounds selectionBounds
    ) {
        @Nullable CameraOverlayProjection overlayProjection = isChromeLayerActive() ? null : cameraOverlayInteractionProjection();
        @Nullable CameraOverlayProjection projectionUnderMouse = overlayProjection != null && overlayProjection.mapBounds().contains(mouseX, mouseY) ? overlayProjection : null;
        if (isChromeWidgetOperational(ChromeWidget.MINIMAP) && cameraOverlayBounds().contains(mouseX, mouseY) && projectionUnderMouse == null) {
            return false;
        }
        CanvasPoint canvasPoint = screenToCanvas(mouseX, mouseY, projectionUnderMouse);
        return selectionBounds.contains(canvasPoint);
    }

    private void initChromeCursors() {
        if (handCursorHandle == 0L) {
            handCursorHandle = GLFW.glfwCreateStandardCursor(GLFW.GLFW_HAND_CURSOR);
        }
        if (moveCursorHandle == 0L) {
            moveCursorHandle = GLFW.glfwCreateStandardCursor(GLFW.GLFW_RESIZE_ALL_CURSOR);
        }
        if (horizontalResizeCursorHandle == 0L) {
            horizontalResizeCursorHandle = GLFW.glfwCreateStandardCursor(GLFW.GLFW_HRESIZE_CURSOR);
        }
        if (verticalResizeCursorHandle == 0L) {
            verticalResizeCursorHandle = GLFW.glfwCreateStandardCursor(GLFW.GLFW_VRESIZE_CURSOR);
        }
        if (diagonalNorthWestSouthEastCursorHandle == 0L) {
            diagonalNorthWestSouthEastCursorHandle = GLFW.glfwCreateStandardCursor(GLFW.GLFW_RESIZE_NWSE_CURSOR);
        }
        if (diagonalNorthEastSouthWestCursorHandle == 0L) {
            diagonalNorthEastSouthWestCursorHandle = GLFW.glfwCreateStandardCursor(GLFW.GLFW_RESIZE_NESW_CURSOR);
        }
    }

    private void releaseChromeCursors() {
        applyChromeCursor(ChromeCursor.DEFAULT);
        if (handCursorHandle != 0L) {
            GLFW.glfwDestroyCursor(handCursorHandle);
            handCursorHandle = 0L;
        }
        if (moveCursorHandle != 0L) {
            GLFW.glfwDestroyCursor(moveCursorHandle);
            moveCursorHandle = 0L;
        }
        if (horizontalResizeCursorHandle != 0L) {
            GLFW.glfwDestroyCursor(horizontalResizeCursorHandle);
            horizontalResizeCursorHandle = 0L;
        }
        if (verticalResizeCursorHandle != 0L) {
            GLFW.glfwDestroyCursor(verticalResizeCursorHandle);
            verticalResizeCursorHandle = 0L;
        }
        if (diagonalNorthWestSouthEastCursorHandle != 0L) {
            GLFW.glfwDestroyCursor(diagonalNorthWestSouthEastCursorHandle);
            diagonalNorthWestSouthEastCursorHandle = 0L;
        }
        if (diagonalNorthEastSouthWestCursorHandle != 0L) {
            GLFW.glfwDestroyCursor(diagonalNorthEastSouthWestCursorHandle);
            diagonalNorthEastSouthWestCursorHandle = 0L;
        }
    }

    private void updateChromeCursor(
            int mouseX,
            int mouseY
    ) {
        if (moveSelectionDrag != null) {
            applyChromeCursor(ChromeCursor.MOVE);
            return;
        }
        if (resizeSelectionDrag != null) {
            applyChromeCursor(cursorForSelectionHandle(resizeSelectionDrag.handle()));
            return;
        }
        if (activeTool == DrawTool.TEXT && textEditingElementId >= 0) {
            TextElement textElement = editingTextElement();
            if (textElement != null && textEditingHandleBounds(textElement).contains(mouseX, mouseY)) {
                // r[impl draw.tool.text.edit.grab-handle.cursor]
                applyChromeCursor(ChromeCursor.MOVE);
                return;
            }
        }
        if (isCanvasLayerActive() && activeTool == DrawTool.CURSOR && selectionMode() == SelectionMode.REPLACE) {
            CanvasBounds selectionBounds = currentSelectionBounds();
            if (selectionBounds != null) {
                if (canResizeCurrentSelection()) {
                    SelectionHandle handle = findSelectionHandle(mouseX, mouseY, selectionBounds);
                    if (handle != null) {
                        // r[impl draw.tool.cursor.transform_selection.handle-cursor]
                        applyChromeCursor(cursorForSelectionHandle(handle));
                        return;
                    }
                }
                if (canTransformCurrentSelection() && isSelectionMoveHover(mouseX, mouseY, selectionBounds)) {
                    // r[impl draw.tool.cursor.transform_selection.move-cursor]
                    applyChromeCursor(ChromeCursor.MOVE);
                    return;
                }
            }
        }
        if (resizingChromeWidget != null && chromeWidgetResizeHandle != null) {
            applyChromeCursor(cursorForSelectionHandle(chromeWidgetResizeHandle));
            return;
        }
        if (draggingChromeWidget != null) {
            applyChromeCursor(ChromeCursor.HAND);
            return;
        }
        if (isChromeWidgetOperational(ChromeWidget.LAYER_WINDOW)) {
            if (layerWindowResizeHandleBounds().contains(mouseX, mouseY)) {
                applyChromeCursor(ChromeCursor.DIAGONAL_NORTH_WEST_SOUTH_EAST);
                return;
            }
            if (layerWindowCloseButtonBounds().contains(mouseX, mouseY)) {
                applyChromeCursor(ChromeCursor.HAND);
                return;
            }
            int hoveredLayerIndex = hoveredLayerIndex(mouseX, mouseY);
            if (hoveredLayerIndex >= 0) {
                applyChromeCursor(ChromeCursor.HAND);
                return;
            }
            if (layerWindowHeaderBounds().contains(mouseX, mouseY)) {
                applyChromeCursor(ChromeCursor.HAND);
                return;
            }
        }
        if (isChromeLayerActive() && selectedChromeWidget != null && selectedChromeWidgets.size() == 1 && isChromeWidgetRendered(selectedChromeWidget)) {
            SelectionHandle handle = findChromeWidgetSelectionHandle(selectedChromeWidget, mouseX, mouseY);
            if (handle != null) {
                applyChromeCursor(cursorForSelectionHandle(handle));
                return;
            }
        }
        if (isChromeLayerActive()) {
            ChromeWidget hoveredWidget = hoveredChromeWidget(mouseX, mouseY);
            if (hoveredWidget != null) {
                applyChromeCursor(ChromeCursor.HAND);
                return;
            }
        }
        applyChromeCursor(ChromeCursor.DEFAULT);
    }

    private ChromeCursor cursorForSelectionHandle(SelectionHandle handle) {
        if ((handle == SelectionHandle.NORTH_WEST) || (handle == SelectionHandle.SOUTH_EAST)) {
            return ChromeCursor.DIAGONAL_NORTH_WEST_SOUTH_EAST;
        }
        if ((handle == SelectionHandle.NORTH_EAST) || (handle == SelectionHandle.SOUTH_WEST)) {
            return ChromeCursor.DIAGONAL_NORTH_EAST_SOUTH_WEST;
        }
        if (handle.movesLeft() || handle.movesRight()) {
            return ChromeCursor.HORIZONTAL;
        }
        return ChromeCursor.VERTICAL;
    }

    private void applyChromeCursor(ChromeCursor desired) {
        if (desired == activeChromeCursor || minecraft == null) {
            return;
        }

        long windowHandle = minecraft.getWindow().getWindow();
        long cursorHandle = switch (desired) {
            case DEFAULT -> 0L;
            case HAND -> handCursorHandle != 0L ? handCursorHandle : 0L;
            case MOVE -> moveCursorHandle != 0L ? moveCursorHandle : 0L;
            case HORIZONTAL -> horizontalResizeCursorHandle != 0L ? horizontalResizeCursorHandle : 0L;
            case VERTICAL -> verticalResizeCursorHandle != 0L ? verticalResizeCursorHandle : 0L;
            case DIAGONAL_NORTH_WEST_SOUTH_EAST -> diagonalNorthWestSouthEastCursorHandle != 0L ? diagonalNorthWestSouthEastCursorHandle : 0L;
            case DIAGONAL_NORTH_EAST_SOUTH_WEST -> diagonalNorthEastSouthWestCursorHandle != 0L ? diagonalNorthEastSouthWestCursorHandle : 0L;
        };
        GLFW.glfwSetCursor(windowHandle, cursorHandle);
        activeChromeCursor = desired;
    }

    private Rect resizedRect(
            Rect originalBounds,
            SelectionHandle handle,
            int currentX,
            int currentY,
            int minimumWidth,
            int minimumHeight
    ) {
        int left = originalBounds.left();
        int top = originalBounds.top();
        int right = originalBounds.right();
        int bottom = originalBounds.bottom();

        if (handle.movesLeft()) {
            left = Math.min(currentX, right - minimumWidth);
        }
        if (handle.movesRight()) {
            right = Math.max(currentX, left + minimumWidth);
        }
        if (handle.movesTop()) {
            top = Math.min(currentY, bottom - minimumHeight);
        }
        if (handle.movesBottom()) {
            bottom = Math.max(currentY, top + minimumHeight);
        }

        return new Rect(left, top, Math.max(minimumWidth, right - left), Math.max(minimumHeight, bottom - top));
    }

    private int scaledPadding(double scale) {
        return Math.max(2, (int) Math.round(4.0D * scale));
    }

    private ScreenRect toScreenRect(Rect rect) {
        return new ScreenRect(rect.left(), rect.top(), rect.right(), rect.bottom());
    }

    private boolean rectsIntersect(
            Rect rect,
            ScreenRect other
    ) {
        return rect.right() >= other.left()
               && rect.left() <= other.right()
               && rect.bottom() >= other.top()
               && rect.top() <= other.bottom();
    }

    private int renderColor(
            int color,
            boolean hidden
    ) {
        if (!hidden) {
            return color;
        }
        int alpha = (color >>> 24) & 0xFF;
        int red = (color >>> 16) & 0xFF;
        int green = (color >>> 8) & 0xFF;
        int blue = color & 0xFF;
        alpha = Math.max(24, (int) Math.round(alpha * 0.35D));
        red = (int) Math.round(red * 0.65D);
        green = (int) Math.round(green * 0.65D);
        blue = (int) Math.round(blue * 0.65D);
        return (alpha << 24) | (red << 16) | (green << 8) | blue;
    }

    private int hotbarIndexForKeyCode(int keyCode) {
        return switch (keyCode) {
            case GLFW.GLFW_KEY_1 -> 0;
            case GLFW.GLFW_KEY_2 -> 1;
            case GLFW.GLFW_KEY_3 -> 2;
            case GLFW.GLFW_KEY_4 -> 3;
            case GLFW.GLFW_KEY_5 -> 4;
            case GLFW.GLFW_KEY_6 -> 5;
            case GLFW.GLFW_KEY_7 -> 6;
            case GLFW.GLFW_KEY_8 -> 7;
            case GLFW.GLFW_KEY_9 -> 8;
            default -> -1;
        };
    }

    private CanvasPoint screenToCanvas(
            double screenX,
            double screenY,
            @Nullable CameraOverlayProjection projection
    ) {
        if (projection != null) {
            return projection.screenToCanvas(screenX, screenY);
        }
        return new CanvasPoint(
                cameraX + (screenX - screenCenterX()) / zoom,
                cameraY + (screenY - screenCenterY()) / zoom
        );
    }

    private ScreenPoint projectCanvasPointToScreen(
            CanvasPoint point,
            @Nullable CameraOverlayProjection projection
    ) {
        return projection != null ? projection.canvasToScreen(point) : canvasToScreen(point);
    }

    private ScreenPoint canvasToScreen(CanvasPoint point) {
        return new ScreenPoint(canvasToScreenX(point.x()), canvasToScreenY(point.y()));
    }

    private double canvasToScreenX(double canvasX) {
        return (canvasX - cameraX) * zoom + screenCenterX();
    }

    private double canvasToScreenY(double canvasY) {
        return (canvasY - cameraY) * zoom + screenCenterY();
    }

    private double screenCenterX() {
        return Math.rint(width / 2.0D);
    }

    private double screenCenterY() {
        return Math.rint(height / 2.0D);
    }

    private CanvasBounds visibleCanvasBounds() {
        CanvasPoint topLeft = screenToCanvas(0.0D, 0.0D, null);
        CanvasPoint bottomRight = screenToCanvas(width, height, null);
        return CanvasBounds.of(topLeft.x(), topLeft.y(), bottomRight.x(), bottomRight.y());
    }

    private CanvasBounds sceneBoundsIncludingViewport() {
        CanvasBounds result = visibleCanvasBounds();
        result = result.expandToInclude(CanvasBounds.of(-128.0D, -128.0D, 128.0D, 128.0D));
        for (DrawElement element : elements) {
            result = result.expandToInclude(element.bounds(this));
        }
        if (draftInteraction != null) {
            result = result.expandToInclude(CanvasBounds.of(
                    draftInteraction.startPoint().x(),
                    draftInteraction.startPoint().y(),
                    draftInteraction.currentPoint().x(),
                    draftInteraction.currentPoint().y()
            ));
        }
        return result.pad(32.0D);
    }

    private Rect cameraOverlayBounds() {
        return new Rect(minimapWidget.x(), minimapWidget.y(), minimapWidget.width(), minimapWidget.height());
    }

    private Rect cameraOverlayHeaderBounds() {
        Rect overlayBounds = cameraOverlayBounds();
        return new Rect(overlayBounds.left(), overlayBounds.top(), overlayBounds.width(), 24);
    }

    private Rect cameraOverlayResizeHandleBounds() {
        Rect overlayBounds = cameraOverlayBounds();
        return new Rect(
                overlayBounds.right() - MINIMAP_RESIZE_HANDLE_SIZE,
                overlayBounds.bottom() - MINIMAP_RESIZE_HANDLE_SIZE,
                MINIMAP_RESIZE_HANDLE_SIZE,
                MINIMAP_RESIZE_HANDLE_SIZE
        );
    }

    private @Nullable CameraOverlayProjection cameraOverlayRenderProjection() {
        return cameraOverlayProjection(true);
    }

    private @Nullable CameraOverlayProjection cameraOverlayInteractionProjection() {
        return cameraOverlayProjection(false);
    }

    private @Nullable CameraOverlayProjection cameraOverlayProjection(boolean allowHidden) {
        if (allowHidden ? !isChromeWidgetRendered(ChromeWidget.MINIMAP) : !isChromeWidgetOperational(ChromeWidget.MINIMAP)) {
            return null;
        }
        Rect overlayBounds = cameraOverlayBounds();
        Rect mapBounds = new Rect(overlayBounds.left() + 10, overlayBounds.top() + 28, overlayBounds.width() - 20, overlayBounds.height() - 38);
        CanvasBounds sceneBounds = sceneBoundsIncludingViewport();
        double sceneWidth = Math.max(1.0D, sceneBounds.width());
        double sceneHeight = Math.max(1.0D, sceneBounds.height());
        double scale = Math.min(mapBounds.width() / sceneWidth, mapBounds.height() / sceneHeight);
        return new CameraOverlayProjection(mapBounds, sceneBounds, scale);
    }

    private ScreenRect canvasBoundsToScreenRect(CanvasBounds bounds) {
        ScreenPoint topLeft = canvasToScreen(new CanvasPoint(bounds.minX(), bounds.minY()));
        ScreenPoint bottomRight = canvasToScreen(new CanvasPoint(bounds.maxX(), bounds.maxY()));
        return new ScreenRect(
                (int) Math.round(Math.min(topLeft.x(), bottomRight.x())),
                (int) Math.round(Math.min(topLeft.y(), bottomRight.y())),
                (int) Math.round(Math.max(topLeft.x(), bottomRight.x())),
                (int) Math.round(Math.max(topLeft.y(), bottomRight.y()))
        );
    }

    private void drawScreenRectOutline(
            PoseStack poseStack,
            ScreenRect rect,
            int color
    ) {
        fill(poseStack, rect.left(), rect.top(), rect.right(), rect.top() + 1, color);
        fill(poseStack, rect.left(), rect.bottom() - 1, rect.right(), rect.bottom(), color);
        fill(poseStack, rect.left(), rect.top(), rect.left() + 1, rect.bottom(), color);
        fill(poseStack, rect.right() - 1, rect.top(), rect.right(), rect.bottom(), color);
    }

    private @Nullable SelectionHandle findSelectionHandle(
            double mouseX,
            double mouseY,
            CanvasBounds selectionBounds
    ) {
        ScreenRect screenBounds = canvasBoundsToScreenRect(selectionBounds);
        for (SelectionHandle handle : SelectionHandle.VALUES) {
            ScreenPoint handlePoint = selectionHandlePoint(screenBounds, handle);
            if (Math.abs(mouseX - handlePoint.x()) <= HANDLE_HALF_SIZE + 2 && Math.abs(mouseY - handlePoint.y()) <= HANDLE_HALF_SIZE + 2) {
                return handle;
            }
        }
        return null;
    }

    private ScreenPoint selectionHandlePoint(
            ScreenRect bounds,
            SelectionHandle handle
    ) {
        double centerX = (bounds.left() + bounds.right()) / 2.0D;
        double centerY = (bounds.top() + bounds.bottom()) / 2.0D;
        return switch (handle) {
            case NORTH_WEST -> new ScreenPoint(bounds.left(), bounds.top());
            case NORTH -> new ScreenPoint(centerX, bounds.top());
            case NORTH_EAST -> new ScreenPoint(bounds.right(), bounds.top());
            case EAST -> new ScreenPoint(bounds.right(), centerY);
            case SOUTH_EAST -> new ScreenPoint(bounds.right(), bounds.bottom());
            case SOUTH -> new ScreenPoint(centerX, bounds.bottom());
            case SOUTH_WEST -> new ScreenPoint(bounds.left(), bounds.bottom());
            case WEST -> new ScreenPoint(bounds.left(), centerY);
        };
    }

    private CanvasBounds resizedBounds(
            CanvasBounds originalBounds,
            SelectionHandle handle,
            CanvasPoint currentPoint,
            double minimumSize,
            double snapIncrement
    ) {
        double left = originalBounds.minX();
        double top = originalBounds.minY();
        double right = originalBounds.maxX();
        double bottom = originalBounds.maxY();

        if (handle.movesLeft()) {
            double snappedWidth = snapDistance(right - currentPoint.x(), snapIncrement, minimumSize);
            left = right - snappedWidth;
        }
        if (handle.movesRight()) {
            double snappedWidth = snapDistance(currentPoint.x() - left, snapIncrement, minimumSize);
            right = left + snappedWidth;
        }
        if (handle.movesTop()) {
            double snappedHeight = snapDistance(bottom - currentPoint.y(), snapIncrement, minimumSize);
            top = bottom - snappedHeight;
        }
        if (handle.movesBottom()) {
            double snappedHeight = snapDistance(currentPoint.y() - top, snapIncrement, minimumSize);
            bottom = top + snappedHeight;
        }

        return new CanvasBounds(left, top, right, bottom);
    }

    private CanvasPoint transformPoint(
            CanvasPoint point,
            CanvasBounds fromBounds,
            CanvasBounds toBounds
    ) {
        double normalizedX = fromBounds.width() <= 0.000001D ? 0.5D : (point.x() - fromBounds.minX()) / fromBounds.width();
        double normalizedY = fromBounds.height() <= 0.000001D ? 0.5D : (point.y() - fromBounds.minY()) / fromBounds.height();
        return new CanvasPoint(
                toBounds.minX() + normalizedX * toBounds.width(),
                toBounds.minY() + normalizedY * toBounds.height()
        );
    }

    private void seedShellElements() {
        for (ShellTextBinding binding : ShellTextBinding.VALUES) {
            elements.add(new TextElement(nextElementId++, DrawLayer.SHELL, binding.defaultX(), binding.defaultY(), binding.placeholderText(), 0xFFF1F5FB, 1.0D, binding));
        }
    }

    private void refreshShellElements() {
        for (DrawElement element : elements) {
            if (element instanceof TextElement textElement && textElement.shellBinding != null) {
                textElement.text = textElement.shellBinding.resolve(this);
            }
        }
    }

    private String formatShellNumber(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private static double distanceSquared(
            CanvasPoint a,
            CanvasPoint b
    ) {
        double dx = a.x() - b.x();
        double dy = a.y() - b.y();
        return dx * dx + dy * dy;
    }

    private static double distancePointToSegment(
            CanvasPoint point,
            CanvasPoint start,
            CanvasPoint end
    ) {
        double dx = end.x() - start.x();
        double dy = end.y() - start.y();
        double lengthSquared = dx * dx + dy * dy;
        if (lengthSquared <= 0.000001D) {
            return Math.sqrt(distanceSquared(point, start));
        }
        double projection = ((point.x() - start.x()) * dx + (point.y() - start.y()) * dy) / lengthSquared;
        projection = Mth.clamp(projection, 0.0D, 1.0D);
        double closestX = start.x() + projection * dx;
        double closestY = start.y() + projection * dy;
        double offsetX = point.x() - closestX;
        double offsetY = point.y() - closestY;
        return Math.sqrt(offsetX * offsetX + offsetY * offsetY);
    }

    private abstract static class DrawElement {
        private final int id;
        private DrawLayer layer;
        private boolean hidden = false;
        private boolean locked = false;
        private Set<Integer> groupIds = new LinkedHashSet<>();

        protected DrawElement(
                int id,
                DrawLayer layer
        ) {
            this.id = id;
            this.layer = layer;
        }

        public int id() {
            return id;
        }

        public boolean hidden() {
            return hidden;
        }

        public boolean locked() {
            return locked;
        }

        public DrawLayer layer() {
            return layer;
        }

        public Set<Integer> groupIds() {
            return groupIds;
        }

        public void setHidden(boolean hidden) {
            this.hidden = hidden;
        }

        public void setLocked(boolean locked) {
            this.locked = locked;
        }

        protected void copyMetadataTo(DrawElement copy) {
            copy.hidden = hidden;
            copy.locked = locked;
            copy.layer = layer;
            copy.groupIds = new LinkedHashSet<>(groupIds);
        }

        protected void copyMetadataFrom(DrawElement other) {
            hidden = other.hidden;
            locked = other.locked;
            layer = other.layer;
            groupIds = new LinkedHashSet<>(other.groupIds);
        }

        public abstract CanvasBounds bounds(SfmDrawScreen screen);

        public abstract DrawElement copy();

        public abstract DrawElement copyWithId(int id);

        public abstract void copyFrom(DrawElement other);

        public abstract void translate(double dx, double dy);

        public abstract void transform(CanvasBounds fromBounds, CanvasBounds toBounds, SfmDrawScreen screen);
    }

    private static final class RectangleElement extends DrawElement {
        private double x1;
        private double y1;
        private double x2;
        private double y2;
        private final int fillColor;
        private final int strokeColor;

        private RectangleElement(
                int id,
            DrawLayer layer,
                double x1,
                double y1,
                double x2,
                double y2,
                int fillColor,
                int strokeColor
        ) {
            super(id, layer);
            this.x1 = x1;
            this.y1 = y1;
            this.x2 = x2;
            this.y2 = y2;
            this.fillColor = fillColor;
            this.strokeColor = strokeColor;
        }

        @Override
        public CanvasBounds bounds(SfmDrawScreen screen) {
            return CanvasBounds.of(x1, y1, x2, y2);
        }

        @Override
        public DrawElement copy() {
            RectangleElement copy = new RectangleElement(id(), layer(), x1, y1, x2, y2, fillColor, strokeColor);
            copyMetadataTo(copy);
            return copy;
        }

        @Override
        public DrawElement copyWithId(int id) {
            RectangleElement copy = new RectangleElement(id, layer(), x1, y1, x2, y2, fillColor, strokeColor);
            copyMetadataTo(copy);
            return copy;
        }

        @Override
        public void copyFrom(DrawElement other) {
            RectangleElement element = (RectangleElement) other;
            copyMetadataFrom(element);
            x1 = element.x1;
            y1 = element.y1;
            x2 = element.x2;
            y2 = element.y2;
        }

        @Override
        public void translate(double dx, double dy) {
            x1 += dx;
            y1 += dy;
            x2 += dx;
            y2 += dy;
        }

        @Override
        public void transform(CanvasBounds fromBounds, CanvasBounds toBounds, SfmDrawScreen screen) {
            CanvasPoint a = screen.transformPoint(new CanvasPoint(x1, y1), fromBounds, toBounds);
            CanvasPoint b = screen.transformPoint(new CanvasPoint(x2, y2), fromBounds, toBounds);
            x1 = a.x();
            y1 = a.y();
            x2 = b.x();
            y2 = b.y();
        }
    }

    private static final class ArrowElement extends DrawElement {
        private List<CanvasPoint> points;
        private Set<Integer> hiddenAnchorIndexes = new LinkedHashSet<>();
        private final int color;

        private ArrowElement(
                int id,
                DrawLayer layer,
                List<CanvasPoint> points,
                int color
        ) {
            super(id, layer);
            this.points = new ArrayList<>(points);
            this.color = color;
        }

        @Override
        public CanvasBounds bounds(SfmDrawScreen screen) {
            double minX = Double.POSITIVE_INFINITY;
            double minY = Double.POSITIVE_INFINITY;
            double maxX = Double.NEGATIVE_INFINITY;
            double maxY = Double.NEGATIVE_INFINITY;
            for (CanvasPoint point : points) {
                minX = Math.min(minX, point.x());
                minY = Math.min(minY, point.y());
                maxX = Math.max(maxX, point.x());
                maxY = Math.max(maxY, point.y());
            }
            return CanvasBounds.of(minX, minY, maxX, maxY).pad(8.0D);
        }

        @Override
        public DrawElement copy() {
            ArrowElement copy = new ArrowElement(id(), layer(), points, color);
            copyMetadataTo(copy);
            copy.hiddenAnchorIndexes = new LinkedHashSet<>(hiddenAnchorIndexes);
            return copy;
        }

        @Override
        public DrawElement copyWithId(int id) {
            ArrowElement copy = new ArrowElement(id, layer(), points, color);
            copyMetadataTo(copy);
            copy.hiddenAnchorIndexes = new LinkedHashSet<>(hiddenAnchorIndexes);
            return copy;
        }

        @Override
        public void copyFrom(DrawElement other) {
            ArrowElement element = (ArrowElement) other;
            copyMetadataFrom(element);
            points = new ArrayList<>(element.points);
            hiddenAnchorIndexes = new LinkedHashSet<>(element.hiddenAnchorIndexes);
        }

        @Override
        public void translate(double dx, double dy) {
            List<CanvasPoint> translated = new ArrayList<>(points.size());
            for (CanvasPoint point : points) {
                translated.add(new CanvasPoint(point.x() + dx, point.y() + dy));
            }
            points = translated;
        }

        @Override
        public void transform(CanvasBounds fromBounds, CanvasBounds toBounds, SfmDrawScreen screen) {
            List<CanvasPoint> transformed = new ArrayList<>(points.size());
            for (CanvasPoint point : points) {
                transformed.add(screen.transformPoint(point, fromBounds, toBounds));
            }
            points = transformed;
        }

        public boolean hasPointIndex(int index) {
            return index >= 0 && index < points.size();
        }

        public boolean isAnchorHidden(int index) {
            return hiddenAnchorIndexes.contains(index);
        }

        public void setAnchorHidden(
                int index,
                boolean hidden
        ) {
            if (hidden) {
                hiddenAnchorIndexes.add(index);
            } else {
                hiddenAnchorIndexes.remove(index);
            }
        }

        public int visibleAnchorCount() {
            return points.size() - hiddenAnchorIndexes.size();
        }

        public void removePoint(int index) {
            points.remove(index);
            Set<Integer> adjustedHiddenAnchorIndexes = new LinkedHashSet<>();
            for (Integer hiddenAnchorIndex : hiddenAnchorIndexes) {
                if (hiddenAnchorIndex == index) {
                    continue;
                }
                adjustedHiddenAnchorIndexes.add(hiddenAnchorIndex > index ? hiddenAnchorIndex - 1 : hiddenAnchorIndex);
            }
            hiddenAnchorIndexes = adjustedHiddenAnchorIndexes;
        }
    }

    private static final class TextElement extends DrawElement {
        private double x;
        private double y;
        private String text;
        private final int color;
        private double textScale;
        private final @Nullable ShellTextBinding shellBinding;

        private TextElement(
                int id,
                DrawLayer layer,
                double x,
                double y,
                String text,
                int color,
                double textScale,
                @Nullable ShellTextBinding shellBinding
        ) {
            super(id, layer);
            this.x = x;
            this.y = y;
            this.text = text;
            this.color = color;
            this.textScale = textScale;
            this.shellBinding = shellBinding;
        }

        @Override
        public CanvasBounds bounds(SfmDrawScreen screen) {
            String[] lines = screen.textLines(this);
            int pixelWidth = 0;
            for (String line : lines) {
                pixelWidth = Math.max(pixelWidth, screen.font.width(line.isEmpty() ? " " : line));
            }
            double width = Math.max(16.0D, pixelWidth * textScale);
            double height = Math.max(screen.font.lineHeight, lines.length * screen.font.lineHeight * textScale);
            return CanvasBounds.of(x, y, x + width, y + height);
        }

        @Override
        public DrawElement copy() {
            TextElement copy = new TextElement(id(), layer(), x, y, text, color, textScale, shellBinding);
            copyMetadataTo(copy);
            return copy;
        }

        @Override
        public DrawElement copyWithId(int id) {
            TextElement copy = new TextElement(id, layer(), x, y, text, color, textScale, shellBinding);
            copyMetadataTo(copy);
            return copy;
        }

        @Override
        public void copyFrom(DrawElement other) {
            TextElement element = (TextElement) other;
            copyMetadataFrom(element);
            x = element.x;
            y = element.y;
            text = element.text;
            textScale = element.textScale;
        }

        @Override
        public void translate(double dx, double dy) {
            x += dx;
            y += dy;
        }

        @Override
        public void transform(CanvasBounds fromBounds, CanvasBounds toBounds, SfmDrawScreen screen) {
            CanvasPoint point = screen.transformPoint(new CanvasPoint(x, y), fromBounds, toBounds);
            x = point.x();
            y = point.y();
            double widthScale = fromBounds.width() <= 0.000001D ? 1.0D : toBounds.width() / fromBounds.width();
            double heightScale = fromBounds.height() <= 0.000001D ? 1.0D : toBounds.height() / fromBounds.height();
            double dominantScale = Math.max(0.1D, Math.max(widthScale, heightScale));
            textScale *= dominantScale;
        }
    }

    private static final class FreehandElement extends DrawElement {
        private List<CanvasPoint> points;
        private final int color;

        private FreehandElement(
                int id,
                DrawLayer layer,
                List<CanvasPoint> points,
                int color
        ) {
            super(id, layer);
            this.points = new ArrayList<>(points);
            this.color = color;
        }

        @Override
        public CanvasBounds bounds(SfmDrawScreen screen) {
            double minX = Double.POSITIVE_INFINITY;
            double minY = Double.POSITIVE_INFINITY;
            double maxX = Double.NEGATIVE_INFINITY;
            double maxY = Double.NEGATIVE_INFINITY;
            for (CanvasPoint point : points) {
                minX = Math.min(minX, point.x());
                minY = Math.min(minY, point.y());
                maxX = Math.max(maxX, point.x());
                maxY = Math.max(maxY, point.y());
            }
            return CanvasBounds.of(minX, minY, maxX, maxY).pad(8.0D);
        }

        @Override
        public DrawElement copy() {
            FreehandElement copy = new FreehandElement(id(), layer(), points, color);
            copyMetadataTo(copy);
            return copy;
        }

        @Override
        public DrawElement copyWithId(int id) {
            FreehandElement copy = new FreehandElement(id, layer(), points, color);
            copyMetadataTo(copy);
            return copy;
        }

        @Override
        public void copyFrom(DrawElement other) {
            FreehandElement element = (FreehandElement) other;
            copyMetadataFrom(element);
            points = new ArrayList<>(element.points);
        }

        @Override
        public void translate(double dx, double dy) {
            List<CanvasPoint> translated = new ArrayList<>(points.size());
            for (CanvasPoint point : points) {
                translated.add(new CanvasPoint(point.x() + dx, point.y() + dy));
            }
            points = translated;
        }

        @Override
        public void transform(CanvasBounds fromBounds, CanvasBounds toBounds, SfmDrawScreen screen) {
            List<CanvasPoint> transformed = new ArrayList<>(points.size());
            for (CanvasPoint point : points) {
                transformed.add(screen.transformPoint(point, fromBounds, toBounds));
            }
            points = transformed;
        }
    }

    private record ElementSnapshot(
            int id,
            DrawElement elementCopy
    ) {
    }

        private record ArrowAnchorReference(
            int arrowId,
            int anchorIndex
        ) {
        }

        private record ArrowAnchorSnapshot(
            int arrowId,
            int anchorIndex,
            CanvasPoint originalPoint
        ) {
        }

        private record ChromeWidgetSnapshot(
            ChromeWidget widget,
            ChromeWidgetState state
        ) {
        }

    private static final class DraftInteraction {
        private final DrawTool tool;
        private final CanvasPoint startPoint;
        private CanvasPoint currentPoint;
        private final @Nullable CameraOverlayProjection projection;
        private final List<CanvasPoint> points = new ArrayList<>();

        private DraftInteraction(
                DrawTool tool,
                CanvasPoint startPoint,
                CanvasPoint currentPoint,
                @Nullable CameraOverlayProjection projection
        ) {
            this.tool = tool;
            this.startPoint = startPoint;
            this.currentPoint = currentPoint;
            this.projection = projection;
        }

        public DrawTool tool() {
            return tool;
        }

        public CanvasPoint startPoint() {
            return startPoint;
        }

        public CanvasPoint currentPoint() {
            return currentPoint;
        }

        public @Nullable CameraOverlayProjection projection() {
            return projection;
        }

        public List<CanvasPoint> points() {
            return points;
        }
    }

    private record MoveSelectionDrag(
            List<ElementSnapshot> snapshots,
            List<ArrowAnchorSnapshot> anchorSnapshots,
            CanvasPoint startPoint,
            @Nullable CameraOverlayProjection projection,
            CanvasBounds originalBounds,
            CanvasPoint snapOrigin
    ) {
    }

    private record ResizeSelectionDrag(
            List<ElementSnapshot> snapshots,
            CanvasBounds originalBounds,
            SelectionHandle handle,
            @Nullable CameraOverlayProjection projection
    ) {
    }

        private record MoveChromeSelectionDrag(
            List<ChromeWidgetSnapshot> snapshots,
            double startMouseX,
            double startMouseY
        ) {
        }

    private static final class MarqueeSelectionDrag {
        private final CanvasPoint startPoint;
        private CanvasPoint currentPoint;
        private final @Nullable CameraOverlayProjection projection;
        private final SelectionMode mode;

        private MarqueeSelectionDrag(
                CanvasPoint startPoint,
                CanvasPoint currentPoint,
                @Nullable CameraOverlayProjection projection,
                SelectionMode mode
        ) {
            this.startPoint = startPoint;
            this.currentPoint = currentPoint;
            this.projection = projection;
            this.mode = mode;
        }

        public @Nullable CameraOverlayProjection projection() {
            return projection;
        }

        public SelectionMode mode() {
            return mode;
        }
    }

    private static final class ChromeMarqueeSelectionDrag {
        private final double startX;
        private final double startY;
        private double currentX;
        private double currentY;
        private final SelectionMode mode;

        private ChromeMarqueeSelectionDrag(
                double startX,
                double startY,
                double currentX,
                double currentY,
                SelectionMode mode
        ) {
            this.startX = startX;
            this.startY = startY;
            this.currentX = currentX;
            this.currentY = currentY;
            this.mode = mode;
        }

        private ScreenRect screenRect() {
            return new ScreenRect(
                    (int) Math.round(Math.min(startX, currentX)),
                    (int) Math.round(Math.min(startY, currentY)),
                    (int) Math.round(Math.max(startX, currentX)),
                    (int) Math.round(Math.max(startY, currentY))
            );
        }

        private SelectionMode mode() {
            return mode;
        }
    }

    private static final class CameraFrameDrag {
        private final CanvasPoint startPoint;
        private CanvasPoint currentPoint;
        private final CameraOverlayProjection projection;

        private CameraFrameDrag(
                CanvasPoint startPoint,
                CanvasPoint currentPoint,
                CameraOverlayProjection projection
        ) {
            this.startPoint = startPoint;
            this.currentPoint = currentPoint;
            this.projection = projection;
        }

        public CameraOverlayProjection projection() {
            return projection;
        }
    }

    private record CanvasPoint(
            double x,
            double y
    ) {
    }

    private record ScreenPoint(
            double x,
            double y
    ) {
    }

        private record CaretPlacement(
            int x,
            int y
        ) {
        }

    private record ScreenRect(
            int left,
            int top,
            int right,
            int bottom
    ) {
    }

    private record CanvasBounds(
            double minX,
            double minY,
            double maxX,
            double maxY
    ) {
        public static CanvasBounds of(
                double aX,
                double aY,
                double bX,
                double bY
        ) {
            return new CanvasBounds(
                    Math.min(aX, bX),
                    Math.min(aY, bY),
                    Math.max(aX, bX),
                    Math.max(aY, bY)
            );
        }

        public double width() {
            return maxX - minX;
        }

        public double height() {
            return maxY - minY;
        }

        public boolean contains(CanvasPoint point) {
            return point.x() >= minX && point.x() <= maxX && point.y() >= minY && point.y() <= maxY;
        }

        public boolean intersects(CanvasBounds other) {
            return maxX >= other.minX && minX <= other.maxX && maxY >= other.minY && minY <= other.maxY;
        }

        public CanvasBounds expandToInclude(CanvasBounds other) {
            return new CanvasBounds(
                    Math.min(minX, other.minX),
                    Math.min(minY, other.minY),
                    Math.max(maxX, other.maxX),
                    Math.max(maxY, other.maxY)
            );
        }

        public CanvasBounds pad(double amount) {
            return new CanvasBounds(minX - amount, minY - amount, maxX + amount, maxY + amount);
        }
    }

    private record Rect(
            int left,
            int top,
            int width,
            int height
    ) {
        public int right() {
            return left + width;
        }

        public int bottom() {
            return top + height;
        }

        public boolean contains(
                double x,
                double y
        ) {
            return x >= left && x < right() && y >= top && y < bottom();
        }
    }

    private record CameraOverlayProjection(
            Rect mapBounds,
            CanvasBounds sceneBounds,
            double scale
    ) {
        public CanvasPoint screenToCanvas(
                double screenX,
                double screenY
        ) {
            return new CanvasPoint(
                    sceneBounds.minX() + (screenX - mapBounds.left()) / scale,
                    sceneBounds.minY() + (screenY - mapBounds.top()) / scale
            );
        }

        public ScreenPoint canvasToScreen(CanvasPoint point) {
            return new ScreenPoint(
                    mapBounds.left() + (point.x() - sceneBounds.minX()) * scale,
                    mapBounds.top() + (point.y() - sceneBounds.minY()) * scale
            );
        }
    }

    private record ChromeWidgetState(
            int x,
            int y,
            int width,
            int height,
            double scale,
            boolean hidden
    ) {
        private ChromeWidgetState(
                int x,
                int y,
                int width,
                int height,
                double scale
        ) {
            this(x, y, width, height, scale, false);
        }

        private ChromeWidgetState withPosition(
                int x,
                int y
        ) {
            return new ChromeWidgetState(x, y, width, height, scale, hidden);
        }

        private ChromeWidgetState withSize(
                int width,
                int height
        ) {
            return new ChromeWidgetState(x, y, width, height, scale, hidden);
        }

        private ChromeWidgetState withScale(double scale) {
            return new ChromeWidgetState(x, y, width, height, scale, hidden);
        }

        private ChromeWidgetState withHidden(boolean hidden) {
            return new ChromeWidgetState(x, y, width, height, scale, hidden);
        }
    }

    private enum SelectionMode {
        REPLACE,
        ADD,
        SUBTRACT
    }

    private enum SelectionHandle {
        NORTH_WEST(true, false, true, false),
        NORTH(false, false, true, false),
        NORTH_EAST(false, true, true, false),
        EAST(false, true, false, false),
        SOUTH_EAST(false, true, false, true),
        SOUTH(false, false, false, true),
        SOUTH_WEST(true, false, false, true),
        WEST(true, false, false, false);

        private static final SelectionHandle[] VALUES = values();

        private final boolean movesLeft;
        private final boolean movesRight;
        private final boolean movesTop;
        private final boolean movesBottom;

        SelectionHandle(
                boolean movesLeft,
                boolean movesRight,
                boolean movesTop,
                boolean movesBottom
        ) {
            this.movesLeft = movesLeft;
            this.movesRight = movesRight;
            this.movesTop = movesTop;
            this.movesBottom = movesBottom;
        }

        public boolean movesLeft() {
            return movesLeft;
        }

        public boolean movesRight() {
            return movesRight;
        }

        public boolean movesTop() {
            return movesTop;
        }

        public boolean movesBottom() {
            return movesBottom;
        }
    }

    private enum DrawLayer {
        ELEMENTS(1, true, IdeLocalizationKeys.IDE_DRAW_LAYER_ELEMENTS.getString(), 0xFF7FD7FF),
        CHROME(2, false, IdeLocalizationKeys.IDE_DRAW_LAYER_CHROME.getString(), 0xFFE8A652),
        SHELL(3, true, IdeLocalizationKeys.IDE_DRAW_LAYER_SHELL.getString(), 0xFFD998FF);

        private static final DrawLayer[] VALUES = values();

        private final int index;
        private final boolean canvasLayer;
        private final String label;
        private final int color;

        DrawLayer(
                int index,
            boolean canvasLayer,
                String label,
                int color
        ) {
            this.index = index;
            this.canvasLayer = canvasLayer;
            this.label = label;
            this.color = color;
        }

        public String label() {
            return label;
        }

        public Component labelComponent() {
            return Component.literal(label);
        }

        public int color() {
            return color;
        }

        public boolean canvasLayer() {
            return canvasLayer;
        }

        public int index() {
            return index;
        }
    }

    private enum ShellTextBinding {
        DIMENSION(-196.0D, -168.0D, "dimension: --"),
        PLAYER_X(-196.0D, -152.0D, "player.x: --"),
        PLAYER_Y(-196.0D, -136.0D, "player.y: --"),
        PLAYER_Z(-196.0D, -120.0D, "player.z: --"),
        LOOK_YAW(-196.0D, -104.0D, "look.yaw: --"),
        LOOK_PITCH(-196.0D, -88.0D, "look.pitch: --");

        private static final ShellTextBinding[] VALUES = values();

        private final double defaultX;
        private final double defaultY;
        private final String placeholderText;

        ShellTextBinding(
                double defaultX,
                double defaultY,
                String placeholderText
        ) {
            this.defaultX = defaultX;
            this.defaultY = defaultY;
            this.placeholderText = placeholderText;
        }

        public double defaultX() {
            return defaultX;
        }

        public double defaultY() {
            return defaultY;
        }

        public String placeholderText() {
            return placeholderText;
        }

        public String resolve(SfmDrawScreen screen) {
            if (screen.minecraft == null || screen.minecraft.player == null) {
                return placeholderText;
            }
            var player = screen.minecraft.player;
            return switch (this) {
                case DIMENSION -> "dimension: " + player.level.dimension().location();
                case PLAYER_X -> "player.x: " + screen.formatShellNumber(player.getX());
                case PLAYER_Y -> "player.y: " + screen.formatShellNumber(player.getY());
                case PLAYER_Z -> "player.z: " + screen.formatShellNumber(player.getZ());
                case LOOK_YAW -> "look.yaw: " + screen.formatShellNumber(player.getYRot());
                case LOOK_PITCH -> "look.pitch: " + screen.formatShellNumber(player.getXRot());
            };
        }
    }

    private record SelectionComponent(Set<Integer> elementIds, CanvasBounds bounds) {
    }

    private enum ChromeWidget {
        SCREEN_TITLE,
        SCREEN_SUBTITLE,
        ACTIVE_LAYER_LABEL,
        ACTIVE_LAYER_VALUE,
        CAMERA_POSITION_LABEL,
        CAMERA_POSITION_X,
        CAMERA_POSITION_SEPARATOR,
        CAMERA_POSITION_Y,
        ZOOM_LABEL,
        ZOOM_VALUE,
        CURSOR_LABEL,
        CURSOR_X,
        CURSOR_SEPARATOR,
        CURSOR_Y,
        HOTBAR_BAR,
        AUXILIARY_HOTBAR_BAR,
        HOTBAR_TITLE,
        HOTBAR_SUBTITLE,
        MINIMAP,
        LAYER_WINDOW;

        private static final ChromeWidget[] VALUES = values();

        public Component label() {
            return switch (this) {
                case SCREEN_TITLE -> Component.literal("Screen title");
                case SCREEN_SUBTITLE -> Component.literal("Screen subtitle");
                case ACTIVE_LAYER_LABEL -> Component.literal("Layer label");
                case ACTIVE_LAYER_VALUE -> Component.literal("Layer value");
                case CAMERA_POSITION_LABEL -> Component.literal("Camera label");
                case CAMERA_POSITION_X -> Component.literal("Camera x");
                case CAMERA_POSITION_SEPARATOR -> Component.literal("Camera separator");
                case CAMERA_POSITION_Y -> Component.literal("Camera y");
                case ZOOM_LABEL -> Component.literal("Zoom label");
                case ZOOM_VALUE -> Component.literal("Zoom value");
                case CURSOR_LABEL -> Component.literal("Cursor label");
                case CURSOR_X -> Component.literal("Cursor x");
                case CURSOR_SEPARATOR -> Component.literal("Cursor separator");
                case CURSOR_Y -> Component.literal("Cursor y");
                case HOTBAR_BAR -> IdeLocalizationKeys.IDE_DRAW_HOTBAR_TITLE.getComponent();
                case AUXILIARY_HOTBAR_BAR -> IdeLocalizationKeys.IDE_DRAW_HOTBAR_AUXILIARY_TITLE.getComponent();
                case HOTBAR_TITLE -> Component.literal("Tools title");
                case HOTBAR_SUBTITLE -> Component.literal("Tools subtitle");
                case MINIMAP -> IdeLocalizationKeys.IDE_DRAW_MINIMAP_TITLE.getComponent();
                case LAYER_WINDOW -> IdeLocalizationKeys.IDE_DRAW_LAYER_WINDOW_TITLE.getComponent();
            };
        }
    }

    private enum ChromeCursor {
        DEFAULT,
        HAND,
        MOVE,
        HORIZONTAL,
        VERTICAL,
        DIAGONAL_NORTH_WEST_SOUTH_EAST,
        DIAGONAL_NORTH_EAST_SOUTH_WEST
    }

    private enum DrawTool {
        CURSOR(GLFW.GLFW_KEY_V, IdeLocalizationKeys.IDE_DRAW_TOOL_CURSOR.getString(), "V", icon("cursor"), true),
        RECTANGLE(GLFW.GLFW_KEY_R, IdeLocalizationKeys.IDE_DRAW_TOOL_RECTANGLE.getString(), "R", icon("rectangle"), true),
        ARROW(GLFW.GLFW_KEY_A, IdeLocalizationKeys.IDE_DRAW_TOOL_ARROW.getString(), "A", icon("arrow"), true),
        TEXT(GLFW.GLFW_KEY_T, IdeLocalizationKeys.IDE_DRAW_TOOL_TEXT.getString(), "T", icon("text"), true),
        FREEHAND(GLFW.GLFW_KEY_F, IdeLocalizationKeys.IDE_DRAW_TOOL_FREEHAND.getString(), "F", icon("freehand"), true),
        HAND(GLFW.GLFW_KEY_H, IdeLocalizationKeys.IDE_DRAW_TOOL_HAND.getString(), "H", icon("hand"), true),
        CAMERA(GLFW.GLFW_KEY_C, IdeLocalizationKeys.IDE_DRAW_TOOL_CAMERA.getString(), "C", icon("camera"), true),
        // r[impl draw.tool.layer.shortcut.tab]
        LAYER(GLFW.GLFW_KEY_TAB, IdeLocalizationKeys.IDE_DRAW_TOOL_LAYER.getString(), "Tab", icon("cursor"), true),
        ZEN(GLFW.GLFW_KEY_Z, IdeLocalizationKeys.IDE_DRAW_TOOL_ZEN.getString(), "Z", icon("camera"), false),
        LOCK(GLFW.GLFW_KEY_L, IdeLocalizationKeys.IDE_DRAW_TOOL_LOCK.getString(), "L", icon("lock"), false);

        private static final DrawTool[] VALUES = values();

        private final int keyCode;
        private final String label;
        private final String shortcutGlyph;
        private final ResourceLocation iconLocation;
        private final boolean selectable;

        DrawTool(
                int keyCode,
                String label,
                String shortcutGlyph,
                ResourceLocation iconLocation,
                boolean selectable
        ) {
            this.keyCode = keyCode;
            this.label = label;
            this.shortcutGlyph = shortcutGlyph;
            this.iconLocation = iconLocation;
            this.selectable = selectable;
        }

        private static ResourceLocation icon(String name) {
            return new ResourceLocation(SFM.MOD_ID, "textures/gui/sfmdraw/tools/" + name + ".png");
        }

        public static @Nullable DrawTool byKeyCode(int keyCode) {
            for (DrawTool tool : VALUES) {
                if (tool.keyCode == keyCode) {
                    return tool;
                }
            }
            return null;
        }

        public String label() {
            return label;
        }

        public String shortcutGlyph() {
            return shortcutGlyph;
        }

        public ResourceLocation iconLocation() {
            return iconLocation;
        }

        public boolean selectable() {
            return selectable;
        }
    }
}

