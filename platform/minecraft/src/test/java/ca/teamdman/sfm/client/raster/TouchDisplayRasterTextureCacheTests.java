package ca.teamdman.sfm.client.raster;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class TouchDisplayRasterTextureCacheTests {
    private static final UUID WORLD = UUID.fromString("461ed316-d128-4be5-a70c-c2de1e1e279c");

    @Test
    void unchangedFramesReuseTheTextureAndDirtyFramesUpdateItInPlace() {
        Fixture fixture = new Fixture(32, 32);
        var lease = fixture.lease(0);
        fixture.inbox.offer(lease, pixel(1, 1));
        var texture = fixture.cache.textureFor(lease).orElseThrow();
        fixture.inbox.offer(lease, pixel(2, 1));
        for (int i = 0; i < 100; i++) assertSame(texture, fixture.cache.textureFor(lease).orElseThrow());
        assertEquals(1, fixture.cache.info(lease).orElseThrow().uploads());
        fixture.inbox.offer(lease, TouchDisplayRasterFrame.dirty(3, 2, 1, 1, 0, 0, 1, 1, new byte[]{2, 0, 0, -1}));
        assertSame(texture, fixture.cache.textureFor(lease).orElseThrow());
        assertEquals(2, texture.image.rgba().get(0));
        assertEquals(2, fixture.cache.info(lease).orElseThrow().uploads());
        assertEquals(1, fixture.textures.created);
    }

    @Test
    void uploadBudgetRetainsPreviousTextureUntilTheLatestFrameCanBeDelivered() {
        Fixture fixture = new Fixture(32, 4);
        var lease = fixture.lease(0);
        fixture.inbox.offer(lease, pixel(1, 1));
        var texture = fixture.cache.textureFor(lease).orElseThrow();
        fixture.inbox.offer(lease, pixel(2, 2));
        assertSame(texture, fixture.cache.textureFor(lease).orElseThrow());
        assertEquals(1, texture.image.rgba().get(0));
        fixture.inbox.offer(lease, pixel(3, 3));
        fixture.clock.set(100);
        assertSame(texture, fixture.cache.textureFor(lease).orElseThrow());
        assertEquals(3, texture.image.rgba().get(0));
        assertEquals(2, fixture.cache.snapshot().uploads());
    }

    @Test
    void gpuPressureDoesNotConsumePendingImagesAndRetiredTexturesStillCountUntilReleased() {
        Fixture fixture = new Fixture(8, 32);
        var first = fixture.lease(0);
        var second = fixture.lease(1);
        var third = fixture.lease(2);
        for (var lease : List.of(first, second, third)) fixture.inbox.offer(lease, pixel(1, 1));
        fixture.cache.textureFor(first).orElseThrow();
        fixture.cache.textureFor(second).orElseThrow();
        assertTrue(fixture.cache.textureFor(third).isEmpty());
        assertTrue(fixture.inbox.latestInfo(third).orElseThrow().pending());
        assertEquals(8, fixture.inbox.snapshot().uploadBytes());
        fixture.inbox.release(first);
        assertTrue(fixture.cache.textureFor(first).isEmpty());
        assertEquals(1, fixture.cache.snapshot().retiredTextures());
        assertEquals(8, fixture.cache.snapshot().gpuBytes());
        assertTrue(fixture.cache.textureFor(third).isEmpty());
        fixture.cache.maintain();
        fixture.cache.textureFor(third).orElseThrow();
        assertEquals(8, fixture.cache.snapshot().gpuBytes());
        assertEquals(1, fixture.textures.released.size());
    }

    @Test
    void resizeKeepsOldFrameWhilePressureDefersTheReplacement() {
        Fixture fixture = new Fixture(12, 32);
        var lease = fixture.lease(0);
        var other = fixture.lease(1);
        fixture.inbox.offer(lease, pixel(1, 1));
        var first = fixture.cache.textureFor(lease).orElseThrow();
        fixture.inbox.offer(other, pixel(1, 2));
        fixture.cache.textureFor(other).orElseThrow();
        fixture.inbox.offer(lease, TouchDisplayRasterFrame.full(2, 2, 1, new byte[8]));
        assertSame(first, fixture.cache.textureFor(lease).orElseThrow());
        assertEquals(1, first.image.width());
        assertEquals(8, fixture.cache.snapshot().gpuBytes());
        assertTrue(fixture.inbox.latestInfo(lease).orElseThrow().pending());
        fixture.inbox.release(other);
        fixture.cache.maintain();
        var resized = fixture.cache.textureFor(lease).orElseThrow();
        assertNotSame(first, resized);
        assertEquals(2, resized.image.width());
        assertEquals(12, fixture.cache.snapshot().gpuBytes());
        fixture.cache.maintain();
        assertEquals(8, fixture.cache.snapshot().gpuBytes());
    }

    @Test
    void failedUploadsDoNotRetryEveryFrameAndNewContentCanRecover() {
        Fixture fixture = new Fixture(32, 64);
        var lease = fixture.lease(0);
        fixture.inbox.offer(lease, pixel(1, 1));
        fixture.cache.textureFor(lease).orElseThrow();
        fixture.textures.fail = true;
        fixture.inbox.offer(lease, pixel(2, 2));
        assertTrue(fixture.cache.textureFor(lease).isEmpty());
        fixture.cache.maintain();
        for (int i = 0; i < 100; i++) assertTrue(fixture.cache.textureFor(lease).isEmpty());
        assertEquals(2, fixture.textures.attempts);
        assertEquals(0, fixture.cache.snapshot().gpuBytes());
        fixture.textures.fail = false;
        fixture.inbox.offer(lease, pixel(3, 3));
        assertEquals(3, fixture.cache.textureFor(lease).orElseThrow().image.rgba().get(0));
        assertEquals(3, fixture.textures.attempts);
    }

    @Test
    void resourceReloadRetriesUnchangedPixelsIncludingAFormerUploadFailure() {
        Fixture fixture = new Fixture(32, 64);
        var lease = fixture.lease(0);
        fixture.inbox.offer(lease, pixel(1, 1));
        var first = fixture.cache.textureFor(lease).orElseThrow();
        fixture.cache.invalidateTextures();
        fixture.cache.maintain();
        var second = fixture.cache.textureFor(lease).orElseThrow();
        assertNotSame(first, second);
        assertEquals(2, fixture.cache.snapshot().uploads());
        fixture.textures.fail = true;
        fixture.inbox.offer(lease, pixel(2, 2));
        assertTrue(fixture.cache.textureFor(lease).isEmpty());
        fixture.textures.fail = false;
        fixture.cache.invalidateTextures();
        fixture.cache.maintain();
        assertEquals(2, fixture.cache.textureFor(lease).orElseThrow().image.rgba().get(0));
    }

    @Test
    void clearInvalidatesLateWritersAndReleasesEveryTextureExactlyOnce() {
        Fixture fixture = new Fixture(32, 64);
        var first = fixture.lease(0);
        var second = fixture.lease(1);
        for (var lease : List.of(first, second)) {
            fixture.inbox.offer(lease, pixel(1, 1));
            fixture.cache.textureFor(lease).orElseThrow();
        }
        fixture.inbox.clear();
        fixture.cache.maintain();
        assertEquals(0, fixture.cache.snapshot().gpuBytes());
        assertEquals(0, fixture.cache.snapshot().textures());
        assertEquals(2, fixture.textures.released.size());
        assertTrue(fixture.cache.textureFor(first).isEmpty());
        fixture.cache.invalidateTextures();
        fixture.cache.maintain();
        assertEquals(2, fixture.textures.released.size());
    }

    @Test
    void tinyTexturesCannotEvadeTheAllocationCountLimitDuringDeferredDeletion() {
        TouchDisplayRasterInbox inbox = new TouchDisplayRasterInbox();
        FakeTextures textures = new FakeTextures();
        var cache = new TouchDisplayRasterTextureCache<>(inbox, textures);
        List<TouchDisplayRasterInbox.WriterLease> leases = new ArrayList<>();
        for (int x = 0; x < TouchDisplayRasterTextureCache.MAX_TEXTURES; x++) {
            var lease = inbox.acquire(new TouchDisplayRasterInbox.Display(WORLD, "minecraft:overworld", x, 0, 0), "program").orElseThrow();
            leases.add(lease);
            inbox.offer(lease, pixel(0, 1));
            cache.textureFor(lease).orElseThrow();
        }
        inbox.release(leases.get(0));
        cache.retire(leases.get(0));
        var next = inbox.acquire(new TouchDisplayRasterInbox.Display(WORLD, "minecraft:overworld", 1000, 0, 0), "replacement").orElseThrow();
        inbox.offer(next, pixel(0, 1));
        assertTrue(cache.textureFor(next).isEmpty());
        assertTrue(inbox.latestInfo(next).orElseThrow().pending());
        assertEquals(512, cache.snapshot().gpuBytes(), "Count admission must apply well below the byte cap");
        cache.maintain();
        cache.textureFor(next).orElseThrow();
        assertEquals(128, cache.snapshot().textures());
        assertEquals(512, cache.snapshot().gpuBytes());
    }

    private static TouchDisplayRasterFrame pixel(long sequence, int red) {
        return TouchDisplayRasterFrame.full(sequence, 1, 1, new byte[]{(byte) red, 0, 0, -1});
    }

    private static final class Fixture {
        private final AtomicLong clock = new AtomicLong();
        private final FakeTextures textures = new FakeTextures();
        private final TouchDisplayRasterInbox inbox;
        private final TouchDisplayRasterTextureCache<FakeTexture> cache;

        private Fixture(long gpuBytes, long uploadBytes) {
            inbox = new TouchDisplayRasterInbox(new TouchDisplayRasterInbox.Limits(16, 1024, 1024, 1024, uploadBytes, 100), clock::get);
            cache = new TouchDisplayRasterTextureCache<>(inbox, textures, gpuBytes, clock::get);
        }

        private TouchDisplayRasterInbox.WriterLease lease(int x) {
            return inbox.acquire(new TouchDisplayRasterInbox.Display(WORLD, "minecraft:overworld", x, 0, 0), "program").orElseThrow();
        }
    }

    private static final class FakeTexture {
        private TouchDisplayRasterFrame.Image image;
        private FakeTexture(TouchDisplayRasterFrame.Image image) { this.image = image; }
    }

    private static final class FakeTextures implements TouchDisplayRasterTextureCache.TextureAccess<FakeTexture> {
        private boolean fail;
        private int attempts;
        private int created;
        private final List<FakeTexture> released = new ArrayList<>();

        @Override
        public Optional<FakeTexture> create(TouchDisplayRasterInbox.WriterLease lease, TouchDisplayRasterFrame.Image image) {
            attempts++;
            if (fail) return Optional.empty();
            created++;
            return Optional.of(new FakeTexture(image));
        }

        @Override
        public boolean update(FakeTexture texture, TouchDisplayRasterFrame.Image image) {
            attempts++;
            if (fail) return false;
            texture.image = image;
            return true;
        }

        @Override
        public void release(FakeTexture texture) {
            assertFalse(released.contains(texture), "Texture released twice");
            released.add(texture);
        }
    }
}
