package ca.teamdman.sfm.common.net;

import ca.teamdman.sfm.client.ClientLabelGunResponseChatHelper;
import ca.teamdman.sfm.common.registry.SFMPackets;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import javax.annotation.Nullable;

public class ClientboundLabelGunUseResponsePacket extends SFMPacket<ClientboundLabelGunUseResponsePacket> {
    private Behaviour behaviour;

    public ClientboundLabelGunUseResponsePacket(Behaviour behaviour) {
        this.behaviour = behaviour;
    }

    public ClientboundLabelGunUseResponsePacket() {
    }

    public enum Behaviour {
        Pushed,
        Pulled
    }

    public void sendToPlayer(EntityPlayerMP player) {
        SFMPackets.SFM_CHANNEL.sendTo(this, player);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        behaviour = Behaviour.values()[buf.readInt()];
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(behaviour.ordinal());
    }

    @Override
    @Nullable
    public IMessage onMessage(ClientboundLabelGunUseResponsePacket message, MessageContext ctx) {
        ClientLabelGunResponseChatHelper.handle(message, ctx);
        return null;
    }

    public Behaviour getBehaviour() {
        return behaviour;
    }
}