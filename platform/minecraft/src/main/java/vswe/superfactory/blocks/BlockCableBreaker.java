package vswe.superfactory.blocks;

import net.minecraft.block.BlockContainer;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.properties.PropertyDirection;
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
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import vswe.superfactory.SuperFactoryManager;
import vswe.superfactory.tiles.TileEntityBreaker;
import vswe.superfactory.tiles.TileEntityCluster;

//This is indeed not a subclass to the cable, you can't relay signals through this block
public class BlockCableBreaker extends BlockContainer {
	public static final IProperty FACING = BlockCableCluster.FACING;
	public static final IProperty FRONT  = BlockCableCluster.FRONT;

	public BlockCableBreaker() {
		super(Material.IRON);
		setCreativeTab(SuperFactoryManager.creativeTab);
		setSoundType(SoundType.METAL);
		setTranslationKey(SuperFactoryManager.UNLOCALIZED_START + "cable_breaker");
		setHardness(1.2F);
	}

	@Override
	public TileEntity createNewTileEntity(World world, int meta) {
		return new TileEntityBreaker();
	}

	@Override
	public IBlockState getStateFromMeta(int meta) {
		return getDefaultState().withProperty(FRONT, getSide(meta)).withProperty(FACING, getSide(meta));
	}

	public static EnumFacing getSide(int meta) {
		return EnumFacing.byIndex(meta % EnumFacing.values().length);
	}

	@Override
	public int getMetaFromState(IBlockState state) {
		return ((EnumFacing) state.getValue(FRONT)).getIndex();
	}

	@Override
	public IBlockState getActualState(IBlockState state, IBlockAccess worldIn, BlockPos pos) {
		TileEntityBreaker entityBreaker = (TileEntityBreaker) worldIn.getTileEntity(pos);
		if (entityBreaker != null && entityBreaker.getPlaceDirection() != null) {
			return state.withProperty(FACING, entityBreaker.getPlaceDirection()).withProperty(FRONT, getSide(getMetaFromState(state)));
		}
		return state.withProperty(FACING, getSide(getMetaFromState(state))).withProperty(FRONT, getSide(getMetaFromState(state)));
	}

	@Override
	public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player, EnumHand hand, EnumFacing facing, float hitX, float hitY, float hitZ) {
		if (player.isSneaking()) {
			facing = facing.getOpposite();
		}

		TileEntityBreaker breaker = TileEntityCluster.getTileEntity(TileEntityBreaker.class, world, pos);
		if (breaker != null && !breaker.isBlocked()) {
			breaker.setPlaceDirection(facing);
			return true;
		}

		return false;
	}

	@Override
	public void onBlockPlacedBy(World world, BlockPos pos, IBlockState state, EntityLivingBase entity, ItemStack item) {
		if (!world.isRemote) {
			EnumFacing facing = EnumFacing.getDirectionFromEntityLiving(pos, entity);
			world.setBlockState(pos, state.withProperty(FACING, facing).withProperty(FRONT, facing), 2);

			TileEntityBreaker breaker = TileEntityCluster.getTileEntity(TileEntityBreaker.class, world, pos);
			if (breaker != null) {
				breaker.setPlaceDirection(facing);
				breaker.setMetaData(facing.getIndex());
			}
		}
	}

	@Override
	public BlockStateContainer createBlockState() {
		return new BlockStateContainer(this, FACING, FRONT);
	}

	@Override
	public EnumBlockRenderType getRenderType(IBlockState state) {
		return EnumBlockRenderType.MODEL;
	}
}
