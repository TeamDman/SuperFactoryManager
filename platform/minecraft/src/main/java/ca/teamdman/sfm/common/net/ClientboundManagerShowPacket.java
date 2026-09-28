package ca.teamdman.sfm.common.net;

import ca.teamdman.sfm.common.blockentity.ClientManagerProgramProjection;
import ca.teamdman.sfml.ast.Program;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.UUID;

/** One bounded answer for an exact, server-authorized manager observation. */
public record ClientboundManagerShowPacket(
        UUID requestId,
        ResourceLocation dimension,
        BlockPos position,
        Status status,
        String program,
        Map<String, List<BlockPos>> labels
) implements SFMPacket {
    public enum Status {
        ALLOWED,
        NO_SENDER,
        NOT_SERVER_THREAD,
        DISCONNECTED_SENDER,
        INACTIVE_SENDER,
        SPECTATOR,
        NOT_OPERATOR,
        WRONG_DIMENSION,
        TARGET_UNLOADED,
        NOT_MANAGER,
        STALE_MANAGER,
        DATA_TOO_LARGE
    }

    public ClientboundManagerShowPacket {
        Objects.requireNonNull(requestId);
        Objects.requireNonNull(dimension);
        position = Objects.requireNonNull(position).immutable();
        Objects.requireNonNull(status);
        Objects.requireNonNull(program);
        Objects.requireNonNull(labels);
        if (dimension.toString().length() > 256) throw new IllegalArgumentException("Manager dimension is too long");
        if (program.length() > Program.MAX_PROGRAM_LENGTH
            || program.getBytes(StandardCharsets.UTF_8).length > ClientManagerProgramProjection.MAX_SOURCE_BYTES) {
            throw new IllegalArgumentException("Manager program exceeds show budget");
        }
        if (labels.size() > ClientManagerProgramProjection.MAX_LABELS) {
            throw new IllegalArgumentException("Manager labels exceed show budget");
        }
        int positions = 0;
        Map<String, List<BlockPos>> owned = new TreeMap<>();
        for (var entry : labels.entrySet()) {
            String name = Objects.requireNonNull(entry.getKey());
            if (name.isBlank() || name.length() > Program.MAX_LABEL_LENGTH
                || name.getBytes(StandardCharsets.UTF_8).length > 1024) {
                throw new IllegalArgumentException("Manager label name exceeds show budget");
            }
            List<BlockPos> copy = new ArrayList<>();
            for (BlockPos value : Objects.requireNonNull(entry.getValue())) {
                copy.add(Objects.requireNonNull(value).immutable());
                if (++positions > ClientManagerProgramProjection.MAX_POSITIONS) {
                    throw new IllegalArgumentException("Manager label positions exceed show budget");
                }
            }
            owned.put(name, List.copyOf(copy));
        }
        if (status != Status.ALLOWED && (!program.isEmpty() || !owned.isEmpty())) {
            throw new IllegalArgumentException("Denied manager response must carry no data");
        }
        labels = Collections.unmodifiableMap(owned);
    }

    public static ClientboundManagerShowPacket denied(
            UUID requestId, ResourceLocation dimension, BlockPos position, Status status
    ) {
        if (status == Status.ALLOWED) throw new IllegalArgumentException("ALLOWED is not a denial");
        return new ClientboundManagerShowPacket(requestId, dimension, position, status, "", Map.of());
    }

    public static final class Daddy implements SFMPacketDaddy<ClientboundManagerShowPacket> {
        @Override public PacketDirection getPacketDirection() { return PacketDirection.CLIENTBOUND; }
        @Override public Class<ClientboundManagerShowPacket> getPacketClass() { return ClientboundManagerShowPacket.class; }

        @Override
        public void encode(ClientboundManagerShowPacket value, FriendlyByteBuf target) {
            target.writeUUID(value.requestId()).writeUtf(value.dimension().toString(), 256)
                    .writeBlockPos(value.position()).writeVarInt(value.status().ordinal())
                    .writeUtf(value.program(), Program.MAX_PROGRAM_LENGTH)
                    .writeVarInt(value.labels().size());
            value.labels().forEach((name, positions) -> {
                target.writeUtf(name, Program.MAX_LABEL_LENGTH).writeVarInt(positions.size());
                positions.forEach(target::writeBlockPos);
            });
        }

        @Override
        public ClientboundManagerShowPacket decode(FriendlyByteBuf source) {
            UUID requestId = source.readUUID();
            ResourceLocation dimension = new ResourceLocation(source.readUtf(256));
            BlockPos position = source.readBlockPos();
            int ordinal = source.readVarInt();
            if (ordinal < 0 || ordinal >= Status.values().length) throw new IllegalArgumentException("Invalid manager show status");
            String program = source.readUtf(Program.MAX_PROGRAM_LENGTH);
            int count = source.readVarInt();
            if (count < 0 || count > ClientManagerProgramProjection.MAX_LABELS) {
                throw new IllegalArgumentException("Invalid manager show label count");
            }
            Map<String, List<BlockPos>> labels = new TreeMap<>();
            int remaining = ClientManagerProgramProjection.MAX_POSITIONS;
            for (int i = 0; i < count; i++) {
                String name = source.readUtf(Program.MAX_LABEL_LENGTH);
                int size = source.readVarInt();
                if (size < 0 || size > remaining) throw new IllegalArgumentException("Invalid manager show position count");
                remaining -= size;
                List<BlockPos> positions = new ArrayList<>(size);
                for (int j = 0; j < size; j++) positions.add(source.readBlockPos());
                if (labels.putIfAbsent(name, positions) != null) throw new IllegalArgumentException("Duplicate manager label");
            }
            return new ClientboundManagerShowPacket(requestId, dimension, position,
                    Status.values()[ordinal], program, labels);
        }

        @Override
        public void handle(ClientboundManagerShowPacket packet, SFMPacketHandlingContext context) {
            ClientManagerShowResponses.receive(packet);
        }
    }
}
