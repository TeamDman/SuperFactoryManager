package ca.teamdman.sfm.client.screen;

import ca.teamdman.sfm.client.ide.action.IdeActionDefinition;
import ca.teamdman.sfm.client.ide.layout.IdeArea;
import ca.teamdman.sfm.client.ide.layout.IdeDockDirection;
import ca.teamdman.sfm.client.ide.layout.IdeDockLayout;
import ca.teamdman.sfm.client.ide.layout.IdeDockPiece;
import ca.teamdman.sfm.client.ide.session.IdeSession;
import ca.teamdman.sfm.client.ide.session.IdeSessionCapture;
import ca.teamdman.sfm.client.ide.session.IdeSessionTarget;
import ca.teamdman.sfm.client.registry.SFMIdePlaygroundActions;
import ca.teamdman.sfm.client.registry.SFMKeyMappings;
import ca.teamdman.sfm.common.localization.IdeLocalizationKeys;
import ca.teamdman.sfm.common.util.SFMResourceLocation;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnknownNullability;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class IdePlaygroundScreen extends Screen {
    private static final int MARGIN = 16;

    private static final int MIN_CONTENT_WIDTH = 240;

    private static final int MIN_CONTENT_HEIGHT = 180;

    private static final int MIN_SIDE_PANEL_WIDTH = 96;

    private static final int MIN_BOTTOM_PANEL_HEIGHT = 64;

    private static final int PANEL_TEXT_PADDING = 6;

    private static final int TERMINAL_INPUT_HEIGHT = 16;

    private static final int TERMINAL_INPUT_MARGIN = 6;

    private static final int MAX_TERMINAL_MESSAGES = 8;

    private static final int COMMAND_PALETTE_INPUT_HEIGHT = 16;

    private static final int COMMAND_PALETTE_ROW_HEIGHT = 22;

    private static final int COMMAND_PALETTE_MAX_RESULTS = 7;

    private static final int COMMAND_PALETTE_MOVE_STEP = 12;

    private static final int EDGE_HIT_MARGIN = 6;

    private static final int COMMAND_PALETTE_Z_OFFSET = 350;

    private static final float IDE_GLOBAL_SCALE_STEP = 0.1F;

    private static final float PANEL_SCALE_STEP = 0.1F;

    private static final float MIN_NONZERO_SCALE = 0.05F;

    private static final String COMMAND_PALETTE_SHORTCUT = "Ctrl+Shift+P";

    private final IdeSession session = new IdeSession();

    private final List<Component> terminalMessages = new ArrayList<>();

    private final List<String> terminalHistory = new ArrayList<>();

    private final List<IdeActionDefinition> recentActions = new ArrayList<>();

    private int shellPanelWidth = 160;

    private int layoutPanelWidth = 180;

    private int terminalPanelHeight = 96;

    private PlaygroundPanel focusedPanel = PlaygroundPanel.WORKSPACE;

    private Map<PlaygroundPanel, IdeArea> currentLayout = Map.of();

    private @UnknownNullability EditBox terminalInput;

    private @UnknownNullability EditBox commandPaletteInput;

    private int terminalHistoryIndex;

    private String terminalHistoryDraft = "";

    private boolean commandPaletteVisible;

    private @Nullable PanelEdge focusedEdge;

    private List<IdeActionDefinition> filteredPaletteActions = List.of();

    private int selectedPaletteActionIndex;

    private int commandPaletteX = Integer.MIN_VALUE;

    private int commandPaletteY = Integer.MIN_VALUE;

    private boolean commandPaletteDragging;

    private @Nullable DragTransaction activeDragTransaction;

    private int commandPaletteDragOffsetX;

    private int commandPaletteDragOffsetY;

    private float ideGlobalScale = 1.0F;

    private int contentMarginLeft = MARGIN;

    private int contentMarginTop = MARGIN;

    private int contentMarginRight = MARGIN;

    private int contentMarginBottom = MARGIN;

    private final EnumMap<PlaygroundPanel, PanelInstanceState> panelInstances = new EnumMap<>(PlaygroundPanel.class);

    public IdePlaygroundScreen() {

        super(IdeLocalizationKeys.IDE_PLAYGROUND_TITLE.getComponent());
        panelInstances.put(PlaygroundPanel.SHELL, new PanelInstanceState(PlaygroundPanel.SHELL, true, IdeDockDirection.LEFT));
        panelInstances.put(PlaygroundPanel.WORKSPACE, new PanelInstanceState(PlaygroundPanel.WORKSPACE, true, IdeDockDirection.CENTER));
        panelInstances.put(PlaygroundPanel.LAYOUT, new PanelInstanceState(PlaygroundPanel.LAYOUT, true, IdeDockDirection.RIGHT));
        panelInstances.put(PlaygroundPanel.TERMINAL, new PanelInstanceState(PlaygroundPanel.TERMINAL, true, IdeDockDirection.DOWN));
    }

    @Override
    public boolean keyPressed(
            int keyCode,
            int scanCode,
            int modifiers
    ) {

        InputConstants.Key key = InputConstants.getKey(keyCode, scanCode);
        if (SFMKeyMappings.IDE_OPEN_PLAYGROUND_KEY.get().isActiveAndMatches(key)) {
            this.onClose();
            return true;
        }
        if (SFMKeyMappings.IDE_TOGGLE_LEFT_PANEL_KEY.get().isActiveAndMatches(key)) {
            dispatchAction(SFMIdePlaygroundActions.TOGGLE_SHELL_PANEL.get());
            return true;
        }
        if (SFMKeyMappings.IDE_TOGGLE_RIGHT_PANEL_KEY.get().isActiveAndMatches(key)) {
            dispatchAction(SFMIdePlaygroundActions.TOGGLE_LAYOUT_PANEL.get());
            return true;
        }
        if (SFMKeyMappings.IDE_TOGGLE_BOTTOM_PANEL_KEY.get().isActiveAndMatches(key)) {
            dispatchAction(SFMIdePlaygroundActions.TOGGLE_TERMINAL_PANEL.get());
            return true;
        }
        if (isCommandPaletteShortcut(keyCode)) {
            toggleCommandPalette();
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_ESCAPE && activeDragTransaction != null) {
            cancelActiveDragTransaction();
            return true;
        }

        if (handleScaleKey(keyCode)) {
            return true;
        }

        if (commandPaletteVisible && handleCommandPaletteKeyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }

        if (handleResizeKey(keyCode)) {
            return true;
        }
        if (handleFocusNavigationKey(keyCode)) {
            return true;
        }

        if (isPanelVisible(PlaygroundPanel.TERMINAL) && terminalInput != null && terminalInput.visible && terminalInput.isFocused()) {
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                submitTerminalCommand();
                return true;
            }
            if (!hasAltDown() && !hasShiftDown()) {
                if (keyCode == GLFW.GLFW_KEY_UP && recallTerminalHistory(-1)) {
                    return true;
                }
                if (keyCode == GLFW.GLFW_KEY_DOWN && recallTerminalHistory(1)) {
                    return true;
                }
            }
            if (terminalInput.keyPressed(keyCode, scanCode, modifiers)) {
                return true;
            }
        }

        if (keyCode == GLFW.GLFW_KEY_TAB) {
            cycleFocus(hasShiftDown());
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void render(
            PoseStack poseStack,
            int mouseX,
            int mouseY,
            float partialTick
    ) {

        renderBackground(poseStack);
        int ideMouseX = ideCoordinate(mouseX);
        int ideMouseY = ideCoordinate(mouseY);

        poseStack.pushPose();
        poseStack.scale(ideGlobalScale, ideGlobalScale, 1.0F);
        clampPanelSizes();
        updateCommandPaletteBounds();
        IdeSessionCapture.capture(session, Minecraft.getInstance(), focusedPanel.display().getString());

        Map<PlaygroundPanel, IdeArea> layout = calculateLayout();
        currentLayout = layout;
        ensureFocusedPanelVisible();

        if (isPanelVisible(PlaygroundPanel.SHELL)) {
            drawPanel(
                    poseStack,
                    PlaygroundPanel.SHELL,
                    layout.get(PlaygroundPanel.SHELL),
                    IdeLocalizationKeys.IDE_PLAYGROUND_PANEL_SHELL.getComponent(),
                    focusedPanel == PlaygroundPanel.SHELL,
                    buildShellLines()
            );
        }
        drawWorkspacePanel(
                poseStack,
                layout.get(PlaygroundPanel.WORKSPACE),
                focusedPanel == PlaygroundPanel.WORKSPACE,
                layout
        );
        if (isPanelVisible(PlaygroundPanel.LAYOUT)) {
            drawPanel(
                    poseStack,
                    PlaygroundPanel.LAYOUT,
                    layout.get(PlaygroundPanel.LAYOUT),
                    IdeLocalizationKeys.IDE_PLAYGROUND_PANEL_LAYOUT.getComponent(),
                    focusedPanel == PlaygroundPanel.LAYOUT,
                    buildLayoutLines()
            );
        }
        drawTerminalPanel(poseStack, layout.get(PlaygroundPanel.TERMINAL), focusedPanel == PlaygroundPanel.TERMINAL);

        boolean restoreCommandPaletteVisibility = false;
        if (commandPaletteVisible && commandPaletteInput != null) {
            restoreCommandPaletteVisibility = commandPaletteInput.visible;
            commandPaletteInput.visible = false;
        }

        super.render(poseStack, ideMouseX, ideMouseY, partialTick);

        if (commandPaletteVisible && commandPaletteInput != null) {
            commandPaletteInput.visible = restoreCommandPaletteVisibility;
            drawCommandPaletteOverlay(poseStack, ideMouseX, ideMouseY, partialTick);
        }
        drawPanelDragOverlay(poseStack);
        poseStack.popPose();
    }

    @Override
    public boolean isPauseScreen() {

        return false;
    }

    @Override
    public boolean charTyped(
            char codePoint,
            int modifiers
    ) {

        if (commandPaletteVisible
            && commandPaletteInput != null
            && commandPaletteInput.visible
            && commandPaletteInput.isFocused()) {
            return commandPaletteInput.charTyped(codePoint, modifiers);
        }
        if (isScaleTypedCharacter(codePoint)) {
            return true;
        }
        if (isPanelVisible(PlaygroundPanel.TERMINAL) && terminalInput != null && terminalInput.visible && terminalInput.isFocused()) {
            return terminalInput.charTyped(codePoint, modifiers);
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean mouseClicked(
            double mouseX,
            double mouseY,
            int button
    ) {

        mouseX = ideCoordinate(mouseX);
        mouseY = ideCoordinate(mouseY);

        if (commandPaletteVisible) {
            if (handleCommandPaletteClick(mouseX, mouseY, button)) {
                return true;
            }
            closeCommandPalette();
            return true;
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            @Nullable ContentCornerHandle contentCornerHandle = findContentCornerHandle(mouseX, mouseY);
            if (contentCornerHandle != null) {
                if (hasAltDown()) {
                    beginContentCornerDrag(contentCornerHandle, mouseX, mouseY);
                    return true;
                }
            }

            @Nullable ContentEdgeHandle contentEdgeHandle = findContentEdgeHandle(mouseX, mouseY);
            if (contentEdgeHandle != null) {
                if (hasAltDown()) {
                    beginContentEdgeDrag(contentEdgeHandle, mouseX, mouseY);
                    return true;
                }
            }

            @Nullable CornerHandle cornerHandle = findCornerHandle(mouseX, mouseY);
            if (cornerHandle != null) {
                focusPanelEdge(cornerHandle.panel(), cornerHandle.corner().primaryEdge());
                if (hasAltDown()) {
                    beginCornerDrag(cornerHandle, mouseX, mouseY);
                }
                return true;
            }

            @Nullable EdgeHandle handle = findEdgeHandle(mouseX, mouseY);
            if (handle != null) {
                focusPanelEdge(handle.panel(), handle.edge());
                if (hasAltDown()) {
                    beginEdgeDrag(handle, mouseX, mouseY);
                }
                return true;
            }

            if (hasAltDown()) {
                @Nullable PlaygroundPanel draggablePanel = findDraggablePanel(mouseX, mouseY);
                if (draggablePanel != null) {
                    focusPanel(draggablePanel);
                    beginPanelDrag(draggablePanel, mouseX, mouseY);
                    return true;
                }
            }
        }

        for (PlaygroundPanel panel : visiblePanels()) {
            IdeArea area = currentLayout.get(panel);
            if (area != null && area.contains(mouseX, mouseY)) {
                focusPanel(panel);
                break;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(
            double mouseX,
            double mouseY,
            int button,
            double dragX,
            double dragY
    ) {

        mouseX = ideCoordinate(mouseX);
        mouseY = ideCoordinate(mouseY);
        dragX /= ideGlobalScale;
        dragY /= ideGlobalScale;

        if (activeDragTransaction != null && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            activeDragTransaction.drag(mouseX, mouseY);
            return true;
        }

        if (commandPaletteVisible && commandPaletteDragging && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            setCommandPalettePosition(
                    (int) Math.round(mouseX) - commandPaletteDragOffsetX,
                    (int) Math.round(mouseY) - commandPaletteDragOffsetY
            );
            updateCommandPaletteBounds();
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

        mouseX = ideCoordinate(mouseX);
        mouseY = ideCoordinate(mouseY);

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && activeDragTransaction != null) {
            releaseActiveDragTransaction(mouseX, mouseY);
            return true;
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && commandPaletteDragging) {
            commandPaletteDragging = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    public void toggleShellPanel() {

        setPanelVisible(PlaygroundPanel.SHELL, !isPanelVisible(PlaygroundPanel.SHELL));
        ensureFocusedPanelVisible();
    }

    public void toggleLayoutPanel() {

        setPanelVisible(PlaygroundPanel.LAYOUT, !isPanelVisible(PlaygroundPanel.LAYOUT));
        ensureFocusedPanelVisible();
    }

    public void toggleTerminalPanel() {

        setPanelVisible(PlaygroundPanel.TERMINAL, !isPanelVisible(PlaygroundPanel.TERMINAL));
        if (!isPanelVisible(PlaygroundPanel.TERMINAL) && terminalInput != null) {
            terminalInput.setFocus(false);
            setFocused(null);
        }
        ensureFocusedPanelVisible();
    }

    public void focusShellPanel() {

        setPanelVisible(PlaygroundPanel.SHELL, true);
        focusPanel(PlaygroundPanel.SHELL);
    }

    public void focusWorkspacePanel() {

        focusPanel(PlaygroundPanel.WORKSPACE);
    }

    public void focusLayoutPanel() {

        setPanelVisible(PlaygroundPanel.LAYOUT, true);
        focusPanel(PlaygroundPanel.LAYOUT);
    }

    public void focusTerminalPanel() {

        setPanelVisible(PlaygroundPanel.TERMINAL, true);
        focusPanel(PlaygroundPanel.TERMINAL);
    }

    public void selectFocusedTarget() {

        session.selectFocusedTarget();
    }

    public void clearSelectedTargets() {

        session.clearSelectedTargets();
    }

    public void showTerminalHelp() {

        appendTerminalMessage(IdeLocalizationKeys.IDE_PLAYGROUND_TERMINAL_HELP.getComponent().withStyle(ChatFormatting.DARK_GRAY));
        appendTerminalMessage(IdeLocalizationKeys.IDE_PLAYGROUND_TERMINAL_EMPTY.getComponent().withStyle(ChatFormatting.DARK_GRAY));
    }

    @Override
    protected void init() {

        super.init();
        clampPanelSizes();

        terminalInput = addRenderableWidget(new EditBox(
                font,
                MARGIN,
            ideHeight() - MARGIN - TERMINAL_INPUT_HEIGHT,
            Math.max(96, ideWidth() - MARGIN * 2),
                TERMINAL_INPUT_HEIGHT,
                IdeLocalizationKeys.IDE_PLAYGROUND_TERMINAL_PLACEHOLDER.getComponent()
        ));
        terminalInput.setMaxLength(256);
        terminalInput.setVisible(false);
        terminalHistoryIndex = terminalHistory.size();

        commandPaletteInput = addRenderableWidget(new EditBox(
                font,
                MARGIN,
                MARGIN,
            Math.max(160, ideWidth() / 3),
                COMMAND_PALETTE_INPUT_HEIGHT,
                IdeLocalizationKeys.IDE_PLAYGROUND_COMMAND_PALETTE_PLACEHOLDER.getComponent()
        ));
        commandPaletteInput.setMaxLength(128);
        commandPaletteInput.setVisible(false);
        commandPaletteInput.active = false;
        commandPaletteInput.setResponder(this::refreshCommandPaletteResults);
        refreshCommandPaletteResults("");

        if (terminalMessages.isEmpty()) {
            appendTerminalMessage(IdeLocalizationKeys.IDE_PLAYGROUND_TERMINAL_EMPTY
                                          .getComponent()
                                          .withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    private boolean handleFocusNavigationKey(int keyCode) {

        if (!hasAltDown() || hasShiftDown()) {
            return false;
        }

        return switch (keyCode) {
            case GLFW.GLFW_KEY_LEFT -> moveFocusInDirection(-1, 0, true);
            case GLFW.GLFW_KEY_RIGHT -> moveFocusInDirection(1, 0, false);
            case GLFW.GLFW_KEY_UP -> moveFocusInDirection(0, -1, true);
            case GLFW.GLFW_KEY_DOWN -> moveFocusInDirection(0, 1, false);
            default -> false;
        };
    }

    private boolean handleResizeKey(int keyCode) {

        if (!hasAltDown() || !hasControlDown()) {
            return false;
        }

        if (focusedEdge != null && resizeFocusedEdge(keyCode)) {
            return true;
        }

        switch (focusedPanel) {
            case SHELL -> {
                if (keyCode == GLFW.GLFW_KEY_LEFT) {
                    shellPanelWidth -= 8;
                    clampPanelSizes();
                    return true;
                }
                if (keyCode == GLFW.GLFW_KEY_RIGHT) {
                    shellPanelWidth += 8;
                    clampPanelSizes();
                    return true;
                }
            }
            case LAYOUT -> {
                if (keyCode == GLFW.GLFW_KEY_LEFT) {
                    layoutPanelWidth += 8;
                    clampPanelSizes();
                    return true;
                }
                if (keyCode == GLFW.GLFW_KEY_RIGHT) {
                    layoutPanelWidth -= 8;
                    clampPanelSizes();
                    return true;
                }
            }
            case TERMINAL -> {
                if (!isPanelVisible(PlaygroundPanel.TERMINAL)) {
                    return false;
                }
                if (keyCode == GLFW.GLFW_KEY_LEFT && isPanelVisible(PlaygroundPanel.SHELL)) {
                    shellPanelWidth = Math.max(MIN_SIDE_PANEL_WIDTH, shellPanelWidth - 8);
                    clampPanelSizes();
                    return true;
                }
                if (keyCode == GLFW.GLFW_KEY_RIGHT && isPanelVisible(PlaygroundPanel.LAYOUT)) {
                    layoutPanelWidth = Math.max(MIN_SIDE_PANEL_WIDTH, layoutPanelWidth - 8);
                    clampPanelSizes();
                    return true;
                }
                if (keyCode == GLFW.GLFW_KEY_DOWN) {
                    terminalPanelHeight -= 8;
                    clampPanelSizes();
                    return true;
                }
                if (keyCode == GLFW.GLFW_KEY_UP) {
                    terminalPanelHeight += 8;
                    clampPanelSizes();
                    return true;
                }
            }
            case WORKSPACE -> {
                if (keyCode == GLFW.GLFW_KEY_LEFT && isPanelVisible(PlaygroundPanel.SHELL)) {
                    shellPanelWidth = Math.max(MIN_SIDE_PANEL_WIDTH, shellPanelWidth - 8);
                    clampPanelSizes();
                    return true;
                }
                if (keyCode == GLFW.GLFW_KEY_RIGHT && isPanelVisible(PlaygroundPanel.LAYOUT)) {
                    layoutPanelWidth = Math.max(MIN_SIDE_PANEL_WIDTH, layoutPanelWidth - 8);
                    clampPanelSizes();
                    return true;
                }
                return false;
            }
        }
        return false;
    }

    private void clampPanelSizes() {

        clampContentMargins();
        IdeArea contentArea = contentArea();
        int availableWidth = Math.max(0, contentArea.width());
        int availableHeight = Math.max(0, contentArea.height());
        int maxCombinedSideWidth = Math.max(
                MIN_SIDE_PANEL_WIDTH * 2,
                availableWidth - 160
        );

        shellPanelWidth = Math.max(
                MIN_SIDE_PANEL_WIDTH,
                Math.min(shellPanelWidth, maxCombinedSideWidth - MIN_SIDE_PANEL_WIDTH)
        );
        layoutPanelWidth = Math.max(
                MIN_SIDE_PANEL_WIDTH,
                Math.min(layoutPanelWidth, maxCombinedSideWidth - MIN_SIDE_PANEL_WIDTH)
        );
        if (shellPanelWidth + layoutPanelWidth > maxCombinedSideWidth) {
            int overflow = shellPanelWidth + layoutPanelWidth - maxCombinedSideWidth;
            if (focusedPanel == PlaygroundPanel.SHELL) {
                layoutPanelWidth = Math.max(MIN_SIDE_PANEL_WIDTH, layoutPanelWidth - overflow);
            } else {
                shellPanelWidth = Math.max(MIN_SIDE_PANEL_WIDTH, shellPanelWidth - overflow);
            }
        }

        terminalPanelHeight = Math.max(
                MIN_BOTTOM_PANEL_HEIGHT,
                Math.min(
                        terminalPanelHeight,
                        Math.max(MIN_BOTTOM_PANEL_HEIGHT, availableHeight - 96)
                )
        );
    }

    private void addDockPieceIfVisible(
            List<IdeDockPiece<PlaygroundPanel>> pieces,
            PlaygroundPanel panel
    ) {

        if (!isPanelVisible(panel) || panel == PlaygroundPanel.WORKSPACE) {
            return;
        }

        IdeDockDirection direction = panelState(panel).dockDirection();
        int size = getDockSize(panel, direction);
        pieces.add(new IdeDockPiece<>(panel, direction, size));
    }

    private int getDockSize(
            PlaygroundPanel panel,
            IdeDockDirection direction
    ) {

        return switch (panel) {
            case SHELL -> direction == IdeDockDirection.UP || direction == IdeDockDirection.DOWN ? terminalPanelHeight : shellPanelWidth;
            case LAYOUT -> direction == IdeDockDirection.UP || direction == IdeDockDirection.DOWN ? terminalPanelHeight : layoutPanelWidth;
            case TERMINAL -> direction == IdeDockDirection.LEFT || direction == IdeDockDirection.RIGHT ? layoutPanelWidth : terminalPanelHeight;
            case WORKSPACE -> 0;
        };
    }

    private IdeDockDirection defaultDirection(PlaygroundPanel panel) {

        return switch (panel) {
            case SHELL -> IdeDockDirection.LEFT;
            case WORKSPACE -> IdeDockDirection.CENTER;
            case LAYOUT -> IdeDockDirection.RIGHT;
            case TERMINAL -> IdeDockDirection.DOWN;
        };
    }

    private Map<PlaygroundPanel, IdeArea> calculateLayout() {

        IdeArea rootArea = contentArea();

        List<IdeDockPiece<PlaygroundPanel>> pieces = new ArrayList<>();

        addDockPieceIfVisible(pieces, PlaygroundPanel.SHELL);
        addDockPieceIfVisible(pieces, PlaygroundPanel.LAYOUT);
        addDockPieceIfVisible(pieces, PlaygroundPanel.TERMINAL);
        pieces.add(new IdeDockPiece<>(PlaygroundPanel.WORKSPACE, IdeDockDirection.CENTER, 0));
        return IdeDockLayout.calculate(rootArea, pieces);
    }

    private List<Component> buildShellLines() {

        ArrayList<Component> lines = new ArrayList<>();

        lines.add(IdeLocalizationKeys.IDE_PLAYGROUND_LABEL_PLAYER.getComponent(session
                                                                                       .shellContext()
                                                                                       .playerPosition()
                                                                                       .orElse("none")));

        lines.add(IdeLocalizationKeys.IDE_PLAYGROUND_LABEL_LOOK.getComponent(session
                                                                                     .shellContext()
                                                                                     .lookVector().orElse("none")));

        lines.add(IdeLocalizationKeys.IDE_PLAYGROUND_LABEL_HIT.getComponent(session
                                                                                    .shellContext()
                                                                                    .hitSummary().orElse("none")));

        lines.add(IdeLocalizationKeys.IDE_PLAYGROUND_LABEL_DIMENSION.getComponent(session
                                                                                          .shellContext()
                                                                                          .dimensionId()
                                                                                          .orElse("none")));
        lines.add(Component.empty());

        lines.add(IdeLocalizationKeys.IDE_PLAYGROUND_LABEL_TARGET.getComponent(session
                                                                                       .shellContext()
                                                                                       .focusedTargetSummary()
                                                                                       .orElse("none")));
        lines.add(IdeLocalizationKeys.IDE_PLAYGROUND_LABEL_SELECTION.getComponent(Integer.toString(session
                                                                                                           .shellContext()
                                                                                                           .selectedTargetCount())));

        lines.add(IdeLocalizationKeys.IDE_PLAYGROUND_LABEL_FOCUS.getComponent(session
                                                                                      .shellContext()
                                                                                      .focusedPanelDisplay()
                                                                                      .orElse("none")));
        lines.add(IdeLocalizationKeys.IDE_PLAYGROUND_LABEL_IDE_SCALE.getComponent(formatScale(ideGlobalScale)));
        lines.add(IdeLocalizationKeys.IDE_PLAYGROUND_LABEL_PANEL_SCALE.getComponent(formatScale(getPanelScale(focusedPanel))));
        lines.add(Component.literal("Palette: " + COMMAND_PALETTE_SHORTCUT).withStyle(ChatFormatting.GRAY));
        lines.add(IdeLocalizationKeys.IDE_PLAYGROUND_LABEL_HINTS.getComponent(SFMKeyMappings.getKeyDisplay(
                SFMKeyMappings.IDE_OPEN_PLAYGROUND_KEY)));
        return lines;
    }

    private List<Component> buildLayoutLines() {

        return List.of(
                IdeLocalizationKeys.IDE_PLAYGROUND_LABEL_LAYOUT_MODE.getComponent(),
                IdeLocalizationKeys.IDE_PLAYGROUND_LABEL_LAYOUT_PIECES.getComponent(),
                Component.empty(),
                Component.literal(String.format(
                        Locale.ROOT, "shell=%s layout=%s terminal=%s",
                    isPanelVisible(PlaygroundPanel.SHELL) ? "shown" : "hidden",
                    isPanelVisible(PlaygroundPanel.LAYOUT) ? "shown" : "hidden",
                    isPanelVisible(PlaygroundPanel.TERMINAL) ? "shown" : "hidden"
                )).withStyle(ChatFormatting.GRAY),
                Component
                        .literal("palette=" + (commandPaletteVisible ? "shown" : "hidden"))
                        .withStyle(ChatFormatting.GRAY),
                IdeLocalizationKeys.IDE_PLAYGROUND_LABEL_TARGET.getComponent(session
                                                                                     .focusedTarget()
                                                                                     .summary().orElse("none")),
                IdeLocalizationKeys.IDE_PLAYGROUND_LABEL_FOCUS.getComponent(focusedPanel.display().getString())
        );
    }

    private void drawWorkspacePanel(
            PoseStack poseStack,
            @Nullable IdeArea area,
            boolean focused,
            Map<PlaygroundPanel, IdeArea> outerLayout
    ) {

        if (area == null || area.isEmpty()) {
            return;
        }
        drawPanelFrame(poseStack, area, IdeLocalizationKeys.IDE_PLAYGROUND_PANEL_WORKSPACE.getComponent(), focused);
        drawFocusedEdgeHighlight(poseStack, PlaygroundPanel.WORKSPACE, area);
        float panelScale = getPanelScale(PlaygroundPanel.WORKSPACE);
        int textWidth = Math.max(32, area.width() - PANEL_TEXT_PADDING * 2);
        int infoY = area.y() + 18;
        infoY = drawScaledWrappedText(
                poseStack,
                IdeLocalizationKeys.IDE_PLAYGROUND_LABEL_WORKSPACE.getComponent(),
                area.x() + PANEL_TEXT_PADDING,
                infoY,
                textWidth,
                0xE0E0E0,
            2,
            panelScale
        );

        infoY = drawScaledWrappedText(
                poseStack,
                IdeLocalizationKeys.IDE_PLAYGROUND_LABEL_TARGET.getComponent(session
                                                                                     .focusedTarget()
                                                                                     .summary().orElse("none")),
                area.x() + PANEL_TEXT_PADDING,
                infoY,
                textWidth,
                0xC8C8C8,
            2,
                panelScale
        );
            infoY = drawScaledWrappedText(
                poseStack,
                IdeLocalizationKeys.IDE_PLAYGROUND_LABEL_SELECTION.getComponent(Integer.toString(session
                                                                                                         .selectedTargets()
                                                                                                         .size())),
                area.x() + PANEL_TEXT_PADDING,
                infoY,
                textWidth,
                0xC8C8C8,
                1,
                panelScale
        );

        IdeArea previewArea = new IdeArea(
                area.x() + 10,
                infoY + 4,
                Math.max(0, area.width() - 20),
                Math.max(0, area.bottom() - (infoY + 14))
        );
        fill(poseStack, previewArea.x(), previewArea.y(), previewArea.right(), previewArea.bottom(), 0x33101010);

        IdeArea contentArea = contentArea();

        for (PlaygroundPanel panel : PlaygroundPanel.values()) {
            IdeArea panelArea = outerLayout.get(panel);
            if (panelArea == null) {
                continue;
            }

                int previewX = previewArea.x() + scale(panelArea.x() - contentArea.x(), contentArea.width(), previewArea.width());
            int previewY = previewArea.y() + scale(
                    panelArea.y() - contentArea.y(),
                    contentArea.height(),
                    previewArea.height()
            );
                int previewW = Math.max(12, scale(panelArea.width(), contentArea.width(), previewArea.width()));
            int previewH = Math.max(
                    10,
                    scale(panelArea.height(), contentArea.height(), previewArea.height())
            );
            int color = panel == focusedPanel ? 0xFF4C7899 : 0xAA2A2A2A;
            fill(poseStack, previewX, previewY, previewX + previewW, previewY + previewH, color);
            String previewLabel = font.plainSubstrByWidth(panel.display().getString(), Math.max(6, previewW - 4));
            drawCenteredString(
                    poseStack,
                    font,
                    previewLabel,
                    previewX + previewW / 2,
                    previewY + Math.max(1, previewH / 2 - 4),
                    0xFFFFFF
            );
        }

        int targetLineStep = Math.max(1, Math.round((font.lineHeight + 2) * panelScale));
        int footerY = previewArea.bottom() - targetLineStep * Math.min(3, session.selectedTargets().size()) - 8;
        for (IdeSessionTarget selectedTarget : session.selectedTargets().stream().limit(3).toList()) {

            footerY = drawScaledWrappedText(
                    poseStack,
                    Component.literal("• " + selectedTarget.summary().orElse("none")).withStyle(ChatFormatting.AQUA),
                    previewArea.x() + PANEL_TEXT_PADDING,
                    footerY,
                    Math.max(32, previewArea.width() - PANEL_TEXT_PADDING * 2),
                    0xAEE8FF,
                    1,
                    panelScale
            );
        }
    }

    private void drawTerminalPanel(
            PoseStack poseStack,
            @Nullable IdeArea area,
            boolean focused
    ) {

        updateTerminalInputBounds(area);
        if (!isPanelVisible(PlaygroundPanel.TERMINAL) || area == null || area.isEmpty()) {
            return;
        }

        drawPanelFrame(poseStack, area, IdeLocalizationKeys.IDE_PLAYGROUND_PANEL_TERMINAL.getComponent(), focused);
        drawFocusedEdgeHighlight(poseStack, PlaygroundPanel.TERMINAL, area);
        float panelScale = getPanelScale(PlaygroundPanel.TERMINAL);

        int inputTop = area.bottom() - TERMINAL_INPUT_HEIGHT - TERMINAL_INPUT_MARGIN - 2;
        int lineStep = Math.max(1, Math.round((font.lineHeight + 2) * panelScale));
        int textWidth = Math.max(32, area.width() - PANEL_TEXT_PADDING * 2);

        int messageTop = drawScaledWrappedText(
                poseStack,
                IdeLocalizationKeys.IDE_PLAYGROUND_TERMINAL_HINT.getComponent(),
                area.x() + PANEL_TEXT_PADDING,
                area.y() + 18,
                textWidth,
                0xC8C8C8,
                2,
                panelScale
        ) + 2;

        int lineY = inputTop - font.lineHeight;
        int scaledTextWidth = Math.max(1, Math.round(textWidth / panelScale));
        int scaledTextX = Math.round((area.x() + PANEL_TEXT_PADDING) / panelScale);
        poseStack.pushPose();
        poseStack.scale(panelScale, panelScale, 1.0F);
        for (int i = terminalMessages.size() - 1; i >= 0; i--) {
            List<FormattedCharSequence> wrapped = font.split(terminalMessages.get(i), scaledTextWidth);
            for (int j = wrapped.size() - 1; j >= 0; j--) {
                if (lineY < messageTop) {
                    poseStack.popPose();
                    return;
                }
                font.draw(poseStack, wrapped.get(j), scaledTextX, Math.round(lineY / panelScale), 0xE0E0E0);
                lineY -= lineStep;
            }
        }
        poseStack.popPose();
    }

    private void drawCommandPaletteOverlay(
            PoseStack poseStack,
            int mouseX,
            int mouseY,
            float partialTick
    ) {

        poseStack.pushPose();
        poseStack.translate(0, 0, COMMAND_PALETTE_Z_OFFSET);
        drawCommandPalette(poseStack);
        if (commandPaletteInput != null) {
            commandPaletteInput.render(poseStack, mouseX, mouseY, partialTick);
        }
        poseStack.popPose();
    }

    private void drawCommandPalette(PoseStack poseStack) {

        if (commandPaletteInput == null) {
            return;
        }

        IdeArea area = getCommandPaletteArea();
        fill(poseStack, area.x(), area.y(), area.right(), area.bottom(), 0xE0181B22);
        fill(poseStack, area.x(), area.y(), area.right(), area.y() + 1, 0xFF66CCFF);
        fill(poseStack, area.x(), area.bottom() - 1, area.right(), area.bottom(), 0xFF66CCFF);
        fill(poseStack, area.x(), area.y(), area.x() + 1, area.bottom(), 0xFF66CCFF);
        fill(poseStack, area.right() - 1, area.y(), area.right(), area.bottom(), 0xFF66CCFF);

        drawString(
                poseStack,
                font,
                IdeLocalizationKeys.IDE_PLAYGROUND_COMMAND_PALETTE_TITLE.getComponent(),
                area.x() + PANEL_TEXT_PADDING,
                area.y() + 6,
                0xFFFFFF
        );
        drawString(
                poseStack,
                font,
                IdeLocalizationKeys.IDE_PLAYGROUND_COMMAND_PALETTE_HINT.getComponent(),
                area.x() + PANEL_TEXT_PADDING,
                area.y() + 6 + font.lineHeight + 4,
                0xB8C8D6
        );

        int resultsTop = commandPaletteInput.y + COMMAND_PALETTE_INPUT_HEIGHT + 8;
        if (filteredPaletteActions.isEmpty()) {
            drawString(
                    poseStack,
                    font,
                    IdeLocalizationKeys.IDE_PLAYGROUND_COMMAND_PALETTE_EMPTY
                            .getComponent()
                            .withStyle(ChatFormatting.DARK_GRAY),
                    area.x() + PANEL_TEXT_PADDING,
                    resultsTop,
                    0x808080
            );
            return;
        }

        for (int i = 0; i < filteredPaletteActions.size() && i < COMMAND_PALETTE_MAX_RESULTS; i++) {
            IdeActionDefinition definition = filteredPaletteActions.get(i);
            int rowTop = resultsTop + i * COMMAND_PALETTE_ROW_HEIGHT;
            boolean selected = i == selectedPaletteActionIndex;
            if (selected) {
                fill(
                        poseStack,
                        area.x() + 4,
                        rowTop - 2,
                        area.right() - 4,
                        rowTop + COMMAND_PALETTE_ROW_HEIGHT - 4,
                        0x553A5E7A
                );
            }

            String titleText = definition.title().getString();
            if (recentActions.contains(definition) && commandPaletteInput.getValue().isBlank()) {
                titleText = "★ " + titleText;
            }
            drawString(
                    poseStack,
                    font,
                    font.plainSubstrByWidth(titleText, Math.max(32, area.width() - 120)),
                    area.x() + PANEL_TEXT_PADDING,
                    rowTop,
                    selected ? 0xFFFFFF : 0xDDE7EF
            );
            drawString(
                    poseStack,
                    font,
                    font.plainSubstrByWidth(
                            definition.id().toString(),
                            Math.max(32, area.width() - PANEL_TEXT_PADDING * 2)
                    ),
                    area.x() + PANEL_TEXT_PADDING,
                    rowTop + font.lineHeight + 1,
                    selected ? 0x9EDAFF : 0x8AA1B5
            );

            definition.keybindingHint().ifPresent(hint -> {
                int hintWidth = font.width(hint);
                drawString(
                        poseStack,
                        font,
                        hint,
                        area.right() - PANEL_TEXT_PADDING - hintWidth,
                        rowTop,
                        selected ? 0xC5F0FF : 0x7FAFC9
                );
            });
        }
    }

    private int scale(
            int value,
            int sourceSize,
            int targetSize
    ) {

        if (sourceSize <= 0 || targetSize <= 0) {
            return 0;
        }
        return (int) Math.round((double) value * targetSize / sourceSize);
    }

    private int drawScaledWrappedText(
            PoseStack poseStack,
            Component text,
            int x,
            int y,
            int maxWidth,
            int color,
            int maxLines,
            float scale
    ) {

        if (Math.abs(scale - 1.0F) < 0.001F) {
            return drawWrappedText(poseStack, text, x, y, maxWidth, color, maxLines);
        }

        int linesDrawn = 0;
        int scaledX = Math.round(x / scale);
        int scaledY = Math.round(y / scale);
        int scaledWidth = Math.max(1, Math.round(maxWidth / scale));
        poseStack.pushPose();
        poseStack.scale(scale, scale, 1.0F);
        for (FormattedCharSequence sequence : font.split(text, scaledWidth)) {
            if (linesDrawn >= maxLines) {
                break;
            }
            font.draw(poseStack, sequence, scaledX, scaledY, color);
            scaledY += font.lineHeight + 2;
            linesDrawn++;
        }
        poseStack.popPose();
        return y + Math.round(linesDrawn * (font.lineHeight + 2) * scale);
    }

    private void drawPanel(
            PoseStack poseStack,
            PlaygroundPanel panel,
            @Nullable IdeArea area,
            Component title,
            boolean focused,
            List<Component> lines
    ) {

        if (area == null || area.isEmpty()) {
            return;
        }
        drawPanelFrame(poseStack, area, title, focused);
        drawFocusedEdgeHighlight(poseStack, panel, area);
        float panelScale = getPanelScale(panel);
        int y = area.y() + 18;
        int textWidth = Math.max(32, area.width() - PANEL_TEXT_PADDING * 2);
        for (Component line : lines) {
            y = drawScaledWrappedText(
                    poseStack,
                    line,
                    area.x() + PANEL_TEXT_PADDING,
                    y,
                    textWidth,
                    0xE0E0E0,
                    Integer.MAX_VALUE,
                    panelScale
            );
            if (y > area.bottom() - Math.round(font.lineHeight * panelScale) - PANEL_TEXT_PADDING) {
                break;
            }
        }
    }

    private void drawPanelFrame(
            PoseStack poseStack,
            @Nullable IdeArea area,
            Component panelTitle,
            boolean focused
    ) {

        if (area == null || area.isEmpty()) {
            return;
        }
        int background = focused ? 0xCC18212A : 0xB8141414;
        int border = focused ? 0xFF66CCFF : 0xFF303030;
        fill(poseStack, area.x(), area.y(), area.right(), area.bottom(), background);
        fill(poseStack, area.x(), area.y(), area.right(), area.y() + 1, border);
        fill(poseStack, area.x(), area.bottom() - 1, area.right(), area.bottom(), border);
        fill(poseStack, area.x(), area.y(), area.x() + 1, area.bottom(), border);
        fill(poseStack, area.right() - 1, area.y(), area.right(), area.bottom(), border);
        drawString(
                poseStack,
                font,
                font.plainSubstrByWidth(panelTitle.getString(), Math.max(16, area.width() - PANEL_TEXT_PADDING * 2)),
                area.x() + PANEL_TEXT_PADDING,
                area.y() + 5,
                0xFFFFFF
        );
    }

    private void drawFocusedEdgeHighlight(
            PoseStack poseStack,
            PlaygroundPanel panel,
            @Nullable IdeArea area
    ) {

        if (area == null || area.isEmpty() || panel != focusedPanel || focusedEdge == null) {
            return;
        }

        int thickness = 3;
        int color = 0xFFF9C74F;
        switch (focusedEdge) {
            case LEFT -> fill(poseStack, area.x(), area.y(), area.x() + thickness, area.bottom(), color);
            case RIGHT -> fill(poseStack, area.right() - thickness, area.y(), area.right(), area.bottom(), color);
            case UP -> fill(poseStack, area.x(), area.y(), area.right(), area.y() + thickness, color);
            case DOWN -> fill(poseStack, area.x(), area.bottom() - thickness, area.right(), area.bottom(), color);
        }
    }

    private void drawPanelDragOverlay(PoseStack poseStack) {

        if (!(activeDragTransaction instanceof PanelDockDragTransaction panelDrag)) {
            return;
        }

        IdeDockDirection previewDirection = panelDrag.previewDirection();
        if (previewDirection == null) {
            return;
        }

        IdeArea preview = getDockPreviewArea(previewDirection);
        if (preview == null || preview.isEmpty()) {
            return;
        }

        fill(poseStack, preview.x(), preview.y(), preview.right(), preview.bottom(), 0x334C9AFF);
        fill(poseStack, preview.x(), preview.y(), preview.right(), preview.y() + 1, 0xFF9EDAFF);
        fill(poseStack, preview.x(), preview.bottom() - 1, preview.right(), preview.bottom(), 0xFF9EDAFF);
        fill(poseStack, preview.x(), preview.y(), preview.x() + 1, preview.bottom(), 0xFF9EDAFF);
        fill(poseStack, preview.right() - 1, preview.y(), preview.right(), preview.bottom(), 0xFF9EDAFF);
        drawCenteredString(poseStack, font, panelDrag.panel().display().getString(), preview.x() + preview.width() / 2, preview.y() + preview.height() / 2 - 4, 0xFFFFFF);
    }

    private @Nullable IdeArea getDockPreviewArea(IdeDockDirection direction) {

        IdeArea root = contentArea();

        return switch (direction) {
            case LEFT -> new IdeArea(root.x(), root.y(), Math.max(MIN_SIDE_PANEL_WIDTH, root.width() / 4), root.height());
            case RIGHT -> new IdeArea(root.right() - Math.max(MIN_SIDE_PANEL_WIDTH, root.width() / 4), root.y(), Math.max(MIN_SIDE_PANEL_WIDTH, root.width() / 4), root.height());
            case UP -> new IdeArea(root.x(), root.y(), root.width(), Math.max(MIN_BOTTOM_PANEL_HEIGHT, root.height() / 4));
            case DOWN -> new IdeArea(root.x(), root.bottom() - Math.max(MIN_BOTTOM_PANEL_HEIGHT, root.height() / 4), root.width(), Math.max(MIN_BOTTOM_PANEL_HEIGHT, root.height() / 4));
            case CENTER -> null;
        };
    }

    private void dispatchAction(IdeActionDefinition action) {

        action.executor().accept(this);
        rememberRecentAction(action);
        refreshCommandPaletteResults(commandPaletteInput == null ? "" : commandPaletteInput.getValue());
        appendTerminalMessage(IdeLocalizationKeys.IDE_PLAYGROUND_TERMINAL_SUCCESS
                                      .getComponent(action)
                                      .withStyle(ChatFormatting.GREEN));
    }


    private void submitTerminalCommand() {

        if (terminalInput == null) {
            return;
        }

        String rawCommand = terminalInput.getValue().trim();
        if (rawCommand.isEmpty()) {
            return;
        }
        terminalInput.setValue("");
        terminalHistory.add(rawCommand);
        terminalHistoryIndex = terminalHistory.size();
        terminalHistoryDraft = "";

        @Nullable IdeActionDefinition resolvedAction = resolveTerminalCommand(rawCommand);

        if (resolvedAction != null) {
            dispatchAction(resolvedAction);
        } else {
            appendTerminalMessage(IdeLocalizationKeys.IDE_PLAYGROUND_TERMINAL_UNKNOWN
                                          .getComponent(rawCommand)
                                          .withStyle(ChatFormatting.RED));
        }
    }

    private void appendTerminalMessage(Component message) {

        terminalMessages.add(message);
        while (terminalMessages.size() > MAX_TERMINAL_MESSAGES) {
            terminalMessages.remove(0);
        }
    }

    private boolean recallTerminalHistory(int direction) {

        if (terminalInput == null || terminalHistory.isEmpty()) {
            return false;
        }

        int size = terminalHistory.size();
        if (terminalHistoryIndex == size) {
            terminalHistoryDraft = terminalInput.getValue();
        }

        int nextIndex = Math.max(0, Math.min(size, terminalHistoryIndex + direction));
        if (nextIndex == terminalHistoryIndex) {
            return true;
        }

        terminalHistoryIndex = nextIndex;
        if (terminalHistoryIndex == size) {
            terminalInput.setValue(terminalHistoryDraft);
        } else {
            terminalInput.setValue(terminalHistory.get(terminalHistoryIndex));
        }
        terminalInput.moveCursorToEnd();
        return true;
    }

    private void updateTerminalInputBounds(@Nullable IdeArea area) {

        if (terminalInput == null) {
            return;
        }
        boolean visible = isPanelVisible(PlaygroundPanel.TERMINAL) && area != null && !area.isEmpty();
        terminalInput.visible = visible;
        terminalInput.active = visible;
        if (!visible) {
            terminalInput.setFocus(false);
            return;
        }

        float panelScale = getPanelScale(PlaygroundPanel.TERMINAL);
        int innerWidth = Math.max(1, area.width() - PANEL_TEXT_PADDING * 2);
        int preferredWidth = Math.round(innerWidth * panelScale);
        int width = Math.max(40, Math.min(innerWidth, preferredWidth));

        int maxHeight = Math.max(1, area.height() - 18 - TERMINAL_INPUT_MARGIN - PANEL_TEXT_PADDING);
        int preferredHeight = Math.round(TERMINAL_INPUT_HEIGHT * panelScale);
        int height = Math.max(TERMINAL_INPUT_HEIGHT, Math.min(maxHeight, preferredHeight));

        terminalInput.setWidth(width);
        terminalInput.setHeight(height);
        terminalInput.setX(area.x() + PANEL_TEXT_PADDING + (innerWidth - width) / 2);
        terminalInput.y = Math.max(area.y() + 18, area.bottom() - height - TERMINAL_INPUT_MARGIN);
    }

    private void updateCommandPaletteBounds() {

        if (commandPaletteInput == null) {
            return;
        }
        IdeArea area = getCommandPaletteArea();
        commandPaletteInput.visible = commandPaletteVisible;
        commandPaletteInput.active = commandPaletteVisible;
        if (!commandPaletteVisible) {
            commandPaletteInput.setFocus(false);
            return;
        }
        commandPaletteInput.setWidth(Math.max(160, area.width() - PANEL_TEXT_PADDING * 2));
        commandPaletteInput.setX(area.x() + PANEL_TEXT_PADDING);
        commandPaletteInput.y = area.y() + 6 + font.lineHeight + 6 + font.lineHeight + 6;
    }

    private IdeArea getCommandPaletteArea() {

        int paletteWidth = Math.min(420, Math.max(220, ideWidth() - MARGIN * 6));
        int visibleRows = Math.max(1, Math.min(COMMAND_PALETTE_MAX_RESULTS, filteredPaletteActions.size()));
        int paletteHeight = 16
                            + COMMAND_PALETTE_INPUT_HEIGHT
                            + 18
                            + font.lineHeight * 2
                            + visibleRows * COMMAND_PALETTE_ROW_HEIGHT;
        int x = commandPaletteX == Integer.MIN_VALUE ? (ideWidth() - paletteWidth) / 2 : commandPaletteX;
        int y = commandPaletteY == Integer.MIN_VALUE ? MARGIN + 18 : commandPaletteY;
        x = clampCommandPaletteX(x, paletteWidth);
        y = clampCommandPaletteY(y, paletteHeight);
        commandPaletteX = x;
        commandPaletteY = y;
        return new IdeArea(x, y, paletteWidth, paletteHeight);
    }

    private int drawWrappedText(
            PoseStack poseStack,
            Component text,
            int x,
            int y,
            int maxWidth,
            int color,
            int maxLines
    ) {

        int linesDrawn = 0;
        for (FormattedCharSequence sequence : font.split(text, Math.max(1, maxWidth))) {
            if (linesDrawn >= maxLines) {
                break;
            }
            font.draw(poseStack, sequence, x, y, color);
            y += font.lineHeight + 2;
            linesDrawn++;
        }
        return y;
    }

    private void ensureFocusedPanelVisible() {

        if (isPanelVisible(focusedPanel)) {
            return;
        }
        focusPanel(visiblePanels().get(0));
    }

    private void cycleFocus(boolean reverse) {

        List<PlaygroundPanel> visiblePanels = visiblePanels();
        int currentIndex = visiblePanels.indexOf(focusedPanel);
        if (currentIndex < 0) {
            currentIndex = 0;
        }
        int nextIndex = reverse
                        ? (currentIndex - 1 + visiblePanels.size()) % visiblePanels.size()
                        : (currentIndex + 1) % visiblePanels.size();
        focusPanel(visiblePanels.get(nextIndex));
    }

    private boolean moveFocusInDirection(
            int xDirection,
            int yDirection,
            boolean reverseFallback
    ) {

        @Nullable PanelEdge requestedEdge = PanelEdge.fromDirection(xDirection, yDirection);
        if (requestedEdge != null) {
            if (focusedEdge != null) {
                if (focusedEdge == requestedEdge) {
                    focusPanel(focusedPanel);
                    return movePanelFocusInDirection(xDirection, yDirection, reverseFallback);
                }
                if (focusedEdge.opposite() == requestedEdge) {
                    focusPanel(focusedPanel);
                    return true;
                }
                if (panelSupportsEdge(focusedPanel, requestedEdge)) {
                    focusPanelEdge(focusedPanel, requestedEdge);
                    return true;
                }
            } else if (panelSupportsEdge(focusedPanel, requestedEdge)) {
                focusPanelEdge(focusedPanel, requestedEdge);
                return true;
            }
        }

        return movePanelFocusInDirection(xDirection, yDirection, reverseFallback);
    }

    private boolean movePanelFocusInDirection(
            int xDirection,
            int yDirection,
            boolean reverseFallback
    ) {

        List<PlaygroundPanel> visiblePanels = visiblePanels();
        if (visiblePanels.size() <= 1) {
            return false;
        }

        if (xDirection < 0 && focusedPanel == PlaygroundPanel.SHELL && isPanelVisible(PlaygroundPanel.LAYOUT)) {
            focusPanel(PlaygroundPanel.LAYOUT);
            return true;
        }
        if (xDirection > 0 && focusedPanel == PlaygroundPanel.LAYOUT && isPanelVisible(PlaygroundPanel.SHELL)) {
            focusPanel(PlaygroundPanel.SHELL);
            return true;
        }

        Map<PlaygroundPanel, IdeArea> layout = calculateLayout();
        currentLayout = layout;

        IdeArea focusedArea = layout.get(focusedPanel);
        if (focusedArea == null || focusedArea.isEmpty()) {
            cycleFocus(reverseFallback);
            return true;
        }

        int focusedCenterX = focusedArea.x() + focusedArea.width() / 2;
        int focusedCenterY = focusedArea.y() + focusedArea.height() / 2;
        PlaygroundPanel bestPanel = null;
        double bestScore = Double.POSITIVE_INFINITY;

        for (PlaygroundPanel panel : visiblePanels) {
            if (panel == focusedPanel) {
                continue;
            }

            IdeArea candidateArea = layout.get(panel);
            if (candidateArea == null || candidateArea.isEmpty()) {
                continue;
            }

            int primaryDistance;
            int secondaryDistance;

            if (xDirection < 0) {
                primaryDistance = focusedArea.x() - candidateArea.right();
                secondaryDistance = perpendicularDistance(
                        focusedArea.y(),
                        focusedArea.bottom(),
                        candidateArea.y(),
                        candidateArea.bottom()
                );
            } else if (xDirection > 0) {
                primaryDistance = candidateArea.x() - focusedArea.right();
                secondaryDistance = perpendicularDistance(
                        focusedArea.y(),
                        focusedArea.bottom(),
                        candidateArea.y(),
                        candidateArea.bottom()
                );
            } else if (yDirection < 0) {
                primaryDistance = focusedArea.y() - candidateArea.bottom();
                secondaryDistance = perpendicularDistance(
                        focusedArea.x(),
                        focusedArea.right(),
                        candidateArea.x(),
                        candidateArea.right()
                );
            } else {
                primaryDistance = candidateArea.y() - focusedArea.bottom();
                secondaryDistance = perpendicularDistance(
                        focusedArea.x(),
                        focusedArea.right(),
                        candidateArea.x(),
                        candidateArea.right()
                );
            }

            if (primaryDistance < 0) {
                continue;
            }

            double score = primaryDistance * 1000.0 + secondaryDistance;
            if (score < bestScore) {
                bestScore = score;
                bestPanel = panel;
            }
        }

        if (bestPanel == null) {
            for (PlaygroundPanel panel : visiblePanels) {
                if (panel == focusedPanel) {
                    continue;
                }

                IdeArea candidateArea = layout.get(panel);
                if (candidateArea == null || candidateArea.isEmpty()) {
                    continue;
                }

                int candidateCenterX = candidateArea.x() + candidateArea.width() / 2;
                int candidateCenterY = candidateArea.y() + candidateArea.height() / 2;
                int primaryDistance;
                int secondaryDistance;

                if (xDirection < 0) {
                    primaryDistance = focusedCenterX - candidateCenterX;
                    secondaryDistance = perpendicularDistance(
                            focusedArea.y(),
                            focusedArea.bottom(),
                            candidateArea.y(),
                            candidateArea.bottom()
                    );
                } else if (xDirection > 0) {
                    primaryDistance = candidateCenterX - focusedCenterX;
                    secondaryDistance = perpendicularDistance(
                            focusedArea.y(),
                            focusedArea.bottom(),
                            candidateArea.y(),
                            candidateArea.bottom()
                    );
                } else if (yDirection < 0) {
                    primaryDistance = focusedCenterY - candidateCenterY;
                    secondaryDistance = perpendicularDistance(
                            focusedArea.x(),
                            focusedArea.right(),
                            candidateArea.x(),
                            candidateArea.right()
                    );
                } else {
                    primaryDistance = candidateCenterY - focusedCenterY;
                    secondaryDistance = perpendicularDistance(
                            focusedArea.x(),
                            focusedArea.right(),
                            candidateArea.x(),
                            candidateArea.right()
                    );
                }

                if (primaryDistance <= 0) {
                    continue;
                }

                double score = primaryDistance * 1000.0 + secondaryDistance;
                if (score < bestScore) {
                    bestScore = score;
                    bestPanel = panel;
                }
            }
        }

        if (bestPanel == null) {
            cycleFocus(reverseFallback);
            return true;
        }

        focusPanel(bestPanel);
        return true;
    }

    private int perpendicularDistance(
            int focusedStart,
            int focusedEnd,
            int candidateStart,
            int candidateEnd
    ) {

        if (candidateEnd <= focusedStart) {
            return focusedStart - candidateEnd;
        }
        if (candidateStart >= focusedEnd) {
            return candidateStart - focusedEnd;
        }
        return 0;
    }

    private void focusPanel(PlaygroundPanel panel) {

        focusedPanel = panel;
        focusedEdge = null;
        if (commandPaletteVisible) {
            return;
        }
        boolean focusTerminalInput = panel == PlaygroundPanel.TERMINAL
                                     && isPanelVisible(PlaygroundPanel.TERMINAL)
                                     && terminalInput != null
                                     && terminalInput.visible;
        if (terminalInput != null) {
            terminalInput.setFocus(focusTerminalInput);
        }
        setFocused(focusTerminalInput ? terminalInput : null);
    }

    private void focusPanelEdge(
            PlaygroundPanel panel,
            PanelEdge edge
    ) {

        focusedPanel = panel;
        focusedEdge = edge;
        if (terminalInput != null) {
            terminalInput.setFocus(false);
        }
        setFocused(null);
    }

    private List<PlaygroundPanel> visiblePanels() {

        ArrayList<PlaygroundPanel> panels = new ArrayList<>();
        if (isPanelVisible(PlaygroundPanel.SHELL)) {
            panels.add(PlaygroundPanel.SHELL);
        }
        panels.add(PlaygroundPanel.WORKSPACE);
        if (isPanelVisible(PlaygroundPanel.LAYOUT)) {
            panels.add(PlaygroundPanel.LAYOUT);
        }
        if (isPanelVisible(PlaygroundPanel.TERMINAL)) {
            panels.add(PlaygroundPanel.TERMINAL);
        }
        return panels;
    }

    private boolean isPanelVisible(PlaygroundPanel panel) {

        return panelState(panel).visible();
    }

    private @Nullable PlaygroundPanel findDraggablePanel(
            double mouseX,
            double mouseY
    ) {

        for (PlaygroundPanel panel : visiblePanels()) {
            IdeArea area = currentLayout.get(panel);
            if (area != null
                && area.contains(mouseX, mouseY)
                && findEdgeHandle(mouseX, mouseY) == null
                && findCornerHandle(mouseX, mouseY) == null
                && findContentEdgeHandle(mouseX, mouseY) == null
                && findContentCornerHandle(mouseX, mouseY) == null) {
                return panel;
            }
        }
        return null;
    }

    private boolean isCommandPaletteShortcut(int keyCode) {

        return keyCode == GLFW.GLFW_KEY_P && hasControlDown() && hasShiftDown();
    }

    private boolean handleScaleKey(int keyCode) {

        if (keyCode == GLFW.GLFW_KEY_0 || keyCode == GLFW.GLFW_KEY_KP_0) {
            if (hasControlDown() && !hasAltDown()) {
                ideGlobalScale = 1.0F;
                clampPanelSizes();
                updateCommandPaletteBounds();
                return true;
            }
            if (hasAltDown() && !hasControlDown()) {
                resetPanelScale(focusedPanel);
                return true;
            }
            return false;
        }

        int direction = scaleKeyDirection(keyCode);
        if (direction == 0) {
            return false;
        }

        if (hasControlDown() && !hasAltDown()) {
            ideGlobalScale = normalizeScale(ideGlobalScale + IDE_GLOBAL_SCALE_STEP * direction);
            clampPanelSizes();
            updateCommandPaletteBounds();
            return true;
        }
        if (hasAltDown() && !hasControlDown()) {
            setPanelScale(focusedPanel, normalizeScale(getPanelScale(focusedPanel) + PANEL_SCALE_STEP * direction));
            return true;
        }
        return false;
    }

    private boolean resizeFocusedEdge(int keyCode) {

        if (focusedEdge == null) {
            return false;
        }

        return switch (focusedEdge) {
            case LEFT, RIGHT -> switch (keyCode) {
                case GLFW.GLFW_KEY_LEFT -> {
                    resizePanelEdgeByDelta(focusedPanel, focusedEdge, -8, 0);
                    yield true;
                }
                case GLFW.GLFW_KEY_RIGHT -> {
                    resizePanelEdgeByDelta(focusedPanel, focusedEdge, 8, 0);
                    yield true;
                }
                default -> false;
            };
            case UP, DOWN -> switch (keyCode) {
                case GLFW.GLFW_KEY_UP -> {
                    resizePanelEdgeByDelta(focusedPanel, focusedEdge, 0, -8);
                    yield true;
                }
                case GLFW.GLFW_KEY_DOWN -> {
                    resizePanelEdgeByDelta(focusedPanel, focusedEdge, 0, 8);
                    yield true;
                }
                default -> false;
            };
        };
    }

    private int scaleKeyDirection(int keyCode) {

        return switch (keyCode) {
            case GLFW.GLFW_KEY_EQUAL, GLFW.GLFW_KEY_KP_ADD -> 1;
            case GLFW.GLFW_KEY_MINUS, GLFW.GLFW_KEY_KP_SUBTRACT -> -1;
            default -> 0;
        };
    }

    private float normalizeScale(float value) {

        float rounded = Math.round(value * 100.0F) / 100.0F;
        if (rounded <= 0.0F) {
            return MIN_NONZERO_SCALE;
        }
        return rounded;
    }

    private boolean panelSupportsEdge(
            PlaygroundPanel panel,
            PanelEdge edge
    ) {

        IdeDockDirection direction = panelState(panel).dockDirection();

        return switch (panel) {
            case WORKSPACE -> edge == PanelEdge.LEFT || edge == PanelEdge.RIGHT || edge == PanelEdge.DOWN || edge == PanelEdge.UP;
            case SHELL, LAYOUT, TERMINAL -> switch (direction) {
                case LEFT -> edge == PanelEdge.RIGHT;
                case RIGHT -> edge == PanelEdge.LEFT;
                case UP -> edge == PanelEdge.DOWN;
                case DOWN -> edge == PanelEdge.UP;
                case CENTER -> false;
            };
        };
    }

    private boolean panelSupportsCorner(
            PlaygroundPanel panel,
            PanelCorner corner
    ) {

        return panelSupportsEdge(panel, corner.horizontalEdge()) || panelSupportsEdge(panel, corner.verticalEdge());
    }

    private IdeArea contentArea() {

        return new IdeArea(
                contentMarginLeft,
                contentMarginTop,
                Math.max(1, ideWidth() - contentMarginLeft - contentMarginRight),
                Math.max(1, ideHeight() - contentMarginTop - contentMarginBottom)
        );
    }

    private void clampContentMargins() {

        int maxHorizontalMargins = Math.max(0, ideWidth() - MIN_CONTENT_WIDTH);
        int maxVerticalMargins = Math.max(0, ideHeight() - MIN_CONTENT_HEIGHT);

        contentMarginLeft = Math.max(0, Math.min(contentMarginLeft, maxHorizontalMargins));
        contentMarginRight = Math.max(0, Math.min(contentMarginRight, maxHorizontalMargins));
        if (contentMarginLeft + contentMarginRight > maxHorizontalMargins) {
            int overflow = contentMarginLeft + contentMarginRight - maxHorizontalMargins;
            if (contentMarginLeft >= contentMarginRight) {
                contentMarginLeft = Math.max(0, contentMarginLeft - overflow);
            } else {
                contentMarginRight = Math.max(0, contentMarginRight - overflow);
            }
        }

        contentMarginTop = Math.max(0, Math.min(contentMarginTop, maxVerticalMargins));
        contentMarginBottom = Math.max(0, Math.min(contentMarginBottom, maxVerticalMargins));
        if (contentMarginTop + contentMarginBottom > maxVerticalMargins) {
            int overflow = contentMarginTop + contentMarginBottom - maxVerticalMargins;
            if (contentMarginTop >= contentMarginBottom) {
                contentMarginTop = Math.max(0, contentMarginTop - overflow);
            } else {
                contentMarginBottom = Math.max(0, contentMarginBottom - overflow);
            }
        }
    }

    private void resizeContentEdgeByDelta(
            PanelEdge edge,
            int deltaX,
            int deltaY
    ) {

        switch (edge) {
            case LEFT -> contentMarginLeft += deltaX;
            case RIGHT -> contentMarginRight -= deltaX;
            case UP -> contentMarginTop += deltaY;
            case DOWN -> contentMarginBottom -= deltaY;
        }
        clampPanelSizes();
    }

    private void translateContentArea(
            int deltaX,
            int deltaY
    ) {

        IdeArea contentArea = contentArea();
        int width = contentArea.width();
        int height = contentArea.height();

        int nextLeft = Math.max(0, Math.min(contentMarginLeft + deltaX, Math.max(0, ideWidth() - width)));
        int nextTop = Math.max(0, Math.min(contentMarginTop + deltaY, Math.max(0, ideHeight() - height)));

        contentMarginLeft = nextLeft;
        contentMarginRight = Math.max(0, ideWidth() - width - nextLeft);
        contentMarginTop = nextTop;
        contentMarginBottom = Math.max(0, ideHeight() - height - nextTop);
        clampPanelSizes();
    }

    private @Nullable ContentCornerHandle findContentCornerHandle(
            double mouseX,
            double mouseY
    ) {

        IdeArea contentArea = contentArea();
        for (PanelCorner corner : PanelCorner.values()) {
            if (isNearCorner(contentArea, corner, mouseX, mouseY)) {
                return new ContentCornerHandle(corner);
            }
        }
        return null;
    }

    private @Nullable ContentEdgeHandle findContentEdgeHandle(
            double mouseX,
            double mouseY
    ) {

        IdeArea contentArea = contentArea();
        for (PanelEdge edge : PanelEdge.values()) {
            if (isNearEdge(contentArea, edge, mouseX, mouseY)) {
                return new ContentEdgeHandle(edge);
            }
        }
        return null;
    }

    private @Nullable CornerHandle findCornerHandle(
            double mouseX,
            double mouseY
    ) {

        List<PlaygroundPanel> candidates = new ArrayList<>();
        candidates.add(focusedPanel);
        for (PlaygroundPanel panel : visiblePanels()) {
            if (!candidates.contains(panel)) {
                candidates.add(panel);
            }
        }

        for (PlaygroundPanel panel : candidates) {
            IdeArea area = currentLayout.get(panel);
            if (area == null || area.isEmpty()) {
                continue;
            }
            for (PanelCorner corner : PanelCorner.values()) {
                if (!panelSupportsCorner(panel, corner)) {
                    continue;
                }
                if (isNearCorner(area, corner, mouseX, mouseY)) {
                    return new CornerHandle(panel, corner);
                }
            }
        }
        return null;
    }

    private @Nullable EdgeHandle findEdgeHandle(
            double mouseX,
            double mouseY
    ) {

        List<PlaygroundPanel> candidates = new ArrayList<>();
        candidates.add(focusedPanel);
        for (PlaygroundPanel panel : visiblePanels()) {
            if (!candidates.contains(panel)) {
                candidates.add(panel);
            }
        }

        for (PlaygroundPanel panel : candidates) {
            IdeArea area = currentLayout.get(panel);
            if (area == null || area.isEmpty()) {
                continue;
            }
            for (PanelEdge edge : PanelEdge.values()) {
                if (!panelSupportsEdge(panel, edge)) {
                    continue;
                }
                if (isNearEdge(area, edge, mouseX, mouseY)) {
                    return new EdgeHandle(panel, edge);
                }
            }
        }
        return null;
    }

    private boolean isNearEdge(
            IdeArea area,
            PanelEdge edge,
            double mouseX,
            double mouseY
    ) {

        return switch (edge) {
            case LEFT -> mouseX >= area.x() && mouseX <= area.x() + EDGE_HIT_MARGIN && mouseY >= area.y() && mouseY < area.bottom();
            case RIGHT -> mouseX >= area.right() - EDGE_HIT_MARGIN && mouseX < area.right() && mouseY >= area.y() && mouseY < area.bottom();
            case UP -> mouseY >= area.y() && mouseY <= area.y() + EDGE_HIT_MARGIN && mouseX >= area.x() && mouseX < area.right();
            case DOWN -> mouseY >= area.bottom() - EDGE_HIT_MARGIN && mouseY < area.bottom() && mouseX >= area.x() && mouseX < area.right();
        };
    }

    private boolean isNearCorner(
            IdeArea area,
            PanelCorner corner,
            double mouseX,
            double mouseY
    ) {

        int left = switch (corner) {
            case TOP_LEFT, BOTTOM_LEFT -> area.x();
            case TOP_RIGHT, BOTTOM_RIGHT -> area.right() - EDGE_HIT_MARGIN;
        };
        int right = switch (corner) {
            case TOP_LEFT, BOTTOM_LEFT -> area.x() + EDGE_HIT_MARGIN;
            case TOP_RIGHT, BOTTOM_RIGHT -> area.right();
        };
        int top = switch (corner) {
            case TOP_LEFT, TOP_RIGHT -> area.y();
            case BOTTOM_LEFT, BOTTOM_RIGHT -> area.bottom() - EDGE_HIT_MARGIN;
        };
        int bottom = switch (corner) {
            case TOP_LEFT, TOP_RIGHT -> area.y() + EDGE_HIT_MARGIN;
            case BOTTOM_LEFT, BOTTOM_RIGHT -> area.bottom();
        };
        return mouseX >= left && mouseX < right && mouseY >= top && mouseY < bottom;
    }

    private void beginEdgeDrag(
            EdgeHandle handle,
            double mouseX,
            double mouseY
    ) {

        activeDragTransaction = new EdgeResizeDragTransaction(handle.panel(), handle.edge(), mouseX, mouseY);
    }

    private void beginCornerDrag(
            CornerHandle handle,
            double mouseX,
            double mouseY
    ) {

        activeDragTransaction = new PanelCornerResizeDragTransaction(handle.panel(), handle.corner(), mouseX, mouseY);
    }

    private void beginContentEdgeDrag(
            ContentEdgeHandle handle,
            double mouseX,
            double mouseY
    ) {

        activeDragTransaction = new ContentEdgeResizeDragTransaction(handle.edge(), mouseX, mouseY);
    }

    private void beginContentCornerDrag(
            ContentCornerHandle handle,
            double mouseX,
            double mouseY
    ) {

        activeDragTransaction = new ContentCornerResizeDragTransaction(handle.corner(), mouseX, mouseY);
    }

    private void resizePanelEdgeByDelta(
            PlaygroundPanel panel,
            PanelEdge edge,
            int deltaX,
            int deltaY
    ) {

        IdeDockDirection direction = panelState(panel).dockDirection();

        switch (panel) {
            case SHELL -> {
                if (direction == IdeDockDirection.LEFT && edge == PanelEdge.RIGHT) {
                    shellPanelWidth += deltaX;
                } else if (direction == IdeDockDirection.RIGHT && edge == PanelEdge.LEFT) {
                    shellPanelWidth -= deltaX;
                } else if (direction == IdeDockDirection.DOWN && edge == PanelEdge.UP) {
                    terminalPanelHeight -= deltaY;
                }
            }
            case LAYOUT -> {
                if (direction == IdeDockDirection.RIGHT && edge == PanelEdge.LEFT) {
                    layoutPanelWidth -= deltaX;
                } else if (direction == IdeDockDirection.LEFT && edge == PanelEdge.RIGHT) {
                    layoutPanelWidth += deltaX;
                } else if (direction == IdeDockDirection.DOWN && edge == PanelEdge.UP) {
                    terminalPanelHeight -= deltaY;
                }
            }
            case WORKSPACE -> {
                switch (edge) {
                    case LEFT -> shellPanelWidth += deltaX;
                    case RIGHT -> layoutPanelWidth -= deltaX;
                    case UP -> terminalPanelHeight += deltaY;
                    case DOWN -> terminalPanelHeight -= deltaY;
                    default -> {
                    }
                }
            }
            case TERMINAL -> {
                switch (direction) {
                    case DOWN -> {
                        switch (edge) {
                            case LEFT -> shellPanelWidth += deltaX;
                            case RIGHT -> layoutPanelWidth -= deltaX;
                            case UP -> terminalPanelHeight -= deltaY;
                            default -> {
                            }
                        }
                    }
                    case LEFT -> {
                        if (edge == PanelEdge.RIGHT) {
                            shellPanelWidth += deltaX;
                        }
                    }
                    case RIGHT -> {
                        if (edge == PanelEdge.LEFT) {
                            layoutPanelWidth -= deltaX;
                        }
                    }
                    case UP -> {
                        if (edge == PanelEdge.DOWN) {
                            terminalPanelHeight += deltaY;
                        }
                    }
                    case CENTER -> {
                    }
                }
            }
        }
        clampPanelSizes();
    }

    private String formatScale(float value) {

        return String.format(Locale.ROOT, "%.2fx", value);
    }

    private float getPanelScale(PlaygroundPanel panel) {

        return panelState(panel).scale();
    }

    private void setPanelScale(
            PlaygroundPanel panel,
            float scale
    ) {

        panelState(panel).setScale(scale);
    }

    private void resetPanelScale(PlaygroundPanel panel) {

        panelState(panel).setScale(1.0F);
    }

    private int ideWidth() {

        return Math.max(1, Math.round(width / ideGlobalScale));
    }

    private int ideHeight() {

        return Math.max(1, Math.round(height / ideGlobalScale));
    }

    private PanelInstanceState panelState(PlaygroundPanel panel) {

        return panelInstances.computeIfAbsent(
                panel,
                it -> new PanelInstanceState(it, true, defaultDirection(it))
        );
    }

    private void setPanelVisible(
            PlaygroundPanel panel,
            boolean visible
    ) {

        panelState(panel).setVisible(visible);
    }

    private int ideCoordinate(int actualCoordinate) {

        return Math.round(actualCoordinate / ideGlobalScale);
    }

    private double ideCoordinate(double actualCoordinate) {

        return actualCoordinate / ideGlobalScale;
    }

    private boolean isScaleTypedCharacter(char codePoint) {

        if (!hasAltDown() && !hasControlDown()) {
            return false;
        }
        return codePoint == '+' || codePoint == '-' || codePoint == '=' || codePoint == '0';
    }

    private void beginPanelDrag(
            PlaygroundPanel panel,
            double mouseX,
            double mouseY
    ) {

        activeDragTransaction = panel == PlaygroundPanel.WORKSPACE
                    ? new ContentMoveDragTransaction(mouseX, mouseY)
                    : new PanelDockDragTransaction(panel, mouseX, mouseY);
    }

    private void releaseActiveDragTransaction(
            double mouseX,
            double mouseY
    ) {

        if (activeDragTransaction == null) {
            return;
        }
        activeDragTransaction.release(mouseX, mouseY);
        activeDragTransaction = null;
    }

    private void cancelActiveDragTransaction() {

        if (activeDragTransaction == null) {
            return;
        }
        activeDragTransaction.cancel();
        activeDragTransaction = null;
    }

    private IdeDockDirection getDockDropDirection(
            double mouseX,
            double mouseY
    ) {

        IdeArea contentArea = contentArea();
        int contentX = contentArea.x();
        int contentY = contentArea.y();
        int contentWidth = Math.max(1, contentArea.width());
        int contentHeight = Math.max(1, contentArea.height());
        double relativeX = (mouseX - contentX) / contentWidth;
        double relativeY = (mouseY - contentY) / contentHeight;

        if (relativeX < 0.2D) {
            return IdeDockDirection.LEFT;
        }
        if (relativeX > 0.8D) {
            return IdeDockDirection.RIGHT;
        }
        if (relativeY > 0.7D) {
            return IdeDockDirection.DOWN;
        }
        if (relativeY < 0.2D) {
            return IdeDockDirection.UP;
        }
        return IdeDockDirection.CENTER;
    }

    private void assignPanelToDirection(
            PlaygroundPanel panel,
            IdeDockDirection direction
    ) {

        if (panel == PlaygroundPanel.WORKSPACE || direction == IdeDockDirection.CENTER) {
            return;
        }

        PlaygroundPanel occupyingPanel = null;
        for (PlaygroundPanel candidate : PlaygroundPanel.values()) {
            if (candidate == panel || candidate == PlaygroundPanel.WORKSPACE) {
                continue;
            }
            if (panelState(candidate).dockDirection() == direction) {
                occupyingPanel = candidate;
                break;
            }
        }

        IdeDockDirection previousDirection = panelState(panel).dockDirection();
        panelState(panel).setDockDirection(direction);
        if (occupyingPanel != null) {
            panelState(occupyingPanel).setDockDirection(previousDirection);
        }
        focusedEdge = null;
        clampPanelSizes();
    }

    private @Nullable IdeActionDefinition resolveTerminalCommand(String rawCommand) {

        String normalized = rawCommand.trim().toLowerCase(Locale.ROOT);
        if (normalized.isBlank()) {
            return null;
        }

        @Nullable ResourceLocation actionId = SFMResourceLocation.tryParse(normalized);
        if (actionId != null) {
            @Nullable IdeActionDefinition exact = SFMIdePlaygroundActions.registry().get(actionId);
            if (exact != null) {
                return exact;
            }
        }

        if (!normalized.contains(":")) {
            return SFMIdePlaygroundActions.registry().get(SFMResourceLocation.tryParse("sfm:"+normalized));
        }

        return null;
    }

    private void toggleCommandPalette() {

        if (commandPaletteVisible) {
            closeCommandPalette();
        } else {
            openCommandPalette();
        }
    }

    private void openCommandPalette() {

        commandPaletteVisible = true;
        refreshCommandPaletteResults(commandPaletteInput == null ? "" : commandPaletteInput.getValue());
        updateCommandPaletteBounds();
        if (commandPaletteInput != null) {
            commandPaletteInput.setValue("");
            commandPaletteInput.setFocus(true);
            commandPaletteInput.moveCursorToEnd();
        }
        if (terminalInput != null) {
            terminalInput.setFocus(false);
        }
        setFocused(commandPaletteInput);
    }

    private void closeCommandPalette() {

        commandPaletteVisible = false;
        if (commandPaletteInput != null) {
            commandPaletteInput.setFocus(false);
        }
        refreshCommandPaletteResults("");
        focusPanel(focusedPanel);
    }

    @SuppressWarnings("RedundantIfStatement")
    private boolean handleCommandPaletteKeyPressed(
            int keyCode,
            int scanCode,
            int modifiers
    ) {

        if (hasAltDown() && hasControlDown() && handleCommandPaletteMoveKey(keyCode)) {
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            closeCommandPalette();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_UP) {
            moveCommandPaletteSelection(-1);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_DOWN) {
            moveCommandPaletteSelection(1);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_TAB) {
            fillCommandPaletteWithSelection();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            executeSelectedPaletteAction();
            return true;
        }
        if (commandPaletteInput != null && commandPaletteInput.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        return false;
    }

    private boolean handleCommandPaletteClick(
            double mouseX,
            double mouseY,
            int button
    ) {

        if (button != 0) {
            return false;
        }
        IdeArea area = getCommandPaletteArea();
        if (!area.contains(mouseX, mouseY)) {
            return false;
        }
        if (hasAltDown()) {
            commandPaletteDragging = true;
            commandPaletteDragOffsetX = (int) Math.round(mouseX) - area.x();
            commandPaletteDragOffsetY = (int) Math.round(mouseY) - area.y();
            return true;
        }
        if (commandPaletteInput != null && commandPaletteInput.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }

        assert commandPaletteInput != null;
        int resultsTop = commandPaletteInput.y + COMMAND_PALETTE_INPUT_HEIGHT + 8;
        for (int i = 0; i < filteredPaletteActions.size() && i < COMMAND_PALETTE_MAX_RESULTS; i++) {
            int rowTop = resultsTop + i * COMMAND_PALETTE_ROW_HEIGHT;
            if (mouseY >= rowTop - 2 && mouseY <= rowTop + COMMAND_PALETTE_ROW_HEIGHT - 4) {
                selectedPaletteActionIndex = i;
                executeSelectedPaletteAction();
                return true;
            }
        }
        return true;
    }

    private void moveCommandPaletteSelection(int direction) {

        if (filteredPaletteActions.isEmpty()) {
            selectedPaletteActionIndex = 0;
            return;
        }
        selectedPaletteActionIndex = Math.floorMod(
                selectedPaletteActionIndex + direction,
                filteredPaletteActions.size()
        );
    }

    private void fillCommandPaletteWithSelection() {

        if (commandPaletteInput == null || filteredPaletteActions.isEmpty()) {
            return;
        }
        commandPaletteInput.setValue(filteredPaletteActions.get(selectedPaletteActionIndex).id().toString());
        commandPaletteInput.moveCursorToEnd();
    }

    private void executeSelectedPaletteAction() {

        if (filteredPaletteActions.isEmpty()) {
            return;
        }
        IdeActionDefinition action = filteredPaletteActions.get(selectedPaletteActionIndex);
        closeCommandPalette();
        dispatchAction(action);
    }

    private void refreshCommandPaletteResults(String query) {

        String normalized = query.trim().toLowerCase(Locale.ROOT);

        List<IdeActionDefinition> definitions = SFMIdePlaygroundActions.registry().stream().toList();
        if (normalized.isBlank()) {
            ArrayList<IdeActionDefinition> results = new ArrayList<>(recentActions);
            for (IdeActionDefinition definition : definitions) {
                if (!results.contains(definition)) {
                    results.add(definition);
                }
            }
            filteredPaletteActions = results.stream().limit(COMMAND_PALETTE_MAX_RESULTS).toList();
            selectedPaletteActionIndex = Math.min(
                    selectedPaletteActionIndex,
                    Math.max(0, filteredPaletteActions.size() - 1)
            );
            return;
        }

        filteredPaletteActions = definitions.stream()
                .filter(definition -> matchesCommandPalette(definition, normalized))
                .sorted((left, right) -> Integer.compare(
                        commandPaletteScore(left, normalized),
                        commandPaletteScore(right, normalized)
                ))
                .limit(COMMAND_PALETTE_MAX_RESULTS)
                .toList();
        selectedPaletteActionIndex = 0;
    }

    private boolean matchesCommandPalette(
            IdeActionDefinition definition,
            String query
    ) {

        return commandPaletteScore(definition, query) < Integer.MAX_VALUE;
    }

    private int commandPaletteScore(
            IdeActionDefinition definition,
            String query
    ) {

        String id = definition.id().toString().toLowerCase(Locale.ROOT);
        String title = definition.title().getString().toLowerCase(Locale.ROOT);

        if (id.equals(query)) {
            return 0;
        }
        if (title.equals(query)) {
            return 1;
        }
        if (id.startsWith(query)) {
            return 10;
        }
        if (title.startsWith(query)) {
            return 20;
        }
        if (id.contains(query)) {
            return 40;
        }
        if (title.contains(query)) {
            return 50;
        }
        return Integer.MAX_VALUE;
    }

    private void rememberRecentAction(@UnknownNullability IdeActionDefinition actionId) {

        recentActions.remove(actionId);
        recentActions.add(0, actionId);
        while (recentActions.size() > COMMAND_PALETTE_MAX_RESULTS) {
            recentActions.remove(recentActions.size() - 1);
        }
    }

    private boolean handleCommandPaletteMoveKey(int keyCode) {

        return switch (keyCode) {
            case GLFW.GLFW_KEY_LEFT -> moveCommandPaletteBy(-COMMAND_PALETTE_MOVE_STEP, 0);
            case GLFW.GLFW_KEY_RIGHT -> moveCommandPaletteBy(COMMAND_PALETTE_MOVE_STEP, 0);
            case GLFW.GLFW_KEY_UP -> moveCommandPaletteBy(0, -COMMAND_PALETTE_MOVE_STEP);
            case GLFW.GLFW_KEY_DOWN -> moveCommandPaletteBy(0, COMMAND_PALETTE_MOVE_STEP);
            default -> false;
        };
    }

    private boolean moveCommandPaletteBy(
            int deltaX,
            int deltaY
    ) {

        IdeArea area = getCommandPaletteArea();
        setCommandPalettePosition(area.x() + deltaX, area.y() + deltaY);
        updateCommandPaletteBounds();
        return true;
    }

    private void setCommandPalettePosition(
            int x,
            int y
    ) {

        IdeArea currentArea = getCommandPaletteArea();
        commandPaletteX = clampCommandPaletteX(x, currentArea.width());
        commandPaletteY = clampCommandPaletteY(y, currentArea.height());
    }

    private int clampCommandPaletteX(
            int x,
            int paletteWidth
    ) {

        int minX = MARGIN;
        int maxX = Math.max(minX, ideWidth() - MARGIN - paletteWidth);
        return Math.max(minX, Math.min(x, maxX));
    }

    private int clampCommandPaletteY(
            int y,
            int paletteHeight
    ) {

        int minY = MARGIN;
        int maxY = Math.max(minY, ideHeight() - MARGIN - paletteHeight);
        return Math.max(minY, Math.min(y, maxY));
    }

    private LayoutSnapshot captureLayoutSnapshot() {

        return new LayoutSnapshot(
                shellPanelWidth,
                layoutPanelWidth,
                terminalPanelHeight,
                contentMarginLeft,
                contentMarginTop,
                contentMarginRight,
                contentMarginBottom,
                panelState(PlaygroundPanel.SHELL).dockDirection(),
                panelState(PlaygroundPanel.LAYOUT).dockDirection(),
                panelState(PlaygroundPanel.TERMINAL).dockDirection()
        );
    }

    private void restoreLayoutSnapshot(LayoutSnapshot snapshot) {

        shellPanelWidth = snapshot.shellPanelWidth();
        layoutPanelWidth = snapshot.layoutPanelWidth();
        terminalPanelHeight = snapshot.terminalPanelHeight();
        contentMarginLeft = snapshot.contentMarginLeft();
        contentMarginTop = snapshot.contentMarginTop();
        contentMarginRight = snapshot.contentMarginRight();
        contentMarginBottom = snapshot.contentMarginBottom();
        panelState(PlaygroundPanel.SHELL).setDockDirection(snapshot.shellDockDirection());
        panelState(PlaygroundPanel.LAYOUT).setDockDirection(snapshot.layoutDockDirection());
        panelState(PlaygroundPanel.TERMINAL).setDockDirection(snapshot.terminalDockDirection());
        clampPanelSizes();
    }

    private abstract class DragTransaction {
        private double lastMouseX;

        private double lastMouseY;

        private final @Nullable LayoutSnapshot cancelSnapshot;

        protected DragTransaction(
                boolean restoreLayoutOnCancel,
                double lastMouseX,
                double lastMouseY
        ) {

            this.cancelSnapshot = restoreLayoutOnCancel ? captureLayoutSnapshot() : null;
            this.lastMouseX = lastMouseX;
            this.lastMouseY = lastMouseY;
        }

        public final void drag(
                double mouseX,
                double mouseY
        ) {

            dragDelta(mouseX - lastMouseX, mouseY - lastMouseY, mouseX, mouseY);
            lastMouseX = mouseX;
            lastMouseY = mouseY;
        }

        protected abstract void dragDelta(
                double deltaX,
                double deltaY,
                double mouseX,
                double mouseY
        );

        public abstract void release(
                double mouseX,
                double mouseY
        );

        public void cancel() {

            if (cancelSnapshot != null) {
                restoreLayoutSnapshot(cancelSnapshot);
            }
        }
    }

    private final class EdgeResizeDragTransaction extends DragTransaction {
        private final PlaygroundPanel panel;

        private final PanelEdge edge;

        private EdgeResizeDragTransaction(
                PlaygroundPanel panel,
                PanelEdge edge,
                double lastMouseX,
                double lastMouseY
        ) {

            super(true, lastMouseX, lastMouseY);
            this.panel = panel;
            this.edge = edge;
        }

        @Override
        protected void dragDelta(
                double deltaX,
                double deltaY,
                double mouseX,
                double mouseY
        ) {

            resizePanelEdgeByDelta(panel, edge, (int) Math.round(deltaX), (int) Math.round(deltaY));
        }

        @Override
        public void release(
                double mouseX,
                double mouseY
        ) {

        }
    }

    private final class PanelCornerResizeDragTransaction extends DragTransaction {
        private final PlaygroundPanel panel;

        private final PanelCorner corner;

        private PanelCornerResizeDragTransaction(
                PlaygroundPanel panel,
                PanelCorner corner,
                double lastMouseX,
                double lastMouseY
        ) {

            super(true, lastMouseX, lastMouseY);
            this.panel = panel;
            this.corner = corner;
        }

        @Override
        protected void dragDelta(
                double deltaX,
                double deltaY,
                double mouseX,
                double mouseY
        ) {

            if (panelSupportsEdge(panel, corner.horizontalEdge())) {
                resizePanelEdgeByDelta(panel, corner.horizontalEdge(), (int) Math.round(deltaX), 0);
            }
            if (panelSupportsEdge(panel, corner.verticalEdge())) {
                resizePanelEdgeByDelta(panel, corner.verticalEdge(), 0, (int) Math.round(deltaY));
            }
        }

        @Override
        public void release(
                double mouseX,
                double mouseY
        ) {

        }
    }

    private final class ContentEdgeResizeDragTransaction extends DragTransaction {
        private final PanelEdge edge;

        private ContentEdgeResizeDragTransaction(
                PanelEdge edge,
                double lastMouseX,
                double lastMouseY
        ) {

            super(true, lastMouseX, lastMouseY);
            this.edge = edge;
        }

        @Override
        protected void dragDelta(
                double deltaX,
                double deltaY,
                double mouseX,
                double mouseY
        ) {

            resizeContentEdgeByDelta(edge, (int) Math.round(deltaX), (int) Math.round(deltaY));
        }

        @Override
        public void release(
                double mouseX,
                double mouseY
        ) {

        }
    }

    private final class ContentCornerResizeDragTransaction extends DragTransaction {
        private final PanelCorner corner;

        private ContentCornerResizeDragTransaction(
                PanelCorner corner,
                double lastMouseX,
                double lastMouseY
        ) {

            super(true, lastMouseX, lastMouseY);
            this.corner = corner;
        }

        @Override
        protected void dragDelta(
                double deltaX,
                double deltaY,
                double mouseX,
                double mouseY
        ) {

            resizeContentEdgeByDelta(corner.horizontalEdge(), (int) Math.round(deltaX), 0);
            resizeContentEdgeByDelta(corner.verticalEdge(), 0, (int) Math.round(deltaY));
        }

        @Override
        public void release(
                double mouseX,
                double mouseY
        ) {

        }
    }

    private final class ContentMoveDragTransaction extends DragTransaction {
        private ContentMoveDragTransaction(
                double lastMouseX,
                double lastMouseY
        ) {

            super(true, lastMouseX, lastMouseY);
        }

        @Override
        protected void dragDelta(
                double deltaX,
                double deltaY,
                double mouseX,
                double mouseY
        ) {

            translateContentArea((int) Math.round(deltaX), (int) Math.round(deltaY));
        }

        @Override
        public void release(
                double mouseX,
                double mouseY
        ) {

        }
    }

    private final class PanelDockDragTransaction extends DragTransaction {
        private final PlaygroundPanel panel;

        private @Nullable IdeDockDirection previewDirection;

        private PanelDockDragTransaction(
                PlaygroundPanel panel,
                double lastMouseX,
                double lastMouseY
        ) {

            super(true, lastMouseX, lastMouseY);
            this.panel = panel;
            this.previewDirection = panelState(panel).dockDirection();
        }

        public PlaygroundPanel panel() {

            return panel;
        }

        public @Nullable IdeDockDirection previewDirection() {

            return previewDirection;
        }

        @Override
        protected void dragDelta(
                double deltaX,
                double deltaY,
                double mouseX,
                double mouseY
        ) {

            previewDirection = getDockDropDirection(mouseX, mouseY);
        }

        @Override
        public void release(
                double mouseX,
                double mouseY
        ) {

            IdeDockDirection dropDirection = getDockDropDirection(mouseX, mouseY);
            previewDirection = dropDirection;
            if (dropDirection != IdeDockDirection.CENTER) {
                assignPanelToDirection(panel, dropDirection);
            }
        }

        @Override
        public void cancel() {

            super.cancel();
            previewDirection = null;
        }
    }

    private record LayoutSnapshot(
            int shellPanelWidth,
            int layoutPanelWidth,
            int terminalPanelHeight,
            int contentMarginLeft,
            int contentMarginTop,
            int contentMarginRight,
            int contentMarginBottom,
            IdeDockDirection shellDockDirection,
            IdeDockDirection layoutDockDirection,
            IdeDockDirection terminalDockDirection
    ) {
    }

    private static final class PanelInstanceState {
        private final PlaygroundPanel panelType;

        private final ResourceLocation instanceId;

        private boolean visible;

        private IdeDockDirection dockDirection;

        private float scale = 1.0F;

        private PanelInstanceState(
                PlaygroundPanel panelType,
                boolean visible,
                IdeDockDirection dockDirection
        ) {

            this.panelType = panelType;
            this.instanceId = panelType.persistentId();
            this.visible = visible;
            this.dockDirection = dockDirection;
        }

        public PlaygroundPanel panelType() {

            return panelType;
        }

        public ResourceLocation instanceId() {

            return instanceId;
        }

        public boolean visible() {

            return visible;
        }

        public void setVisible(boolean visible) {

            this.visible = visible;
        }

        public IdeDockDirection dockDirection() {

            return dockDirection;
        }

        public void setDockDirection(IdeDockDirection dockDirection) {

            this.dockDirection = dockDirection;
        }

        public float scale() {

            return scale;
        }

        public void setScale(float scale) {

            this.scale = scale;
        }
    }

    private enum PlaygroundPanel {
        SHELL,
        WORKSPACE,
        LAYOUT,
        TERMINAL;

        public ResourceLocation persistentId() {

            return switch (this) {
                case SHELL -> SFMResourceLocation.fromSFMPath("ide_playground/panel/shell");
                case WORKSPACE -> SFMResourceLocation.fromSFMPath("ide_playground/panel/workspace");
                case LAYOUT -> SFMResourceLocation.fromSFMPath("ide_playground/panel/layout");
                case TERMINAL -> SFMResourceLocation.fromSFMPath("ide_playground/panel/terminal");
            };
        }

        public Component display() {

            return switch (this) {
                case SHELL -> IdeLocalizationKeys.IDE_PLAYGROUND_PANEL_SHELL.getComponent();
                case WORKSPACE -> IdeLocalizationKeys.IDE_PLAYGROUND_PANEL_WORKSPACE.getComponent();
                case LAYOUT -> IdeLocalizationKeys.IDE_PLAYGROUND_PANEL_LAYOUT.getComponent();
                case TERMINAL -> IdeLocalizationKeys.IDE_PLAYGROUND_PANEL_TERMINAL.getComponent();
            };
        }
    }

    private enum PanelEdge {
        LEFT,
        RIGHT,
        UP,
        DOWN;

        public static @Nullable PanelEdge fromDirection(
                int xDirection,
                int yDirection
        ) {

            if (xDirection < 0) {
                return LEFT;
            }
            if (xDirection > 0) {
                return RIGHT;
            }
            if (yDirection < 0) {
                return UP;
            }
            if (yDirection > 0) {
                return DOWN;
            }
            return null;
        }

        public PanelEdge opposite() {

            return switch (this) {
                case LEFT -> RIGHT;
                case RIGHT -> LEFT;
                case UP -> DOWN;
                case DOWN -> UP;
            };
        }
    }

    private enum PanelCorner {
        TOP_LEFT(PanelEdge.LEFT, PanelEdge.UP),
        TOP_RIGHT(PanelEdge.RIGHT, PanelEdge.UP),
        BOTTOM_LEFT(PanelEdge.LEFT, PanelEdge.DOWN),
        BOTTOM_RIGHT(PanelEdge.RIGHT, PanelEdge.DOWN);

        private final PanelEdge horizontalEdge;

        private final PanelEdge verticalEdge;

        PanelCorner(
                PanelEdge horizontalEdge,
                PanelEdge verticalEdge
        ) {

            this.horizontalEdge = horizontalEdge;
            this.verticalEdge = verticalEdge;
        }

        public PanelEdge horizontalEdge() {

            return horizontalEdge;
        }

        public PanelEdge verticalEdge() {

            return verticalEdge;
        }

        public PanelEdge primaryEdge() {

            return horizontalEdge;
        }
    }

    private record EdgeHandle(
            PlaygroundPanel panel,
            PanelEdge edge
    ) {
    }

    private record CornerHandle(
            PlaygroundPanel panel,
            PanelCorner corner
    ) {
    }

    private record ContentEdgeHandle(PanelEdge edge) {
    }

    private record ContentCornerHandle(PanelCorner corner) {
    }

}
