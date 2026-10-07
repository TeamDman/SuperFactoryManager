package ca.teamdman.sfm.common.net.multiplayer;

import ca.teamdman.sfm.common.net.*;
import net.minecraft.network.FriendlyByteBuf;

/** New negotiated transport. Legacy private-world packets remain a separate, closed boundary. */
public record ServerboundMultiplayerPacket(byte[] frame) implements SFMPacket {
    public ServerboundMultiplayerPacket {
        SFMMultiplayerPacketProtocol.requireFrameSize(frame.length);
        frame = frame.clone();
    }
    @Override public byte[] frame() { return frame.clone(); }
    public static final class Daddy implements SFMPacketDaddy<ServerboundMultiplayerPacket> {
        public PacketDirection getPacketDirection() { return PacketDirection.SERVERBOUND; }
        public Class<ServerboundMultiplayerPacket> getPacketClass() { return ServerboundMultiplayerPacket.class; }
        public void encode(ServerboundMultiplayerPacket packet, FriendlyByteBuf buffer) { buffer.writeBytes(packet.frame); }
        public ServerboundMultiplayerPacket decode(FriendlyByteBuf buffer) {
            return new ServerboundMultiplayerPacket(SFMMultiplayerPacketWire.copyFrame(buffer));
        }
        public void handle(ServerboundMultiplayerPacket packet, SFMPacketHandlingContext context) {
            SFMMultiplayerServerRuntime.receive(context.sender(), packet.frame);
        }
    }
}
