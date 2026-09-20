package ca.teamdman.sfm.common.net;

import ca.teamdman.sfm.common.program.signature.ProgramSignatureDescriptor;
import ca.teamdman.sfml.ast.Program;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Review or CAS-save request; declared capabilities are authorship data, not server permissions. */
public record ServerboundClientManagerSigningRequestPacket(UUID requestId, ResourceLocation dimension, BlockPos position,
                                                           List<ResourceLocation> declaredCapabilities, Optional<Save> save)
        implements SFMPacket {
    public record Save(UUID incarnation, long revision, String source) {
        public Save {
            Objects.requireNonNull(incarnation);
            Objects.requireNonNull(source);
            if (revision < 0 || source.length() > Program.MAX_PROGRAM_LENGTH) throw new IllegalArgumentException("Invalid source revision");
            ProgramSignatureDescriptor.normalizedSourceBytes(source);
        }
    }
    public ServerboundClientManagerSigningRequestPacket {
        Objects.requireNonNull(requestId);
        Objects.requireNonNull(dimension);
        position = Objects.requireNonNull(position).immutable();
        Objects.requireNonNull(save);
        if (dimension.toString().length() > 256 || declaredCapabilities.isEmpty()
            || declaredCapabilities.size() > ProgramSignatureDescriptor.MAX_CAPABILITIES) throw new IllegalArgumentException("Invalid signing request");
        declaredCapabilities = List.copyOf(declaredCapabilities);
        for (var capability : declaredCapabilities) {
            if (capability.toString().length() > 256) throw new IllegalArgumentException("Capability identifier too large");
        }
    }
    public int chargedBytes() {
        return 128 + declaredCapabilities.stream().mapToInt(cap -> cap.toString().length()).sum()
                + save.map(value -> value.source().getBytes(java.nio.charset.StandardCharsets.UTF_8).length).orElse(0);
    }
    public static final class Daddy implements SFMPacketDaddy<ServerboundClientManagerSigningRequestPacket> {
        public PacketDirection getPacketDirection() { return PacketDirection.SERVERBOUND; }
        public Class<ServerboundClientManagerSigningRequestPacket> getPacketClass() { return ServerboundClientManagerSigningRequestPacket.class; }
        public void encode(ServerboundClientManagerSigningRequestPacket value, FriendlyByteBuf target) {
            target.writeUUID(value.requestId).writeUtf(value.dimension.toString(), 256).writeBlockPos(value.position);
            target.writeVarInt(value.declaredCapabilities.size());
            value.declaredCapabilities.forEach(capability -> target.writeUtf(capability.toString(), 256));
            target.writeBoolean(value.save.isPresent());
            value.save.ifPresent(save -> {
                target.writeUUID(save.incarnation);
                target.writeLong(save.revision);
                target.writeUtf(save.source, Program.MAX_PROGRAM_LENGTH);
            });
        }
        public ServerboundClientManagerSigningRequestPacket decode(FriendlyByteBuf source) {
            UUID request = source.readUUID();
            ResourceLocation dimension = new ResourceLocation(source.readUtf(256));
            BlockPos position = source.readBlockPos();
            int count = source.readVarInt();
            if (count <= 0 || count > ProgramSignatureDescriptor.MAX_CAPABILITIES) throw new IllegalArgumentException("Invalid capability count");
            var capabilities = new ArrayList<ResourceLocation>(count);
            for (int i = 0; i < count; i++) capabilities.add(new ResourceLocation(source.readUtf(256)));
            Optional<Save> save = source.readBoolean() ? Optional.of(new Save(source.readUUID(), source.readLong(),
                    source.readUtf(Program.MAX_PROGRAM_LENGTH))) : Optional.empty();
            return new ServerboundClientManagerSigningRequestPacket(request, dimension, position, capabilities, save);
        }
        public void handle(ServerboundClientManagerSigningRequestPacket packet, SFMPacketHandlingContext context) {
            SFMServerClientManagerSigningTransport.receive(packet, context.sender());
        }
    }
}
