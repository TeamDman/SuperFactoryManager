package ca.teamdman.sfm.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;

import javax.annotation.Nullable;

public interface IStackableScreen {
    void setParent(GuiScreen parent);

   @Nullable
   GuiScreen getParent();

    default void onClose() {
        if (this.getParent() != null) {
            Minecraft.getMinecraft().displayGuiScreen(this.getParent());
        } else {
            Minecraft.getMinecraft().displayGuiScreen(null);
        }
    }
}
