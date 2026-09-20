package ca.teamdman.sfm.client.syntax.process;

import ca.teamdman.sfm.client.syntax.SFMSyntaxHighlightJsonCodec;
import ca.teamdman.sfm.client.syntax.SFMSyntaxHighlightRequest;
import ca.teamdman.sfm.client.syntax.SFMSyntaxHighlightResult;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Strict Java codec for the independently versioned Rust syntax-server envelopes. */
public final class SFMSyntaxServerProtocol {
    public static final String PROTOCOL_SCHEMA = "sfm.syntax-server/1";
    public static final String HELLO_SCHEMA = "sfm.syntax-server.hello/1";
    public static final String HIGHLIGHT_SCHEMA = "sfm.syntax-server.highlight/1";
    public static final String CANCEL_SCHEMA = "sfm.syntax-server.cancel/1";
    public static final String PING_SCHEMA = "sfm.syntax-server.ping/1";
    public static final String SHUTDOWN_SCHEMA = "sfm.syntax-server.shutdown/1";
    public static final String ERROR_SCHEMA = "sfm.syntax-server.error/1";
    public static final Set<String> CLIENT_CAPABILITIES = Set.of(
            "highlight", "cancellation", "ping", "shutdown"
    );

    private SFMSyntaxServerProtocol() {
    }

    public record ServerHello(
            String protocolSchema,
            String serverName,
            String serverVersion,
            Set<String> capabilities,
            int maximumFrameBytes,
            int maximumPendingRequests,
            List<String> supportedLanguages,
            String requestSchema,
            String resultSchema,
            String rawJson
    ) {
        public ServerHello {
            require(PROTOCOL_SCHEMA.equals(protocolSchema), "Unsupported syntax-server protocol schema");
            serverName = nonBlank(serverName, "serverName");
            serverVersion = nonBlank(serverVersion, "serverVersion");
            capabilities = Set.copyOf(capabilities);
            require(capabilities.containsAll(CLIENT_CAPABILITIES), "Syntax server lacks required capabilities");
            require(maximumFrameBytes > 0, "Syntax server frame limit must be positive");
            require(maximumPendingRequests > 0, "Syntax server pending limit must be positive");
            supportedLanguages = List.copyOf(supportedLanguages);
            require(supportedLanguages.contains("java"), "Syntax server does not advertise Java");
            require(SFMSyntaxHighlightRequest.SCHEMA.equals(requestSchema), "Syntax request schema mismatch");
            require(SFMSyntaxHighlightResult.SCHEMA.equals(resultSchema), "Syntax result schema mismatch");
            Objects.requireNonNull(rawJson, "rawJson");
        }
    }

    public sealed interface ServerFrame permits HelloFrame, HighlightResultFrame,
            CancelledFrame, PongFrame, ShutdownFrame, ErrorFrame {
    }

    public record HelloFrame(ServerHello hello) implements ServerFrame {
        public HelloFrame { Objects.requireNonNull(hello, "hello"); }
    }

    public record HighlightResultFrame(SFMSyntaxHighlightResult result) implements ServerFrame {
        public HighlightResultFrame { Objects.requireNonNull(result, "result"); }
    }

    public record CancelledFrame(
            long requestId,
            long requestGeneration,
            String originId,
            long originGeneration,
            String status
    ) implements ServerFrame {
        public CancelledFrame {
            originId = nonBlank(originId, "originId");
            status = nonBlank(status, "status");
        }
    }

    public record PongFrame(long nonce) implements ServerFrame {
    }

    public record ShutdownFrame() implements ServerFrame {
    }

    public record ErrorFrame(
            String code,
            String message,
            String disposition,
            Optional<Long> requestId,
            Optional<Long> requestGeneration
    ) implements ServerFrame {
        public ErrorFrame {
            code = nonBlank(code, "code");
            Objects.requireNonNull(message, "message");
            disposition = nonBlank(disposition, "disposition");
            require(disposition.equals("request") || disposition.equals("fatal"),
                    "Unknown syntax error disposition");
            requestId = Objects.requireNonNull(requestId, "requestId");
            requestGeneration = Objects.requireNonNull(requestGeneration, "requestGeneration");
        }

        public boolean fatal() {
            return disposition.equals("fatal");
        }
    }

    public static String hello(String clientName, String clientVersion, int maximumFrameBytes) {
        JsonObject value = base("hello", HELLO_SCHEMA);
        JsonObject hello = new JsonObject();
        hello.addProperty("protocol_schema", PROTOCOL_SCHEMA);
        hello.addProperty("client_name", nonBlank(clientName, "clientName"));
        hello.addProperty("client_version", nonBlank(clientVersion, "clientVersion"));
        JsonArray capabilities = new JsonArray();
        List.copyOf(CLIENT_CAPABILITIES).stream().sorted().forEach(capabilities::add);
        hello.add("capabilities", capabilities);
        hello.addProperty("max_frame_bytes", maximumFrameBytes);
        value.add("hello", hello);
        return value.toString();
    }

    public static String highlight(SFMSyntaxHighlightRequest request) {
        JsonObject value = base("highlight", HIGHLIGHT_SCHEMA);
        value.add("request", JsonParser.parseString(SFMSyntaxHighlightJsonCodec.encodeRequest(request)));
        return value.toString();
    }

    public static String cancel(SFMSyntaxHighlightRequest request, String reason) {
        JsonObject value = base("cancel", CANCEL_SCHEMA);
        value.addProperty("request_id", request.requestId());
        value.addProperty("request_generation", request.requestGeneration());
        value.addProperty("origin_id", request.originId());
        value.addProperty("origin_generation", request.originGeneration());
        value.addProperty("reason", Objects.requireNonNull(reason, "reason"));
        return value.toString();
    }

    public static String ping(long nonce) {
        JsonObject value = base("ping", PING_SCHEMA);
        value.addProperty("nonce", nonce);
        return value.toString();
    }

    public static String shutdown(String reason) {
        JsonObject value = base("shutdown", SHUTDOWN_SCHEMA);
        value.addProperty("reason", Objects.requireNonNull(reason, "reason"));
        return value.toString();
    }

    public static ServerFrame decodeServerFrame(String json) throws ProtocolException {
        try {
            JsonObject frame = object(JsonParser.parseString(json), "syntax-server frame");
            String kind = string(frame, "kind");
            String schema = string(frame, "schema");
            return switch (kind) {
                case "hello" -> {
                    requireSchema(schema, HELLO_SCHEMA);
                    requireFields(frame, "hello frame", "kind", "schema", "hello");
                    yield new HelloFrame(readHello(requiredObject(frame, "hello"), json));
                }
                case "highlight-result" -> {
                    requireSchema(schema, HIGHLIGHT_SCHEMA);
                    requireFields(frame, "highlight-result frame", "kind", "schema", "result");
                    yield new HighlightResultFrame(SFMSyntaxHighlightJsonCodec.decodeResult(
                            requiredObject(frame, "result").toString()
                    ));
                }
                case "cancelled" -> {
                    requireSchema(schema, CANCEL_SCHEMA);
                    requireFields(frame, "cancelled frame", "kind", "schema", "cancellation");
                    JsonObject value = requiredObject(frame, "cancellation");
                    requireFields(value, "cancellation", "request_id", "request_generation", "origin_id",
                            "origin_generation", "status");
                    yield new CancelledFrame(
                            nonNegativeLong(value, "request_id"),
                            nonNegativeLong(value, "request_generation"),
                            string(value, "origin_id"),
                            nonNegativeLong(value, "origin_generation"),
                            string(value, "status")
                    );
                }
                case "pong" -> {
                    requireSchema(schema, PING_SCHEMA);
                    requireFields(frame, "pong frame", "kind", "schema", "nonce");
                    yield new PongFrame(nonNegativeLong(frame, "nonce"));
                }
                case "shutdown" -> {
                    requireSchema(schema, SHUTDOWN_SCHEMA);
                    requireFields(frame, "shutdown frame", "kind", "schema");
                    yield new ShutdownFrame();
                }
                case "error" -> {
                    requireSchema(schema, ERROR_SCHEMA);
                    requireFields(frame, "error frame", "kind", "schema", "error");
                    JsonObject value = requiredObject(frame, "error");
                    requireFields(value, "error", "code", "message", "disposition", "request_id",
                            "request_generation");
                    yield new ErrorFrame(
                            string(value, "code"),
                            string(value, "message"),
                            string(value, "disposition"),
                            optionalLong(value, "request_id"),
                            optionalLong(value, "request_generation")
                    );
                }
                default -> throw new ProtocolException("Unknown syntax-server frame kind: " + bounded(kind));
            };
        } catch (ProtocolException failure) {
            throw failure;
        } catch (JsonParseException | IllegalArgumentException failure) {
            throw new ProtocolException("Invalid syntax-server JSON envelope", failure);
        }
    }

    private static ServerHello readHello(JsonObject value, String rawJson) throws ProtocolException {
        requireFields(value, "hello", "protocol_schema", "server_name", "server_version", "capabilities",
                "max_frame_bytes", "max_pending_requests", "supported_languages", "request_schema",
                "result_schema");
        try {
            return new ServerHello(
                    string(value, "protocol_schema"),
                    string(value, "server_name"),
                    string(value, "server_version"),
                    Set.copyOf(strings(value, "capabilities")),
                    positiveInt(value, "max_frame_bytes"),
                    positiveInt(value, "max_pending_requests"),
                    strings(value, "supported_languages"),
                    string(value, "request_schema"),
                    string(value, "result_schema"),
                    rawJson
            );
        } catch (IllegalArgumentException failure) {
            throw new ProtocolException("Invalid syntax-server hello", failure);
        }
    }

    private static JsonObject base(String kind, String schema) {
        JsonObject value = new JsonObject();
        value.addProperty("kind", kind);
        value.addProperty("schema", schema);
        return value;
    }

    private static void requireSchema(String actual, String expected) throws ProtocolException {
        if (!expected.equals(actual)) throw new ProtocolException("Syntax-server frame schema mismatch");
    }

    private static int positiveInt(JsonObject json, String name) throws ProtocolException {
        long value = nonNegativeLong(json, name);
        if (value <= 0 || value > Integer.MAX_VALUE) throw new ProtocolException(name + " is not a positive int");
        return (int) value;
    }

    private static long nonNegativeLong(JsonObject json, String name) throws ProtocolException {
        JsonElement value = required(json, name);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            throw new ProtocolException(name + " must be a non-negative JSON integer");
        }
        String encoded = value.getAsString();
        if (!encoded.matches("0|[1-9][0-9]*")) {
            throw new ProtocolException(name + " must be a non-negative JSON integer");
        }
        try {
            return Long.parseLong(encoded);
        } catch (NumberFormatException failure) {
            throw new ProtocolException(name + " exceeds Java's supported u64 range", failure);
        }
    }

    private static Optional<Long> optionalLong(JsonObject json, String name) throws ProtocolException {
        JsonElement value = required(json, name);
        if (value instanceof JsonNull) return Optional.empty();
        return Optional.of(nonNegativeLong(json, name));
    }

    private static String string(JsonObject json, String name) throws ProtocolException {
        JsonElement value = required(json, name);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw new ProtocolException(name + " must be a JSON string");
        }
        return value.getAsString();
    }

    private static List<String> strings(JsonObject json, String name) throws ProtocolException {
        JsonElement value = required(json, name);
        if (!value.isJsonArray()) throw new ProtocolException(name + " must be a JSON array");
        ArrayList<String> answer = new ArrayList<>();
        for (JsonElement entry : value.getAsJsonArray()) {
            if (!entry.isJsonPrimitive() || !entry.getAsJsonPrimitive().isString()) {
                throw new ProtocolException(name + " contains a non-string");
            }
            answer.add(entry.getAsString());
        }
        return List.copyOf(answer);
    }

    private static JsonElement required(JsonObject json, String name) throws ProtocolException {
        if (!json.has(name) || json.get(name) == null) throw new ProtocolException("Missing JSON field: " + name);
        return json.get(name);
    }

    private static JsonObject requiredObject(JsonObject json, String name) throws ProtocolException {
        return object(required(json, name), name);
    }

    private static JsonObject object(JsonElement value, String label) throws ProtocolException {
        if (value == null || !value.isJsonObject()) throw new ProtocolException(label + " must be an object");
        return value.getAsJsonObject();
    }

    private static void requireFields(JsonObject json, String label, String... fields) throws ProtocolException {
        Set<String> expected = Set.of(fields);
        Set<String> actual = new HashSet<>(json.keySet());
        if (!expected.equals(actual)) throw new ProtocolException(label + " has missing or unknown fields");
    }

    private static String nonBlank(String value, String label) {
        Objects.requireNonNull(value, label);
        if (value.isBlank()) throw new IllegalArgumentException(label + " must not be blank");
        return value;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }

    private static String bounded(String value) {
        return value.length() <= 80 ? value : value.substring(0, 80);
    }

    public static final class ProtocolException extends IOException {
        public ProtocolException(String message) { super(message); }
        public ProtocolException(String message, Throwable cause) { super(message, cause); }
    }
}
