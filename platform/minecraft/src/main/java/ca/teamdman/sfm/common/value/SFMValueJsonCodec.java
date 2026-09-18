package ca.teamdman.sfm.common.value;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Strict, bounded, canonical JSON codec for {@link SFMValue}. */
public final class SFMValueJsonCodec {
    public static final int VERSION = 2;
    public static final int OLDEST_READABLE_VERSION = 1;
    public static final int MAX_ENCODED_UTF8_BYTES = 3_072;
    public static final int MAX_NESTING_DEPTH = 16;
    public static final int MAX_CONTAINER_ENTRIES = 256;

    private SFMValueJsonCodec() {
    }

    public static String encode(SFMValue value) {
        String encoded = write(value, "");
        requireWithinByteLimit(encoded);
        return encoded;
    }

    /**
     * Formats a value for human inspection after enforcing the same value
     * limits as the canonical wire representation.
     */
    public static String encodePretty(SFMValue value) {
        encode(value);
        return write(value, "  ");
    }

    private static String write(SFMValue value, String indent) {
        Objects.requireNonNull(value, "value");
        StringWriter target = new StringWriter();
        try (JsonWriter writer = new JsonWriter(target)) {
            writer.setHtmlSafe(false);
            writer.setSerializeNulls(true);
            writer.setIndent(indent);
            writeValue(writer, value, 0);
        } catch (IOException impossible) {
            throw new IllegalStateException("String-backed JSON writing failed", impossible);
        }
        return target.toString();
    }

    public static SFMValue decode(String encoded) {
        return decode(encoded, VERSION);
    }

    public static boolean isReadableVersion(int version) {
        return version >= OLDEST_READABLE_VERSION && version <= VERSION;
    }

    public static SFMValue decode(String encoded, int version) {
        if (!isReadableVersion(version)) {
            throw new IllegalArgumentException("Unsupported packet value codec version " + version);
        }
        if (encoded == null) {
            throw new IllegalArgumentException("Packet value JSON is required");
        }
        requireWellFormedUnicode(encoded, "packet value JSON");
        requireWithinByteLimit(encoded);
        try (JsonReader reader = new JsonReader(new StringReader(encoded))) {
            reader.setLenient(false);
            SFMValue value = readValue(reader, 0, version);
            if (reader.peek() != JsonToken.END_DOCUMENT) {
                throw new IllegalArgumentException("Packet value JSON must contain exactly one value");
            }
            return value;
        } catch (IOException | IllegalStateException | NumberFormatException invalid) {
            throw new IllegalArgumentException("Malformed packet value JSON", invalid);
        }
    }

    static void requireWellFormedUnicode(String value, String label) {
        try {
            ByteBuffer ignored = StandardCharsets.UTF_8
                    .newEncoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .encode(CharBuffer.wrap(value));
        } catch (CharacterCodingException invalid) {
            throw new IllegalArgumentException(label + " contains malformed Unicode", invalid);
        }
    }

    private static void requireWithinByteLimit(String encoded) {
        int byteCount = encoded.getBytes(StandardCharsets.UTF_8).length;
        if (byteCount > MAX_ENCODED_UTF8_BYTES) {
            throw new IllegalArgumentException(
                    "Packet value JSON exceeds " + MAX_ENCODED_UTF8_BYTES + " UTF-8 bytes"
            );
        }
    }

    private static void writeValue(JsonWriter writer, SFMValue value, int depth) throws IOException {
        if (depth > MAX_NESTING_DEPTH) {
            throw new IllegalArgumentException("Packet value exceeds nesting depth " + MAX_NESTING_DEPTH);
        }
        if (value instanceof SFMValue.NullValue) {
            writer.nullValue();
        } else if (value instanceof SFMValue.BooleanValue booleanValue) {
            writer.value(booleanValue.value());
        } else if (value instanceof SFMValue.LongValue longValue) {
            writer.value(longValue.value());
        } else if (value instanceof SFMValue.DoubleValue doubleValue) {
            writer.value(doubleValue.value());
        } else if (value instanceof SFMValue.StringValue stringValue) {
            writer.value(stringValue.value());
        } else if (value instanceof SFMValue.ArrayValue arrayValue) {
            requireContainerSize(arrayValue.values().size(), "array");
            writer.beginArray();
            for (SFMValue element : arrayValue.values()) {
                writeValue(writer, element, depth + 1);
            }
            writer.endArray();
        } else if (value instanceof SFMValue.ObjectValue objectValue) {
            requireContainerSize(objectValue.fields().size(), "object");
            writer.beginObject();
            for (Map.Entry<String, SFMValue> field : objectValue.fields().entrySet()) {
                writer.name(field.getKey());
                writeValue(writer, field.getValue(), depth + 1);
            }
            writer.endObject();
        } else {
            throw new IllegalArgumentException("Unsupported packet value implementation " + value.getClass());
        }
    }

    private static SFMValue readValue(JsonReader reader, int depth, int version) throws IOException {
        if (depth > MAX_NESTING_DEPTH) {
            throw new IllegalArgumentException("Packet value exceeds nesting depth " + MAX_NESTING_DEPTH);
        }
        return switch (reader.peek()) {
            case NULL -> {
                reader.nextNull();
                yield SFMValue.nullValue();
            }
            case BOOLEAN -> SFMValue.of(reader.nextBoolean());
            case STRING -> SFMValue.of(reader.nextString());
            case NUMBER -> parseNumber(reader.nextString(), version);
            case BEGIN_ARRAY -> readArray(reader, depth, version);
            case BEGIN_OBJECT -> readObject(reader, depth, version);
            default -> throw new IllegalArgumentException("Unexpected JSON token " + reader.peek());
        };
    }

    private static SFMValue readArray(JsonReader reader, int depth, int version) throws IOException {
        reader.beginArray();
        ArrayList<SFMValue> values = new ArrayList<>();
        while (reader.hasNext()) {
            requireRoom(values.size(), "array");
            values.add(readValue(reader, depth + 1, version));
        }
        reader.endArray();
        return SFMValue.array(values);
    }

    private static SFMValue readObject(JsonReader reader, int depth, int version) throws IOException {
        reader.beginObject();
        LinkedHashMap<String, SFMValue> fields = new LinkedHashMap<>();
        while (reader.hasNext()) {
            requireRoom(fields.size(), "object");
            String name = reader.nextName();
            requireWellFormedUnicode(name, "object field name");
            if (fields.containsKey(name)) {
                throw new IllegalArgumentException("Duplicate packet value field: " + name);
            }
            fields.put(name, readValue(reader, depth + 1, version));
        }
        reader.endObject();
        return SFMValue.object(fields);
    }

    private static SFMValue parseNumber(String token, int version) {
        if (token.indexOf('.') >= 0 || token.indexOf('e') >= 0 || token.indexOf('E') >= 0) {
            if (version == OLDEST_READABLE_VERSION) {
                throw new IllegalArgumentException("Version 1 packet value numbers must be signed 64-bit integers");
            }
            try {
                return SFMValue.of(Double.parseDouble(token));
            } catch (NumberFormatException invalid) {
                throw new IllegalArgumentException("Invalid packet value double", invalid);
            }
        }
        try {
            return SFMValue.of(Long.parseLong(token));
        } catch (NumberFormatException invalid) {
            throw new IllegalArgumentException("Packet value number is outside signed 64-bit range", invalid);
        }
    }

    private static void requireRoom(int currentSize, String kind) {
        if (currentSize >= MAX_CONTAINER_ENTRIES) {
            throw new IllegalArgumentException(
                    "Packet value " + kind + " exceeds " + MAX_CONTAINER_ENTRIES + " entries"
            );
        }
    }

    private static void requireContainerSize(int size, String kind) {
        if (size > MAX_CONTAINER_ENTRIES) {
            throw new IllegalArgumentException(
                    "Packet value " + kind + " exceeds " + MAX_CONTAINER_ENTRIES + " entries"
            );
        }
    }
}
