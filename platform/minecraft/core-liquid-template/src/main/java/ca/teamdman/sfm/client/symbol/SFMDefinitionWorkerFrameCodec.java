package ca.teamdman.sfm.client.symbol;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/** Bounded {@code u32-le length + UTF-8 JSON} worker framing. */
public final class SFMDefinitionWorkerFrameCodec {
    public static final int DEFAULT_MAXIMUM_FRAME_BYTES = 16 * 1024 * 1024;

    private SFMDefinitionWorkerFrameCodec() {
    }

    /** Empty is clean EOF before a prefix; truncated prefix/payload is an error. */
    public static Optional<String> read(InputStream input, int maximumFrameBytes) throws IOException {
        requireMaximum(maximumFrameBytes);
        byte[] prefix = new byte[Integer.BYTES];
        int first = input.read();
        if (first < 0) return Optional.empty();
        prefix[0] = (byte) first;
        readExactly(input, prefix, 1, prefix.length - 1, "frame length");
        long length = Integer.toUnsignedLong(ByteBuffer.wrap(prefix)
                .order(ByteOrder.LITTLE_ENDIAN)
                .getInt());
        if (length == 0) throw new IOException("Definition worker frame is empty");
        if (length > maximumFrameBytes) {
            throw new IOException("Definition worker frame exceeds " + maximumFrameBytes + " bytes: " + length);
        }
        byte[] payload = new byte[(int) length];
        readExactly(input, payload, 0, payload.length, "frame payload");
        try {
            CharBuffer decoded = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(payload));
            return Optional.of(decoded.toString());
        } catch (CharacterCodingException failure) {
            throw new IOException("Definition worker frame is not valid UTF-8", failure);
        }
    }

    public static void write(OutputStream output, String json, int maximumFrameBytes) throws IOException {
        requireMaximum(maximumFrameBytes);
        if (json == null || json.isEmpty()) throw new IllegalArgumentException("Worker JSON must not be empty");
        byte[] payload;
        try {
            ByteBuffer encoded = StandardCharsets.UTF_8.newEncoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .encode(CharBuffer.wrap(json));
            payload = new byte[encoded.remaining()];
            encoded.get(payload);
        } catch (CharacterCodingException failure) {
            throw new IOException("Definition worker frame contains malformed Unicode", failure);
        }
        if (payload.length > maximumFrameBytes) {
            throw new IOException("Definition worker frame exceeds " + maximumFrameBytes + " bytes: " + payload.length);
        }
        byte[] prefix = ByteBuffer.allocate(Integer.BYTES)
                .order(ByteOrder.LITTLE_ENDIAN)
                .putInt(payload.length)
                .array();
        output.write(prefix);
        output.write(payload);
        output.flush();
    }

    private static void readExactly(
            InputStream input,
            byte[] target,
            int offset,
            int length,
            String label
    ) throws IOException {
        int read = 0;
        while (read < length) {
            int count = input.read(target, offset + read, length - read);
            if (count < 0) throw new EOFException("Definition worker closed during " + label);
            if (count == 0) continue;
            read += count;
        }
    }

    private static void requireMaximum(int maximumFrameBytes) {
        if (maximumFrameBytes <= 0) throw new IllegalArgumentException("maximumFrameBytes must be positive");
    }
}
