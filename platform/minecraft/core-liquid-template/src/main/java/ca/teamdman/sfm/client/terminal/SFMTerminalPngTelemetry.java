package ca.teamdman.sfm.client.terminal;

/** Bounded in-memory timing and counter evidence for PNG presentation. */
public final class SFMTerminalPngTelemetry {
    private long renderCalls;
    private long renderSuccesses;
    private long renderNanosTotal;
    private long renderNanosMax;
    private long uploadAttempts;
    private long uploadFailures;
    private long uploadNanosTotal;
    private long uploadNanosMax;
    private long pngDecodeAttempts;
    private long pngDecodeNanosTotal;
    private long pngDecodeNanosMax;
    private long decodedImageAllocations;
    private long decodedImageCloses;
    private long encodedBufferAllocations;
    private long encodedBufferReuses;
    private long encodedBufferReplacements;
    private long encodedBufferCloses;
    private long encodedBufferCapacity;
    private long encodedBufferCapacityMax;
    private long dynamicTextureAllocations;
    private long dynamicTextureAllocationNanosTotal;
    private long dynamicTextureAllocationNanosMax;
    private long dynamicTextureReuses;
    private long dynamicTextureReplacements;
    private long dynamicTextureCloses;
    private long dynamicTextureRegistrations;
    private long dynamicTextureRegistrationNanosTotal;
    private long dynamicTextureRegistrationNanosMax;
    private long dynamicTextureUploads;
    private long dynamicTextureUploadNanosTotal;
    private long dynamicTextureUploadNanosMax;
    private long staleFrames;
    private long droppedFrames;
    private long coalescedFrames;
    private long framesPresented;
    private long sequencePresented = Long.MIN_VALUE;

    public record Snapshot(
            long renderCalls, long renderSuccesses, long renderNanosTotal, long renderNanosMax,
            long uploadAttempts, long uploadFailures, long uploadNanosTotal, long uploadNanosMax,
            long pngDecodeAttempts, long pngDecodeNanosTotal, long pngDecodeNanosMax,
            long decodedImageAllocations, long decodedImageCloses,
            long encodedBufferAllocations, long encodedBufferReuses, long encodedBufferReplacements,
            long encodedBufferCloses, long encodedBufferCapacity, long encodedBufferCapacityMax,
            long dynamicTextureAllocations, long dynamicTextureAllocationNanosTotal,
            long dynamicTextureAllocationNanosMax, long dynamicTextureReuses,
            long dynamicTextureReplacements, long dynamicTextureCloses, long dynamicTextureRegistrations,
            long dynamicTextureRegistrationNanosTotal, long dynamicTextureRegistrationNanosMax,
            long dynamicTextureUploads, long dynamicTextureUploadNanosTotal,
            long dynamicTextureUploadNanosMax,
            long staleFrames, long droppedFrames, long coalescedFrames, long framesPresented,
            long sequencePresented) {}

    public synchronized Snapshot snapshot() {
        return new Snapshot(renderCalls, renderSuccesses, renderNanosTotal, renderNanosMax,
                uploadAttempts, uploadFailures, uploadNanosTotal, uploadNanosMax,
                pngDecodeAttempts, pngDecodeNanosTotal, pngDecodeNanosMax,
                decodedImageAllocations, decodedImageCloses,
                encodedBufferAllocations, encodedBufferReuses, encodedBufferReplacements,
                encodedBufferCloses, encodedBufferCapacity, encodedBufferCapacityMax,
                dynamicTextureAllocations, dynamicTextureAllocationNanosTotal,
                dynamicTextureAllocationNanosMax, dynamicTextureReuses,
                dynamicTextureReplacements, dynamicTextureCloses, dynamicTextureRegistrations,
                dynamicTextureRegistrationNanosTotal, dynamicTextureRegistrationNanosMax,
                dynamicTextureUploads, dynamicTextureUploadNanosTotal, dynamicTextureUploadNanosMax,
                staleFrames, droppedFrames, coalescedFrames, framesPresented, sequencePresented);
    }

    synchronized void recordRender(long elapsedNanos, boolean success) {
        long duration = nonNegative(elapsedNanos);
        renderCalls = increment(renderCalls);
        if (success) renderSuccesses = increment(renderSuccesses);
        renderNanosTotal = add(renderNanosTotal, duration);
        renderNanosMax = Math.max(renderNanosMax, duration);
    }

    synchronized void recordUploadStarted() { uploadAttempts = increment(uploadAttempts); }

    synchronized void recordUploadFinished(long elapsedNanos) {
        long duration = nonNegative(elapsedNanos);
        uploadNanosTotal = add(uploadNanosTotal, duration);
        uploadNanosMax = Math.max(uploadNanosMax, duration);
    }

    synchronized void recordUploadFailure() { uploadFailures = increment(uploadFailures); }

    synchronized void recordPngDecode(long elapsedNanos) {
        long duration = nonNegative(elapsedNanos);
        pngDecodeAttempts = increment(pngDecodeAttempts);
        pngDecodeNanosTotal = add(pngDecodeNanosTotal, duration);
        pngDecodeNanosMax = Math.max(pngDecodeNanosMax, duration);
    }

    synchronized void recordDecodedImageAllocation() {
        decodedImageAllocations = increment(decodedImageAllocations);
    }

    synchronized void recordDecodedImageClose() {
        decodedImageCloses = increment(decodedImageCloses);
    }

    synchronized void recordEncodedBufferAllocation(int capacity) {
        encodedBufferAllocations = increment(encodedBufferAllocations);
        recordEncodedBufferCapacity(capacity);
    }

    synchronized void recordEncodedBufferReuse(int capacity) {
        encodedBufferReuses = increment(encodedBufferReuses);
        recordEncodedBufferCapacity(capacity);
    }

    synchronized void recordEncodedBufferReplacement(int capacity) {
        encodedBufferReplacements = increment(encodedBufferReplacements);
        recordEncodedBufferCapacity(capacity);
    }

    synchronized void recordEncodedBufferClose() {
        encodedBufferCloses = increment(encodedBufferCloses);
        encodedBufferCapacity = 0;
    }

    synchronized void recordDynamicTextureAllocation(long elapsedNanos) {
        long duration = nonNegative(elapsedNanos);
        dynamicTextureAllocations = increment(dynamicTextureAllocations);
        dynamicTextureAllocationNanosTotal = add(dynamicTextureAllocationNanosTotal, duration);
        dynamicTextureAllocationNanosMax = Math.max(dynamicTextureAllocationNanosMax, duration);
    }

    synchronized void recordDynamicTextureReuse() {
        dynamicTextureReuses = increment(dynamicTextureReuses);
    }

    synchronized void recordDynamicTextureReplacement() {
        dynamicTextureReplacements = increment(dynamicTextureReplacements);
    }

    synchronized void recordDynamicTextureClose() {
        dynamicTextureCloses = increment(dynamicTextureCloses);
    }

    synchronized void recordDynamicTextureRegistration(long elapsedNanos) {
        long duration = nonNegative(elapsedNanos);
        dynamicTextureRegistrations = increment(dynamicTextureRegistrations);
        dynamicTextureRegistrationNanosTotal = add(dynamicTextureRegistrationNanosTotal, duration);
        dynamicTextureRegistrationNanosMax = Math.max(dynamicTextureRegistrationNanosMax, duration);
    }

    synchronized void recordDynamicTextureUpload(long elapsedNanos) {
        long duration = nonNegative(elapsedNanos);
        dynamicTextureUploads = increment(dynamicTextureUploads);
        dynamicTextureUploadNanosTotal = add(dynamicTextureUploadNanosTotal, duration);
        dynamicTextureUploadNanosMax = Math.max(dynamicTextureUploadNanosMax, duration);
    }

    synchronized void recordStaleFrame() { staleFrames = increment(staleFrames); }
    synchronized void recordDroppedFrame() { droppedFrames = increment(droppedFrames); }
    synchronized void recordCoalescedFrame() { coalescedFrames = increment(coalescedFrames); }

    synchronized void recordPresented(long sequence) {
        framesPresented = increment(framesPresented);
        sequencePresented = sequence;
    }

    private void recordEncodedBufferCapacity(int capacity) {
        long boundedCapacity = Math.max(0, capacity);
        encodedBufferCapacity = boundedCapacity;
        encodedBufferCapacityMax = Math.max(encodedBufferCapacityMax, boundedCapacity);
    }

    private static long increment(long value) { return value == Long.MAX_VALUE ? value : value + 1; }

    private static long add(long left, long right) {
        if (right <= 0 || left == Long.MAX_VALUE) return left;
        return Long.MAX_VALUE - left < right ? Long.MAX_VALUE : left + right;
    }

    private static long nonNegative(long value) { return Math.max(0, value); }
}
