package ca.teamdman.sfm.common.net;

import ca.teamdman.sfm.common.program.ProgramContext;
import ca.teamdman.sfm.common.program.SimulateExploreAllPathsProgramBehaviour;
import ca.teamdman.sfm.common.registry.registration.SFMPackets;
import ca.teamdman.sfml.ast.IfStatement;
import ca.teamdman.sfml.ast.Program;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}
import net.minecraft.network.FriendlyByteBuf;
{% when '1.21', '1.21.1', '26.1.2' %}
import net.minecraft.network.RegistryFriendlyByteBuf;
{% endcase %}

public record ServerboundIfStatementInspectionRequestPacket(
        String programString,

        int inputNodeIndex
) implements SFMPacket {
    public static class Daddy implements SFMPacketDaddy<ServerboundIfStatementInspectionRequestPacket> {
        @Override
        public PacketDirection getPacketDirection() {

            return PacketDirection.SERVERBOUND;
        }

        @Override
        public void encode(
                ServerboundIfStatementInspectionRequestPacket msg,
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}
                FriendlyByteBuf friendlyByteBuf
{% when '1.21', '1.21.1', '26.1.2' %}
                RegistryFriendlyByteBuf friendlyByteBuf
{% endcase %}
        ) {

            friendlyByteBuf.writeUtf(msg.programString, Program.MAX_PROGRAM_LENGTH);
            friendlyByteBuf.writeInt(msg.inputNodeIndex());
        }

        @Override
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}
        public ServerboundIfStatementInspectionRequestPacket decode(FriendlyByteBuf friendlyByteBuf) {
{% when '1.21', '1.21.1', '26.1.2' %}
        public ServerboundIfStatementInspectionRequestPacket decode(RegistryFriendlyByteBuf friendlyByteBuf) {
{% endcase %}
            return new ServerboundIfStatementInspectionRequestPacket(
                    friendlyByteBuf.readUtf(Program.MAX_PROGRAM_LENGTH),
                    friendlyByteBuf.readInt()
            );
        }

        @Override
        public void handle(
                ServerboundIfStatementInspectionRequestPacket msg,
                SFMPacketHandlingContext context
        ) {

            context.compileAndThen(
                    msg.programString,
                    false,
                    (program, player, managerBlockEntity) -> program.astBuilder()
                            .getNodeAtIndex(msg.inputNodeIndex)
                            .filter(IfStatement.class::isInstance)
                            .map(IfStatement.class::cast)
                            .ifPresent(ifStatement -> {
                                StringBuilder payload = new StringBuilder();
                                payload
                                        .append(ifStatement.toStringCondensed())
                                        .append("\n-- peek results --\n");
                                ProgramContext programContext = new ProgramContext(
                                        program,
                                        managerBlockEntity,
                                        new SimulateExploreAllPathsProgramBehaviour()
                                );
                                boolean result = ifStatement.condition().test(programContext);
                                payload.append(result ? "TRUE" : "FALSE");

                                SFMPackets.sendToPlayer(
                                        () -> player, new ClientboundIfStatementInspectionResultsPacket(
                                                SFMPacketDaddy.truncate(
                                                        payload.toString(),
                                                        ClientboundIfStatementInspectionResultsPacket.MAX_RESULTS_LENGTH
                                                ))
                                );
                            })
            );
        }

        @Override
        public Class<ServerboundIfStatementInspectionRequestPacket> getPacketClass() {

            return ServerboundIfStatementInspectionRequestPacket.class;
        }

    }

}
