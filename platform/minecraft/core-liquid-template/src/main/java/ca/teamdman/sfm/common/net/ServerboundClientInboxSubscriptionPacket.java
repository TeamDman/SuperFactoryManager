package ca.teamdman.sfm.common.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;
import java.util.UUID;

/** A subscription is scoped to one client-world session and the sender's own inbox. */
public record ServerboundClientInboxSubscriptionPacket(
        UUID session,
        ResourceLocation dimension,
        ResourceLocation channel,
        boolean subscribe
) implements SFMPacket {
    public ServerboundClientInboxSubscriptionPacket {
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(dimension, "dimension");
        Objects.requireNonNull(channel, "channel");
        if (dimension.toString().length() > SFMClientInboxAddress.MAX_ID_CHARACTERS
            || channel.toString().length() > SFMClientInboxAddress.MAX_ID_CHARACTERS) {
            throw new IllegalArgumentException("Inbox identifier is too long");
        }
    }

    public static class Daddy implements SFMPacketDaddy<ServerboundClientInboxSubscriptionPacket> {
        @Override
        public PacketDirection getPacketDirection() {
            return PacketDirection.SERVERBOUND;
        }

        @Override
        public Class<ServerboundClientInboxSubscriptionPacket> getPacketClass() {
            return ServerboundClientInboxSubscriptionPacket.class;
        }

        @Override
        public void encode(ServerboundClientInboxSubscriptionPacket msg, FriendlyByteBuf target) {
            target.writeUUID(msg.session);
            target.writeUtf(msg.dimension.toString(), SFMClientInboxAddress.MAX_ID_CHARACTERS);
            target.writeUtf(msg.channel.toString(), SFMClientInboxAddress.MAX_ID_CHARACTERS);
            target.writeBoolean(msg.subscribe);
        }

        @Override
        public ServerboundClientInboxSubscriptionPacket decode(FriendlyByteBuf source) {
            return new ServerboundClientInboxSubscriptionPacket(
                    source.readUUID(),
                    new ResourceLocation(source.readUtf(SFMClientInboxAddress.MAX_ID_CHARACTERS)),
                    new ResourceLocation(source.readUtf(SFMClientInboxAddress.MAX_ID_CHARACTERS)),
                    source.readBoolean()
            );
        }

        @Override
        public void handle(ServerboundClientInboxSubscriptionPacket msg, SFMPacketHandlingContext context) {
            SFMServerClientInboxTransport.receiveSubscription(msg, context.sender());
        }
    }
}
