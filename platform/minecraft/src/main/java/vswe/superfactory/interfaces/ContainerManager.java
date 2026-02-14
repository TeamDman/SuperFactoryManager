package vswe.superfactory.interfaces;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.IContainerListener;
import net.minecraft.tileentity.TileEntity;
import vswe.superfactory.blocks.ConnectionBlock;
import vswe.superfactory.components.FlowComponent;
import vswe.superfactory.network.packets.PacketHandler;
import vswe.superfactory.tiles.TileEntityManager;
import vswe.superfactory.util.WorldCoordinate;

import java.util.ArrayList;
import java.util.List;

public class ContainerManager extends ContainerBase {
	private TileEntityManager manager;
	private List<FlowComponent>   oldComponents;
	private int                   oldIdIndexToRemove;
	private List<WorldCoordinate> oldInventories;
	private EntityPlayer      player;

	public ContainerManager(TileEntityManager manager, InventoryPlayer player) {
		super(manager, player);
		this.manager = manager;
		this.player = player.player;
	}

	@Override
	public void addListener(IContainerListener listener) {
		super.addListener(listener);

		PacketHandler.sendAllData(this, listener, manager);
		oldComponents = new ArrayList<FlowComponent>();
		for (FlowComponent component : manager.getFlowItems()) {
			oldComponents.add(component.copy());
		}
		manager.updateInventories();
		oldInventories = new ArrayList<WorldCoordinate>();
		for (ConnectionBlock connection : manager.getConnectedInventories()) {
			oldInventories.add(new WorldCoordinate(connection.getTileEntity().getPos().getX(), connection.getTileEntity().getPos().getY(), connection.getTileEntity().getPos().getZ()));
		}
		oldIdIndexToRemove = manager.getRemovedIds().size();
	}

	@Override
	public void detectAndSendChanges() {
		super.detectAndSendChanges();

		if (oldComponents != null) {
			if (oldIdIndexToRemove < manager.getRemovedIds().size()) {
				int idToRemove = manager.getRemovedIds().get(oldIdIndexToRemove);
				oldIdIndexToRemove++;
				manager.removeFlowComponent(idToRemove, oldComponents);
				PacketHandler.sendRemovalPacket(this, idToRemove);
			}

			for (int i = 0; i < manager.getFlowItems().size(); i++) {
				if (i >= oldComponents.size()) {
					PacketHandler.sendNewFlowComponent(this, manager.getFlowItems().get(i));
					oldComponents.add(manager.getFlowItems().get(i).copy());
				} else {
					oldComponents.get(i).refreshData(this, manager.getFlowItems().get(i));
				}
			}

			boolean hasInventoriesChanged = oldInventories.size() != manager.getConnectedInventories().size();

			if (!hasInventoriesChanged) {
				for (int i = 0; i < oldInventories.size(); i++) {
					TileEntity tileEntity = manager.getConnectedInventories().get(i).getTileEntity();
					if (oldInventories.get(i).equals(new WorldCoordinate(tileEntity.getPos().getX(), tileEntity.getPos().getY(), tileEntity.getPos().getZ()))) {
						hasInventoriesChanged = true;
						break;
					}
				}
			}

			if (hasInventoriesChanged) {
				oldInventories.clear();
				for (ConnectionBlock connection : manager.getConnectedInventories()) {
					oldInventories.add(new WorldCoordinate(connection.getTileEntity().getPos().getX(), connection.getTileEntity().getPos().getY(), connection.getTileEntity().getPos().getZ()));
				}
				PacketHandler.sendUpdateInventoryPacket(this);
			}
		}
	}

	@Override
	public boolean canInteractWith(EntityPlayer entityplayer) {
		return entityplayer.getDistanceSq(manager.getPos().getX(), manager.getPos().getY(), manager.getPos().getZ()) <= 64;
	}
}
