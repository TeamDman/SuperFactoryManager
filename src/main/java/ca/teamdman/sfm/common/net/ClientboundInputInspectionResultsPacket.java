package ca.teamdman.sfm.common.net;

import ca.teamdman.sfm.client.screen.SFMScreenChangeHelpers;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import java.io.IOException;

public class ClientboundInputInspectionResultsPacket extends SFMPacket<ClientboundInputInspectionResultsPacket> {
    private String results;

    public ClientboundInputInspectionResultsPacket(String results) {
        this.results = results;
    }

    public ClientboundInputInspectionResultsPacket() {
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        try {
            results = new PacketBuffer(buf).readString(20480);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        new PacketBuffer(buf).writeString(results);
    }

    @Override
    public IMessage onMessage(ClientboundInputInspectionResultsPacket message, MessageContext ctx) {
        SFMScreenChangeHelpers.showProgramEditScreen(message.results);
        return null;
    }
}