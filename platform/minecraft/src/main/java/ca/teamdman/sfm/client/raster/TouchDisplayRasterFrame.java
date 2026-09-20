package ca.teamdman.sfm.client.raster;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Transport-neutral, top-left-origin, row-major RGBA8 raster update. Pixels
 * use sRGB colour and straight alpha. This is transient client presentation,
 * independent of the persisted {@code sfm:image} resource and semantic state.
 */
public final class TouchDisplayRasterFrame {
    public static final int MAX_DIMENSION = 512;
    public static final int MAX_IMAGE_BYTES = MAX_DIMENSION * MAX_DIMENSION * 4;

    private final long sequence;
    private final long baseSequence;
    private final int width;
    private final int height;
    private final int x;
    private final int y;
    private final int regionWidth;
    private final int regionHeight;
    private final byte[] rgba;

    private TouchDisplayRasterFrame(
            long sequence, long baseSequence, int width, int height,
            int x, int y, int regionWidth, int regionHeight, byte[] rgba
    ) {
        if (sequence < 0 || baseSequence < -1 || baseSequence >= sequence) {
            throw new IllegalArgumentException("Invalid raster sequence");
        }
        validateDimensions(width, height);
        if (x < 0 || y < 0 || regionWidth < 1 || regionHeight < 1
                || (long) x + regionWidth > width || (long) y + regionHeight > height) {
            throw new IllegalArgumentException("Raster region is outside the image");
        }
        Objects.requireNonNull(rgba, "rgba");
        if (rgba.length != regionWidth * regionHeight * 4) {
            throw new IllegalArgumentException("RGBA8 length does not match the raster region");
        }
        this.sequence = sequence;
        this.baseSequence = baseSequence;
        this.width = width;
        this.height = height;
        this.x = x;
        this.y = y;
        this.regionWidth = regionWidth;
        this.regionHeight = regionHeight;
        this.rgba = rgba.clone();
    }

    public static TouchDisplayRasterFrame full(long sequence, int width, int height, byte[] rgba) {
        return new TouchDisplayRasterFrame(sequence, -1, width, height, 0, 0, width, height, rgba);
    }

    public static TouchDisplayRasterFrame dirty(
            long sequence, long baseSequence, int width, int height,
            int x, int y, int regionWidth, int regionHeight, byte[] rgba
    ) {
        if (baseSequence < 0) throw new IllegalArgumentException("A dirty raster requires a base sequence");
        return new TouchDisplayRasterFrame(
                sequence, baseSequence, width, height, x, y, regionWidth, regionHeight, rgba
        );
    }

    public long sequence() { return sequence; }
    public long baseSequence() { return baseSequence; }
    public int width() { return width; }
    public int height() { return height; }
    public boolean full() { return baseSequence == -1; }
    public int payloadBytes() { return rgba.length; }
    public int imageBytes() { return width * height * 4; }

    Image applyTo(Image base) {
        if (full()) return new Image(width, height, rgba);
        if (base == null || base.width != width || base.height != height) {
            throw new IllegalArgumentException("Dirty raster dimensions do not match the base image");
        }
        byte[] pixels = base.rgba.clone();
        for (int row = 0; row < regionHeight; row++) {
            System.arraycopy(rgba, row * regionWidth * 4, pixels, ((y + row) * width + x) * 4, regionWidth * 4);
        }
        return new Image(width, height, pixels);
    }

    private static void validateDimensions(int width, int height) {
        if (width < 1 || width > MAX_DIMENSION || height < 1 || height > MAX_DIMENSION) {
            throw new IllegalArgumentException("Raster dimensions must be between 1 and " + MAX_DIMENSION);
        }
    }

    /** Immutable full image delivered to a renderer, regardless of ingress frame kind. */
    public static final class Image {
        private final int width;
        private final int height;
        private final byte[] rgba;
        private final String sha256;

        private Image(int width, int height, byte[] ownedRgba) {
            this.width = width;
            this.height = height;
            this.rgba = ownedRgba;
            try {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                digest.update(ByteBuffer.allocate(8).putInt(width).putInt(height).array());
                sha256 = HexFormat.of().formatHex(digest.digest(rgba));
            } catch (NoSuchAlgorithmException e) {
                throw new IllegalStateException("SHA-256 is required by the Java runtime", e);
            }
        }

        public int width() { return width; }
        public int height() { return height; }
        public int byteSize() { return rgba.length; }
        public String sha256() { return sha256; }
        public ByteBuffer rgba() { return ByteBuffer.wrap(rgba).asReadOnlyBuffer(); }

        boolean samePixels(Image other) {
            return other != null && width == other.width && height == other.height && Arrays.equals(rgba, other.rgba);
        }
    }
}
