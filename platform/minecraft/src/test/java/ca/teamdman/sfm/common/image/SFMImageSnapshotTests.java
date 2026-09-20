package ca.teamdman.sfm.common.image;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.zip.CRC32;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SFMImageSnapshotTests {
    @Test
    void acceptedImageHasBoundedDimensionsAndExactByteIdentity() throws Exception {
        byte[] encoded = png(2, 3, 0xff336699);
        byte[] original = encoded.clone();
        SFMImageSnapshot snapshot = SFMImageSnapshot.fromPng(encoded);

        assertEquals(2, snapshot.width());
        assertEquals(3, snapshot.height());
        assertEquals(encoded.length, snapshot.byteLength());
        assertEquals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(original)),
                snapshot.sha256());
        assertEquals(snapshot, SFMImageSnapshot.fromPng(original));
        assertEquals(snapshot.hashCode(), SFMImageSnapshot.fromPng(original).hashCode());
        assertNotEquals(snapshot, SFMImageSnapshot.fromPng(png(2, 3, 0xff996633)));

        encoded[0] = 0;
        assertArrayEquals(original, snapshot.pngBytes());
        byte[] returned = snapshot.pngBytes();
        returned[0] = 0;
        assertArrayEquals(original, snapshot.pngBytes());
        assertFalse(snapshot.toString().contains(Arrays.toString(original)));
    }

    @Test
    void persistedSnapshotChecksVersionDigestDimensionsAndPayload() throws Exception {
        SFMImageSnapshot snapshot = SFMImageSnapshot.fromPng(png(2, 3, 0xff336699));
        CompoundTag tag = SFMImageSnapshotCodec.toTag(snapshot);
        assertEquals(snapshot, SFMImageSnapshotCodec.fromTag(tag).orElseThrow());

        CompoundTag wrongVersion = tag.copy();
        wrongVersion.putInt("version", SFMImageSnapshotCodec.VERSION + 1);
        assertTrue(SFMImageSnapshotCodec.fromTag(wrongVersion).isEmpty());

        CompoundTag wrongDigest = tag.copy();
        wrongDigest.putString("sha256", "not-the-payload-digest");
        assertTrue(SFMImageSnapshotCodec.fromTag(wrongDigest).isEmpty());

        CompoundTag wrongDimensions = tag.copy();
        wrongDimensions.putInt("width", snapshot.width() + 1);
        assertTrue(SFMImageSnapshotCodec.fromTag(wrongDimensions).isEmpty());

        CompoundTag wrongType = tag.copy();
        wrongType.putString("png", "not-a-byte-array");
        assertTrue(SFMImageSnapshotCodec.fromTag(wrongType).isEmpty());

        CompoundTag tooLarge = tag.copy();
        tooLarge.putByteArray("png", new byte[SFMImageSnapshot.MAX_ENCODED_BYTES + 1]);
        assertTrue(SFMImageSnapshotCodec.fromTag(tooLarge).isEmpty());
        assertTrue(SFMImageSnapshotCodec.fromTag(null).isEmpty());
        assertTrue(SFMImageSnapshotCodec.fromTag(new CompoundTag()).isEmpty());
    }

    @Test
    void invalidSignatureTruncationAndCrcNeverBecomeSnapshots() throws Exception {
        byte[] valid = png(2, 3, 0xff336699);
        byte[] wrongSignature = valid.clone();
        wrongSignature[0] = 0;
        assertThrows(IllegalArgumentException.class, () -> SFMImageSnapshot.fromPng(wrongSignature));

        byte[] truncated = Arrays.copyOf(valid, valid.length - 1);
        assertThrows(IllegalArgumentException.class, () -> SFMImageSnapshot.fromPng(truncated));

        byte[] alteredCrc = valid.clone();
        alteredCrc[20] ^= 1;
        assertThrows(IllegalArgumentException.class, () -> SFMImageSnapshot.fromPng(alteredCrc));

        byte[] trailingBytes = Arrays.copyOf(valid, valid.length + 1);
        assertThrows(IllegalArgumentException.class, () -> SFMImageSnapshot.fromPng(trailingBytes));
    }

    @Test
    void oversizedEncodedAndDeclaredPixelBudgetsRejectBeforeDecode() throws Exception {
        byte[] valid = png(2, 3, 0xff336699);
        assertThrows(IllegalArgumentException.class,
                () -> SFMImageSnapshot.fromPng(new byte[SFMImageSnapshot.MAX_ENCODED_BYTES + 1]));
        assertThrows(IllegalArgumentException.class,
                () -> SFMImageSnapshot.fromPng(withIhdrDimension(valid, 0, 3)));
        assertThrows(IllegalArgumentException.class,
                () -> SFMImageSnapshot.fromPng(withIhdrDimension(valid, 513, 3)));
        assertThrows(IllegalArgumentException.class,
                () -> SFMImageSnapshot.fromPng(withIhdrDimension(valid, 2, 513)));
    }

    @Test
    void malformedImageDataFailsEvenWithAValidChunkChecksum() throws Exception {
        byte[] encoded = png(2, 3, 0xff336699);
        int idatOffset = 33;
        assertEquals(0x49444154, readInt(encoded, idatOffset + 4));
        byte[] broken = encoded.clone();
        broken[idatOffset + 8] = 0;
        updateCrc(broken, idatOffset);
        assertThrows(IllegalArgumentException.class, () -> SFMImageSnapshot.fromPng(broken));
    }

    @Test
    void unknownMetadataAndAnimationChunksAreOutsideSnapshotFormat() throws Exception {
        byte[] valid = png(2, 3, 0xff336699);
        byte[] withText = insertChunkBeforeIend(valid, "tEXt", new byte[]{'x', 0, 'y'});
        byte[] withAnimation = insertChunkBeforeIend(valid, "acTL", new byte[8]);
        assertThrows(IllegalArgumentException.class, () -> SFMImageSnapshot.fromPng(withText));
        assertThrows(IllegalArgumentException.class, () -> SFMImageSnapshot.fromPng(withAnimation));
    }

    private static byte[] png(int width, int height, int argb) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                image.setRGB(x, y, argb);
            }
        }
        ByteArrayOutputStream encoded = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(image, "png", encoded));
        return encoded.toByteArray();
    }

    private static byte[] withIhdrDimension(byte[] source, int width, int height) {
        byte[] altered = source.clone();
        writeInt(altered, 16, width);
        writeInt(altered, 20, height);
        updateCrc(altered, 8);
        return altered;
    }

    private static byte[] insertChunkBeforeIend(byte[] source, String type, byte[] data) {
        int iendOffset = source.length - 12;
        byte[] result = new byte[source.length + 12 + data.length];
        System.arraycopy(source, 0, result, 0, iendOffset);
        writeInt(result, iendOffset, data.length);
        for (int i = 0; i < 4; i++) {
            result[iendOffset + 4 + i] = (byte) type.charAt(i);
        }
        System.arraycopy(data, 0, result, iendOffset + 8, data.length);
        updateCrc(result, iendOffset);
        System.arraycopy(source, iendOffset, result, iendOffset + 12 + data.length, 12);
        return result;
    }

    private static void updateCrc(byte[] bytes, int chunkOffset) {
        int length = readInt(bytes, chunkOffset);
        CRC32 crc = new CRC32();
        crc.update(bytes, chunkOffset + 4, length + 4);
        writeInt(bytes, chunkOffset + 8 + length, (int) crc.getValue());
    }

    private static int readInt(byte[] bytes, int offset) {
        return (Byte.toUnsignedInt(bytes[offset]) << 24)
                | (Byte.toUnsignedInt(bytes[offset + 1]) << 16)
                | (Byte.toUnsignedInt(bytes[offset + 2]) << 8)
                | Byte.toUnsignedInt(bytes[offset + 3]);
    }

    private static void writeInt(byte[] bytes, int offset, int value) {
        bytes[offset] = (byte) (value >>> 24);
        bytes[offset + 1] = (byte) (value >>> 16);
        bytes[offset + 2] = (byte) (value >>> 8);
        bytes[offset + 3] = (byte) value;
    }
}
