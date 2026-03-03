package ca.teamdman.sfm.client.screen;

import ca.teamdman.sfm.client.ide.action.ManagerIdeActionRegistry;
import ca.teamdman.sfm.client.registry.SFMKeyMappings;
import ca.teamdman.sfm.common.containermenu.ManagerContainerMenu;
import ca.teamdman.sfm.common.localization.IdeLocalizationKeys;
import ca.teamdman.sfm.common.net.ServerboundManagerIdeExplorerDropPacket;
import ca.teamdman.sfm.common.net.ServerboundManagerIdeTerminalCommandPacket;
import ca.teamdman.sfm.common.registry.registration.SFMPackets;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public class ManagerIdeScreen extends AbstractContainerScreen<ManagerContainerMenu> {
    private static final int RIGHT_PANEL_WIDTH = 180;
    private static final int BOTTOM_PANEL_HEIGHT = 120;

    private boolean rightPanelVisible = true;
    private boolean bottomPanelVisible = true;
    private boolean managerExpanded = true;
    private boolean playerExpanded = true;

    private PanelFocus focusedPanel = PanelFocus.CENTER;
    private Integer selectedExplorerRow = 0;
    private Integer draggingSourceMenuSlot;
    private Integer hoveredExplorerMenuSlot;

    private EditBox terminalInput;

    public ManagerIdeScreen(ManagerContainerMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
    }

    @Override
    protected void init() {
        super.init();
        this.terminalInput = this.addRenderableWidget(new EditBox(
                this.font,
                10,
                this.height - 24,
                this.width - 20,
                16,
                IdeLocalizationKeys.IDE_TERMINAL_INPUT_PLACEHOLDER.getComponent()
        ));
        this.terminalInput.setMaxLength(ServerboundManagerIdeTerminalCommandPacket.MAX_COMMAND_LENGTH);
        updateTerminalInputBounds();
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
            if (pKeyCode == GLFW.GLFW_KEY_LEFT || pKeyCode == GLFW.GLFW_KEY_RIGHT || pKeyCode == GLFW.GLFW_KEY_ENTER) {
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
            }
        }

        if (bottomPanelVisible && terminalInput != null) {
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
        if (bottomPanelVisible && terminalInput != null && terminalInput.visible && terminalInput.mouseClicked(pMouseX, pMouseY, pButton)) {
            focusTerminalPanel();
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
                    } else if (row.kind == ExplorerRowKind.SLOT) {
                        draggingSourceMenuSlot = row.menuSlot;
                    }
                    return true;
                }
            }
        }

        return super.mouseClicked(pMouseX, pMouseY, pButton);
    }

    @Override
    public boolean mouseReleased(double pMouseX, double pMouseY, int pButton) {
        if (pButton == 0 && draggingSourceMenuSlot != null && rightPanelVisible) {
            Integer rowIndex = getExplorerRowAt((int) pMouseX, (int) pMouseY);
            if (rowIndex != null) {
                var rows = getExplorerRows();
                if (rowIndex >= 0 && rowIndex < rows.size()) {
                    ExplorerRow row = rows.get(rowIndex);
                    ServerboundManagerIdeExplorerDropPacket.TargetKind targetKind;
                    int targetSlot = -1;
                    if (row.kind == ExplorerRowKind.SLOT) {
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

    @Override
    public void render(PoseStack poseStack, int mx, int my, float partialTicks) {
        this.renderBackground(poseStack);
        super.render(poseStack, mx, my, partialTicks);
        renderCenterSlotHighlights(poseStack);

        hoveredExplorerMenuSlot = null;
        drawIdePanels(poseStack, mx, my);

        if (bottomPanelVisible && terminalInput != null && terminalInput.visible) {
            terminalInput.render(poseStack, mx, my, partialTicks);
        }

        if (draggingSourceMenuSlot != null) {
            drawString(poseStack, font, Component.literal("Dragging slot " + draggingSourceMenuSlot), mx + 8, my + 8, 0xFFFFFF);
        }

        if (hoveredExplorerMenuSlot != null) {
            ItemStack stack = menu.getSlot(hoveredExplorerMenuSlot).getItem();
            if (!stack.isEmpty()) {
                this.renderTooltip(poseStack, stack, mx, my);
            }
        }

        this.renderTooltip(poseStack, mx, my);
    }

    private void drawIdePanels(PoseStack poseStack, int mx, int my) {
        if (rightPanelVisible) {
            drawExplorerPanel(poseStack, mx, my);
        }
        if (bottomPanelVisible) {
            drawTerminalPanel(poseStack);
        }
    }

    private void drawExplorerPanel(PoseStack poseStack, int mx, int my) {
        int x = getRightPanelX();
        int y = getRightPanelY();
        int w = RIGHT_PANEL_WIDTH;
        int h = getRightPanelHeight();

        fill(poseStack, x, y, x + w, y + h, 0xAA101010);
        drawString(poseStack, font, IdeLocalizationKeys.IDE_PANEL_EXPLORER_TITLE.getComponent(), x + 6, y + 6, 0xFFFFFF);

        List<ExplorerRow> rows = getExplorerRows();
        int rowY = y + 22;
        for (int i = 0; i < rows.size(); i++) {
            ExplorerRow row = rows.get(i);
            int color = focusedPanel == PanelFocus.EXPLORER && selectedExplorerRow != null && selectedExplorerRow == i
                        ? 0xFF66CCFF
                        : 0xFFFFFFFF;
            String indent = row.depth == 0 ? "" : "  ";
            String prefix = switch (row.kind) {
                case MANAGER_CONTAINER -> managerExpanded ? "▾ " : "▸ ";
                case PLAYER_CONTAINER -> playerExpanded ? "▾ " : "▸ ";
                case SLOT -> "• ";
            };

            int textX = x + 6;
            Component line = Component.literal(indent + prefix).append(row.label);

            if (row.kind == ExplorerRowKind.SLOT && row.menuSlot >= 0 && row.menuSlot < menu.slots.size()) {
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

            rowY += font.lineHeight + 2;
            if (rowY > y + h - 8) {
                break;
            }
        }
    }

    private void drawTerminalPanel(PoseStack poseStack) {
        int x = getBottomPanelX();
        int y = getBottomPanelY();
        int w = getBottomPanelWidth();
        int h = BOTTOM_PANEL_HEIGHT;

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
            return;
        }
        terminalInput.visible = true;
        terminalInput.setWidth(getBottomPanelWidth() - 12);
    }

    @Override
    protected void renderBg(PoseStack matrixStack, float partialTicks, int mx, int my) {
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.disableTexture();
        fill(matrixStack, leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0x55000000);
        RenderSystem.enableTexture();
    }

    @Override
    protected void renderLabels(PoseStack poseStack, int mx, int my) {
        drawString(poseStack, font, title, titleLabelX, titleLabelY, 0xFFFFFF);
        drawString(
                poseStack,
                font,
                IdeLocalizationKeys.IDE_PLAYER_INVENTORY_LABEL.getComponent(),
                8,
                74,
                0xB0B0B0
        );
    }

    private void renderCenterSlotHighlights(PoseStack poseStack) {
        for (Slot slot : menu.slots) {
            int x = leftPos + slot.x;
            int y = topPos + slot.y;
            fill(poseStack, x - 1, y - 1, x + 17, y, 0x88D0D0D0);
            fill(poseStack, x - 1, y + 16, x + 17, y + 17, 0x88D0D0D0);
            fill(poseStack, x - 1, y, x, y + 16, 0x88D0D0D0);
            fill(poseStack, x + 16, y, x + 17, y + 16, 0x88D0D0D0);
        }
    }

    private int getRightPanelX() {
        return this.width - RIGHT_PANEL_WIDTH - 8;
    }

    private int getRightPanelY() {
        return 8;
    }

    private int getRightPanelHeight() {
        if (bottomPanelVisible) {
            return this.height - BOTTOM_PANEL_HEIGHT - 20;
        }
        return this.height - 16;
    }

    private int getBottomPanelX() {
        return 8;
    }

    private int getBottomPanelY() {
        return this.height - BOTTOM_PANEL_HEIGHT - 8;
    }

    private int getBottomPanelWidth() {
        return this.width - 16;
    }

    public List<Rect2i> getJeiExclusionZones() {
        return List.of(new Rect2i(0, 0, this.width, this.height));
    }

    private Integer getExplorerRowAt(int mx, int my) {
        int x = getRightPanelX();
        int y = getRightPanelY();
        int h = getRightPanelHeight();
        if (mx < x || mx > x + RIGHT_PANEL_WIDTH || my < y || my > y + h) {
            return null;
        }
        int startY = y + 22;
        if (my < startY) {
            return null;
        }
        int rowHeight = font.lineHeight + 2;
        int index = (my - startY) / rowHeight;
        if (index < 0 || index >= getExplorerRows().size()) {
            return null;
        }
        return index;
    }

    private List<ExplorerRow> getExplorerRows() {
        List<ExplorerRow> rows = new ArrayList<>();
        rows.add(new ExplorerRow(ExplorerRowKind.MANAGER_CONTAINER, 0, -1, IdeLocalizationKeys.IDE_EXPLORER_MANAGER_NODE.getComponent()));
        if (managerExpanded) {
            rows.add(new ExplorerRow(ExplorerRowKind.SLOT, 1, 0, IdeLocalizationKeys.IDE_EXPLORER_SLOT_NODE.getComponent(0)));
        }
        rows.add(new ExplorerRow(ExplorerRowKind.PLAYER_CONTAINER, 0, -1, IdeLocalizationKeys.IDE_EXPLORER_PLAYER_NODE.getComponent()));
        if (playerExpanded) {
            for (int i = 0; i < 36; i++) {
                rows.add(new ExplorerRow(ExplorerRowKind.SLOT, 1, i + 1, IdeLocalizationKeys.IDE_EXPLORER_SLOT_NODE.getComponent(i)));
            }
        }
        return rows;
    }

    private enum PanelFocus {
        CENTER,
        EXPLORER,
        TERMINAL
    }

    private enum ExplorerRowKind {
        MANAGER_CONTAINER,
        PLAYER_CONTAINER,
        SLOT
    }

    private record ExplorerRow(
            ExplorerRowKind kind,
            int depth,
            int menuSlot,
            Component label
    ) {
    }
}
