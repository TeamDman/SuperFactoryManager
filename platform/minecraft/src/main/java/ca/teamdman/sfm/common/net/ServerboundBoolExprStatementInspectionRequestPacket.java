package ca.teamdman.sfm.common.net;

import ca.teamdman.sfm.common.program.ProgramContext;
import ca.teamdman.sfm.common.program.SimulateExploreAllPathsProgramBehaviour;
import ca.teamdman.sfm.common.registry.registration.SFMPackets;
import ca.teamdman.sfml.ast.BoolExpr;
import ca.teamdman.sfml.ast.Program;
import com.github.bsideup.jabel.Desugar;

@Desugar
public record ServerboundBoolExprStatementInspectionRequestPacket(
        String programString,

        int inputNodeIndex
) implements SFMPacket<ServerboundBoolExprStatementInspectionRequestPacket> {
    public static class Daddy implements SFMPacketDaddy<ServerboundBoolExprStatementInspectionRequestPacket> {
        @Override
        public PacketDirection getPacketDirection() {

            return PacketDirection.SERVERBOUND;

    }
        @Override
        public void encode(
                ServerboundBoolExprStatementInspectionRequestPacket msg,
                FriendlyByteBuf friendlyByteBuf
        ) {

            friendlyByteBuf.writeString(SFMPacketDaddy.truncate(msg.programString, Program.MAX_PROGRAM_LENGTH));
            friendlyByteBuf.writeInt(msg.inputNodeIndex());
        }

        @Override
        public ServerboundBoolExprStatementInspectionRequestPacket decode(FriendlyByteBuf friendlyByteBuf) {

            return new ServerboundBoolExprStatementInspectionRequestPacket(
                    friendlyByteBuf.readString(Program.MAX_PROGRAM_LENGTH),
                    friendlyByteBuf.readInt()
            );
        }

        @Override
        public void handle(
                ServerboundBoolExprStatementInspectionRequestPacket msg,
                SFMPacketHandlingContext context
        ) {

            context.compileAndThen(
                    msg.programString,
                    false,
                    (program, player, managerBlockEntity) ->
                            program.astBuilder()
                                    .getNodeAtIndex(msg.inputNodeIndex)
                                    .filter(BoolExpr.class::isInstance)
                                    .map(BoolExpr.class::cast)
                                    .ifPresent(expr -> {
                                        StringBuilder payload = new StringBuilder();
                                        payload
                                                .append(expr.toStringPretty())
                                                .append("\n-- peek results --\n");
                                        ProgramContext programContext = new ProgramContext(
                                                program,
                                                managerBlockEntity,
                                                new SimulateExploreAllPathsProgramBehaviour()
                                        );
                                        boolean result = expr.test(programContext);
                                        payload.append(result ? "TRUE" : "FALSE");

                                        SFMPackets.sendToPlayer(
                                                player,
                                                new ClientboundBoolExprStatementInspectionResultsPacket(
                                                        SFMPacketDaddy.truncate(
                                                                payload.toString(),
                                                                ClientboundBoolExprStatementInspectionResultsPacket.MAX_RESULTS_LENGTH
                                                        ))
                                        );
                                    })
            );
        }

        @Override
        public Class<Packet> getPacketClass() {

            return Packet.class;
        }

    }

    public static final Daddy daddy = new Daddy();

    public static class Packet extends Wrapper<ServerboundBoolExprStatementInspectionRequestPacket> {

        @Override
        SFMPacketDaddy<ServerboundBoolExprStatementInspectionRequestPacket> getDaddy() {
            return daddy;
        }

    }


    @Override
    public Wrapper<ServerboundBoolExprStatementInspectionRequestPacket> wrap() {
        var wrapper = new Packet();
        wrapper.ourRecord = this;
        return wrapper;
    }


}