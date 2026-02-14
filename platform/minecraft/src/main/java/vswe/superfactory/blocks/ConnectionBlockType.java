package vswe.superfactory.blocks;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.items.CapabilityItemHandler;
import vswe.superfactory.Localization;
import vswe.superfactory.tiles.*;

import javax.annotation.Nullable;

public enum ConnectionBlockType {
	INVENTORY(Localization.TYPE_INVENTORY, null, CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, false),
	TANK(Localization.TYPE_TANK, null, CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, false),
	EMITTER(Localization.TYPE_EMITTER, TileEntityOutput.class, null, false),
	RECEIVER(Localization.TYPE_RECEIVER, TileEntityInput.class, null, false),
	NODE(Localization.TYPE_NODE, IRedstoneNode.class, null, true),
	BUD(Localization.TYPE_BUD, TileEntityBUD.class, null, false),
	CAMOUFLAGE(Localization.TYPE_CAMOUFLAGE, TileEntityCamouflage.class, null, false),
	SIGN(Localization.TYPE_SIGN, TileEntitySignUpdater.class, null, false);

	private Capability   capability;
	private Class        clazz;
	private boolean      group;
	private Localization name;

	ConnectionBlockType(Localization name, @Nullable Class clazz, @Nullable Capability capability, boolean group) {
		this.name = name;
		this.clazz = clazz;
		this.capability = capability;
		this.group = group;
	}

	public boolean isInstance(TileEntity tileEntity) {
		if (clazz != null && clazz.isInstance(tileEntity)) {
			return true;
		}
		for (EnumFacing facing : EnumFacing.VALUES) {
			if (capability != null && tileEntity != null && tileEntity.hasCapability(capability, facing))
				return true;
		}
		return false;
	}

	public <T> T getObject(TileEntity tileEntity) {
		return (T) tileEntity;
	}

	public boolean isGroup() {
		return group;
	}

	public Localization getName() {
		return name;
	}

	@Override
	public String toString() {
		return name.toString();
	}
}
