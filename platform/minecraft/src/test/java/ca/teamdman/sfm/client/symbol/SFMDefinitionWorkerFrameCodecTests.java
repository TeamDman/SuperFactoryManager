package ca.teamdman.sfm.client.symbol;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SFMDefinitionWorkerFrameCodecTests {
    @Test
    void fragmentedAndCoalescedFramesPreserveUnicodeNewlinesAndNul() throws IOException {
        String first = "{\"source\":\"line 1\\nline 2 🦀 \\u0000\"}";
        String second = "{\"kind\":\"ping\"}";
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        SFMDefinitionWorkerFrameCodec.write(bytes, first, 1024);
        SFMDefinitionWorkerFrameCodec.write(bytes, second, 1024);

        InputStream fragmented = new FragmentedInputStream(bytes.toByteArray(), 1);
        assertEquals(first, SFMDefinitionWorkerFrameCodec.read(fragmented, 1024).orElseThrow());
        assertEquals(second, SFMDefinitionWorkerFrameCodec.read(fragmented, 1024).orElseThrow());
        assertTrue(SFMDefinitionWorkerFrameCodec.read(fragmented, 1024).isEmpty());
    }

    @Test
    void cleanEofDiffersFromTruncatedPrefixOrPayload() throws IOException {
        assertEquals(Optional.empty(), SFMDefinitionWorkerFrameCodec.read(
                new ByteArrayInputStream(new byte[0]), 64));
        assertThrows(EOFException.class, () -> SFMDefinitionWorkerFrameCodec.read(
                new ByteArrayInputStream(new byte[]{1, 0}), 64));
        assertThrows(EOFException.class, () -> SFMDefinitionWorkerFrameCodec.read(
                new ByteArrayInputStream(new byte[]{2, 0, 0, 0, '{'}), 64));
    }

    @Test
    void oversizedLengthFailsBeforePayloadAllocationOrRead() {
        byte[] prefix = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(65).array();
        CountingInputStream input = new CountingInputStream(prefix);

        IOException failure = assertThrows(IOException.class,
                () -> SFMDefinitionWorkerFrameCodec.read(input, 64));
        assertTrue(failure.getMessage().contains("exceeds"));
        assertEquals(4, input.bytesRead);
    }

    @Test
    void malformedUtf8AndEmptyFramesFailClosed() {
        assertThrows(IOException.class, () -> SFMDefinitionWorkerFrameCodec.read(
                new ByteArrayInputStream(new byte[]{1, 0, 0, 0, (byte) 0xFF}), 64));
        assertThrows(IOException.class, () -> SFMDefinitionWorkerFrameCodec.read(
                new ByteArrayInputStream(new byte[]{0, 0, 0, 0}), 64));
        assertThrows(IllegalArgumentException.class,
                () -> SFMDefinitionWorkerFrameCodec.write(new ByteArrayOutputStream(), "", 64));
    }

    private static final class FragmentedInputStream extends ByteArrayInputStream {
        private final int maximumChunk;
        private FragmentedInputStream(byte[] bytes, int maximumChunk) {
            super(bytes);
            this.maximumChunk = maximumChunk;
        }
        @Override public synchronized int read(byte[] bytes, int offset, int length) {
            return super.read(bytes, offset, Math.min(length, maximumChunk));
        }
    }

    private static final class CountingInputStream extends ByteArrayInputStream {
        private int bytesRead;
        private CountingInputStream(byte[] bytes) { super(bytes); }
        @Override public synchronized int read() {
            int value = super.read();
            if (value >= 0) bytesRead++;
            return value;
        }
        @Override public synchronized int read(byte[] bytes, int offset, int length) {
            int count = super.read(bytes, offset, length);
            if (count > 0) bytesRead += count;
            return count;
        }
    }
}
