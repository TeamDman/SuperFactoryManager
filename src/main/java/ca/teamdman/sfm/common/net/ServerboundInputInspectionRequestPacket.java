package ca.teamdman.sfm.common.net;

import net.minecraft.network.PacketBuffer;

import ca.teamdman.sfm.common.program.ProgramContext;
import ca.teamdman.sfm.common.program.SimulateExploreAllPathsProgramBehaviour;
import ca.teamdman.sfm.common.registry.SFMPackets;
import ca.teamdman.sfm.common.util.SFMASTUtils;
import ca.teamdman.sfml.ast.InputStatement;
import ca.teamdman.sfml.ast.Program;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderException;

public class ServerboundInputInspectionRequestPacket extends
                                                     SFMAdvancedPacket<ServerboundInputInspectionRequestPacket> {

    private String programString;
    private int inputNodeIndex;

    public ServerboundInputInspectionRequestPacket(String programString, int inputNodeIndex) {
        this.programString = programString;
        this.inputNodeIndex = inputNodeIndex;
    }

    public ServerboundInputInspectionRequestPacket() {}

    @Override
    public void fromBytes(ByteBuf buf) {
        PacketBuffer packetBuffer = new PacketBuffer(buf);
        try {
            programString = packetBuffer.readString(Program.MAX_PROGRAM_LENGTH);
        } catch (DecoderException e) {
            throw new RuntimeException(e);
        }
        inputNodeIndex = packetBuffer.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        PacketBuffer packetBuffer = new PacketBuffer(buf);
        packetBuffer.writeString(programString);
        packetBuffer.writeInt(inputNodeIndex);
    }

    @Override
    public void handle(
                       ServerboundInputInspectionRequestPacket msg,
                       SFMPacketHandlingContext context) {
        context.compileAndThen(
                msg.programString,
                (program, player, managerBlockEntity) -> program.astBuilder()
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
                                    new SimulateExploreAllPathsProgramBehaviour());
                            int preLen = payload.length();
                            inputStatement.gatherSlots(
                                    programContext,
                                    slot -> SFMASTUtils
                                            .getInputStatementForSlot(
                                                    slot,
                                                    inputStatement.labelAccess())
                                            .ifPresent(is -> payload
                                                    .append(is.toStringPretty())
                                                    .append("\n")));
                            if (payload.length() == preLen) {
                                payload.append("none");
                            }

                            SFMPackets.sendToPlayer(
                                    player,
                                    new ClientboundInputInspectionResultsPacket(
                                            SFMAdvancedPacket.truncate(
                                                    payload.toString(),
                                                    ClientboundInputInspectionResultsPacket.MAX_RESULTS_LENGTH)));
                        }));
    }
}
