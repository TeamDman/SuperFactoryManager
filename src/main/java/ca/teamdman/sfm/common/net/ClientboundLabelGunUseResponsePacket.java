package ca.teamdman.sfm.common.net;

import ca.teamdman.sfm.client.ClientLabelGunResponseChatHelper;
import ca.teamdman.sfm.common.registry.SFMPackets;
import com.github.bsideup.jabel.Desugar;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;

@Desugar
public record ClientboundLabelGunUseResponsePacket(
        Behaviour behaviour
) implements SFMPacket<ClientboundLabelGunUseResponsePacket> {
    public enum Behaviour {
        Pushed,
        Pulled
    }

    public void sendToPlayer(EntityPlayer player) {
        if (player instanceof EntityPlayerMP serverPlayer) {
            SFMPackets.sendToPlayer(serverPlayer, this);
        }
    }

    public static class Daddy implements SFMPacketDaddy<ClientboundLabelGunUseResponsePacket> {

        @Override
        public PacketDirection getPacketDirection() {
            return PacketDirection.CLIENTBOUND;
        }

        @Override
        public Class<Packet> getPacketClass() {
            return Packet.class;
        }

        @Override
        public void encode(
                ClientboundLabelGunUseResponsePacket msg,
                FriendlyByteBuf friendlyByteBuf
        ) {
            friendlyByteBuf.writeEnumValue(msg.behaviour());
        }

        @Override
        public ClientboundLabelGunUseResponsePacket decode(FriendlyByteBuf friendlyByteBuf) {
            return new ClientboundLabelGunUseResponsePacket(friendlyByteBuf.readEnumValue(Behaviour.class));
        }

        @Override
        public void handle(
                ClientboundLabelGunUseResponsePacket msg,
                SFMPacketHandlingContext context
        ) {
            ClientLabelGunResponseChatHelper.handle(msg, context);
        }
    }


    public static final Daddy daddy = new Daddy();

    public static class Packet extends Wrapper<ClientboundLabelGunUseResponsePacket> {

        @Override
        SFMPacketDaddy<ClientboundLabelGunUseResponsePacket> getDaddy() {
            return daddy;
        }
    }


    @Override
    public Wrapper<ClientboundLabelGunUseResponsePacket> wrap() {
        var wrapper = new Packet();
        wrapper.ourRecord = this;
        return wrapper;
    }
}