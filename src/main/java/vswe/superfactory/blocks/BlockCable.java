package vswe.superfactory.blocks;

import ca.teamdman.sfm.common.registry.SFMBlocks;
import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import vswe.superfactory.SuperFactoryManager;
import vswe.superfactory.api.ICable;
import vswe.superfactory.tiles.TileEntityManager;
import vswe.superfactory.util.WorldCoordinate;

import java.util.ArrayList;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Queue;

public class BlockCable extends Block implements ICable {
	public BlockCable() {
		super(Material.IRON);
		setCreativeTab(SuperFactoryManager.creativeTab);
		setSoundType(SoundType.METAL);
		setTranslationKey(SuperFactoryManager.UNLOCALIZED_START + "cable");
		setHardness(0.4F);
	}

	@Override
	public void onBlockAdded(World world, BlockPos pos, IBlockState state) {
		super.onBlockAdded(world, pos, state);

		updateInventories(world, pos);
	}

	@Override
	public void breakBlock(World world, BlockPos pos, IBlockState state) {
		super.breakBlock(world, pos, state);
		updateInventories(world, pos);
	}

	@Override
	public void onNeighborChange(IBlockAccess world, BlockPos pos, BlockPos neighbor) {
		super.onNeighborChange(world, pos, neighbor);

		updateInventories(world, pos);
	}

	public static void updateInventories(IBlockAccess world, BlockPos pos) {
		List<WorldCoordinate> visited = new ArrayList<WorldCoordinate>();

		Queue<WorldCoordinate> queue = new PriorityQueue<WorldCoordinate>();
		WorldCoordinate        start = new WorldCoordinate(pos.getX(), pos.getY(), pos.getZ(), 0);
		queue.add(start);
		visited.add(start);

		while (!queue.isEmpty()) {
			WorldCoordinate element = queue.poll();

			for (int x = -1; x <= 1; x++) {
				for (int y = -1; y <= 1; y++) {
					for (int z = -1; z <= 1; z++) {
						if (Math.abs(x) + Math.abs(y) + Math.abs(z) == 1) {
							WorldCoordinate target = new WorldCoordinate(element.getX() + x, element.getY() + y, element.getZ() + z, element.getDepth() + 1);

							if (!visited.contains(target)) {
								visited.add(target);
								IBlockState block = world.getBlockState(new BlockPos(x, y, z));
								int         meta  = block.getBlock().getMetaFromState(block);
								if (block.getBlock() == SFMBlocks.MANAGER) {
									TileEntity tileEntity = world.getTileEntity(new BlockPos(target.getX(), target.getY(), target.getZ()));
									if (tileEntity instanceof TileEntityManager) {
										((TileEntityManager) tileEntity).updateInventories();
									}
								} else if (isCable(block.getBlock(), meta)) {
									queue.add(target);
								}
							}
						}
					}
				}
			}
		}
	}

	public static boolean isCable(Block block, int meta) {
		return block instanceof ICable;
	}

	@Override
	public boolean isCable() {
		return true;
	}
}
