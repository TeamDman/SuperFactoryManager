package ca.teamdman.sfm.common.net;

import ca.teamdman.sfm.common.command.draw.CapturingDrawCommandSource;
import ca.teamdman.sfm.common.registry.registration.SFMPackets;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

public record ServerboundSfmDrawCommandPacket(
        int commandElementId,
        String command
) implements SFMPacket {
    public static final int MAX_COMMAND_LENGTH = 512;

    public static class Daddy implements SFMPacketDaddy<ServerboundSfmDrawCommandPacket> {
        @Override
        public PacketDirection getPacketDirection() {
            return PacketDirection.SERVERBOUND;
        }

        @Override
        public Class<ServerboundSfmDrawCommandPacket> getPacketClass() {
            return ServerboundSfmDrawCommandPacket.class;
        }

        @Override
        public void encode(ServerboundSfmDrawCommandPacket msg, FriendlyByteBuf friendlyByteBuf) {
            friendlyByteBuf.writeVarInt(msg.commandElementId());
            friendlyByteBuf.writeUtf(msg.command(), MAX_COMMAND_LENGTH);
        }

        @Override
        public ServerboundSfmDrawCommandPacket decode(FriendlyByteBuf friendlyByteBuf) {
            return new ServerboundSfmDrawCommandPacket(
                    friendlyByteBuf.readVarInt(),
                    friendlyByteBuf.readUtf(MAX_COMMAND_LENGTH)
            );
        }

        @Override
        public void handle(ServerboundSfmDrawCommandPacket msg, SFMPacketHandlingContext context) {
            ServerPlayer sender = context.sender();
            if (sender == null) {
                return;
            }

            String rawCommand = msg.command().trim();
            ArrayList<String> lines = new ArrayList<>();
            if (rawCommand.isBlank()) {
                lines.add("Missing draw command.");
                SFMPackets.sendToPlayer(sender, new ClientboundSfmDrawCommandOutputPacket(msg.commandElementId(), lines));
                return;
            }

            var server = sender.getServer();
            if (server == null) {
                lines.add("Failed to run draw command.");
                SFMPackets.sendToPlayer(sender, new ClientboundSfmDrawCommandOutputPacket(msg.commandElementId(), lines));
                return;
            }

            CapturingDrawCommandSource capture = new CapturingDrawCommandSource();
            var commandSource = sender.createCommandSourceStack().withSource(capture);
            server.getCommands().performPrefixedCommand(commandSource, "sfm draw " + rawCommand);

            for (String capturedLine : capture.getCapturedLines()) {
                String[] splitLines = capturedLine.split("\\R", -1);
                for (String splitLine : splitLines) {
                    lines.add(splitLine);
                }
            }
            if (lines.isEmpty()) {
                lines.add("(no output)");
            }
            SFMPackets.sendToPlayer(sender, new ClientboundSfmDrawCommandOutputPacket(msg.commandElementId(), lines));
        }
    }
}