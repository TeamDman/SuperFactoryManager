package vswe.superfactory.blocks;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.CommonProxy;
import ca.teamdman.sfm.common.block_network.ICableBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockContainer;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.properties.PropertyBool;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import vswe.superfactory.SuperFactoryManager;
import vswe.superfactory.tiles.TileEntityManager;

public class BlockManager extends BlockContainer implements ICableBlock {
	public static final IProperty LIMITLESS = PropertyBool.create("limitless");

	public BlockManager() {
		super(Material.IRON);

		setSoundType(SoundType.METAL);
        setCreativeTab(SuperFactoryManager.creativeTab);
        setHardness(2F);
    }

	@Override
	public IBlockState getStateFromMeta(int meta) {
		return getDefaultState().withProperty(LIMITLESS, meta == 1);
	}

	@Override
	public int getMetaFromState(IBlockState state) {
		return (Boolean) state.getValue(LIMITLESS) ? 1 : 0;
	}

	@Override
	public void neighborChanged(IBlockState state, World worldIn, BlockPos pos, Block blockIn, BlockPos fromPos) {
		super.neighborChanged(state, worldIn, pos, blockIn, fromPos);
		updateInventories(worldIn, pos);

	}

	@Override
	public void onBlockAdded(World world, BlockPos pos, IBlockState state) {
		super.onBlockAdded(world, pos, state);
		updateInventories(world, pos);
	}

	@Override
	public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player, EnumHand hand, EnumFacing facing, float hitX, float hitY, float hitZ) {
		if (!world.isRemote) {
			player.openGui(SFM.instance, CommonProxy.GuiType.PROVIDER.ordinal(), world, pos.getX(), pos.getY(), pos.getZ());
		}
		return true;
	}

	@Override
	public void onBlockPlacedBy(World world, BlockPos pos, IBlockState state, EntityLivingBase entity, ItemStack itemStack) {
		super.onBlockPlacedBy(world, pos, state, entity, itemStack);
	}

	@Override
	protected BlockStateContainer createBlockState() {
		return new BlockStateContainer(this, LIMITLESS);
	}

	@Override
	public ItemStack getPickBlock(IBlockState state, RayTraceResult target, World world, BlockPos pos, EntityPlayer player) {
		return super.getPickBlock(state, target, world, pos, player);
	}

	@Override
	public void onNeighborChange(IBlockAccess world, BlockPos pos, BlockPos neighbor) {
		super.onNeighborChange(world, pos, neighbor);
		updateInventories((World) world, pos);
	}

	private void updateInventories(World world, BlockPos pos) {
		TileEntity tileEntity = world.getTileEntity(pos);
		if (tileEntity instanceof TileEntityManager) {
			((TileEntityManager) tileEntity).updateInventories();
		}
	}

	@Override
	public TileEntity createNewTileEntity(World world, int meta) {
		return new TileEntityManager();
	}

	@Override
	public EnumBlockRenderType getRenderType(IBlockState state) {
		return EnumBlockRenderType.MODEL;
	}

	@Override
	public void breakBlock(World world, BlockPos pos, IBlockState state) {
		super.breakBlock(world, pos, state);

		updateInventories(world, pos);
	}
}
