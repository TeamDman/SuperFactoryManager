package ca.teamdman.sfm.common.net;

import ca.teamdman.sfm.client.tutorial.SFMTutorialClientContext;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

public record ClientboundTutorialContextPacket(
        boolean hasTutorialContext,
        @Nullable BlockPos chamberOrigin,
        @Nullable ResourceLocation chamberId
) implements SFMPacket {
    public static class Daddy implements SFMPacketDaddy<ClientboundTutorialContextPacket> {
        @Override
        public PacketDirection getPacketDirection() {
            return PacketDirection.CLIENTBOUND;
        }

        @Override
        public Class<ClientboundTutorialContextPacket> getPacketClass() {
            return ClientboundTutorialContextPacket.class;
        }

        @Override
        public void encode(
                ClientboundTutorialContextPacket msg,
                FriendlyByteBuf friendlyByteBuf
        ) {
            friendlyByteBuf.writeBoolean(msg.hasTutorialContext());
            if (!msg.hasTutorialContext()) {
                return;
            }

            friendlyByteBuf.writeBlockPos(msg.chamberOrigin() == null ? BlockPos.ZERO : msg.chamberOrigin());
            friendlyByteBuf.writeBoolean(msg.chamberId() != null);
            if (msg.chamberId() != null) {
                friendlyByteBuf.writeResourceLocation(msg.chamberId());
            }
        }

        @Override
        public ClientboundTutorialContextPacket decode(FriendlyByteBuf friendlyByteBuf) {
            boolean hasTutorialContext = friendlyByteBuf.readBoolean();
            if (!hasTutorialContext) {
                return new ClientboundTutorialContextPacket(false, null, null);
            }

            BlockPos chamberOrigin = friendlyByteBuf.readBlockPos();
            ResourceLocation chamberId = null;
            boolean hasChamberId = friendlyByteBuf.readBoolean();
            if (hasChamberId) {
                chamberId = friendlyByteBuf.readResourceLocation();
            }
            return new ClientboundTutorialContextPacket(true, chamberOrigin, chamberId);
        }

        @Override
        public void handle(
                ClientboundTutorialContextPacket msg,
                SFMPacketHandlingContext context
        ) {
            if (!msg.hasTutorialContext()) {
                SFMTutorialClientContext.clear();
            } else {
                SFMTutorialClientContext.set(msg.chamberOrigin(), msg.chamberId());
            }
        }
    }
}
