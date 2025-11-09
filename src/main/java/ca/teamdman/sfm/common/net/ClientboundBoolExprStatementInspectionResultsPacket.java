package ca.teamdman.sfm.common.net;

import ca.teamdman.sfm.client.screen.SFMScreenChangeHelpers;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import javax.annotation.Nullable;
import java.io.IOException;

public class ClientboundBoolExprStatementInspectionResultsPacket extends SFMPacket<ClientboundBoolExprStatementInspectionResultsPacket> {
    public static final int MAX_RESULTS_LENGTH = 2048;

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
        } catch (DecoderException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        new PacketBuffer(buf).writeString(results);
    }

    @Override
    @Nullable
    public IMessage onMessage(ClientboundBoolExprStatementInspectionResultsPacket message, MessageContext ctx) {
        SFMScreenChangeHelpers.showProgramEditScreen(message.results);
        return null;
    }
}