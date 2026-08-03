package ca.teamdman.sfm.client.terminal;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static ca.teamdman.sfm.client.terminal.SFMTerminalRgbaCompositor.ApplyResult.APPLIED;
import static ca.teamdman.sfm.client.terminal.SFMTerminalRgbaCompositor.ApplyResult.STALE_GENERATION;

class SFMTerminalRgbaCompositorTests {
    private static final SFMTerminalRasterLimits LIMITS = new SFMTerminalRasterLimits(8, 8, 1024, 8);

    @Test
    void fullRawPlusOrderedDirtyUpdatesEqualExpectedFullPixelsExactly() {
        byte[] initial = rgba(
                1, 2, 3, 4,       5, 6, 7, 8,       9, 10, 11, 12,
                13, 14, 15, 16,   17, 18, 19, 20,   21, 22, 23, 24);
        byte[] dirtyPayload = rgba(
                101, 102, 103, 104, 105, 106, 107, 108,
                109, 110, 111, 112);
        byte[] expected = rgba(
                1, 2, 3, 4,       101, 102, 103, 104, 105, 106, 107, 108,
                109, 110, 111, 112, 17, 18, 19, 20,  21, 22, 23, 24);

        SFMTerminalRgbaCompositor dirtyCompositor = new SFMTerminalRgbaCompositor(LIMITS);
        assertEquals(APPLIED, dirtyCompositor.apply(fullFrame("generation-a", 1, 3, 2, initial)));
        assertEquals(APPLIED, dirtyCompositor.apply(dirtyFrame(
                "generation-a",
                2,
                1,
                3,
                2,
                dirtyPayload,
                List.of(
                        region(1, 0, 2, 1, 0),
                        region(0, 1, 1, 1, 8)))));

        SFMTerminalRgbaCompositor fullCompositor = new SFMTerminalRgbaCompositor(LIMITS);
        assertEquals(APPLIED, fullCompositor.apply(fullFrame("generation-b", 1, 3, 2, expected)));

        assertArrayEquals(expected, dirtyCompositor.pixels());
        assertArrayEquals(fullCompositor.pixels(), dirtyCompositor.pixels());
        assertEquals(3, dirtyCompositor.width());
        assertEquals(2, dirtyCompositor.height());
        assertEquals(2, dirtyCompositor.lastFrameSequence());
    }

    @Test
    void multiRowRegionUsesTopLeftRowMajorRgbaByteOrderExactly() {
        byte[] initial = rgba(
                1, 2, 3, 4,       5, 6, 7, 8,
                9, 10, 11, 12,    13, 14, 15, 16);
        byte[] replacementColumn = rgba(
                101, 102, 103, 104,
                105, 106, 107, 108);
        byte[] expected = rgba(
                1, 2, 3, 4,       101, 102, 103, 104,
                9, 10, 11, 12,    105, 106, 107, 108);

        SFMTerminalRgbaCompositor compositor = new SFMTerminalRgbaCompositor(LIMITS);
        compositor.apply(fullFrame("generation-a", 1, 2, 2, initial));
        compositor.apply(dirtyFrame(
                "generation-a",
                2,
                1,
                2,
                2,
                replacementColumn,
                List.of(region(1, 0, 1, 2, 0))));

        assertArrayEquals(expected, compositor.pixels());
    }

    @Test
    void baseMismatchIsRejectedWithoutChangingPixelsOrSequence() {
        SFMTerminalRgbaCompositor compositor = new SFMTerminalRgbaCompositor(LIMITS);
        byte[] initial = rgba(1, 2, 3, 4, 5, 6, 7, 8);
        compositor.apply(fullFrame("generation-a", 5, 2, 1, initial));

        assertThrows(IllegalArgumentException.class, () -> compositor.apply(dirtyFrame(
                "generation-a",
                7,
                4,
                2,
                1,
                rgba(9, 10, 11, 12),
                List.of(region(0, 0, 1, 1, 0)))));

        assertArrayEquals(initial, compositor.pixels());
        assertEquals(5, compositor.lastFrameSequence());
    }

    @Test
    void expectedGenerationRejectsRetiredFramesWithoutChangingRetainedState() {
        SFMTerminalRgbaCompositor compositor = new SFMTerminalRgbaCompositor(LIMITS);
        byte[] initial = rgba(1, 2, 3, 4);
        compositor.apply(fullFrame("generation-a", 1, 1, 1, initial));
        compositor.expectGeneration("generation-b");

        assertEquals(STALE_GENERATION, compositor.apply(dirtyFrame(
                "generation-a",
                2,
                1,
                1,
                1,
                rgba(9, 10, 11, 12),
                List.of(region(0, 0, 1, 1, 0)))));

        assertArrayEquals(initial, compositor.pixels());
        assertEquals("generation-a", compositor.generation());
        assertEquals("generation-b", compositor.expectedGeneration());
        assertEquals(1, compositor.lastFrameSequence());
    }

    @Test
    void expectedGenerationFullResyncCanRestartSequenceAndRejectLateOldGeneration() {
        SFMTerminalRgbaCompositor compositor = new SFMTerminalRgbaCompositor(LIMITS);
        compositor.apply(fullFrame("generation-a", 10, 1, 1, rgba(1, 2, 3, 4)));
        byte[] replacement = rgba(5, 6, 7, 8, 9, 10, 11, 12);
        compositor.expectGeneration("generation-b");

        assertThrows(IllegalArgumentException.class, () -> compositor.apply(fullFrame(
                SFMTerminalTransportId.FULL_RAW_RGBA,
                "generation-b",
                1,
                false,
                2,
                1,
                replacement)));
        assertArrayEquals(rgba(1, 2, 3, 4), compositor.pixels());

        assertEquals(APPLIED, compositor.apply(fullFrame(
                SFMTerminalTransportId.DIRTY_RAW_RGBA,
                "generation-b",
                1,
                true,
                2,
                1,
                replacement)));
        assertEquals(STALE_GENERATION, compositor.apply(fullFrame(
                "generation-a", 11, 1, 1, rgba(99, 99, 99, 99))));

        assertArrayEquals(replacement, compositor.pixels());
        assertEquals("generation-b", compositor.generation());
        assertEquals("generation-b", compositor.expectedGeneration());
        assertEquals(1, compositor.lastFrameSequence());
        assertEquals(2, compositor.width());
    }

    @Test
    void generationChangesAreNeverAdoptedWithoutExplicitExpectation() {
        SFMTerminalRgbaCompositor compositor = new SFMTerminalRgbaCompositor(LIMITS);
        byte[] initial = rgba(1, 2, 3, 4);
        compositor.apply(fullFrame("generation-a", 1, 1, 1, initial));

        assertEquals(STALE_GENERATION, compositor.apply(fullFrame(
                "generation-b", 1, 1, 1, rgba(5, 6, 7, 8))));
        assertArrayEquals(initial, compositor.pixels());
        assertEquals("generation-a", compositor.generation());
        assertEquals("generation-a", compositor.expectedGeneration());
    }

    @Test
    void sameGenerationFullFramesAdvanceButDimensionChangesRequireResync() {
        SFMTerminalRgbaCompositor compositor = new SFMTerminalRgbaCompositor(LIMITS);
        compositor.apply(fullFrame("generation-a", 1, 1, 1, rgba(1, 2, 3, 4)));

        assertEquals(APPLIED, compositor.apply(fullFrame(
                SFMTerminalTransportId.FULL_RAW_RGBA,
                "generation-a",
                2,
                false,
                1,
                1,
                rgba(5, 6, 7, 8))));
        assertThrows(IllegalArgumentException.class, () -> compositor.apply(fullFrame(
                SFMTerminalTransportId.FULL_RAW_RGBA,
                "generation-a",
                3,
                false,
                2,
                1,
                rgba(9, 10, 11, 12, 13, 14, 15, 16))));

        assertArrayEquals(rgba(5, 6, 7, 8), compositor.pixels());
        assertEquals(2, compositor.lastFrameSequence());
    }

    @Test
    void frameAndCompositorDefensivelyOwnTheirPixelArraysAndRegionList() {
        byte[] source = rgba(1, 2, 3, 4);
        SFMTerminalRasterFrame frame = fullFrame("generation-a", 1, 1, 1, source);
        source[0] = 99;
        byte[] exposedFramePayload = frame.payload();
        exposedFramePayload[1] = 99;

        SFMTerminalRgbaCompositor compositor = new SFMTerminalRgbaCompositor(LIMITS);
        assertFalse(compositor.initialized());
        compositor.apply(frame);
        byte[] exposedPixels = compositor.pixels();
        exposedPixels[2] = 99;

        assertTrue(compositor.initialized());
        assertArrayEquals(rgba(1, 2, 3, 4), frame.payload());
        assertArrayEquals(rgba(1, 2, 3, 4), compositor.pixels());
        assertThrows(UnsupportedOperationException.class,
                () -> frame.regions().add(region(0, 0, 1, 1, 0)));
    }

    private static SFMTerminalRasterFrame fullFrame(
            String generation,
            long sequence,
            int width,
            int height,
            byte[] payload) {
        return fullFrame(
                SFMTerminalTransportId.FULL_RAW_RGBA,
                generation,
                sequence,
                true,
                width,
                height,
                payload);
    }

    private static SFMTerminalRasterFrame fullFrame(
            SFMTerminalTransportId transportId,
            String generation,
            long sequence,
            boolean fullResync,
            int width,
            int height,
            byte[] payload) {
        return new SFMTerminalRasterFrame(
                transportId,
                1,
                1,
                generation,
                sequence,
                0,
                fullResync,
                SFMTerminalRasterEncoding.RGBA8,
                SFMTerminalRasterFrameKind.FULL,
                SFMTerminalRasterOrigin.TOP_LEFT,
                SFMTerminalRasterAlphaMode.STRAIGHT,
                SFMTerminalRasterColorSpace.SRGB,
                width,
                height,
                width * 4L,
                payload,
                List.of());
    }

    private static SFMTerminalRasterFrame dirtyFrame(
            String generation,
            long sequence,
            long baseSequence,
            int width,
            int height,
            byte[] payload,
            List<SFMTerminalRasterRegion> regions) {
        return new SFMTerminalRasterFrame(
                SFMTerminalTransportId.DIRTY_RAW_RGBA,
                1,
                1,
                generation,
                sequence,
                baseSequence,
                false,
                SFMTerminalRasterEncoding.RGBA8,
                SFMTerminalRasterFrameKind.DIRTY_REGIONS,
                SFMTerminalRasterOrigin.TOP_LEFT,
                SFMTerminalRasterAlphaMode.STRAIGHT,
                SFMTerminalRasterColorSpace.SRGB,
                width,
                height,
                0,
                payload,
                regions);
    }

    private static SFMTerminalRasterRegion region(
            int x,
            int y,
            int width,
            int height,
            long payloadOffset) {
        long stride = width * 4L;
        return new SFMTerminalRasterRegion(
                x,
                y,
                width,
                height,
                stride,
                payloadOffset,
                stride * height);
    }

    private static byte[] rgba(int... unsignedBytes) {
        byte[] result = new byte[unsignedBytes.length];
        for (int index = 0; index < unsignedBytes.length; index++) {
            result[index] = (byte) unsignedBytes[index];
        }
        return result;
    }
}
