package ca.teamdman.sfm.common.net;

import ca.teamdman.sfm.common.program.ProgramContext;
import ca.teamdman.sfm.common.program.SimulateExploreAllPathsProgramBehaviour;
import ca.teamdman.sfm.common.registry.registration.SFMPackets;
import ca.teamdman.sfml.ast.IfStatement;
import ca.teamdman.sfml.ast.Program;
import com.github.bsideup.jabel.Desugar;

@Desugar
public record ServerboundIfStatementInspectionRequestPacket(
        String programString,

        int inputNodeIndex
) implements SFMPacket<ServerboundIfStatementInspectionRequestPacket> {
    public static class Daddy implements SFMPacketDaddy<ServerboundIfStatementInspectionRequestPacket> {
        @Override
        public PacketDirection getPacketDirection() {

            return PacketDirection.SERVERBOUND;
        }

        @Override
        public void encode(
                ServerboundIfStatementInspectionRequestPacket msg,
                FriendlyByteBuf friendlyByteBuf
        ) {

            friendlyByteBuf.writeString(SFMPacketDaddy.truncate(msg.programString, Program.MAX_PROGRAM_LENGTH));
            friendlyByteBuf.writeInt(msg.inputNodeIndex());
        }

        @Override
        public ServerboundIfStatementInspectionRequestPacket decode(FriendlyByteBuf friendlyByteBuf) {

            return new ServerboundIfStatementInspectionRequestPacket(
                    friendlyByteBuf.readString(Program.MAX_PROGRAM_LENGTH),
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
                                        player, new ClientboundIfStatementInspectionResultsPacket(
                                        SFMPacketDaddy.truncate(
                                                payload.toString(),
                                                ClientboundIfStatementInspectionResultsPacket.MAX_RESULTS_LENGTH
                                        )));
                            })
            );
        }

        @Override
        public Class<Packet> getPacketClass() {

            return Packet.class;
        }
    }

    public static final Daddy daddy = new Daddy();

    public static class Packet extends Wrapper<ServerboundIfStatementInspectionRequestPacket> {

        @Override
        SFMPacketDaddy<ServerboundIfStatementInspectionRequestPacket> getDaddy() {
            return daddy;
        }

    }


    @Override
    public Wrapper<ServerboundIfStatementInspectionRequestPacket> wrap() {
        var wrapper = new Packet();
        wrapper.ourRecord = this;
        return wrapper;
    }
}
