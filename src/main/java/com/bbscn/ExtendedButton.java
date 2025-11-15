/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package com.bbscn;

import net.minecraft.client.Minecraft;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.ITextComponent;

/**
 * This class provides a button that fixes several bugs present in the vanilla GuiButton drawing code.
 * The gist of it is that it allows buttons of any size without gaps in the graphics and with the
 * borders drawn properly. It also prevents button text from extending out of the sides of the button by
 * trimming the end of the string and adding an ellipsis.<br/><br/>
 *
 * The code that handles drawing the button is in GuiUtils.
 *
 * @author bspkrs
 */
public class ExtendedButton extends Button {
    protected ExtendedButton(
            int pX, int pY, int pWidth, int pHeight, ITextComponent pMessage, OnPress pOnPress
    ) {
        super(pX, pY, pWidth, pHeight, pMessage, pOnPress);
    }
    /**
     * Draws this button to the screen.
     */
    @Override
    public void renderWidget(int mouseX, int mouseY, float partialTick) {
        Minecraft mc = Minecraft.getMinecraft();
        Tools.blitSprite(SPRITES.get(this.active, this.isHoveredOrFocused()), this.getX(), this.getY(), this.getWidth(), this.getHeight());

        String ellipsize = Tools.ellipsize(this.getMessage().getFormattedText(), this.width - 6);// Remove 6 pixels so that the text is always contained within the button's borders
        Tools.drawCenteredString(ellipsize, this.getX() + this.width / 2, this.getY() + (this.height - 8) / 2, getFGColor());
    }

    // 前景色方法，可以根据需要修改颜色
    @Override
    public int getFGColor() {
        return 0xFFFFFF;  // 设置为白色
    }
}
