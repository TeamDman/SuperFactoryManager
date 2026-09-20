package ca.teamdman.sfm.client.net;

import ca.teamdman.sfm.common.net.*;
import ca.teamdman.sfm.common.net.multiplayer.*;
import ca.teamdman.sfm.common.program.signature.*;
import ca.teamdman.sfm.common.value.SFMValue;
import ca.teamdman.sfm.common.value.SFMValueJsonCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

import static ca.teamdman.sfm.common.net.multiplayer.SFMMultiplayerPacketProtocol.*;
import static org.junit.jupiter.api.Assertions.*;

class SFMMultiplayerClientSessionTests {
    private static final ResourceLocation DIM = new ResourceLocation("minecraft:overworld");
    private static final ResourceLocation CHANNEL = new ResourceLocation("sfm:fixture");
    private static final UUID PLAYER = new UUID(1, 2), WORLD = new UUID(3, 4), NONCE = new UUID(5, 6);
    private static final SFMPacketInventoryAddress TARGET = new SFMPacketInventoryAddress(DIM, new BlockPos(1, 64, 2), Optional.empty());
    private static SFMMultiplayerPacketWire.SessionOffer offer(UUID nonce) {
        return new SFMMultiplayerPacketWire.SessionOffer(new Offer(VERSION, nonce, MAX_FRAME_BYTES,
                SFMValueJsonCodec.MAX_ENCODED_UTF8_BYTES), WORLD);
    }
    private static SFMMultiplayerClientSession ready(Object connection, AtomicLong clock) {
        var session = new SFMMultiplayerClientSession(clock::get);
        session.bind(connection, "example.invalid:25565", PLAYER, DIM);
        assertTrue(session.acceptOffer(connection, offer(NONCE)));
        assertTrue(session.negotiation().isPresent());
        assertTrue(session.acceptResult(connection, new Acknowledgement(NONCE, 0, Status.NEGOTIATED, Optional.empty())));
        return session;
    }

    @Test void exactConnectionObjectNonceAndWorldIdentityAreRequired() {
        Object connection = new String("same text");
        var session = ready(connection, new AtomicLong());
        assertFalse(session.acceptOffer(new String("same text"), offer(UUID.randomUUID())));
        assertEquals("example.invalid:25565", session.worldIdentity(WORLD).orElseThrow().serverEndpoint());
        assertTrue(session.worldIdentity(UUID.randomUUID()).isEmpty());
        var pending = session.insertion(TARGET, SFMValue.of(1), Optional.empty()).orElseThrow();
        assertFalse(session.acceptResult(connection, new Acknowledgement(UUID.randomUUID(), pending.sequence(), Status.AUTHORITY_DENIED, Optional.empty())));
        assertEquals(1, session.pendingCount());
        session.bind(new String("same text"), "example.invalid:25565", PLAYER, DIM);
        assertTrue(session.session().isEmpty());
        assertTrue(session.acknowledgements().isEmpty());
        assertFalse(session.acceptResult(connection, new Acknowledgement(NONCE, 1, Status.AUTHORITY_DENIED, Optional.empty())));
    }

    @Test void negotiationIsExplicitOnceAndDuplicateOfferNeverRewindsSequence() {
        var clock = new AtomicLong();
        var session = new SFMMultiplayerClientSession(clock::get);
        Object connection = new Object();
        session.bind(connection, "example.invalid", PLAYER, DIM);
        assertTrue(session.insertion(TARGET, SFMValue.of(1), Optional.empty()).isEmpty());
        assertTrue(session.acceptOffer(connection, offer(NONCE)));
        assertTrue(session.negotiation().isPresent());
        assertTrue(session.negotiation().isEmpty());
        assertTrue(session.insertion(TARGET, SFMValue.of(1), Optional.empty()).isEmpty());
        session.acceptResult(connection, new Acknowledgement(NONCE, 0, Status.NEGOTIATED, Optional.empty()));
        assertEquals(1, session.insertion(TARGET, SFMValue.of(1), Optional.empty()).orElseThrow().sequence());
        assertTrue(session.acceptOffer(connection, offer(NONCE)));
        assertTrue(session.negotiation().isEmpty());
        assertEquals(2, session.insertion(TARGET, SFMValue.of(1), Optional.empty()).orElseThrow().sequence());
    }

    @Test void explicitNextAttemptAfterRateLimitUsesNextSequenceAndNeverRetriesItself() {
        Object connection = new Object();
        var session = ready(connection, new AtomicLong());
        var attempt = session.insertion(TARGET, SFMValue.of(1), Optional.empty()).orElseThrow();
        assertTrue(session.acceptResult(connection, new Acknowledgement(NONCE, attempt.sequence(), Status.RATE_LIMITED, Optional.empty())));
        assertEquals(SFMMultiplayerClientSession.State.READY, session.state());
        assertEquals(0, session.pendingCount());
        assertEquals(2, session.insertion(TARGET, SFMValue.of(1), Optional.empty()).orElseThrow().sequence());
        assertEquals(1, session.pendingCount());
    }

    @Test void pendingAndHistoryAreBoundedAndTimeoutNeverResends() {
        var clock = new AtomicLong();
        Object connection = new Object();
        var session = ready(connection, clock);
        for (int i = 0; i < 200; i++) {
            var attempt = session.insertion(TARGET, SFMValue.of(i), Optional.empty()).orElseThrow();
            assertTrue(session.acceptResult(connection, new Acknowledgement(NONCE, attempt.sequence(), Status.AUTHORITY_DENIED, Optional.empty())));
        }
        assertEquals(SFMMultiplayerClientSession.MAX_HISTORY, session.acknowledgements().size());
        for (int i = 0; i < SFMMultiplayerClientSession.MAX_PENDING; i++) assertTrue(session.insertion(TARGET, SFMValue.of(i), Optional.empty()).isPresent());
        assertTrue(session.insertion(TARGET, SFMValue.of(1), Optional.empty()).isEmpty());
        clock.set(SFMMultiplayerClientSession.ACK_TIMEOUT_MILLIS);
        assertEquals(SFMMultiplayerClientSession.State.FAILED, session.state());
        assertEquals(0, session.pendingCount());
        assertTrue(session.insertion(TARGET, SFMValue.of(1), Optional.empty()).isEmpty());
    }

    @Test void foreignRecipientDimensionAndLocalInboxEpochCannotBeReused() {
        Object connection = new Object();
        var session = ready(connection, new AtomicLong());
        UUID localEpoch = new UUID(7, 8);
        var address = new SFMClientInboxAddress(PLAYER, DIM, CHANNEL);
        assertTrue(session.subscription(localEpoch, new SFMClientInboxAddress(UUID.randomUUID(), DIM, CHANNEL), Optional.empty(), true).isEmpty());
        var outbound = session.subscription(localEpoch, address, Optional.empty(), true).orElseThrow();
        var decoded = (SFMMultiplayerPacketWire.Subscription) SFMMultiplayerPacketWire.decodeClient(outbound.frame());
        assertEquals(localEpoch, decoded.localInboxSession());
        assertNotEquals(NONCE, decoded.localInboxSession());
        var incoming = new SFMMultiplayerPacketWire.InboxValue(NONCE, localEpoch, address, SFMPacketValueEnvelope.fromValue(SFMValue.of(1)));
        assertTrue(session.acceptsInbox(connection, incoming, localEpoch));
        assertFalse(session.acceptsInbox(new Object(), incoming, localEpoch));
        assertFalse(session.acceptsInbox(connection, incoming, UUID.randomUUID()));
        session.bind(connection, "example.invalid:25565", PLAYER, new ResourceLocation("minecraft:the_nether"));
        assertFalse(session.acceptsInbox(connection, incoming, localEpoch));
    }

    @Test void allLabelsAreCarriedInClaimEvenWhenConsentCouldReferenceOnlyOne() {
        Object connection = new Object();
        var session = ready(connection, new AtomicLong());
        var body = new ClientManagerSigningBody("CLIENT BTW", Map.of("display", List.of(1L), "unused", List.of(2L)));
        var snapshot = new ClientManagerSigningSnapshot(UUID.randomUUID(), 3, body, List.of());
        var claim = SFMMultiplayerClientSession.claimFromSnapshot(DIM, BlockPos.ZERO, snapshot);
        assertEquals(body.bindingSha256(), claim.bindingsSha256());
        var changed = new ClientManagerSigningBody(body.source(), Map.of("display", List.of(1L), "unused", List.of(3L)));
        assertNotEquals(changed.bindingSha256(), claim.bindingsSha256());
        var outbound = session.insertion(TARGET, SFMValue.of(1), Optional.of(claim)).orElseThrow();
        var decoded = (SFMMultiplayerPacketWire.Insert) SFMMultiplayerPacketWire.decodeClient(outbound.frame());
        assertEquals(Optional.of(claim), decoded.program(), "A program never silently becomes the human caller");
    }

    @Test void ownChannelReferencesCannotPiggybackAnotherProgramOrHumanAuthority() {
        var runtime = new SFMClientInboxRuntime(new SFMClientInbox());
        runtime.observeSessionIdentity(new Object(), new Object(), PLAYER, DIM, true);
        var address = new SFMClientInboxAddress(PLAYER, DIM, CHANNEL);
        List<ServerboundClientInboxSubscriptionPacket> sent = new ArrayList<>();
        Object alice = new Object(), bob = new Object();
        var first = runtime.subscribe(address, alice, sent::add).orElseThrow();
        var same = runtime.subscribe(address, alice, sent::add).orElseThrow();
        assertTrue(runtime.subscribe(address, bob, sent::add).isEmpty());
        assertTrue(runtime.subscribe(address, sent::add).isEmpty());
        assertEquals(1, sent.size());
        first.close();
        assertEquals(1, sent.size());
        same.close();
        assertEquals(2, sent.size());
        assertFalse(sent.get(1).subscribe());
        runtime.subscribe(address, bob, sent::add).orElseThrow().close();
        assertEquals(4, sent.size());
    }

    @Test void staleLeaseCannotReadReplacementOwnersStreamAfterSameWorldSessionRotation() {
        var runtime = new SFMClientInboxRuntime(new SFMClientInbox());
        Object world = new Object(), alice = new Object(), bob = new Object();
        var address = new SFMClientInboxAddress(PLAYER, DIM, CHANNEL);
        runtime.observeSessionIdentity(new Object(), world, PLAYER, DIM, true);
        List<ServerboundClientInboxSubscriptionPacket> sent = new ArrayList<>();
        var oldAlice = runtime.subscribe(address, alice, sent::add).orElseThrow();
        runtime.observeSessionIdentity(new Object(), world, PLAYER, DIM, true);
        var newBob = runtime.subscribe(address, bob, sent::add).orElseThrow();
        assertTrue(runtime.receive(new ClientboundClientInboxValuePacket(runtime.session().orElseThrow(), address,
                SFMPacketValueEnvelope.fromValue(SFMValue.of("Bob's new stream")))));
        assertTrue(oldAlice.page(Optional.empty(), 10).isEmpty(), "Old A handle cannot piggyback B after nonce rotation");
        assertEquals(SFMValue.of("Bob's new stream"), newBob.page(Optional.empty(), 10).orElseThrow().entries().get(0).value());
        oldAlice.close();
        assertEquals(2, sent.size(), "Closing the stale handle cannot unsubscribe B's replacement stream");
        newBob.close();
        var newAlice = runtime.subscribe(address, alice, sent::add).orElseThrow();
        assertTrue(newAlice.page(Optional.empty(), 10).orElseThrow().entries().isEmpty());
        newAlice.close();
    }

    @Test void staleSameOwnerLeaseMustReacquireButPrivateLegacyReferencesStillShare() {
        var runtime = new SFMClientInboxRuntime(new SFMClientInbox());
        Object world = new Object(), alice = new Object();
        var address = new SFMClientInboxAddress(PLAYER, DIM, CHANNEL);
        runtime.observeSessionIdentity(new Object(), world, PLAYER, DIM, true);
        var oldAlice = runtime.subscribe(address, alice, ignored -> {}).orElseThrow();
        runtime.observeSessionIdentity(new Object(), world, PLAYER, DIM, true);
        var newAlice = runtime.subscribe(address, alice, ignored -> {}).orElseThrow();
        assertTrue(oldAlice.page(Optional.empty(), 1).isEmpty());
        assertTrue(newAlice.page(Optional.empty(), 1).isPresent());
        oldAlice.close();
        newAlice.close();
        var legacyFirst = runtime.subscribe(address, ignored -> {}).orElseThrow();
        var legacySecond = runtime.subscribe(address, ignored -> {}).orElseThrow();
        legacyFirst.close();
        assertTrue(legacyFirst.page(Optional.empty(), 1).isEmpty());
        assertTrue(legacySecond.page(Optional.empty(), 1).isPresent(), "Private legacy callers retain shared channel semantics");
        legacySecond.close();
    }
}
