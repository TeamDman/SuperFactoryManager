package ca.teamdman.sfm.common.util;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

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
            for (var dir : new EnumFacing[]{EnumFacing.NORTH, EnumFacing.EAST, EnumFacing.SOUTH, EnumFacing.WEST, EnumFacing.DOWN, EnumFacing.UP}) {
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

    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(boundingVolumes.size());
        for (var volume : boundingVolumes) {
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

    public Set<BlockPos> into() {
        int capacity = 0;
        for (var volume : boundingVolumes) {
            capacity += volume.extension + 1;
        }
        HashSet<BlockPos> rtn = new HashSet<>(capacity);
        for (var volume : boundingVolumes) {
            BlockPos start = volume.start;
            BlockPos end = start.relative(volume.direction, volume.extension);
            for (BlockPos blockPos : BlockPos.betweenClosed(start, end)) {
                rtn.add(blockPos.immutable());
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


    private record Volume(
            BlockPos start,
            EnumFacing direction,
            int extension
    ) {
        public void write(ByteBuf buf) {
            buf.writeBlockPos(start);
            buf.writeEnum(direction);
            buf.writeVarInt(extension);
        }

        public static Volume read(ByteBuf buf) {
            return new Volume(
                    buf.readBlockPos(),
                    buf.readEnum(EnumFacing.class),
                    buf.readVarInt()
            );
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
