package ca.teamdman.sfm.client.raster;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.LongSupplier;

/**
 * Render-thread GPU owner for live rasters. CPU residency belongs to the inbox;
 * GPU textures have a separate cap, including retired textures awaiting release
 * after the current render buffers have finished using them.
 */
public final class TouchDisplayRasterTextureCache<T> {
    public static final long MAX_GPU_BYTES = 8L * 1024 * 1024;
    public static final int MAX_TEXTURES = 128;
    private static final long FAILURE_RETRY_NANOS = Duration.ofSeconds(1).toNanos();

    public interface TextureAccess<T> {
        Optional<T> create(TouchDisplayRasterInbox.WriterLease lease, TouchDisplayRasterFrame.Image image);
        boolean update(T texture, TouchDisplayRasterFrame.Image image);
        void release(T texture);
    }

    public record TextureInfo(int width, int height, String sha256, long uploads) {}
    public record Snapshot(int textures, int retiredTextures, long gpuBytes, long uploads, long failures, long releases) {}
    private record Failure(String digest, long atNanos) {}

    private static final class Entry<T> {
        private final T texture;
        private final int width;
        private final int height;
        private String digest;
        private long uploads = 1;

        private Entry(T texture, TouchDisplayRasterFrame.Image image) {
            this.texture = texture;
            width = image.width();
            height = image.height();
            digest = image.sha256();
        }

        private int bytes() { return width * height * 4; }
    }

    private final TouchDisplayRasterInbox inbox;
    private final TextureAccess<T> access;
    private final long maxGpuBytes;
    private final LongSupplier clock;
    private final Map<TouchDisplayRasterInbox.WriterLease, Entry<T>> entries = new HashMap<>();
    private final Map<TouchDisplayRasterInbox.WriterLease, Failure> failures = new LinkedHashMap<>();
    private final List<Entry<T>> retired = new ArrayList<>();
    private long gpuBytes;
    private long uploads;
    private long failedUploads;
    private long releases;

    public TouchDisplayRasterTextureCache(TouchDisplayRasterInbox inbox, TextureAccess<T> access) {
        this(inbox, access, MAX_GPU_BYTES, System::nanoTime);
    }

    public TouchDisplayRasterTextureCache(
            TouchDisplayRasterInbox inbox, TextureAccess<T> access, long maxGpuBytes, LongSupplier monotonicNanos
    ) {
        this.inbox = Objects.requireNonNull(inbox, "inbox");
        this.access = Objects.requireNonNull(access, "access");
        this.clock = Objects.requireNonNull(monotonicNanos, "monotonicNanos");
        if (maxGpuBytes < 4 || maxGpuBytes > MAX_GPU_BYTES) throw new IllegalArgumentException("Invalid GPU byte limit");
        this.maxGpuBytes = maxGpuBytes;
    }

    public Optional<T> textureFor(TouchDisplayRasterInbox.WriterLease lease) {
        if (!inbox.isCurrent(lease)) {
            retire(lease);
            return Optional.empty();
        }
        var info = inbox.latestInfo(lease).orElse(null);
        if (info == null) return Optional.empty();
        Entry<T> entry = entries.get(lease);
        Failure failure = failures.get(lease);
        if (failure != null) {
            if (failure.digest().equals(info.sha256()) && clock.getAsLong() - failure.atNanos() < FAILURE_RETRY_NANOS) {
                return entry == null ? Optional.empty() : Optional.of(entry.texture);
            }
            failures.remove(lease);
            inbox.requestUpload(lease);
        }
        boolean allocationRequired = entry == null || entry.width != info.width() || entry.height != info.height();
        if (allocationRequired && (info.byteSize() > maxGpuBytes - gpuBytes
                || entries.size() + retired.size() >= MAX_TEXTURES)) {
            return entry == null ? Optional.empty() : Optional.of(entry.texture);
        }
        var delivery = inbox.takeLatest(lease).orElse(null);
        if (delivery == null) return entry == null ? Optional.empty() : Optional.of(entry.texture);

        // A transport can replace a frame between admission metadata and take.
        var image = delivery.image();
        allocationRequired = entry == null || entry.width != image.width() || entry.height != image.height();
        if (allocationRequired && (image.byteSize() > maxGpuBytes - gpuBytes
                || entries.size() + retired.size() >= MAX_TEXTURES)) {
            inbox.requestUpload(lease);
            return entry == null ? Optional.empty() : Optional.of(entry.texture);
        }
        if (!inbox.isCurrent(lease)) {
            retire(lease);
            return Optional.empty();
        }
        if (allocationRequired) {
            Optional<T> texture = access.create(lease, image);
            if (texture.isEmpty()) {
                failed(lease, image);
                return entry == null ? Optional.empty() : Optional.of(entry.texture);
            }
            if (entry != null) retired.add(entry);
            entry = new Entry<>(texture.get(), image);
            entries.put(lease, entry);
            gpuBytes += image.byteSize();
        } else {
            if (!access.update(entry.texture, image)) {
                // The GPU may have received only part of a failing upload; its
                // previous contents can no longer be treated as a valid frame.
                retire(lease);
                failed(lease, image);
                return Optional.empty();
            }
            entry.digest = image.sha256();
            entry.uploads++;
        }
        uploads++;
        return Optional.of(entry.texture);
    }

    /** Immediately stops presentation; actual deletion waits for maintain. */
    public void retire(TouchDisplayRasterInbox.WriterLease lease) {
        Entry<T> entry = entries.remove(lease);
        if (entry != null) retired.add(entry);
        failures.remove(lease);
    }

    /** Call after rendering, at a client tick boundary. */
    public void maintain() {
        Iterator<Map.Entry<TouchDisplayRasterInbox.WriterLease, Entry<T>>> iterator = entries.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (!inbox.isCurrent(entry.getKey())) {
                retired.add(entry.getValue());
                iterator.remove();
            }
        }
        for (Entry<T> entry : retired) {
            access.release(entry.texture);
            gpuBytes -= entry.bytes();
            releases++;
        }
        retired.clear();
        failures.keySet().removeIf(lease -> !inbox.isCurrent(lease));
    }

    /** Invalidates the GPU projection, keeping CPU frames eligible for reupload. */
    public void invalidateTextures() {
        for (var entry : entries.entrySet()) {
            retired.add(entry.getValue());
            inbox.requestUpload(entry.getKey());
        }
        failures.keySet().forEach(inbox::requestUpload);
        entries.clear();
        failures.clear();
    }

    public Optional<T> currentTexture(TouchDisplayRasterInbox.WriterLease lease) {
        Entry<T> entry = entries.get(lease);
        return entry == null || !inbox.isCurrent(lease) ? Optional.empty() : Optional.of(entry.texture);
    }

    public Optional<TextureInfo> info(TouchDisplayRasterInbox.WriterLease lease) {
        Entry<T> entry = entries.get(lease);
        return entry == null ? Optional.empty()
                : Optional.of(new TextureInfo(entry.width, entry.height, entry.digest, entry.uploads));
    }

    public Snapshot snapshot() {
        return new Snapshot(entries.size(), retired.size(), gpuBytes, uploads, failedUploads, releases);
    }

    private void failed(TouchDisplayRasterInbox.WriterLease lease, TouchDisplayRasterFrame.Image image) {
        failedUploads++;
        if (!failures.containsKey(lease) && failures.size() >= MAX_TEXTURES) {
            Iterator<TouchDisplayRasterInbox.WriterLease> oldest = failures.keySet().iterator();
            TouchDisplayRasterInbox.WriterLease forgotten = oldest.next();
            oldest.remove();
            inbox.requestUpload(forgotten);
        }
        failures.put(lease, new Failure(image.sha256(), clock.getAsLong()));
    }
}
