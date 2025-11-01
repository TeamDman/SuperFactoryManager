package ca.teamdman.sfm.common.net;

import ca.teamdman.sfm.client.screen.SFMScreenChangeHelpers;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.inventory.Container;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import java.io.IOException;

public class ClientboundContainerExportsInspectionResultsPacket extends SFMPacket<ClientboundContainerExportsInspectionResultsPacket> {
    private int windowId;
    private String results;

    public ClientboundContainerExportsInspectionResultsPacket(int windowId, String results) {
        this.windowId = windowId;
        this.results = results;
    }

    public ClientboundContainerExportsInspectionResultsPacket() {
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        PacketBuffer packetBuffer = new PacketBuffer(buf);
        windowId = packetBuffer.readVarInt();
        try {
            results = packetBuffer.readString(20480);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        PacketBuffer packetBuffer = new PacketBuffer(buf);
        packetBuffer.writeVarInt(windowId);
        packetBuffer.writeString(results);
    }

    @Override
    public IMessage onMessage(ClientboundContainerExportsInspectionResultsPacket message, MessageContext ctx) {
        EntityPlayerSP player = Minecraft.getMinecraft().player;
        if (player == null) return null;
        Container container = player.openContainer;
        if (container.windowId != message.windowId) return null;
        SFMScreenChangeHelpers.showProgramEditScreen(message.results);
        return null;
    }
}