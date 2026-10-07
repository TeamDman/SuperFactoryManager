package ca.teamdman.sfm.client.screen;

import com.sun.management.ThreadMXBean;

import java.lang.management.ManagementFactory;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Bounded, allocation-conscious EditorV3 stage and input-to-frame telemetry. */
public final class SFMDrawCanvasPerformanceTracker {
    private static final int SAMPLE_LIMIT = 240;

    public record Snapshot(
            long coldLoadNanos,
            long frames,
            long frameMedianNanos,
            long frameP95Nanos,
            long frameMaximumNanos,
            long inputToFrameMedianNanos,
            long inputToFrameP95Nanos,
            long inputToFrameMaximumNanos,
            long inputToFrameSamples,
            long latestInputToFrameNanos,
            long inputEvents,
            long inputApplications,
            long inputApplyMedianNanos,
            long inputApplyP95Nanos,
            long inputApplyMaximumNanos,
            boolean frameAllocationMeasurementAvailable,
            long frameAllocationSamples,
            long frameAllocatedMedianBytes,
            long frameAllocatedP95Bytes,
            long frameAllocatedMaximumBytes,
            long viewportRebuilds,
            long viewportCacheHits,
            long viewportResolveNanos,
            long glyphsVisited,
            long glyphsDrawn,
            long contextCaptures,
            long contextCaptureNanos,
            long syntaxWorkerSubmissions,
            long styleProjectionRebuilds,
            long styleProjectionNanos,
            long selectionGeometryRebuilds,
            long selectionGeometryCacheHits,
            long selectionGeometryNanos,
            long openTargetGeometryBuilds,
            long openTargetGeometryNanos
    ) {
    }

    private final ArrayDeque<Long> frameNanos = new ArrayDeque<>();
    private final ArrayDeque<Long> inputToFrameNanos = new ArrayDeque<>();
    private final ArrayDeque<Long> inputApplyNanos = new ArrayDeque<>();
    private final ArrayDeque<Long> frameAllocatedBytes = new ArrayDeque<>();
    private final ThreadMXBean allocationBean;
    private long coldLoadNanos;
    private long frames;
    private long viewportRebuilds;
    private long viewportCacheHits;
    private long viewportResolveNanos;
    private long glyphsVisited;
    private long glyphsDrawn;
    private long contextCaptures;
    private long contextCaptureNanos;
    private long syntaxWorkerSubmissions;
    private long inputEvents;
    private long inputToFrameSamples;
    private long latestInputToFrameNanos;
    private long inputApplications;
    private long styleProjectionRebuilds;
    private long styleProjectionNanos;
    private long selectionGeometryRebuilds;
    private long selectionGeometryCacheHits;
    private long selectionGeometryNanos;
    private long openTargetGeometryBuilds;
    private long openTargetGeometryNanos;
    private long frameAllocationSamples;
    private long pendingInputNanos = -1L;
    private long currentInputNanos = -1L;

    public SFMDrawCanvasPerformanceTracker() {
        this(allocationBean());
    }

    SFMDrawCanvasPerformanceTracker(ThreadMXBean allocationBean) {
        this.allocationBean = allocationBean;
    }

    public void coldLoad(long nanos) {
        coldLoadNanos = Math.max(0L, nanos);
    }

    /**
     * Starts an explicit warm interaction window while retaining the separately
     * measured cold-load cost. This prevents time spent with another tab or
     * palette visible from being misreported as EditorV3 input-to-frame latency.
     */
    public void beginWarmMeasurement() {
        frameNanos.clear();
        inputToFrameNanos.clear();
        inputApplyNanos.clear();
        frameAllocatedBytes.clear();
        frames = 0L;
        viewportRebuilds = 0L;
        viewportCacheHits = 0L;
        viewportResolveNanos = 0L;
        glyphsVisited = 0L;
        glyphsDrawn = 0L;
        contextCaptures = 0L;
        contextCaptureNanos = 0L;
        syntaxWorkerSubmissions = 0L;
        inputEvents = 0L;
        inputToFrameSamples = 0L;
        latestInputToFrameNanos = 0L;
        inputApplications = 0L;
        styleProjectionRebuilds = 0L;
        styleProjectionNanos = 0L;
        selectionGeometryRebuilds = 0L;
        selectionGeometryCacheHits = 0L;
        selectionGeometryNanos = 0L;
        openTargetGeometryBuilds = 0L;
        openTargetGeometryNanos = 0L;
        frameAllocationSamples = 0L;
        pendingInputNanos = -1L;
        currentInputNanos = -1L;
    }

    public void inputReceived(long nowNanos) {
        if (pendingInputNanos < 0L) pendingInputNanos = nowNanos;
        currentInputNanos = nowNanos;
        inputEvents++;
    }

    public void inputApplied(long nowNanos) {
        if (currentInputNanos < 0L) return;
        append(inputApplyNanos, Math.max(0L, nowNanos - currentInputNanos));
        currentInputNanos = -1L;
        inputApplications++;
    }

    public long allocationCheckpoint() {
        if (allocationBean == null) return -1L;
        try {
            return allocationBean.getThreadAllocatedBytes(Thread.currentThread().getId());
        } catch (RuntimeException | LinkageError unavailable) {
            return -1L;
        }
    }

    public void frame(
            long startedNanos,
            long completedNanos,
            SFMDrawCanvasDocumentIndex.VisibleSlice visible,
            boolean viewportRebuilt
    ) {
        frame(startedNanos, completedNanos, visible, viewportRebuilt, -1L);
    }

    public void frame(
            long startedNanos,
            long completedNanos,
            SFMDrawCanvasDocumentIndex.VisibleSlice visible,
            boolean viewportRebuilt,
            long allocatedBefore
    ) {
        long elapsed = Math.max(0L, completedNanos - startedNanos);
        append(frameNanos, elapsed);
        frames++;
        if (viewportRebuilt) viewportRebuilds++;
        else viewportCacheHits++;
        glyphsVisited += visible.glyphsVisited();
        glyphsDrawn += visible.glyphs().size();
        long allocatedAfter = allocationCheckpoint();
        if (allocatedBefore >= 0L && allocatedAfter >= allocatedBefore) {
            append(frameAllocatedBytes, allocatedAfter - allocatedBefore);
            frameAllocationSamples++;
        }
        if (pendingInputNanos >= 0L) {
            latestInputToFrameNanos = Math.max(0L, completedNanos - pendingInputNanos);
            append(inputToFrameNanos, latestInputToFrameNanos);
            inputToFrameSamples++;
            pendingInputNanos = -1L;
        }
    }

    public void contextCapture(long nanos) {
        contextCaptures++;
        contextCaptureNanos += Math.max(0L, nanos);
    }

    public void syntaxWorkerSubmitted() {
        syntaxWorkerSubmissions++;
    }

    public void viewportResolved(long nanos) {
        viewportResolveNanos += Math.max(0L, nanos);
    }

    public void styleProjection(long nanos) {
        styleProjectionRebuilds++;
        styleProjectionNanos += Math.max(0L, nanos);
    }

    public void selectionGeometry(boolean rebuilt, long nanos) {
        if (rebuilt) selectionGeometryRebuilds++;
        else selectionGeometryCacheHits++;
        selectionGeometryNanos += Math.max(0L, nanos);
    }

    public void openTargetGeometry(long nanos) {
        openTargetGeometryBuilds++;
        openTargetGeometryNanos += Math.max(0L, nanos);
    }

    public Snapshot snapshot() {
        Distribution frame = distribution(frameNanos);
        Distribution input = distribution(inputToFrameNanos);
        Distribution inputApply = distribution(inputApplyNanos);
        Distribution allocated = distribution(frameAllocatedBytes);
        return new Snapshot(
                coldLoadNanos,
                frames,
                frame.median(),
                frame.p95(),
                frame.maximum(),
                input.median(),
                input.p95(),
                input.maximum(),
                inputToFrameSamples,
                latestInputToFrameNanos,
                inputEvents,
                inputApplications,
                inputApply.median(),
                inputApply.p95(),
                inputApply.maximum(),
                allocationBean != null,
                frameAllocationSamples,
                allocated.median(),
                allocated.p95(),
                allocated.maximum(),
                viewportRebuilds,
                viewportCacheHits,
                viewportResolveNanos,
                glyphsVisited,
                glyphsDrawn,
                contextCaptures,
                contextCaptureNanos,
                syntaxWorkerSubmissions,
                styleProjectionRebuilds,
                styleProjectionNanos,
                selectionGeometryRebuilds,
                selectionGeometryCacheHits,
                selectionGeometryNanos,
                openTargetGeometryBuilds,
                openTargetGeometryNanos
        );
    }

    private static ThreadMXBean allocationBean() {
        try {
            if (!(ManagementFactory.getThreadMXBean() instanceof ThreadMXBean bean)) return null;
            if (!bean.isThreadAllocatedMemorySupported()) return null;
            if (!bean.isThreadAllocatedMemoryEnabled()) bean.setThreadAllocatedMemoryEnabled(true);
            return bean;
        } catch (RuntimeException | LinkageError unavailable) {
            return null;
        }
    }

    private static void append(ArrayDeque<Long> samples, long value) {
        samples.addLast(value);
        while (samples.size() > SAMPLE_LIMIT) samples.removeFirst();
    }

    private static Distribution distribution(ArrayDeque<Long> samples) {
        if (samples.isEmpty()) return new Distribution(0L, 0L, 0L);
        ArrayList<Long> ordered = new ArrayList<>(samples);
        ordered.sort(Comparator.naturalOrder());
        return new Distribution(
                percentile(ordered, 0.50D),
                percentile(ordered, 0.95D),
                ordered.get(ordered.size() - 1)
        );
    }

    private static long percentile(List<Long> ordered, double percentile) {
        int index = (int) Math.ceil(percentile * ordered.size()) - 1;
        return ordered.get(Math.max(0, Math.min(index, ordered.size() - 1)));
    }

    private record Distribution(long median, long p95, long maximum) {
    }
}
