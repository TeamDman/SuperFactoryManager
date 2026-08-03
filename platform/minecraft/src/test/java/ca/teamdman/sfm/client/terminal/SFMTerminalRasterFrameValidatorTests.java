package ca.teamdman.sfm.client.terminal;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SFMTerminalRasterFrameValidatorTests {
    private static final SFMTerminalRasterLimits LIMITS = new SFMTerminalRasterLimits(8, 8, 1024, 8);

    @Test
    void acceptsVersionOneTopLeftStraightAlphaFullAndOrderedDirtyFrames() {
        assertDoesNotThrow(() -> validate(fullFrame("generation-a", 1, 2, 2, rgba(
                1, 2, 3, 4,
                5, 6, 7, 8,
                9, 10, 11, 12,
                13, 14, 15, 16))));

        SFMTerminalRasterFrame dirty = dirtyFrame(
                "generation-a",
                2,
                1,
                3,
                2,
                rgba(20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31),
                List.of(
                        region(1, 0, 2, 1, 0),
                        region(0, 1, 1, 1, 8)));
        assertDoesNotThrow(() -> validate(dirty));
    }

    @Test
    void rejectsMalformedDimensionsStrideAndPayloadLength() {
        assertThrows(IllegalArgumentException.class,
                () -> validate(fullFrame("g", 1, 0, 1, new byte[0])));

        SFMTerminalRasterFrame wrongStride = frame(
                SFMTerminalTransportId.FULL_RAW_RGBA,
                "g",
                1,
                0,
                true,
                SFMTerminalRasterFrameKind.FULL,
                1,
                1,
                3,
                new byte[4],
                List.of());
        assertThrows(IllegalArgumentException.class, () -> validate(wrongStride));

        assertThrows(IllegalArgumentException.class,
                () -> validate(fullFrame("g", 1, 1, 1, new byte[3])));
        assertThrows(IllegalArgumentException.class,
                () -> validate(fullFrame("g", 1, 9, 1, new byte[36])));
        SFMTerminalRasterLimits smallerPayloadLimit = new SFMTerminalRasterLimits(8, 8, 128, 8);
        assertThrows(IllegalArgumentException.class,
                () -> SFMTerminalRasterFrameValidator.validateRgba8(
                        fullFrame("g", 1, 8, 8, new byte[256]),
                        smallerPayloadLimit));
    }

    @Test
    void negotiatedLimitsCannotExceedContractVersionOneHardBounds() {
        assertEquals(4096, SFMTerminalRasterLimits.RGBA8_V1_DEFAULTS.maxWidth());
        assertEquals(4096, SFMTerminalRasterLimits.RGBA8_V1_DEFAULTS.maxHeight());
        assertEquals(16L * 1024L * 1024L,
                SFMTerminalRasterLimits.RGBA8_V1_DEFAULTS.maxPayloadBytes());
        assertEquals(64, SFMTerminalRasterLimits.RGBA8_V1_DEFAULTS.maxRegions());

        assertThrows(IllegalArgumentException.class,
                () -> new SFMTerminalRasterLimits(0, 1, 4, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new SFMTerminalRasterLimits(4097, 1, 4, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new SFMTerminalRasterLimits(1, 4097, 4, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new SFMTerminalRasterLimits(1, 1, 16L * 1024L * 1024L + 1, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new SFMTerminalRasterLimits(1, 1, 4, 65));
    }

    @Test
    void rejectsWrongContractMetadataAndTransportShape() {
        SFMTerminalRasterFrame png = frame(
                SFMTerminalTransportId.FULL_PNG,
                "g",
                1,
                0,
                true,
                SFMTerminalRasterFrameKind.FULL,
                1,
                1,
                4,
                new byte[4],
                List.of());
        assertThrows(IllegalArgumentException.class, () -> validate(png));

        assertThrows(IllegalArgumentException.class,
                () -> validate(frameWithVersions(2, 1, "g")));
        assertThrows(IllegalArgumentException.class,
                () -> validate(frameWithVersions(1, 2, "g")));
        assertThrows(IllegalArgumentException.class,
                () -> validate(frameWithVersions(1, 1, "   ")));

        assertThrows(IllegalArgumentException.class, () -> validate(frameWithRasterLayout(
                SFMTerminalRasterEncoding.PNG,
                SFMTerminalRasterOrigin.TOP_LEFT,
                SFMTerminalRasterAlphaMode.STRAIGHT,
                SFMTerminalRasterColorSpace.SRGB)));
        assertThrows(IllegalArgumentException.class, () -> validate(frameWithRasterLayout(
                SFMTerminalRasterEncoding.RGBA8,
                SFMTerminalRasterOrigin.BOTTOM_LEFT,
                SFMTerminalRasterAlphaMode.STRAIGHT,
                SFMTerminalRasterColorSpace.SRGB)));
        assertThrows(IllegalArgumentException.class, () -> validate(frameWithRasterLayout(
                SFMTerminalRasterEncoding.RGBA8,
                SFMTerminalRasterOrigin.TOP_LEFT,
                SFMTerminalRasterAlphaMode.PREMULTIPLIED,
                SFMTerminalRasterColorSpace.SRGB)));
        assertThrows(IllegalArgumentException.class, () -> validate(frameWithRasterLayout(
                SFMTerminalRasterEncoding.RGBA8,
                SFMTerminalRasterOrigin.TOP_LEFT,
                SFMTerminalRasterAlphaMode.STRAIGHT,
                SFMTerminalRasterColorSpace.LINEAR)));
    }

    @Test
    void rejectsInvalidDirtyBaseSequenceAndGenerationMetadata() {
        SFMTerminalRasterRegion region = region(0, 0, 1, 1, 0);
        assertThrows(IllegalArgumentException.class,
                () -> validate(dirtyFrame("g", 2, 0, 1, 1, new byte[4], List.of(region))));
        assertThrows(IllegalArgumentException.class,
                () -> validate(dirtyFrame("g", 2, 2, 1, 1, new byte[4], List.of(region))));
        assertThrows(IllegalArgumentException.class,
                () -> validate(dirtyFrame("", 2, 1, 1, 1, new byte[4], List.of(region))));
        assertThrows(IllegalArgumentException.class,
                () -> validate(dirtyFrame("g", 1, 1, 1, 1, new byte[4], List.of(region))));
        assertThrows(IllegalArgumentException.class,
                () -> validate(dirtyFrame("g", -1, 1, 1, 1, new byte[4], List.of(region))));
    }

    @Test
    void rejectsOutOfBoundsUnorderedOverlappingAndUnpackedRegions() {
        assertThrows(IllegalArgumentException.class, () -> validate(dirtyFrame(
                "g", 2, 1, 2, 2, new byte[8], List.of(region(1, 1, 2, 1, 0)))));

        assertThrows(IllegalArgumentException.class, () -> validate(dirtyFrame(
                "g", 2, 1, 2, 2, new byte[8], List.of(
                        region(0, 1, 1, 1, 0),
                        region(0, 0, 1, 1, 4)))));

        assertThrows(IllegalArgumentException.class, () -> validate(dirtyFrame(
                "g", 2, 1, 3, 2, new byte[24], List.of(
                        region(0, 0, 2, 2, 0),
                        region(1, 1, 2, 1, 16)))));

        assertThrows(IllegalArgumentException.class, () -> validate(dirtyFrame(
                "g", 2, 1, 2, 1, new byte[8], List.of(
                        region(0, 0, 1, 1, 0),
                        region(1, 0, 1, 1, 5)))));

        assertThrows(IllegalArgumentException.class, () -> validate(dirtyFrame(
                "g", 2, 1, 2, 1, new byte[8], List.of(
                        region(0, 0, 1, 1, 0),
                        region(1, 0, 1, 1, 4),
                        region(0, 0, 1, 1, 8)))));
    }

    @Test
    void rejectsInvalidRegionShapeStrideLengthCountAndTrailingPayload() {
        assertThrows(IllegalArgumentException.class, () -> validate(dirtyFrame(
                "g", 2, 1, 1, 1, new byte[4], List.of(
                        new SFMTerminalRasterRegion(0, 0, 0, 1, 0, 0, 0)))));
        assertThrows(IllegalArgumentException.class, () -> validate(dirtyFrame(
                "g", 2, 1, 1, 1, new byte[4], List.of(
                        new SFMTerminalRasterRegion(0, 0, 1, 1, 8, 0, 4)))));
        assertThrows(IllegalArgumentException.class, () -> validate(dirtyFrame(
                "g", 2, 1, 1, 1, new byte[4], List.of(
                        new SFMTerminalRasterRegion(0, 0, 1, 1, 4, 0, 3)))));
        assertThrows(IllegalArgumentException.class, () -> validate(dirtyFrame(
                "g", 2, 1, 1, 1, new byte[5], List.of(region(0, 0, 1, 1, 0)))));

        List<SFMTerminalRasterRegion> tooMany = java.util.stream.IntStream.range(0, 9)
                .mapToObj(index -> region(index % 8, index / 8, 1, 1, index * 4L))
                .toList();
        assertThrows(IllegalArgumentException.class, () -> validate(dirtyFrame(
                "g", 2, 1, 8, 2, new byte[36], tooMany)));
    }

    @Test
    void acceptsGeometricallyAdjacentRegionsAndRejectsInvalidFrameShapes() {
        assertDoesNotThrow(() -> validate(dirtyFrame(
                "g", 2, 1, 2, 2, new byte[16], List.of(
                        region(0, 0, 1, 2, 0),
                        region(1, 0, 1, 2, 8)))));

        assertThrows(IllegalArgumentException.class, () -> validate(frame(
                SFMTerminalTransportId.FULL_RAW_RGBA,
                "g",
                2,
                1,
                false,
                SFMTerminalRasterFrameKind.FULL,
                1,
                1,
                4,
                new byte[4],
                List.of())));
        assertThrows(IllegalArgumentException.class, () -> validate(frame(
                SFMTerminalTransportId.DIRTY_RAW_RGBA,
                "g",
                2,
                0,
                false,
                SFMTerminalRasterFrameKind.FULL,
                1,
                1,
                4,
                new byte[4],
                List.of())));
        assertThrows(IllegalArgumentException.class, () -> validate(frame(
                SFMTerminalTransportId.FULL_RAW_RGBA,
                "g",
                2,
                1,
                false,
                SFMTerminalRasterFrameKind.DIRTY_REGIONS,
                1,
                1,
                0,
                new byte[4],
                List.of(region(0, 0, 1, 1, 0)))));
    }

    @Test
    void rejectsArithmeticOverflowBeforeNarrowingPayloadRanges() {
        SFMTerminalRasterRegion overflowing = new SFMTerminalRasterRegion(
                0,
                0,
                1,
                1,
                4,
                Long.MAX_VALUE,
                4);
        assertThrows(IllegalArgumentException.class, () -> validate(dirtyFrame(
                "g", 2, 1, 1, 1, new byte[4], List.of(overflowing))));
        assertThrows(IllegalArgumentException.class,
                () -> SFMTerminalRasterFrameValidator.checkedProduct(Long.MAX_VALUE, 2, "test product"));
    }

    private static void validate(SFMTerminalRasterFrame frame) {
        SFMTerminalRasterFrameValidator.validateRgba8(frame, LIMITS);
    }

    private static SFMTerminalRasterFrame fullFrame(
            String generation,
            long sequence,
            int width,
            int height,
            byte[] payload) {
        return frame(
                SFMTerminalTransportId.FULL_RAW_RGBA,
                generation,
                sequence,
                0,
                true,
                SFMTerminalRasterFrameKind.FULL,
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
        return frame(
                SFMTerminalTransportId.DIRTY_RAW_RGBA,
                generation,
                sequence,
                baseSequence,
                false,
                SFMTerminalRasterFrameKind.DIRTY_REGIONS,
                width,
                height,
                0,
                payload,
                regions);
    }

    private static SFMTerminalRasterFrame frameWithVersions(
            int transportVersion,
            int contractVersion,
            String generation) {
        return new SFMTerminalRasterFrame(
                SFMTerminalTransportId.FULL_RAW_RGBA,
                transportVersion,
                contractVersion,
                generation,
                1,
                0,
                true,
                SFMTerminalRasterEncoding.RGBA8,
                SFMTerminalRasterFrameKind.FULL,
                SFMTerminalRasterOrigin.TOP_LEFT,
                SFMTerminalRasterAlphaMode.STRAIGHT,
                SFMTerminalRasterColorSpace.SRGB,
                1,
                1,
                4,
                new byte[4],
                List.of());
    }

    private static SFMTerminalRasterFrame frameWithRasterLayout(
            SFMTerminalRasterEncoding encoding,
            SFMTerminalRasterOrigin origin,
            SFMTerminalRasterAlphaMode alphaMode,
            SFMTerminalRasterColorSpace colorSpace) {
        return new SFMTerminalRasterFrame(
                SFMTerminalTransportId.FULL_RAW_RGBA,
                1,
                1,
                "g",
                1,
                0,
                true,
                encoding,
                SFMTerminalRasterFrameKind.FULL,
                origin,
                alphaMode,
                colorSpace,
                1,
                1,
                4,
                new byte[4],
                List.of());
    }

    private static SFMTerminalRasterFrame frame(
            SFMTerminalTransportId transportId,
            String generation,
            long sequence,
            long baseSequence,
            boolean fullResync,
            SFMTerminalRasterFrameKind kind,
            int width,
            int height,
            long stride,
            byte[] payload,
            List<SFMTerminalRasterRegion> regions) {
        return new SFMTerminalRasterFrame(
                transportId,
                1,
                1,
                generation,
                sequence,
                baseSequence,
                fullResync,
                SFMTerminalRasterEncoding.RGBA8,
                kind,
                SFMTerminalRasterOrigin.TOP_LEFT,
                SFMTerminalRasterAlphaMode.STRAIGHT,
                SFMTerminalRasterColorSpace.SRGB,
                width,
                height,
                stride,
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
