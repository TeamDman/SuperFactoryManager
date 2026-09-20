package ca.teamdman.sfm.common.net;

import ca.teamdman.sfm.common.net.multiplayer.SFMMultiplayerPacketBudgets;
import ca.teamdman.sfm.common.net.multiplayer.SFMMultiplayerPacketPolicy;
import ca.teamdman.sfm.common.net.multiplayer.SFMMultiplayerPacketTransport;
import ca.teamdman.sfm.common.value.SFMValue;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static ca.teamdman.sfm.common.net.multiplayer.SFMMultiplayerPacketProtocol.*;
import static ca.teamdman.sfm.common.net.multiplayer.SFMMultiplayerPacketProtocol.Action.*;
import static ca.teamdman.sfm.common.net.multiplayer.SFMMultiplayerPacketProtocol.Status.*;
import static org.junit.jupiter.api.Assertions.*;

class SFMMultiplayerPacketTransportTests {
    private static final UUID PLAYER = new UUID(0, 1);
    private static final UUID OTHER = new UUID(0, 2);
    private static final ResourceLocation DIM = new ResourceLocation("minecraft", "overworld");
    private static final ResourceLocation NETHER = new ResourceLocation("minecraft", "the_nether");
    private static final InventoryScope TARGET = target(DIM, new BlockPos(1, 64, 2), Optional.of(Direction.NORTH));
    private static final InboxScope INBOX = new InboxScope(DIM, new ResourceLocation("sfm", "dashboard"));
    private static final ManagerAddress MANAGER = new ManagerAddress(DIM, new BlockPos(3, 64, 2));
    private static final ManagerAddress PUBLISHER = new ManagerAddress(DIM, new BlockPos(5, 64, 2));
    private static final DeliveryScope DELIVERY = new DeliveryScope(PUBLISHER, INBOX);
    private static final ProgramClaim PROGRAM = new ProgramClaim(MANAGER, new UUID(0, 3), 7, "a".repeat(64), "b".repeat(64));

    @Test
    void negotiationIsExplicitVersionedAndDefaultDenyBeforePayloadDecode() {
        Fixture f = new Fixture();
        var c = f.open(PLAYER);
        assertEquals(VERSION, c.offer().protocolVersion());
        assertEquals(MAX_FRAME_BYTES, c.offer().maximumFrameBytes());
        assertEquals(NOT_NEGOTIATED, f.insert(c, Optional.empty()).status());
        assertEquals(UNSUPPORTED_PROTOCOL, f.transport.negotiate(c, VERSION + 1, c.session()));
        assertEquals(STALE_SESSION, f.transport.negotiate(c, VERSION, UUID.randomUUID()));
        assertEquals(NEGOTIATED, f.transport.negotiate(c, VERSION, c.session()));
        // The unnegotiated request did not consume a sequence.
        f.sequences.put(c, 0L);
        assertEquals(AUTHORITY_DENIED, f.insert(c, Optional.empty()).status());
        assertEquals(0, f.reads.get());
        assertEquals(0, f.insertions.get());
    }

    @Test
    void exactPlayerTargetDimensionPositionAndSideAreRequired() {
        Fixture f = new Fixture();
        var c = f.connect(PLAYER);
        f.grant(OTHER, PACKET_SEND, TARGET, Optional.empty());
        assertEquals(AUTHORITY_DENIED, f.insert(c, Optional.empty()).status());
        f.grant(PLAYER, PACKET_SEND, TARGET, Optional.empty());
        for (InventoryScope wrong : Set.of(
                target(NETHER, TARGET.target().position(), TARGET.target().side()),
                target(DIM, TARGET.target().position().above(), TARGET.target().side()),
                target(DIM, TARGET.target().position(), Optional.of(Direction.SOUTH)),
                target(DIM, TARGET.target().position(), Optional.empty()))) {
            assertEquals(AUTHORITY_DENIED, f.transport.insert(c, f.request(c), wrong, Optional.empty(), f.payload(), f.effect()).status());
        }
        assertEquals(0, f.reads.get());
        var ack = f.insert(c, Optional.empty());
        assertEquals(INSERTION_ATTEMPTED, ack.status());
        assertEquals(Optional.of(SFMPacketInventoryInserter.Result.INSERTED), ack.insertion());
        assertEquals(1, f.insertions.get());
    }

    @Test
    void orderedRequestsRejectDuplicatesGapsAndCrossConnectionSessionClaims() {
        Fixture f = new Fixture();
        var c = f.connect(PLAYER);
        var other = f.connect(OTHER);
        f.grant(PLAYER, PACKET_SEND, TARGET, Optional.empty());
        Request first = f.request(c);
        assertEquals(INSERTION_ATTEMPTED, f.transport.insert(c, first, TARGET, Optional.empty(), f.payload(), f.effect()).status());
        assertEquals(REPLAYED_REQUEST, f.transport.insert(c, first, TARGET, Optional.empty(), f.payload(), f.effect()).status());
        assertEquals(OUT_OF_ORDER, f.transport.insert(c, new Request(c.session(), 3, 128), TARGET, Optional.empty(), f.payload(), f.effect()).status());
        assertEquals(STALE_SESSION, f.transport.insert(other, first, TARGET, Optional.empty(), f.payload(), f.effect()).status());
        assertEquals(INSERTION_ATTEMPTED, f.insert(c, Optional.empty()).status());
        assertEquals(2, f.insertions.get());
        assertEquals(2, f.reads.get());
    }

    @Test
    void deniedAttemptConsumesItsSequenceButRepeatedNegotiationDoesNotResetIt() {
        Fixture f = new Fixture();
        var c = f.connect(PLAYER);
        Request denied = f.request(c);
        assertEquals(AUTHORITY_DENIED, f.transport.insert(c, denied, TARGET, Optional.empty(), f.payload(), f.effect()).status());
        f.grant(PLAYER, PACKET_SEND, TARGET, Optional.empty());
        assertEquals(NEGOTIATED, f.transport.negotiate(c, VERSION, c.session()));
        assertEquals(REPLAYED_REQUEST, f.transport.insert(c, denied, TARGET, Optional.empty(), f.payload(), f.effect()).status());
        assertEquals(INSERTION_ATTEMPTED, f.insert(c, Optional.empty()).status());
    }

    @Test
    void reconnectReplacesNonceAndClearsSubscriptionsWhileOldHandleCannotCloseNewOne() {
        Fixture f = new Fixture();
        var old = f.connect(PLAYER);
        f.grant(PLAYER, INBOX_SUBSCRIBE, INBOX, Optional.empty());
        f.grant(PLAYER, INBOX_DELIVER, DELIVERY, Optional.empty());
        assertEquals(SUBSCRIBED, f.subscribe(old, PLAYER, INBOX, Optional.empty()).status());
        var current = f.connect(PLAYER);
        assertNotEquals(old.session(), current.session());
        assertEquals(0, f.transport.diagnostics().subscriptions());
        f.transport.close(old);
        assertEquals(1, f.transport.diagnostics().connections());
        assertEquals(STALE_SESSION, f.insert(old, Optional.empty()).status());
        assertEquals(STALE_SESSION, f.transport.publish(current, old.session(), DELIVERY, 128, f.payload(), message -> true));
        assertEquals(NOT_SUBSCRIBED, f.publish(current, DELIVERY, message -> true));
        assertEquals(SUBSCRIBED, f.subscribe(current, PLAYER, INBOX, Optional.empty()).status());
        assertEquals(DELIVERED_TO_TRANSPORT, f.publish(current, DELIVERY, message -> true));
    }

    @Test
    void sameAuthenticatedConnectionDoesNotRotateAndCannotChangePlayer() {
        Fixture f = new Fixture();
        Object actualConnection = new Object();
        var c = f.transport.openConnection(PLAYER, actualConnection).orElseThrow();
        assertSame(c, f.transport.openConnection(PLAYER, actualConnection).orElseThrow());
        assertThrows(IllegalArgumentException.class, () -> f.transport.openConnection(OTHER, actualConnection));
        Fixture foreign = new Fixture();
        assertEquals(STALE_SESSION, foreign.transport.negotiate(c, VERSION, c.session()));
    }

    @Test
    void unacknowledgedOfferExpiresAndCannotBeRevivedByClockRollback() {
        Fixture f = new Fixture();
        var c = f.open(PLAYER);
        f.wall.addAndGet(SFMMultiplayerPacketTransport.NEGOTIATION_TIMEOUT_MILLIS);
        assertEquals(STALE_SESSION, f.transport.negotiate(c, VERSION, c.session()));
        f.wall.set(0);
        assertEquals(STALE_SESSION, f.transport.negotiate(c, VERSION, c.session()));
    }

    @Test
    void expiredHandleCanBeDetectedAndReofferedOnTheSameAuthenticatedConnection() {
        Fixture f = new Fixture();
        Object network = new Object();
        var old = f.transport.openConnection(PLAYER, network).orElseThrow();
        assertTrue(f.transport.isActive(old));
        assertFalse(f.transport.isNegotiated(old));
        f.wall.addAndGet(SFMMultiplayerPacketTransport.NEGOTIATION_TIMEOUT_MILLIS);
        assertEquals(STALE_SESSION, f.transport.negotiate(old, VERSION, old.session()));
        assertFalse(f.transport.isActive(old));
        var fresh = f.transport.openConnection(PLAYER, network).orElseThrow();
        assertNotEquals(old.session(), fresh.session());
        assertEquals(NEGOTIATED, f.transport.negotiate(fresh, VERSION, fresh.session()));
        assertTrue(f.transport.isNegotiated(fresh));
        f.transport.close(fresh);
        assertFalse(f.transport.isActive(fresh));
        assertFalse(f.transport.isNegotiated(fresh));
    }

    @Test
    void claimIsOnlyARestrictionAndCannotReplaceAnExactPlayerGrant() {
        Fixture f = new Fixture();
        var c = f.connect(PLAYER);
        f.observed(PROGRAM, new ProgramOperation(PACKET_SEND, TARGET));
        assertEquals(AUTHORITY_DENIED, f.insert(c, Optional.of(PROGRAM)).status());
        assertEquals(0, f.lookups.get(), "Do not resolve arbitrary claimed manager positions before ACL admission");
        f.grant(PLAYER, PACKET_SEND, TARGET, Optional.of(PROGRAM));
        assertEquals(AUTHORITY_DENIED, f.insert(c, Optional.empty()).status());
        assertEquals(INSERTION_ATTEMPTED, f.insert(c, Optional.of(PROGRAM)).status());
    }

    @Test
    void serverObservedManagerIncarnationRevisionSourceBindingsAndOperationsMustMatch() {
        Fixture f = new Fixture();
        var c = f.connect(PLAYER);
        f.grant(PLAYER, PACKET_SEND, TARGET, Optional.empty());
        assertEquals(PROGRAM_REJECTED, f.insert(c, Optional.of(PROGRAM)).status());
        for (ProgramClaim mismatch : Set.of(
                new ProgramClaim(MANAGER, UUID.randomUUID(), 7, PROGRAM.sourceSha256(), PROGRAM.bindingsSha256()),
                new ProgramClaim(MANAGER, PROGRAM.incarnation(), 8, PROGRAM.sourceSha256(), PROGRAM.bindingsSha256()),
                new ProgramClaim(MANAGER, PROGRAM.incarnation(), 7, "c".repeat(64), PROGRAM.bindingsSha256()),
                new ProgramClaim(MANAGER, PROGRAM.incarnation(), 7, PROGRAM.sourceSha256(), "d".repeat(64)))) {
            f.observed(mismatch, new ProgramOperation(PACKET_SEND, TARGET));
            assertEquals(PROGRAM_REJECTED, f.insert(c, Optional.of(PROGRAM)).status());
        }
        f.observed(PROGRAM, new ProgramOperation(INBOX_SUBSCRIBE, INBOX));
        assertEquals(PROGRAM_REJECTED, f.insert(c, Optional.of(PROGRAM)).status());
        f.observed(PROGRAM, new ProgramOperation(PACKET_SEND, TARGET));
        assertEquals(INSERTION_ATTEMPTED, f.insert(c, Optional.of(PROGRAM)).status());
        assertEquals(1, f.reads.get());
    }

    @Test
    void recipientIsDerivedFromConnectionAndSubscriptionDoesNotAuthorizePublishing() {
        Fixture f = new Fixture();
        var c = f.connect(PLAYER);
        f.grant(PLAYER, INBOX_SUBSCRIBE, INBOX, Optional.empty());
        assertEquals(RECIPIENT_REJECTED, f.subscribe(c, OTHER, INBOX, Optional.empty()).status());
        assertEquals(SUBSCRIBED, f.subscribe(c, PLAYER, INBOX, Optional.empty()).status());
        assertEquals(AUTHORITY_DENIED, f.publish(c, DELIVERY, message -> true));
        f.grant(PLAYER, INBOX_DELIVER, DELIVERY, Optional.empty());
        DeliveryScope impostor = new DeliveryScope(new ManagerAddress(DIM, PUBLISHER.position().above()), INBOX);
        assertEquals(AUTHORITY_DENIED, f.publish(c, impostor, message -> true));
        AtomicReference<SFMMultiplayerPacketTransport.Delivery> delivered = new AtomicReference<>();
        assertEquals(DELIVERED_TO_TRANSPORT, f.publish(c, DELIVERY, message -> { delivered.set(message); return true; }));
        assertEquals(PLAYER, delivered.get().recipient());
        assertEquals(c.session(), delivered.get().session());
        assertEquals(INBOX, delivered.get().inbox());
        assertEquals(SFMValue.of(1), delivered.get().value());
        assertEquals(1, f.reads.get());
    }

    @Test
    void grantsRevokeImmediatelyAndExpiredAuthorityDoesNotReturnAfterClockRollback() {
        Fixture f = new Fixture();
        var c = f.connect(PLAYER);
        var grant = new SFMMultiplayerPacketPolicy.Grant(UUID.randomUUID(), PLAYER, PACKET_SEND, TARGET,
                Optional.empty(), OptionalLong.of(1_001));
        f.policy.grant(grant);
        assertEquals(INSERTION_ATTEMPTED, f.insert(c, Optional.empty()).status());
        f.wall.set(1_001);
        assertEquals(AUTHORITY_DENIED, f.insert(c, Optional.empty()).status());
        f.wall.set(999);
        assertEquals(AUTHORITY_DENIED, f.insert(c, Optional.empty()).status());
        var permanent = f.grant(PLAYER, PACKET_SEND, TARGET, Optional.empty());
        assertEquals(INSERTION_ATTEMPTED, f.insert(c, Optional.empty()).status());
        assertTrue(f.policy.revoke(permanent.id()));
        assertEquals(AUTHORITY_DENIED, f.insert(c, Optional.empty()).status());
    }

    @Test
    void grantAndProgramChangesInvalidateExistingSubscriptionBeforeValueRead() {
        Fixture f = new Fixture();
        var c = f.connect(PLAYER);
        f.observed(PROGRAM, new ProgramOperation(INBOX_SUBSCRIBE, INBOX));
        var readGrant = f.grant(PLAYER, INBOX_SUBSCRIBE, INBOX, Optional.of(PROGRAM));
        var deliveryGrant = f.grant(PLAYER, INBOX_DELIVER, DELIVERY, Optional.empty());
        assertEquals(SUBSCRIBED, f.subscribe(c, PLAYER, INBOX, Optional.of(PROGRAM)).status());
        f.policy.revoke(deliveryGrant.id());
        assertEquals(AUTHORITY_DENIED, f.publish(c, DELIVERY, message -> true));
        f.grant(PLAYER, INBOX_DELIVER, DELIVERY, Optional.empty());
        f.programs.clear();
        assertEquals(PROGRAM_REJECTED, f.publish(c, DELIVERY, message -> true));
        assertEquals(NOT_SUBSCRIBED, f.publish(c, DELIVERY, message -> true));
        f.observed(PROGRAM, new ProgramOperation(INBOX_SUBSCRIBE, INBOX));
        assertEquals(SUBSCRIBED, f.subscribe(c, PLAYER, INBOX, Optional.of(PROGRAM)).status());
        f.policy.revoke(readGrant.id());
        assertEquals(AUTHORITY_DENIED, f.publish(c, DELIVERY, message -> true));
        assertEquals(0, f.reads.get());
    }

    @Test
    void unsubscribeIsOwnRecipientOnlyAndWorksAfterRevocation() {
        Fixture f = new Fixture();
        var c = f.connect(PLAYER);
        var grant = f.grant(PLAYER, INBOX_SUBSCRIBE, INBOX, Optional.empty());
        assertEquals(SUBSCRIBED, f.subscribe(c, PLAYER, INBOX, Optional.empty()).status());
        assertEquals(RECIPIENT_REJECTED, f.transport.unsubscribe(c, f.request(c), OTHER, INBOX).status());
        assertEquals(1, f.transport.diagnostics().subscriptions());
        f.policy.revoke(grant.id());
        assertEquals(UNSUBSCRIBED, f.transport.unsubscribe(c, f.request(c), PLAYER, INBOX).status());
        assertEquals(0, f.transport.diagnostics().subscriptions());
    }

    @Test
    void subscriptionSnapshotLetsAdapterDiscardEpochsDuringRevocationChurn() {
        Fixture f = new Fixture();
        var c = f.connect(PLAYER);
        var epochs = new HashMap<InboxScope, UUID>();
        for (int i = 0; i <= SFMMultiplayerPacketTransport.MAX_SUBSCRIPTIONS; i++) {
            f.tick.addAndGet(20);
            var inbox = new InboxScope(DIM, new ResourceLocation("sfm", "churn_" + i));
            var grant = f.grant(PLAYER, INBOX_SUBSCRIBE, inbox, Optional.empty());
            assertEquals(SUBSCRIBED, f.subscribe(c, PLAYER, inbox, Optional.empty()).status());
            epochs.put(inbox, UUID.randomUUID());
            f.policy.revoke(grant.id());
            assertEquals(AUTHORITY_DENIED, f.publish(c, new DeliveryScope(PUBLISHER, inbox), value -> true));
            epochs.keySet().retainAll(f.transport.subscribedInboxes(c));
            assertTrue(epochs.isEmpty());
        }
        assertThrows(UnsupportedOperationException.class, () -> f.transport.subscribedInboxes(c).add(INBOX));
    }

    @Test
    void oversizedTruncatedOrUnsupportedPayloadDoesNotAllocateDecodeOrResolveProgram() {
        Fixture f = new Fixture();
        var c = f.connect(PLAYER);
        f.grant(PLAYER, PACKET_SEND, TARGET, Optional.empty());
        for (int size : new int[]{-1, 0, 3_073, Integer.MAX_VALUE}) {
            var source = new PayloadSource(2, size, count -> { fail("Reader must not run"); return null; });
            assertEquals(FRAME_REJECTED, f.transport.insert(c, f.request(c), TARGET, Optional.of(PROGRAM), source, f.effect()).status());
        }
        var source = new PayloadSource(2, 129, count -> { fail("Truncated reader must not run"); return null; });
        assertEquals(FRAME_REJECTED, f.transport.insert(c, f.request(c), TARGET, Optional.empty(), source, f.effect()).status());
        var future = new PayloadSource(99, 1, count -> { fail("Future codec must not decode"); return null; });
        assertEquals(UNSUPPORTED_CODEC, f.transport.insert(c, f.request(c), TARGET, Optional.empty(), future, f.effect()).status());
        assertEquals(FRAME_REJECTED, f.transport.insert(c, new Request(c.session(), 100, MAX_FRAME_BYTES + 1),
                TARGET, Optional.empty(), f.payload(), f.effect()).status());
        assertEquals(0, f.lookups.get());
        assertEquals(0, f.insertions.get());
        assertThrows(IllegalArgumentException.class, () -> requireFrameSize(MAX_FRAME_BYTES + 1));
        assertThrows(IllegalArgumentException.class, () -> requireFrameSize(0));
        assertDoesNotThrow(() -> requireFrameSize(MAX_FRAME_BYTES));
    }

    @Test
    void strictCanonicalUtf8AndBothReadableCodecVersionsUseExistingValueCodec() {
        Fixture f = new Fixture();
        var c = f.connect(PLAYER);
        f.grant(PLAYER, PACKET_SEND, TARGET, Optional.empty());
        for (String invalid : new String[]{" 1", "{", "NaN", "1e999", "{\"x\":1,\"x\":2}"}) {
            assertEquals(PAYLOAD_REJECTED, f.transport.insert(c, f.request(c), TARGET, Optional.empty(), payload(2, invalid), f.effect()).status());
        }
        assertEquals(PAYLOAD_REJECTED, f.transport.insert(c, f.request(c), TARGET, Optional.empty(),
                new PayloadSource(2, 1, count -> new byte[]{(byte) 0xff}), f.effect()).status());
        assertEquals(PAYLOAD_REJECTED, f.transport.insert(c, f.request(c), TARGET, Optional.empty(),
                new PayloadSource(2, 1, count -> new byte[2]), f.effect()).status());
        assertEquals(INSERTION_ATTEMPTED, f.transport.insert(c, f.request(c), TARGET, Optional.empty(), payload(1, "1"), f.effect()).status());
        assertEquals(INSERTION_ATTEMPTED, f.transport.insert(c, f.request(c), TARGET, Optional.empty(), payload(2, "0.25"), f.effect()).status());
        assertEquals(2, f.insertions.get());
    }

    @Test
    void payloadReaderCannotRaceRevocationOrReconnectIntoAnEffect() {
        Fixture f = new Fixture();
        var c = f.connect(PLAYER);
        var grant = f.grant(PLAYER, PACKET_SEND, TARGET, Optional.empty());
        var revoking = new PayloadSource(2, 1, count -> { f.policy.revoke(grant.id()); return new byte[]{'1'}; });
        assertEquals(AUTHORITY_DENIED, f.transport.insert(c, f.request(c), TARGET, Optional.empty(), revoking, f.effect()).status());
        f.grant(PLAYER, PACKET_SEND, TARGET, Optional.empty());
        var reconnecting = new PayloadSource(2, 1, count -> { f.connect(PLAYER); return new byte[]{'1'}; });
        assertEquals(STALE_SESSION, f.transport.insert(c, f.request(c), TARGET, Optional.empty(), reconnecting, f.effect()).status());
        assertEquals(0, f.insertions.get());
    }

    @Test
    void insertionAcknowledgementMeansAttemptOnlyIncludingFullInventoryOrHandlerFailure() {
        Fixture f = new Fixture();
        var c = f.connect(PLAYER);
        f.grant(PLAYER, PACKET_SEND, TARGET, Optional.empty());
        var rejected = f.transport.insert(c, f.request(c), TARGET, Optional.empty(), f.payload(),
                (target, value) -> SFMPacketInventoryInserter.Result.INVENTORY_REJECTED);
        assertEquals(INSERTION_ATTEMPTED, rejected.status());
        assertEquals(Optional.of(SFMPacketInventoryInserter.Result.INVENTORY_REJECTED), rejected.insertion());
        var thrown = f.transport.insert(c, f.request(c), TARGET, Optional.empty(), f.payload(),
                (target, value) -> { throw new IllegalStateException("handler failed"); });
        assertEquals(Optional.of(SFMPacketInventoryInserter.Result.ITEM_HANDLER_ERROR), thrown.insertion());
        assertThrows(IllegalArgumentException.class, () -> new Acknowledgement(c.session(), 1, SUBSCRIBED,
                Optional.of(SFMPacketInventoryInserter.Result.INSERTED)));
        assertThrows(IllegalArgumentException.class, () -> new Acknowledgement(c.session(), 1, INSERTION_ATTEMPTED, Optional.empty()));
    }

    @Test
    void deliveryFailureNeverClaimsClientReceiptAndDiagnosticsContainOnlyBoundedCounts() {
        Fixture f = new Fixture();
        var c = f.connect(PLAYER);
        f.grant(PLAYER, INBOX_SUBSCRIBE, INBOX, Optional.empty());
        f.grant(PLAYER, INBOX_DELIVER, DELIVERY, Optional.empty());
        f.subscribe(c, PLAYER, INBOX, Optional.empty());
        assertEquals(DELIVERY_FAILED, f.publish(c, DELIVERY, message -> false));
        assertEquals(DELIVERY_FAILED, f.publish(c, DELIVERY, message -> { throw new IllegalStateException(); }));
        assertEquals(2L, f.transport.diagnostics().outcomes().get(DELIVERY_FAILED));
        assertThrows(UnsupportedOperationException.class, () -> f.transport.diagnostics().outcomes().clear());
    }

    @Test
    void reconnectAndMalformedHeaderAttemptsCannotResetPlayerQuota() {
        Fixture f = new Fixture(limits(100, 4, 100, 100, 100));
        var old = f.connect(PLAYER); // First attempt is negotiation.
        assertEquals(FRAME_REJECTED, f.transport.rejectMalformedFrame(old, 128));
        assertEquals(AUTHORITY_DENIED, f.insert(old, Optional.empty()).status());
        var current = f.connect(PLAYER); // Fourth attempt is negotiation, not a quota reset.
        assertEquals(RATE_LIMITED, f.insert(current, Optional.empty()).status());
        f.transport.close(current);
        var latest = f.open(PLAYER);
        assertEquals(RATE_LIMITED, f.transport.negotiate(latest, VERSION, latest.session()));
        f.tick.addAndGet(20);
        assertEquals(NEGOTIATED, f.transport.negotiate(latest, VERSION, latest.session()));
        f.sequences.put(latest, 0L);
        assertEquals(AUTHORITY_DENIED, f.insert(latest, Optional.empty()).status());
    }

    @Test
    void globalQuotaSurvivesPlayerIdentityChurnAndClockRollback() {
        var budgets = new SFMMultiplayerPacketBudgets(limits(2, 100, 100, 100, 100));
        assertTrue(budgets.reserveAttempt(PLAYER, 100, 1));
        assertTrue(budgets.reserveAttempt(OTHER, 100, 1));
        assertFalse(budgets.reserveAttempt(UUID.randomUUID(), 100, 1));
        assertFalse(budgets.reserveAttempt(UUID.randomUUID(), 0, 1));
        assertTrue(budgets.reserveAttempt(UUID.randomUUID(), 120, 1));
    }

    @Test
    void bothAttemptAndActionRateLimitsConsumeValidSequenceWithoutRetryingTheEffect() {
        for (var budget : java.util.List.of(limits(100, 2, 100, 100, 100), limits(100, 100, 1, 100, 100))) {
            Fixture f = new Fixture(budget);
            var connection = f.connect(PLAYER);
            f.grant(PLAYER, PACKET_SEND, TARGET, Optional.empty());
            assertEquals(INSERTION_ATTEMPTED, f.insert(connection, Optional.empty()).status());
            Request limited = f.request(connection);
            assertEquals(RATE_LIMITED, f.transport.insert(connection, limited, TARGET, Optional.empty(), f.payload(), f.effect()).status());
            assertEquals(1, f.reads.get());
            assertEquals(1, f.insertions.get());
            f.tick.addAndGet(20);
            assertEquals(INSERTION_ATTEMPTED, f.insert(connection, Optional.empty()).status());
            assertEquals(2, f.insertions.get());
            assertEquals(REPLAYED_REQUEST, f.transport.insert(connection, limited, TARGET, Optional.empty(), f.payload(), f.effect()).status());
        }
    }

    @Test
    void byteAndOperationBudgetsAreIndependentAndMultiKeyReservationIsAtomic() {
        var roomy = new SFMMultiplayerPacketBudgets.Limit(10, 10_000);
        var budget = new SFMMultiplayerPacketBudgets(new SFMMultiplayerPacketBudgets.Limits(20, 20,
                roomy, new SFMMultiplayerPacketBudgets.Limit(10, 10), roomy, roomy, roomy));
        assertFalse(budget.reserveAttempt(PLAYER, 0, 11));
        assertTrue(budget.reserveAttempt(PLAYER, 0, 6));
        assertFalse(budget.reserveAttempt(PLAYER, 0, 5));
        assertTrue(budget.reserveAttempt(PLAYER, 0, 4));
        assertFalse(budget.reserveAttempt(PLAYER, 0, 1));
        assertTrue(budget.reserveAttempt(OTHER, 0, 10));
        assertFalse(budget.reserveAttempt(OTHER, 0, -1));
    }

    @Test
    void distinctChannelsAreIndependentButShareGlobalPlayerActionAndProgramCaps() {
        var budgets = new SFMMultiplayerPacketBudgets(limits(100, 100, 3, 2, 1));
        InboxScope second = new InboxScope(DIM, new ResourceLocation("sfm", "second"));
        assertTrue(budgets.reserveOperation(PLAYER, INBOX_DELIVER, Optional.of(MANAGER), Optional.of(INBOX), 0, 1));
        assertFalse(budgets.reserveOperation(PLAYER, INBOX_DELIVER, Optional.of(MANAGER), Optional.of(INBOX), 0, 1));
        assertTrue(budgets.reserveOperation(PLAYER, INBOX_DELIVER, Optional.of(MANAGER), Optional.of(second), 0, 1));
        // Changing the action does not replenish the same manager's cross-action cap.
        assertFalse(budgets.reserveOperation(PLAYER, PACKET_SEND, Optional.of(MANAGER), Optional.empty(), 0, 1));
        assertTrue(budgets.reserveOperation(PLAYER, INBOX_DELIVER, Optional.of(PUBLISHER), Optional.empty(), 0, 1));
        assertFalse(budgets.reserveOperation(PLAYER, INBOX_DELIVER, Optional.empty(), Optional.empty(), 0, 1));
        assertTrue(budgets.reserveOperation(OTHER, INBOX_DELIVER, Optional.of(MANAGER), Optional.of(INBOX), 0, 1));
    }

    @Test
    void budgetKeyCapacityCannotEvictAnActiveQuotaAndOnlyNewWindowsReclaim() {
        var roomy = new SFMMultiplayerPacketBudgets.Limit(1, 100);
        var budgets = new SFMMultiplayerPacketBudgets(new SFMMultiplayerPacketBudgets.Limits(20, 2,
                new SFMMultiplayerPacketBudgets.Limit(100, 1000), roomy, roomy, roomy, roomy));
        assertTrue(budgets.reserveAttempt(PLAYER, 0, 1));
        assertFalse(budgets.reserveAttempt(OTHER, 0, 1));
        assertFalse(budgets.reserveAttempt(PLAYER, 0, 1));
        assertTrue(budgets.reserveAttempt(OTHER, 20, 1));
    }

    @Test
    void subscriptionAndConnectionCountsAreBoundedWithoutEvictingOtherOwners() {
        Fixture f = new Fixture();
        var c = f.connect(PLAYER);
        for (int i = 0; i < SFMMultiplayerPacketTransport.MAX_SUBSCRIPTIONS; i++) {
            InboxScope inbox = new InboxScope(DIM, new ResourceLocation("sfm", "channel_" + i));
            f.grant(PLAYER, INBOX_SUBSCRIBE, inbox, Optional.empty());
            assertEquals(SUBSCRIBED, f.subscribe(c, PLAYER, inbox, Optional.empty()).status());
        }
        f.grant(PLAYER, INBOX_SUBSCRIBE, INBOX, Optional.empty());
        assertEquals(SUBSCRIPTION_CAPACITY, f.subscribe(c, PLAYER, INBOX, Optional.empty()).status());
        for (int i = 1; i < SFMMultiplayerPacketTransport.MAX_CONNECTIONS; i++) f.open(new UUID(1, i));
        assertTrue(f.transport.openConnection(OTHER, new Object()).isEmpty());
        assertEquals(SFMMultiplayerPacketTransport.MAX_CONNECTIONS, f.transport.diagnostics().connections());
        assertTrue(f.transport.openConnection(PLAYER, new Object()).isPresent(), "Reconnect replaces only its own handle");
        assertEquals(0, f.transport.diagnostics().subscriptions());
    }

    @Test
    void grantScopeAndProgramManifestCollectionsAreValidatedAndImmutable() {
        assertThrows(IllegalArgumentException.class, () -> new SFMMultiplayerPacketPolicy.Grant(UUID.randomUUID(),
                PLAYER, PACKET_SEND, INBOX, Optional.empty(), OptionalLong.empty()));
        assertThrows(IllegalArgumentException.class, () -> new ProgramOperation(PACKET_SEND, INBOX));
        assertThrows(IllegalArgumentException.class, () -> new DeliveryScope(new ManagerAddress(NETHER, BlockPos.ZERO), INBOX));
        assertThrows(IllegalArgumentException.class, () -> new ProgramClaim(MANAGER, UUID.randomUUID(), 1, "bad", "b".repeat(64)));
        var observed = new ObservedProgram(PROGRAM, Set.of(new ProgramOperation(PACKET_SEND, TARGET)));
        assertThrows(UnsupportedOperationException.class, () -> observed.operations().clear());
        var policy = new SFMMultiplayerPacketPolicy();
        for (int i = 0; i < SFMMultiplayerPacketPolicy.MAX_GRANTS; i++) {
            policy.grant(new SFMMultiplayerPacketPolicy.Grant(new UUID(0, i), PLAYER, PACKET_SEND, TARGET,
                    Optional.empty(), OptionalLong.empty()));
        }
        assertThrows(IllegalStateException.class, () -> policy.grant(new SFMMultiplayerPacketPolicy.Grant(UUID.randomUUID(),
                PLAYER, PACKET_SEND, TARGET, Optional.empty(), OptionalLong.empty())));
        assertThrows(UnsupportedOperationException.class, () -> policy.snapshot().clear());
    }

    private static InventoryScope target(ResourceLocation dimension, BlockPos position, Optional<Direction> side) {
        return new InventoryScope(new SFMPacketInventoryAddress(dimension, position, side));
    }

    private static PayloadSource payload(int codec, String json) {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        return new PayloadSource(codec, bytes.length, count -> bytes.clone());
    }

    private static SFMMultiplayerPacketBudgets.Limits limits(int global, int player, int action, int program, int channel) {
        return new SFMMultiplayerPacketBudgets.Limits(20, 4096,
                new SFMMultiplayerPacketBudgets.Limit(global, 1024 * 1024),
                new SFMMultiplayerPacketBudgets.Limit(player, 128 * 1024),
                new SFMMultiplayerPacketBudgets.Limit(action, 64 * 1024),
                new SFMMultiplayerPacketBudgets.Limit(program, 64 * 1024),
                new SFMMultiplayerPacketBudgets.Limit(channel, 32 * 1024));
    }

    private static final class Fixture {
        final AtomicLong tick = new AtomicLong(100);
        final AtomicLong wall = new AtomicLong(1000);
        final AtomicInteger reads = new AtomicInteger();
        final AtomicInteger insertions = new AtomicInteger();
        final AtomicInteger lookups = new AtomicInteger();
        final Map<ManagerAddress, ObservedProgram> programs = new HashMap<>();
        final Map<SFMMultiplayerPacketTransport.Connection, Long> sequences = new IdentityHashMap<>();
        final SFMMultiplayerPacketPolicy policy = new SFMMultiplayerPacketPolicy();
        final SFMMultiplayerPacketTransport transport;

        Fixture() { this(SFMMultiplayerPacketBudgets.Limits.defaults()); }
        Fixture(SFMMultiplayerPacketBudgets.Limits limits) {
            transport = new SFMMultiplayerPacketTransport(policy, new SFMMultiplayerPacketBudgets(limits),
                    (player, address) -> { lookups.incrementAndGet(); return Optional.ofNullable(programs.get(address)); },
                    tick::get, wall::get, new UUID(17, 29));
        }

        SFMMultiplayerPacketTransport.Connection open(UUID player) {
            return transport.openConnection(player, new Object()).orElseThrow();
        }
        SFMMultiplayerPacketTransport.Connection connect(UUID player) {
            var c = open(player);
            assertEquals(NEGOTIATED, transport.negotiate(c, VERSION, c.session()));
            return c;
        }
        Request request(SFMMultiplayerPacketTransport.Connection c) {
            return new Request(c.session(), sequences.merge(c, 1L, Long::sum), 128);
        }
        PayloadSource payload() {
            return new PayloadSource(2, 1, count -> { reads.incrementAndGet(); return new byte[]{'1'}; });
        }
        SFMMultiplayerPacketTransport.InsertionEffect effect() {
            return (target, value) -> { insertions.incrementAndGet(); return SFMPacketInventoryInserter.Result.INSERTED; };
        }
        Acknowledgement insert(SFMMultiplayerPacketTransport.Connection c, Optional<ProgramClaim> program) {
            return transport.insert(c, request(c), TARGET, program, payload(), effect());
        }
        Acknowledgement subscribe(SFMMultiplayerPacketTransport.Connection c, UUID player, InboxScope inbox, Optional<ProgramClaim> program) {
            return transport.subscribe(c, request(c), player, inbox, program);
        }
        Status publish(SFMMultiplayerPacketTransport.Connection c, DeliveryScope scope, SFMMultiplayerPacketTransport.DeliveryEffect effect) {
            return transport.publish(c, c.session(), scope, 128, payload(), effect);
        }
        SFMMultiplayerPacketPolicy.Grant grant(UUID player, Action action, Scope scope, Optional<ProgramClaim> program) {
            var grant = new SFMMultiplayerPacketPolicy.Grant(UUID.randomUUID(), player, action, scope, program, OptionalLong.empty());
            policy.grant(grant);
            return grant;
        }
        void observed(ProgramClaim claim, ProgramOperation... operations) {
            programs.put(claim.manager(), new ObservedProgram(claim, Set.of(operations)));
        }
    }
}
