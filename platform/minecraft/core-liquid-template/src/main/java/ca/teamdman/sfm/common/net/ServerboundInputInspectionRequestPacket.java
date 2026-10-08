package ca.teamdman.sfm.common.net;

import ca.teamdman.sfm.common.program.ProgramContext;
import ca.teamdman.sfm.common.program.SimulateExploreAllPathsProgramBehaviour;
{% if features.packet_computation %}
import ca.teamdman.sfm.common.program.WorldProgramInputSource;
{% endif %}
import ca.teamdman.sfm.common.registry.registration.SFMPackets;
import ca.teamdman.sfm.common.util.SFMASTUtils;
import ca.teamdman.sfml.ast.InputStatement;
import ca.teamdman.sfml.ast.Program;
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4" %}
import net.minecraft.network.FriendlyByteBuf;
{% when "1.21", "1.21.1", "26.1.2" %}
import net.minecraft.network.RegistryFriendlyByteBuf;
{% endcase %}

public record ServerboundInputInspectionRequestPacket(
        String programString,

        int inputNodeIndex
) implements SFMPacket {
    public static class Daddy implements SFMPacketDaddy<ServerboundInputInspectionRequestPacket> {
        @Override
        public PacketDirection getPacketDirection() {

            return PacketDirection.SERVERBOUND;
        }

        @Override
        public void encode(
                ServerboundInputInspectionRequestPacket msg,
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4" %}
                FriendlyByteBuf friendlyByteBuf
{% when "1.21", "1.21.1", "26.1.2" %}
                RegistryFriendlyByteBuf friendlyByteBuf
{% endcase %}
        ) {

            friendlyByteBuf.writeUtf(msg.programString, Program.MAX_PROGRAM_LENGTH);
            friendlyByteBuf.writeInt(msg.inputNodeIndex());
        }

        @Override
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4" %}
        public ServerboundInputInspectionRequestPacket decode(FriendlyByteBuf friendlyByteBuf) {
{% when "1.21", "1.21.1", "26.1.2" %}
        public ServerboundInputInspectionRequestPacket decode(RegistryFriendlyByteBuf friendlyByteBuf) {
{% endcase %}
            return new ServerboundInputInspectionRequestPacket(
                    friendlyByteBuf.readUtf(Program.MAX_PROGRAM_LENGTH),
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
{% if features.packet_computation %}
                                        WorldProgramInputSource inputSource = new WorldProgramInputSource(inputStatement);
                                        try {
                                            inputSource.gatherSlots(
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
                                        } finally {
                                            inputSource.free();
                                        }
{% else %}
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
{% endif %}
                                        if (payload.length() == preLen) {
                                            payload.append("none");
                                        }

                                        SFMPackets.sendToPlayer(
                                                () -> player,
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
        public Class<ServerboundInputInspectionRequestPacket> getPacketClass() {

            return ServerboundInputInspectionRequestPacket.class;
        }

    }

}
