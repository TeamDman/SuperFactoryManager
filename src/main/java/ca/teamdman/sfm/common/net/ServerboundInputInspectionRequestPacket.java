package ca.teamdman.sfm.common.net;

import ca.teamdman.sfm.common.program.ProgramContext;
import ca.teamdman.sfm.common.program.SimulateExploreAllPathsProgramBehaviour;
import ca.teamdman.sfm.common.registry.SFMPackets;
import ca.teamdman.sfm.common.util.SFMASTUtils;
import ca.teamdman.sfml.ast.InputStatement;
import ca.teamdman.sfml.ast.Program;
import com.github.bsideup.jabel.Desugar;

@Desugar
public record ServerboundInputInspectionRequestPacket(
        String programString,

        int inputNodeIndex
) implements SFMPacket<ServerboundInputInspectionRequestPacket> {
    public static class Daddy implements SFMPacketDaddy<ServerboundInputInspectionRequestPacket> {
        @Override
        public PacketDirection getPacketDirection() {

            return PacketDirection.SERVERBOUND;
        }

        @Override
        public void encode(
                ServerboundInputInspectionRequestPacket msg,
                FriendlyByteBuf friendlyByteBuf
        ) {

            friendlyByteBuf.writeString(SFMPacketDaddy.truncate(msg.programString, Program.MAX_PROGRAM_LENGTH));
            friendlyByteBuf.writeInt(msg.inputNodeIndex());
        }

        @Override
        public ServerboundInputInspectionRequestPacket decode(FriendlyByteBuf friendlyByteBuf) {

            return new ServerboundInputInspectionRequestPacket(
                    friendlyByteBuf.readString(Program.MAX_PROGRAM_LENGTH),
                    friendlyByteBuf.readInt()
            );
        }

        @Override
        public void handle(
                ServerboundInputInspectionRequestPacket msg,
                SFMPacketHandlingContext context
        ) {

            context.compileAndThen(
                    msg.programString,
                    false,
                    (program, player, managerBlockEntity) ->
                            program.astBuilder()
                                    .getNodeAtIndex(msg.inputNodeIndex)
                                    .filter(InputStatement.class::isInstance)
                                    .map(InputStatement.class::cast)
                                    .ifPresent(inputStatement -> {
                                        StringBuilder payload = new StringBuilder();
                                        payload
                                                .append(inputStatement.toStringPretty())
                                                .append("\n-- peek results --\n");

                                        ProgramContext programContext = new ProgramContext(
                                                program,
                                                managerBlockEntity,
                                                new SimulateExploreAllPathsProgramBehaviour()
                                        );
                                        int preLen = payload.length();
                                        inputStatement.gatherSlots(
                                                programContext,
                                                slot -> SFMASTUtils
                                                        .getInputStatementForSlot(
                                                                slot,
                                                                inputStatement.labelAccess()
                                                        )
                                                        .ifPresent(is -> payload
                                                                .append(is.toStringPretty())
                                                                .append("\n"))
                                        );
                                        if (payload.length() == preLen) {
                                            payload.append("none");
                                        }

                                        SFMPackets.sendToPlayer(
                                                player,
                                                new ClientboundInputInspectionResultsPacket(
                                                        SFMPacketDaddy.truncate(
                                                                payload.toString(),
                                                                ClientboundInputInspectionResultsPacket.MAX_RESULTS_LENGTH
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

    public static class Packet extends Wrapper<ServerboundInputInspectionRequestPacket> {

        @Override
        SFMPacketDaddy<ServerboundInputInspectionRequestPacket> getDaddy() {
            return daddy;
        }

    }


    @Override
    public Wrapper<ServerboundInputInspectionRequestPacket> wrap() {
        var wrapper = new Packet();
        wrapper.ourRecord = this;
        return wrapper;
    }
}
