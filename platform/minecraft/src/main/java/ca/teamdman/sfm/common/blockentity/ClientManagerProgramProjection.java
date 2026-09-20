package ca.teamdman.sfm.common.blockentity;

import ca.teamdman.sfml.ast.Program;
import ca.teamdman.sfm.common.program.signature.ProgramSignatureDescriptor;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

/** Bounds the program projection before expanding compressed disk labels. */
public final class ClientManagerProgramProjection {
    public static final int MAX_SOURCE_BYTES = 64 * 1024;
    public static final int MAX_LABELS = 32;
    public static final int MAX_POSITIONS = 64;
    public static final int MAX_COMPRESSED_BYTES = 4096;

    private ClientManagerProgramProjection() {}

    /** Only source and normalized labels reach clients; unrelated item metadata stays server-side. */
    public static Optional<CompoundTag> project(CompoundTag raw) {
        String source;
        try {
            source = new String(ProgramSignatureDescriptor.normalizedSourceBytes(raw.getString("sfm:program")),
                    StandardCharsets.UTF_8);
        } catch (IllegalArgumentException invalidSource) {
            return Optional.empty();
        }
        if (source.length() > Program.MAX_PROGRAM_LENGTH
            || source.getBytes(StandardCharsets.UTF_8).length > MAX_SOURCE_BYTES) return Optional.empty();
        if (raw.contains("sfm:labels") && !raw.contains("sfm:labels", Tag.TAG_COMPOUND)) return Optional.empty();
        CompoundTag labels = raw.getCompound("sfm:labels");
        if (labels.size() > MAX_LABELS) return Optional.empty();
        CompoundTag projectedLabels = new CompoundTag();
        int remaining = MAX_POSITIONS;
        try {
            for (String name : labels.getAllKeys()) {
                if (name.isBlank() || name.length() > Program.MAX_LABEL_LENGTH
                    || name.getBytes(StandardCharsets.UTF_8).length > 1024) return Optional.empty();
                ListTag positions = boundedPositions(labels.get(name), remaining);
                remaining -= positions.size();
                projectedLabels.put(name, positions);
            }
        } catch (RuntimeException malformed) {
            return Optional.empty();
        }
        CompoundTag projected = new CompoundTag();
        projected.putString("sfm:program", source);
        projected.put("sfm:labels", projectedLabels);
        return Optional.of(projected);
    }

    private static ListTag boundedPositions(Tag raw, int remaining) {
        ListTag result = new ListTag();
        if (raw instanceof ListTag list) {
            require(list.size() <= remaining);
            for (Tag element : list) {
                if (element instanceof LongTag packed) {
                    result.add(LongTag.valueOf(packed.getAsLong()));
                } else if (element instanceof CompoundTag position) {
                    require(position.contains("X", Tag.TAG_INT)
                            && position.contains("Y", Tag.TAG_INT) && position.contains("Z", Tag.TAG_INT));
                    result.add(LongTag.valueOf(new BlockPos(position.getInt("X"), position.getInt("Y"),
                            position.getInt("Z")).asLong()));
                } else {
                    throw new IllegalArgumentException("Invalid Client Manager label position");
                }
            }
            return result;
        }
        if (!(raw instanceof ByteArrayTag bytes)) throw new IllegalArgumentException("Invalid label encoding");
        byte[] encoded = bytes.getAsByteArray();
        require(encoded.length <= MAX_COMPRESSED_BYTES);
        FriendlyByteBuf input = new FriendlyByteBuf(Unpooled.wrappedBuffer(encoded));
        try {
            int volumes = input.readVarInt();
            require(volumes >= 0 && volumes <= remaining);
            for (int i = 0; i < volumes; i++) {
                BlockPos start = input.readBlockPos();
                int directionOrdinal = input.readVarInt();
                require(directionOrdinal >= 0 && directionOrdinal < Direction.values().length);
                Direction direction = Direction.values()[directionOrdinal];
                int extension = input.readVarInt();
                // Check before allocation/iteration; a few encoded bytes can otherwise expand without bound.
                require(extension >= 0 && extension < remaining - result.size());
                for (int offset = 0; offset <= extension; offset++) {
                    result.add(LongTag.valueOf(start.relative(direction, offset).asLong()));
                }
            }
            // Legacy encoder stores the backing byte array, so zero padding is legitimate.
            while (input.isReadable()) require(input.readByte() == 0);
            return result;
        } finally {
            input.release();
        }
    }

    private static void require(boolean condition) {
        if (!condition) throw new IllegalArgumentException("Client Manager label projection exceeds its budget");
    }
}
