package ca.teamdman.sfm.client.gui.screen;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.containermenu.ProxyContainerMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.PlayerRideable;
import net.minecraft.world.entity.player.Inventory;

public class ProxyScreen extends AbstractContainerScreen<ProxyContainerMenu> {
    private static final ResourceLocation PROXY_IMAGE = ResourceLocation.fromNamespaceAndPath(
            SFM.MOD_ID,
            "textures/gui/container/proxy.png"
    );

    public ProxyScreen(
            ProxyContainerMenu menu,
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
    protected void renderBg(GuiGraphics pGuiGraphics, float pPartialTick, int pMouseX, int pMouseY) {

    }

    @Override
    public void render(GuiGraphics pGuiGraphics, int pMouseX, int pMouseY, float pPartialTick) {
        super.render(pGuiGraphics, pMouseX, pMouseY, pPartialTick);
    }
}
