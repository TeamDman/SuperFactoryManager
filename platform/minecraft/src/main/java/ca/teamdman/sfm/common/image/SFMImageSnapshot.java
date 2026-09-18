package ca.teamdman.sfm.common.image;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.Objects;
import java.util.zip.CRC32;

/**
 * An immutable, content-addressed still image for ordinary SFM resource movement.
 * Live terminal frames have a separate path with different limits.
 */
public final class SFMImageSnapshot {
    public static final int MAX_ENCODED_BYTES = 64 * 1024;
    public static final int MAX_WIDTH = 512;
    public static final int MAX_HEIGHT = 512;
    public static final int MAX_PIXELS = 512 * 512;

    private static final byte[] PNG_SIGNATURE = {
            (byte) 137, 80, 78, 71, 13, 10, 26, 10
    };
    private static final int IHDR = 0x49484452;
    private static final int PLTE = 0x504c5445;
    private static final int TRNS = 0x74524e53;
    private static final int IDAT = 0x49444154;
    private static final int IEND = 0x49454e44;

    private final byte[] pngBytes;
    private final int width;
    private final int height;
    private final String sha256;
    private final int hashCode;

    private SFMImageSnapshot(byte[] pngBytes, int width, int height) {
        this.pngBytes = pngBytes.clone();
        this.width = width;
        this.height = height;
        this.sha256 = sha256(pngBytes);
        this.hashCode = Arrays.hashCode(pngBytes);
    }

    /**
     * Accepts a bounded, independently decodable PNG. Only the image chunks
     * IHDR/PLTE/tRNS/IDAT/IEND are accepted; text, profiles and animation
     * metadata are not part of the snapshot format.
     *
     * @throws IllegalArgumentException if the bytes are not a supported image
     */
    public static SFMImageSnapshot fromPng(byte[] pngBytes) {
        Objects.requireNonNull(pngBytes, "pngBytes");
        if (pngBytes.length > MAX_ENCODED_BYTES) {
            throw new IllegalArgumentException("PNG exceeds the encoded-byte limit");
        }
        Dimensions dimensions = validatePngChunks(pngBytes);
        validateDecodable(pngBytes, dimensions);
        return new SFMImageSnapshot(pngBytes, dimensions.width(), dimensions.height());
    }

    public byte[] pngBytes() {
        return pngBytes.clone();
    }

    public int byteLength() {
        return pngBytes.length;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    /** Lowercase SHA-256 of the exact encoded PNG bytes, not decoded pixels. */
    public String sha256() {
        return sha256;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof SFMImageSnapshot snapshot
                && Arrays.equals(pngBytes, snapshot.pngBytes);
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    @Override
    public String toString() {
        return "SFMImageSnapshot{" + width + "x" + height + ", bytes=" + pngBytes.length
                + ", sha256=" + sha256 + "}";
    }

    private static Dimensions validatePngChunks(byte[] bytes) {
        if (bytes.length < PNG_SIGNATURE.length + 12 + 13 + 12 + 12) {
            throw new IllegalArgumentException("PNG is too short");
        }
        for (int i = 0; i < PNG_SIGNATURE.length; i++) {
            if (bytes[i] != PNG_SIGNATURE[i]) {
                throw new IllegalArgumentException("Invalid PNG signature");
            }
        }

        int offset = PNG_SIGNATURE.length;
        boolean seenHeader = false;
        boolean seenPalette = false;
        boolean seenTransparency = false;
        boolean seenData = false;
        boolean endedData = false;
        boolean seenEnd = false;
        int colorType = -1;
        int bitDepth = -1;
        int paletteEntries = 0;
        Dimensions dimensions = null;

        while (offset < bytes.length) {
            if (bytes.length - offset < 12) {
                throw new IllegalArgumentException("Truncated PNG chunk");
            }
            long length = Integer.toUnsignedLong(readInt(bytes, offset));
            if (length > bytes.length - offset - 12L) {
                throw new IllegalArgumentException("PNG chunk length exceeds the input");
            }
            int type = readInt(bytes, offset + 4);
            int dataOffset = offset + 8;
            int chunkEnd = (int) (dataOffset + length);
            CRC32 crc = new CRC32();
            crc.update(bytes, offset + 4, (int) length + 4);
            if ((int) crc.getValue() != readInt(bytes, chunkEnd)) {
                throw new IllegalArgumentException("PNG chunk checksum mismatch");
            }

            if (!seenHeader && type != IHDR) {
                throw new IllegalArgumentException("PNG must begin with IHDR");
            }
            if (seenData && type != IDAT) {
                endedData = true;
            }
            switch (type) {
                case IHDR -> {
                    if (seenHeader || length != 13) {
                        throw new IllegalArgumentException("Invalid PNG IHDR");
                    }
                    int width = readInt(bytes, dataOffset);
                    int height = readInt(bytes, dataOffset + 4);
                    if (width <= 0 || width > MAX_WIDTH || height <= 0 || height > MAX_HEIGHT
                            || (long) width * height > MAX_PIXELS) {
                        throw new IllegalArgumentException("PNG dimensions exceed the image limit");
                    }
                    bitDepth = Byte.toUnsignedInt(bytes[dataOffset + 8]);
                    colorType = Byte.toUnsignedInt(bytes[dataOffset + 9]);
                    if (!supportedColorDepth(colorType, bitDepth)
                            || bytes[dataOffset + 10] != 0
                            || bytes[dataOffset + 11] != 0
                            || (bytes[dataOffset + 12] != 0 && bytes[dataOffset + 12] != 1)) {
                        throw new IllegalArgumentException("Unsupported PNG image format");
                    }
                    dimensions = new Dimensions(width, height);
                    seenHeader = true;
                }
                case PLTE -> {
                    if (seenPalette || seenData || (colorType != 2 && colorType != 3 && colorType != 6)
                            || length == 0 || length > 768 || length % 3 != 0) {
                        throw new IllegalArgumentException("Invalid PNG palette");
                    }
                    paletteEntries = (int) length / 3;
                    if (colorType == 3 && paletteEntries > (1 << bitDepth)) {
                        throw new IllegalArgumentException("PNG palette exceeds bit depth");
                    }
                    seenPalette = true;
                }
                case TRNS -> {
                    if (seenTransparency || seenData || (colorType != 0 && colorType != 2 && colorType != 3)
                            || (colorType == 0 && length != 2)
                            || (colorType == 2 && length != 6)
                            || (colorType == 3 && (!seenPalette || length == 0 || length > paletteEntries))) {
                        throw new IllegalArgumentException("Invalid PNG transparency chunk");
                    }
                    seenTransparency = true;
                }
                case IDAT -> {
                    if (endedData || (colorType == 3 && !seenPalette)) {
                        throw new IllegalArgumentException("Invalid PNG image-data ordering");
                    }
                    seenData = true;
                }
                case IEND -> {
                    if (!seenData || seenEnd || length != 0 || chunkEnd + 4 != bytes.length) {
                        throw new IllegalArgumentException("Invalid PNG end chunk");
                    }
                    seenEnd = true;
                }
                default -> throw new IllegalArgumentException("Unsupported PNG metadata or chunk type");
            }
            offset = chunkEnd + 4;
        }
        if (!seenEnd || dimensions == null) {
            throw new IllegalArgumentException("PNG is incomplete");
        }
        return dimensions;
    }

    private static boolean supportedColorDepth(int colorType, int depth) {
        return switch (colorType) {
            case 0 -> depth == 1 || depth == 2 || depth == 4 || depth == 8 || depth == 16;
            case 2, 4, 6 -> depth == 8 || depth == 16;
            case 3 -> depth == 1 || depth == 2 || depth == 4 || depth == 8;
            default -> false;
        };
    }

    private static void validateDecodable(byte[] bytes, Dimensions expected) {
        Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("png");
        if (!readers.hasNext()) {
            throw new IllegalStateException("No PNG decoder is available");
        }
        ImageReader reader = readers.next();
        try (MemoryCacheImageInputStream input = new MemoryCacheImageInputStream(
                new ByteArrayInputStream(bytes))) {
            reader.setInput(input, true, true);
            BufferedImage decoded = reader.read(0);
            if (decoded == null || decoded.getWidth() != expected.width()
                    || decoded.getHeight() != expected.height()) {
                throw new IllegalArgumentException("Decoded PNG dimensions do not match IHDR");
            }
        } catch (IOException | RuntimeException ex) {
            if (ex instanceof IllegalArgumentException invalid) {
                throw invalid;
            }
            throw new IllegalArgumentException("PNG could not be decoded", ex);
        } finally {
            reader.dispose();
        }
    }

    private static int readInt(byte[] bytes, int offset) {
        return (Byte.toUnsignedInt(bytes[offset]) << 24)
                | (Byte.toUnsignedInt(bytes[offset + 1]) << 16)
                | (Byte.toUnsignedInt(bytes[offset + 2]) << 8)
                | Byte.toUnsignedInt(bytes[offset + 3]);
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    private record Dimensions(int width, int height) {}
}
