package ca.teamdman.sfm.client.terminal;

import com.google.gson.JsonPrimitive;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.StringReader;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** Bounded scalar-only JSON Lines spoken by the checkout-local sfm terminal worker. */
public final class TouchDisplayTerminalWorkerProtocol {
    public static final int MAX_RECORD_CHARS = 1024;
    public static final int MAX_CONTENT_CHARS = 16 * 1024;
    public static final int MAX_RECORDS = 64;
    public static final long MAX_ID = 0xffff_ffffL;

    public record Ack(long id, long count, double u, double v) {}
    public record Render(long count, String color) {}
    public record Observation(boolean ready, @Nullable Ack ack, @Nullable Render render,
                              boolean ended, @Nullable String error, int rejectedRecords) {}

    private TouchDisplayTerminalWorkerProtocol() {}

    public static String touch(long id, double u, double v) {
        requireId(id);
        requireCoordinate(u);
        requireCoordinate(v);
        return "{\"version\":1,\"type\":\"touch\",\"id\":" + id + ",\"u\":"
                + (u == 0 ? 0.0 : u) + ",\"v\":" + (v == 0 ? 0.0 : v) + "}";
    }

    public static String quit(long id) {
        requireId(id);
        return "{\"version\":1,\"type\":\"quit\",\"id\":" + id + "}";
    }

    /**
     * Content is a bounded terminal snapshot, not an arbitrary shell transcript.
     * A scalar record may wrap across physical rows, so adjacent fragments are
     * joined until a closing brace. Echoed touch/quit records never count as replies.
     * Incomplete trailing records are ignored until a later snapshot completes them.
     */
    public static Observation inspect(String content) {
        if (content == null || content.length() > MAX_CONTENT_CHARS) {
            throw new IllegalArgumentException("Terminal worker content exceeds its bound");
        }
        boolean ready = false;
        boolean ended = false;
        Ack ack = null;
        Render render = null;
        String error = null;
        int rejected = 0;
        int records = 0;
        StringBuilder pending = new StringBuilder();
        for (String physicalLine : content.split("\\R", -1)) {
            String fragment = physicalLine.strip();
            if (pending.isEmpty() && !fragment.startsWith("{")) continue;
            if (pending.length() + fragment.length() > MAX_RECORD_CHARS) {
                pending.setLength(0);
                rejected++;
                if (++records > MAX_RECORDS) throw new IllegalArgumentException("Too many worker records");
                continue;
            }
            pending.append(fragment);
            if (!fragment.endsWith("}")) continue;
            if (++records > MAX_RECORDS) throw new IllegalArgumentException("Too many worker records");
            try {
                Map<String, JsonPrimitive> fields = fields(pending.toString());
                if (integer(fields, "version") != 1) throw new IllegalArgumentException("Unsupported worker version");
                String type = string(fields, "type");
                switch (type) {
                    case "touch", "quit" -> { /* terminal input echo is not worker evidence */ }
                    case "ready" -> {
                        exact(fields, "type", "version", "max_line_bytes", "max_commands");
                        if (integer(fields, "max_line_bytes") != 1024 || integer(fields, "max_commands") != 4096) {
                            throw new IllegalArgumentException("Unexpected worker limits");
                        }
                        ready = true;
                    }
                    case "ack" -> {
                        exact(fields, "type", "version", "id", "count", "u", "v");
                        Ack next = new Ack(integer(fields, "id"), count(fields), number(fields, "u"), number(fields, "v"));
                        if (ack == null || next.count() > ack.count()) ack = next;
                    }
                    case "render" -> {
                        exact(fields, "type", "version", "count", "color");
                        String color = string(fields, "color");
                        long count = count(fields);
                        if (!color.equals(count % 2 == 0 ? "red" : "blue")) throw new IllegalArgumentException("Invalid render state");
                        if (render == null || count > render.count()) render = new Render(count, color);
                    }
                    case "bye" -> {
                        exact(fields, "type", "version", "count", "reason");
                        count(fields);
                        if (!Set.of("quit", "eof", "input_limit").contains(string(fields, "reason"))) {
                            throw new IllegalArgumentException("Invalid worker end reason");
                        }
                        ended = true;
                    }
                    case "error" -> {
                        exact(fields, "type", "version", "count", "code");
                        count(fields);
                        String code = string(fields, "code");
                        if (!code.matches("[a-z_]{1,48}")) throw new IllegalArgumentException("Invalid worker error");
                        error = code;
                    }
                    default -> throw new IllegalArgumentException("Unknown worker output");
                }
            } catch (IOException | IllegalArgumentException invalid) {
                rejected++;
            }
            pending.setLength(0);
        }
        return new Observation(ready, ack, render, ended, error, rejected);
    }

    private static Map<String, JsonPrimitive> fields(String source) throws IOException {
        Map<String, JsonPrimitive> fields = new HashMap<>();
        try (JsonReader reader = new JsonReader(new StringReader(source))) {
            reader.setLenient(false);
            reader.beginObject();
            while (reader.hasNext()) {
                String name = reader.nextName();
                if (name.length() > 24 || fields.containsKey(name) || fields.size() >= 8) {
                    throw new IllegalArgumentException("Duplicate or excess worker field");
                }
                JsonToken token = reader.peek();
                if (token != JsonToken.STRING && token != JsonToken.NUMBER) {
                    throw new IllegalArgumentException("Worker fields must be scalars");
                }
                String value = reader.nextString();
                if (value.length() > 128) throw new IllegalArgumentException("Worker scalar exceeds its bound");
                // Number preserves its exact lexeme without coercing quoted strings.
                fields.put(name, token == JsonToken.STRING ? new JsonPrimitive(value)
                        : new JsonPrimitive(new com.google.gson.internal.LazilyParsedNumber(value)));
            }
            reader.endObject();
            if (reader.peek() != JsonToken.END_DOCUMENT) throw new IllegalArgumentException("Trailing worker data");
        }
        return fields;
    }

    private static void exact(Map<String, JsonPrimitive> fields, String... keys) {
        if (!fields.keySet().equals(Set.of(keys))) throw new IllegalArgumentException("Unexpected worker fields");
    }

    private static String string(Map<String, JsonPrimitive> fields, String key) {
        JsonPrimitive value = fields.get(key);
        if (value == null || !value.isString()) throw new IllegalArgumentException("Expected worker text");
        return value.getAsString();
    }

    private static long integer(Map<String, JsonPrimitive> fields, String key) {
        JsonPrimitive value = fields.get(key);
        if (value == null || !value.isNumber() || !value.getAsString().matches("0|[1-9][0-9]{0,9}")) {
            throw new IllegalArgumentException("Expected worker integer");
        }
        long result = Long.parseLong(value.getAsString());
        requireId(result);
        return result;
    }

    private static long count(Map<String, JsonPrimitive> fields) {
        long count = integer(fields, "count");
        if (count > 4096) throw new IllegalArgumentException("Worker count exceeds its bound");
        return count;
    }

    private static double number(Map<String, JsonPrimitive> fields, String key) {
        JsonPrimitive value = fields.get(key);
        if (value == null || !value.isNumber()) throw new IllegalArgumentException("Expected worker coordinate");
        double number = value.getAsDouble();
        requireCoordinate(number);
        return number == 0 ? 0.0 : number;
    }

    private static void requireId(long value) {
        if (value < 0 || value > MAX_ID) throw new IllegalArgumentException("Worker ID is outside uint32");
    }

    private static void requireCoordinate(double value) {
        if (!Double.isFinite(value) || value < 0 || value > 1) throw new IllegalArgumentException("Worker UV is outside 0..1");
    }
}
