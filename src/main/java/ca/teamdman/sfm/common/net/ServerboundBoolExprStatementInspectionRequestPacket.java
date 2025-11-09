package ca.teamdman.sfm.common.net;

import ca.teamdman.sfm.common.program.ProgramContext;
import ca.teamdman.sfm.common.program.SimulateExploreAllPathsProgramBehaviour;
import ca.teamdman.sfm.common.registry.SFMPackets;
import ca.teamdman.sfml.ast.BoolExpr;
import ca.teamdman.sfml.ast.Program;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import java.io.IOException;

public class ServerboundBoolExprStatementInspectionRequestPacket extends SFMAdvancedPacket<ServerboundBoolExprStatementInspectionRequestPacket> {
    private String programString;
    private int inputNodeIndex;

    public ServerboundBoolExprStatementInspectionRequestPacket(String programString, int inputNodeIndex) {
        this.programString = programString;
        this.inputNodeIndex = inputNodeIndex;
    }

    public ServerboundBoolExprStatementInspectionRequestPacket() {
    }

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
            ServerboundBoolExprStatementInspectionRequestPacket msg,
            SFMPacketHandlingContext context
    ) {
        context.compileAndThen(
                msg.programString,
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
                                                    SFMAdvancedPacket.truncate(
                                                            payload.toString(),
                                                            ClientboundBoolExprStatementInspectionResultsPacket.MAX_RESULTS_LENGTH
                                                    ))
                                    );
                                })
        );
    }


}