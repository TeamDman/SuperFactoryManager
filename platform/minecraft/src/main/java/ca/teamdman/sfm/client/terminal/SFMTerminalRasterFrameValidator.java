package ca.teamdman.sfm.client.terminal;

import java.util.List;
import java.util.Objects;

/**
 * Strict validation for the version-one RGBA8 contract: RGBA byte order,
 * top-left origin, top-to-bottom tightly packed rows, straight alpha, and sRGB.
 */
public final class SFMTerminalRasterFrameValidator {
    public static final int TRANSPORT_VERSION = 1;
    public static final int RGBA8_CONTRACT_VERSION = 1;
    public static final int RGBA8_BYTES_PER_PIXEL = 4;

    private SFMTerminalRasterFrameValidator() {
    }

    public static void validateRgba8(
            SFMTerminalRasterFrame frame,
            SFMTerminalRasterLimits limits) {
        Objects.requireNonNull(frame, "frame");
        Objects.requireNonNull(limits, "limits");

        require(frame.transportId() != SFMTerminalTransportId.FULL_PNG,
                "full-png is not an RGBA8 transport");
        require(frame.transportVersion() == TRANSPORT_VERSION,
                "unsupported terminal raster transport version");
        require(frame.frameContractVersion() == RGBA8_CONTRACT_VERSION,
                "unsupported RGBA8 frame contract version");
        require(frame.encoding() == SFMTerminalRasterEncoding.RGBA8,
                "terminal raster encoding must be RGBA8");
        require(frame.origin() == SFMTerminalRasterOrigin.TOP_LEFT,
                "RGBA8 v1 coordinate origin must be top-left");
        require(frame.alphaMode() == SFMTerminalRasterAlphaMode.STRAIGHT,
                "RGBA8 v1 alpha must be straight");
        require(frame.colorSpace() == SFMTerminalRasterColorSpace.SRGB,
                "RGBA8 v1 color space must be sRGB");
        validateGeneration(frame.generation());
        require(frame.frameSequence() > 0, "frame sequence must be positive");
        require(frame.baseFrameSequence() >= 0, "base frame sequence must not be negative");
        require(frame.width() > 0 && frame.width() <= limits.maxWidth(),
                "frame width is outside negotiated bounds");
        require(frame.height() > 0 && frame.height() <= limits.maxHeight(),
                "frame height is outside negotiated bounds");
        require(frame.payloadLength() <= limits.maxPayloadBytes(),
                "frame payload exceeds negotiated bounds");

        if (frame.kind() == SFMTerminalRasterFrameKind.FULL) {
            validateFull(frame);
        } else {
            validateDirty(frame, limits);
        }
    }

    private static void validateFull(SFMTerminalRasterFrame frame) {
        require(frame.transportId() == SFMTerminalTransportId.FULL_RAW_RGBA
                        || frame.transportId() == SFMTerminalTransportId.DIRTY_RAW_RGBA,
                "full RGBA8 frame has an incompatible transport id");
        require(frame.baseFrameSequence() == 0, "full frame base sequence must be zero");
        require(frame.regions().isEmpty(), "full frame must not contain dirty regions");
        if (frame.transportId() == SFMTerminalTransportId.DIRTY_RAW_RGBA) {
            require(frame.fullResync(), "a full dirty-transport frame must be a resynchronization");
        }

        long expectedStride = checkedProduct(frame.width(), RGBA8_BYTES_PER_PIXEL, "full frame stride");
        require(frame.stride() == expectedStride, "full frame stride must equal width times four");
        long expectedLength = checkedProduct(expectedStride, frame.height(), "full frame payload length");
        require(frame.payloadLength() == expectedLength, "full frame payload length does not match dimensions");
    }

    private static void validateDirty(
            SFMTerminalRasterFrame frame,
            SFMTerminalRasterLimits limits) {
        require(frame.kind() == SFMTerminalRasterFrameKind.DIRTY_REGIONS,
                "unknown terminal raster frame kind");
        require(frame.transportId() == SFMTerminalTransportId.DIRTY_RAW_RGBA,
                "dirty regions require the dirty-raw-rgba transport");
        require(!frame.fullResync(), "dirty regions cannot be marked as a full resynchronization");
        require(frame.stride() == 0, "dirty frame-level stride must be zero");
        require(frame.baseFrameSequence() > 0, "dirty frame requires a positive base sequence");
        require(frame.baseFrameSequence() < frame.frameSequence(),
                "dirty frame base sequence must precede its frame sequence");

        List<SFMTerminalRasterRegion> regions = frame.regions();
        require(!regions.isEmpty(), "dirty frame must contain at least one region");
        require(regions.size() <= limits.maxRegions(), "dirty frame exceeds negotiated region count");

        long expectedPackedOffset = 0;
        SFMTerminalRasterRegion previous = null;
        for (SFMTerminalRasterRegion region : regions) {
            Objects.requireNonNull(region, "dirty region");
            validateRegionBounds(frame, region);

            long expectedStride = checkedProduct(region.width(), RGBA8_BYTES_PER_PIXEL, "dirty region stride");
            require(region.stride() == expectedStride,
                    "dirty region stride must equal region width times four");
            long expectedLength = checkedProduct(expectedStride, region.height(), "dirty region payload length");
            require(region.payloadLength() == expectedLength,
                    "dirty region payload length does not match dimensions");
            require(region.payloadOffset() >= 0, "dirty region payload offset must not be negative");
            require(region.payloadLength() >= 0, "dirty region payload length must not be negative");
            long packedEnd = checkedSum(
                    region.payloadOffset(),
                    region.payloadLength(),
                    "dirty region payload range");
            require(region.payloadOffset() == expectedPackedOffset,
                    "dirty region payload ranges must be ordered and contiguous");
            require(packedEnd <= frame.payloadLength(), "dirty region payload range exceeds frame payload");

            if (previous != null) {
                require(isAfter(previous, region),
                        "dirty regions must be ordered top-to-bottom then left-to-right");
            }
            expectedPackedOffset = packedEnd;
            previous = region;
        }

        require(expectedPackedOffset == frame.payloadLength(),
                "dirty region payload ranges must consume the complete packed payload");
        validateNoGeometricOverlap(regions);
    }

    private static void validateRegionBounds(
            SFMTerminalRasterFrame frame,
            SFMTerminalRasterRegion region) {
        require(region.x() >= 0 && region.y() >= 0, "dirty region origin must not be negative");
        require(region.width() > 0 && region.height() > 0, "dirty region dimensions must be positive");
        long right = checkedSum(region.x(), region.width(), "dirty region right edge");
        long bottom = checkedSum(region.y(), region.height(), "dirty region bottom edge");
        require(right <= frame.width() && bottom <= frame.height(),
                "dirty region lies outside the frame bounds");
    }

    private static boolean isAfter(
            SFMTerminalRasterRegion previous,
            SFMTerminalRasterRegion current) {
        return current.y() > previous.y()
                || current.y() == previous.y() && current.x() > previous.x();
    }

    private static void validateNoGeometricOverlap(List<SFMTerminalRasterRegion> regions) {
        for (int leftIndex = 0; leftIndex < regions.size(); leftIndex++) {
            SFMTerminalRasterRegion left = regions.get(leftIndex);
            long leftRight = checkedSum(left.x(), left.width(), "dirty region right edge");
            long leftBottom = checkedSum(left.y(), left.height(), "dirty region bottom edge");
            for (int rightIndex = leftIndex + 1; rightIndex < regions.size(); rightIndex++) {
                SFMTerminalRasterRegion right = regions.get(rightIndex);
                long rightRight = checkedSum(right.x(), right.width(), "dirty region right edge");
                long rightBottom = checkedSum(right.y(), right.height(), "dirty region bottom edge");
                boolean overlaps = left.x() < rightRight
                        && right.x() < leftRight
                        && left.y() < rightBottom
                        && right.y() < leftBottom;
                require(!overlaps, "dirty regions must not overlap");
            }
        }
    }

    static long checkedProduct(long left, long right, String description) {
        try {
            return Math.multiplyExact(left, right);
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException(description + " overflowed", exception);
        }
    }

    static long checkedSum(long left, long right, String description) {
        try {
            return Math.addExact(left, right);
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException(description + " overflowed", exception);
        }
    }

    static void validateGeneration(String generation) {
        require(generation != null && !generation.isBlank(),
                "transport generation must not be blank");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }
}
