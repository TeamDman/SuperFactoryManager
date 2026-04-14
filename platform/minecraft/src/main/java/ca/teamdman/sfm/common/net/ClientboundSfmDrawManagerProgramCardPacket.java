package ca.teamdman.sfm.common.net;

import ca.teamdman.sfm.client.screen.SfmDrawScreen;
import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import ca.teamdman.sfm.common.command.draw.DrawManagerProgramCard;
import ca.teamdman.sfml.ast.Program;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;

public record ClientboundSfmDrawManagerProgramCardPacket(
        int commandElementId,
        DrawManagerProgramCard card
) implements SFMPacket {
    private static final int MAX_DISK_NAME_LENGTH = 256;
    private static final int MAX_CARD_LINE_LENGTH = 512;
    private static final int MAX_CARD_LINES = 256;

    public static class Daddy implements SFMPacketDaddy<ClientboundSfmDrawManagerProgramCardPacket> {
        @Override
        public PacketDirection getPacketDirection() {
            return PacketDirection.CLIENTBOUND;
        }

        @Override
        public Class<ClientboundSfmDrawManagerProgramCardPacket> getPacketClass() {
            return ClientboundSfmDrawManagerProgramCardPacket.class;
        }

        @Override
        public void encode(
                ClientboundSfmDrawManagerProgramCardPacket msg,
                FriendlyByteBuf friendlyByteBuf
        ) {
            friendlyByteBuf.writeVarInt(msg.commandElementId());
            friendlyByteBuf.writeBlockPos(msg.card().managerPos());
            friendlyByteBuf.writeEnum(msg.card().state());
            friendlyByteBuf.writeUtf(msg.card().diskName(), MAX_DISK_NAME_LENGTH);
            friendlyByteBuf.writeUtf(msg.card().programString(), Program.MAX_PROGRAM_LENGTH);
            writeLines(friendlyByteBuf, msg.card().detailLines());
            writeLines(friendlyByteBuf, msg.card().warningLines());
            writeLines(friendlyByteBuf, msg.card().errorLines());
            writeLines(friendlyByteBuf, msg.card().astLines());
        }

        @Override
        public ClientboundSfmDrawManagerProgramCardPacket decode(FriendlyByteBuf friendlyByteBuf) {
            int commandElementId = friendlyByteBuf.readVarInt();
            DrawManagerProgramCard card = new DrawManagerProgramCard(
                    friendlyByteBuf.readBlockPos(),
                    friendlyByteBuf.readEnum(ManagerBlockEntity.State.class),
                    friendlyByteBuf.readUtf(MAX_DISK_NAME_LENGTH),
                    friendlyByteBuf.readUtf(Program.MAX_PROGRAM_LENGTH),
                    readLines(friendlyByteBuf),
                    readLines(friendlyByteBuf),
                    readLines(friendlyByteBuf),
                    readLines(friendlyByteBuf)
            );
            return new ClientboundSfmDrawManagerProgramCardPacket(commandElementId, card);
        }

        @Override
        public void handle(
                ClientboundSfmDrawManagerProgramCardPacket msg,
                SFMPacketHandlingContext context
        ) {
            if (Minecraft.getInstance().screen instanceof SfmDrawScreen screen) {
                screen.appendManagerProgramCard(msg.commandElementId(), msg.card());
            }
        }

        private static void writeLines(
                FriendlyByteBuf friendlyByteBuf,
                List<String> lines
        ) {
            int count = Math.min(lines.size(), MAX_CARD_LINES);
            friendlyByteBuf.writeVarInt(count);
            for (int i = 0; i < count; i++) {
                friendlyByteBuf.writeUtf(lines.get(i), MAX_CARD_LINE_LENGTH);
            }
        }

        private static List<String> readLines(FriendlyByteBuf friendlyByteBuf) {
            int count = friendlyByteBuf.readVarInt();
            List<String> lines = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                lines.add(friendlyByteBuf.readUtf(MAX_CARD_LINE_LENGTH));
            }
            return lines;
        }
    }
}