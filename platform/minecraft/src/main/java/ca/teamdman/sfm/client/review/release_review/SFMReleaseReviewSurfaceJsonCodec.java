package ca.teamdman.sfm.client.review.release_review;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/** Strict snake-case JSON codec shared with the Rust review-surface Facet contract. */
public final class SFMReleaseReviewSurfaceJsonCodec {
    private SFMReleaseReviewSurfaceJsonCodec() {
    }

    public static String encodeRequest(SFMReleaseReviewSurfaceV1.Request request) {
        return writeRequest(request).toString();
    }

    public static SFMReleaseReviewSurfaceV1.Request decodeRequest(String json) {
        return readRequest(object(JsonParser.parseString(json), "request"));
    }

    public static String encodeSurface(SFMReleaseReviewSurfaceV1.Surface surface) {
        return writeSurface(surface).toString();
    }

    public static SFMReleaseReviewSurfaceV1.Surface decodeSurface(String json) {
        return readSurface(object(JsonParser.parseString(json), "surface"));
    }

    private static JsonObject writeRequest(SFMReleaseReviewSurfaceV1.Request value) {
        JsonObject json = new JsonObject();
        json.addProperty("schema", value.schema());
        json.addProperty("request_id", value.requestId());
        json.addProperty("request_generation", value.requestGeneration());
        json.add("file_pair", writeFilePair(value.filePair()));
        json.addProperty("surface_kind", value.surfaceKind().wireName());
        json.addProperty("context_lines", value.contextLines());
        json.addProperty("maximum_output_bytes", value.maximumOutputBytes());
        json.addProperty("maximum_mappings", value.maximumMappings());
        json.addProperty("maximum_regions", value.maximumRegions());
        json.addProperty("maximum_diagnostics", value.maximumDiagnostics());
        return json;
    }

    private static SFMReleaseReviewSurfaceV1.Request readRequest(JsonObject json) {
        fields(json, "request", Set.of(
                "schema", "request_id", "request_generation", "file_pair", "surface_kind", "context_lines",
                "maximum_output_bytes", "maximum_mappings", "maximum_regions", "maximum_diagnostics"), Set.of());
        return new SFMReleaseReviewSurfaceV1.Request(
                string(json, "schema"),
                positiveLong(json, "request_id"),
                positiveLong(json, "request_generation"),
                readFilePair(object(required(json, "file_pair"), "file_pair")),
                SFMReleaseReviewSurfaceV1.SurfaceKind.fromWireName(string(json, "surface_kind")),
                nonNegativeInt(json, "context_lines"),
                positiveInt(json, "maximum_output_bytes"),
                positiveInt(json, "maximum_mappings"),
                positiveInt(json, "maximum_regions"),
                positiveInt(json, "maximum_diagnostics")
        );
    }

    private static JsonObject writeFilePair(SFMReleaseReviewSurfaceV1.FilePair value) {
        JsonObject json = new JsonObject();
        json.addProperty("schema", value.schema());
        json.addProperty("id", value.id());
        json.addProperty("lane_id", value.laneId());
        json.addProperty("operation", SFMReleaseReviewSurfaceV1.operationWireName(value.operation()));
        json.add("review_unit_ids", strings(value.reviewUnitIds()));
        value.before().ifPresent(source -> json.add("before", writeSource(source)));
        value.after().ifPresent(source -> json.add("after", writeSource(source)));
        return json;
    }

    private static SFMReleaseReviewSurfaceV1.FilePair readFilePair(JsonObject json) {
        fields(json, "file_pair", Set.of("schema", "id", "lane_id", "operation", "review_unit_ids"),
                Set.of("before", "after"));
        return new SFMReleaseReviewSurfaceV1.FilePair(
                string(json, "schema"),
                string(json, "id"),
                string(json, "lane_id"),
                SFMReleaseReviewSurfaceV1.operationFromWireName(string(json, "operation")),
                stringList(json, "review_unit_ids", SFMReleaseReviewSurfaceV1.DEFAULT_MAX_REGIONS),
                optionalObject(json, "before").map(SFMReleaseReviewSurfaceJsonCodec::readSource),
                optionalObject(json, "after").map(SFMReleaseReviewSurfaceJsonCodec::readSource)
        );
    }

    private static JsonObject writeSource(SFMReleaseReviewSurfaceV1.Source value) {
        JsonObject json = new JsonObject();
        json.addProperty("document_revision_id", value.documentRevisionId());
        json.addProperty("path", value.path());
        json.addProperty("language", value.language());
        json.addProperty("sha256", value.sha256());
        json.addProperty("text", value.text());
        return json;
    }

    private static SFMReleaseReviewSurfaceV1.Source readSource(JsonObject json) {
        fields(json, "source", Set.of("document_revision_id", "path", "language", "sha256", "text"), Set.of());
        return new SFMReleaseReviewSurfaceV1.Source(
                string(json, "document_revision_id"), string(json, "path"), string(json, "language"),
                string(json, "sha256"), string(json, "text"));
    }

    private static JsonObject writeSurface(SFMReleaseReviewSurfaceV1.Surface value) {
        JsonObject json = new JsonObject();
        json.addProperty("schema", value.schema());
        json.addProperty("request_id", value.requestId());
        json.addProperty("request_generation", value.requestGeneration());
        json.addProperty("file_pair_id", value.filePairId());
        json.addProperty("surface_kind", value.surfaceKind().wireName());
        json.addProperty("algorithm", value.algorithm());
        json.addProperty("outcome", value.outcome().wireName());
        json.addProperty("complete", value.complete());
        value.fallbackKind().ifPresent(kind -> json.addProperty("fallback_kind", kind.wireName()));
        json.addProperty("text", value.text());
        json.addProperty("text_sha256", value.textSha256());
        json.add("mappings", objects(value.mappings(), SFMReleaseReviewSurfaceJsonCodec::writeMapping));
        json.add("regions", objects(value.regions(), SFMReleaseReviewSurfaceJsonCodec::writeRegion));
        json.add("correspondence", writeCorrespondenceReport(value.correspondence()));
        json.add("diagnostics", objects(value.diagnostics(), SFMReleaseReviewSurfaceJsonCodec::writeDiagnostic));
        return json;
    }

    private static SFMReleaseReviewSurfaceV1.Surface readSurface(JsonObject json) {
        fields(json, "surface", Set.of(
                        "schema", "request_id", "request_generation", "file_pair_id", "surface_kind", "algorithm",
                        "outcome", "complete", "text", "text_sha256", "mappings", "regions", "correspondence",
                        "diagnostics"),
                Set.of("fallback_kind"));
        return new SFMReleaseReviewSurfaceV1.Surface(
                string(json, "schema"),
                positiveLong(json, "request_id"),
                positiveLong(json, "request_generation"),
                string(json, "file_pair_id"),
                SFMReleaseReviewSurfaceV1.SurfaceKind.fromWireName(string(json, "surface_kind")),
                string(json, "algorithm"),
                SFMReleaseReviewSurfaceV1.Outcome.fromWireName(string(json, "outcome")),
                bool(json, "complete"),
                optionalString(json, "fallback_kind").map(SFMReleaseReviewSurfaceV1.SurfaceKind::fromWireName),
                string(json, "text"),
                string(json, "text_sha256"),
                objectList(json, "mappings", SFMReleaseReviewSurfaceJsonCodec::readMapping,
                        SFMReleaseReviewSurfaceV1.DEFAULT_MAX_MAPPINGS),
                objectList(json, "regions", SFMReleaseReviewSurfaceJsonCodec::readRegion,
                        SFMReleaseReviewSurfaceV1.DEFAULT_MAX_REGIONS),
                readCorrespondenceReport(object(required(json, "correspondence"), "correspondence")),
                objectList(json, "diagnostics", SFMReleaseReviewSurfaceJsonCodec::readDiagnostic,
                        SFMReleaseReviewSurfaceV1.DEFAULT_MAX_DIAGNOSTICS)
        );
    }

    private static JsonObject writeMapping(SFMReleaseReviewSurfaceV1.Mapping value) {
        JsonObject json = new JsonObject();
        json.add("surface_range", writeRange(value.surfaceRange()));
        json.addProperty("kind", value.kind().wireName());
        json.add("source_ranges", objects(value.sourceRanges(), SFMReleaseReviewSurfaceJsonCodec::writeSourceRange));
        return json;
    }

    private static SFMReleaseReviewSurfaceV1.Mapping readMapping(JsonObject json) {
        fields(json, "mapping", Set.of("surface_range", "kind", "source_ranges"), Set.of());
        return new SFMReleaseReviewSurfaceV1.Mapping(
                readRange(object(required(json, "surface_range"), "surface_range")),
                SFMReleaseReviewSurfaceV1.MappingKind.fromWireName(string(json, "kind")),
                objectList(json, "source_ranges", SFMReleaseReviewSurfaceJsonCodec::readSourceRange, 16));
    }

    private static JsonObject writeRegion(SFMReleaseReviewSurfaceV1.Region value) {
        JsonObject json = new JsonObject();
        json.addProperty("id", value.id());
        json.addProperty("kind", value.kind().wireName());
        json.addProperty("label", value.label());
        json.add("surface_range", writeRange(value.surfaceRange()));
        json.add("source_ranges", objects(value.sourceRanges(), SFMReleaseReviewSurfaceJsonCodec::writeSourceRange));
        return json;
    }

    private static SFMReleaseReviewSurfaceV1.Region readRegion(JsonObject json) {
        fields(json, "region", Set.of("id", "kind", "label", "surface_range", "source_ranges"), Set.of());
        return new SFMReleaseReviewSurfaceV1.Region(
                string(json, "id"),
                SFMReleaseReviewSurfaceV1.RegionKind.fromWireName(string(json, "kind")),
                string(json, "label"),
                readRange(object(required(json, "surface_range"), "surface_range")),
                objectList(json, "source_ranges", SFMReleaseReviewSurfaceJsonCodec::readSourceRange, 16));
    }

    private static JsonObject writeSourceRange(SFMReleaseReviewSurfaceV1.SourceRange value) {
        JsonObject json = new JsonObject();
        json.addProperty("side", SFMReleaseReviewSurfaceV1.sideWireName(value.side()));
        json.addProperty("document_revision_id", value.documentRevisionId());
        json.addProperty("document_sha256", value.documentSha256());
        json.addProperty("path", value.path());
        json.add("range", writeRange(value.range()));
        return json;
    }

    private static SFMReleaseReviewSurfaceV1.SourceRange readSourceRange(JsonObject json) {
        fields(json, "source_range", Set.of(
                "side", "document_revision_id", "document_sha256", "path", "range"), Set.of());
        return new SFMReleaseReviewSurfaceV1.SourceRange(
                SFMReleaseReviewSurfaceV1.sideFromWireName(string(json, "side")),
                string(json, "document_revision_id"),
                string(json, "document_sha256"),
                string(json, "path"),
                readRange(object(required(json, "range"), "range")));
    }

    private static JsonObject writeRange(SFMReleaseReviewSurfaceV1.Utf8Range value) {
        JsonObject json = new JsonObject();
        json.addProperty("start_byte", value.startByte());
        json.addProperty("end_byte", value.endByte());
        return json;
    }

    private static SFMReleaseReviewSurfaceV1.Utf8Range readRange(JsonObject json) {
        fields(json, "range", Set.of("start_byte", "end_byte"), Set.of());
        return new SFMReleaseReviewSurfaceV1.Utf8Range(
                nonNegativeInt(json, "start_byte"), nonNegativeInt(json, "end_byte"));
    }

    private static JsonObject writeDiagnostic(SFMReleaseReviewSurfaceV1.Diagnostic value) {
        JsonObject json = new JsonObject();
        json.addProperty("code", value.code());
        json.addProperty("severity", value.severity().wireName());
        json.addProperty("message", value.message());
        value.side().ifPresent(side -> json.addProperty("side", SFMReleaseReviewSurfaceV1.sideWireName(side)));
        value.sourceRange().ifPresent(range -> json.add("source_range", writeRange(range)));
        return json;
    }

    private static SFMReleaseReviewSurfaceV1.Diagnostic readDiagnostic(JsonObject json) {
        fields(json, "diagnostic", Set.of("code", "severity", "message"), Set.of("side", "source_range"));
        return new SFMReleaseReviewSurfaceV1.Diagnostic(
                string(json, "code"),
                SFMReleaseReviewSurfaceV1.Severity.fromWireName(string(json, "severity")),
                string(json, "message"),
                optionalString(json, "side").map(SFMReleaseReviewSurfaceV1::sideFromWireName),
                optionalObject(json, "source_range").map(SFMReleaseReviewSurfaceJsonCodec::readRange));
    }

    private static JsonObject writeCorrespondenceReport(SFMReleaseReviewSurfaceV1.CorrespondenceReport value) {
        JsonObject json = new JsonObject();
        json.addProperty("schema", value.schema());
        json.addProperty("file_pair_id", value.filePairId());
        json.addProperty("complete", value.complete());
        json.add("correspondences", objects(value.correspondences(),
                SFMReleaseReviewSurfaceJsonCodec::writeCorrespondence));
        json.add("diagnostics", objects(value.diagnostics(), SFMReleaseReviewSurfaceJsonCodec::writeDiagnostic));
        return json;
    }

    private static SFMReleaseReviewSurfaceV1.CorrespondenceReport readCorrespondenceReport(JsonObject json) {
        fields(json, "correspondence report", Set.of(
                "schema", "file_pair_id", "complete", "correspondences", "diagnostics"), Set.of());
        return new SFMReleaseReviewSurfaceV1.CorrespondenceReport(
                string(json, "schema"), string(json, "file_pair_id"), bool(json, "complete"),
                objectList(json, "correspondences", SFMReleaseReviewSurfaceJsonCodec::readCorrespondence,
                        SFMReleaseReviewSurfaceV1.DEFAULT_MAX_REGIONS),
                objectList(json, "diagnostics", SFMReleaseReviewSurfaceJsonCodec::readDiagnostic,
                        SFMReleaseReviewSurfaceV1.DEFAULT_MAX_DIAGNOSTICS));
    }

    private static JsonObject writeCorrespondence(SFMReleaseReviewSurfaceV1.Correspondence value) {
        JsonObject json = new JsonObject();
        json.addProperty("id", value.id());
        json.addProperty("kind", value.kind().wireName());
        json.addProperty("confidence", value.confidence().wireName());
        value.semanticKeyBefore().ifPresent(key -> json.addProperty("semantic_key_before", key));
        value.semanticKeyAfter().ifPresent(key -> json.addProperty("semantic_key_after", key));
        json.add("before_ranges", objects(value.beforeRanges(), SFMReleaseReviewSurfaceJsonCodec::writeSourceRange));
        json.add("after_ranges", objects(value.afterRanges(), SFMReleaseReviewSurfaceJsonCodec::writeSourceRange));
        json.add("evidence", strings(value.evidence()));
        return json;
    }

    private static SFMReleaseReviewSurfaceV1.Correspondence readCorrespondence(JsonObject json) {
        fields(json, "correspondence", Set.of(
                        "id", "kind", "confidence", "before_ranges", "after_ranges", "evidence"),
                Set.of("semantic_key_before", "semantic_key_after"));
        return new SFMReleaseReviewSurfaceV1.Correspondence(
                string(json, "id"),
                SFMReleaseReviewSurfaceV1.CorrespondenceKind.fromWireName(string(json, "kind")),
                SFMReleaseReviewSurfaceV1.CorrespondenceConfidence.fromWireName(string(json, "confidence")),
                optionalString(json, "semantic_key_before"),
                optionalString(json, "semantic_key_after"),
                objectList(json, "before_ranges", SFMReleaseReviewSurfaceJsonCodec::readSourceRange, 256),
                objectList(json, "after_ranges", SFMReleaseReviewSurfaceJsonCodec::readSourceRange, 256),
                stringList(json, "evidence", 256));
    }

    private static <T> JsonArray objects(List<T> values, Function<T, JsonObject> writer) {
        JsonArray answer = new JsonArray();
        values.forEach(value -> answer.add(writer.apply(value)));
        return answer;
    }

    private static JsonArray strings(List<String> values) {
        JsonArray answer = new JsonArray();
        values.forEach(answer::add);
        return answer;
    }

    private static <T> List<T> objectList(
            JsonObject owner,
            String name,
            Function<JsonObject, T> reader,
            int maximum
    ) {
        JsonArray values = array(owner, name);
        if (values.size() > maximum) throw new IllegalArgumentException(name + " exceeds collection limit");
        ArrayList<T> answer = new ArrayList<>(values.size());
        for (JsonElement value : values) answer.add(reader.apply(object(value, name + "[]")));
        return List.copyOf(answer);
    }

    private static List<String> stringList(JsonObject owner, String name, int maximum) {
        JsonArray values = array(owner, name);
        if (values.size() > maximum) throw new IllegalArgumentException(name + " exceeds collection limit");
        ArrayList<String> answer = new ArrayList<>(values.size());
        for (JsonElement value : values) {
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
                throw new IllegalArgumentException(name + " contains a non-string");
            }
            answer.add(value.getAsString());
        }
        return List.copyOf(answer);
    }

    private static Optional<JsonObject> optionalObject(JsonObject owner, String name) {
        if (!owner.has(name)) return Optional.empty();
        return Optional.of(object(owner.get(name), name));
    }

    private static Optional<String> optionalString(JsonObject owner, String name) {
        if (!owner.has(name)) return Optional.empty();
        return Optional.of(string(owner, name));
    }

    private static JsonArray array(JsonObject owner, String name) {
        JsonElement value = required(owner, name);
        if (!value.isJsonArray()) throw new IllegalArgumentException(name + " must be a JSON array");
        return value.getAsJsonArray();
    }

    private static String string(JsonObject owner, String name) {
        JsonElement value = required(owner, name);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException(name + " must be a JSON string");
        }
        return value.getAsString();
    }

    private static boolean bool(JsonObject owner, String name) {
        JsonElement value = required(owner, name);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) {
            throw new IllegalArgumentException(name + " must be a JSON boolean");
        }
        return value.getAsBoolean();
    }

    private static long positiveLong(JsonObject owner, String name) {
        long value = nonNegativeLong(owner, name);
        if (value == 0) throw new IllegalArgumentException(name + " must be positive");
        return value;
    }

    private static int positiveInt(JsonObject owner, String name) {
        int value = nonNegativeInt(owner, name);
        if (value == 0) throw new IllegalArgumentException(name + " must be positive");
        return value;
    }

    private static int nonNegativeInt(JsonObject owner, String name) {
        long value = nonNegativeLong(owner, name);
        if (value > Integer.MAX_VALUE) throw new IllegalArgumentException(name + " exceeds Java int range");
        return (int) value;
    }

    private static long nonNegativeLong(JsonObject owner, String name) {
        JsonElement value = required(owner, name);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException(name + " must be a non-negative integer");
        }
        String encoded = value.getAsString();
        if (!encoded.matches("0|[1-9][0-9]*")) {
            throw new IllegalArgumentException(name + " must be a non-negative integer");
        }
        try {
            return Long.parseLong(encoded);
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException(name + " exceeds Java's supported u64 range", error);
        }
    }

    private static JsonElement required(JsonObject owner, String name) {
        if (!owner.has(name) || owner.get(name) == null || owner.get(name).isJsonNull()) {
            throw new IllegalArgumentException("Missing JSON field: " + name);
        }
        return owner.get(name);
    }

    private static JsonObject object(JsonElement value, String label) {
        if (value == null || !value.isJsonObject()) throw new IllegalArgumentException(label + " must be an object");
        return value.getAsJsonObject();
    }

    private static void fields(JsonObject value, String label, Set<String> required, Set<String> optional) {
        Set<String> actual = new HashSet<>(value.keySet());
        HashSet<String> missing = new HashSet<>(required);
        missing.removeAll(actual);
        HashSet<String> unknown = new HashSet<>(actual);
        unknown.removeAll(required);
        unknown.removeAll(optional);
        if (!missing.isEmpty() || !unknown.isEmpty()) {
            throw new IllegalArgumentException(label + " JSON fields disagree; missing=" + missing
                    + ", unknown=" + unknown);
        }
    }
}
