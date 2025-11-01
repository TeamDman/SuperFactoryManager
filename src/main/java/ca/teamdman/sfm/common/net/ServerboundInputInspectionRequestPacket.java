package ca.teamdman.sfm.common.net;

import ca.teamdman.sfm.common.program.ProgramContext;
import ca.teamdman.sfm.common.program.SimulateExploreAllPathsProgramBehaviour;
import ca.teamdman.sfm.common.registry.SFMPackets;
import ca.teamdman.sfm.common.util.SFMASTUtils;
import ca.teamdman.sfml.ast.InputStatement;
import ca.teamdman.sfml.ast.Program;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import java.io.IOException;

public class ServerboundInputInspectionRequestPacket extends SFMPacket<ServerboundInputInspectionRequestPacket> {
    private String programString;
    private int inputNodeIndex;

    public ServerboundInputInspectionRequestPacket(String programString, int inputNodeIndex) {
        this.programString = programString;
        this.inputNodeIndex = inputNodeIndex;
    }

    public ServerboundInputInspectionRequestPacket() {
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
    public IMessage onMessage(ServerboundInputInspectionRequestPacket message, MessageContext ctx) {
        EntityPlayerMP player = ctx.getServerHandler().player;
        player.getServerWorld().addScheduledTask(() -> {
            Program.compile(message.programString).ifPresent(program -> {
                program.astBuilder()
                        .getNodeAtIndex(message.inputNodeIndex)
                        .filter(InputStatement.class::isInstance)
                        .map(InputStatement.class::cast)
                        .ifPresent(inputStatement -> {
                            StringBuilder payload = new StringBuilder();
                            payload
                                    .append(inputStatement.toStringPretty())
                                    .append("\n-- peek results --\n");

                            ProgramContext programContext = new ProgramContext(
                                    program,
                                    null, // managerBlockEntity is not available on the client
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

                            SFMPackets.SFM_CHANNEL.sendTo(new ClientboundInputInspectionResultsPacket(
                                    payload.toString()
                            ), player);
                        });
            });
        });
        return null;
    }
}