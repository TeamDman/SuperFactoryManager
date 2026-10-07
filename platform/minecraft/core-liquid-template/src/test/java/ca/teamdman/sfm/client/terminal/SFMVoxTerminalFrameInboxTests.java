package ca.teamdman.sfm.client.terminal;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SFMVoxTerminalFrameInboxTests {
    private static final long MAX_FRAME_BYTES = 1024;

    @Test
    void firstEventMustBeAFullResynchronization() {
        SFMVoxTerminalFrameInbox inbox = new SFMVoxTerminalFrameInbox(MAX_FRAME_BYTES);
        var subscription = inbox.begin("session-a");

        assertEquals(SFMVoxTerminalFrameInbox.OfferResult.REJECTED_AWAITING_FULL_RESYNC,
                inbox.offer(subscription, event("session-a", "connection-a", "epoch-a", 1, 1, false)));
        assertEquals(SFMVoxTerminalFrameInbox.State.AWAITING_FULL_RESYNC, inbox.snapshot().state());
        assertTrue(inbox.takeLatest().isEmpty());

        assertEquals(SFMVoxTerminalFrameInbox.OfferResult.ACCEPTED,
                inbox.offer(subscription, event("session-a", "connection-a", "epoch-a", 1, 1, true)));
        assertEquals(SFMVoxTerminalFrameInbox.State.LIVE, inbox.snapshot().state());
    }

    @Test
    void handoffKeepsOnlyTheNewestUnconsumedFrame() {
        SFMVoxTerminalFrameInbox inbox = new SFMVoxTerminalFrameInbox(MAX_FRAME_BYTES);
        var subscription = inbox.begin("session-a");

        assertEquals(SFMVoxTerminalFrameInbox.OfferResult.ACCEPTED,
                inbox.offer(subscription, event("session-a", "connection-a", "epoch-a", 1, 10, true)));
        assertEquals(SFMVoxTerminalFrameInbox.OfferResult.ACCEPTED_SUPERSEDING,
                inbox.offer(subscription, event("session-a", "connection-a", "epoch-a", 3, 12, false)));

        assertEquals(12, inbox.takeLatest().orElseThrow().sequence());
        assertTrue(inbox.takeLatest().isEmpty());
        assertEquals(2, inbox.snapshot().eventsAccepted());
        assertEquals(1, inbox.snapshot().eventsSuperseded());
        assertEquals(1, inbox.snapshot().framesDelivered());
    }

    @Test
    void terminalSequenceNeverRegressesAndFrameSequenceStrictlyAdvances() {
        SFMVoxTerminalFrameInbox inbox = new SFMVoxTerminalFrameInbox(MAX_FRAME_BYTES);
        var subscription = inbox.begin("session-a");
        inbox.offer(subscription, event("session-a", "connection-a", "epoch-a", 5, 8, true));
        inbox.takeLatest();

        assertEquals(SFMVoxTerminalFrameInbox.OfferResult.ACCEPTED,
                inbox.offer(subscription, event("session-a", "connection-a", "epoch-a", 5, 9, false)));
        inbox.takeLatest();
        assertEquals(SFMVoxTerminalFrameInbox.OfferResult.REJECTED_SEQUENCE,
                inbox.offer(subscription, event("session-a", "connection-a", "epoch-a", 6, 8, false)));
        assertEquals(SFMVoxTerminalFrameInbox.OfferResult.ACCEPTED,
                inbox.offer(subscription, event("session-a", "connection-a", "epoch-a", 7, 10, false)));
    }

    @Test
    void reconnectRejectsLateOldReceiverAndRequiresAnotherFullFrame() {
        SFMVoxTerminalFrameInbox inbox = new SFMVoxTerminalFrameInbox(MAX_FRAME_BYTES);
        var oldSubscription = inbox.begin("session-a");
        inbox.offer(oldSubscription, event("session-a", "connection-a", "epoch-a", 1, 1, true));

        var replacement = inbox.begin("session-b");
        assertEquals(SFMVoxTerminalFrameInbox.OfferResult.REJECTED_STALE_SUBSCRIPTION,
                inbox.offer(oldSubscription, event("session-a", "connection-a", "epoch-a", 2, 2, false)));
        inbox.disconnect(oldSubscription);
        assertEquals(SFMVoxTerminalFrameInbox.State.AWAITING_FULL_RESYNC, inbox.snapshot().state());
        assertEquals(SFMVoxTerminalFrameInbox.OfferResult.REJECTED_AWAITING_FULL_RESYNC,
                inbox.offer(replacement, event("session-b", "connection-b", "epoch-b", 1, 1, false)));
        assertEquals(SFMVoxTerminalFrameInbox.OfferResult.ACCEPTED,
                inbox.offer(replacement, event("session-b", "connection-b", "epoch-b", 1, 1, true)));
    }

    @Test
    void activeSubscriptionRejectsSessionAndEpochChanges() {
        SFMVoxTerminalFrameInbox inbox = new SFMVoxTerminalFrameInbox(MAX_FRAME_BYTES);
        var subscription = inbox.begin("session-a");
        inbox.offer(subscription, event("session-a", "connection-a", "epoch-a", 1, 1, true));
        inbox.takeLatest();

        assertEquals(SFMVoxTerminalFrameInbox.OfferResult.REJECTED_SESSION,
                inbox.offer(subscription, event("session-b", "connection-a", "epoch-a", 2, 2, false)));
        assertEquals(SFMVoxTerminalFrameInbox.OfferResult.REJECTED_EPOCH,
                inbox.offer(subscription, event("session-a", "old-connection", "epoch-a", 2, 2, false)));
        assertEquals(SFMVoxTerminalFrameInbox.OfferResult.REJECTED_EPOCH,
                inbox.offer(subscription, event("session-a", "connection-a", "old-epoch", 2, 2, false)));
    }

    @Test
    void malformedOrOutOfBoundsFramesNeverReachTheHandoff() {
        SFMVoxTerminalFrameInbox inbox = new SFMVoxTerminalFrameInbox(8);
        var subscription = inbox.begin("session-a");
        var valid = event("session-a", "connection-a", "epoch-a", 1, 1, true);
        var oversized = new SFMVoxTerminalFrameInbox.Event(
                valid.sessionId(), valid.connectionEpoch(), valid.sessionEpoch(),
                valid.terminalSequence(), valid.frameSequence(), valid.fullResync(), 7,
                valid.backendId(), valid.transportId(), valid.correlationId(),
                valid.snapshotSessionId(), valid.snapshotSequence(), valid.snapshotComplete(),
                valid.frame());

        assertEquals(SFMVoxTerminalFrameInbox.OfferResult.REJECTED_INVALID_FRAME,
                inbox.offer(subscription, oversized));
        assertTrue(inbox.takeLatest().isEmpty());

        var wrongSnapshotSequence = new SFMVoxTerminalFrameInbox.Event(
                valid.sessionId(), valid.connectionEpoch(), valid.sessionEpoch(),
                2, 2, true, MAX_FRAME_BYTES,
                valid.backendId(), valid.transportId(), valid.correlationId(),
                valid.snapshotSessionId(), 1, true, frame(2));
        SFMVoxTerminalFrameInbox sequenceInbox = new SFMVoxTerminalFrameInbox(MAX_FRAME_BYTES);
        var sequenceSubscription = sequenceInbox.begin("session-a");
        assertEquals(SFMVoxTerminalFrameInbox.OfferResult.REJECTED_INVALID_FRAME,
                sequenceInbox.offer(sequenceSubscription, wrongSnapshotSequence));

        SFMVoxTerminalFrameInbox incompleteInbox = new SFMVoxTerminalFrameInbox(MAX_FRAME_BYTES);
        var incompleteSubscription = incompleteInbox.begin("session-a");
        var incomplete = new SFMVoxTerminalFrameInbox.Event(
                valid.sessionId(), valid.connectionEpoch(), valid.sessionEpoch(),
                1, 1, true, MAX_FRAME_BYTES,
                valid.backendId(), valid.transportId(), valid.correlationId(),
                valid.snapshotSessionId(), 1, false, frame(1));
        assertEquals(SFMVoxTerminalFrameInbox.OfferResult.REJECTED_INVALID_FRAME,
                incompleteInbox.offer(incompleteSubscription, incomplete));
    }

    @Test
    void currentDisconnectClearsPendingStateButStaleDisconnectDoesNot() {
        SFMVoxTerminalFrameInbox inbox = new SFMVoxTerminalFrameInbox(MAX_FRAME_BYTES);
        var oldSubscription = inbox.begin("session-a");
        var current = inbox.begin("session-b");
        inbox.offer(current, event("session-b", "connection-b", "epoch-b", 1, 1, true));

        inbox.disconnect(oldSubscription);
        assertTrue(inbox.snapshot().framePending());
        inbox.disconnect(current);

        assertEquals(SFMVoxTerminalFrameInbox.State.DISCONNECTED, inbox.snapshot().state());
        assertFalse(inbox.snapshot().framePending());
        assertEquals(1, inbox.snapshot().disconnects());
    }

    @Test
    void separateSessionsHaveIndependentEpochsSequencesAndHandoffs() {
        SFMVoxTerminalFrameInbox left = new SFMVoxTerminalFrameInbox(MAX_FRAME_BYTES);
        SFMVoxTerminalFrameInbox right = new SFMVoxTerminalFrameInbox(MAX_FRAME_BYTES);
        var leftSubscription = left.begin("left");
        var rightSubscription = right.begin("right");

        assertTrue(left.offer(leftSubscription,
                event("left", "connection-left", "epoch-left", 20, 30, true)).accepted());
        assertTrue(right.offer(rightSubscription,
                event("right", "connection-right", "epoch-right", 2, 3, true)).accepted());

        assertEquals(30, left.takeLatest().orElseThrow().sequence());
        assertEquals(3, right.takeLatest().orElseThrow().sequence());
        assertEquals("epoch-left", left.snapshot().sessionEpoch());
        assertEquals("epoch-right", right.snapshot().sessionEpoch());
    }

    @Test
    void negotiatedBoundAndCorrelationMetadataAreValidated() {
        SFMVoxTerminalFrameInbox inbox = new SFMVoxTerminalFrameInbox(MAX_FRAME_BYTES);
        var subscription = inbox.begin("session-a");
        var first = event("session-a", "connection-a", "epoch-a", 1, 1, true);
        assertTrue(inbox.offer(subscription, first).accepted());
        inbox.takeLatest();

        var changedBound = new SFMVoxTerminalFrameInbox.Event(
                first.sessionId(), first.connectionEpoch(), first.sessionEpoch(),
                2, 2, false, MAX_FRAME_BYTES - 1,
                first.backendId(), first.transportId(), "test/2",
                first.snapshotSessionId(), 2, true, frame(2));
        assertEquals(SFMVoxTerminalFrameInbox.OfferResult.REJECTED_EPOCH,
                inbox.offer(subscription, changedBound));

        var blankCorrelation = new SFMVoxTerminalFrameInbox.Event(
                first.sessionId(), first.connectionEpoch(), first.sessionEpoch(),
                2, 2, false, MAX_FRAME_BYTES,
                first.backendId(), first.transportId(), "",
                first.snapshotSessionId(), 2, true, frame(2));
        assertEquals(SFMVoxTerminalFrameInbox.OfferResult.REJECTED_INVALID_FRAME,
                inbox.offer(subscription, blankCorrelation));
    }

    private static SFMVoxTerminalFrameInbox.Event event(
            String sessionId,
            String connectionEpoch,
            String sessionEpoch,
            long terminalSequence,
            long frameSequence,
            boolean fullResync) {
        return new SFMVoxTerminalFrameInbox.Event(
                sessionId,
                connectionEpoch,
                sessionEpoch,
                terminalSequence,
                frameSequence,
                fullResync,
                MAX_FRAME_BYTES,
                "rust.cpu.fontdue",
                "vox",
                "test/" + terminalSequence,
                sessionId,
                terminalSequence,
                true,
                frame(frameSequence));
    }

    private static SFMTerminalFrame frame(long sequence) {
        return new SFMTerminalFrame(
                sequence,
                true,
                true,
                new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A},
                null);
    }
}
