package ca.teamdman.sfm.client.widget;

import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.gui.ScreenUtils;
import net.minecraftforge.client.gui.widget.ExtendedButton;

public class SFMExtendedButton extends ExtendedButton {
    public SFMExtendedButton(
            int xPos,
            int yPos,
            int width,
            int height,
            Component displayString,
            OnPress handler
    ) {
        super(xPos, yPos, width, height, displayString, handler);
    }

    /**
     * This fn is copied from {@link ExtendedButton#renderButton(PoseStack, int, int, float)}
     * I copied it to fix tab navigation button highlight.
     * <a href="https://github.com/TeamDman/SuperFactoryManager/issues/253">issue</a>
     */
    @Override
    @MCVersionDependentBehaviour // Removed as unnecessary for MC versions 1.19.4 and later
    public void renderButton(PoseStack poseStack, int mouseX, int mouseY, float partialTick)
    {
        Minecraft mc = Minecraft.getInstance();
        int k = this.getYImage(this.isHoveredOrFocused());
        ScreenUtils.blitWithBorder(poseStack, WIDGETS_LOCATION, this.x, this.y, 0, 46 + k * 20, this.width, this.height, 200, 20, 2, 3, 2, 2, this.getBlitOffset());
        this.renderBg(poseStack, mc, mouseX, mouseY);

        Component buttonText = this.getMessage();
        int strWidth = mc.font.width(buttonText);
        int ellipsisWidth = mc.font.width("...");

        if (strWidth > width - 6 && strWidth > ellipsisWidth)
            //TODO, srg names make it hard to figure out how to append to an ITextProperties from this trim operation, wraping this in StringTextComponent is kinda dirty.
            buttonText = Component.literal(mc.font.substrByWidth(buttonText, width - 6 - ellipsisWidth).getString() + "...");

        drawCenteredString(poseStack, mc.font, buttonText, this.x + this.width / 2, this.y + (this.height - 8) / 2, getFGColor());
    }

}
