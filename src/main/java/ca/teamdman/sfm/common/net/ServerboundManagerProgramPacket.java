package ca.teamdman.sfm.common.net;

import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import ca.teamdman.sfm.common.containermenu.ManagerContainerMenu;
import ca.teamdman.sfml.ast.Program;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.PacketBuffer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import java.io.IOException;

public class ServerboundManagerProgramPacket extends SFMAdvancedPacket<ServerboundManagerProgramPacket> {
    private int windowId;
    private BlockPos pos;
    private String program;

    public ServerboundManagerProgramPacket(int windowId, BlockPos pos, String program) {
        this.windowId = windowId;
        this.pos = pos;
        this.program = program;
    }

    public ServerboundManagerProgramPacket() {
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        PacketBuffer packetBuffer = new PacketBuffer(buf);
        windowId = packetBuffer.readVarInt();
        pos = packetBuffer.readBlockPos();
        try {
            program = packetBuffer.readString(Program.MAX_PROGRAM_LENGTH);
        } catch (DecoderException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        PacketBuffer packetBuffer = new PacketBuffer(buf);
        packetBuffer.writeVarInt(windowId);
        packetBuffer.writeBlockPos(pos);
        packetBuffer.writeString(program);
    }

    @Override
    public void handle(
            ServerboundManagerProgramPacket msg,
            SFMPacketHandlingContext context
    ) {
        context.handleServerboundContainerPacket(
                ManagerContainerMenu.class,
                ManagerBlockEntity.class,
                msg.pos,
                msg.windowId,
                (menu, manager) -> manager.setProgram(msg.program)
        );
    }

}