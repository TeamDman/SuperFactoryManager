package ca.teamdman.sfm.client.terminal;

import java.util.Optional;

/** Bounded in-memory polling and Vox wait evidence for one terminal service. */
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
    private NativeFrameMetadata latest;

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

    public record Snapshot(
            long pollsStarted, long pollsCompleted, long pollsFailed,
            long pollsSkippedInFlight, long pollsSkippedUnavailable,
            long pollNanosTotal, long pollNanosMax, long voxWaits,
            long voxWaitNanosTotal, long voxWaitNanosMax, long voxWaitFailures,
            long voxWaitTimeouts, long staleFrames, long droppedFrames,
            long coalescedFrames, long acceptedFrames,
            Optional<NativeFrameMetadata> latest) {
        public Snapshot {
            latest = latest == null ? Optional.empty() : latest;
        }
    }

    public synchronized Snapshot snapshot() {
        return new Snapshot(pollsStarted, pollsCompleted, pollsFailed,
                pollsSkippedInFlight, pollsSkippedUnavailable, pollNanosTotal,
                pollNanosMax, voxWaits, voxWaitNanosTotal, voxWaitNanosMax,
                voxWaitFailures, voxWaitTimeouts, staleFrames, droppedFrames,
                coalescedFrames, acceptedFrames, Optional.ofNullable(latest));
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
