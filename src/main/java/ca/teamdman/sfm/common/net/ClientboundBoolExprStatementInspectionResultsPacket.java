package ca.teamdman.sfm.common.net;

import ca.teamdman.sfm.client.screen.SFMScreenChangeHelpers;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import java.io.IOException;

public class ClientboundBoolExprStatementInspectionResultsPacket extends SFMPacket<ClientboundBoolExprStatementInspectionResultsPacket> {
    private String results;

    public ClientboundBoolExprStatementInspectionResultsPacket(String results) {
        this.results = results;
    }

    public ClientboundBoolExprStatementInspectionResultsPacket() {
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        try {
            results = new PacketBuffer(buf).readString(2048);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        new PacketBuffer(buf).writeString(results);
    }

    @Override
    public IMessage onMessage(ClientboundBoolExprStatementInspectionResultsPacket message, MessageContext ctx) {
        SFMScreenChangeHelpers.showProgramEditScreen(message.results);
        return null;
    }
}