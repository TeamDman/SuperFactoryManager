package ca.teamdman.sfm.common.net;

import ca.teamdman.sfm.common.value.SFMValue;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SFMClientInboxServerTests {
    private static final ResourceLocation DIMENSION = new ResourceLocation("minecraft", "overworld");
    private static final ResourceLocation CHANNEL = new ResourceLocation("sfm", "dashboard");

    @Test
    void subscriptionSessionAndPerTickBudgetAreBounded() {
        SFMServerClientInboxSubscriptions state = new SFMServerClientInboxSubscriptions();
        UUID owner = UUID.randomUUID();
        UUID firstSession = UUID.randomUUID();
        SFMClientInboxAddress address = new SFMClientInboxAddress(owner, DIMENSION, CHANNEL);
        assertTrue(state.subscribe(owner, firstSession, DIMENSION, CHANNEL));
        assertEquals(firstSession, state.subscribedSession(address).orElseThrow());
        for (int i = 0; i < SFMServerClientInboxSubscriptions.MAX_MESSAGES_PER_TICK; i++) {
            assertTrue(state.reserveSend(owner, 42, 1));
        }
        assertFalse(state.reserveSend(owner, 42, 1));
        assertTrue(state.reserveSend(owner, 43, 1));

        UUID nextSession = UUID.randomUUID();
        assertTrue(state.subscribe(owner, nextSession, DIMENSION, CHANNEL));
        state.unsubscribe(owner, firstSession, DIMENSION, CHANNEL);
        assertEquals(nextSession, state.subscribedSession(address).orElseThrow());
        state.unsubscribe(owner, nextSession, DIMENSION, CHANNEL);
        assertTrue(state.subscribedSession(address).isEmpty());
    }

    @Test
    void addressedWirePacketsRoundTripWithExplicitDirections() {
        UUID owner = UUID.randomUUID();
        UUID session = UUID.randomUUID();
        SFMClientInboxAddress address = new SFMClientInboxAddress(owner, DIMENSION, CHANNEL);
        ClientboundClientInboxValuePacket delivery = ClientboundClientInboxValuePacket.fromValue(
                session, address, SFMValue.of(0.5)
        );
        ClientboundClientInboxValuePacket.Daddy deliveryDaddy = new ClientboundClientInboxValuePacket.Daddy();
        FriendlyByteBuf deliveryBytes = new FriendlyByteBuf(Unpooled.buffer());
        deliveryDaddy.encode(delivery, deliveryBytes);
        assertEquals(SFMPacketDaddy.PacketDirection.CLIENTBOUND, deliveryDaddy.getPacketDirection());
        assertEquals(delivery, deliveryDaddy.decode(deliveryBytes));

        ServerboundClientInboxSubscriptionPacket request = new ServerboundClientInboxSubscriptionPacket(
                session, DIMENSION, CHANNEL, true
        );
        ServerboundClientInboxSubscriptionPacket.Daddy requestDaddy =
                new ServerboundClientInboxSubscriptionPacket.Daddy();
        FriendlyByteBuf requestBytes = new FriendlyByteBuf(Unpooled.buffer());
        requestDaddy.encode(request, requestBytes);
        assertEquals(SFMPacketDaddy.PacketDirection.SERVERBOUND, requestDaddy.getPacketDirection());
        assertEquals(request, requestDaddy.decode(requestBytes));
    }

    @Test
    void channelAndByteLimitsHoldAcrossSessionRotation() {
        SFMServerClientInboxSubscriptions state = new SFMServerClientInboxSubscriptions();
        UUID owner = UUID.randomUUID();
        UUID firstSession = UUID.randomUUID();
        for (int i = 0; i < SFMServerClientInboxSubscriptions.MAX_CHANNELS_PER_PLAYER; i++) {
            assertTrue(state.subscribe(
                    owner, firstSession, DIMENSION, new ResourceLocation("sfm", "channel_" + i)
            ));
        }
        assertFalse(state.subscribe(owner, firstSession, DIMENSION, new ResourceLocation("sfm", "overflow")));
        assertTrue(state.reserveSend(owner, 10, SFMServerClientInboxSubscriptions.MAX_BYTES_PER_TICK));
        assertFalse(state.reserveSend(owner, 10, 1));
        assertTrue(state.subscribe(owner, UUID.randomUUID(), DIMENSION, CHANNEL));
        assertFalse(state.reserveSend(owner, 10, 1));
        assertTrue(state.reserveSend(owner, 11, 1));
    }
}
