package ca.teamdman.sfm.common.util;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.structure.StructureBoundingBox;
import org.jetbrains.annotations.Nullable;

public class SFMBackportHelper {
    private static final int NUM_X_BITS = 1 + MathHelper.log2(MathHelper.smallestEncompassingPowerOfTwo(30000000));
    private static final int NUM_Z_BITS = NUM_X_BITS;
    private static final int NUM_Y_BITS = 64 - NUM_X_BITS - NUM_Z_BITS;
    private static final int Y_SHIFT = NUM_Z_BITS;
    private static final int X_SHIFT = Y_SHIFT + NUM_Y_BITS;

    public static long asChunkLong(BlockPos pos) {
        return ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4);
    }

    public static long asChunkLong(ChunkPos chunkPos) {
        return ChunkPos.asLong(chunkPos.x, chunkPos.z);
    }

    public static @Nullable StructureBoundingBox boundingBoxOf(BlockPosIterator iterator) {
        if (!iterator.hasNext()) {
            return null;
        } else {
            var pos = iterator.next();
            StructureBoundingBox box = new StructureBoundingBox(pos, pos);
            StructureBoundingBox adjust = new StructureBoundingBox();
            while (iterator.hasNext()) {
                pos = iterator.next();
                adjust.maxX = adjust.minX = pos.getX();
                adjust.maxY = adjust.minY = pos.getY();
                adjust.maxZ = adjust.minZ = pos.getZ();
                box.expandTo(adjust);
            }
            return box;
        }
    }

    public static ChunkPos asChunk(long longKey) {
        return new ChunkPos((int) longKey, (int) (longKey >> 32));
    }

    public static BlockPos.MutableBlockPos setMutableBlockPos(BlockPos.MutableBlockPos pos, long newPos) {
        return pos.setPos(
                (int) (newPos << 64 - X_SHIFT - NUM_X_BITS >> 64 - NUM_X_BITS),
                (int) (newPos << 64 - Y_SHIFT - NUM_Y_BITS >> 64 - NUM_Y_BITS),
                (int) (newPos << 64 - NUM_Z_BITS >> 64 - NUM_Z_BITS)
        );
    }
}
