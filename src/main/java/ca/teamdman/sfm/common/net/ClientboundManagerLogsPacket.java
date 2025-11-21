package ca.teamdman.sfm.common.net;

import java.util.Collection;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.inventory.Container;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import ca.teamdman.sfm.common.containermenu.ManagerContainerMenu;
import ca.teamdman.sfm.common.logging.TranslatableLogEvent;
import ca.teamdman.sfm.common.logging.TranslatableLogger;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

public class ClientboundManagerLogsPacket extends SFMPacket<ClientboundManagerLogsPacket> {

    private int windowId;
    private PacketBuffer logsBuf;

    public ClientboundManagerLogsPacket(int windowId, PacketBuffer logsBuf) {
        this.windowId = windowId;
        this.logsBuf = logsBuf;
    }

    public ClientboundManagerLogsPacket() {}

    public static ClientboundManagerLogsPacket drainToCreate(
                                                             int windowId,
                                                             Collection<TranslatableLogEvent> logs) {
        PacketBuffer buf = new PacketBuffer(Unpooled.buffer());
        TranslatableLogger.encodeAndDrain(logs, buf);
        return new ClientboundManagerLogsPacket(windowId, buf);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        PacketBuffer packetBuffer = new PacketBuffer(buf);
        windowId = packetBuffer.readVarInt();
        int size = packetBuffer.readVarInt();
        logsBuf = new PacketBuffer(Unpooled.buffer(size));
        packetBuffer.readBytes(logsBuf, size);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        PacketBuffer packetBuffer = new PacketBuffer(buf);
        packetBuffer.writeVarInt(windowId);
        packetBuffer.writeVarInt(logsBuf.readableBytes());
        packetBuffer.writeBytes(logsBuf, 0, logsBuf.readableBytes());
    }

    @Override
    @Nullable
    public IMessage onMessage(ClientboundManagerLogsPacket message, MessageContext ctx) {
        EntityPlayerSP player = Minecraft.getMinecraft().player;
        if (player == null) return null;
        Container container = player.openContainer;
        if (!(container instanceof ManagerContainerMenu) || container.windowId != message.windowId) {
            return null;
        }
        ManagerContainerMenu menu = (ManagerContainerMenu) container;
        var logs = TranslatableLogger.decode(message.logsBuf);
        menu.logs.addAll(logs);
        return null;
    }
}
