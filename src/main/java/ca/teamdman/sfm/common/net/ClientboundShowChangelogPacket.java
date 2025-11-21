package ca.teamdman.sfm.common.net;

import javax.annotation.Nullable;

import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import ca.teamdman.sfm.client.screen.SFMScreenChangeHelpers;
import io.netty.buffer.ByteBuf;

public class ClientboundShowChangelogPacket extends SFMPacket<ClientboundShowChangelogPacket> {

    public ClientboundShowChangelogPacket() {}

    @Override
    public void fromBytes(ByteBuf buf) {}

    @Override
    public void toBytes(ByteBuf buf) {}

    @Override
    @Nullable
    public IMessage onMessage(ClientboundShowChangelogPacket message, MessageContext ctx) {
        SFMScreenChangeHelpers.showChangelog();
        return null;
    }
}
