package ca.teamdman.sfm.client.raster;

import java.time.Duration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.LongSupplier;

/**
 * Bounded latest-wins live-raster storage, usable from transport and render
 * threads. Authorisation belongs to the caller that acquires a writer lease.
 * A lease is an opaque local capability; replacing a producer requires release
 * and acquisition, so its late completions cannot write through the new lease.
 *
 * <p>Only one full image is retained per display. Dirty frames are applied to
 * the latest accepted image, including frames not yet delivered. Delivery is
 * always a full image so dropping intermediate frames cannot lose a patch.
 * The renderer owns GPU allocation/cleanup and must release deliveries after
 * uploading: this inbox's memory limit covers retained CPU pixels only.
 */
public final class TouchDisplayRasterInbox {
    public static final long DEFAULT_RESIDENT_BYTES = 8L * 1024 * 1024;
    private static final long DEFAULT_TRANSFER_BYTES = 64L * 1024 * 1024;
    private static final int MAX_PROCESSED_FRAMES_PER_WINDOW = 4096;

    public record Display(UUID world, String dimension, int x, int y, int z) {
        public Display {
            Objects.requireNonNull(world, "world");
            if (dimension == null || dimension.isBlank() || dimension.length() > 256) {
                throw new IllegalArgumentException("A bounded dimension identifier is required");
            }
        }
    }

    public record Limits(
            int maxDisplays, long residentBytes, long ingressBytesPerWindow,
            long processingBytesPerWindow, long uploadBytesPerWindow, long windowNanos
    ) {
        public static final Limits DEFAULT = new Limits(
                128, DEFAULT_RESIDENT_BYTES, DEFAULT_TRANSFER_BYTES, DEFAULT_TRANSFER_BYTES * 2,
                DEFAULT_TRANSFER_BYTES, Duration.ofSeconds(1).toNanos()
        );

        public Limits {
            if (maxDisplays < 1 || maxDisplays > 128 || residentBytes < 4 || residentBytes > DEFAULT_RESIDENT_BYTES
                    || ingressBytesPerWindow < 4 || processingBytesPerWindow < 4
                    || uploadBytesPerWindow < 4 || windowNanos < 1) {
                throw new IllegalArgumentException("Invalid raster limits");
            }
        }
    }

    public static final class WriterLease {
        private final Display display;
        private final String writer;
        private final long generation;

        private WriterLease(Display display, String writer, long generation) {
            this.display = display;
            this.writer = writer;
            this.generation = generation;
        }

        public Display display() { return display; }
        public String writer() { return writer; }
        public long generation() { return generation; }
    }

    public enum OfferResult {
        ACCEPTED, ACCEPTED_SUPERSEDING, ACCEPTED_UNCHANGED,
        REJECTED_LEASE, REJECTED_FRAME, REJECTED_SEQUENCE, REJECTED_BASE,
        REJECTED_MEMORY_LIMIT, REJECTED_INGRESS_BUDGET, REJECTED_PROCESSING_BUDGET;

        public boolean accepted() {
            return this == ACCEPTED || this == ACCEPTED_SUPERSEDING || this == ACCEPTED_UNCHANGED;
        }
    }

    public record Delivery(long generation, long sequence, TouchDisplayRasterFrame.Image image) {}
    public record FrameInfo(long sequence, int width, int height, String sha256, boolean pending) {
        public int byteSize() { return width * height * 4; }
    }
    public record Snapshot(
            int displays, long residentBytes, long ingressBytes, long processingBytes,
            long uploadBytes, int pendingDisplays
    ) {}

    private static final class Entry {
        private final WriterLease lease;
        private TouchDisplayRasterFrame.Image image;
        private long sequence = -1;
        private String deliveredDigest;
        private boolean pending;

        private Entry(WriterLease lease) { this.lease = lease; }
    }

    private final Limits limits;
    private final LongSupplier clock;
    private final Map<Display, Entry> entries = new HashMap<>();
    private long generation;
    private long residentBytes;
    private long windowStart;
    private long ingressBytes;
    private long processingBytes;
    private int processedFrames;
    private long uploadBytes;

    public TouchDisplayRasterInbox() { this(Limits.DEFAULT, System::nanoTime); }

    public TouchDisplayRasterInbox(Limits limits, LongSupplier monotonicNanos) {
        this.limits = Objects.requireNonNull(limits, "limits");
        this.clock = Objects.requireNonNull(monotonicNanos, "monotonicNanos");
        windowStart = clock.getAsLong();
    }

    /** Competing writers fail rather than silently taking ownership. */
    public synchronized Optional<WriterLease> acquire(Display display, String writer) {
        Objects.requireNonNull(display, "display");
        if (writer == null || writer.isBlank() || writer.length() > 256) {
            throw new IllegalArgumentException("A bounded writer identity is required");
        }
        if (entries.containsKey(display) || entries.size() >= limits.maxDisplays() || generation == Long.MAX_VALUE) {
            return Optional.empty();
        }
        WriterLease lease = new WriterLease(display, writer, ++generation);
        entries.put(display, new Entry(lease));
        return Optional.of(lease);
    }

    public synchronized boolean isCurrent(WriterLease lease) { return current(lease) != null; }

    public synchronized boolean release(WriterLease lease) {
        Entry entry = current(lease);
        if (entry == null) return false;
        remove(entry);
        entries.remove(lease.display());
        return true;
    }

    public synchronized OfferResult offer(WriterLease lease, TouchDisplayRasterFrame frame) {
        Entry entry = current(lease);
        if (entry == null) return OfferResult.REJECTED_LEASE;
        if (frame == null) return OfferResult.REJECTED_FRAME;
        if (frame.sequence() <= entry.sequence) return OfferResult.REJECTED_SEQUENCE;
        if (!frame.full() && (frame.baseSequence() != entry.sequence || entry.image == null
                || frame.width() != entry.image.width() || frame.height() != entry.image.height())) {
            return OfferResult.REJECTED_BASE;
        }
        refreshWindow();
        if (frame.payloadBytes() > limits.ingressBytesPerWindow() - ingressBytes) {
            return OfferResult.REJECTED_INGRESS_BUDGET;
        }
        // Charge valid ingress before hashing/copying, including unchanged or
        // memory-rejected content. Releasing a lease cannot reset this budget.
        ingressBytes += frame.payloadBytes();
        // Small images have fixed hashing/allocation overhead that a byte-only
        // budget does not capture. Bound those operations as well.
        if (processedFrames >= MAX_PROCESSED_FRAMES_PER_WINDOW) return OfferResult.REJECTED_PROCESSING_BUDGET;
        processedFrames++;
        int oldBytes = entry.image == null ? 0 : entry.image.byteSize();
        if (frame.imageBytes() > limits.residentBytes() - (residentBytes - oldBytes)) {
            return OfferResult.REJECTED_MEMORY_LIMIT;
        }
        // A tiny dirty region still requires reconstructing/hashing its full
        // image. Charge that work separately from actual ingress bytes.
        if (frame.imageBytes() > limits.processingBytesPerWindow() - processingBytes) {
            return OfferResult.REJECTED_PROCESSING_BUDGET;
        }
        processingBytes += frame.imageBytes();

        TouchDisplayRasterFrame.Image image = frame.applyTo(entry.image);
        entry.sequence = frame.sequence();
        if (image.samePixels(entry.image)) return OfferResult.ACCEPTED_UNCHANGED;
        boolean superseded = entry.pending;
        residentBytes += image.byteSize() - oldBytes;
        entry.image = image;
        // A -> pending B -> A needs no upload when the renderer still has A.
        entry.pending = !image.sha256().equals(entry.deliveredDigest);
        if (!entry.pending) return OfferResult.ACCEPTED_UNCHANGED;
        return superseded ? OfferResult.ACCEPTED_SUPERSEDING : OfferResult.ACCEPTED;
    }

    /**
     * Takes the latest image when the full-upload byte budget allows it.
     * Budget exhaustion retains the latest pending image for a later call.
     * This acknowledges delivery to the renderer, not successful GPU upload;
     * call requestUpload after upload failure or texture eviction.
     */
    public synchronized Optional<Delivery> takeLatest(WriterLease lease) {
        Entry entry = current(lease);
        if (entry == null || !entry.pending) return Optional.empty();
        refreshWindow();
        if (entry.image.byteSize() > limits.uploadBytesPerWindow() - uploadBytes) return Optional.empty();
        uploadBytes += entry.image.byteSize();
        entry.pending = false;
        entry.deliveredDigest = entry.image.sha256();
        return Optional.of(new Delivery(lease.generation(), entry.sequence, entry.image));
    }

    public synchronized void requestUpload(WriterLease lease) {
        Entry entry = current(lease);
        if (entry == null) return;
        entry.deliveredDigest = null;
        entry.pending = entry.image != null;
    }

    /** Bounded metadata for texture admission; this does not consume a pending frame. */
    public synchronized Optional<FrameInfo> latestInfo(WriterLease lease) {
        Entry entry = current(lease);
        if (entry == null || entry.image == null) return Optional.empty();
        return Optional.of(new FrameInfo(entry.sequence, entry.image.width(), entry.image.height(),
                entry.image.sha256(), entry.pending));
    }

    public synchronized void unloadChunk(UUID world, String dimension, int chunkX, int chunkZ) {
        Iterator<Entry> iterator = entries.values().iterator();
        while (iterator.hasNext()) {
            Entry entry = iterator.next();
            Display display = entry.lease.display();
            if (display.world().equals(world) && display.dimension().equals(dimension)
                    && (display.x() >> 4) == chunkX && (display.z() >> 4) == chunkZ) {
                remove(entry);
                iterator.remove();
            }
        }
    }

    public synchronized void unloadWorld(UUID world) {
        Iterator<Entry> iterator = entries.values().iterator();
        while (iterator.hasNext()) {
            Entry entry = iterator.next();
            if (entry.lease.display().world().equals(world)) {
                remove(entry);
                iterator.remove();
            }
        }
    }

    /** Drops CPU state; old leases remain invalid even after acquiring again. */
    public synchronized void clear() {
        entries.clear();
        residentBytes = 0;
    }

    public synchronized Snapshot snapshot() {
        refreshWindow();
        int pending = 0;
        for (Entry entry : entries.values()) if (entry.pending) pending++;
        return new Snapshot(entries.size(), residentBytes, ingressBytes, processingBytes, uploadBytes, pending);
    }

    private Entry current(WriterLease lease) {
        if (lease == null) return null;
        Entry entry = entries.get(lease.display());
        return entry != null && entry.lease == lease ? entry : null;
    }

    private void remove(Entry entry) {
        if (entry.image != null) residentBytes -= entry.image.byteSize();
    }

    private void refreshWindow() {
        long now = clock.getAsLong();
        if (now - windowStart >= limits.windowNanos()) {
            windowStart = now;
            ingressBytes = 0;
            processingBytes = 0;
            processedFrames = 0;
            uploadBytes = 0;
        }
    }
}
