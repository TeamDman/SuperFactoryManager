package vswe.superfactory.blocks;

import net.minecraft.entity.player.EntityPlayer;

import ca.teamdman.sfm.common.registry.IGuiProvider;
import vswe.superfactory.network.packets.DataReader;
import vswe.superfactory.network.packets.DataWriter;

public interface ITileEntityInterface extends IGuiProvider {

    void readAllData(DataReader dr, EntityPlayer player);

    void readUpdatedData(DataReader dr, EntityPlayer player);

    void writeAllData(DataWriter dw);
}
