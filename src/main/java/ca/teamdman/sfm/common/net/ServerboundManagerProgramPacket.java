package ca.teamdman.sfm.common.net;

import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import ca.teamdman.sfm.common.containermenu.ManagerContainerMenu;
import ca.teamdman.sfml.ast.Program;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.PacketBuffer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import java.io.IOException;

public class ServerboundManagerProgramPacket extends SFMPacket<ServerboundManagerProgramPacket> {
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
        } catch (IOException e) {
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
    public IMessage onMessage(ServerboundManagerProgramPacket message, MessageContext ctx) {
        EntityPlayerMP player = ctx.getServerHandler().player;
        player.getServerWorld().addScheduledTask(() -> {
            if (player.openContainer instanceof ManagerContainerMenu && player.openContainer.windowId == message.windowId) {
                TileEntity te = player.world.getTileEntity(message.pos);
                if (te instanceof ManagerBlockEntity) {
                    ((ManagerBlockEntity) te).setProgram(message.program);
                }
            }
        });
        return null;
    }
}