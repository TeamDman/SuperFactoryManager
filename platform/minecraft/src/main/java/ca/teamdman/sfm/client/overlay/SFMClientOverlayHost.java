package ca.teamdman.sfm.client.overlay;

import ca.teamdman.sfm.client.overlay.scene.SFMClientOverlayRuntime;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

/** One reusable Forge HUD adapter for the declarative SFM overlay scene. */
public final class SFMClientOverlayHost implements IGuiOverlay {
    @Override
    public void render(ForgeGui gui, PoseStack poseStack, float partialTick, int screenWidth, int screenHeight) {
        Minecraft minecraft = gui.getMinecraft();
        if (minecraft.options.hideGui || minecraft.level == null) return;
        int mouseX = (int) Math.floor(minecraft.mouseHandler.xpos()
                * minecraft.getWindow().getGuiScaledWidth() / minecraft.getWindow().getScreenWidth());
        int mouseY = (int) Math.floor(minecraft.mouseHandler.ypos()
                * minecraft.getWindow().getGuiScaledHeight() / minecraft.getWindow().getScreenHeight());
        SFMClientOverlayRuntime.get().render(
                poseStack,
                minecraft,
                screenWidth,
                screenHeight,
                mouseX,
                mouseY,
                partialTick
        );
    }
}
