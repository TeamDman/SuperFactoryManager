package ca.teamdman.sfm.client.screen;

import ca.teamdman.sfm.client.ide.action.ManagerIdeActionRegistry;
import ca.teamdman.sfm.client.registry.SFMKeyMappings;
import ca.teamdman.sfm.client.text_styling.ProgramSyntaxHighlightingHelper;
import ca.teamdman.sfm.client.widget.VirtualScrollViewport;
import ca.teamdman.sfm.common.containermenu.ManagerContainerMenu;
import ca.teamdman.sfm.common.item.DiskItem;
import ca.teamdman.sfm.common.localization.IdeLocalizationKeys;
import ca.teamdman.sfm.common.net.ServerboundManagerIdeExplorerDropPacket;
import ca.teamdman.sfm.common.net.ServerboundManagerIdeTerminalCommandPacket;
import ca.teamdman.sfm.common.registry.registration.SFMPackets;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public class ManagerIdeScreen extends Screen {
    private static final int DEFAULT_RIGHT_PANEL_WIDTH = 180;
    private static final int DEFAULT_BOTTOM_PANEL_HEIGHT = 120;
    private static final int MIN_RIGHT_PANEL_WIDTH = 120;
    private static final int MIN_BOTTOM_PANEL_HEIGHT = 72;
    private static final int MIN_REMAINING_MAIN_AREA_WIDTH = 120;
    private static final int MIN_REMAINING_MAIN_AREA_HEIGHT = 120;
    private static final int RESIZE_HANDLE_THICKNESS = 4;
    private static final int CENTER_TAB_HEIGHT = 20;
    private static final int CENTER_PANEL_WIDTH = 176;
    private static final int CENTER_PANEL_HEIGHT = 166;

    private final ManagerContainerMenu menu;
    private final Inventory playerInventory;

    private boolean rightPanelVisible = true;
    private boolean bottomPanelVisible = true;
    private boolean managerExpanded = true;
    private boolean playerExpanded = true;
    private int rightPanelWidth = DEFAULT_RIGHT_PANEL_WIDTH;
    private int bottomPanelHeight = DEFAULT_BOTTOM_PANEL_HEIGHT;

    private PanelFocus focusedPanel = PanelFocus.CENTER;
    private CenterTab activeCenterTab = CenterTab.INVENTORY;

    private Integer selectedExplorerRow = 0;
    private Integer draggingSourceMenuSlot;
    private Integer hoveredExplorerMenuSlot;
    private Integer clickedMenuSlot;
    private Integer hoveredCenterMenuSlot;
    private Integer diskPreviewMenuSlot;
    private Integer hoveredDiskPreviewMenuSlot;

    private String diskPreviewProgram = "";
    private List<MutableComponent> diskPreviewLines = new ArrayList<>();

    private EditBox terminalInput;
    private CommandSuggestions terminalCommandSuggestions;

    private VirtualScrollViewport explorerScroll;
    private VirtualScrollViewport diskPreviewScroll;

    private boolean explorerScrollDragging;
    private int explorerScrollDragStartMouseY;
    private double explorerScrollDragStartOffset;
    private ResizeEdge activeResizeEdge = ResizeEdge.NONE;
    private int resizeDragStartMouseX;
    private int resizeDragStartMouseY;
    private int resizeDragStartRightPanelWidth;
    private int resizeDragStartBottomPanelHeight;
    private boolean hoveredRightResizeHandle;
    private boolean hoveredBottomResizeHandle;
    private boolean hoveredCornerResizeHandle;
    private long horizontalResizeCursorHandle;
    private long verticalResizeCursorHandle;
    private long diagonalResizeCursorHandle;
    private ResizeCursor activeResizeCursor = ResizeCursor.DEFAULT;

    public ManagerIdeScreen(ManagerContainerMenu menu, Inventory inv, Component title) {
        super(title);
        this.menu = menu;
        this.playerInventory = inv;
    }

    public ManagerContainerMenu getMenu() {
        return menu;
    }

    @Override
    protected void init() {
        super.init();
        clampPanelSizesToScreen();
        initResizeCursors();

        int rowHeight = this.font.lineHeight + 2;
        this.explorerScroll = new VirtualScrollViewport(rowHeight);
        this.diskPreviewScroll = new VirtualScrollViewport(rowHeight);

        this.terminalInput = this.addRenderableWidget(new EditBox(
                this.font,
                10,
                this.height - 24,
                this.width - 20,
                16,
                IdeLocalizationKeys.IDE_TERMINAL_INPUT_PLACEHOLDER.getComponent()
        ));
        this.terminalInput.setMaxLength(ServerboundManagerIdeTerminalCommandPacket.MAX_COMMAND_LENGTH);
        this.terminalInput.setResponder(this::onTerminalInputEdited);
        this.terminalCommandSuggestions = new CommandSuggestions(
                this.minecraft,
                this,
                this.terminalInput,
                this.font,
                false,
                false,
                1,
                10,
                true,
                0xD0000000
        );
        this.terminalCommandSuggestions.setAllowSuggestions(false);
        updateTerminalInputBounds();
    }

    @Override
    public void onClose() {
        releaseResizeCursors();
        if (this.minecraft != null && this.minecraft.player != null) {
            this.minecraft.player.closeContainer();
        }
        super.onClose();
    }

    private void onTerminalInputEdited(String value) {
        if (terminalCommandSuggestions == null) {
            return;
        }
        terminalCommandSuggestions.setAllowSuggestions(value.startsWith("/"));
        terminalCommandSuggestions.updateCommandInfo();
    }

    public boolean executeIdeAction(String actionId) {
        boolean executed = ManagerIdeActionRegistry.run(actionId, this);
        if (executed) {
            updateTerminalInputBounds();
        }
        return executed;
    }

    public void toggleRightPanel() {
        setRightPanelVisible(!rightPanelVisible);
    }

    public void toggleBottomPanel() {
        setBottomPanelVisible(!bottomPanelVisible);
    }

    public void setRightPanelVisible(boolean visible) {
        this.rightPanelVisible = visible;
    }

    public void setBottomPanelVisible(boolean visible) {
        this.bottomPanelVisible = visible;
        updateTerminalInputBounds();
    }

    public void focusExplorerPanel() {
        focusedPanel = PanelFocus.EXPLORER;
        if (selectedExplorerRow == null) {
            selectedExplorerRow = 0;
        }
    }

    private void focusTerminalPanel() {
        focusedPanel = PanelFocus.TERMINAL;
        if (terminalInput != null && terminalInput.visible) {
            terminalInput.setFocus(true);
            setFocused(terminalInput);
        }
    }

    private void focusCenterPanel() {
        focusedPanel = PanelFocus.CENTER;
        if (terminalInput != null) {
            terminalInput.setFocus(false);
        }
        setFocused(null);
    }

    private void cycleFocus(boolean reverse) {
        List<PanelFocus> visiblePanels = new ArrayList<>();
        visiblePanels.add(PanelFocus.CENTER);
        if (rightPanelVisible) {
            visiblePanels.add(PanelFocus.EXPLORER);
        }
        if (bottomPanelVisible) {
            visiblePanels.add(PanelFocus.TERMINAL);
        }
        int current = visiblePanels.indexOf(focusedPanel);
        if (current < 0) {
            current = 0;
        }
        int next = reverse
                   ? (current - 1 + visiblePanels.size()) % visiblePanels.size()
                   : (current + 1) % visiblePanels.size();
        PanelFocus nextFocus = visiblePanels.get(next);
        switch (nextFocus) {
            case CENTER -> focusCenterPanel();
            case EXPLORER -> focusExplorerPanel();
            case TERMINAL -> focusTerminalPanel();
        }
    }

    @Override
    public boolean keyPressed(int pKeyCode, int pScanCode, int pModifiers) {
        InputConstants.Key key = InputConstants.getKey(pKeyCode, pScanCode);

        if (bottomPanelVisible
            && terminalInput != null
            && terminalInput.visible
            && terminalInput.isFocused()
            && this.minecraft != null
            && this.minecraft.options.keyInventory.isActiveAndMatches(key)) {
            return true;
        }

        if (SFMKeyMappings.IDE_TOGGLE_RIGHT_PANEL_KEY.get().isActiveAndMatches(key)) {
            executeIdeAction("sfm:toggle_right_panel_visibility");
            return true;
        }
        if (SFMKeyMappings.IDE_TOGGLE_BOTTOM_PANEL_KEY.get().isActiveAndMatches(key)) {
            executeIdeAction("sfm:toggle_bottom_panel_visibility");
            return true;
        }
        if (SFMKeyMappings.IDE_FOCUS_EXPLORER_PANEL_KEY.get().isActiveAndMatches(key)) {
            executeIdeAction("sfm:focus_explorer_panel");
            return true;
        }

        if (pKeyCode == GLFW.GLFW_KEY_TAB) {
            cycleFocus(hasShiftDown());
            return true;
        }

        if (focusedPanel == PanelFocus.EXPLORER) {
            var rows = getExplorerRows();
            if (rows.isEmpty()) {
                return true;
            }
            if (selectedExplorerRow == null) {
                selectedExplorerRow = 0;
            }
            if (pKeyCode == GLFW.GLFW_KEY_DOWN) {
                selectedExplorerRow = Math.min(rows.size() - 1, selectedExplorerRow + 1);
                return true;
            }
            if (pKeyCode == GLFW.GLFW_KEY_UP) {
                selectedExplorerRow = Math.max(0, selectedExplorerRow - 1);
                return true;
            }
            if (pKeyCode == GLFW.GLFW_KEY_LEFT || pKeyCode == GLFW.GLFW_KEY_RIGHT || pKeyCode == GLFW.GLFW_KEY_ENTER || pKeyCode == GLFW.GLFW_KEY_SPACE) {
                ExplorerRow row = rows.get(selectedExplorerRow);
                if (row.kind == ExplorerRowKind.MANAGER_CONTAINER) {
                    managerExpanded = !managerExpanded;
                    selectedExplorerRow = Math.min(selectedExplorerRow, getExplorerRows().size() - 1);
                    return true;
                }
                if (row.kind == ExplorerRowKind.PLAYER_CONTAINER) {
                    playerExpanded = !playerExpanded;
                    selectedExplorerRow = Math.min(selectedExplorerRow, getExplorerRows().size() - 1);
                    return true;
                }
                if ((pKeyCode == GLFW.GLFW_KEY_ENTER || pKeyCode == GLFW.GLFW_KEY_SPACE) && row.kind.isSlotTarget()) {
                    openDiskPreviewForMenuSlot(row.menuSlot);
                    return true;
                }
            }
        }

        if (bottomPanelVisible && terminalInput != null) {
            if (terminalInput.isFocused() && terminalCommandSuggestions != null && terminalCommandSuggestions.keyPressed(pKeyCode, pScanCode, pModifiers)) {
                return true;
            }
            if (terminalInput.isFocused() && pKeyCode == GLFW.GLFW_KEY_ENTER) {
                submitTerminalCommand();
                return true;
            }
            if (terminalInput.keyPressed(pKeyCode, pScanCode, pModifiers)) {
                return true;
            }
        }

        return super.keyPressed(pKeyCode, pScanCode, pModifiers);
    }

    @Override
    public boolean charTyped(char pCodePoint, int pModifiers) {
        if (bottomPanelVisible && terminalInput != null && terminalInput.isFocused()) {
            return terminalInput.charTyped(pCodePoint, pModifiers);
        }
        return super.charTyped(pCodePoint, pModifiers);
    }

    @Override
    public boolean mouseClicked(double pMouseX, double pMouseY, int pButton) {
        if (bottomPanelVisible
            && terminalInput != null
            && terminalInput.visible
            && terminalInput.isFocused()
            && terminalCommandSuggestions != null
            && terminalCommandSuggestions.mouseClicked((double) ((int) pMouseX), (double) ((int) pMouseY), pButton)) {
            return true;
        }

        if (bottomPanelVisible && terminalInput != null && terminalInput.visible && terminalInput.mouseClicked(pMouseX, pMouseY, pButton)) {
            focusTerminalPanel();
            return true;
        }

        if (pButton == 0 && handleCenterTabClick((int) pMouseX, (int) pMouseY)) {
            return true;
        }

        if (pButton == 0 && tryBeginResizeDrag((int) pMouseX, (int) pMouseY)) {
            return true;
        }

        if (rightPanelVisible && pButton == 0 && isInsideExplorerScrollbar((int) pMouseX, (int) pMouseY)) {
            explorerScrollDragging = true;
            explorerScrollDragStartMouseY = (int) pMouseY;
            explorerScrollDragStartOffset = explorerScroll.scrollOffset();
            return true;
        }

        if (rightPanelVisible && pButton == 0) {
            Integer rowIndex = getExplorerRowAt((int) pMouseX, (int) pMouseY);
            if (rowIndex != null) {
                var rows = getExplorerRows();
                if (rowIndex >= 0 && rowIndex < rows.size()) {
                    selectedExplorerRow = rowIndex;
                    focusExplorerPanel();
                    ExplorerRow row = rows.get(rowIndex);
                    if (row.kind == ExplorerRowKind.MANAGER_CONTAINER) {
                        managerExpanded = !managerExpanded;
                    } else if (row.kind == ExplorerRowKind.PLAYER_CONTAINER) {
                        playerExpanded = !playerExpanded;
                    } else if (row.kind.isSlotTarget()) {
                        draggingSourceMenuSlot = row.menuSlot;
                        clickedMenuSlot = row.menuSlot;
                        openDiskPreviewForMenuSlot(row.menuSlot);
                    }
                    return true;
                }
            }
        }

        if (activeCenterTab == CenterTab.INVENTORY && pButton == 0) {
            Integer slot = getCenterSlotAt((int) pMouseX, (int) pMouseY);
            if (slot != null) {
                clickedMenuSlot = slot;
                return true;
            }
        }

        if (activeCenterTab == CenterTab.TEXT_EDITOR && isInsideCenterContent((int) pMouseX, (int) pMouseY)) {
            focusCenterPanel();
            return true;
        }

        return super.mouseClicked(pMouseX, pMouseY, pButton);
    }

    @Override
    public boolean mouseDragged(double pMouseX, double pMouseY, int pButton, double pDragX, double pDragY) {
        if (pButton == 0 && activeResizeEdge != ResizeEdge.NONE) {
            if (activeResizeEdge == ResizeEdge.RIGHT_PANEL || activeResizeEdge == ResizeEdge.BOTH_PANELS) {
                int deltaX = (int) pMouseX - resizeDragStartMouseX;
                setRightPanelWidth(resizeDragStartRightPanelWidth - deltaX);
                hoveredRightResizeHandle = true;
            }
            if (activeResizeEdge == ResizeEdge.BOTTOM_PANEL || activeResizeEdge == ResizeEdge.BOTH_PANELS) {
                int deltaY = (int) pMouseY - resizeDragStartMouseY;
                setBottomPanelHeight(resizeDragStartBottomPanelHeight - deltaY);
                hoveredBottomResizeHandle = true;
            }
            if (activeResizeEdge == ResizeEdge.BOTH_PANELS) {
                hoveredCornerResizeHandle = true;
            }
            return true;
        }

        if (explorerScrollDragging && pButton == 0) {
            List<ExplorerRow> rows = getExplorerRows();
            int contentHeight = getExplorerContentHeight();
            int rowCount = rows.size();
            int maxOffset = explorerScroll.maxScrollOffset(rowCount, contentHeight);
            if (maxOffset <= 0) {
                return true;
            }

            int thumbHeight = getScrollbarThumbHeight(contentHeight, rowCount, explorerScroll);
            int travel = Math.max(1, contentHeight - thumbHeight);
            double pixelsMoved = pMouseY - explorerScrollDragStartMouseY;
            double contentOffsetDelta = (pixelsMoved / (double) travel) * maxOffset;
            explorerScroll.setScrollOffset(explorerScrollDragStartOffset + contentOffsetDelta, rowCount, contentHeight);
            return true;
        }

        return super.mouseDragged(pMouseX, pMouseY, pButton, pDragX, pDragY);
    }

    @Override
    public boolean mouseReleased(double pMouseX, double pMouseY, int pButton) {
        if (pButton == 0) {
            explorerScrollDragging = false;
            activeResizeEdge = ResizeEdge.NONE;
        }

        if (pButton == 0 && draggingSourceMenuSlot != null && rightPanelVisible) {
            Integer rowIndex = getExplorerRowAt((int) pMouseX, (int) pMouseY);
            if (rowIndex != null) {
                var rows = getExplorerRows();
                if (rowIndex >= 0 && rowIndex < rows.size()) {
                    ExplorerRow row = rows.get(rowIndex);
                    ServerboundManagerIdeExplorerDropPacket.TargetKind targetKind;
                    int targetSlot = -1;
                    if (row.kind.isSlotTarget()) {
                        targetKind = ServerboundManagerIdeExplorerDropPacket.TargetKind.SLOT;
                        targetSlot = row.menuSlot;
                    } else if (row.kind == ExplorerRowKind.MANAGER_CONTAINER) {
                        targetKind = ServerboundManagerIdeExplorerDropPacket.TargetKind.MANAGER_CONTAINER;
                    } else {
                        targetKind = ServerboundManagerIdeExplorerDropPacket.TargetKind.PLAYER_CONTAINER;
                    }
                    SFMPackets.sendToServer(new ServerboundManagerIdeExplorerDropPacket(
                            menu.containerId,
                            menu.MANAGER_POSITION,
                            draggingSourceMenuSlot,
                            targetKind,
                            targetSlot
                    ));
                }
            }
            draggingSourceMenuSlot = null;
            return true;
        }
        return super.mouseReleased(pMouseX, pMouseY, pButton);
    }

    @Override
    public boolean mouseScrolled(double pMouseX, double pMouseY, double pDelta) {
        if (bottomPanelVisible
            && terminalInput != null
            && terminalInput.visible
            && terminalInput.isFocused()
            && terminalCommandSuggestions != null
            && terminalCommandSuggestions.mouseScrolled(pDelta)) {
            return true;
        }

        if (rightPanelVisible && isInsideExplorerContent((int) pMouseX, (int) pMouseY)) {
            List<ExplorerRow> rows = getExplorerRows();
            explorerScroll.scrollByWheel(pDelta, rows.size(), getExplorerContentHeight());
            return true;
        }

        if (activeCenterTab == CenterTab.TEXT_EDITOR && isInsideTextEditorContent((int) pMouseX, (int) pMouseY)) {
            diskPreviewScroll.scrollByWheel(pDelta, diskPreviewLines.size(), getTextEditorContentHeight());
            return true;
        }

        return super.mouseScrolled(pMouseX, pMouseY, pDelta);
    }

    private void submitTerminalCommand() {
        if (terminalInput == null) {
            return;
        }
        String input = terminalInput.getValue().trim();
        if (input.isBlank()) {
            return;
        }
        String normalized = input.startsWith("/") ? input : "/sfm ide " + input;
        SFMPackets.sendToServer(new ServerboundManagerIdeTerminalCommandPacket(
                menu.containerId,
                menu.MANAGER_POSITION,
                normalized
        ));
        terminalInput.setValue("");
    }

    private boolean openDiskPreviewForMenuSlot(int menuSlot) {
        if (menuSlot < 0 || menuSlot >= menu.slots.size()) {
            return false;
        }
        ItemStack stack = menu.getSlot(menuSlot).getItem();
        if (!(stack.getItem() instanceof DiskItem)) {
            return false;
        }

        activeCenterTab = CenterTab.TEXT_EDITOR;
        diskPreviewMenuSlot = menuSlot;
        refreshDiskPreviewFromSlot();
        return true;
    }

    private void refreshDiskPreviewFromSlot() {
        if (diskPreviewMenuSlot == null || diskPreviewMenuSlot < 0 || diskPreviewMenuSlot >= menu.slots.size()) {
            return;
        }

        ItemStack stack = menu.getSlot(diskPreviewMenuSlot).getItem();
        if (!(stack.getItem() instanceof DiskItem)) {
            diskPreviewProgram = "";
            diskPreviewLines = new ArrayList<>();
            diskPreviewMenuSlot = null;
            diskPreviewScroll.reset();
            return;
        }

        String program = DiskItem.getProgramString(stack);
        if (!program.equals(diskPreviewProgram)) {
            diskPreviewProgram = program;
            diskPreviewLines = ProgramSyntaxHighlightingHelper.withSyntaxHighlighting(program, false);
            if (diskPreviewLines.isEmpty()) {
                diskPreviewLines = new ArrayList<>();
                diskPreviewLines.add(Component.empty());
            }
            diskPreviewScroll.reset();
        }
    }

    @Override
    public void render(PoseStack poseStack, int mx, int my, float partialTicks) {
        this.renderBackground(poseStack);
        clampPanelSizesToScreen();

        hoveredExplorerMenuSlot = null;
        hoveredDiskPreviewMenuSlot = null;
        hoveredCenterMenuSlot = null;
        hoveredCornerResizeHandle = isOverResizeCornerHandle(mx, my) || activeResizeEdge == ResizeEdge.BOTH_PANELS;
        hoveredRightResizeHandle = hoveredCornerResizeHandle
                                   || isOverRightResizeHandle(mx, my)
                                   || activeResizeEdge == ResizeEdge.RIGHT_PANEL;
        hoveredBottomResizeHandle = hoveredCornerResizeHandle
                                    || isOverBottomResizeHandle(mx, my)
                                    || activeResizeEdge == ResizeEdge.BOTTOM_PANEL;
        updateResizeCursor();

        drawCenterPanel(poseStack, mx, my);
        drawIdePanels(poseStack, mx, my);
        drawResizeHandles(poseStack);

        super.render(poseStack, mx, my, partialTicks);

        if (bottomPanelVisible && terminalInput != null && terminalInput.visible && terminalInput.isFocused() && terminalCommandSuggestions != null) {
            terminalCommandSuggestions.render(poseStack, mx, my);
        }

        if (draggingSourceMenuSlot != null) {
            drawString(poseStack, font, Component.literal("Dragging slot " + draggingSourceMenuSlot), mx + 8, my + 8, 0xFFFFFF);
        }

        if (hoveredCenterMenuSlot != null) {
            ItemStack stack = menu.getSlot(hoveredCenterMenuSlot).getItem();
            if (!stack.isEmpty()) {
                this.renderTooltip(poseStack, stack, mx, my);
            }
        }
        if (hoveredExplorerMenuSlot != null) {
            ItemStack stack = menu.getSlot(hoveredExplorerMenuSlot).getItem();
            if (!stack.isEmpty()) {
                this.renderTooltip(poseStack, stack, mx, my);
            }
        }
        if (hoveredDiskPreviewMenuSlot != null) {
            ItemStack stack = menu.getSlot(hoveredDiskPreviewMenuSlot).getItem();
            if (!stack.isEmpty()) {
                this.renderTooltip(poseStack, stack, mx, my);
            }
        }
    }

    private void drawIdePanels(PoseStack poseStack, int mx, int my) {
        if (rightPanelVisible) {
            drawExplorerPanel(poseStack, mx, my);
        }
        if (bottomPanelVisible) {
            drawTerminalPanel(poseStack);
        }
    }

    private void drawCenterPanel(PoseStack poseStack, int mx, int my) {
        int x = getCenterPanelX();
        int y = getCenterPanelY();
        int w = getCenterPanelWidth();

        fill(poseStack, x, y - CENTER_TAB_HEIGHT, x + w, y, 0xB0101010);

        drawCenterTabButton(
                poseStack,
                x + 4,
                y - CENTER_TAB_HEIGHT + 2,
                76,
                CENTER_TAB_HEIGHT - 4,
                IdeLocalizationKeys.IDE_CENTER_TAB_INVENTORY.getComponent(),
                activeCenterTab == CenterTab.INVENTORY
        );
        drawCenterTabButton(
                poseStack,
                x + 84,
                y - CENTER_TAB_HEIGHT + 2,
                92,
                CENTER_TAB_HEIGHT - 4,
                IdeLocalizationKeys.IDE_CENTER_TAB_TEXT_EDITOR.getComponent(),
                activeCenterTab == CenterTab.TEXT_EDITOR
        );

        if (activeCenterTab == CenterTab.INVENTORY) {
            drawCenterInventoryPanel(poseStack, mx, my);
        } else {
            drawTextEditorCenterPanel(poseStack, mx, my);
        }
    }

    private void drawCenterInventoryPanel(PoseStack poseStack, int mx, int my) {
        int x = getCenterPanelX();
        int y = getCenterPanelY();
        int w = getCenterPanelWidth();
        int h = getCenterPanelHeight();

        fill(poseStack, x, y, x + w, y + h, 0x55000000);
        drawString(poseStack, font, this.title, x + 8, y + 6, 0xFFFFFF);
        drawString(
                poseStack,
                font,
                IdeLocalizationKeys.IDE_PLAYER_INVENTORY_LABEL.getComponent(),
                x + 8,
                y + 74,
                0xB0B0B0
        );

        for (int i = 0; i < menu.slots.size(); i++) {
            Slot slot = menu.slots.get(i);
            int sx = x + slot.x;
            int sy = y + slot.y;

            fill(poseStack, sx - 1, sy - 1, sx + 17, sy + 17, 0x88303030);
            fill(poseStack, sx, sy, sx + 16, sy + 16, 0x88202020);

            if (clickedMenuSlot != null && clickedMenuSlot == i) {
                fill(poseStack, sx - 1, sy - 1, sx + 17, sy, 0xFF66CCFF);
                fill(poseStack, sx - 1, sy + 16, sx + 17, sy + 17, 0xFF66CCFF);
                fill(poseStack, sx - 1, sy, sx, sy + 16, 0xFF66CCFF);
                fill(poseStack, sx + 16, sy, sx + 17, sy + 16, 0xFF66CCFF);
            }

            ItemStack stack = slot.getItem();
            if (!stack.isEmpty()) {
                itemRenderer.renderAndDecorateItem(stack, sx, sy);
                itemRenderer.renderGuiItemDecorations(font, stack, sx, sy);
                if (mx >= sx && mx <= sx + 16 && my >= sy && my <= sy + 16) {
                    hoveredCenterMenuSlot = i;
                }
            }
        }
    }

    private void drawCenterTabButton(PoseStack poseStack, int x, int y, int w, int h, Component label, boolean active) {
        fill(poseStack, x, y, x + w, y + h, active ? 0xFF2A3A4A : 0xFF1A1A1A);
        drawString(poseStack, font, label, x + 6, y + 5, active ? 0xFFFFFF : 0xC0C0C0);
    }

    private void drawTextEditorCenterPanel(PoseStack poseStack, int mx, int my) {
        refreshDiskPreviewFromSlot();

        int x = getCenterPanelX();
        int y = getCenterPanelY();
        int w = getCenterPanelWidth();
        int h = getCenterPanelHeight();

        fill(poseStack, x, y, x + w, y + h, 0xEE080808);

        if (diskPreviewMenuSlot == null || diskPreviewMenuSlot < 0 || diskPreviewMenuSlot >= menu.slots.size()) {
            drawString(poseStack, font, IdeLocalizationKeys.IDE_CENTER_TEXT_EDITOR_EMPTY.getComponent(), x + 8, y + 8, 0xB0B0B0);
            return;
        }

        ItemStack stack = menu.getSlot(diskPreviewMenuSlot).getItem();
        if (!(stack.getItem() instanceof DiskItem)) {
            drawString(poseStack, font, IdeLocalizationKeys.IDE_CENTER_TEXT_EDITOR_EMPTY.getComponent(), x + 8, y + 8, 0xB0B0B0);
            return;
        }

        drawString(
                poseStack,
                font,
                IdeLocalizationKeys.IDE_CENTER_TEXT_EDITOR_TITLE.getComponent(diskPreviewMenuSlot),
                x + 8,
                y + 8,
                0xFFFFFF
        );

        int iconX = x + w - 22;
        int iconY = y + 6;
        this.itemRenderer.renderAndDecorateItem(stack, iconX, iconY);
        if (mx >= iconX && mx <= iconX + 16 && my >= iconY && my <= iconY + 16) {
            hoveredDiskPreviewMenuSlot = diskPreviewMenuSlot;
        }

        int contentX = x + 8;
        int contentY = y + 24;
        int contentW = w - 16;
        int contentH = h - 30;

        fill(poseStack, contentX - 2, contentY - 2, contentX + contentW, contentY + contentH, 0x55111111);

        int rowCount = diskPreviewLines.size();
        diskPreviewScroll.clamp(rowCount, contentH);

        int first = diskPreviewScroll.firstVisibleRow(rowCount, contentH);
        int last = diskPreviewScroll.lastVisibleRowExclusive(rowCount, contentH);

        for (int i = first; i < last; i++) {
            int rowY = diskPreviewScroll.rowTop(i, contentY);
            MutableComponent line = diskPreviewLines.get(i);
            drawString(poseStack, font, line, contentX, rowY, 0xFFFFFF);
        }

        drawSimpleScrollbar(
                poseStack,
                contentX + contentW - 3,
                contentY,
                contentH,
                rowCount,
                diskPreviewScroll
        );
    }

    private boolean handleCenterTabClick(int mouseX, int mouseY) {
        int x = getCenterPanelX();
        int y = getCenterPanelY() - CENTER_TAB_HEIGHT + 2;
        int h = CENTER_TAB_HEIGHT - 4;

        if (mouseY < y || mouseY > y + h) {
            return false;
        }

        if (mouseX >= x + 4 && mouseX <= x + 80) {
            activeCenterTab = CenterTab.INVENTORY;
            focusCenterPanel();
            return true;
        }
        if (mouseX >= x + 84 && mouseX <= x + 176) {
            activeCenterTab = CenterTab.TEXT_EDITOR;
            focusCenterPanel();
            return true;
        }
        return false;
    }

    private void drawExplorerPanel(PoseStack poseStack, int mx, int my) {
        int x = getRightPanelX();
        int y = getRightPanelY();
        int w = getRightPanelWidth();
        int h = getRightPanelHeight();

        fill(poseStack, x, y, x + w, y + h, 0xAA101010);
        drawString(poseStack, font, IdeLocalizationKeys.IDE_PANEL_EXPLORER_TITLE.getComponent(), x + 6, y + 6, 0xFFFFFF);

        List<ExplorerRow> rows = getExplorerRows();
        int contentTop = y + 22;
        int contentHeight = getExplorerContentHeight();

        explorerScroll.clamp(rows.size(), contentHeight);
        int first = explorerScroll.firstVisibleRow(rows.size(), contentHeight);
        int last = explorerScroll.lastVisibleRowExclusive(rows.size(), contentHeight);

        for (int i = first; i < last; i++) {
            ExplorerRow row = rows.get(i);
            int rowY = explorerScroll.rowTop(i, contentTop);
            int color = focusedPanel == PanelFocus.EXPLORER && selectedExplorerRow != null && selectedExplorerRow == i
                        ? 0xFF66CCFF
                        : 0xFFFFFFFF;
            String indent = row.depth == 0 ? "" : "  ";
            String prefix = switch (row.kind) {
                case MANAGER_CONTAINER -> managerExpanded ? "▾ " : "▸ ";
                case PLAYER_CONTAINER -> playerExpanded ? "▾ " : "▸ ";
                case MAIN_HAND_SLOT -> "→ ";
                case CLICKED_SLOT_META -> "◉ ";
                case SLOT -> "• ";
            };

            int textX = x + 6;
            Component line = Component.literal(indent + prefix).append(row.label);

            if (row.kind.isSlotTarget() && row.menuSlot >= 0 && row.menuSlot < menu.slots.size()) {
                ItemStack stack = menu.getSlot(row.menuSlot).getItem();
                int iconX = x + w - 60;
                int iconY = rowY - 2;
                if (!stack.isEmpty()) {
                    this.itemRenderer.renderAndDecorateItem(stack, iconX, iconY);
                    if (mx >= iconX && mx <= iconX + 16 && my >= iconY && my <= iconY + 16) {
                        hoveredExplorerMenuSlot = row.menuSlot;
                    }

                    ResourceLocation itemId = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem());
                    if (itemId != null) {
                        line = line.copy().append(Component.literal(" " + itemId).withStyle(ChatFormatting.GRAY));
                    }
                }
            }

            drawString(poseStack, font, line, textX, rowY, color);
        }

        drawSimpleScrollbar(
                poseStack,
                x + w - 4,
                contentTop,
                contentHeight,
                rows.size(),
                explorerScroll
        );
    }

    private void drawSimpleScrollbar(
            PoseStack poseStack,
            int x,
            int y,
            int height,
            int rowCount,
            VirtualScrollViewport viewport
    ) {
        int maxOffset = viewport.maxScrollOffset(rowCount, height);
        if (maxOffset <= 0) {
            return;
        }

        fill(poseStack, x, y, x + 2, y + height, 0x55303030);

        int thumbHeight = getScrollbarThumbHeight(height, rowCount, viewport);
        int travel = Math.max(1, height - thumbHeight);
        int thumbY = y + (int) ((viewport.scrollOffset() / (double) maxOffset) * travel);

        fill(poseStack, x, thumbY, x + 2, thumbY + thumbHeight, 0xFFA0A0A0);
    }

    private int getScrollbarThumbHeight(int viewportHeight, int rowCount, VirtualScrollViewport viewport) {
        int contentHeight = Math.max(1, rowCount * viewport.rowHeight());
        return Math.max(16, (viewportHeight * viewportHeight) / contentHeight);
    }

    private boolean isInsideExplorerScrollbar(int mx, int my) {
        int x = getRightPanelX() + getRightPanelWidth() - 4;
        int y = getRightPanelY() + 22;
        int h = getExplorerContentHeight();
        return mx >= x && mx <= x + 3 && my >= y && my <= y + h;
    }

    private void drawTerminalPanel(PoseStack poseStack) {
        int x = getBottomPanelX();
        int y = getBottomPanelY();
        int w = getBottomPanelWidth();
        int h = getBottomPanelHeight();

        fill(poseStack, x, y, x + w, y + h, 0xAA101010);
        drawString(poseStack, font, IdeLocalizationKeys.IDE_PANEL_TERMINAL_TITLE.getComponent(), x + 6, y + 6, 0xFFFFFF);
        drawString(poseStack, font, IdeLocalizationKeys.IDE_TERMINAL_PREFIX_HINT.getComponent(), x + 6, y + 18, 0xAAAAAA);

        int maxLines = Math.max(1, (h - 44) / (font.lineHeight + 1));
        int total = menu.ideTerminalOutput.size();
        int skip = Math.max(0, total - maxLines);
        int lineY = y + 32;
        int index = 0;
        for (String line : menu.ideTerminalOutput) {
            if (index++ < skip) {
                continue;
            }
            drawString(poseStack, font, line, x + 6, lineY, 0xE0E0E0);
            lineY += font.lineHeight + 1;
        }
    }

    private void updateTerminalInputBounds() {
        if (terminalInput == null) {
            return;
        }
        if (!bottomPanelVisible) {
            terminalInput.visible = false;
            terminalInput.setFocus(false);
            if (terminalCommandSuggestions != null) {
                terminalCommandSuggestions.hide();
            }
            return;
        }
        terminalInput.visible = true;
        terminalInput.setWidth(getBottomPanelWidth() - 12);
        terminalInput.setX(getBottomPanelX() + 6);
        terminalInput.y = getBottomPanelY() + getBottomPanelHeight() - 18;
        if (terminalCommandSuggestions != null) {
            terminalCommandSuggestions.updateCommandInfo();
        }
    }

    private int getRightPanelX() {
        return this.width - getRightPanelWidth() - 8;
    }

    private int getRightPanelY() {
        return 8;
    }

    private int getRightPanelHeight() {
        if (bottomPanelVisible) {
            return this.height - getBottomPanelHeight() - 20;
        }
        return this.height - 16;
    }

    private int getExplorerContentHeight() {
        return Math.max(16, getRightPanelHeight() - 26);
    }

    private boolean isInsideExplorerContent(int mx, int my) {
        int x = getRightPanelX();
        int y = getRightPanelY() + 22;
        int h = getExplorerContentHeight();
        return mx >= x && mx <= x + getRightPanelWidth() && my >= y && my <= y + h;
    }

    private int getBottomPanelX() {
        return 8;
    }

    private int getBottomPanelY() {
        return this.height - getBottomPanelHeight() - 8;
    }

    private int getBottomPanelWidth() {
        return this.width - 16;
    }

    private int getCenterPanelX() {
        return (this.width - CENTER_PANEL_WIDTH) / 2;
    }

    private int getCenterPanelY() {
        return (this.height - CENTER_PANEL_HEIGHT) / 2;
    }

    private int getCenterPanelWidth() {
        return CENTER_PANEL_WIDTH;
    }

    private int getCenterPanelHeight() {
        return CENTER_PANEL_HEIGHT;
    }

    private int getRightPanelWidth() {
        return rightPanelWidth;
    }

    private int getBottomPanelHeight() {
        return bottomPanelHeight;
    }

    private void setRightPanelWidth(int requestedWidth) {
        rightPanelWidth = Math.max(MIN_RIGHT_PANEL_WIDTH, Math.min(getMaxRightPanelWidth(), requestedWidth));
    }

    private void setBottomPanelHeight(int requestedHeight) {
        bottomPanelHeight = Math.max(MIN_BOTTOM_PANEL_HEIGHT, Math.min(getMaxBottomPanelHeight(), requestedHeight));
        updateTerminalInputBounds();
    }

    private int getMaxRightPanelWidth() {
        return Math.max(MIN_RIGHT_PANEL_WIDTH, this.width - 16 - MIN_REMAINING_MAIN_AREA_WIDTH);
    }

    private int getMaxBottomPanelHeight() {
        return Math.max(MIN_BOTTOM_PANEL_HEIGHT, this.height - 16 - MIN_REMAINING_MAIN_AREA_HEIGHT);
    }

    private void clampPanelSizesToScreen() {
        rightPanelWidth = Math.max(MIN_RIGHT_PANEL_WIDTH, Math.min(getMaxRightPanelWidth(), rightPanelWidth));
        bottomPanelHeight = Math.max(MIN_BOTTOM_PANEL_HEIGHT, Math.min(getMaxBottomPanelHeight(), bottomPanelHeight));
    }

    private boolean tryBeginResizeDrag(int mouseX, int mouseY) {
        if (rightPanelVisible && bottomPanelVisible && isOverResizeCornerHandle(mouseX, mouseY)) {
            activeResizeEdge = ResizeEdge.BOTH_PANELS;
            resizeDragStartMouseX = mouseX;
            resizeDragStartMouseY = mouseY;
            resizeDragStartRightPanelWidth = rightPanelWidth;
            resizeDragStartBottomPanelHeight = bottomPanelHeight;
            return true;
        }

        if (rightPanelVisible && isOverRightResizeHandle(mouseX, mouseY)) {
            activeResizeEdge = ResizeEdge.RIGHT_PANEL;
            resizeDragStartMouseX = mouseX;
            resizeDragStartRightPanelWidth = rightPanelWidth;
            return true;
        }

        if (bottomPanelVisible && isOverBottomResizeHandle(mouseX, mouseY)) {
            activeResizeEdge = ResizeEdge.BOTTOM_PANEL;
            resizeDragStartMouseY = mouseY;
            resizeDragStartBottomPanelHeight = bottomPanelHeight;
            return true;
        }

        return false;
    }

    private boolean isOverResizeCornerHandle(int mouseX, int mouseY) {
        if (!rightPanelVisible || !bottomPanelVisible) {
            return false;
        }

        int cornerX = getRightPanelX();
        int cornerY = getBottomPanelY();
        return mouseX >= cornerX - RESIZE_HANDLE_THICKNESS
               && mouseX <= cornerX + RESIZE_HANDLE_THICKNESS
               && mouseY >= cornerY - RESIZE_HANDLE_THICKNESS
               && mouseY <= cornerY + RESIZE_HANDLE_THICKNESS;
    }

    private boolean isOverRightResizeHandle(int mouseX, int mouseY) {
        if (!rightPanelVisible) {
            return false;
        }

        int edgeX = getRightPanelX();
        int minY = getRightPanelY();
        int maxY = getRightPanelY() + getRightPanelHeight();
        return mouseX >= edgeX - RESIZE_HANDLE_THICKNESS
               && mouseX <= edgeX + RESIZE_HANDLE_THICKNESS
               && mouseY >= minY
               && mouseY <= maxY;
    }

    private boolean isOverBottomResizeHandle(int mouseX, int mouseY) {
        if (!bottomPanelVisible) {
            return false;
        }

        int edgeY = getBottomPanelY();
        int minX = getBottomPanelX();
        int maxX = getBottomPanelX() + getBottomPanelWidth();
        return mouseY >= edgeY - RESIZE_HANDLE_THICKNESS
               && mouseY <= edgeY + RESIZE_HANDLE_THICKNESS
               && mouseX >= minX
               && mouseX <= maxX;
    }

    private void drawResizeHandles(PoseStack poseStack) {
        if (rightPanelVisible && hoveredRightResizeHandle) {
            int x = getRightPanelX();
            int y = getRightPanelY();
            int h = getRightPanelHeight();
            int color = (activeResizeEdge == ResizeEdge.RIGHT_PANEL || activeResizeEdge == ResizeEdge.BOTH_PANELS)
                        ? 0xFF66CCFF
                        : 0xB066CCFF;
            fill(poseStack, x - 1, y, x + 1, y + h, color);
        }

        if (bottomPanelVisible && hoveredBottomResizeHandle) {
            int x = getBottomPanelX();
            int y = getBottomPanelY();
            int w = getBottomPanelWidth();
            int color = (activeResizeEdge == ResizeEdge.BOTTOM_PANEL || activeResizeEdge == ResizeEdge.BOTH_PANELS)
                        ? 0xFF66CCFF
                        : 0xB066CCFF;
            fill(poseStack, x, y - 1, x + w, y + 1, color);
        }

        if (rightPanelVisible && bottomPanelVisible && hoveredCornerResizeHandle) {
            int cornerX = getRightPanelX();
            int cornerY = getBottomPanelY();
            int color = activeResizeEdge == ResizeEdge.BOTH_PANELS ? 0xFF66CCFF : 0xB066CCFF;
            fill(poseStack, cornerX - 2, cornerY - 2, cornerX + 2, cornerY + 2, color);
        }
    }

    private void initResizeCursors() {
        if (horizontalResizeCursorHandle == 0L) {
            horizontalResizeCursorHandle = GLFW.glfwCreateStandardCursor(GLFW.GLFW_HRESIZE_CURSOR);
        }
        if (verticalResizeCursorHandle == 0L) {
            verticalResizeCursorHandle = GLFW.glfwCreateStandardCursor(GLFW.GLFW_VRESIZE_CURSOR);
        }
        if (diagonalResizeCursorHandle == 0L) {
            diagonalResizeCursorHandle = GLFW.glfwCreateStandardCursor(GLFW.GLFW_CROSSHAIR_CURSOR);
        }
    }

    private void releaseResizeCursors() {
        applyResizeCursor(ResizeCursor.DEFAULT);
        if (horizontalResizeCursorHandle != 0L) {
            GLFW.glfwDestroyCursor(horizontalResizeCursorHandle);
            horizontalResizeCursorHandle = 0L;
        }
        if (verticalResizeCursorHandle != 0L) {
            GLFW.glfwDestroyCursor(verticalResizeCursorHandle);
            verticalResizeCursorHandle = 0L;
        }
        if (diagonalResizeCursorHandle != 0L) {
            GLFW.glfwDestroyCursor(diagonalResizeCursorHandle);
            diagonalResizeCursorHandle = 0L;
        }
    }

    private void updateResizeCursor() {
        ResizeCursor desired;
        if (activeResizeEdge == ResizeEdge.BOTH_PANELS || hoveredCornerResizeHandle) {
            desired = ResizeCursor.DIAGONAL;
        } else if (activeResizeEdge == ResizeEdge.RIGHT_PANEL || hoveredRightResizeHandle) {
            desired = ResizeCursor.HORIZONTAL;
        } else if (activeResizeEdge == ResizeEdge.BOTTOM_PANEL || hoveredBottomResizeHandle) {
            desired = ResizeCursor.VERTICAL;
        } else {
            desired = ResizeCursor.DEFAULT;
        }
        applyResizeCursor(desired);
    }

    private void applyResizeCursor(ResizeCursor desired) {
        if (desired == activeResizeCursor || this.minecraft == null) {
            return;
        }

        long windowHandle = this.minecraft.getWindow().getWindow();
        long cursorHandle = switch (desired) {
            case DEFAULT -> 0L;
            case HORIZONTAL -> horizontalResizeCursorHandle != 0L ? horizontalResizeCursorHandle : 0L;
            case VERTICAL -> verticalResizeCursorHandle != 0L ? verticalResizeCursorHandle : 0L;
            case DIAGONAL -> diagonalResizeCursorHandle != 0L ? diagonalResizeCursorHandle : 0L;
        };
        GLFW.glfwSetCursor(windowHandle, cursorHandle);
        activeResizeCursor = desired;
    }

    private boolean isInsideCenterContent(int mx, int my) {
        return mx >= getCenterPanelX()
               && mx <= getCenterPanelX() + getCenterPanelWidth()
               && my >= getCenterPanelY()
               && my <= getCenterPanelY() + getCenterPanelHeight();
    }

    private boolean isInsideTextEditorContent(int mx, int my) {
        int x = getCenterPanelX() + 8;
        int y = getCenterPanelY() + 24;
        int w = getCenterPanelWidth() - 16;
        int h = getTextEditorContentHeight();
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    private int getTextEditorContentHeight() {
        return Math.max(16, getCenterPanelHeight() - 30);
    }

    private Integer getCenterSlotAt(int mx, int my) {
        int left = getCenterPanelX();
        int top = getCenterPanelY();
        for (int i = 0; i < menu.slots.size(); i++) {
            Slot slot = menu.slots.get(i);
            int sx = left + slot.x;
            int sy = top + slot.y;
            if (mx >= sx && mx <= sx + 16 && my >= sy && my <= sy + 16) {
                return i;
            }
        }
        return null;
    }

    public List<Rect2i> getJeiExclusionZones() {
        return List.of(new Rect2i(0, 0, this.width, this.height));
    }

    private Integer getExplorerRowAt(int mx, int my) {
        if (!isInsideExplorerContent(mx, my)) {
            return null;
        }
        List<ExplorerRow> rows = getExplorerRows();
        return explorerScroll.rowAt(my, getRightPanelY() + 22, rows.size(), getExplorerContentHeight());
    }

    private List<ExplorerRow> getExplorerRows() {
        List<ExplorerRow> rows = new ArrayList<>();

        if (clickedMenuSlot == null) {
            rows.add(new ExplorerRow(
                    ExplorerRowKind.CLICKED_SLOT_META,
                    0,
                    -1,
                    IdeLocalizationKeys.IDE_EXPLORER_CLICKED_SLOT_NONE.getComponent()
            ));
        } else {
            rows.add(new ExplorerRow(
                    ExplorerRowKind.CLICKED_SLOT_META,
                    0,
                    clickedMenuSlot,
                    IdeLocalizationKeys.IDE_EXPLORER_CLICKED_SLOT_NODE.getComponent(clickedMenuSlot)
            ));
        }

        rows.add(new ExplorerRow(ExplorerRowKind.MANAGER_CONTAINER, 0, -1, IdeLocalizationKeys.IDE_EXPLORER_MANAGER_NODE.getComponent()));
        if (managerExpanded) {
            rows.add(new ExplorerRow(ExplorerRowKind.SLOT, 1, 0, IdeLocalizationKeys.IDE_EXPLORER_SLOT_NODE.getComponent(0)));
        }

        rows.add(new ExplorerRow(ExplorerRowKind.PLAYER_CONTAINER, 0, -1, IdeLocalizationKeys.IDE_EXPLORER_PLAYER_NODE.getComponent()));
        if (playerExpanded) {
            int heldPlayerSlot = this.minecraft != null && this.minecraft.player != null
                                 ? this.minecraft.player.getInventory().selected
                                 : 0;
            rows.add(new ExplorerRow(
                    ExplorerRowKind.MAIN_HAND_SLOT,
                    1,
                    28 + heldPlayerSlot,
                    IdeLocalizationKeys.IDE_EXPLORER_MAIN_HAND_SLOT_NODE.getComponent(heldPlayerSlot)
            ));
            for (int i = 0; i < 36; i++) {
                rows.add(new ExplorerRow(ExplorerRowKind.SLOT, 1, i + 1, IdeLocalizationKeys.IDE_EXPLORER_SLOT_NODE.getComponent(i)));
            }
        }

        return rows;
    }

    private enum CenterTab {
        INVENTORY,
        TEXT_EDITOR
    }

    private enum PanelFocus {
        CENTER,
        EXPLORER,
        TERMINAL
    }

    private enum ExplorerRowKind {
        CLICKED_SLOT_META,
        MANAGER_CONTAINER,
        PLAYER_CONTAINER,
        MAIN_HAND_SLOT,
        SLOT;

        private boolean isSlotTarget() {
            return this == MAIN_HAND_SLOT || this == SLOT;
        }
    }

    private enum ResizeEdge {
        NONE,
        RIGHT_PANEL,
        BOTTOM_PANEL,
        BOTH_PANELS
    }

    private enum ResizeCursor {
        DEFAULT,
        HORIZONTAL,
        VERTICAL,
        DIAGONAL
    }

    private record ExplorerRow(
            ExplorerRowKind kind,
            int depth,
            int menuSlot,
            Component label
    ) {
    }
}
