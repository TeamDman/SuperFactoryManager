package ca.teamdman.sfm.common.util;

import com.github.bsideup.jabel.Desugar;
import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.longs.LongIterator;
import net.minecraft.nbt.NBTTagByteArray;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;

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
        EnumFacing[] directions = {
                EnumFacing.NORTH,
                EnumFacing.EAST,
                EnumFacing.SOUTH,
                EnumFacing.WEST,
                EnumFacing.DOWN,
                EnumFacing.UP
        };

        // Drain the unencoded positions into the return set
        LongIterator iter = positions.iterator();
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
            EnumFacing extendDirection = EnumFacing.NORTH;

            int extension = 0;
            BlockPos.MutableBlockPos pos = BlockPos.PooledMutableBlockPos.retain();
            // Check each direction for valid extensions
            for (EnumFacing checkDirection : directions) {
                SFMBackportHelper.setMutableBlockPos(pos, volumeStartBlockPosLong).move(checkDirection);
                // Extend in the direction
                long extensionBlockPosLong = pos.toLong();

                // Ensure the position hasn't already been encoded
                if (positions.contains(extensionBlockPosLong) && !visited.contains(extensionBlockPosLong)) {

                    // Update the direction of the volume
                    extendDirection = checkDirection;

                    // Extend as far as possible
                    while (positions.contains(extensionBlockPosLong) && !visited.contains(extensionBlockPosLong)) {
                        // Track as seen
                        visited.add(extensionBlockPosLong);

                        SFMBackportHelper.setMutableBlockPos(pos, extensionBlockPosLong).move(checkDirection);
                        // Step in the direction
                        extensionBlockPosLong = pos.toLong();

                        // Increment the extension
                        extension++;
                    }
                    break;
                }
            }
            rtn.boundingVolumes.add(new Volume(BlockPos.fromLong(volumeStartBlockPosLong), extendDirection, extension));
        }

        // Return the result set
        return rtn;
    }

    public void write(PacketBuffer buf) {
        buf.writeVarInt(boundingVolumes.size());
        for (Volume volume : boundingVolumes) {
            volume.write(buf);
        }
    }

    public static CompressedBlockPosSet read(PacketBuffer buf) {
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
            BlockPos end = start.offset(volume.direction, volume.extension);
            for (BlockPos blockPos : BlockPos.getAllInBox(start, end)) {
                rtn.add(blockPos); // correctness: BlockPosSet makes it immutable
            }
        }
        return rtn;
    }

    public NBTTagByteArray asTag() {

        PacketBuffer buf = new PacketBuffer(Unpooled.buffer());
        this.write(buf);
        return new NBTTagByteArray(buf.array());
    }

    public static CompressedBlockPosSet from(NBTTagByteArray tag) {

        return from(tag.getByteArray());
    }

    public static CompressedBlockPosSet from(byte[] data) {

        PacketBuffer buf = new PacketBuffer(Unpooled.wrappedBuffer(data));
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

    @Desugar
    private record Volume(
            /// Where the volume begins
            BlockPos start,

            /// The direction the volume extends
            EnumFacing direction,

            /// How far beyond the initial block the volume extends
            int extension
// this would be better as "size" but whatever, can't change now because it's stored in existing nbt
    ) {
        public void write(PacketBuffer buf) {
            buf.writeBlockPos(start);
            buf.writeInt(direction.ordinal());
            buf.writeVarInt(extension);
        }

        public static Volume read(PacketBuffer buf) {
            return new Volume(
                    buf.readBlockPos(),
                    EnumFacing.byIndex(buf.readInt()),
                    buf.readVarInt()
            );
        }

    }

}
