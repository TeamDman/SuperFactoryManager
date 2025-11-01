package ca.teamdman.sfm.common.net;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.containermenu.ManagerContainerMenu;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.inventory.Container;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import java.io.IOException;

public class ClientboundManagerLogLevelUpdatedPacket extends SFMPacket<ClientboundManagerLogLevelUpdatedPacket> {
    private int windowId;
    private String logLevel;

    public ClientboundManagerLogLevelUpdatedPacket(int windowId, String logLevel) {
        this.windowId = windowId;
        this.logLevel = logLevel;
    }

    public ClientboundManagerLogLevelUpdatedPacket() {
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        PacketBuffer packetBuffer = new PacketBuffer(buf);
        windowId = packetBuffer.readVarInt();
        try {
            logLevel = packetBuffer.readString(ServerboundManagerSetLogLevelPacket.MAX_LOG_LEVEL_NAME_LENGTH);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        PacketBuffer packetBuffer = new PacketBuffer(buf);
        packetBuffer.writeVarInt(windowId);
        packetBuffer.writeString(logLevel);
    }

    @Override
    public IMessage onMessage(ClientboundManagerLogLevelUpdatedPacket message, MessageContext ctx) {
        EntityPlayerSP player = Minecraft.getMinecraft().player;
        if (player == null) return null;
        Container container = player.openContainer;
        if (!(container instanceof ManagerContainerMenu) || container.windowId != message.windowId) {
            SFM.LOGGER.error("Invalid log level packet received, ignoring.");
            return null;
        }
        ManagerContainerMenu menu = (ManagerContainerMenu) container;
        menu.logLevel = message.logLevel;
        return null;
    }
}