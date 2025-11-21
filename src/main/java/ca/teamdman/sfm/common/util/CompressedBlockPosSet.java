package ca.teamdman.sfm.common.util;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import net.minecraft.nbt.NBTTagByteArray;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;

import com.github.bsideup.jabel.Desugar;

import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;

/**
 * Compress a set of BlockPos by storing cuboids.
 */
public class CompressedBlockPosSet {

    private final ArrayList<Volume> boundingVolumes = new ArrayList<>();

    /**
     * @param positions owned list of positions, this will be modified.
     * @return this
     */
    public static CompressedBlockPosSet from(Set<BlockPos> positions) {
        CompressedBlockPosSet rtn = new CompressedBlockPosSet();
        LongSet remaining = new LongLinkedOpenHashSet(positions.size());
        for (BlockPos pos : positions) {
            remaining.add(pos.toLong());
        }
        while (!remaining.isEmpty()) {
            long start = remaining.iterator().nextLong();
            remaining.remove(start);
            EnumFacing direction = EnumFacing.NORTH;
            int extension = 0;
            // we want to put down/up last so we don't use .values() here
            for (var dir : new EnumFacing[] { EnumFacing.NORTH, EnumFacing.EAST, EnumFacing.SOUTH, EnumFacing.WEST,
                    EnumFacing.DOWN, EnumFacing.UP }) {
                BlockPos offset = BlockPos.fromLong(start).offset(dir);
                if (remaining.contains(offset.toLong())) {
                    direction = dir;
                    while (remaining.contains(offset.toLong())) {
                        remaining.remove(offset.toLong());
                        offset = offset.offset(dir);
                        extension++;
                    }
                    break;
                }
            }
            rtn.boundingVolumes.add(new Volume(BlockPos.fromLong(start), direction, extension));
        }
        return rtn;
    }

    public void write(PacketBuffer buf) {
        buf.writeVarInt(boundingVolumes.size());
        for (var volume : boundingVolumes) {
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

    public Set<BlockPos> into() {
        int capacity = 0;
        for (var volume : boundingVolumes) {
            capacity += volume.extension + 1;
        }
        HashSet<BlockPos> rtn = new HashSet<>(capacity);
        for (var volume : boundingVolumes) {
            BlockPos start = volume.start;
            BlockPos end = start.offset(volume.direction, volume.extension);
            for (BlockPos blockPos : BlockPos.getAllInBox(start, end)) {
                rtn.add(blockPos.toImmutable());
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

    @Desugar
    private record Volume(
                          BlockPos start,
                          EnumFacing direction,
                          int extension) {

        public void write(PacketBuffer buf) {
            buf.writeBlockPos(start);
            buf.writeInt(direction.ordinal());
            buf.writeVarInt(extension);
        }

        public static Volume read(PacketBuffer buf) {
            return new Volume(
                    buf.readBlockPos(),
                    EnumFacing.byIndex(buf.readInt()),
                    buf.readVarInt());
        }
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
}
