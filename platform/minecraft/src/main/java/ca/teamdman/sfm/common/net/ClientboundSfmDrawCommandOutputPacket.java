package ca.teamdman.sfm.common.net;

import ca.teamdman.sfm.client.screen.SfmDrawScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;

public record ClientboundSfmDrawCommandOutputPacket(
        int commandElementId,
        List<String> lines
) implements SFMPacket {
    public static final int MAX_LINE_LENGTH = 512;
    public static final int MAX_LINES = 200;

    public static class Daddy implements SFMPacketDaddy<ClientboundSfmDrawCommandOutputPacket> {
        @Override
        public PacketDirection getPacketDirection() {
            return PacketDirection.CLIENTBOUND;
        }

        @Override
        public Class<ClientboundSfmDrawCommandOutputPacket> getPacketClass() {
            return ClientboundSfmDrawCommandOutputPacket.class;
        }

        @Override
        public void encode(ClientboundSfmDrawCommandOutputPacket msg, FriendlyByteBuf friendlyByteBuf) {
            friendlyByteBuf.writeVarInt(msg.commandElementId());
            int count = Math.min(msg.lines().size(), MAX_LINES);
            friendlyByteBuf.writeVarInt(count);
            for (int i = 0; i < count; i++) {
                friendlyByteBuf.writeUtf(msg.lines().get(i), MAX_LINE_LENGTH);
            }
        }

        @Override
        public ClientboundSfmDrawCommandOutputPacket decode(FriendlyByteBuf friendlyByteBuf) {
            int commandElementId = friendlyByteBuf.readVarInt();
            int count = friendlyByteBuf.readVarInt();
            List<String> lines = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                lines.add(friendlyByteBuf.readUtf(MAX_LINE_LENGTH));
            }
            return new ClientboundSfmDrawCommandOutputPacket(commandElementId, lines);
        }

        @Override
        public void handle(ClientboundSfmDrawCommandOutputPacket msg, SFMPacketHandlingContext context) {
            if (Minecraft.getInstance().screen instanceof SfmDrawScreen screen) {
                screen.appendCommandOutput(msg.commandElementId(), msg.lines());
            }
        }
    }
}