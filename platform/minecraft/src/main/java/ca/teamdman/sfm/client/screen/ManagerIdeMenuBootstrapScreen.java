package ca.teamdman.sfm.client.screen;

import ca.teamdman.sfm.common.containermenu.ManagerContainerMenu;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

public class ManagerIdeMenuBootstrapScreen extends AbstractContainerScreen<ManagerContainerMenu> {
    private final Inventory playerInventory;

    public ManagerIdeMenuBootstrapScreen(ManagerContainerMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.playerInventory = inv;
    }

    @Override
    protected void init() {
        super.init();
        if (this.minecraft != null) {
            this.minecraft.setScreen(new ManagerIdeScreen(this.menu, this.playerInventory, this.title));
        }
    }

    @Override
    public void removed() {
    }

    @Override
    protected void renderBg(PoseStack pPoseStack, float pPartialTick, int pMouseX, int pMouseY) {
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
