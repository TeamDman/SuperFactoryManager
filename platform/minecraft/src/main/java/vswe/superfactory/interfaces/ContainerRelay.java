package vswe.superfactory.interfaces;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import vswe.superfactory.tiles.TileEntityRelay;
import vswe.superfactory.util.UserPermission;

import java.util.List;

public class ContainerRelay extends ContainerBase {
	public boolean              oldCreativeMode;
	public boolean              oldOpList;
	public List<UserPermission> oldPermissions;
	private TileEntityRelay relay;

	//    @Override
	//    public void onCraftGuiOpened(ICrafting player) {
	//        super.onCraftGuiOpened(player);
	//        PacketHandler.sendAllData(this, player, relay);
	//        oldPermissions = new ArrayList<UserPermission>();
	//        for (UserPermission permission : relay.getPermissions()) {
	//            oldPermissions.add(permission.copy());
	//        }
	//        oldCreativeMode = relay.isCreativeMode();
	//        oldOpList = relay.doesListRequireOp();
	//    }

	public ContainerRelay(TileEntityRelay relay, InventoryPlayer player) {
		super(relay, player);
		this.relay = relay;
	}

	@Override
	public void detectAndSendChanges() {
		super.detectAndSendChanges();

		if (oldPermissions != null) {
			relay.updateData(this);
		}
	}

	@Override
	public boolean canInteractWith(EntityPlayer entityplayer) {
		return entityplayer.getDistanceSq(relay.getPos().getX(), relay.getPos().getY(), relay.getPos().getZ()) <= 64;
	}

}
