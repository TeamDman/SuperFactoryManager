package ca.teamdman.sfm.client.syntax;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Strict snake-case JSON codec shared with the Rust Facet syntax contract. */
public final class SFMSyntaxHighlightJsonCodec {
    private SFMSyntaxHighlightJsonCodec() {
    }

    public static String encodeRequest(SFMSyntaxHighlightRequest request) {
        return writeRequest(request).toString();
    }

    public static SFMSyntaxHighlightRequest decodeRequest(String json) {
        return readRequest(object(JsonParser.parseString(json), "request"));
    }

    public static String encodeResult(SFMSyntaxHighlightResult result) {
        return writeResult(result).toString();
    }

    public static SFMSyntaxHighlightResult decodeResult(String json) {
        return readResult(object(JsonParser.parseString(json), "result"));
    }

    private static JsonObject writeRequest(SFMSyntaxHighlightRequest value) {
        JsonObject json = new JsonObject();
        json.addProperty("schema", value.schema());
        json.addProperty("request_id", value.requestId());
        json.addProperty("request_generation", value.requestGeneration());
        json.addProperty("origin_id", value.originId());
        json.addProperty("origin_generation", value.originGeneration());
        json.addProperty("language", value.language());
        json.addProperty("source", value.source());
        json.addProperty("source_sha256", value.sourceSha256());
        json.addProperty("maximum_spans", value.maximumSpans());
        return json;
    }

    private static SFMSyntaxHighlightRequest readRequest(JsonObject json) {
        requireFields(json, "request",
                "schema", "request_id", "request_generation", "origin_id", "origin_generation", "language",
                "source", "source_sha256", "maximum_spans");
        return new SFMSyntaxHighlightRequest(
                string(json, "schema"),
                longValue(json, "request_id"),
                longValue(json, "request_generation"),
                string(json, "origin_id"),
                longValue(json, "origin_generation"),
                string(json, "language"),
                string(json, "source"),
                string(json, "source_sha256"),
                longValue(json, "maximum_spans")
        );
    }

    private static JsonObject writeResult(SFMSyntaxHighlightResult value) {
        JsonObject json = new JsonObject();
        json.addProperty("schema", value.schema());
        json.addProperty("request_id", value.requestId());
        json.addProperty("request_generation", value.requestGeneration());
        json.addProperty("origin_id", value.originId());
        json.addProperty("origin_generation", value.originGeneration());
        json.addProperty("language", value.language());
        json.addProperty("source_sha256", value.sourceSha256());
        json.addProperty("source_bytes", value.sourceBytes());
        json.addProperty("outcome", value.outcome().wireName());
        json.addProperty("complete", value.complete());
        json.addProperty("parser_fingerprint", value.parserFingerprint());
        json.addProperty("formatting_schema", value.formattingSchema());
        json.addProperty("elapsed_micros", value.elapsedMicros());
        json.add("cache", writeCache(value.cache()));
        JsonArray diagnostics = new JsonArray();
        value.diagnostics().forEach(diagnostic -> diagnostics.add(writeDiagnostic(diagnostic)));
        json.add("diagnostics", diagnostics);
        JsonArray spans = new JsonArray();
        value.spans().forEach(span -> spans.add(writeSpan(span)));
        json.add("spans", spans);
        return json;
    }

    private static SFMSyntaxHighlightResult readResult(JsonObject json) {
        requireFields(json, "result",
                "schema", "request_id", "request_generation", "origin_id", "origin_generation", "language",
                "source_sha256", "source_bytes", "outcome", "complete", "parser_fingerprint", "formatting_schema",
                "elapsed_micros", "cache", "diagnostics", "spans");
        return new SFMSyntaxHighlightResult(
                string(json, "schema"),
                longValue(json, "request_id"),
                longValue(json, "request_generation"),
                string(json, "origin_id"),
                longValue(json, "origin_generation"),
                string(json, "language"),
                string(json, "source_sha256"),
                longValue(json, "source_bytes"),
                SFMSyntaxHighlightResult.Outcome.fromWireName(string(json, "outcome")),
                bool(json, "complete"),
                string(json, "parser_fingerprint"),
                string(json, "formatting_schema"),
                longValue(json, "elapsed_micros"),
                readCache(requiredObject(json, "cache")),
                diagnostics(json),
                spans(json)
        );
    }

    private static JsonObject writeCache(SFMSyntaxHighlightResult.CacheEvidence value) {
        JsonObject json = new JsonObject();
        json.addProperty("status", value.status().wireName());
        json.addProperty("entries", value.entries());
        json.addProperty("retained_bytes", value.retainedBytes());
        json.addProperty("hits", value.hits());
        json.addProperty("misses", value.misses());
        json.addProperty("evictions", value.evictions());
        return json;
    }

    private static SFMSyntaxHighlightResult.CacheEvidence readCache(JsonObject json) {
        requireFields(json, "cache", "status", "entries", "retained_bytes", "hits", "misses", "evictions");
        return new SFMSyntaxHighlightResult.CacheEvidence(
                SFMSyntaxHighlightResult.CacheStatus.fromWireName(string(json, "status")),
                longValue(json, "entries"),
                longValue(json, "retained_bytes"),
                longValue(json, "hits"),
                longValue(json, "misses"),
                longValue(json, "evictions")
        );
    }

    private static JsonObject writeDiagnostic(SFMSyntaxHighlightResult.Diagnostic value) {
        JsonObject json = new JsonObject();
        json.addProperty("code", value.code());
        json.addProperty("severity", value.severity().wireName());
        json.addProperty("message", value.message());
        addOptionalLong(json, "start_byte", value.startByte());
        addOptionalLong(json, "end_byte", value.endByte());
        return json;
    }

    private static SFMSyntaxHighlightResult.Diagnostic readDiagnostic(JsonObject json) {
        requireFields(json, "diagnostic", "code", "severity", "message", "start_byte", "end_byte");
        return new SFMSyntaxHighlightResult.Diagnostic(
                string(json, "code"),
                SFMSyntaxHighlightResult.DiagnosticSeverity.fromWireName(string(json, "severity")),
                string(json, "message"),
                optionalLong(json, "start_byte"),
                optionalLong(json, "end_byte")
        );
    }

    private static JsonObject writeSpan(SFMSyntaxHighlightResult.Span value) {
        JsonObject json = new JsonObject();
        json.addProperty("start_byte", value.startByte());
        json.addProperty("end_byte", value.endByte());
        json.addProperty("arborium_tag", value.arboriumTag());
        JsonArray formatting = new JsonArray();
        value.chatFormatting().forEach(formatting::add);
        json.add("chat_formatting", formatting);
        return json;
    }

    private static SFMSyntaxHighlightResult.Span readSpan(JsonObject json) {
        requireFields(json, "span", "start_byte", "end_byte", "arborium_tag", "chat_formatting");
        return new SFMSyntaxHighlightResult.Span(
                longValue(json, "start_byte"),
                longValue(json, "end_byte"),
                string(json, "arborium_tag"),
                strings(json, "chat_formatting")
        );
    }

    private static List<SFMSyntaxHighlightResult.Diagnostic> diagnostics(JsonObject json) {
        JsonArray values = requiredArray(json, "diagnostics");
        if (values.size() > SFMSyntaxHighlightLimits.DEFAULT_MAXIMUM_DIAGNOSTICS) {
            throw new IllegalArgumentException("diagnostics exceeds the process limit");
        }
        ArrayList<SFMSyntaxHighlightResult.Diagnostic> result = new ArrayList<>(values.size());
        for (JsonElement value : values) result.add(readDiagnostic(object(value, "diagnostics[]")));
        return List.copyOf(result);
    }

    private static List<SFMSyntaxHighlightResult.Span> spans(JsonObject json) {
        JsonArray values = requiredArray(json, "spans");
        if (values.size() > SFMSyntaxHighlightLimits.DEFAULT_MAXIMUM_SPANS) {
            throw new IllegalArgumentException("spans exceeds the process limit");
        }
        ArrayList<SFMSyntaxHighlightResult.Span> result = new ArrayList<>(values.size());
        for (JsonElement value : values) result.add(readSpan(object(value, "spans[]")));
        return List.copyOf(result);
    }

    private static List<String> strings(JsonObject json, String name) {
        JsonArray values = requiredArray(json, name);
        ArrayList<String> result = new ArrayList<>(values.size());
        for (JsonElement value : values) {
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
                throw new IllegalArgumentException(name + " contains a non-string value");
            }
            result.add(value.getAsString());
        }
        return List.copyOf(result);
    }

    private static void addOptionalLong(JsonObject json, String name, Optional<Long> value) {
        if (value.isPresent()) json.addProperty(name, value.orElseThrow());
        else json.add(name, JsonNull.INSTANCE);
    }

    private static Optional<Long> optionalLong(JsonObject json, String name) {
        JsonElement value = required(json, name);
        if (value instanceof JsonNull) return Optional.empty();
        return Optional.of(longValue(json, name));
    }

    private static String string(JsonObject json, String name) {
        JsonElement value = required(json, name);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException(name + " must be a JSON string");
        }
        return value.getAsString();
    }

    private static long longValue(JsonObject json, String name) {
        JsonElement value = required(json, name);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException(name + " must be a non-negative JSON integer");
        }
        String encoded = value.getAsString();
        if (!encoded.matches("0|[1-9][0-9]*")) {
            throw new IllegalArgumentException(name + " must be a non-negative JSON integer");
        }
        try {
            return Long.parseLong(encoded);
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException(name + " exceeds Java's supported u64 range", error);
        }
    }

    private static boolean bool(JsonObject json, String name) {
        JsonElement value = required(json, name);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) {
            throw new IllegalArgumentException(name + " must be a JSON boolean");
        }
        return value.getAsBoolean();
    }

    private static JsonObject requiredObject(JsonObject json, String name) {
        return object(required(json, name), name);
    }

    private static JsonArray requiredArray(JsonObject json, String name) {
        JsonElement value = required(json, name);
        if (!value.isJsonArray()) throw new IllegalArgumentException(name + " must be a JSON array");
        return value.getAsJsonArray();
    }

    private static JsonElement required(JsonObject json, String name) {
        if (!json.has(name) || json.get(name) == null) {
            throw new IllegalArgumentException("Missing JSON field: " + name);
        }
        return json.get(name);
    }

    private static JsonObject object(JsonElement value, String label) {
        if (value == null || !value.isJsonObject()) {
            throw new IllegalArgumentException(label + " must be a JSON object");
        }
        return value.getAsJsonObject();
    }

    private static void requireFields(JsonObject json, String label, String... names) {
        Set<String> expected = Set.of(names);
        Set<String> actual = new HashSet<>(json.keySet());
        if (!actual.equals(expected)) {
            HashSet<String> missing = new HashSet<>(expected);
            missing.removeAll(actual);
            HashSet<String> unknown = new HashSet<>(actual);
            unknown.removeAll(expected);
            throw new IllegalArgumentException(
                    label + " JSON fields disagree; missing=" + missing + ", unknown=" + unknown
            );
        }
    }
}
