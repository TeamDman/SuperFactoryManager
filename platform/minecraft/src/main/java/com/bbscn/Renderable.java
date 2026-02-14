package com.bbscn;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public interface Renderable {
    /**
     * Renders the graphical user interface (GUI) element.
     *
     * @param pMouseX      the x-coordinate of the mouse cursor.
     * @param pMouseY      the y-coordinate of the mouse cursor.
     * @param pPartialTick the partial tick time.
     */
    void render(int pMouseX, int pMouseY, float pPartialTick);
}
