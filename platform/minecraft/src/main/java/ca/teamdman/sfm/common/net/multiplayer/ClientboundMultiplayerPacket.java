package ca.teamdman.sfm.common.net.multiplayer;

import ca.teamdman.sfm.common.net.*;
import net.minecraft.network.FriendlyByteBuf;

public record ClientboundMultiplayerPacket(byte[] frame) implements SFMPacket {
    public ClientboundMultiplayerPacket {
        SFMMultiplayerPacketProtocol.requireFrameSize(frame.length);
        frame = frame.clone();
    }
    @Override public byte[] frame() { return frame.clone(); }
    public static final class Daddy implements SFMPacketDaddy<ClientboundMultiplayerPacket> {
        public PacketDirection getPacketDirection() { return PacketDirection.CLIENTBOUND; }
        public Class<ClientboundMultiplayerPacket> getPacketClass() { return ClientboundMultiplayerPacket.class; }
        public void encode(ClientboundMultiplayerPacket packet, FriendlyByteBuf buffer) { buffer.writeBytes(packet.frame); }
        public ClientboundMultiplayerPacket decode(FriendlyByteBuf buffer) {
            return new ClientboundMultiplayerPacket(SFMMultiplayerPacketWire.copyFrame(buffer));
        }
        public void handle(ClientboundMultiplayerPacket packet, SFMPacketHandlingContext context) {
            SFMMultiplayerPacketReceivers.receiveClient(context.networkConnectionIdentity(), packet.frame());
        }
    }
}
