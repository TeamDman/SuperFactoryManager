package vswe.superfactory.blocks;

import ca.teamdman.sfm.common.registry.IGuiProvider;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import vswe.superfactory.network.packets.DataReader;
import vswe.superfactory.network.packets.DataWriter;

public interface ITileEntityInterface extends IGuiProvider {


	void readAllData(DataReader dr, EntityPlayer player);

	void readUpdatedData(DataReader dr, EntityPlayer player);

	void writeAllData(DataWriter dw);
}
