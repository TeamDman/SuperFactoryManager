package ca.teamdman.sfm.client.screen;

import ca.teamdman.sfm.common.blockentity.ClientManagerBlockEntity;
import ca.teamdman.sfm.common.containermenu.ClientManagerContainerMenu;
import ca.teamdman.sfm.common.localization.LocalizationEntry;
import ca.teamdman.sfm.common.localization.SFMLocalizationDatagen;
import ca.teamdman.sfm.common.util.SFMResourceLocation;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

/** The dedicated, read-only-program-summary screen for a Client Manager. */
public final class ClientManagerScreen extends AbstractContainerScreen<ClientManagerContainerMenu> {
    @SFMLocalizationDatagen
    public static final LocalizationEntry PROGRAM_SYNCHRONIZED = new LocalizationEntry(
            "gui.sfm.client_manager.program_synchronized", "Client program synchronized"
    );
    @SFMLocalizationDatagen
    public static final LocalizationEntry PROGRAM_EMPTY = new LocalizationEntry(
            "gui.sfm.client_manager.program_empty", "No client program installed"
    );

    private static final ResourceLocation BACKGROUND_TEXTURE_LOCATION = SFMResourceLocation.fromSFMPath(
            "textures/gui/container/manager.png"
    );

    public ClientManagerScreen(ClientManagerContainerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    /**
     * Keep the tooltip pass explicit, matching the other SFM container screens.
     * AbstractContainerScreen's normal render path does not guarantee the item
     * tooltip after custom label/background work on every supported 1.19.2
     * client, so the dedicated screen owns the pass here.
     */
    @Override
    public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(poseStack);
        super.render(poseStack, mouseX, mouseY, partialTicks);
        this.renderTooltip(poseStack, mouseX, mouseY);
    }

    @Override
    protected void renderLabels(PoseStack poseStack, int mouseX, int mouseY) {
        super.renderLabels(poseStack, mouseX, mouseY);
        String source = "";
        if (this.minecraft != null && this.minecraft.level != null
                && this.minecraft.level.getBlockEntity(menu.MANAGER_POSITION) instanceof ClientManagerBlockEntity manager) {
            source = manager.storedSource();
        }
        SFMFontUtils.draw(
                poseStack,
                this.font,
                source.isBlank() ? PROGRAM_EMPTY.getComponent() : PROGRAM_SYNCHRONIZED.getComponent(),
                8,
                20,
                0x404040,
                false
        );
        if (!source.isBlank()) {
            String firstLine = source.replace('\n', ' ').replace('\r', ' ');
            int maximumWidth = Math.max(0, this.imageWidth - 16);
            String ellipsis = "...";
            if (this.font.width(firstLine) > maximumWidth) {
                int textWidth = Math.max(0, maximumWidth - this.font.width(ellipsis));
                firstLine = this.font.plainSubstrByWidth(firstLine, textWidth) + ellipsis;
            }
            SFMFontUtils.draw(poseStack, this.font, firstLine, 8, 32, 0x404040, false);
        }
    }

    @Override
    protected void renderTooltip(PoseStack poseStack, int mouseX, int mouseY) {
        super.renderTooltip(poseStack, mouseX, mouseY);
    }

    @Override
    protected void renderBg(PoseStack poseStack, float partialTicks, int mouseX, int mouseY) {
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.setShaderTexture(0, BACKGROUND_TEXTURE_LOCATION);
        int left = (this.width - this.imageWidth) / 2;
        int top = (this.height - this.imageHeight) / 2;
        this.blit(poseStack, left, top, 0, 0, this.imageWidth, this.imageHeight);
    }
}
