package ca.teamdman.sfm.client.terminal;

import java.util.Optional;

/** Bounded in-memory subscription, snapshot, and Vox wait evidence for one terminal service. */
public final class SFMVoxTerminalTelemetry {
    private long pollsStarted;
    private long pollsCompleted;
    private long pollsFailed;
    private long pollsSkippedInFlight;
    private long pollsSkippedUnavailable;
    private long pollNanosTotal;
    private long pollNanosMax;
    private long voxWaits;
    private long voxWaitNanosTotal;
    private long voxWaitNanosMax;
    private long voxWaitFailures;
    private long voxWaitTimeouts;
    private long staleFrames;
    private long droppedFrames;
    private long coalescedFrames;
    private long acceptedFrames;
    private long subscriptionsStarted;
    private long subscriptionsCompleted;
    private long subscriptionsFailed;
    private long subscriptionEventsReceived;
    private long subscriptionEventsAccepted;
    private long subscriptionEventsSuperseded;
    private long subscriptionEventsRejected;
    private long subscriptionChannelsClosed;
    private long snapshotCalls;
    private NativeFrameMetadata latest;
    private ProducerMetadata latestProducer;
    private SubscriptionEventMetadata latestSubscriptionEvent;

    public record NativeFrameMetadata(
            long sequence, long requestSequence, int logicalColumns, int logicalRows,
            int panelWidth, int panelHeight, int cellWidth, int cellHeight, int fontPixelSize,
            String backendId, String transportId, String correlationId,
            long rustTotalUs, int payloadBytes) {
        public NativeFrameMetadata {
            backendId = backendId == null ? "" : backendId;
            transportId = transportId == null ? "" : transportId;
            correlationId = correlationId == null ? "" : correlationId;
            payloadBytes = Math.max(0, payloadBytes);
        }
    }

    /** Latest Rust producer counters carried by a pushed frame. */
    public record ProducerMetadata(
            long mutations, long rendersStarted, long rendersCompleted,
            long preRenderCoalesced, long creditBlockedSends, long framesPushed,
            int pendingDepth, int pendingDepthMax, long mutationToSendUs,
            long creditWaitUs) {}

    /** Identity and ordering evidence for the latest accepted pushed event. */
    public record SubscriptionEventMetadata(
            String sessionId, String connectionEpoch, String sessionEpoch,
            long terminalSequence, long frameSequence, boolean fullResync,
            long maxFrameBytes, String correlationId) {}

    public record Snapshot(
            long pollsStarted, long pollsCompleted, long pollsFailed,
            long pollsSkippedInFlight, long pollsSkippedUnavailable,
            long pollNanosTotal, long pollNanosMax, long voxWaits,
            long voxWaitNanosTotal, long voxWaitNanosMax, long voxWaitFailures,
            long voxWaitTimeouts, long staleFrames, long droppedFrames,
            long coalescedFrames, long acceptedFrames, long subscriptionsStarted,
            long subscriptionsCompleted, long subscriptionsFailed,
            long subscriptionEventsReceived, long subscriptionEventsAccepted,
            long subscriptionEventsSuperseded, long subscriptionEventsRejected,
            long subscriptionChannelsClosed, long snapshotCalls,
            Optional<NativeFrameMetadata> latest,
            Optional<ProducerMetadata> latestProducer,
            Optional<SubscriptionEventMetadata> latestSubscriptionEvent) {
        public Snapshot {
            latest = latest == null ? Optional.empty() : latest;
            latestProducer = latestProducer == null ? Optional.empty() : latestProducer;
            latestSubscriptionEvent = latestSubscriptionEvent == null
                    ? Optional.empty()
                    : latestSubscriptionEvent;
        }
    }

    public synchronized Snapshot snapshot() {
        return new Snapshot(pollsStarted, pollsCompleted, pollsFailed,
                pollsSkippedInFlight, pollsSkippedUnavailable, pollNanosTotal,
                pollNanosMax, voxWaits, voxWaitNanosTotal, voxWaitNanosMax,
                voxWaitFailures, voxWaitTimeouts, staleFrames, droppedFrames,
                coalescedFrames, acceptedFrames, subscriptionsStarted,
                subscriptionsCompleted, subscriptionsFailed,
                subscriptionEventsReceived, subscriptionEventsAccepted,
                subscriptionEventsSuperseded, subscriptionEventsRejected,
                subscriptionChannelsClosed, snapshotCalls, Optional.ofNullable(latest),
                Optional.ofNullable(latestProducer), Optional.ofNullable(latestSubscriptionEvent));
    }

    synchronized void recordPollStarted() { pollsStarted = increment(pollsStarted); }
    synchronized void recordPollSkippedInFlight() { pollsSkippedInFlight = increment(pollsSkippedInFlight); }
    synchronized void recordPollSkippedUnavailable() { pollsSkippedUnavailable = increment(pollsSkippedUnavailable); }
    synchronized void recordPollCompleted(long nanos) { recordPollDuration(nanos); pollsCompleted = increment(pollsCompleted); }
    synchronized void recordPollFailed(long nanos) { recordPollDuration(nanos); pollsFailed = increment(pollsFailed); }

    synchronized void recordVoxWait(long nanos, boolean failure, boolean timeout) {
        long duration = nonNegative(nanos);
        voxWaits = increment(voxWaits);
        voxWaitNanosTotal = add(voxWaitNanosTotal, duration);
        voxWaitNanosMax = Math.max(voxWaitNanosMax, duration);
        if (failure) voxWaitFailures = increment(voxWaitFailures);
        if (timeout) voxWaitTimeouts = increment(voxWaitTimeouts);
    }

    synchronized void recordObserved(NativeFrameMetadata metadata) { latest = metadata; }
    synchronized void recordAccepted(boolean replaced) {
        acceptedFrames = increment(acceptedFrames);
        if (replaced) coalescedFrames = increment(coalescedFrames);
    }
    synchronized void recordStale() { staleFrames = increment(staleFrames); }
    synchronized void recordDropped() { droppedFrames = increment(droppedFrames); }
    synchronized void recordSubscriptionStarted() { subscriptionsStarted = increment(subscriptionsStarted); }
    synchronized void recordSubscriptionCompleted() { subscriptionsCompleted = increment(subscriptionsCompleted); }
    synchronized void recordSubscriptionFailed() { subscriptionsFailed = increment(subscriptionsFailed); }
    synchronized void recordSubscriptionEventReceived() {
        subscriptionEventsReceived = increment(subscriptionEventsReceived);
    }
    synchronized void recordSubscriptionEventAccepted(boolean superseded) {
        subscriptionEventsAccepted = increment(subscriptionEventsAccepted);
        if (superseded) subscriptionEventsSuperseded = increment(subscriptionEventsSuperseded);
    }
    synchronized void recordSubscriptionEventRejected() {
        subscriptionEventsRejected = increment(subscriptionEventsRejected);
    }
    synchronized void recordSubscriptionChannelClosed() {
        subscriptionChannelsClosed = increment(subscriptionChannelsClosed);
    }
    synchronized void recordSnapshotCall() { snapshotCalls = increment(snapshotCalls); }
    synchronized void recordProducer(ProducerMetadata producer) { latestProducer = producer; }
    synchronized void recordSubscriptionEvent(SubscriptionEventMetadata event) {
        latestSubscriptionEvent = event;
    }

    private void recordPollDuration(long nanos) {
        long duration = nonNegative(nanos);
        pollNanosTotal = add(pollNanosTotal, duration);
        pollNanosMax = Math.max(pollNanosMax, duration);
    }
    private static long increment(long value) { return value == Long.MAX_VALUE ? value : value + 1; }
    private static long add(long left, long right) {
        if (right <= 0 || left == Long.MAX_VALUE) return left;
        return Long.MAX_VALUE - left < right ? Long.MAX_VALUE : left + right;
    }
    private static long nonNegative(long value) { return Math.max(0, value); }
}
