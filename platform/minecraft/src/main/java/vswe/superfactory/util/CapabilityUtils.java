package vswe.superfactory.util;

import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.ISidedInventory;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityInject;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.templates.EmptyFluidHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.SlotItemHandler;
import net.minecraftforge.items.wrapper.EmptyHandler;
import net.minecraftforge.items.wrapper.InvWrapper;
import net.minecraftforge.items.wrapper.SidedInvWrapper;

/**
 * Stolen from CCL, with permission from covers1624.
 * <p>
 * Created by covers1624 on 6/02/2018.
 */
public class CapabilityUtils {

	@CapabilityInject(IFluidHandler.class)
	public static final Capability<IFluidHandler> FLUID_HANDLER = null;

	@CapabilityInject(IItemHandler.class)
	public static final Capability<IItemHandler> ITEM_HANDLER = null;

	public static boolean hasFluidHandler(IBlockAccess world, BlockPos pos, EnumFacing face) {
		return hasFluidHandler(world.getTileEntity(pos), face);
	}

	//region hasFluidHandler
	public static boolean hasFluidHandler(TileEntity tile, EnumFacing face) {
		return tile != null && tile.hasCapability(FLUID_HANDLER, face);
	}

	public static boolean hasFluidHandler(IBlockAccess world, BlockPos pos, int face) {
		return hasFluidHandler(world.getTileEntity(pos), face);
	}

	public static boolean hasFluidHandler(TileEntity tile, int face) {
		return hasFluidHandler(tile, EnumFacing.VALUES[face]);
	}
	//endregion

	public static IFluidHandler getFluidHandler_Raw(IBlockAccess world, BlockPos pos, EnumFacing face) {
		return getFluidHandler_Raw(world.getTileEntity(pos), face);
	}

	//region getFluidHandler_Raw
	public static IFluidHandler getFluidHandler_Raw(TileEntity tile, EnumFacing face) {
		return tile.getCapability(FLUID_HANDLER, face);
	}

	public static IFluidHandler getFluidHandler_Raw(IBlockAccess world, BlockPos pos, int face) {
		return getFluidHandler_Raw(world.getTileEntity(pos), face);
	}

	public static IFluidHandler getFluidHandler_Raw(TileEntity tile, int face) {
		return getFluidHandler_Raw(tile, EnumFacing.VALUES[face]);
	}
	//endregion

	//region getFluidHandler
	public static IFluidHandler getFluidHandler(TileEntity tile, EnumFacing face) {
		return getFluidHandlerOr(tile, face, null);
	}

	//region getFluidHandlerOr
	public static IFluidHandler getFluidHandlerOr(TileEntity tile, EnumFacing face, IFluidHandler _default) {
		return hasFluidHandler(tile, face) ? getFluidHandler_Raw(tile, face) : _default;
	}

	public static IFluidHandler getFluidHandler(TileEntity tile, int face) {
		return getFluidHandlerOr(tile, face, null);
	}

	public static IFluidHandler getFluidHandlerOr(TileEntity tile, int face, IFluidHandler _default) {
		return hasFluidHandler(tile, face) ? getFluidHandler_Raw(tile, face) : _default;
	}
	//endregion

	public static IFluidHandler getFluidHandler(IBlockAccess world, BlockPos pos, EnumFacing face) {
		return getFluidHandlerOr(world, pos, face, null);
	}

	public static IFluidHandler getFluidHandlerOr(IBlockAccess world, BlockPos pos, EnumFacing face, IFluidHandler _default) {
		return getFluidHandlerOr(world.getTileEntity(pos), face, _default);
	}

	public static IFluidHandler getFluidHandler(IBlockAccess world, BlockPos pos, int face) {
		return getFluidHandlerOr(world, pos, face, null);
	}

	public static IFluidHandler getFluidHandlerOr(IBlockAccess world, BlockPos pos, int face, IFluidHandler _default) {
		return getFluidHandlerOr(world.getTileEntity(pos), face, _default);
	}
	//endregion

	//region getFluidHandlerOrEmpty
	public static IFluidHandler getFluidHandlerOrEmpty(TileEntity tile, EnumFacing face) {
		return getFluidHandlerOr(tile, face, EmptyFluidHandler.INSTANCE);
	}

	public static IFluidHandler getFluidHandlerOrEmpty(TileEntity tile, int face) {
		return getFluidHandlerOr(tile, face, EmptyFluidHandler.INSTANCE);
	}

	public static IFluidHandler getFluidHandlerOrEmpty(IBlockAccess world, BlockPos pos, EnumFacing face) {
		return getFluidHandlerOr(world.getTileEntity(pos), face, EmptyFluidHandler.INSTANCE);
	}

	public static IFluidHandler getFluidHandlerOrEmpty(IBlockAccess world, BlockPos pos, int face) {
		return getFluidHandlerOr(world.getTileEntity(pos), face, EmptyFluidHandler.INSTANCE);
	}
	//endregion

	//region hasItemHandler_Raw

	public static boolean hasItemHandler_Raw(IBlockAccess world, BlockPos pos, EnumFacing face) {
		return hasItemHandler_Raw(world.getTileEntity(pos), face);
	}

	/**
	 * Checks if only the capability exists on the tile for the specified face.
	 * Overloaded methods delegate to this in the end.
	 *
	 * @param tile The tile.
	 * @param face The face.
	 * @return If the tile has the cap.
	 */
	public static boolean hasItemHandler_Raw(TileEntity tile, EnumFacing face) {
		return tile != null && tile.hasCapability(ITEM_HANDLER, face);
	}

	public static boolean hasItemHandler_Raw(IBlockAccess world, BlockPos pos, int face) {
		return hasItemHandler_Raw(world.getTileEntity(pos), face);
	}

	public static boolean hasItemHandler_Raw(TileEntity tile, int face) {
		return hasItemHandler_Raw(tile, EnumFacing.VALUES[face]);
	}
	//endregion

	//region hasItemHandler

	public static boolean hasItemHandler(IBlockAccess world, BlockPos pos, EnumFacing face) {
		return hasItemHandler(world.getTileEntity(pos), face);
	}

	/**
	 * Checks if the capability exists on the tile for the specified face,
	 * Or if the tile is an instance of the Legacy, IInventory or ISidedInventory.
	 * Overloaded methods delegate to this in the end.
	 *
	 * @param tile The tile.
	 * @param face The face.
	 * @return If the tile has the cap, or uses legacy interfaces.
	 */
	public static boolean hasItemHandler(TileEntity tile, EnumFacing face) {
		return hasItemHandler_Raw(tile, face) || tile instanceof IInventory || tile instanceof ISidedInventory;
	}

	public static boolean hasItemHandler(IBlockAccess world, BlockPos pos, int face) {
		return hasItemHandler(world.getTileEntity(pos), face);
	}

	public static boolean hasItemHandler(TileEntity tile, int face) {
		return hasItemHandler(tile, EnumFacing.VALUES[face]);
	}
	//endregion

	//region getItemHandler_Raw

	public static IItemHandler getItemHandler_Raw(IBlockAccess world, BlockPos pos, EnumFacing face) {
		return getItemHandler_Raw(world.getTileEntity(pos), face);
	}

	/**
	 * Grabs the IItemHandler capability for the tile,
	 * Will wrap if the cap doesnt exist, If you care about only interacting with the cap,
	 * Then use {@link #hasItemHandler_Raw} to check if the tile only has the cap before calling.
	 *
	 * @param tile The tile.
	 * @param face The face.
	 * @return The handler, wrapped if the tile uses legacy interfaces and no cap.
	 */
	public static IItemHandler getItemHandler_Raw(TileEntity tile, EnumFacing face) {
		if (hasItemHandler(tile, face)) {
			if (hasItemHandler_Raw(tile, face)) {
				return tile.getCapability(ITEM_HANDLER, face);
			} else {
				if (tile instanceof ISidedInventory && face != null) {
					return new SidedInvWrapper((ISidedInventory) tile, face);
				} else {
					return new InvWrapper((IInventory) tile);
				}
			}
		}
		return null;
	}

	public static IItemHandler getItemHandler_Raw(IBlockAccess world, BlockPos pos, int face) {
		return getItemHandler_Raw(world.getTileEntity(pos), face);
	}

	public static IItemHandler getItemHandler_Raw(TileEntity tile, int face) {
		return getItemHandler_Raw(tile, EnumFacing.VALUES[face]);
	}
	//endregion

	//region getItemHandlerOr

	public static IItemHandler getItemHandlerOr(TileEntity tile, int face, IItemHandler _default) {
		return hasItemHandler(tile, face) ? getItemHandler_Raw(tile, face) : _default;
	}

	public static IItemHandler getItemHandlerOr(IBlockAccess world, BlockPos pos, int face, IItemHandler _default) {
		return getItemHandlerOr(world.getTileEntity(pos), face, _default);
	}

	/**
	 * Grabs the IITemHandler capability for the tile or null if none.
	 *
	 * @param tile The tile.
	 * @param face The face.
	 * @return The handler or null.
	 */
	public static IItemHandler getItemHandler(TileEntity tile, EnumFacing face) {
		return getItemHandlerOr(tile, face, null);
	}

	/**
	 * Grabs the IITemHandler capability for the tile or the default if none.
	 *
	 * @param tile     The tile.
	 * @param face     The face.
	 * @param _default The default.
	 * @return The handler or default.
	 */
	public static IItemHandler getItemHandlerOr(TileEntity tile, EnumFacing face, IItemHandler _default) {
		return hasItemHandler(tile, face) ? getItemHandler_Raw(tile, face) : _default;
	}
	//endregion

	//region getItemHandler

	public static IItemHandler getItemHandler(TileEntity tile, int face) {
		return getItemHandlerOr(tile, face, null);
	}

	public static IItemHandler getItemHandler(IBlockAccess world, BlockPos pos, EnumFacing face) {
		return getItemHandlerOr(world, pos, face, null);
	}

	public static IItemHandler getItemHandlerOr(IBlockAccess world, BlockPos pos, EnumFacing face, IItemHandler _default) {
		return getItemHandlerOr(world.getTileEntity(pos), face, _default);
	}

	public static IItemHandler getItemHandler(IBlockAccess world, BlockPos pos, int face) {
		return getItemHandlerOr(world, pos, face, null);
	}
	//endregion

	//region getItemHandlerOrEmpty

	/**
	 * Grabs the IITemHandler capability for the tile or EmptyHandler.INSTANCE if none.
	 *
	 * @param tile The tile.
	 * @param face The face.
	 * @return The handler or EmptyHandler.INSTANCE.
	 */
	public static IItemHandler getItemHandlerOrEmpty(TileEntity tile, EnumFacing face) {
		return getItemHandlerOr(tile, face, EmptyHandler.INSTANCE);
	}

	public static IItemHandler getItemHandlerOrEmpty(TileEntity tile, int face) {
		return getItemHandlerOr(tile, face, EmptyHandler.INSTANCE);
	}

	public static IItemHandler getItemHandlerOrEmpty(IBlockAccess world, BlockPos pos, EnumFacing face) {
		return getItemHandlerOr(world.getTileEntity(pos), face, EmptyHandler.INSTANCE);
	}

	public static IItemHandler getItemHandlerOrEmpty(IBlockAccess world, BlockPos pos, int face) {
		return getItemHandlerOr(world.getTileEntity(pos), face, EmptyHandler.INSTANCE);
	}
	//endregion

}
