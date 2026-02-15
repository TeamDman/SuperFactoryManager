package vswe.superfactory.tiles;

import ca.teamdman.sfm.common.registry.registration.SFMBlocks;
import com.mojang.authlib.GameProfile;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.wrapper.InvWrapper;
import vswe.superfactory.blocks.BlockCableBreaker;
import vswe.superfactory.blocks.ClusterMethodRegistration;
import vswe.superfactory.network.packets.*;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

public class TileEntityBreaker extends TileEntityClusterElement implements IInventory, IPacketBlock {
	private static final UUID            FAKE_PLAYER_ID         = null;
	private static final String          FAKE_PLAYER_NAME       = "[SFM_PLAYER]";
	private static final String          NBT_DIRECTION          = "Direction";
	private static final int[]           ROTATION_SIDE_MAPPING  = {0, 0, 0, 2, 3, 1};
	private static final int             UPDATE_BUFFER_DISTANCE = 5;
	private              boolean         blocked;
	private              boolean         broken;
	private              boolean         hasUpdatedData;
	private              List<ItemStack> inventory;
	private              List<ItemStack> inventoryCache;
	private              boolean         missingPlaceDirection;
	private              EnumFacing      placeDirection;// = BlockCableBreaker.getSide(getBlockMetadata());

	@Override
	protected void writeContentToNBT(NBTTagCompound tagCompound) {
		tagCompound.setByte(NBT_DIRECTION, (byte) (placeDirection != null ? placeDirection.getIndex() : 0));
	}

	@Override
	protected void readContentFromNBT(NBTTagCompound tagCompound) {
		if (tagCompound.hasKey(NBT_DIRECTION)) {
			setPlaceDirection(EnumFacing.byIndex(tagCompound.getByte(NBT_DIRECTION)));
		} else {
			if (world != null) {
				setPlaceDirection(EnumFacing.byIndex(getBlockMetadata()));
			} else {
				missingPlaceDirection = true;
			}
		}
	}

	@Override
	public void update() {
		if (missingPlaceDirection) {
			setPlaceDirection(EnumFacing.byIndex(getBlockMetadata()));
			missingPlaceDirection = false;
		}
		if (world.isRemote) {
			keepClientDataUpdated();
		}

		if (inventory != null) {
			EnumFacing direction = EnumFacing.byIndex(getBlockMetadata() % EnumFacing.values().length);

			for (ItemStack itemStack : getInventoryForDrop()) {
				List<ItemStack> items = placeItem(itemStack);
				if (items != null && !items.isEmpty()) {
					for (ItemStack item : items) {
						double x = getPos().getX() + 0.5 + direction.getXOffset() * 0.75;
						double y = getPos().getY() + 0.5 + direction.getYOffset() * 0.75;
						double z = getPos().getZ() + 0.5 + direction.getZOffset() * 0.75;


						if (direction.getYOffset() == 0) {
							y -= 0.1;
						}

						EntityItem entityitem = new EntityItem(world, x, y, z, item);

						entityitem.motionX = direction.getXOffset() * 0.1;
						entityitem.motionY = direction.getYOffset() * 0.1;
						entityitem.motionZ = direction.getZOffset() * 0.1;

						entityitem.setPickupDelay(40);
						world.spawnEntity(entityitem);
					}
				}
			}
		}
		inventory = null;
		inventoryCache = null;
		broken = false;
	}

	private List<ItemStack> placeItem(ItemStack itemstack) {
		List<ItemStack> items = new ArrayList<ItemStack>();

		if (!itemstack.isEmpty() && itemstack.getItem() != null && itemstack.getCount() > 0) {
			EnumFacing side      = EnumFacing.byIndex(getBlockMetadata() % EnumFacing.values().length);
			EnumFacing direction = placeDirection.getOpposite();

			float hitX = 0.5F + direction.getXOffset() * 0.5F;
			float hitY = 0.5F + direction.getYOffset() * 0.5F;
			float hitZ = 0.5F + direction.getZOffset() * 0.5F;

			EntityPlayerMP player       = FakePlayerFactory.get((WorldServer) world, new GameProfile(FAKE_PLAYER_ID, FAKE_PLAYER_NAME));
			int            rotationSide = ROTATION_SIDE_MAPPING[direction.ordinal()];

			player.prevRotationPitch = player.rotationYaw = rotationSide * 90;
			player.prevRotationYaw = player.rotationPitch = direction == EnumFacing.UP ? 90 : direction == EnumFacing.DOWN ? -90 : 0;
			player.prevPosX = player.posX = getPos().getX() + side.getXOffset() + 0.5 + direction.getXOffset() * 0.4;
			player.prevPosY = player.posY = getPos().getY() + side.getYOffset() + 0.5 + direction.getYOffset() * 0.4;
			player.prevPosZ = player.posZ = getPos().getZ() + side.getZOffset() + 0.5 + direction.getZOffset() * 0.4;
			player.eyeHeight = 0;
			player.interactionManager.setBlockReachDistance(1);

			blocked = true;
			try {
				player.inventory.clear();
				player.inventory.currentItem = 0;
				player.inventory.setInventorySlotContents(0, itemstack);
				ActionResult<ItemStack> result = itemstack.useItemRightClick(world, player, EnumHand.MAIN_HAND);
				if (result.getType().equals(EnumActionResult.PASS) && ItemStack.areItemStacksEqual(result.getResult(), itemstack)) {
					int x = getPos().getX() + side.getXOffset() - direction.getXOffset();
					int y = getPos().getY() + side.getYOffset() - direction.getYOffset();
					int z = getPos().getZ() + side.getZOffset() - direction.getZOffset();

					player.interactionManager.processRightClickBlock(player, world, itemstack, EnumHand.MAIN_HAND, new BlockPos(x, y, z), direction, hitX, hitY, hitZ);

				} else {
					player.inventory.setInventorySlotContents(0, result.getResult());
				}
			} catch (Exception ignored) {

			} finally {
				for (ItemStack itemStack : player.inventory.mainInventory) {
					if (!itemStack.isEmpty() && itemStack.getCount() > 0) {
						items.add(itemStack);
					}
				}
				blocked = false;
			}

		}

		return items;
	}

	private List<ItemStack> getInventoryForDrop() {
		List<ItemStack> ret = new ArrayList<ItemStack>();
		for (ItemStack itemStack : inventory) {
			if (!itemStack.isEmpty()) {
				ItemStack newStack = itemStack.copy();


				if (!broken) {
					for (int i = 0; i < inventoryCache.size(); i++) {
						ItemStack copyStack = inventoryCache.get(i);

						if (!copyStack.isEmpty() && newStack.isItemEqual(copyStack) && ItemStack.areItemStackTagsEqual(newStack, copyStack)) {
							int max = Math.min(copyStack.getCount(), newStack.getCount());

							copyStack.shrink(max);
							if (copyStack.getCount() == 0) {
								inventoryCache.set(0, ItemStack.EMPTY);
							}

							newStack.shrink(max);
							if (newStack.getCount() == 0) {
								newStack = ItemStack.EMPTY;
								break;
							}
						}
					}
				}


				if (!newStack.isEmpty()) {
					ret.add(newStack);
				}
			}
		}
		return ret;
	}

	@Override
	protected EnumSet<ClusterMethodRegistration> getRegistrations() {
		return EnumSet.of(ClusterMethodRegistration.ON_BLOCK_PLACED_BY, ClusterMethodRegistration.ON_BLOCK_ACTIVATED);
	}

	@SideOnly(Side.CLIENT)
	private void keepClientDataUpdated() {
		if (isPartOfCluster()) {
			return;
		}

		double distance = Minecraft.getMinecraft().player.getDistanceSq(getPos().getX() + 0.5, getPos().getY() + 0.5, getPos().getZ() + 0.5);

		if (distance > Math.pow(PacketHandler.BLOCK_UPDATE_RANGE, 2)) {
			hasUpdatedData = false;
		} else if (!hasUpdatedData && distance < Math.pow(PacketHandler.BLOCK_UPDATE_RANGE - UPDATE_BUFFER_DISTANCE, 2)) {
			hasUpdatedData = true;
			PacketHandler.sendBlockPacket(this, Minecraft.getMinecraft().player, 0);
		}
	}

	@Override
	public int getSizeInventory() {
		return getInventory().size() + 1;
	}

	private List<ItemStack> getInventory() {
		if (inventory == null) {
			EnumFacing direction = EnumFacing.byIndex(getBlockMetadata() % EnumFacing.values().length);

			int         x     = getPos().getX() + direction.getXOffset();
			int         y     = getPos().getY() + direction.getYOffset();
			int         z     = getPos().getZ() + direction.getZOffset();
			BlockPos    pos   = new BlockPos(x, y, z);
			IBlockState state = world.getBlockState(pos);
			if (canBreakBlock(state, state.getBlock(), pos)) {
				inventory = state.getBlock().getDrops(world, pos, state, 0);
			}
			if (inventory == null) {
				inventory = new ArrayList<ItemStack>();
			}
			inventoryCache = new ArrayList<ItemStack>();
			for (ItemStack itemStack : inventory) {
				inventoryCache.add(itemStack.copy());
			}
		}

		return inventory;
	}

	@Override
	public boolean isEmpty() {
		return false;
	}

	@Override
	public ItemStack getStackInSlot(int id) {
		if (id < getInventory().size()) {
			return getInventory().get(id);
		} else {
			return ItemStack.EMPTY;
		}
	}

	@Override
	public ItemStack decrStackSize(int id, int count) {

		ItemStack item = getStackInSlot(id);
		if (!item.isEmpty()) {
			if (item.getCount() <= count) {
				getInventory().set(id, ItemStack.EMPTY);
				return item;
			}

			ItemStack ret = item.splitStack(count);

			if (item.getCount() == 0) {
				getInventory().set(id, ItemStack.EMPTY);
			}

			return ret;
		} else {
			return ItemStack.EMPTY;
		}

	}

	@Override
	public ItemStack removeStackFromSlot(int i) {
		return ItemStack.EMPTY;
	}

	@Override
	public void setInventorySlotContents(int id, ItemStack itemstack) {
		if (id < getInventory().size()) {
			getInventory().set(id, itemstack);
		} else {
			getInventory().add(itemstack);
			inventoryCache.add(ItemStack.EMPTY);
		}
	}

	@Override
	public int getInventoryStackLimit() {
		return 64;
	}

	@Override
	public boolean isUsableByPlayer(EntityPlayer entityplayer) {
		return false;
	}

	@Override
	public void openInventory(EntityPlayer player) {

	}

	@Override
	public void closeInventory(EntityPlayer player) {

	}

	@Override
	public boolean isItemValidForSlot(int i, ItemStack itemstack) {
		return true;
	}

	@Override
	public int getField(int id) {
		return 0;
	}

	@Override
	public void setField(int id, int value) {

	}

	@Override
	public int getFieldCount() {
		return 0;
	}

	@Override
	public void clear() {

	}

	@Override
	public String getName() {
		return SFMBlocks.CABLE_BREAKER.getLocalizedName();
	}

	@Override
	public boolean hasCustomName() {
		return true;
	}

	@Override
	public void writeData(DataWriter dw, EntityPlayer player, boolean onServer, int id) {
		if (onServer) {
			if (placeDirection == null)
				placeDirection = BlockCableBreaker.getSide(getBlockMetadata()); //might be a cheap fix, but seams to some kind of sync bug between threads or something
			dw.writeData(placeDirection.getIndex(), DataBitHelper.PLACE_DIRECTION);
		} else {
			//nothing to write, empty packet
		}
	}

	@Override
	public void readData(DataReader dr, EntityPlayer player, boolean onServer, int id) {
		if (onServer) {
			//respond by sending the data to the client that required it
			PacketHandler.sendBlockPacket(this, player, 0);
		} else {
			int val = dr.readData(DataBitHelper.PLACE_DIRECTION);
			setPlaceDirection(EnumFacing.byIndex(val));
			world.notifyBlockUpdate(getPos(), getWorld().getBlockState(getPos()), getWorld().getBlockState(getPos()), 3);
			markDirty();
		}
	}

	@Override
	public int infoBitLength(boolean onServer) {
		return 0;
	}

	public EnumFacing getPlaceDirection() {
		return placeDirection;
	}

	public void setPlaceDirection(EnumFacing placeDirection) {
		if (this.placeDirection != placeDirection) {
			this.placeDirection = placeDirection;
			this.markDirty();

			if (!isPartOfCluster() && world != null && !world.isRemote) {
				PacketHandler.sendBlockPacket(this, null, 0);
			}
		}
	}

	@Override
	public void markDirty() {
		super.markDirty();

		if (inventory != null && !broken) {
			boolean match = true;
			for (int i = 0; i < inventory.size(); i++) {
				ItemStack itemStack     = inventory.get(i);
				ItemStack itemStackCopy = inventoryCache.get(i);

				if (!itemStackCopy.isEmpty() && (itemStack.isEmpty() || Item.getIdFromItem(itemStack.getItem()) != Item.getIdFromItem(itemStackCopy.getItem()) || itemStack.getItemDamage() != itemStackCopy.getItemDamage() || !ItemStack.areItemStackTagsEqual(itemStack, itemStackCopy) || itemStack.getCount() < itemStackCopy.getCount())) {
					match = false;
					break;
				}
			}

			if (!match) {
				EnumFacing direction = EnumFacing.byIndex(getBlockMetadata() % EnumFacing.values().length);

				int x = getPos().getX() + direction.getXOffset();
				int y = getPos().getY() + direction.getYOffset();
				int z = getPos().getZ() + direction.getZOffset();

				BlockPos    pos   = new BlockPos(x, y, z);
				IBlockState state = world.getBlockState(pos);
				Block       block = state.getBlock();


				if (canBreakBlock(state, block, pos)) {
					broken = true;
					int meta = state.getBlock().getMetaFromState(state);
					block.breakBlock(world, pos, state);
					//                   worldObj.playAuxSFX(2001, pos, Block.getIdFromBlock(block) + (meta << 12));
					world.setBlockToAir(pos);
				}

			}
		}
	}

	@Override
	public ITextComponent getDisplayName() {
		return new TextComponentString(SFMBlocks.CABLE_BREAKER.getLocalizedName());
	}

	@Override
	public boolean hasCapability(Capability<?> capability, EnumFacing facing) {
		if (capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) {
			return true;
		}
		return super.hasCapability(capability, facing);
	}

	@Override
	public <T> T getCapability(Capability<T> capability, EnumFacing facing) {
		if (capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) {
			return (T) new InvWrapper(this);
		}
		return super.getCapability(capability, facing);
	}

	private boolean canBreakBlock(IBlockState state, Block block, BlockPos pos) {
		return block != null && Block.getIdFromBlock(block) != Block.getIdFromBlock(Blocks.BEDROCK) && block.getBlockHardness(state, world, pos) >= 0;
	}

	public boolean isBlocked() {
		return blocked;
	}
}
