package ca.teamdman.sfm.client.terminal;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SFMVoxTerminalTelemetryTests {
    @Test
    void idleSubscriptionEvidenceStartsWithZeroPollsAndZeroSnapshots() {
        SFMVoxTerminalTelemetry.Snapshot snapshot = new SFMVoxTerminalTelemetry().snapshot();

        assertEquals(0, snapshot.pollsStarted());
        assertEquals(0, snapshot.pollsCompleted());
        assertEquals(0, snapshot.pollsFailed());
        assertEquals(0, snapshot.subscriptionsStarted());
        assertEquals(0, snapshot.subscriptionEventsReceived());
        assertEquals(0, snapshot.snapshotCalls());
        assertTrue(snapshot.latestProducer().isEmpty());
        assertTrue(snapshot.latestSubscriptionEvent().isEmpty());
    }

    @Test
    void subscriptionAndExplicitSnapshotCountersRemainDistinct() {
        SFMVoxTerminalTelemetry telemetry = new SFMVoxTerminalTelemetry();
        telemetry.recordSubscriptionStarted();
        telemetry.recordSubscriptionEventReceived();
        telemetry.recordSubscriptionEventAccepted(false);
        telemetry.recordSubscriptionEventReceived();
        telemetry.recordSubscriptionEventAccepted(true);
        telemetry.recordSubscriptionEventReceived();
        telemetry.recordSubscriptionEventRejected();
        telemetry.recordSubscriptionCompleted();
        telemetry.recordSubscriptionChannelClosed();
        telemetry.recordSnapshotCall();
        telemetry.recordProducer(new SFMVoxTerminalTelemetry.ProducerMetadata(
                10, 9, 8, 2, 1, 7, 1, 1, 300, 20));
        telemetry.recordSubscriptionEvent(new SFMVoxTerminalTelemetry.SubscriptionEventMetadata(
                "session-a", "connection-a", "epoch-a", 10, 8, true,
                1024, "test/10"));

        SFMVoxTerminalTelemetry.Snapshot snapshot = telemetry.snapshot();
        assertEquals(1, snapshot.subscriptionsStarted());
        assertEquals(1, snapshot.subscriptionsCompleted());
        assertEquals(0, snapshot.subscriptionsFailed());
        assertEquals(3, snapshot.subscriptionEventsReceived());
        assertEquals(2, snapshot.subscriptionEventsAccepted());
        assertEquals(1, snapshot.subscriptionEventsSuperseded());
        assertEquals(1, snapshot.subscriptionEventsRejected());
        assertEquals(1, snapshot.subscriptionChannelsClosed());
        assertEquals(1, snapshot.snapshotCalls());
        assertEquals(0, snapshot.pollsStarted());
        assertEquals(10, snapshot.latestProducer().orElseThrow().mutations());
        assertEquals("epoch-a", snapshot.latestSubscriptionEvent().orElseThrow().sessionEpoch());
    }
}
