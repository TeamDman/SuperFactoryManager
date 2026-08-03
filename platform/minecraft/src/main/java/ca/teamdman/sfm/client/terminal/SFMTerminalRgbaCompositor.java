package ca.teamdman.sfm.client.terminal;

import java.util.Arrays;
import java.util.Objects;

/**
 * Pure retained heap RGBA8 compositor with no Minecraft renderer or GL dependency.
 * A requested generation may be changed while the last accepted pixels remain
 * visible; frames from every other generation are classified as stale.
 */
public final class SFMTerminalRgbaCompositor {
    public enum ApplyResult {
        APPLIED,
        STALE_GENERATION
    }

    private final SFMTerminalRasterLimits limits;
    private boolean initialized;
    private int width;
    private int height;
    private String activeGeneration = "";
    private String expectedGeneration = "";
    private long lastFrameSequence;
    private byte[] pixels = new byte[0];

    public SFMTerminalRgbaCompositor(SFMTerminalRasterLimits limits) {
        this.limits = Objects.requireNonNull(limits, "limits");
    }

    /**
     * Select the only generation whose subsequent frames may be applied.
     * Existing pixels and active sequence remain intact until a complete v1
     * resynchronization for the expected generation is accepted.
     */
    public void expectGeneration(String generation) {
        SFMTerminalRasterFrameValidator.validateGeneration(generation);
        expectedGeneration = generation;
    }

    public ApplyResult apply(SFMTerminalRasterFrame frame) {
        Objects.requireNonNull(frame, "frame");
        SFMTerminalRasterFrameValidator.validateGeneration(frame.generation());
        if (!expectedGeneration.isEmpty() && !expectedGeneration.equals(frame.generation())) {
            return ApplyResult.STALE_GENERATION;
        }

        SFMTerminalRasterFrameValidator.validateRgba8(frame, limits);
        if (frame.kind() == SFMTerminalRasterFrameKind.FULL) {
            applyFull(frame);
        } else {
            applyDirty(frame);
        }

        if (expectedGeneration.isEmpty()) {
            expectedGeneration = frame.generation();
        }
        return ApplyResult.APPLIED;
    }

    private void applyFull(SFMTerminalRasterFrame frame) {
        boolean generationChanged = initialized && !activeGeneration.equals(frame.generation());
        if (!initialized || generationChanged) {
            require(frame.fullResync(), "a new transport generation must begin with a full resynchronization");
        } else {
            require(frame.frameSequence() > lastFrameSequence,
                    "full frame sequence must advance within its transport generation");
            boolean dimensionsChanged = frame.width() != width || frame.height() != height;
            require(!dimensionsChanged || frame.fullResync(),
                    "dimension changes require a full resynchronization");
        }

        byte[] replacement = frame.payload();
        width = frame.width();
        height = frame.height();
        activeGeneration = frame.generation();
        lastFrameSequence = frame.frameSequence();
        pixels = replacement;
        initialized = true;
    }

    private void applyDirty(SFMTerminalRasterFrame frame) {
        require(initialized, "dirty frame requires a composed full base frame");
        require(activeGeneration.equals(frame.generation()),
                "dirty frame transport generation does not match the composed base");
        require(width == frame.width() && height == frame.height(), "dirty frame dimensions do not match its base");
        require(frame.baseFrameSequence() == lastFrameSequence,
                "dirty frame base sequence does not match the currently composed frame");
        require(frame.frameSequence() > lastFrameSequence,
                "dirty frame sequence must advance within its transport generation");

        byte[] packedPayload = frame.payload();
        byte[] replacement = Arrays.copyOf(pixels, pixels.length);
        for (SFMTerminalRasterRegion region : frame.regions()) {
            int rowLength = Math.toIntExact(SFMTerminalRasterFrameValidator.checkedProduct(
                    region.width(),
                    SFMTerminalRasterFrameValidator.RGBA8_BYTES_PER_PIXEL,
                    "dirty compositor row length"));
            for (int row = 0; row < region.height(); row++) {
                int sourceOffset = Math.toIntExact(SFMTerminalRasterFrameValidator.checkedSum(
                        region.payloadOffset(),
                        SFMTerminalRasterFrameValidator.checkedProduct(
                                row,
                                region.stride(),
                                "dirty compositor source row"),
                        "dirty compositor source offset"));
                long destinationPixel = SFMTerminalRasterFrameValidator.checkedSum(
                        SFMTerminalRasterFrameValidator.checkedProduct(
                                region.y() + (long) row,
                                width,
                                "dirty compositor destination row"),
                        region.x(),
                        "dirty compositor destination pixel");
                int destinationOffset = Math.toIntExact(SFMTerminalRasterFrameValidator.checkedProduct(
                        destinationPixel,
                        SFMTerminalRasterFrameValidator.RGBA8_BYTES_PER_PIXEL,
                        "dirty compositor destination offset"));
                System.arraycopy(packedPayload, sourceOffset, replacement, destinationOffset, rowLength);
            }
        }

        pixels = replacement;
        lastFrameSequence = frame.frameSequence();
    }

    public boolean initialized() {
        return initialized;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public String generation() {
        return activeGeneration;
    }

    public String expectedGeneration() {
        return expectedGeneration;
    }

    public long lastFrameSequence() {
        return lastFrameSequence;
    }

    public byte[] pixels() {
        return Arrays.copyOf(pixels, pixels.length);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }
}
