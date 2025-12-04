package vswe.superfactory.blocks;

import net.minecraft.block.BlockContainer;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.properties.PropertyInteger;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.common.property.ExtendedBlockState;
import net.minecraftforge.common.property.IExtendedBlockState;
import net.minecraftforge.common.property.IUnlistedProperty;
import net.minecraftforge.common.property.Properties;
import vswe.superfactory.SuperFactoryManager;
import vswe.superfactory.tiles.TileEntityCluster;
import vswe.superfactory.tiles.TileEntityOutput;

import java.util.Random;

//This is indeed not a subclass to the cable, you can't relay signals through this block
public class BlockCableOutput extends BlockContainer {
	public static final IUnlistedProperty<Integer> STRONG_SIDES = new Properties.PropertyAdapter<Integer>(PropertyInteger.create("strong_sides", 0, 63)); // 000000 -> 111111
	public static final IUnlistedProperty<Integer> WEAK_SIDES   = new Properties.PropertyAdapter<Integer>(PropertyInteger.create("strong_sides", 0, 63)); // 000000 -> 111111

	public BlockCableOutput() {
		super(Material.IRON);
		setCreativeTab(SuperFactoryManager.creativeTab);
		setSoundType(SoundType.METAL);
		setTranslationKey(SuperFactoryManager.UNLOCALIZED_START + "cable_output");
		setHardness(1.2F);
	}

	@Override
	public TileEntity createNewTileEntity(World world, int var2) {
		return new TileEntityOutput();
	}

	@Override
	public EnumBlockRenderType getRenderType(IBlockState state) {
		return EnumBlockRenderType.MODEL;
	}

	@Override
	public void updateTick(World worldIn, BlockPos pos, IBlockState state, Random rand) {
		for (EnumFacing enumfacing : EnumFacing.values()) {
			worldIn.notifyNeighborsOfStateChange(pos.offset(enumfacing), this, true);
		}

		super.updateTick(worldIn, pos, state, rand);
		//        worldIn.notifyNeighborsOfStateExcept(pos, this, null);
	}

	@Override
	public int getWeakPower(IBlockState state, IBlockAccess blockAccess, BlockPos pos, EnumFacing side) {
		TileEntityOutput te = getTileEntity(blockAccess, pos);
		if (te != null) {
			return te.getStrengthFromOppositeSide(side);
		}
		return 0;
	}

	private TileEntityOutput getTileEntity(IBlockAccess world, BlockPos pos) {
		return TileEntityCluster.getTileEntity(TileEntityOutput.class, world, pos);
	}

	@Override
	public boolean canProvidePower(IBlockState state) {
		return true;
	}

	@Override
	public int getStrongPower(IBlockState state, IBlockAccess blockAccess, BlockPos pos, EnumFacing side) {
		TileEntityOutput te = getTileEntity(blockAccess, pos);
		if (te != null && te.hasStrongSignalAtOppositeSide(side)) {
			return te.getStrengthFromOppositeSide(side);
		}
		return 0;
	}

	@Override
	protected BlockStateContainer createBlockState() {
		IProperty[]         listedProperties   = new IProperty[0];
		IUnlistedProperty[] unlistedProperties = new IUnlistedProperty[]{STRONG_SIDES, WEAK_SIDES};
		return new ExtendedBlockState(this, listedProperties, unlistedProperties);
	}

	@Override
	public boolean canConnectRedstone(IBlockState state, IBlockAccess world, BlockPos pos, EnumFacing side) {
		return true;
	}

	@Override
	public boolean getWeakChanges(IBlockAccess world, BlockPos pos) {
		return true;
	}

	@Override
	public IBlockState getExtendedState(IBlockState state, IBlockAccess world, BlockPos pos) {
		TileEntityOutput tileEntity = (TileEntityOutput) world.getTileEntity(pos);
		if (state instanceof IExtendedBlockState && tileEntity != null) {
			int strongVals = 0;
			int weakVals   = 0;
			for (EnumFacing facing : EnumFacing.values()) {
				if (tileEntity.getStrengthFromSide(facing) > 0) {
					if (tileEntity.hasStrongSignalAtSide(facing)) {
						strongVals |= 1 << facing.getIndex();
					} else {
						weakVals |= 1 << facing.getIndex();
					}
				}
			}
			return ((IExtendedBlockState) state).withProperty(STRONG_SIDES, strongVals).withProperty(WEAK_SIDES, weakVals);
		}
		return state;
	}

}
