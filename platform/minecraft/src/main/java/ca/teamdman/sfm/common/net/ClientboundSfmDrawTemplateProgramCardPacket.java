package ca.teamdman.sfm.common.net;

import ca.teamdman.sfm.client.screen.SfmDrawScreen;
import ca.teamdman.sfm.common.command.draw.DrawTemplateProgramCard;
import ca.teamdman.sfml.ast.Program;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;

public record ClientboundSfmDrawTemplateProgramCardPacket(
        int commandElementId,
        DrawTemplateProgramCard card
) implements SFMPacket {
    private static final int MAX_TEMPLATE_NAME_LENGTH = 256;
    private static final int MAX_CARD_LINE_LENGTH = 512;
    private static final int MAX_CARD_LINES = 256;

    public static class Daddy implements SFMPacketDaddy<ClientboundSfmDrawTemplateProgramCardPacket> {
        @Override
        public PacketDirection getPacketDirection() {
            return PacketDirection.CLIENTBOUND;
        }

        @Override
        public Class<ClientboundSfmDrawTemplateProgramCardPacket> getPacketClass() {
            return ClientboundSfmDrawTemplateProgramCardPacket.class;
        }

        @Override
        public void encode(
                ClientboundSfmDrawTemplateProgramCardPacket msg,
                FriendlyByteBuf friendlyByteBuf
        ) {
            friendlyByteBuf.writeVarInt(msg.commandElementId());
            friendlyByteBuf.writeUtf(msg.card().templateKey(), MAX_TEMPLATE_NAME_LENGTH);
            friendlyByteBuf.writeUtf(msg.card().displayName(), MAX_TEMPLATE_NAME_LENGTH);
            friendlyByteBuf.writeUtf(msg.card().programString(), Program.MAX_PROGRAM_LENGTH);
            writeLines(friendlyByteBuf, msg.card().detailLines());
            writeLines(friendlyByteBuf, msg.card().warningLines());
            writeLines(friendlyByteBuf, msg.card().errorLines());
            writeLines(friendlyByteBuf, msg.card().astLines());
        }

        @Override
        public ClientboundSfmDrawTemplateProgramCardPacket decode(FriendlyByteBuf friendlyByteBuf) {
            int commandElementId = friendlyByteBuf.readVarInt();
            DrawTemplateProgramCard card = new DrawTemplateProgramCard(
                    friendlyByteBuf.readUtf(MAX_TEMPLATE_NAME_LENGTH),
                    friendlyByteBuf.readUtf(MAX_TEMPLATE_NAME_LENGTH),
                    friendlyByteBuf.readUtf(Program.MAX_PROGRAM_LENGTH),
                    readLines(friendlyByteBuf),
                    readLines(friendlyByteBuf),
                    readLines(friendlyByteBuf),
                    readLines(friendlyByteBuf)
            );
            return new ClientboundSfmDrawTemplateProgramCardPacket(commandElementId, card);
        }

        @Override
        public void handle(
                ClientboundSfmDrawTemplateProgramCardPacket msg,
                SFMPacketHandlingContext context
        ) {
            if (Minecraft.getInstance().screen instanceof SfmDrawScreen screen) {
                screen.appendTemplateProgramCard(msg.commandElementId(), msg.card());
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