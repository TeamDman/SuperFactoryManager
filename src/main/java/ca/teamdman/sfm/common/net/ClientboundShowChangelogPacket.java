package ca.teamdman.sfm.common.net;

import ca.teamdman.sfm.client.screen.SFMScreenChangeHelpers;
import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

public class ClientboundShowChangelogPacket extends SFMPacket<ClientboundShowChangelogPacket> {

    public ClientboundShowChangelogPacket() {
    }

    @Override
    public void fromBytes(ByteBuf buf) {
    }

    @Override
    public void toBytes(ByteBuf buf) {
    }

    @Override
    public IMessage onMessage(ClientboundShowChangelogPacket message, MessageContext ctx) {
        SFMScreenChangeHelpers.showChangelog();
        return null;
    }
}