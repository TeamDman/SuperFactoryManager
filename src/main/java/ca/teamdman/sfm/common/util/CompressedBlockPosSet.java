package ca.teamdman.sfm.common.util;

import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.longs.LongIterator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;

/**
 * Compress a set of BlockPos by storing cuboids.
 */
public class CompressedBlockPosSet {
    private final ArrayList<Volume> boundingVolumes = new ArrayList<>();

    public static CompressedBlockPosSet from(BlockPosSet positions) {

        // Create the return object
        CompressedBlockPosSet rtn = new CompressedBlockPosSet();

        // Track which positions we have encoded
        BlockPosSet visited = new BlockPosSet(positions.size());

        // When creating the volumes, we want to try vertical directions last
        Direction[] directions = {
                Direction.NORTH,
                Direction.EAST,
                Direction.SOUTH,
                Direction.WEST,
                Direction.DOWN,
                Direction.UP
        };

        // Drain the unencoded positions into the return set
        LongIterator iter = positions.longIterator();
        while (iter.hasNext()) {
            // Pop the next value to encode
            long volumeStartBlockPosLong = iter.nextLong();

            // SKIP if this block was already swallowed by a previous volume extension
            if (visited.contains(volumeStartBlockPosLong)) {
                continue;
            }

            // Track as seen
            visited.add(volumeStartBlockPosLong);

            // Default to extending northwards
            Direction extendDirection = Direction.NORTH;

            int extension = 0;
            // Check each direction for valid extensions
            for (Direction checkDirection : directions) {

                // Extend in the direction
                long extensionBlockPosLong = BlockPos.offset(volumeStartBlockPosLong, checkDirection);

                // Ensure the position hasn't already been encoded
                if (positions.contains(extensionBlockPosLong) && !visited.contains(extensionBlockPosLong)) {

                    // Update the direction of the volume
                    extendDirection = checkDirection;

                    // Extend as far as possible
                    while (positions.contains(extensionBlockPosLong) && !visited.contains(extensionBlockPosLong)) {
                        // Track as seen
                        visited.add(extensionBlockPosLong);

                        // Step in the direction
                        extensionBlockPosLong = BlockPos.offset(extensionBlockPosLong, checkDirection);

                        // Increment the extension
                        extension++;
                    }
                    break;
                }
            }
            rtn.boundingVolumes.add(new Volume(BlockPos.of(volumeStartBlockPosLong), extendDirection, extension));
        }

        // Return the result set
        return rtn;
    }

    public void write(FriendlyByteBuf buf) {

        buf.writeVarInt(boundingVolumes.size());
        for (Volume volume : boundingVolumes) {
            volume.write(buf);
        }
    }

    public static CompressedBlockPosSet read(FriendlyByteBuf buf) {

        CompressedBlockPosSet rtn = new CompressedBlockPosSet();
        int count = buf.readVarInt();
        for (int i = 0; i < count; i++) {
            rtn.boundingVolumes.add(Volume.read(buf));
        }
        return rtn;
    }

    public BlockPosSet into() {

        int capacity = 0;
        for (Volume volume : boundingVolumes) {
            capacity += volume.extension + 1;
        }
        BlockPosSet rtn = new BlockPosSet(capacity);
        for (Volume volume : boundingVolumes) {
            BlockPos start = volume.start;
            BlockPos end = start.relative(volume.direction, volume.extension);
            for (BlockPos blockPos : BlockPos.betweenClosed(start, end)) {
                rtn.add(blockPos); // correctness: BlockPosSet makes it immutable
            }
        }
        return rtn;
    }

    public ByteArrayTag asTag() {

        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        this.write(buf);
        return new ByteArrayTag(buf.array());
    }

    public static CompressedBlockPosSet from(ByteArrayTag tag) {

        return from(tag.getAsByteArray());
    }

    public static CompressedBlockPosSet from(byte[] data) {

        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.wrappedBuffer(data));
        return CompressedBlockPosSet.read(buf);
    }

    @Override
    public int hashCode() {

        return this.boundingVolumes.hashCode();
    }

    @Override
    public boolean equals(Object obj) {

        if (obj instanceof CompressedBlockPosSet set) {
            return set.boundingVolumes.equals(this.boundingVolumes);
        }
        return false;
    }

    private record Volume(
            /// Where the volume begins
            BlockPos start,

            /// The direction the volume extends
            Direction direction,

            /// How far beyond the initial block the volume extends
            int extension
            // this would be better as "size" but whatever, can't change now because it's stored in existing nbt
    ) {
        public void write(FriendlyByteBuf buf) {

            buf.writeBlockPos(start);
            buf.writeEnum(direction);
            buf.writeVarInt(extension);
        }

        public static Volume read(FriendlyByteBuf buf) {

            return new Volume(
                    buf.readBlockPos(),
                    buf.readEnum(Direction.class),
                    buf.readVarInt()
            );
        }

    }

}
