package ca.teamdman.sfm.common.registry;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public interface IGuiProvider {
    Container getContainer(int id, InventoryPlayer inv);

    @SideOnly(Side.CLIENT)
    GuiScreen getGui(int id, InventoryPlayer inv);
}
