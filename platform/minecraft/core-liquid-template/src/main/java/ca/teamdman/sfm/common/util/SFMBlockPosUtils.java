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
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
        return Arrays.stream(SFMDirections.DIRECTIONS_WITHOUT_NULL).map(d -> pos.offset(d.getNormal()));
{% when '26.1.2' %}
        return Arrays.stream(SFMDirections.DIRECTIONS_WITHOUT_NULL).map(d -> pos.offset(d.getUnitVec3i()));
{% endcase %}
    }


    /// @return true iff 1 unit offsets the block positions along a single axis
    public static boolean isAdjacent(BlockPos first, BlockPos second) {
        return Math.abs(first.getX() - second.getX()) + Math.abs(first.getY() - second.getY()) + Math.abs(first.getZ() - second.getZ()) == 1;
    }

}
