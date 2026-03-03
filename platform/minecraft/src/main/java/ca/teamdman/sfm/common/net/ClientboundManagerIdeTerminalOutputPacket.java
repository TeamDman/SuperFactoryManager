package ca.teamdman.sfm.common.net;

import ca.teamdman.sfm.common.containermenu.ManagerContainerMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;

public record ClientboundManagerIdeTerminalOutputPacket(
        int windowId,
        List<String> lines
) implements SFMPacket {
    public static final int MAX_LINE_LENGTH = 512;
    public static final int MAX_LINES = 200;

    public static class Daddy implements SFMPacketDaddy<ClientboundManagerIdeTerminalOutputPacket> {
        @Override
        public PacketDirection getPacketDirection() {
            return PacketDirection.CLIENTBOUND;
        }

        @Override
        public Class<ClientboundManagerIdeTerminalOutputPacket> getPacketClass() {
            return ClientboundManagerIdeTerminalOutputPacket.class;
        }

        @Override
        public void encode(ClientboundManagerIdeTerminalOutputPacket msg, FriendlyByteBuf friendlyByteBuf) {
            friendlyByteBuf.writeVarInt(msg.windowId());
            int count = Math.min(msg.lines().size(), MAX_LINES);
            friendlyByteBuf.writeVarInt(count);
            for (int i = 0; i < count; i++) {
                friendlyByteBuf.writeUtf(msg.lines().get(i), MAX_LINE_LENGTH);
            }
        }

        @Override
        public ClientboundManagerIdeTerminalOutputPacket decode(FriendlyByteBuf friendlyByteBuf) {
            int windowId = friendlyByteBuf.readVarInt();
            int count = friendlyByteBuf.readVarInt();
            List<String> lines = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                lines.add(friendlyByteBuf.readUtf(MAX_LINE_LENGTH));
            }
            return new ClientboundManagerIdeTerminalOutputPacket(windowId, lines);
        }

        @Override
        public void handle(ClientboundManagerIdeTerminalOutputPacket msg, SFMPacketHandlingContext context) {
            LocalPlayer player = Minecraft.getInstance().player;
            if (player == null
                || !(player.containerMenu instanceof ManagerContainerMenu menu)
                || menu.containerId != msg.windowId()) {
                return;
            }
            menu.appendTerminalOutput(msg.lines());
        }
    }
}
