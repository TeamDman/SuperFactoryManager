package ca.teamdman.sfm.common.util;

import net.minecraft.core.BlockPos;

import java.util.Arrays;
import java.util.stream.Stream;

public class SFMBlockPosUtils {
    public static Stream<BlockPos> get3DNeighboursIncludingKittyCorner(BlockPos pos) {
        Stream.Builder<BlockPos> builder = Stream.builder();
        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -1; z <= 1; z++) {
                    if (x == 0 && y == 0 && z == 0) continue;
                    builder.accept(pos.offset(x, y, z));
                }
            }
        }
        return builder.build();
    }

    public static Stream<BlockPos> get3DNeighbours(BlockPos pos) {
        return Arrays.stream(SFMDirections.DIRECTIONS_WITHOUT_NULL).map(d -> pos.offset(d.getNormal()));
    }


    /// @return true iff 1 unit offsets the block positions along a single axis
    public static boolean isAdjacent(BlockPos first, BlockPos second) {
        return Math.abs(first.getX() - second.getX()) + Math.abs(first.getY() - second.getY()) + Math.abs(first.getZ() - second.getZ()) == 1;
    }

}
