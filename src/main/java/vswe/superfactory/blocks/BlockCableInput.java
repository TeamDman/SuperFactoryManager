package vswe.superfactory.blocks;

import net.minecraft.block.Block;
import net.minecraft.block.BlockContainer;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import vswe.superfactory.SuperFactoryManager;
import vswe.superfactory.tiles.TileEntityInput;

import java.util.Random;

public class BlockCableInput extends BlockContainer {
	public BlockCableInput() {
		super(Material.IRON);
		setCreativeTab(SuperFactoryManager.creativeTab);
		setSoundType(SoundType.METAL);
		setTranslationKey(SuperFactoryManager.UNLOCALIZED_START + "cable_input");
		setHardness(1.2F);
	}

	@Override
	public TileEntity createNewTileEntity(World world, int meta) {
		return new TileEntityInput();
	}

	@Override
	public EnumBlockRenderType getRenderType(IBlockState state) {
		return EnumBlockRenderType.MODEL;
	}

	@Override
	public void updateTick(World worldIn, BlockPos pos, IBlockState state, Random rand) {
		super.updateTick(worldIn, pos, state, rand);
	}

	@Override
	public void neighborChanged(IBlockState state, World world, BlockPos pos, Block blockIn, BlockPos fromPos) {
		updateRedstone(world, pos);
		super.neighborChanged(state, world, pos, blockIn, fromPos);
	}

	@Override
	public void onBlockAdded(World world, BlockPos pos, IBlockState state) {
		updateRedstone(world, pos);
		super.onBlockAdded(world, pos, state);
	}

	@Override
	public boolean requiresUpdates() {
		return super.requiresUpdates();
	}

	@Override
	public boolean canConnectRedstone(IBlockState state, IBlockAccess world, BlockPos pos, EnumFacing side) {
		return true;
	}

//	@Override
//	public boolean shouldCheckWeakPower(IBlockState state, IBlockAccess world, BlockPos pos, EnumFacing side) {
//		updateRedstone(world, pos);
//		return super.shouldCheckWeakPower(state, world, pos, side);
//	}

	@Override
	public boolean getWeakChanges(IBlockAccess world, BlockPos pos) {
//		updateRedstone(world, pos); // causes infinite loop
		return true;
	}

	private void updateRedstone(IBlockAccess world, BlockPos pos) {
		TileEntityInput input = (TileEntityInput) world.getTileEntity(pos);
		if (input != null) {
			input.triggerRedstone();
		}
	}

	private void updateRedstone(World world, BlockPos pos) {
		TileEntityInput input = (TileEntityInput) world.getTileEntity(pos);
		if (input != null) {
			input.triggerRedstone();
		}
	}
}
