package ca.teamdman.sfm.common.net;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.inventory.Container;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import ca.teamdman.sfm.common.containermenu.ManagerContainerMenu;
import ca.teamdman.sfml.ast.Program;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderException;

public class ClientboundManagerGuiUpdatePacket extends SFMPacket<ClientboundManagerGuiUpdatePacket> {

    private int windowId;
    private String program;
    private ManagerBlockEntity.State state;
    private long[] tickTimes;

    public ClientboundManagerGuiUpdatePacket(int windowId, String program, ManagerBlockEntity.State state,
                                             long[] tickTimes) {
        this.windowId = windowId;
        this.program = program;
        this.state = state;
        this.tickTimes = tickTimes;
    }

    public ClientboundManagerGuiUpdatePacket() {}

    public ClientboundManagerGuiUpdatePacket cloneWithWindowId(int windowId) {
        return new ClientboundManagerGuiUpdatePacket(windowId, program, state, tickTimes);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        PacketBuffer packetBuffer = new PacketBuffer(buf);
        windowId = packetBuffer.readVarInt();
        try {
            program = packetBuffer.readString(Program.MAX_PROGRAM_LENGTH);
        } catch (DecoderException e) {
            throw new RuntimeException(e);
        }
        state = packetBuffer.readEnumValue(ManagerBlockEntity.State.class);
        tickTimes = packetBuffer.readLongArray(this.tickTimes, ManagerBlockEntity.TICK_TIME_HISTORY_SIZE * 2);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        PacketBuffer packetBuffer = new PacketBuffer(buf);
        packetBuffer.writeVarInt(windowId);
        packetBuffer.writeString(program);
        packetBuffer.writeEnumValue(state);
        packetBuffer.writeLongArray(tickTimes);
    }

    @Override
    @Nullable
    public IMessage onMessage(ClientboundManagerGuiUpdatePacket message, MessageContext ctx) {
        EntityPlayerSP player = Minecraft.getMinecraft().player;
        if (player == null) return null;
        Container container = player.openContainer;
        if (!(container instanceof ManagerContainerMenu menu) || container.windowId != message.windowId) {
            return null;
        }
        menu.tickTimeNanos = message.tickTimes;
        menu.state = message.state;
        menu.program = message.program;
        return null;
    }
}
