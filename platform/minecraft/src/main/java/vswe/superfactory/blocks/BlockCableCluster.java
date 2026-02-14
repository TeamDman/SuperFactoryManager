package vswe.superfactory.blocks;

import ca.teamdman.sfm.common.block_network.ICableBlock;
import ca.teamdman.sfm.common.registry.SFMBlocks;
import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.properties.PropertyBool;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.common.property.ExtendedBlockState;
import net.minecraftforge.common.property.IExtendedBlockState;
import net.minecraftforge.common.property.IUnlistedProperty;
import vswe.superfactory.SuperFactoryManager;
import vswe.superfactory.interfaces.IItemBlockProvider;
import vswe.superfactory.tiles.TileEntityCluster;

import java.util.ArrayList;

public class BlockCableCluster extends BlockCamouflageBase implements ICableBlock, IItemBlockProvider {
	public static final IProperty ADVANCED = PropertyBool.create("advanced");
	public static final IProperty FACING   = PropertyDirection.create("facing");
	public static final IProperty FRONT = PropertyDirection.create("front");
	public BlockCableCluster() {
		super(Material.IRON);
		setCreativeTab(SuperFactoryManager.creativeTab);
		setSoundType(SoundType.METAL);
		setHardness(2F);
	}

	@Override
	public IBlockState getStateFromMeta(int meta) {
		return getDefaultState().withProperty(ADVANCED, isAdvanced(meta)).withProperty(FACING, getSide(meta)).withProperty(FRONT, getSide(meta).getOpposite());
	}

	@Override
	public int getMetaFromState(IBlockState state) {
		return addAdvancedMeta(((EnumFacing) state.getValue(FACING)).getIndex(), ((Boolean) state.getValue(ADVANCED)) ? 8 : 0);
	}

	@Override
	public void neighborChanged(IBlockState state, World world, BlockPos pos, Block blockIn, BlockPos fromPos) {
		TileEntityCluster cluster = getTe(world, pos);
		Block             block   = world.getBlockState(pos).getBlock();

		if (cluster != null) {
			cluster.onNeighborBlockChange(world, pos, state, block);
		}

		if (isAdvanced(state.getBlock().getMetaFromState(state))) {
			BlockCable.bustCaches(world, pos, fromPos);
		}
		super.neighborChanged(state, world, pos, blockIn, fromPos);
	}

	@Override
	public void onBlockAdded(World world, BlockPos pos, IBlockState state) {
		if (world.getChunk(pos).getTileEntity(pos, null) != null) {
			//		if (world.loadedTileEntityList.contains(pos))
			TileEntityCluster cluster = getTe(world, pos);

			if (cluster != null) {
				cluster.onBlockAdded(world, pos, state);
			}
		}
		if (isAdvanced(state.getBlock().getMetaFromState(state))) {
			BlockCable.onNeighborChange(world, pos);
		}
	}

	@Override
	public int damageDropped(IBlockState state) {
		return getAdvancedMeta(state.getBlock().getMetaFromState(state));
	}

	@Override
	public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player, EnumHand hand, EnumFacing facing, float hitX, float hitY, float hitZ) {
		TileEntityCluster cluster = getTe(world, pos);

		if (cluster != null) {
			return cluster.onBlockActivated(world, pos, state, player, hand, player.getHeldItem(hand), facing, hitX, hitY, hitZ);
		}
		return false;
	}

	@Override
	public int getWeakPower(IBlockState state, IBlockAccess world, BlockPos pos, EnumFacing side) {
		TileEntityCluster cluster = getTe(world, pos);

		if (cluster != null) {
			return cluster.isProvidingWeakPower(state, world, pos, side);
		}

		return 0;
	}

	@Override
	public int getStrongPower(IBlockState state, IBlockAccess blockAccess, BlockPos pos, EnumFacing side) {
		TileEntityCluster cluster = getTe(blockAccess, pos);

		if (cluster != null) {
			return cluster.isProvidingStrongPower(state, blockAccess, pos, side);
		}
		return 0;
	}

	@Override
	public void onBlockPlacedBy(World world, BlockPos pos, IBlockState state, EntityLivingBase entity, ItemStack itemStack) {
		TileEntityCluster cluster = getTe(world, pos);

		if (cluster != null) {
			cluster.loadElements(itemStack);
			cluster.onBlockPlacedBy(world, pos, state, entity, itemStack);
		}
	}

	private TileEntityCluster getTe(IBlockAccess world, BlockPos pos) {
		TileEntity te = world.getTileEntity(pos);
		if (te != null && te instanceof TileEntityCluster) {
			return (TileEntityCluster) te;
		}
		return null;
	}

	@Override
	public boolean hasTileEntity(IBlockState state) {
		return true;
	}

	@Override
	public void getSubBlocks(CreativeTabs item, NonNullList<ItemStack> list) {
		list.add(new ItemStack(this, 1, 0));
		list.add(new ItemStack(this, 1, 8));
	}

	@Override
	protected BlockStateContainer createBlockState() {
		IProperty[]         listedProperties   = new IProperty[]{ADVANCED, FACING, FRONT};
		IUnlistedProperty[] unlistedProperties = new IUnlistedProperty[]{BlockCableCamouflages.BLOCK_POS};
		return new ExtendedBlockState(this, listedProperties, unlistedProperties);
	}

	@Override
	public ArrayList<ItemStack> getDrops(IBlockAccess world, BlockPos pos, IBlockState state, int fortune) {
		ArrayList<ItemStack> drop = new ArrayList<ItemStack>();
		drop.add(getItemStack(world, pos, state));
		return drop;
	}

	@Override
	public boolean canConnectRedstone(IBlockState state, IBlockAccess world, BlockPos pos, EnumFacing side) {
		TileEntityCluster cluster = getTe(world, pos);

		if (cluster != null) {
			return cluster.canConnectRedstone(state, world, pos, side);
		}
		return false;
	}

	@Override
	public ItemStack getPickBlock(IBlockState state, RayTraceResult target, World world, BlockPos pos, EntityPlayer player) {
		ItemStack itemStack = getItemStack(world, pos, world.getBlockState(pos));
		if (!itemStack.isEmpty()) {
			return itemStack;
		}
		return super.getPickBlock(state, target, world, pos, player);
	}

	private ItemStack getItemStack(IBlockAccess world, BlockPos pos, IBlockState state) {
		TileEntity te = world.getTileEntity(pos);

		if (te instanceof TileEntityCluster) {
			TileEntityCluster cluster   = (TileEntityCluster) te;
			ItemStack         itemStack = new ItemStack(SFMBlocks.CABLE_CLUSTER, 1, damageDropped(state));
			NBTTagCompound    compound  = new NBTTagCompound();
			itemStack.setTagCompound(compound);
			NBTTagCompound cable = new NBTTagCompound();
			compound.setTag(ItemCluster.NBT_CABLE, cable);
			cable.setByteArray(ItemCluster.NBT_TYPES, cluster.getTypes());
			return itemStack;
		}
		return ItemStack.EMPTY;
	}

	@Override
	public void onNeighborChange(IBlockAccess world, BlockPos pos, BlockPos neighbor) {
		TileEntityCluster cluster = getTe(world, pos);
		IBlockState       state   = world.getBlockState(pos);
		Block             block   = world.getBlockState(pos).getBlock();

		if (cluster != null) {
			cluster.onNeighborBlockChange(world, pos, state, block);
		}

		if (isAdvanced(state.getBlock().getMetaFromState(state))) {
			BlockCable.onNeighborChange(world, pos);
		}
	}

	@Override
	public boolean shouldCheckWeakPower(IBlockState state, IBlockAccess world, BlockPos pos, EnumFacing side) {
		TileEntityCluster cluster = getTe(world, pos);

		if (cluster != null) {
			return cluster.shouldCheckWeakPower(state, world, pos, side);
		}

		return false;
	}

	@Override
	public IBlockState getExtendedState(IBlockState state, IBlockAccess world, BlockPos pos) {
		TileEntityCluster tileEntity = (TileEntityCluster) world.getTileEntity(pos);
		if (state instanceof IExtendedBlockState && tileEntity != null) {
			return ((IExtendedBlockState) state).withProperty(BlockCableCamouflages.BLOCK_POS, pos);
		}
		return state;
	}

	private int getAdvancedMeta(int meta) {
		return addAdvancedMeta(0, meta);
	}

	private int addAdvancedMeta(int meta, int advancedMeta) {
		return meta | (advancedMeta & 8);
	}

	public static boolean isAdvanced(int meta) {
		return (meta & 8) != 0;
	}

	public EnumFacing getSide(int meta) {
		return EnumFacing.byIndex(getSideMeta(meta));
	}

	public int getSideMeta(int meta) {
		return meta & 7;
	}

	@Override
	public void breakBlock(World world, BlockPos pos, IBlockState state) {
		super.breakBlock(world, pos, state);
		if (isAdvanced(state.getBlock().getMetaFromState(state))) {
			BlockCable.onNeighborChange(world, pos);
		}
	}

	@Override
	public TileEntity createNewTileEntity(World world, int meta) {
		return new TileEntityCluster();
	}

	@Override
	public boolean isOpaqueCube(IBlockState state) {
		return false;
	}

	@Override
	public boolean isFullCube(IBlockState state) {
		return false;
	}


	@Override
	public ItemBlock getItem() {
		return new ItemCluster(this);
	}
}
