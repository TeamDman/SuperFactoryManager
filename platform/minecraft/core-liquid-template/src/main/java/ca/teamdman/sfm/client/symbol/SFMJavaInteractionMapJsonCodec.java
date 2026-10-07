package ca.teamdman.sfm.client.symbol;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Strict snake-case JSON adapter shared with the Rust interaction-map contract. */
public final class SFMJavaInteractionMapJsonCodec {
    private SFMJavaInteractionMapJsonCodec() {
    }

    public static String encodeRequest(SFMJavaInteractionMap.Request request) {
        return encodeRequestObject(request).toString();
    }

    public static SFMJavaInteractionMap.Request decodeRequest(String json) {
        JsonObject value = object(JsonParser.parseString(json), "interaction-map request");
        return new SFMJavaInteractionMap.Request(
                string(value, "schema"),
                nonNegativeLong(value, "request_id"),
                nonNegativeLong(value, "request_generation"),
                SFMDefinitionJsonCodec.decodeWorkspaceObject(requiredObject(value, "workspace")),
                SFMDefinitionJsonCodec.decodeDocumentObject(requiredObject(value, "document")),
                readWindow(requiredObject(value, "window")),
                optionalString(value, "known_semantic_fingerprint")
        );
    }

    public static SFMJavaInteractionMap.Result decodeResult(String json) {
        return decodeResultObject(object(JsonParser.parseString(json), "interaction-map result"));
    }

    static JsonObject encodeRequestObject(SFMJavaInteractionMap.Request value) {
        JsonObject json = new JsonObject();
        json.addProperty("schema", value.schema());
        json.addProperty("request_id", value.requestId());
        json.addProperty("request_generation", value.requestGeneration());
        json.add("workspace", SFMDefinitionJsonCodec.encodeWorkspaceObject(value.workspace()));
        json.add("document", SFMDefinitionJsonCodec.encodeDocumentObject(value.document()));
        json.add("window", writeWindow(value.window()));
        value.knownSemanticFingerprint().ifPresent(fingerprint ->
                json.addProperty("known_semantic_fingerprint", fingerprint));
        return json;
    }

    static SFMJavaInteractionMap.Result decodeResultObject(JsonObject json) {
        return new SFMJavaInteractionMap.Result(
                string(json, "schema"),
                nonNegativeLong(json, "request_id"),
                nonNegativeLong(json, "request_generation"),
                nonNegativeLong(json, "workspace_generation"),
                string(json, "workspace_fingerprint"),
                nonNegativeLong(json, "document_generation"),
                nonNegativeLong(json, "semantic_generation"),
                string(json, "semantic_fingerprint"),
                SFMJavaInteractionMap.Outcome.fromWireName(string(json, "outcome")),
                SFMDefinitionJsonCodec.decodeDocumentIdentityObject(requiredObject(json, "document")),
                objects(json, "domains").stream().map(SFMJavaInteractionMapJsonCodec::readDomain).toList(),
                objects(json, "projections").stream().map(SFMJavaInteractionMapJsonCodec::readProjection).toList(),
                objects(json, "regions").stream().map(SFMJavaInteractionMapJsonCodec::readRegion).toList(),
                objects(json, "classifications").stream()
                        .map(SFMJavaInteractionMapJsonCodec::readClassification).toList(),
                objects(json, "outlinks").stream().map(SFMJavaInteractionMapJsonCodec::readOutlink).toList(),
                objects(json, "reciprocity").stream().map(SFMJavaInteractionMapJsonCodec::readReciprocity).toList(),
                objects(json, "exceptions").stream().map(SFMJavaInteractionMapJsonCodec::readException).toList(),
                objects(json, "files").stream().map(SFMJavaInteractionMapJsonCodec::readFile).toList(),
                readPage(requiredObject(json, "page")),
                objects(json, "diagnostics").stream()
                        .map(SFMDefinitionJsonCodec::decodeDiagnosticObject).toList()
        );
    }

    private static JsonObject writeWindow(SFMJavaInteractionMap.Window value) {
        JsonObject json = new JsonObject();
        json.addProperty("region_offset", value.regionOffset());
        json.addProperty("max_regions", value.maximumRegions());
        json.addProperty("inventory_offset", value.inventoryOffset());
        json.addProperty("max_inventory_files", value.maximumInventoryFiles());
        json.addProperty("max_encoded_bytes", value.maximumEncodedBytes());
        return json;
    }

    private static SFMJavaInteractionMap.Window readWindow(JsonObject json) {
        return new SFMJavaInteractionMap.Window(
                nonNegativeLong(json, "region_offset"),
                nonNegativeLong(json, "max_regions"),
                nonNegativeLong(json, "inventory_offset"),
                nonNegativeLong(json, "max_inventory_files"),
                nonNegativeLong(json, "max_encoded_bytes")
        );
    }

    private static SFMJavaInteractionMap.Domain readDomain(JsonObject json) {
        return new SFMJavaInteractionMap.Domain(
                string(json, "schema"),
                string(json, "id"),
                string(json, "kind"),
                nonNegativeLong(json, "dimensions"),
                strings(json, "coordinate_kinds"),
                string(json, "authority"),
                string(json, "snapshot_identity")
        );
    }

    private static SFMJavaInteractionMap.Projection readProjection(JsonObject json) {
        return new SFMJavaInteractionMap.Projection(
                string(json, "schema"),
                string(json, "id"),
                string(json, "from_domain_id"),
                string(json, "to_domain_id"),
                string(json, "loss"),
                string(json, "completeness"),
                string(json, "transform"),
                string(json, "fingerprint"),
                string(json, "authority")
        );
    }

    private static SFMJavaInteractionMap.Region readRegion(JsonObject json) {
        return new SFMJavaInteractionMap.Region(
                string(json, "schema"),
                string(json, "id"),
                string(json, "domain_id"),
                string(json, "representation"),
                objects(json, "bounds").stream().map(axis -> new SFMJavaInteractionMap.Axis(
                        nonNegativeLong(axis, "start_inclusive"),
                        nonNegativeLong(axis, "end_exclusive")
                )).toList(),
                string(json, "edge_policy"),
                string(json, "semantic_kind"),
                string(json, "provenance"),
                strings(json, "projection_ids")
        );
    }

    private static SFMJavaInteractionMap.Classification readClassification(JsonObject json) {
        return new SFMJavaInteractionMap.Classification(
                string(json, "region_id"),
                SFMJavaInteractionMap.ClassificationStatus.fromWireName(string(json, "status")),
                optionalString(json, "reason_code"),
                strings(json, "navigation_outlink_ids"),
                strings(json, "contextual_action_ids")
        );
    }

    private static SFMJavaInteractionMap.Outlink readOutlink(JsonObject json) {
        return new SFMJavaInteractionMap.Outlink(
                string(json, "schema"),
                string(json, "id"),
                string(json, "source_region_id"),
                stringAllowEmpty(json, "destination_region_id"),
                optionalString(json, "destination_query"),
                string(json, "relation_kind"),
                string(json, "intent"),
                string(json, "provider_id"),
                nonNegativeLong(json, "provider_generation"),
                string(json, "reason"),
                string(json, "confidence"),
                string(json, "completeness"),
                string(json, "recommended_projection"),
                objects(json, "action_drafts").stream().map(SFMJavaInteractionMapJsonCodec::readActionDraft).toList(),
                string(json, "provenance")
        );
    }

    private static SFMJavaInteractionMap.ActionDraft readActionDraft(JsonObject json) {
        return new SFMJavaInteractionMap.ActionDraft(
                string(json, "action_id"),
                strings(json, "arguments")
        );
    }

    private static SFMJavaInteractionMap.Reciprocity readReciprocity(JsonObject json) {
        return new SFMJavaInteractionMap.Reciprocity(
                string(json, "definition_outlink_id"),
                string(json, "reference_outlink_id"),
                SFMJavaInteractionMap.ReciprocityStatus.fromWireName(string(json, "status")),
                optionalString(json, "exception_code"),
                string(json, "witness")
        );
    }

    private static SFMJavaInteractionMap.ExceptionWitness readException(JsonObject json) {
        return new SFMJavaInteractionMap.ExceptionWitness(
                string(json, "id"),
                string(json, "region_id"),
                string(json, "code"),
                string(json, "reason"),
                SFMJavaInteractionMap.ExceptionEffect.fromWireName(string(json, "effect")),
                string(json, "witness")
        );
    }

    private static SFMJavaInteractionMap.FileRow readFile(JsonObject json) {
        return new SFMJavaInteractionMap.FileRow(
                string(json, "address"),
                string(json, "resolver_id"),
                string(json, "root_id"),
                string(json, "root_relative_path"),
                string(json, "report_path"),
                string(json, "source_set"),
                optionalString(json, "content_hash"),
                SFMJavaInteractionMap.FileState.fromWireName(string(json, "state")),
                optionalString(json, "diagnostic")
        );
    }

    private static SFMJavaInteractionMap.Page readPage(JsonObject json) {
        return new SFMJavaInteractionMap.Page(
                nonNegativeLong(json, "region_offset"),
                nonNegativeLong(json, "returned_regions"),
                nonNegativeLong(json, "total_regions"),
                optionalLong(json, "next_region_offset"),
                nonNegativeLong(json, "inventory_offset"),
                nonNegativeLong(json, "returned_inventory_files"),
                nonNegativeLong(json, "total_inventory_files"),
                optionalLong(json, "next_inventory_offset"),
                nonNegativeLong(json, "encoded_bytes")
        );
    }

    private static JsonObject object(JsonElement element, String label) {
        if (element == null || !element.isJsonObject()) {
            throw new IllegalArgumentException(label + " must be an object");
        }
        return element.getAsJsonObject();
    }

    private static JsonObject requiredObject(JsonObject json, String name) {
        JsonElement element = json.get(name);
        if (element == null || element instanceof JsonNull || !element.isJsonObject()) {
            throw new IllegalArgumentException(name + " must be an object");
        }
        return element.getAsJsonObject();
    }

    private static List<JsonObject> objects(JsonObject json, String name) {
        JsonElement element = json.get(name);
        if (element == null || !element.isJsonArray()) {
            throw new IllegalArgumentException(name + " must be an array");
        }
        ArrayList<JsonObject> values = new ArrayList<>();
        for (JsonElement value : element.getAsJsonArray()) values.add(object(value, name + "[]"));
        return List.copyOf(values);
    }

    private static List<String> strings(JsonObject json, String name) {
        JsonElement element = json.get(name);
        if (element == null || !element.isJsonArray()) {
            throw new IllegalArgumentException(name + " must be an array");
        }
        ArrayList<String> values = new ArrayList<>();
        for (JsonElement value : element.getAsJsonArray()) {
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
                throw new IllegalArgumentException(name + "[] must be a string");
            }
            values.add(value.getAsString());
        }
        return List.copyOf(values);
    }

    private static String string(JsonObject json, String name) {
        String value = stringAllowEmpty(json, name);
        if (value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value;
    }

    private static String stringAllowEmpty(JsonObject json, String name) {
        JsonElement element = json.get(name);
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException(name + " must be a string");
        }
        return element.getAsString();
    }

    private static Optional<String> optionalString(JsonObject json, String name) {
        JsonElement element = json.get(name);
        if (element == null || element instanceof JsonNull) return Optional.empty();
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException(name + " must be a string when present");
        }
        return Optional.of(element.getAsString());
    }

    private static long nonNegativeLong(JsonObject json, String name) {
        JsonElement element = json.get(name);
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException(name + " must be an integer");
        }
        long value = element.getAsLong();
        if (value < 0) throw new IllegalArgumentException(name + " must not be negative");
        return value;
    }

    private static Optional<Long> optionalLong(JsonObject json, String name) {
        JsonElement element = json.get(name);
        if (element == null || element instanceof JsonNull) return Optional.empty();
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException(name + " must be an integer when present");
        }
        long value = element.getAsLong();
        if (value < 0) throw new IllegalArgumentException(name + " must not be negative");
        return Optional.of(value);
    }
}
