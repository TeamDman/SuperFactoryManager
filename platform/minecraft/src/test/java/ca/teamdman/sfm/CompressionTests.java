package ca.teamdman.sfm;

import ca.teamdman.sfm.common.util.CompressedBlockPosSet;
import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.*;

public class CompressionTests {
    private static final int DEFAULT_NBT_QUOTA = 2097152; // 2 MB

    public static void assertTagSizeOkay(Tag tag) {
        CompoundTag compoundTag = new CompoundTag();
        compoundTag.put(
                "sfm:cable_positions",
                tag
        );
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeNbt(compoundTag);
        System.out.printf("Wrote %d bytes to buf\n", buf.readableBytes());
        assertTrue(buf.readableBytes() < DEFAULT_NBT_QUOTA);
        buf.readNbt();
    }

    @MCVersionDependentBehaviour // In 1.21.0, writeBlockPos uses an intarray instead of compound tag
    private static CompoundTag writeBlockPosVersionAgnostic(BlockPos pos) {
        CompoundTag compoundtag = new CompoundTag();
        compoundtag.putInt("X", pos.getX());
        compoundtag.putInt("Y", pos.getY());
        compoundtag.putInt("Z", pos.getZ());
        return compoundtag;
    }

    @Test
    public void compound_list() {
        Set<BlockPos> positions = new HashSet<>();
        BlockPos first = new BlockPos(14, -58, 22);
        BlockPos second = new BlockPos(32, -25, 3);
        BlockPos.betweenClosedStream(first, second).map(BlockPos::immutable).forEach(positions::add);
        System.out.println("There are " + positions.size() + " positions");

        ListTag tag = positions
                .stream()
                .map(CompressionTests::writeBlockPosVersionAgnostic)
                .collect(ListTag::new, ListTag::add, ListTag::addAll);
        assertThrows(RuntimeException.class, ()-> assertTagSizeOkay(tag));
    }

    @Test
    public void long_list() {
        Set<BlockPos> positions = new HashSet<>();
        BlockPos first = new BlockPos(14, -58, 22);
        BlockPos second = new BlockPos(32, -25, 3);
        BlockPos.betweenClosedStream(first, second).map(BlockPos::immutable).forEach(positions::add);

        ListTag tag = positions
                .stream()
                .map(BlockPos::asLong)
                .map(LongTag::valueOf)
                .collect(ListTag::new, ListTag::add, ListTag::addAll);
        assertTagSizeOkay(tag);
    }

    @Test
    public void long_list_big() {
        Set<BlockPos> positions = new HashSet<>();
        BlockPos first = new BlockPos(0, 0, 0);
        BlockPos second = new BlockPos(255, 1, 255);
        BlockPos.betweenClosedStream(first, second).map(BlockPos::immutable).forEach(positions::add);

        ListTag tag = positions
                .stream()
                .map(BlockPos::asLong)
                .map(LongTag::valueOf)
                .collect(ListTag::new, ListTag::add, ListTag::addAll);
        assertThrows(RuntimeException.class, ()-> assertTagSizeOkay(tag));
    }

    @Test
    public void long_array() {
        Set<BlockPos> positions = new HashSet<>();
        BlockPos first = new BlockPos(14, -58, 22);
        BlockPos second = new BlockPos(32, -25, 3);
        BlockPos.betweenClosedStream(first, second).map(BlockPos::immutable).forEach(positions::add);

        LongArrayTag tag = new LongArrayTag(
                positions
                        .stream()
                        .mapToLong(BlockPos::asLong)
                        .toArray()
        );
        assertTagSizeOkay(tag);
    }

    @Test
    public void long_array_big() {
        Set<BlockPos> positions = new HashSet<>();
        BlockPos first = new BlockPos(0, 0, 0);
        BlockPos second = new BlockPos(255, 1, 255);
        BlockPos.betweenClosedStream(first, second).map(BlockPos::immutable).forEach(positions::add);

        LongArrayTag tag = new LongArrayTag(
                positions
                        .stream()
                        .mapToLong(BlockPos::asLong)
                        .toArray()
        );
        assertTagSizeOkay(tag);
    }

    /**
     * Test method to compress large BlockPos set using GZIP and verify integrity.
     */
    @Test
    public void gzip_big() {
        try {
            Set<BlockPos> positions = new HashSet<>();
            BlockPos first = new BlockPos(0, 0, 0);
            BlockPos second = new BlockPos(255, 1, 255);
            BlockPos.betweenClosedStream(first, second).map(BlockPos::immutable).forEach(positions::add);
            assertEquals(131072, positions.size());

            // Serialize the BlockPos set to bytes
            byte[] serializedData = serializeBlockPosSet(positions);

            // Compress using GZIP
            byte[] compressedData = compressGZIP(serializedData);
            System.out.printf("GZIP Compressed %d positions to %d bytes\n", positions.size(), compressedData.length);
            assertTrue(compressedData.length < DEFAULT_NBT_QUOTA, "GZIP compressed data exceeds quota");

            // Store compressed data in NBT
            ByteArrayTag tag = new ByteArrayTag(compressedData);
            assertTagSizeOkay(tag);

            byte[] readCompressedData = tag.getAsByteArray();
            assertNotNull(readCompressedData, "Read compressed data is null");

            // Decompress
            byte[] decompressedData = decompressGZIP(readCompressedData);

            // Deserialize
            Set<BlockPos> decompressedPositions = deserializeBlockPosSet(decompressedData);

            // Verify integrity
            assertEquals(positions, decompressedPositions, "Decompressed positions do not match original");
        } catch (IOException e) {
            fail("IOException occurred: " + e.getMessage());
        }
    }

    @Test
    public void custom_big() {
        HashSet<BlockPos> positions = new HashSet<>();
        BlockPos first = new BlockPos(0, 0, 0);
        BlockPos second = new BlockPos(255, 1, 255);
        BlockPos.betweenClosedStream(first, second).map(BlockPos::immutable).forEach(positions::add);
        System.out.println("There are " + positions.size() + " positions");

        CompressedBlockPosSet compressedBlockPosSet = CompressedBlockPosSet.from(positions);
        ByteArrayTag tag = compressedBlockPosSet.asTag();
        assertTagSizeOkay(tag);

        CompressedBlockPosSet read = CompressedBlockPosSet.from(tag);
        Set<BlockPos> check = read.into();
        assertEquals(positions.size(), check.size());
        assertTrue(check.containsAll(positions));
    }

    @Test
    public void custom_massive() {
        HashSet<BlockPos> positions = new HashSet<>();
        {
            BlockPos first = new BlockPos(0, 0, 0);
            BlockPos second = new BlockPos(255, 2, 255);
            BlockPos.betweenClosedStream(first, second).map(BlockPos::immutable).forEach(positions::add);
        }
        {
            BlockPos first = new BlockPos(1000, 0, 200);
            BlockPos second = new BlockPos(1000, 64, 255);
            BlockPos.betweenClosedStream(first, second).map(BlockPos::immutable).forEach(positions::add);
        }
        {
            BlockPos first = new BlockPos(-1000, 0, 200);
            BlockPos second = new BlockPos(-1100, 64, 255);
            BlockPos.betweenClosedStream(first, second).map(BlockPos::immutable).forEach(positions::add);
        }
        for (int i = 0; i < 100; i++) {

            BlockPos first = new BlockPos(12345 + i * 500, 0, 10);
            BlockPos second = new BlockPos(12345 + i * 500, 10, 10);
            BlockPos.betweenClosedStream(first, second).map(BlockPos::immutable).forEach(positions::add);
        }
        System.out.println("There are " + positions.size() + " positions");

        CompressedBlockPosSet compressedBlockPosSet = CompressedBlockPosSet.from(positions);
        ByteArrayTag tag = compressedBlockPosSet.asTag();
        assertTagSizeOkay(tag);

        CompressedBlockPosSet read = CompressedBlockPosSet.from(tag);
        Set<BlockPos> check = read.into();
        assertEquals(positions.size(), check.size());
        assertTrue(check.containsAll(positions));
    }

    @Test
    public void custom_works() {
        HashSet<BlockPos> positions = new HashSet<>();
        positions.add(new BlockPos(0, 0, 0));
        positions.add(new BlockPos(0, 0, 1));
        positions.add(new BlockPos(0, 0, 2));
        positions.add(new BlockPos(0, 0, 10));

        CompressedBlockPosSet compressedBlockPosSet = CompressedBlockPosSet.from(positions);
        Set<BlockPos> check = compressedBlockPosSet.into();
        assertEquals(positions.size(), check.size());
        System.out.println("Positions:");
        for (BlockPos position : positions) {
            System.out.println(position);
        }
        System.out.println("Check:");
        for (BlockPos position : check) {
            System.out.println(position);
        }
        for (BlockPos position : positions) {
            assertTrue(check.contains(position), "Check does not contain " + position);
        }
    }

    /**
     * Helper method to serialize Set<BlockPos> to a byte array as longs.
     */
    private static byte[] serializeBlockPosSet(Set<BlockPos> blockPositions) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);
        for (BlockPos pos : blockPositions) {
            dos.writeLong(pos.asLong());
        }
        dos.close();
        return baos.toByteArray();
    }

    /**
     * Helper method to deserialize byte array back to Set<BlockPos>.
     */
    private Set<BlockPos> deserializeBlockPosSet(byte[] data) throws IOException {
        Set<BlockPos> blockPositions = new HashSet<>();
        ByteArrayInputStream bais = new ByteArrayInputStream(data);
        DataInputStream dis = new DataInputStream(bais);
        while (dis.available() >= 8) { // Each long is 8 bytes
            long posLong = dis.readLong();
            blockPositions.add(BlockPos.of(posLong));
        }
        dis.close();
        return blockPositions;
    }

    /**
     * Compress data using GZIP.
     */
    private byte[] compressGZIP(byte[] data) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        GZIPOutputStream gzipOS = new GZIPOutputStream(baos);
        gzipOS.write(data);
        gzipOS.close();
        return baos.toByteArray();
    }

    /**
     * Decompress data using GZIP.
     */
    private byte[] decompressGZIP(byte[] compressedData) throws IOException {
        ByteArrayInputStream bais = new ByteArrayInputStream(compressedData);
        GZIPInputStream gzipIS = new GZIPInputStream(bais);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int len;
        while ((len = gzipIS.read(buffer)) != -1) {
            baos.write(buffer, 0, len);
        }
        gzipIS.close();
        return baos.toByteArray();
    }
}
