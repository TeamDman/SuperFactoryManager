package ca.teamdman.sfm.client.raster;

import org.junit.jupiter.api.Test;

import java.nio.ReadOnlyBufferException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static ca.teamdman.sfm.client.raster.TouchDisplayRasterInbox.OfferResult.*;
import static org.junit.jupiter.api.Assertions.*;

class TouchDisplayRasterInboxTests {
    private static final UUID WORLD = UUID.fromString("b1d480aa-b519-47c8-a685-23a96d0577cb");
    private static final String DIMENSION = "minecraft:overworld";

    @Test
    void newestFullFrameWinsAndCallerCannotMutatePixels() {
        TouchDisplayRasterInbox inbox = new TouchDisplayRasterInbox();
        var lease = inbox.acquire(display(0), "program").orElseThrow();
        byte[] original = {1, 2, 3, 4};
        var frame = TouchDisplayRasterFrame.full(1, 1, 1, original);
        original[0] = 99;
        assertEquals(ACCEPTED, inbox.offer(lease, frame));
        assertEquals(ACCEPTED_SUPERSEDING, inbox.offer(lease, pixel(3, 7)));
        assertEquals(REJECTED_SEQUENCE, inbox.offer(lease, pixel(2, 9)));
        assertEquals(REJECTED_SEQUENCE, inbox.offer(lease, pixel(3, 9)));
        var delivery = inbox.takeLatest(lease).orElseThrow();
        assertEquals(3, delivery.sequence());
        assertEquals(lease.generation(), delivery.generation());
        assertArrayEquals(new byte[]{7, 0, 0, -1}, pixels(delivery));
        assertThrows(ReadOnlyBufferException.class, () -> delivery.image().rgba().put(0, (byte) 99));
        assertTrue(inbox.takeLatest(lease).isEmpty());
        assertEquals(4, inbox.snapshot().residentBytes());

        var second = inbox.acquire(display(1), "second").orElseThrow();
        inbox.offer(second, frame);
        assertEquals(1, inbox.takeLatest(second).orElseThrow().image().rgba().get(0));
    }

    @Test
    void dirtyUpdatesComposeAcrossDroppedFramesAndRejectUnknownBases() {
        TouchDisplayRasterInbox inbox = new TouchDisplayRasterInbox();
        var lease = inbox.acquire(display(0), "program").orElseThrow();
        var right = TouchDisplayRasterFrame.dirty(2, 1, 2, 1, 1, 0, 1, 1, new byte[]{5, 6, 7, 8});
        assertEquals(REJECTED_BASE, inbox.offer(lease, right));
        inbox.offer(lease, TouchDisplayRasterFrame.full(1, 2, 1, new byte[8]));
        assertEquals(ACCEPTED_SUPERSEDING, inbox.offer(lease, right));
        assertEquals(REJECTED_BASE, inbox.offer(lease,
                TouchDisplayRasterFrame.dirty(4, 1, 2, 1, 0, 0, 1, 1, new byte[4])));
        assertEquals(REJECTED_BASE, inbox.offer(lease,
                TouchDisplayRasterFrame.dirty(4, 2, 1, 1, 0, 0, 1, 1, new byte[4])));
        assertEquals(ACCEPTED_SUPERSEDING, inbox.offer(lease,
                TouchDisplayRasterFrame.dirty(4, 2, 2, 1, 0, 0, 1, 1, new byte[]{1, 2, 3, 4})));
        assertArrayEquals(new byte[]{1, 2, 3, 4, 5, 6, 7, 8}, pixels(inbox.takeLatest(lease).orElseThrow()));
        assertEquals(8, inbox.snapshot().residentBytes());
    }

    @Test
    void unchangedPixelsAdvanceSequenceWithoutUploadingAndCancelRevertedPendingFrame() {
        TouchDisplayRasterInbox inbox = new TouchDisplayRasterInbox();
        var lease = inbox.acquire(display(0), "program").orElseThrow();
        inbox.offer(lease, pixel(1, 1));
        inbox.takeLatest(lease).orElseThrow();
        assertEquals(ACCEPTED_UNCHANGED, inbox.offer(lease, pixel(2, 1)));
        assertTrue(inbox.takeLatest(lease).isEmpty());
        assertEquals(ACCEPTED, inbox.offer(lease,
                TouchDisplayRasterFrame.dirty(3, 2, 1, 1, 0, 0, 1, 1, new byte[]{2, 0, 0, -1})));
        assertEquals(ACCEPTED_UNCHANGED, inbox.offer(lease, pixel(4, 1)));
        assertTrue(inbox.takeLatest(lease).isEmpty());
        assertEquals(4, inbox.snapshot().uploadBytes());
        assertEquals(0, inbox.snapshot().pendingDisplays());
    }

    @Test
    void ownershipIsExclusiveAndOldGenerationsCannotWriteOrReleaseReplacement() {
        TouchDisplayRasterInbox inbox = new TouchDisplayRasterInbox();
        var first = inbox.acquire(display(0), "first").orElseThrow();
        assertTrue(inbox.acquire(display(0), "second").isEmpty());
        inbox.offer(first, pixel(1, 1));
        assertTrue(inbox.release(first));
        var second = inbox.acquire(display(0), "second").orElseThrow();
        assertTrue(second.generation() > first.generation());
        assertEquals(REJECTED_LEASE, inbox.offer(first, pixel(100, 3)));
        assertFalse(inbox.release(first));
        assertTrue(inbox.isCurrent(second));
        assertEquals(ACCEPTED, inbox.offer(second, pixel(0, 2)));
        assertEquals(REJECTED_LEASE, new TouchDisplayRasterInbox().offer(second, pixel(1, 3)));
        inbox.clear();
        var third = inbox.acquire(display(0), "second").orElseThrow();
        assertTrue(third.generation() > second.generation());
        assertEquals(REJECTED_LEASE, inbox.offer(second, pixel(2, 3)));
    }

    @Test
    void ingressBudgetIncludesUnchangedContentAndSurvivesLeaseChurn() {
        AtomicLong clock = new AtomicLong();
        TouchDisplayRasterInbox inbox = limited(clock, 32, 8, 32, 32);
        var lease = inbox.acquire(display(0), "program").orElseThrow();
        assertEquals(ACCEPTED, inbox.offer(lease, pixel(1, 1)));
        assertEquals(ACCEPTED_UNCHANGED, inbox.offer(lease, pixel(2, 1)));
        assertEquals(REJECTED_INGRESS_BUDGET, inbox.offer(lease, pixel(3, 3)));
        inbox.release(lease);
        lease = inbox.acquire(display(0), "program").orElseThrow();
        assertEquals(REJECTED_INGRESS_BUDGET, inbox.offer(lease, pixel(1, 3)));
        clock.set(99);
        assertEquals(REJECTED_INGRESS_BUDGET, inbox.offer(lease, pixel(1, 3)));
        clock.set(100);
        assertEquals(ACCEPTED, inbox.offer(lease, pixel(1, 3)));
        assertEquals(4, inbox.snapshot().ingressBytes());
    }

    @Test
    void uploadBudgetDefersLatestAndFailedUploadCanBeRequestedAgain() {
        AtomicLong clock = new AtomicLong();
        TouchDisplayRasterInbox inbox = limited(clock, 32, 32, 32, 4);
        var lease = inbox.acquire(display(0), "program").orElseThrow();
        inbox.offer(lease, pixel(1, 1));
        inbox.takeLatest(lease).orElseThrow();
        inbox.offer(lease, pixel(2, 2));
        assertTrue(inbox.takeLatest(lease).isEmpty());
        inbox.offer(lease, pixel(3, 3));
        assertEquals(1, inbox.snapshot().pendingDisplays());
        clock.set(100);
        assertEquals(3, inbox.takeLatest(lease).orElseThrow().sequence());
        inbox.requestUpload(lease);
        assertTrue(inbox.takeLatest(lease).isEmpty());
        clock.set(200);
        assertEquals(3, inbox.takeLatest(lease).orElseThrow().sequence());
        assertTrue(inbox.takeLatest(lease).isEmpty());
    }

    @Test
    void tinyDirtyUpdatesPayForFullReconstructionWork() {
        AtomicLong clock = new AtomicLong();
        TouchDisplayRasterInbox inbox = limited(clock, 64, 64, 32, 64);
        var lease = inbox.acquire(display(0), "program").orElseThrow();
        inbox.offer(lease, TouchDisplayRasterFrame.full(1, 2, 2, new byte[16]));
        var dirty = TouchDisplayRasterFrame.dirty(2, 1, 2, 2, 0, 0, 1, 1, new byte[4]);
        assertEquals(ACCEPTED_UNCHANGED, inbox.offer(lease, dirty));
        assertEquals(REJECTED_PROCESSING_BUDGET, inbox.offer(lease,
                TouchDisplayRasterFrame.dirty(3, 2, 2, 2, 0, 0, 1, 1, new byte[4])));
        assertEquals(24, inbox.snapshot().ingressBytes());
        assertEquals(32, inbox.snapshot().processingBytes());
        clock.set(100);
        assertEquals(ACCEPTED_UNCHANGED, inbox.offer(lease,
                TouchDisplayRasterFrame.dirty(3, 2, 2, 2, 0, 0, 1, 1, new byte[4])));
    }

    @Test
    void manyDisplaysStayWithinEightMiBAndReclaimMemoryOnRelease() {
        AtomicLong clock = new AtomicLong();
        TouchDisplayRasterInbox inbox = new TouchDisplayRasterInbox(TouchDisplayRasterInbox.Limits.DEFAULT, clock::get);
        List<TouchDisplayRasterInbox.WriterLease> leases = new ArrayList<>();
        byte[] full = new byte[TouchDisplayRasterFrame.MAX_IMAGE_BYTES];
        for (int index = 0; index < 9; index++) {
            leases.add(inbox.acquire(display(index), "program-" + index).orElseThrow());
            clock.addAndGet(1_000_000_000L);
            assertEquals(index < 8 ? ACCEPTED : REJECTED_MEMORY_LIMIT,
                    inbox.offer(leases.get(index), TouchDisplayRasterFrame.full(1, 512, 512, full)));
        }
        assertEquals(8L * 1024 * 1024, inbox.snapshot().residentBytes());
        assertTrue(inbox.release(leases.get(0)));
        assertEquals(ACCEPTED, inbox.offer(leases.get(8), TouchDisplayRasterFrame.full(1, 512, 512, full)));
        assertEquals(8L * 1024 * 1024, inbox.snapshot().residentBytes());
        inbox.clear();
        assertEquals(0, inbox.snapshot().residentBytes());
        assertEquals(0, inbox.snapshot().displays());
        assertTrue(inbox.takeLatest(leases.get(1)).isEmpty());
    }

    @Test
    void chunkAndWorldUnloadOnlyRemoveMatchingLeasesIncludingNegativeCoordinates() {
        TouchDisplayRasterInbox inbox = new TouchDisplayRasterInbox();
        UUID otherWorld = UUID.fromString("6583c17b-53f1-4085-a1b1-364a4804136b");
        var negative = inbox.acquire(display(-1), "negative").orElseThrow();
        var adjacent = inbox.acquire(display(0), "adjacent").orElseThrow();
        var otherDimension = inbox.acquire(new TouchDisplayRasterInbox.Display(WORLD, "minecraft:the_nether", -1, 0, 0), "nether").orElseThrow();
        var other = inbox.acquire(new TouchDisplayRasterInbox.Display(otherWorld, DIMENSION, -1, 0, 0), "other").orElseThrow();
        for (var lease : List.of(negative, adjacent, otherDimension, other)) inbox.offer(lease, pixel(0, 1));
        inbox.unloadChunk(WORLD, DIMENSION, -1, 0);
        assertFalse(inbox.isCurrent(negative));
        assertTrue(inbox.isCurrent(adjacent));
        assertTrue(inbox.isCurrent(otherDimension));
        assertEquals(12, inbox.snapshot().residentBytes());
        inbox.unloadWorld(WORLD);
        assertFalse(inbox.isCurrent(adjacent));
        assertFalse(inbox.isCurrent(otherDimension));
        assertTrue(inbox.isCurrent(other));
        assertEquals(4, inbox.snapshot().residentBytes());
    }

    @Test
    void frameConstructionRejectsMalformedOrOversizedPayloadsBeforeCopying() {
        assertThrows(IllegalArgumentException.class, () -> TouchDisplayRasterFrame.full(0, 513, 1, new byte[0]));
        assertThrows(IllegalArgumentException.class, () -> TouchDisplayRasterFrame.full(0, 1, 0, new byte[0]));
        assertThrows(IllegalArgumentException.class, () -> TouchDisplayRasterFrame.full(0, 1, 1, new byte[5]));
        assertThrows(IllegalArgumentException.class, () -> TouchDisplayRasterFrame.full(-1, 1, 1, new byte[4]));
        assertThrows(IllegalArgumentException.class, () -> TouchDisplayRasterFrame.dirty(1, 1, 1, 1, 0, 0, 1, 1, new byte[4]));
        assertThrows(IllegalArgumentException.class, () -> TouchDisplayRasterFrame.dirty(1, 0, 1, 1, Integer.MAX_VALUE, 0, 1, 1, new byte[4]));
        assertThrows(IllegalArgumentException.class, () -> TouchDisplayRasterFrame.dirty(1, 0, 1, 1, 0, 0, -1, 1, new byte[4]));
        TouchDisplayRasterInbox inbox = new TouchDisplayRasterInbox(
                new TouchDisplayRasterInbox.Limits(1, 4, 4, 4, 4, 100), () -> 0
        );
        var lease = inbox.acquire(display(0), "program").orElseThrow();
        assertTrue(inbox.acquire(display(1), "another").isEmpty());
        assertEquals(REJECTED_FRAME, inbox.offer(lease, null));
    }

    @Test
    void tinyFramesCannotEvadeTheProcessingOperationCeiling() {
        AtomicLong clock = new AtomicLong();
        TouchDisplayRasterInbox inbox = new TouchDisplayRasterInbox(TouchDisplayRasterInbox.Limits.DEFAULT, clock::get);
        var lease = inbox.acquire(display(0), "program").orElseThrow();
        for (int sequence = 0; sequence < 4096; sequence++) {
            assertTrue(inbox.offer(lease, pixel(sequence, 1)).accepted());
        }
        assertEquals(REJECTED_PROCESSING_BUDGET, inbox.offer(lease, pixel(4096, 1)));
        clock.set(1_000_000_000L);
        assertEquals(ACCEPTED_UNCHANGED, inbox.offer(lease, pixel(4096, 1)));
    }

    @Test
    void changedDimensionsRequireUploadEvenWhenPixelBytesAreIdentical() {
        TouchDisplayRasterInbox inbox = new TouchDisplayRasterInbox();
        var lease = inbox.acquire(display(0), "program").orElseThrow();
        byte[] pixels = new byte[8];
        inbox.offer(lease, TouchDisplayRasterFrame.full(1, 2, 1, pixels));
        var first = inbox.takeLatest(lease).orElseThrow();
        assertEquals(ACCEPTED, inbox.offer(lease, TouchDisplayRasterFrame.full(2, 1, 2, pixels)));
        var second = inbox.takeLatest(lease).orElseThrow();
        assertNotEquals(first.image().sha256(), second.image().sha256());
        assertEquals(1, second.image().width());
        assertEquals(2, second.image().height());
        assertEquals(8, inbox.snapshot().residentBytes());
    }

    private static TouchDisplayRasterInbox limited(AtomicLong clock, long memory, long ingress, long work, long upload) {
        return new TouchDisplayRasterInbox(
                new TouchDisplayRasterInbox.Limits(16, memory, ingress, work, upload, 100), clock::get
        );
    }

    private static TouchDisplayRasterInbox.Display display(int x) {
        return new TouchDisplayRasterInbox.Display(WORLD, DIMENSION, x, 0, 0);
    }

    private static TouchDisplayRasterFrame pixel(long sequence, int red) {
        return TouchDisplayRasterFrame.full(sequence, 1, 1, new byte[]{(byte) red, 0, 0, -1});
    }

    private static byte[] pixels(TouchDisplayRasterInbox.Delivery delivery) {
        byte[] pixels = new byte[delivery.image().byteSize()];
        delivery.image().rgba().get(pixels);
        return pixels;
    }
}
