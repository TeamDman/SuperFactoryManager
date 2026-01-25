package ca.teamdman.sfm.client.screen;

import ca.teamdman.sfm.client.text_editor.SFMTextEditScreenLibraryDiskOpenContext;
import ca.teamdman.sfm.common.blockentity.LibraryBlockEntity;
import ca.teamdman.sfm.common.containermenu.LibraryContainerMenu;
import ca.teamdman.sfm.common.containermenu.LibraryContainerMenu.LibraryEntry;
import ca.teamdman.sfm.common.item.DiskItem;
import ca.teamdman.sfm.common.net.ServerboundLibraryDiskSetProgramPacket;
import ca.teamdman.sfm.common.registry.SFMPackets;
import ca.teamdman.sfm.common.util.SFMResourceLocation;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * Client-side screen for the library block.
 * Shows disk slots and displays available library names from inserted disks
 * in a computer terminal-style panel on the left.
 */
public class LibraryScreen extends AbstractContainerScreen<LibraryContainerMenu> {
    private static final ResourceLocation BACKGROUND_TEXTURE_LOCATION = SFMResourceLocation.fromSFMPath(
            "textures/gui/container/library.png"
    );

    private static final int PANEL_WIDTH = 120;
    private static final int PANEL_GAP = 4;

    // Bezel/Frame colors (warm dark grays like old monitor plastic)
    private static final int BEZEL_OUTER = 0xFF2D2A26;
    private static final int BEZEL_INNER = 0xFF1A1816;
    private static final int BEZEL_HIGHLIGHT = 0xFF3D3A36;

    // Screen colors
    private static final int SCREEN_BG = 0xFF0D0C0A;
    private static final int SCREEN_EDGE = 0xFF050504;

    // Amber phosphor text colors
    private static final int TEXT_HEADER = 0xFFFFB84D;
    private static final int TEXT_NORMAL = 0xFFCC9640;
    private static final int TEXT_HOVER = 0xFFFFD080;
    private static final int TEXT_DIM = 0xFF806020;

    // Error/Warning colors (red phosphor style)
    private static final int TEXT_ERROR = 0xFFFF4040;
    private static final int TEXT_ERROR_DIM = 0xFFCC3030;
    private static final int ERROR_GLOW = 0x40FF2020;
    private static final int TEXT_WARNING = 0xFFFFCC40;

    // Effects
    private static final int CURSOR_GLOW = 0x30FFB84D;

    private int hoveredLibraryEntry = -1;

    public LibraryScreen(
            LibraryContainerMenu menu,
            Inventory inv,
            Component title
    ) {
        super(menu, inv, title);
    }

    @Override
    protected void init() {
        super.init();
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        refreshLibraryEntries();
    }

    private void refreshLibraryEntries() {
        menu.libraryEntries = LibraryContainerMenu.extractLibraryEntries(
                menu.CONTAINER, LibraryBlockEntity.DISK_SLOT_COUNT);
    }

    private void openEditorForSlot(int slotIndex) {
        ItemStack disk = menu.CONTAINER.getItem(slotIndex);
        if (!DiskItem.isValidDisk(disk)) return;

        String source = DiskItem.getProgramString(disk);
        SFMScreenChangeHelpers.showProgramEditScreen(new SFMTextEditScreenLibraryDiskOpenContext(
                source,
                newSource -> {
                    SFMPackets.sendToServer(new ServerboundLibraryDiskSetProgramPacket(
                            menu.containerId, menu.LIBRARY_POSITION, slotIndex, newSource));
                    DiskItem.setProgram(disk, newSource);
                    refreshLibraryEntries();
                }
        ));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Click on library entry in panel
        if (button == 0 && hoveredLibraryEntry >= 0 && hoveredLibraryEntry < menu.libraryEntries.size()) {
            LibraryEntry entry = menu.libraryEntries.get(hoveredLibraryEntry);
            openEditorForSlot(entry.slotIndex());
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void render(
            PoseStack poseStack,
            int mx,
            int my,
            float partialTicks
    ) {
        this.renderBackground(poseStack);
        super.render(poseStack, mx, my, partialTicks);
        this.renderTooltip(poseStack, mx, my);

        // Render library list in the panel
        int panelX = this.leftPos - PANEL_WIDTH - PANEL_GAP;
        int screenX = panelX + 5;
        int screenY = this.topPos + 5;
        int screenRight = panelX + PANEL_WIDTH - 5;

        // Header area with subtle background
        int headerY = screenY + 2;
        fill(poseStack, screenX + 3, headerY - 1, screenRight - 3, headerY + font.lineHeight + 2, 0x20FFB84D);

        // Header text - centered
        String header = "LIBRARY INDEX";
        int headerWidth = font.width(header);
        int headerX = screenX + (PANEL_WIDTH - 10 - headerWidth) / 2;
        font.drawShadow(poseStack, header, headerX, headerY, TEXT_HEADER);

        // Divider line
        int dividerY = headerY + font.lineHeight + 4;
        fill(poseStack, screenX + 8, dividerY, screenRight - 8, dividerY + 1, TEXT_DIM);

        // Content area starts below divider
        int contentY = dividerY + 6;
        int textX = screenX + 6;

        // Reset hover state
        hoveredLibraryEntry = -1;

        if (menu.libraryEntries.isEmpty()) {
            // Show hint when no libraries are available
            font.drawShadow(poseStack, "No libraries found", textX, contentY, TEXT_DIM);
            contentY += font.lineHeight + 2;
            font.drawShadow(poseStack, "Insert disks with", textX, contentY, TEXT_DIM);
            contentY += font.lineHeight;
            font.drawShadow(poseStack, "NAME statements", textX, contentY, TEXT_DIM);
        } else {
            // Draw library entries with slot numbers right-aligned
            for (int i = 0; i < menu.libraryEntries.size(); i++) {
                LibraryEntry entry = menu.libraryEntries.get(i);

                // Hover detection - use full row width
                boolean hovered = mx >= screenX + 3 && mx <= screenRight - 3
                        && my >= contentY - 1 && my <= contentY + font.lineHeight + 1;

                // Determine colors based on error/warning status
                int bgGlow;
                int textColor;
                int statusColor;
                String statusIndicator = "";

                if (entry.hasErrors()) {
                    // Error state - red phosphor effect
                    bgGlow = hovered ? ERROR_GLOW : 0x20FF2020;
                    textColor = hovered ? TEXT_ERROR : TEXT_ERROR_DIM;
                    statusColor = TEXT_ERROR;
                    statusIndicator = "!";
                } else if (entry.hasWarnings()) {
                    // Warning state - yellow/amber
                    bgGlow = hovered ? CURSOR_GLOW : 0x20FFCC40;
                    textColor = hovered ? TEXT_WARNING : TEXT_NORMAL;
                    statusColor = TEXT_WARNING;
                    statusIndicator = "?";
                } else {
                    // Normal state
                    bgGlow = CURSOR_GLOW;
                    textColor = hovered ? TEXT_HOVER : TEXT_NORMAL;
                    statusColor = TEXT_DIM;
                    statusIndicator = "";
                }

                if (hovered) {
                    hoveredLibraryEntry = i;
                    // Draw hover glow background
                    fill(poseStack, screenX + 3, contentY - 1, screenRight - 3, contentY + font.lineHeight + 1, bgGlow);
                } else if (entry.hasErrors()) {
                    // Always show subtle error background glow
                    fill(poseStack, screenX + 3, contentY - 1, screenRight - 3, contentY + font.lineHeight + 1, bgGlow);
                }

                // Cursor indicator and library name
                String cursor = hovered ? "> " : "  ";
                font.drawShadow(poseStack, cursor + entry.name(), textX, contentY, textColor);

                // Status indicator (! for error, ? for warning) and slot number right-aligned
                String slotText = statusIndicator + "[" + (entry.slotIndex()) + "]";
                int slotWidth = font.width(slotText);
                font.drawShadow(poseStack, slotText, screenRight - 6 - slotWidth, contentY,
                        statusIndicator.isEmpty() ? TEXT_DIM : statusColor);

                contentY += font.lineHeight + 3;

                // Don't overflow past the panel
                if (contentY > this.topPos + this.imageHeight - font.lineHeight - 10) break;
            }
        }
    }

    @Override
    protected void renderLabels(
            PoseStack poseStack,
            int mx,
            int my
    ) {
        // Draw title centered in the container area
        this.font.draw(poseStack, this.title, (float) this.titleLabelX, (float) this.titleLabelY, 4210752);
        this.font.draw(poseStack, this.playerInventoryTitle, (float) this.inventoryLabelX, (float) this.inventoryLabelY, 4210752);
    }

    @Override
    protected void renderBg(
            PoseStack matrixStack,
            float partialTicks,
            int mx,
            int my
    ) {
        // Render the computer panel with CRT monitor bezel effect
        int panelX = this.leftPos - PANEL_WIDTH - PANEL_GAP;
        int panelY = this.topPos;
        int panelRight = panelX + PANEL_WIDTH;
        int panelBottom = panelY + imageHeight;

        // Outer bezel (3px frame)
        fill(matrixStack, panelX, panelY, panelRight, panelBottom, BEZEL_OUTER);

        // Bezel highlight (top and left edges, 1px)
        fill(matrixStack, panelX, panelY, panelRight, panelY + 1, BEZEL_HIGHLIGHT);
        fill(matrixStack, panelX, panelY, panelX + 1, panelBottom, BEZEL_HIGHLIGHT);

        // Inner bezel shadow (bottom and right, 1px)
        fill(matrixStack, panelX, panelBottom - 1, panelRight, panelBottom, SCREEN_EDGE);
        fill(matrixStack, panelRight - 1, panelY, panelRight, panelBottom, SCREEN_EDGE);

        // Inner frame (2px inset)
        int innerX = panelX + 3;
        int innerY = panelY + 3;
        int innerRight = panelRight - 3;
        int innerBottom = panelBottom - 3;
        fill(matrixStack, innerX, innerY, innerRight, innerBottom, BEZEL_INNER);

        // Screen area (the "CRT glass")
        int screenX = innerX + 2;
        int screenY = innerY + 2;
        int screenRight = innerRight - 2;
        int screenBottom = innerBottom - 2;
        fill(matrixStack, screenX, screenY, screenRight, screenBottom, SCREEN_BG);

        // Screen edge darkening (vignette effect - 1px darker border inside screen)
        fill(matrixStack, screenX, screenY, screenRight, screenY + 1, SCREEN_EDGE);
        fill(matrixStack, screenX, screenY, screenX + 1, screenBottom, SCREEN_EDGE);

        // Render the main container background
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.setShaderTexture(0, BACKGROUND_TEXTURE_LOCATION);
        int i = (this.width - this.imageWidth) / 2;
        int j = (this.height - this.imageHeight) / 2;
        blit(matrixStack, i, j, 0, 0, this.imageWidth, this.imageHeight);
    }
}
