package ca.teamdman.sfm.client.render;

import ca.teamdman.sfm.common.image.SFMImageSnapshot;
import net.minecraft.resources.ResourceLocation;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Render-thread cache of bounded static image snapshots. Admission never
 * releases a texture while a block-entity render buffer may still reference
 * it; pressure eviction happens at the next client-tick boundary instead.
 */
final class TouchDisplayTextureCache {
    static final int MAX_TEXTURES = 16;
    static final long MAX_GPU_BYTES = 16L * 1024 * 1024;

    interface TextureAccess {
        Optional<ResourceLocation> upload(SFMImageSnapshot snapshot);

        void release(ResourceLocation location);
    }

    private record Entry(SFMImageSnapshot snapshot, ResourceLocation location, long gpuBytes) {}

    private final TextureAccess textures;
    private final LinkedHashMap<String, Entry> entries = new LinkedHashMap<>(16, 0.75F, true);
    private final Set<String> failedDigests = new LinkedHashSet<>();
    private long gpuBytes;
    private boolean pressure;

    TouchDisplayTextureCache(TextureAccess textures) {
        this.textures = Objects.requireNonNull(textures, "textures");
    }

    Optional<ResourceLocation> textureFor(SFMImageSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        String digest = snapshot.sha256();
        Entry existing = entries.get(digest);
        if (existing != null) {
            // Digest collision, however unlikely, must not serve another image.
            return existing.snapshot().equals(snapshot)
                    ? Optional.of(existing.location())
                    : Optional.empty();
        }
        if (failedDigests.contains(digest)) return Optional.empty();

        long cost = 4L * snapshot.width() * snapshot.height();
        if (entries.size() >= MAX_TEXTURES || gpuBytes + cost > MAX_GPU_BYTES) {
            pressure = true;
            return Optional.empty();
        }
        Optional<ResourceLocation> uploaded = textures.upload(snapshot);
        if (uploaded.isEmpty()) {
            // A bad GPU/decoder result should not be retried on every frame.
            if (failedDigests.size() >= MAX_TEXTURES) {
                Iterator<String> failures = failedDigests.iterator();
                failures.next();
                failures.remove();
            }
            failedDigests.add(digest);
            return Optional.empty();
        }
        ResourceLocation location = uploaded.get();
        entries.put(digest, new Entry(snapshot, location, cost));
        gpuBytes += cost;
        return Optional.of(location);
    }

    /** Called after the render loop at a client-tick boundary. */
    void maintain() {
        if (!pressure || entries.isEmpty()) return;
        Iterator<Map.Entry<String, Entry>> iterator = entries.entrySet().iterator();
        Entry oldest = iterator.next().getValue();
        iterator.remove();
        gpuBytes -= oldest.gpuBytes();
        textures.release(oldest.location());
        pressure = false;
    }

    void clear() {
        for (Entry entry : entries.values()) textures.release(entry.location());
        entries.clear();
        failedDigests.clear();
        gpuBytes = 0;
        pressure = false;
    }

    int size() {
        return entries.size();
    }

    long gpuBytes() {
        return gpuBytes;
    }
}
