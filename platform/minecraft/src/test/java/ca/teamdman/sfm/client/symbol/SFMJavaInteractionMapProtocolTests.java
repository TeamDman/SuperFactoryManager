package ca.teamdman.sfm.client.symbol;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SFMJavaInteractionMapProtocolTests {
    @Test
    void requestRoundTripsAndUsesTheNegotiatedSymbolServerFrame() throws Exception {
        SFMJavaInteractionMap.Request request = request();
        assertEquals(request, SFMJavaInteractionMapJsonCodec.decodeRequest(
                SFMJavaInteractionMapJsonCodec.encodeRequest(request)));

        JsonObject envelope = JsonParser.parseString(
                SFMSymbolServerProtocol.javaInteractionMap(request)).getAsJsonObject();
        assertEquals("java-interaction-map", envelope.get("kind").getAsString());
        assertEquals(SFMSymbolServerProtocol.JAVA_INTERACTION_MAP_SCHEMA,
                envelope.get("schema").getAsString());
        assertEquals(request, SFMJavaInteractionMapJsonCodec.decodeRequest(
                envelope.getAsJsonObject("request").toString()));
        JsonObject cancellation = JsonParser.parseString(
                SFMSymbolServerProtocol.cancel(request, "test")).getAsJsonObject();
        assertEquals(request.requestId(), cancellation.get("request_id").getAsLong());
        assertEquals(request.requestGeneration(), cancellation.get("request_generation").getAsLong());
        assertEquals(request.workspace().workspaceGeneration(),
                cancellation.get("workspace_generation").getAsLong());
    }

    @Test
    void resultFrameRetainsNestedRegionsOutlinksInventoryAndGenerationIdentity() throws Exception {
        SFMJavaInteractionMap.Request request = request();
        JsonObject envelope = new JsonObject();
        envelope.addProperty("kind", "java-interaction-map-result");
        envelope.addProperty("schema", SFMSymbolServerProtocol.JAVA_INTERACTION_MAP_SCHEMA);
        envelope.add("result", resultJson(request));

        var frame = assertInstanceOf(
                SFMSymbolServerProtocol.JavaInteractionMapResultFrame.class,
                SFMSymbolServerProtocol.decodeServerFrame(envelope.toString())
        );
        SFMJavaInteractionMap.Result result = frame.result();
        assertTrue(result.matches(request));
        assertEquals("identifier", result.mostSpecificRegionAtByte(2).orElseThrow().semanticKind());
        SFMJavaInteractionMap.Classification classification =
                result.classification("region:identifier").orElseThrow();
        assertEquals(SFMJavaInteractionMap.ClassificationStatus.ACTIONABLE, classification.status());
        assertEquals("definition", result.navigationOutlinks(classification).get(0).relationKind());
        assertEquals(2, result.outlinks().size());
        assertTrue(result.outlinks().get(0).destinationQuery().isPresent());
        assertEquals(SFMJavaInteractionMap.FileState.COVERED, result.files().get(0).state());
        assertEquals(9, result.semanticGeneration());
    }

    @Test
    void clientHelloAdvertisesTheInteractionMapCapability() {
        JsonObject hello = JsonParser.parseString(
                SFMSymbolServerProtocol.hello("minecraft", "1", 16_777_216)
        ).getAsJsonObject().getAsJsonObject("hello");
        boolean found = false;
        for (var value : hello.getAsJsonArray("capabilities")) {
            if (value.getAsString().equals(SFMSymbolServerProtocol.CAPABILITY_JAVA_INTERACTION_MAP)) {
                found = true;
                break;
            }
        }
        assertTrue(found);
    }

    @Test
    void emptyHalfOpenAxisMatchesTheFrozenRegionContract() {
        SFMJavaInteractionMap.Axis empty = new SFMJavaInteractionMap.Axis(4, 4);
        assertEquals(4, empty.startInclusive());
        assertEquals(4, empty.endExclusive());
        assertFalse(empty.contains(4));
    }

    static SFMJavaInteractionMap.Request request() {
        String text = "class A {}\n";
        SFMDefinitionRequest.SourceRoot root = new SFMDefinitionRequest.SourceRoot(
                "custom-0", "custom", "source", "custom", true);
        SFMDefinitionRequest.Workspace workspace = new SFMDefinitionRequest.Workspace(
                "1.19.2",
                SFMDefinitionRequest.ClasspathMode.ISOLATED,
                List.of(root),
                "blake3:workspace",
                Optional.empty(),
                "blake3:" + "0".repeat(64),
                7
        );
        SFMDefinitionRequest.Document document = SFMDefinitionRequest.Document.sha256(
                "file:///D:/workspace/source/A.java",
                root.id(),
                "A.java",
                "source/A.java",
                root.sourceSet(),
                text,
                Optional.empty()
        );
        return new SFMJavaInteractionMap.Request(17, 3, workspace, document);
    }

    static JsonObject resultJson(SFMJavaInteractionMap.Request request) {
        JsonObject result = new JsonObject();
        result.addProperty("schema", SFMJavaInteractionMap.RESULT_SCHEMA);
        result.addProperty("request_id", request.requestId());
        result.addProperty("request_generation", request.requestGeneration());
        result.addProperty("workspace_generation", request.workspace().workspaceGeneration());
        result.addProperty("workspace_fingerprint", request.workspace().workspaceFingerprint());
        result.addProperty("document_generation", request.requestGeneration());
        result.addProperty("semantic_generation", 9);
        result.addProperty("semantic_fingerprint", "blake3:" + "1".repeat(64));
        result.addProperty("outcome", "success");
        result.add("document", documentIdentity(request.document()));

        JsonObject domain = new JsonObject();
        domain.addProperty("schema", SFMJavaInteractionMap.DOMAIN_SCHEMA);
        domain.addProperty("id", "domain:source");
        domain.addProperty("kind", "utf8");
        domain.addProperty("dimensions", 1);
        domain.add("coordinate_kinds", strings("byte"));
        domain.addProperty("authority", "sfm:rust-java-index");
        domain.addProperty("snapshot_identity", request.document().contentHash());
        result.add("domains", array(domain));
        result.add("projections", new JsonArray());

        JsonObject region = new JsonObject();
        region.addProperty("schema", SFMJavaInteractionMap.REGION_SCHEMA);
        region.addProperty("id", "region:identifier");
        region.addProperty("domain_id", "domain:source");
        region.addProperty("representation", "source-interval");
        JsonObject axis = new JsonObject();
        axis.addProperty("start_inclusive", 0);
        axis.addProperty("end_exclusive", 5);
        region.add("bounds", array(axis));
        region.addProperty("edge_policy", "half-open");
        region.addProperty("semantic_kind", "identifier");
        region.addProperty("provenance", "fixture");
        region.add("projection_ids", new JsonArray());
        result.add("regions", array(region));

        JsonObject classification = new JsonObject();
        classification.addProperty("region_id", "region:identifier");
        classification.addProperty("status", "actionable");
        classification.add("reason_code", null);
        classification.add("navigation_outlink_ids", strings("outlink:definition"));
        classification.add("contextual_action_ids", new JsonArray());
        result.add("classifications", array(classification));

        JsonObject outlink = new JsonObject();
        outlink.addProperty("schema", SFMJavaInteractionMap.OUTLINK_SCHEMA);
        outlink.addProperty("id", "outlink:definition");
        outlink.addProperty("source_region_id", "region:identifier");
        outlink.addProperty("destination_region_id", "region:identifier");
        outlink.addProperty("destination_query", request.document().address());
        outlink.addProperty("relation_kind", "definition");
        outlink.addProperty("intent", "navigate");
        outlink.addProperty("provider_id", "sfm:rust-java-index");
        outlink.addProperty("provider_generation", 9);
        outlink.addProperty("reason", "fixture definition");
        outlink.addProperty("confidence", "resolved");
        outlink.addProperty("completeness", "complete");
        outlink.addProperty("recommended_projection", "start");
        outlink.add("action_drafts", new JsonArray());
        outlink.addProperty("provenance", "fixture");
        JsonObject reciprocal = outlink.deepCopy();
        reciprocal.addProperty("id", "outlink:reference");
        reciprocal.addProperty("source_region_id", "region:external-definition");
        reciprocal.addProperty("destination_region_id", "region:identifier");
        reciprocal.addProperty("relation_kind", "reference");
        result.add("outlinks", array(outlink, reciprocal));
        result.add("reciprocity", new JsonArray());
        result.add("exceptions", new JsonArray());

        JsonObject file = new JsonObject();
        file.addProperty("address", request.document().address());
        file.addProperty("resolver_id", "file");
        file.addProperty("root_id", request.document().rootId());
        file.addProperty("root_relative_path", request.document().rootRelativePath());
        file.addProperty("report_path", request.document().reportPath());
        file.addProperty("source_set", request.document().sourceSet());
        file.addProperty("content_hash", request.document().contentHash());
        file.addProperty("state", "covered");
        file.add("diagnostic", null);
        result.add("files", array(file));

        JsonObject page = new JsonObject();
        page.addProperty("region_offset", 0);
        page.addProperty("returned_regions", 1);
        page.addProperty("total_regions", 1);
        page.add("next_region_offset", null);
        page.addProperty("inventory_offset", 0);
        page.addProperty("returned_inventory_files", 1);
        page.addProperty("total_inventory_files", 1);
        page.add("next_inventory_offset", null);
        page.addProperty("encoded_bytes", 256);
        result.add("page", page);
        result.add("diagnostics", new JsonArray());
        return result;
    }

    private static JsonObject documentIdentity(SFMDefinitionRequest.Document document) {
        JsonObject json = new JsonObject();
        json.addProperty("address", document.address());
        json.addProperty("root_id", document.rootId());
        json.addProperty("root_relative_path", document.rootRelativePath());
        json.addProperty("report_path", document.reportPath());
        json.addProperty("source_set", document.sourceSet());
        json.addProperty("content_hash", document.contentHash());
        json.add("disk_content_hash", null);
        return json;
    }

    private static JsonArray strings(String... values) {
        JsonArray array = new JsonArray();
        for (String value : values) array.add(value);
        return array;
    }

    private static JsonArray array(JsonObject... values) {
        JsonArray array = new JsonArray();
        for (JsonObject value : values) array.add(value);
        return array;
    }
}
