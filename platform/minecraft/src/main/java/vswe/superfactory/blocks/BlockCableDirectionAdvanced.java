package vswe.superfactory.blocks;

import net.minecraft.block.BlockContainer;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.properties.PropertyBool;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import vswe.superfactory.SuperFactoryManager;
import vswe.superfactory.tiles.TileEntityCluster;
import vswe.superfactory.tiles.TileEntityClusterElement;

public abstract class BlockCableDirectionAdvanced extends BlockContainer {
	public static final IProperty ADVANCED = BlockCableCluster.ADVANCED;//PropertyBool.create("advanced");
	public static final IProperty FACING   = BlockCableCluster.FACING;//PropertyDirection.create("facing");
	public BlockCableDirectionAdvanced() {
		super(Material.IRON);
		setCreativeTab(SuperFactoryManager.creativeTab);
		setSoundType(SoundType.METAL);
		setHardness(1.2F);
		this.setDefaultState(this.blockState.getBaseState().withProperty(FACING, EnumFacing.NORTH).withProperty(ADVANCED, false));
	}

	@Override
	public IBlockState getStateFromMeta(int meta) {
		return getDefaultState().withProperty(ADVANCED, isAdvanced(meta)).withProperty(FACING, getSide(meta));
	}

	@Override
	public int getMetaFromState(IBlockState state) {
		return addAdvancedMeta(((EnumFacing) state.getValue(FACING)).getIndex(), ((Boolean) state.getValue(ADVANCED)) ? 8 : 0);
	}

	private int addAdvancedMeta(int meta, int advancedMeta) {
		return meta | (advancedMeta & 8);
	}

	@Override
	public int damageDropped(IBlockState state) {
		return getAdvancedMeta(state.getBlock().getMetaFromState(state));
	}

	@Override
	public void onBlockPlacedBy(World world, BlockPos pos, IBlockState state, EntityLivingBase entity, ItemStack item) {
		int meta = addAdvancedMeta(EnumFacing.getDirectionFromEntityLiving(pos, entity).getIndex(), item.getItemDamage());

		TileEntityClusterElement element = TileEntityCluster.getTileEntity(getTeClass(), world, pos);
		if (element != null) {
			element.setMetaData(meta);
		}
	}

	protected abstract Class<? extends TileEntityClusterElement> getTeClass();

	@Override
	public void getSubBlocks(CreativeTabs itemIn, NonNullList<ItemStack> list) {
		list.add(new ItemStack(this, 1, 0));
		list.add(new ItemStack(this, 1, 8));
	}

	@Override
	protected BlockStateContainer createBlockState() {
		return new BlockStateContainer(this, ADVANCED, FACING);
	}

	private int getAdvancedMeta(int meta) {
		return addAdvancedMeta(0, meta);
	}

	public static boolean isAdvanced(int meta) {
		return (meta & 8) != 0;
	}

	public EnumFacing getSide(int meta) {
		return EnumFacing.byIndex(getSideMeta(meta));
	}

	public static int getSideMeta(int meta) {
		return meta & 7;
	}

	@Override
	public EnumBlockRenderType getRenderType(IBlockState state) {
		return EnumBlockRenderType.MODEL;
	}
}
