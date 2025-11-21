package ca.teamdman.sfm.common.net;

import net.minecraft.network.PacketBuffer;

import ca.teamdman.sfm.common.program.ProgramContext;
import ca.teamdman.sfm.common.program.SimulateExploreAllPathsProgramBehaviour;
import ca.teamdman.sfm.common.registry.SFMPackets;
import ca.teamdman.sfml.ast.IfStatement;
import ca.teamdman.sfml.ast.Program;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderException;

public class ServerboundIfStatementInspectionRequestPacket extends
                                                           SFMAdvancedPacket<ServerboundIfStatementInspectionRequestPacket> {

    private String programString;
    private int inputNodeIndex;

    public ServerboundIfStatementInspectionRequestPacket(String programString, int inputNodeIndex) {
        this.programString = programString;
        this.inputNodeIndex = inputNodeIndex;
    }

    public ServerboundIfStatementInspectionRequestPacket() {}

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
                       ServerboundIfStatementInspectionRequestPacket msg,
                       SFMPacketHandlingContext context) {
        context.compileAndThen(
                msg.programString,
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
                                    new SimulateExploreAllPathsProgramBehaviour());
                            boolean result = ifStatement.condition().test(programContext);
                            payload.append(result ? "TRUE" : "FALSE");

                            SFMPackets.sendToPlayer(player, new ClientboundIfStatementInspectionResultsPacket(
                                    SFMAdvancedPacket.truncate(
                                            payload.toString(),
                                            ClientboundIfStatementInspectionResultsPacket.MAX_RESULTS_LENGTH)));
                        }));
    }
}
