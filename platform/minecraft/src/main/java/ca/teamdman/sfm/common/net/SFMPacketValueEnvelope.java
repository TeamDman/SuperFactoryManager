package ca.teamdman.sfm.common.net;

import ca.teamdman.sfm.common.value.SFMValue;
import ca.teamdman.sfm.common.value.SFMValueJsonCodec;
import net.minecraft.network.FriendlyByteBuf;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;

/** A bounded, versioned wire envelope for an {@link SFMValue}. */
public record SFMPacketValueEnvelope(
        int codecVersion,
        String canonicalJson
) {
    public SFMPacketValueEnvelope {
        if (codecVersion < 0) {
            throw new IllegalArgumentException("Packet value codec version cannot be negative");
        }
        Objects.requireNonNull(canonicalJson, "canonicalJson");
        byte[] encoded = encodeUtf8(canonicalJson);
        if (encoded.length > SFMValueJsonCodec.MAX_ENCODED_UTF8_BYTES) {
            throw new IllegalArgumentException(
                    "Packet value JSON exceeds "
                    + SFMValueJsonCodec.MAX_ENCODED_UTF8_BYTES
                    + " UTF-8 bytes"
            );
        }
        if (SFMValueJsonCodec.isReadableVersion(codecVersion)) {
            SFMValue decoded = SFMValueJsonCodec.decode(canonicalJson, codecVersion);
            if (!canonicalJson.equals(SFMValueJsonCodec.encode(decoded))) {
                throw new IllegalArgumentException("Packet value JSON is not canonical");
            }
        }
    }

    public static SFMPacketValueEnvelope fromValue(SFMValue value) {
        return new SFMPacketValueEnvelope(
                SFMValueJsonCodec.VERSION,
                SFMValueJsonCodec.encode(value)
        );
    }

    /** Returns a value from any readable codec version; unknown future versions remain opaque. */
    public Optional<SFMValue> currentValue() {
        if (!SFMValueJsonCodec.isReadableVersion(codecVersion)) {
            return Optional.empty();
        }
        return Optional.of(SFMValueJsonCodec.decode(canonicalJson, codecVersion));
    }

    public void encode(FriendlyByteBuf target) {
        target.writeVarInt(codecVersion);
        encodePayload(target);
    }

    public void encodePayload(FriendlyByteBuf target) {
        byte[] bytes = encodeUtf8(canonicalJson);
        target.writeVarInt(bytes.length);
        target.writeBytes(bytes);
    }

    public static SFMPacketValueEnvelope decode(FriendlyByteBuf source) {
        int codecVersion = source.readVarInt();
        return decodePayload(codecVersion, source);
    }

    public static SFMPacketValueEnvelope decodePayload(
            int codecVersion,
            FriendlyByteBuf source
    ) {
        int byteCount = source.readVarInt();
        if (byteCount < 0 || byteCount > SFMValueJsonCodec.MAX_ENCODED_UTF8_BYTES) {
            throw new IllegalArgumentException(
                    "Invalid packet value byte count " + byteCount
            );
        }
        if (byteCount > source.readableBytes()) {
            throw new IllegalArgumentException("Truncated packet value payload");
        }
        byte[] bytes = new byte[byteCount];
        source.readBytes(bytes);
        return new SFMPacketValueEnvelope(codecVersion, decodeUtf8(bytes));
    }

    private static byte[] encodeUtf8(String value) {
        try {
            ByteBuffer encoded = StandardCharsets.UTF_8
                    .newEncoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .encode(CharBuffer.wrap(value));
            byte[] bytes = new byte[encoded.remaining()];
            encoded.get(bytes);
            return bytes;
        } catch (CharacterCodingException invalid) {
            throw new IllegalArgumentException("Packet value JSON contains malformed Unicode", invalid);
        }
    }

    private static String decodeUtf8(byte[] bytes) {
        try {
            return StandardCharsets.UTF_8
                    .newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException invalid) {
            throw new IllegalArgumentException("Packet value payload is not valid UTF-8", invalid);
        }
    }
}
