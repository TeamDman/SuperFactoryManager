package ca.teamdman.sfm.common.net;

import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import ca.teamdman.sfm.common.command.ide.CapturingCommandSource;
import ca.teamdman.sfm.common.containermenu.ManagerContainerMenu;
import ca.teamdman.sfm.common.registry.registration.SFMPackets;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;

public record ServerboundManagerIdeTerminalCommandPacket(
        int windowId,
        BlockPos pos,
        String command
) implements SFMPacket {
    public static final int MAX_COMMAND_LENGTH = 512;

    public static class Daddy implements SFMPacketDaddy<ServerboundManagerIdeTerminalCommandPacket> {
        @Override
        public PacketDirection getPacketDirection() {
            return PacketDirection.SERVERBOUND;
        }

        @Override
        public Class<ServerboundManagerIdeTerminalCommandPacket> getPacketClass() {
            return ServerboundManagerIdeTerminalCommandPacket.class;
        }

        @Override
        public void encode(ServerboundManagerIdeTerminalCommandPacket msg, FriendlyByteBuf friendlyByteBuf) {
            friendlyByteBuf.writeVarInt(msg.windowId());
            friendlyByteBuf.writeBlockPos(msg.pos());
            friendlyByteBuf.writeUtf(msg.command(), MAX_COMMAND_LENGTH);
        }

        @Override
        public ServerboundManagerIdeTerminalCommandPacket decode(FriendlyByteBuf friendlyByteBuf) {
            return new ServerboundManagerIdeTerminalCommandPacket(
                    friendlyByteBuf.readVarInt(),
                    friendlyByteBuf.readBlockPos(),
                    friendlyByteBuf.readUtf(MAX_COMMAND_LENGTH)
            );
        }

        @Override
        public void handle(ServerboundManagerIdeTerminalCommandPacket msg, SFMPacketHandlingContext context) {
            context.handleServerboundContainerPacket(
                    ManagerContainerMenu.class,
                    ManagerBlockEntity.class,
                    msg.pos,
                    msg.windowId,
                    (menu, manager) -> {
                        ServerPlayer sender = context.sender();
                        if (sender == null) {
                            return;
                        }
                        String raw = msg.command().trim();
                        if (raw.isBlank()) {
                            return;
                        }

                        String commandString = raw.startsWith("/") ? raw.substring(1) : raw;
                        CapturingCommandSource capture = new CapturingCommandSource();
                        var commandSource = sender.createCommandSourceStack().withSource(capture);
                        var server = sender.getServer();
                        if (server != null) {
                            server.getCommands().performPrefixedCommand(commandSource, commandString);
                        }

                        ArrayList<String> lines = new ArrayList<>();
                        lines.add("> " + raw);
                        lines.addAll(capture.getCapturedLines());
                        if (lines.size() == 1) {
                            lines.add("(no output)");
                        }
                        SFMPackets.sendToPlayer(sender, new ClientboundManagerIdeTerminalOutputPacket(menu.containerId, lines));
                    }
            );
        }
    }
}
