package vswe.superfactory.network.packets;

import net.minecraft.entity.player.EntityPlayer;

public interface IPacketBlock {
	void writeData(DataWriter dw, EntityPlayer player, boolean onServer, int id);

	void readData(DataReader dr, EntityPlayer player, boolean onServer, int id);

	int infoBitLength(boolean onServer);
}
