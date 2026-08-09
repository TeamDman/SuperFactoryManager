package ca.teamdman.sfm.client.screen;

import ca.teamdman.sfm.client.text_editor.SFMTextEditScreenLibraryDiskOpenContext;
import ca.teamdman.sfm.common.blockentity.LibraryBlockEntity;
import ca.teamdman.sfm.common.containermenu.LibraryContainerMenu;
import ca.teamdman.sfm.common.containermenu.LibraryContainerMenu.LibraryEntry;
import ca.teamdman.sfm.common.item.DiskItem;
import ca.teamdman.sfm.common.net.ServerboundLibraryDiskSetProgramPacket;
import ca.teamdman.sfm.common.registry.registration.SFMPackets;
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

    // Industrial server rack color palette (matching block textures)
    private static final int PANEL_BG = 0xFF1A1A1A;        // Darkest background
    private static final int PANEL_FRAME = 0xFF2D2D2D;    // Mid panel
    private static final int PANEL_HIGHLIGHT = 0xFF4A4A4A; // Edge highlights
    private static final int PANEL_SHADOW = 0xFF0F0F0F;   // Deep shadows
    private static final int PANEL_LIGHT = 0xFF3D3D3D;    // Light metal accents

    // Text colors (cool industrial)
    private static final int TEXT_HEADER = 0xFF60C0C0;    // Cyan accent
    private static final int TEXT_PRIMARY = 0xFFE0E0E0;   // Light gray
    private static final int TEXT_SECONDARY = 0xFF707070; // Dim gray
    private static final int TEXT_HOVER = 0xFFFFFFFF;     // White on hover
    private static final int TEXT_ERROR = 0xFFFF6060;     // Error text
    private static final int TEXT_WARNING = 0xFFE0E040;   // Warning text

    // Effects
    private static final int HOVER_BAR = 0x40FFFFFF;      // Subtle highlight bar
    private static final int ERROR_BAR = 0x30FF4040;      // Error highlight
    private static final int WARNING_BAR = 0x30FFFF40;    // Warning highlight

    private int hoveredLibraryEntry = -1;
    private int lastHoveredLibraryEntry = -1;
    private long hoverStartTime = 0;
    private static final long SCROLL_DELAY_MS = 500;      // Wait before scrolling starts
    private static final float SCROLL_SPEED = 30f;        // Pixels per second

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

        // Header area with recessed metal panel effect
        int headerY = screenY + 2;
        fill(poseStack, screenX + 3, headerY - 2, screenRight - 3, headerY + font.lineHeight + 3, PANEL_SHADOW);
        fill(poseStack, screenX + 4, headerY - 1, screenRight - 4, headerY + font.lineHeight + 2, PANEL_BG);

        // Header text - centered with cyan accent
        String header = "LIBRARY INDEX";
        int headerWidth = font.width(header);
        int headerX = screenX + (PANEL_WIDTH - 10 - headerWidth) / 2;
        font.drawShadow(poseStack, header, headerX, headerY, TEXT_HEADER);

        // Horizontal divider lines (ventilation slit style)
        int dividerY = headerY + font.lineHeight + 6;
        fill(poseStack, screenX + 6, dividerY, screenRight - 6, dividerY + 1, PANEL_SHADOW);
        fill(poseStack, screenX + 6, dividerY + 2, screenRight - 6, dividerY + 3, PANEL_HIGHLIGHT);

        // Content area starts below divider
        int contentY = dividerY + 8;
        int textX = screenX + 6;

        // Reset hover state
        hoveredLibraryEntry = -1;

        if (menu.libraryEntries.isEmpty()) {
            // Show hint when no libraries are available
            font.drawShadow(poseStack, "No libraries found", textX, contentY, TEXT_SECONDARY);
            contentY += font.lineHeight + 2;
            font.drawShadow(poseStack, "Insert disks with", textX, contentY, TEXT_SECONDARY);
            contentY += font.lineHeight;
            font.drawShadow(poseStack, "NAME statements", textX, contentY, TEXT_SECONDARY);
        } else {
            // Draw library entries with cursor indicator
            for (int i = 0; i < menu.libraryEntries.size(); i++) {
                LibraryEntry entry = menu.libraryEntries.get(i);

                // Hover detection - use full row width
                boolean hovered = mx >= screenX + 3 && mx <= screenRight - 3
                        && my >= contentY - 1 && my <= contentY + font.lineHeight + 1;

                // Determine text color based on status
                int textColor;
                int barColor;

                if (entry.hasErrors()) {
                    textColor = hovered ? TEXT_ERROR : TEXT_PRIMARY;
                    barColor = ERROR_BAR;
                } else if (entry.hasWarnings()) {
                    textColor = hovered ? TEXT_WARNING : TEXT_PRIMARY;
                    barColor = WARNING_BAR;
                } else {
                    textColor = hovered ? TEXT_HOVER : TEXT_PRIMARY;
                    barColor = HOVER_BAR;
                }

                // Draw hover highlight bar or status bar
                if (hovered) {
                    hoveredLibraryEntry = i;
                    if (lastHoveredLibraryEntry != i) {
                        hoverStartTime = System.currentTimeMillis();
                        lastHoveredLibraryEntry = i;
                    }
                    fill(poseStack, screenX + 3, contentY - 1, screenRight - 3, contentY + font.lineHeight + 1, barColor);
                } else if (entry.hasErrors()) {
                    fill(poseStack, screenX + 3, contentY - 1, screenRight - 3, contentY + font.lineHeight + 1, ERROR_BAR);
                } else if (entry.hasWarnings()) {
                    fill(poseStack, screenX + 3, contentY - 1, screenRight - 3, contentY + font.lineHeight + 1, WARNING_BAR);
                }

                // Cursor indicator and library name
                String cursor = hovered ? "> " : "  ";
                int cursorWidth = font.width(cursor);

                // Slot number right-aligned
                String slotText = "[" + entry.slotIndex() + "]";
                int slotWidth = font.width(slotText);
                int slotX = screenRight - 6 - slotWidth;

                // Calculate available width for library name (between cursor and slot number)
                int nameStartX = textX + cursorWidth;
                int maxNameWidth = slotX - nameStartX - 4; // 4px padding before slot

                String name = entry.name();
                int nameWidth = font.width(name);

                // Draw cursor
                font.drawShadow(poseStack, cursor, textX, contentY, textColor);

                // Draw library name with truncation or scrolling
                if (nameWidth <= maxNameWidth) {
                    // Name fits - draw normally
                    font.drawShadow(poseStack, name, nameStartX, contentY, textColor);
                } else if (hovered) {
                    // Hovered and too long - scroll the text
                    long currentTime = System.currentTimeMillis();
                    long hoverDuration = currentTime - hoverStartTime;

                    if (hoverDuration > SCROLL_DELAY_MS) {
                        // Calculate scroll offset - infinite scroll with pause at start
                        float scrollTime = (hoverDuration - SCROLL_DELAY_MS) / 1000f;
                        int scrollDistance = nameWidth + 20; // Full width plus gap before repeat
                        float scrollOffset = (scrollTime * SCROLL_SPEED) % scrollDistance;

                        // Enable scissor to clip text
                        enableScissor(nameStartX, contentY - 1, slotX - 4, contentY + font.lineHeight + 1);
                        font.drawShadow(poseStack, name, nameStartX - (int) scrollOffset, contentY, textColor);
                        // Draw second copy for seamless loop
                        font.drawShadow(poseStack, name, nameStartX - (int) scrollOffset + scrollDistance, contentY, textColor);
                        disableScissor();
                    } else {
                        // Still in delay period - show truncated with ellipsis
                        enableScissor(nameStartX, contentY - 1, slotX - 4, contentY + font.lineHeight + 1);
                        font.drawShadow(poseStack, name, nameStartX, contentY, textColor);
                        disableScissor();
                    }
                } else {
                    // Not hovered and too long - truncate with ellipsis
                    String ellipsis = "...";
                    int ellipsisWidth = font.width(ellipsis);
                    String truncated = font.plainSubstrByWidth(name, maxNameWidth - ellipsisWidth) + ellipsis;
                    font.drawShadow(poseStack, truncated, nameStartX, contentY, textColor);
                }

                // Draw slot number
                font.drawShadow(poseStack, slotText, slotX, contentY, TEXT_SECONDARY);

                contentY += font.lineHeight + 3;

                // Don't overflow past the panel
                if (contentY > this.topPos + this.imageHeight - font.lineHeight - 10) break;
            }
        }

        // Reset scroll state when no longer hovering any entry
        if (hoveredLibraryEntry == -1) {
            lastHoveredLibraryEntry = -1;
        }
    }

    @Override
    protected void renderLabels(
            PoseStack poseStack,
            int mx,
            int my
    ) {
        // Draw title and inventory label with light text for readability on dark background
        this.font.draw(poseStack, this.title, (float) this.titleLabelX, (float) this.titleLabelY, TEXT_PRIMARY);
        this.font.draw(poseStack, this.playerInventoryTitle, (float) this.inventoryLabelX, (float) this.inventoryLabelY, TEXT_PRIMARY);
    }

    @Override
    protected void renderBg(
            PoseStack matrixStack,
            float partialTicks,
            int mx,
            int my
    ) {
        // Render the industrial metal panel frame
        int panelX = this.leftPos - PANEL_WIDTH - PANEL_GAP;
        int panelY = this.topPos;
        int panelRight = panelX + PANEL_WIDTH;
        int panelBottom = panelY + imageHeight;

        // Main panel background
        fill(matrixStack, panelX, panelY, panelRight, panelBottom, PANEL_FRAME);

        // Clean outer border - dark edge
        fill(matrixStack, panelX, panelY, panelRight, panelY + 1, PANEL_SHADOW);
        fill(matrixStack, panelX, panelY, panelX + 1, panelBottom, PANEL_SHADOW);
        fill(matrixStack, panelX, panelBottom - 1, panelRight, panelBottom, PANEL_SHADOW);
        fill(matrixStack, panelRight - 1, panelY, panelRight, panelBottom, PANEL_SHADOW);

        // Inner lighter border for depth
        fill(matrixStack, panelX + 1, panelY + 1, panelRight - 1, panelY + 2, PANEL_LIGHT);
        fill(matrixStack, panelX + 1, panelY + 1, panelX + 2, panelBottom - 1, PANEL_LIGHT);
        fill(matrixStack, panelX + 1, panelBottom - 2, panelRight - 1, panelBottom - 1, PANEL_LIGHT);
        fill(matrixStack, panelRight - 2, panelY + 1, panelRight - 1, panelBottom - 1, PANEL_LIGHT);

        // Inner recessed area
        int innerX = panelX + 3;
        int innerY = panelY + 3;
        int innerRight = panelRight - 3;
        int innerBottom = panelBottom - 3;

        // Main display area (dark background)
        fill(matrixStack, innerX, innerY, innerRight, innerBottom, PANEL_BG);

        // Render the main container background
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.setShaderTexture(0, BACKGROUND_TEXTURE_LOCATION);
        int i = (this.width - this.imageWidth) / 2;
        int j = (this.height - this.imageHeight) / 2;
        blit(matrixStack, i, j, 0, 0, this.imageWidth, this.imageHeight);
    }
}
