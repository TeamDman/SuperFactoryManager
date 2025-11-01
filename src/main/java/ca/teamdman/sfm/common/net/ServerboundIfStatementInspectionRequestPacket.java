package ca.teamdman.sfm.common.net;

import ca.teamdman.sfm.common.program.ProgramContext;
import ca.teamdman.sfm.common.program.SimulateExploreAllPathsProgramBehaviour;
import ca.teamdman.sfm.common.registry.SFMPackets;
import ca.teamdman.sfml.ast.IfStatement;
import ca.teamdman.sfml.ast.Program;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import java.io.IOException;

public class ServerboundIfStatementInspectionRequestPacket extends SFMPacket<ServerboundIfStatementInspectionRequestPacket> {
    private String programString;
    private int inputNodeIndex;

    public ServerboundIfStatementInspectionRequestPacket(String programString, int inputNodeIndex) {
        this.programString = programString;
        this.inputNodeIndex = inputNodeIndex;
    }

    public ServerboundIfStatementInspectionRequestPacket() {
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        PacketBuffer packetBuffer = new PacketBuffer(buf);
        try {
            programString = packetBuffer.readString(Program.MAX_PROGRAM_LENGTH);
        } catch (IOException e) {
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
    public IMessage onMessage(ServerboundIfStatementInspectionRequestPacket message, MessageContext ctx) {
        EntityPlayerMP player = ctx.getServerHandler().player;
        player.getServerWorld().addScheduledTask(() -> {
            Program.compile(message.programString).ifPresent(program -> {
                program.astBuilder()
                        .getNodeAtIndex(message.inputNodeIndex)
                        .filter(IfStatement.class::isInstance)
                        .map(IfStatement.class::cast)
                        .ifPresent(ifStatement -> {
                            StringBuilder payload = new StringBuilder();
                            payload
                                    .append(ifStatement.toStringCondensed())
                                    .append("\n-- peek results --\n");
                            ProgramContext programContext = new ProgramContext(
                                    program,
                                    null, // managerBlockEntity is not available on the client
                                    new SimulateExploreAllPathsProgramBehaviour()
                            );
                            boolean result = ifStatement.condition().test(programContext);
                            payload.append(result ? "TRUE" : "FALSE");

                            SFMPackets.SFM_CHANNEL.sendTo(new ClientboundIfStatementInspectionResultsPacket(
                                    payload.toString()
                            ), player);
                        });
            });
        });
        return null;
    }
}