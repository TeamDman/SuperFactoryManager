package ca.teamdman.sfm.client.screen;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.localization.IdeLocalizationKeys;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

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
    private static final int HOTBAR_SLOT_COUNT = 9;
    private static final int TOOL_COUNT = 7;
    private static final int MINIMAP_WIDTH = 150;
    private static final int MINIMAP_HEIGHT = 110;
    private static final int HANDLE_HALF_SIZE = 4;

    private final List<DrawElement> elements = new ArrayList<>();
    private final Set<Integer> selectedElementIds = new LinkedHashSet<>();

    private DrawTool activeTool = DrawTool.CURSOR;
    private int nextElementId = 1;

    private double cameraX = 0.0D;
    private double cameraY = 0.0D;
    private double zoom = 1.0D;
    private boolean cameraOverlayVisible = true;

    private int hotbarX = Integer.MIN_VALUE;
    private int hotbarY = Integer.MIN_VALUE;
    private boolean hotbarDragging = false;
    private int hotbarDragOffsetX = 0;
    private int hotbarDragOffsetY = 0;

    private boolean panning = false;
    private int panButton = -1;
    private double panAnchorMouseX = 0.0D;
    private double panAnchorMouseY = 0.0D;
    private double panAnchorCameraX = 0.0D;
    private double panAnchorCameraY = 0.0D;

    private boolean textToolCreatesBoundText = true;
    private int textEditingElementId = -1;

    private @Nullable DraftInteraction draftInteraction = null;
    private @Nullable MoveSelectionDrag moveSelectionDrag = null;
    private @Nullable ResizeSelectionDrag resizeSelectionDrag = null;
    private @Nullable MarqueeSelectionDrag marqueeSelectionDrag = null;

    private long lastCursorClickAtMs = 0L;
    private double lastCursorClickX = 0.0D;
    private double lastCursorClickY = 0.0D;

    public SfmDrawScreen() {
        super(IdeLocalizationKeys.IDE_DRAW_TITLE.getComponent());
        seedPrototypeElements();
    }

    @Override
    protected void init() {
        super.init();
        if (hotbarX == Integer.MIN_VALUE || hotbarY == Integer.MIN_VALUE) {
            hotbarX = (width - HOTBAR_WIDTH) / 2;
            hotbarY = Math.max(32, height - HOTBAR_HEIGHT - 30);
        }
        clampHotbarToScreen();
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

        if (textEditingElementId >= 0) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                textEditingElementId = -1;
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                mutateEditingTextBackspace();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                appendEditingText("\n");
                return true;
            }
            if (hasControlDown() && keyCode == GLFW.GLFW_KEY_V) {
                appendEditingText(minecraft != null ? minecraft.keyboardHandler.getClipboard() : "");
                return true;
            }
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_C) {
            cameraOverlayVisible = !cameraOverlayVisible;
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_T) {
            if (activeTool == DrawTool.TEXT) {
                textToolCreatesBoundText = !textToolCreatesBoundText;
            } else {
                activeTool = DrawTool.TEXT;
            }
            return true;
        }

        @Nullable DrawTool shortcutTool = DrawTool.byKeyCode(keyCode);
        if (shortcutTool != null && shortcutTool.selectable()) {
            activeTool = shortcutTool;
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

        Rect hotbarHeaderBounds = hotbarHeaderBounds();
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && hotbarHeaderBounds.contains(mouseX, mouseY)) {
            hotbarDragging = true;
            hotbarDragOffsetX = (int) Math.round(mouseX) - hotbarX;
            hotbarDragOffsetY = (int) Math.round(mouseY) - hotbarY;
            return true;
        }

        int hoveredToolIndex = hoveredToolIndex(mouseX, mouseY);
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && hoveredToolIndex >= 0) {
            handleHotbarToolClick(DrawTool.VALUES[hoveredToolIndex]);
            return true;
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE) {
            beginPan(mouseX, mouseY, button);
            return true;
        }

        Rect overlayBounds = cameraOverlayBounds();
        @Nullable CameraOverlayProjection overlayProjection = cameraOverlayProjection();
        @Nullable CameraOverlayProjection projectionUnderMouse = overlayProjection != null && overlayProjection.mapBounds().contains(mouseX, mouseY) ? overlayProjection : null;
        if (cameraOverlayVisible && overlayBounds.contains(mouseX, mouseY) && projectionUnderMouse == null) {
            return true;
        }

        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return super.mouseClicked(mouseX, mouseY, button);
        }

        if (activeTool == DrawTool.HAND) {
            beginPan(mouseX, mouseY, button);
            return true;
        }

        if (textEditingElementId >= 0) {
            return true;
        }

        CanvasPoint canvasPoint = screenToCanvas(mouseX, mouseY, projectionUnderMouse);

        if (activeTool == DrawTool.CURSOR) {
            boolean doubleClick = isCursorDoubleClick(mouseX, mouseY);
            if (doubleClick && handleCursorDoubleClick(canvasPoint)) {
                return true;
            }
            handleCursorPress(mouseX, mouseY, canvasPoint, projectionUnderMouse);
            return true;
        }

        if (activeTool == DrawTool.TEXT) {
            TextElement textElement = createTextElement(canvasPoint);
            if (textToolCreatesBoundText) {
                activeTool = DrawTool.TEXT;
                textEditingElementId = textElement.id();
            }
            return true;
        }

        draftInteraction = new DraftInteraction(activeTool, canvasPoint, canvasPoint, projectionUnderMouse);
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

        if (hotbarDragging && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            hotbarX = (int) Math.round(mouseX) - hotbarDragOffsetX;
            hotbarY = (int) Math.round(mouseY) - hotbarDragOffsetY;
            clampHotbarToScreen();
            return true;
        }

        if (panning && button == panButton) {
            cameraX = panAnchorCameraX - (mouseX - panAnchorMouseX) / zoom;
            cameraY = panAnchorCameraY - (mouseY - panAnchorMouseY) / zoom;
            return true;
        }

        if (moveSelectionDrag != null && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            CanvasPoint canvasPoint = screenToCanvas(mouseX, mouseY, moveSelectionDrag.projection());
            applyMoveSelectionDrag(moveSelectionDrag, canvasPoint);
            return true;
        }

        if (resizeSelectionDrag != null && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            CanvasPoint canvasPoint = screenToCanvas(mouseX, mouseY, resizeSelectionDrag.projection());
            applyResizeSelectionDrag(resizeSelectionDrag, canvasPoint);
            return true;
        }

        if (marqueeSelectionDrag != null && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            marqueeSelectionDrag.currentPoint = screenToCanvas(mouseX, mouseY, marqueeSelectionDrag.projection());
            return true;
        }

        if (draftInteraction != null && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            CanvasPoint canvasPoint = screenToCanvas(mouseX, mouseY, draftInteraction.projection());
            draftInteraction.currentPoint = canvasPoint;
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

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && hotbarDragging) {
            hotbarDragging = false;
            clampHotbarToScreen();
            return true;
        }

        if (panning && button == panButton) {
            panning = false;
            panButton = -1;
            return true;
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && draftInteraction != null) {
            draftInteraction.currentPoint = screenToCanvas(mouseX, mouseY, draftInteraction.projection());
            commitDraft();
            return true;
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && moveSelectionDrag != null) {
            moveSelectionDrag = null;
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

        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(
            double mouseX,
            double mouseY,
            double delta
    ) {

        if (!hasControlDown() || delta == 0.0D) {
            return super.mouseScrolled(mouseX, mouseY, delta);
        }

        @Nullable CameraOverlayProjection projection = cameraOverlayProjection();
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
        cameraX = focusPoint.x() - (mouseX - width / 2.0D) / zoom;
        cameraY = focusPoint.y() - (mouseY - height / 2.0D) / zoom;
        return true;
    }

    @Override
    public void render(
            PoseStack poseStack,
            int mouseX,
            int mouseY,
            float partialTick
    ) {

        renderBackground(poseStack);
        fill(poseStack, 0, 0, width, height, 0xFF111318);
        drawGrid(poseStack);
        drawOriginAxes(poseStack);
        drawElements(poseStack);
        drawDraft(poseStack);
        drawSelectionOverlay(poseStack);
        drawOverlayChrome(poseStack, mouseX, mouseY);
        super.render(poseStack, mouseX, mouseY, partialTick);
    }

    private void drawOverlayChrome(
            PoseStack poseStack,
            int mouseX,
            int mouseY
    ) {
        drawString(poseStack, font, IdeLocalizationKeys.IDE_DRAW_TITLE.getComponent(), 12, 12, 0xF0F3F7);
        drawString(poseStack, font, IdeLocalizationKeys.IDE_DRAW_SUBTITLE.getComponent(), 12, 24, 0x9AA3B2);
        drawString(poseStack, font, IdeLocalizationKeys.IDE_DRAW_HINTS.getComponent(), 12, 40, 0x7B8697);

        Component cameraLabel = Component.literal(String.format("cam %.0f, %.0f  zoom %.2fx", cameraX, cameraY, zoom));
        int cameraLabelWidth = font.width(cameraLabel);
        drawString(poseStack, font, cameraLabel, width - cameraLabelWidth - 12, 12, 0xC4CBD6);
        drawString(poseStack, font, IdeLocalizationKeys.IDE_DRAW_WORLD_LABEL.getComponent(), 12, height - 16, 0x707B8D);

        if (cameraOverlayVisible) {
            drawMinimap(poseStack);
        }
        drawToolHotbar(poseStack, mouseX, mouseY);
    }

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

    private void drawOriginAxes(PoseStack poseStack) {
        int axisColor = 0x4C8AC6FF;
        int originX = (int) Math.round(canvasToScreenX(0.0D));
        int originY = (int) Math.round(canvasToScreenY(0.0D));
        fill(poseStack, originX, 0, originX + 1, height, axisColor);
        fill(poseStack, 0, originY, width, originY + 1, axisColor);
        fill(poseStack, originX - 3, originY - 3, originX + 4, originY + 4, 0xAA8AC6FF);
    }

    private void drawElements(PoseStack poseStack) {
        for (DrawElement element : elements) {
            boolean selected = selectedElementIds.contains(element.id());
            if (element instanceof RectangleElement rectangleElement) {
                drawRectangleElement(poseStack, rectangleElement, selected);
            } else if (element instanceof ArrowElement arrowElement) {
                drawArrowElement(poseStack, arrowElement, selected);
            } else if (element instanceof TextElement textElement) {
                drawTextElement(poseStack, textElement, selected);
            } else if (element instanceof FreehandElement freehandElement) {
                drawFreehandElement(poseStack, freehandElement, selected);
            }
        }
    }

    private void drawDraft(PoseStack poseStack) {
        if (draftInteraction == null) {
            return;
        }

        switch (draftInteraction.tool()) {
            case RECTANGLE -> drawRectangleElement(
                    poseStack,
                    new RectangleElement(-1, draftInteraction.startPoint().x(), draftInteraction.startPoint().y(), draftInteraction.currentPoint().x(), draftInteraction.currentPoint().y(), 0x332FB5FF, 0xFF7FD7FF),
                    true
            );
            case ARROW -> drawArrowElement(
                    poseStack,
                    new ArrowElement(-1, draftInteraction.startPoint(), draftInteraction.currentPoint(), 0xFFE8A652),
                    true
            );
            case FREEHAND -> {
                if (draftInteraction.points().size() >= 2) {
                    drawFreehandElement(
                            poseStack,
                            new FreehandElement(-1, List.copyOf(draftInteraction.points()), 0xFF88D498),
                            true
                    );
                }
            }
            default -> {
            }
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

        if (activeTool == DrawTool.CURSOR) {
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

        drawMarqueeOverlay(poseStack);
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
            boolean selected
    ) {
        ScreenRect screenBounds = canvasBoundsToScreenRect(CanvasBounds.of(element.x1, element.y1, element.x2, element.y2));
        fill(poseStack, screenBounds.left(), screenBounds.top(), screenBounds.right(), screenBounds.bottom(), element.fillColor);
        int strokeColor = selected ? 0xFFF6E27F : element.strokeColor;
        drawScreenRectOutline(poseStack, screenBounds, strokeColor);
    }

    private void drawArrowElement(
            PoseStack poseStack,
            ArrowElement element,
            boolean selected
    ) {
        ScreenPoint start = canvasToScreen(element.start);
        ScreenPoint end = canvasToScreen(element.end);
        int color = selected ? 0xFFF6E27F : element.color;
        drawLineStrip(poseStack, List.of(start, end), color);

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
        drawLineStrip(poseStack, List.of(end, headA), color);
        drawLineStrip(poseStack, List.of(end, headB), color);
    }

    private void drawTextElement(
            PoseStack poseStack,
            TextElement element,
            boolean selected
    ) {
        ScreenPoint point = canvasToScreen(new CanvasPoint(element.x, element.y));
        Component label = Component.literal(element.text);
        int textWidth = font.width(label);
        int left = (int) Math.round(point.x()) - 3;
        int top = (int) Math.round(point.y()) - 2;
        int right = left + textWidth + 6;
        int bottom = top + font.lineHeight + 4;
        if (selected) {
            fill(poseStack, left, top, right, bottom, 0x33F6E27F);
        }
        drawString(poseStack, font, label, (int) Math.round(point.x()), (int) Math.round(point.y()), element.color);

        if (element.id() == textEditingElementId && (Util.getMillis() / 400L) % 2L == 0L) {
            int caretX = (int) Math.round(point.x()) + textWidth + 1;
            fill(poseStack, caretX, (int) Math.round(point.y()) - 1, caretX + 1, (int) Math.round(point.y()) + font.lineHeight + 1, 0xFFF1F5FB);
        }
    }

    private void drawFreehandElement(
            PoseStack poseStack,
            FreehandElement element,
            boolean selected
    ) {
        if (element.points.size() < 2) {
            return;
        }
        List<ScreenPoint> screenPoints = new ArrayList<>(element.points.size());
        for (CanvasPoint point : element.points) {
            screenPoints.add(canvasToScreen(point));
        }
        drawLineStrip(poseStack, screenPoints, selected ? 0xFFF6E27F : element.color);
    }

    private void drawLineStrip(
            PoseStack poseStack,
            List<ScreenPoint> points,
            int color
    ) {
        if (points.size() < 2) {
            return;
        }

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
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
        RenderSystem.disableBlend();
    }

    private void drawMinimap(PoseStack poseStack) {
        Rect overlayBounds = cameraOverlayBounds();
        fill(poseStack, overlayBounds.left(), overlayBounds.top(), overlayBounds.right(), overlayBounds.bottom(), 0xCC10141A);
        drawScreenRectOutline(poseStack, new ScreenRect(overlayBounds.left(), overlayBounds.top(), overlayBounds.right(), overlayBounds.bottom()), 0xFF38404B);
        drawString(poseStack, font, IdeLocalizationKeys.IDE_DRAW_MINIMAP_TITLE.getComponent(), overlayBounds.left() + 6, overlayBounds.top() + 6, 0xD5D9E0);
        drawString(poseStack, font, IdeLocalizationKeys.IDE_DRAW_SCREEN_LABEL.getComponent(), overlayBounds.left() + 6, overlayBounds.top() + 18, 0x7D8794);

        @Nullable CameraOverlayProjection projection = cameraOverlayProjection();
        if (projection == null) {
            return;
        }

        Rect mapBounds = projection.mapBounds();
        fill(poseStack, mapBounds.left(), mapBounds.top(), mapBounds.right(), mapBounds.bottom(), 0x55182229);

        for (DrawElement element : elements) {
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
            fill(poseStack, elementLeft, elementTop, elementRight, elementBottom, selectedElementIds.contains(element.id()) ? 0xAAF6E27F : 0x8877C8FF);
        }

        CanvasBounds visibleBounds = visibleCanvasBounds();
        int viewportLeft = mapBounds.left() + (int) Math.round((visibleBounds.minX() - projection.sceneBounds().minX()) * projection.scale());
        int viewportRight = mapBounds.left() + (int) Math.round((visibleBounds.maxX() - projection.sceneBounds().minX()) * projection.scale());
        int viewportTop = mapBounds.top() + (int) Math.round((visibleBounds.minY() - projection.sceneBounds().minY()) * projection.scale());
        int viewportBottom = mapBounds.top() + (int) Math.round((visibleBounds.maxY() - projection.sceneBounds().minY()) * projection.scale());
        drawScreenRectOutline(poseStack, new ScreenRect(viewportLeft, viewportTop, viewportRight, viewportBottom), 0xFFE8A652);

        int originX = mapBounds.left() + (int) Math.round((0.0D - projection.sceneBounds().minX()) * projection.scale());
        int originY = mapBounds.top() + (int) Math.round((0.0D - projection.sceneBounds().minY()) * projection.scale());
        fill(poseStack, originX - 1, mapBounds.top(), originX + 1, mapBounds.bottom(), 0x558AC6FF);
        fill(poseStack, mapBounds.left(), originY - 1, mapBounds.right(), originY + 1, 0x558AC6FF);

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
            case ARROW -> drawLineStrip(
                    poseStack,
                    List.of(projection.canvasToScreen(interaction.startPoint()), projection.canvasToScreen(interaction.currentPoint())),
                    0xFFE8A652
            );
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

    private void drawToolHotbar(
            PoseStack poseStack,
            int mouseX,
            int mouseY
    ) {
        Rect headerBounds = hotbarHeaderBounds();
        fill(poseStack, headerBounds.left(), headerBounds.top(), headerBounds.right(), headerBounds.bottom(), 0xCC11161E);
        drawString(poseStack, font, IdeLocalizationKeys.IDE_DRAW_HOTBAR_TITLE.getComponent(), hotbarX + 6, hotbarY - HOTBAR_HEADER_HEIGHT + 2, 0xE4E8EF);

        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, AbstractWidget.WIDGETS_LOCATION);
        blit(poseStack, hotbarX, hotbarY, 0, 0, HOTBAR_WIDTH, HOTBAR_HEIGHT);

        if (activeTool.selectable()) {
            blit(poseStack, hotbarX - 1 + activeTool.ordinal() * 20, hotbarY - 1, 0, 22, 24, HOTBAR_HEIGHT);
        }
        if (cameraOverlayVisible) {
            Rect cameraSlotBounds = hotbarSlotBounds(DrawTool.CAMERA.ordinal());
            drawScreenRectOutline(
                    poseStack,
                    new ScreenRect(cameraSlotBounds.left() - 2, cameraSlotBounds.top() - 2, cameraSlotBounds.right() + 2, cameraSlotBounds.bottom() + 2),
                    0xFFE8A652
            );
        }

        for (int index = 0; index < TOOL_COUNT; index++) {
            DrawTool tool = DrawTool.VALUES[index];
            Rect slotBounds = hotbarSlotBounds(index);
            boolean hovered = slotBounds.contains(mouseX, mouseY);
            if (hovered) {
                fill(poseStack, slotBounds.left(), slotBounds.top(), slotBounds.right(), slotBounds.bottom(), 0x2238A3FF);
            }
            drawToolIcon(poseStack, tool, slotBounds.left(), slotBounds.top());
        }

        int hoveredToolIndex = hoveredToolIndex(mouseX, mouseY);
        DrawTool describedTool = hoveredToolIndex >= 0 ? DrawTool.VALUES[hoveredToolIndex] : activeTool;
        Component label = Component.literal(describeTool(describedTool));
        int labelWidth = font.width(label);
        fill(poseStack, hotbarX + HOTBAR_WIDTH / 2 - labelWidth / 2 - 4, hotbarY + HOTBAR_HEIGHT + 4, hotbarX + HOTBAR_WIDTH / 2 + labelWidth / 2 + 4, hotbarY + HOTBAR_HEIGHT + 16, 0xCC10141A);
        drawString(poseStack, font, label, hotbarX + HOTBAR_WIDTH / 2 - labelWidth / 2, hotbarY + HOTBAR_HEIGHT + 6, 0xDCE2EC);
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
        if (tool == DrawTool.TEXT) {
            return tool.label() + " [t] " + (textToolCreatesBoundText ? "bound" : "unbound");
        }
        if (tool == DrawTool.CAMERA) {
            return tool.label() + " [c] " + (cameraOverlayVisible ? "on" : "off");
        }
        return tool.label() + " [" + tool.shortcutGlyph().toLowerCase() + "]";
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
        if (tool == DrawTool.CAMERA) {
            cameraOverlayVisible = !cameraOverlayVisible;
            return;
        }
        if (tool == DrawTool.TEXT && activeTool == DrawTool.TEXT) {
            textToolCreatesBoundText = !textToolCreatesBoundText;
            return;
        }
        if (tool.selectable()) {
            activeTool = tool;
        }
    }

    private boolean handleCursorDoubleClick(CanvasPoint canvasPoint) {
        int hitElementId = findTopElementAt(canvasPoint);
        if (hitElementId >= 0) {
            DrawElement element = findElementById(hitElementId);
            if (element instanceof TextElement) {
                selectOnly(hitElementId);
                activeTool = DrawTool.TEXT;
                textEditingElementId = hitElementId;
                return true;
            }
            return false;
        }

        TextElement textElement = createTextElement(canvasPoint);
        activeTool = DrawTool.TEXT;
        textEditingElementId = textElement.id();
        return true;
    }

    private void handleCursorPress(
            double mouseX,
            double mouseY,
            CanvasPoint canvasPoint,
            @Nullable CameraOverlayProjection projection
    ) {
        CanvasBounds selectionBounds = currentSelectionBounds();
        if (projection == null && selectionBounds != null) {
            SelectionHandle handle = findSelectionHandle(mouseX, mouseY, selectionBounds);
            if (handle != null) {
                resizeSelectionDrag = new ResizeSelectionDrag(selectionSnapshot(), selectionBounds, handle, projection);
                return;
            }
        }

        if (selectionBounds != null && selectionBounds.contains(canvasPoint)) {
            moveSelectionDrag = new MoveSelectionDrag(selectionSnapshot(), canvasPoint, projection);
            return;
        }

        int hitElementId = findTopElementAt(canvasPoint);
        if (hitElementId >= 0) {
            if (!selectedElementIds.contains(hitElementId)) {
                selectOnly(hitElementId);
            }
            moveSelectionDrag = new MoveSelectionDrag(selectionSnapshot(), canvasPoint, projection);
            return;
        }

        marqueeSelectionDrag = new MarqueeSelectionDrag(canvasPoint, canvasPoint, projection);
    }

    private void applyMoveSelectionDrag(
            MoveSelectionDrag drag,
            CanvasPoint currentPoint
    ) {
        double dx = currentPoint.x() - drag.startPoint().x();
        double dy = currentPoint.y() - drag.startPoint().y();
        for (ElementSnapshot snapshot : drag.snapshots()) {
            DrawElement element = findElementById(snapshot.id());
            if (element == null) {
                continue;
            }
            element.copyFrom(snapshot.elementCopy());
            element.translate(dx, dy);
        }
    }

    private void applyResizeSelectionDrag(
            ResizeSelectionDrag drag,
            CanvasPoint currentPoint
    ) {
        CanvasBounds newBounds = resizedBounds(drag.originalBounds(), drag.handle(), currentPoint, 8.0D / Math.max(zoom, 0.01D));
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

        CanvasBounds marqueeBounds = CanvasBounds.of(
                marqueeSelectionDrag.startPoint.x(),
                marqueeSelectionDrag.startPoint.y(),
                marqueeSelectionDrag.currentPoint.x(),
                marqueeSelectionDrag.currentPoint.y()
        );
        marqueeSelectionDrag = null;

        if (marqueeBounds.width() < 1.0D && marqueeBounds.height() < 1.0D) {
            selectedElementIds.clear();
            return;
        }

        selectedElementIds.clear();
        for (DrawElement element : elements) {
            if (element.bounds(this).intersects(marqueeBounds)) {
                selectedElementIds.add(element.id());
            }
        }
    }

    private void commitDraft() {
        if (draftInteraction == null) {
            return;
        }

        switch (draftInteraction.tool()) {
            case RECTANGLE -> {
                if (distanceSquared(draftInteraction.startPoint(), draftInteraction.currentPoint()) > 4.0D) {
                    RectangleElement element = new RectangleElement(nextElementId++, draftInteraction.startPoint().x(), draftInteraction.startPoint().y(), draftInteraction.currentPoint().x(), draftInteraction.currentPoint().y(), 0x332FB5FF, 0xFF7FD7FF);
                    elements.add(element);
                    selectOnly(element.id());
                }
            }
            case ARROW -> {
                if (distanceSquared(draftInteraction.startPoint(), draftInteraction.currentPoint()) > 4.0D) {
                    ArrowElement element = new ArrowElement(nextElementId++, draftInteraction.startPoint(), draftInteraction.currentPoint(), 0xFFE8A652);
                    elements.add(element);
                    selectOnly(element.id());
                }
            }
            case FREEHAND -> {
                if (draftInteraction.points().size() >= 2) {
                    FreehandElement element = new FreehandElement(nextElementId++, List.copyOf(draftInteraction.points()), 0xFF88D498);
                    elements.add(element);
                    selectOnly(element.id());
                }
            }
            default -> {
            }
        }
        draftInteraction = null;
    }

    private TextElement createTextElement(CanvasPoint point) {
        TextElement element = new TextElement(nextElementId++, point.x(), point.y(), "", 0xFFF1F5FB);
        elements.add(element);
        selectOnly(element.id());
        return element;
    }

    private void appendEditingText(String value) {
        if (value == null || value.isEmpty()) {
            return;
        }
        DrawElement element = findElementById(textEditingElementId);
        if (element instanceof TextElement textElement) {
            textElement.text = textElement.text + value;
        }
    }

    private void mutateEditingTextBackspace() {
        DrawElement element = findElementById(textEditingElementId);
        if (element instanceof TextElement textElement && !textElement.text.isEmpty()) {
            textElement.text = textElement.text.substring(0, textElement.text.length() - 1);
        }
    }

    private int findTopElementAt(CanvasPoint point) {
        for (int index = elements.size() - 1; index >= 0; index--) {
            DrawElement element = elements.get(index);
            if (elementContains(element, point)) {
                return element.id();
            }
        }
        return -1;
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
            return distancePointToSegment(point, arrowElement.start, arrowElement.end) <= 8.0D / zoom;
        }
        if (element instanceof TextElement textElement) {
            CanvasBounds bounds = textElement.bounds(this);
            return bounds.contains(point);
        }
        if (element instanceof FreehandElement freehandElement) {
            List<CanvasPoint> points = freehandElement.points;
            for (int i = 1; i < points.size(); i++) {
                if (distancePointToSegment(point, points.get(i - 1), points.get(i)) <= 8.0D / zoom) {
                    return true;
                }
            }
        }
        return false;
    }

    private @Nullable DrawElement findElementById(int id) {
        for (DrawElement element : elements) {
            if (element.id() == id) {
                return element;
            }
        }
        return null;
    }

    private void selectOnly(int id) {
        selectedElementIds.clear();
        selectedElementIds.add(id);
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
        for (int index = 0; index < TOOL_COUNT; index++) {
            if (hotbarSlotBounds(index).contains(mouseX, mouseY)) {
                return index;
            }
        }
        return -1;
    }

    private Rect hotbarHeaderBounds() {
        return new Rect(hotbarX, hotbarY - HOTBAR_HEADER_HEIGHT, HOTBAR_WIDTH, HOTBAR_HEADER_HEIGHT);
    }

    private Rect hotbarSlotBounds(int slotIndex) {
        return new Rect(hotbarX + slotIndex * 20 + 3, hotbarY + 3, 16, 16);
    }

    private void clampHotbarToScreen() {
        hotbarX = Mth.clamp(hotbarX, 8, Math.max(8, width - HOTBAR_WIDTH - 8));
        hotbarY = Mth.clamp(hotbarY, HOTBAR_HEADER_HEIGHT + 8, Math.max(HOTBAR_HEADER_HEIGHT + 8, height - HOTBAR_HEIGHT - 24));
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
                cameraX + (screenX - width / 2.0D) / zoom,
                cameraY + (screenY - height / 2.0D) / zoom
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
        return (canvasX - cameraX) * zoom + width / 2.0D;
    }

    private double canvasToScreenY(double canvasY) {
        return (canvasY - cameraY) * zoom + height / 2.0D;
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
        return new Rect(width - MINIMAP_WIDTH - 12, 30, MINIMAP_WIDTH, MINIMAP_HEIGHT);
    }

    private @Nullable CameraOverlayProjection cameraOverlayProjection() {
        if (!cameraOverlayVisible) {
            return null;
        }
        Rect overlayBounds = cameraOverlayBounds();
        Rect mapBounds = new Rect(overlayBounds.left() + 10, overlayBounds.top() + 28, MINIMAP_WIDTH - 20, MINIMAP_HEIGHT - 38);
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
            double minimumSize
    ) {
        double left = originalBounds.minX();
        double top = originalBounds.minY();
        double right = originalBounds.maxX();
        double bottom = originalBounds.maxY();

        if (handle.movesLeft()) {
            left = Math.min(currentPoint.x(), right - minimumSize);
        }
        if (handle.movesRight()) {
            right = Math.max(currentPoint.x(), left + minimumSize);
        }
        if (handle.movesTop()) {
            top = Math.min(currentPoint.y(), bottom - minimumSize);
        }
        if (handle.movesBottom()) {
            bottom = Math.max(currentPoint.y(), top + minimumSize);
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

    private void seedPrototypeElements() {
        RectangleElement rectangle = new RectangleElement(nextElementId++, -180.0D, -80.0D, 40.0D, 60.0D, 0x334D7CFE, 0xFF7FD7FF);
        ArrowElement arrow = new ArrowElement(nextElementId++, new CanvasPoint(60.0D, -40.0D), new CanvasPoint(210.0D, 80.0D), 0xFFE8A652);
        TextElement text = new TextElement(nextElementId++, -20.0D, -130.0D, "Document origin", 0xFFF1F5FB);
        elements.add(rectangle);
        elements.add(arrow);
        elements.add(text);
        selectOnly(rectangle.id());
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

        protected DrawElement(int id) {
            this.id = id;
        }

        public int id() {
            return id;
        }

        public abstract CanvasBounds bounds(SfmDrawScreen screen);

        public abstract DrawElement copy();

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
                double x1,
                double y1,
                double x2,
                double y2,
                int fillColor,
                int strokeColor
        ) {
            super(id);
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
            return new RectangleElement(id(), x1, y1, x2, y2, fillColor, strokeColor);
        }

        @Override
        public void copyFrom(DrawElement other) {
            RectangleElement element = (RectangleElement) other;
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
        private CanvasPoint start;
        private CanvasPoint end;
        private final int color;

        private ArrowElement(
                int id,
                CanvasPoint start,
                CanvasPoint end,
                int color
        ) {
            super(id);
            this.start = start;
            this.end = end;
            this.color = color;
        }

        @Override
        public CanvasBounds bounds(SfmDrawScreen screen) {
            return CanvasBounds.of(start.x(), start.y(), end.x(), end.y()).pad(8.0D);
        }

        @Override
        public DrawElement copy() {
            return new ArrowElement(id(), start, end, color);
        }

        @Override
        public void copyFrom(DrawElement other) {
            ArrowElement element = (ArrowElement) other;
            start = element.start;
            end = element.end;
        }

        @Override
        public void translate(double dx, double dy) {
            start = new CanvasPoint(start.x() + dx, start.y() + dy);
            end = new CanvasPoint(end.x() + dx, end.y() + dy);
        }

        @Override
        public void transform(CanvasBounds fromBounds, CanvasBounds toBounds, SfmDrawScreen screen) {
            start = screen.transformPoint(start, fromBounds, toBounds);
            end = screen.transformPoint(end, fromBounds, toBounds);
        }
    }

    private static final class TextElement extends DrawElement {
        private double x;
        private double y;
        private String text;
        private final int color;

        private TextElement(
                int id,
                double x,
                double y,
                String text,
                int color
        ) {
            super(id);
            this.x = x;
            this.y = y;
            this.text = text;
            this.color = color;
        }

        @Override
        public CanvasBounds bounds(SfmDrawScreen screen) {
            double width = Math.max(16.0D, screen.font.width(text.isEmpty() ? " " : text)) / Math.max(screen.zoom, 0.01D);
            double height = screen.font.lineHeight / Math.max(screen.zoom, 0.01D);
            return CanvasBounds.of(x, y, x + width, y + height);
        }

        @Override
        public DrawElement copy() {
            return new TextElement(id(), x, y, text, color);
        }

        @Override
        public void copyFrom(DrawElement other) {
            TextElement element = (TextElement) other;
            x = element.x;
            y = element.y;
            text = element.text;
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
        }
    }

    private static final class FreehandElement extends DrawElement {
        private List<CanvasPoint> points;
        private final int color;

        private FreehandElement(
                int id,
                List<CanvasPoint> points,
                int color
        ) {
            super(id);
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
            return new FreehandElement(id(), points, color);
        }

        @Override
        public void copyFrom(DrawElement other) {
            FreehandElement element = (FreehandElement) other;
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
            CanvasPoint startPoint,
            @Nullable CameraOverlayProjection projection
    ) {
    }

    private record ResizeSelectionDrag(
            List<ElementSnapshot> snapshots,
            CanvasBounds originalBounds,
            SelectionHandle handle,
            @Nullable CameraOverlayProjection projection
    ) {
    }

    private static final class MarqueeSelectionDrag {
        private final CanvasPoint startPoint;
        private CanvasPoint currentPoint;
        private final @Nullable CameraOverlayProjection projection;

        private MarqueeSelectionDrag(
                CanvasPoint startPoint,
                CanvasPoint currentPoint,
                @Nullable CameraOverlayProjection projection
        ) {
            this.startPoint = startPoint;
            this.currentPoint = currentPoint;
            this.projection = projection;
        }

        public @Nullable CameraOverlayProjection projection() {
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

    private enum DrawTool {
        CURSOR(GLFW.GLFW_KEY_V, IdeLocalizationKeys.IDE_DRAW_TOOL_CURSOR.getString(), "V", icon("cursor"), true),
        RECTANGLE(GLFW.GLFW_KEY_R, IdeLocalizationKeys.IDE_DRAW_TOOL_RECTANGLE.getString(), "R", icon("rectangle"), true),
        ARROW(GLFW.GLFW_KEY_A, IdeLocalizationKeys.IDE_DRAW_TOOL_ARROW.getString(), "A", icon("arrow"), true),
        TEXT(GLFW.GLFW_KEY_T, IdeLocalizationKeys.IDE_DRAW_TOOL_TEXT.getString(), "T", icon("text"), true),
        FREEHAND(GLFW.GLFW_KEY_F, IdeLocalizationKeys.IDE_DRAW_TOOL_FREEHAND.getString(), "F", icon("freehand"), true),
        HAND(GLFW.GLFW_KEY_H, IdeLocalizationKeys.IDE_DRAW_TOOL_HAND.getString(), "H", icon("hand"), true),
        CAMERA(GLFW.GLFW_KEY_C, IdeLocalizationKeys.IDE_DRAW_TOOL_CAMERA.getString(), "C", icon("camera"), false);

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
