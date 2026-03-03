package ca.teamdman.sfm.common.net;

import ca.teamdman.sfm.client.screen.ManagerIdeScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;

public record ClientboundManagerIdeActionPacket(
        int windowId,
        String actionId
) implements SFMPacket {
    public static final int MAX_ACTION_ID_LENGTH = 128;

    public static class Daddy implements SFMPacketDaddy<ClientboundManagerIdeActionPacket> {
        @Override
        public PacketDirection getPacketDirection() {
            return PacketDirection.CLIENTBOUND;
        }

        @Override
        public Class<ClientboundManagerIdeActionPacket> getPacketClass() {
            return ClientboundManagerIdeActionPacket.class;
        }

        @Override
        public void encode(ClientboundManagerIdeActionPacket msg, FriendlyByteBuf friendlyByteBuf) {
            friendlyByteBuf.writeVarInt(msg.windowId());
            friendlyByteBuf.writeUtf(msg.actionId(), MAX_ACTION_ID_LENGTH);
        }

        @Override
        public ClientboundManagerIdeActionPacket decode(FriendlyByteBuf friendlyByteBuf) {
            return new ClientboundManagerIdeActionPacket(
                    friendlyByteBuf.readVarInt(),
                    friendlyByteBuf.readUtf(MAX_ACTION_ID_LENGTH)
            );
        }

        @Override
        public void handle(ClientboundManagerIdeActionPacket msg, SFMPacketHandlingContext context) {
            if (Minecraft.getInstance().screen instanceof ManagerIdeScreen screen
                && screen.getMenu().containerId == msg.windowId()) {
                screen.executeIdeAction(msg.actionId());
            }
        }
    }
}
