package ca.teamdman.sfm.client.symbol;

import ca.teamdman.sfm.client.semantic.SFMJavaInteractionMapSpatialAdapter;
import ca.teamdman.sfm.client.semantic.SFMSpatialSemanticContract;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SFMJavaInteractionMapSpatialAdapterTests {
    @Test
    void projectsTheRustRegionClassificationAndOutlinkIntoTheCanvasOracle() {
        SFMJavaInteractionMap.Request request = SFMJavaInteractionMapProtocolTests.request();
        SFMJavaInteractionMap.Result result = SFMJavaInteractionMapJsonCodec.decodeResult(
                SFMJavaInteractionMapProtocolTests.resultJson(request).toString());
        SFMJavaInteractionMapSpatialAdapter adapter =
                new SFMJavaInteractionMapSpatialAdapter(request.document().text(), result);

        var semantic = adapter.atUtf16(2).orElseThrow();

        assertEquals(SFMSpatialSemanticContract.ClassificationStatus.ACTIONABLE,
                semantic.classification().status());
        assertEquals(SFMSpatialSemanticContract.Intent.NAVIGATE,
                semantic.outlinks().get(0).intent());
        assertEquals("definition", semantic.outlinks().get(0).relationKind());
        assertEquals(result.semanticGeneration(), semantic.semanticGeneration());
        assertTrue(semantic.reciprocityExpected());
        assertSame(semantic, adapter.atUtf16(3).orElseThrow(),
                "Offsets in one semantic region should reuse the indexed result");
    }

    @Test
    void rejectsAResultForDifferentDocumentBytes() {
        SFMJavaInteractionMap.Request request = SFMJavaInteractionMapProtocolTests.request();
        SFMJavaInteractionMap.Result result = SFMJavaInteractionMapJsonCodec.decodeResult(
                SFMJavaInteractionMapProtocolTests.resultJson(request).toString());

        assertThrows(IllegalArgumentException.class,
                () -> new SFMJavaInteractionMapSpatialAdapter("class B {}\n", result));
    }

    @Test
    void equalSpanSemanticReferenceOutranksRawSyntaxRegion() {
        SFMJavaInteractionMap.Request request = SFMJavaInteractionMapProtocolTests.request();
        JsonObject json = SFMJavaInteractionMapProtocolTests.resultJson(request);

        JsonArray regions = json.getAsJsonArray("regions");
        JsonObject semanticRegion = regions.get(0).getAsJsonObject();
        semanticRegion.addProperty("id", "region:z-semantic-reference");
        semanticRegion.addProperty("semantic_kind", "java-local-reference");
        JsonObject rawRegion = semanticRegion.deepCopy();
        rawRegion.addProperty("id", "region:a-raw-identifier");
        rawRegion.addProperty("semantic_kind", "java-identifier");
        regions.add(rawRegion);

        JsonArray classifications = json.getAsJsonArray("classifications");
        classifications.get(0).getAsJsonObject()
                .addProperty("region_id", "region:z-semantic-reference");
        JsonObject rawClassification = new JsonObject();
        rawClassification.addProperty("region_id", "region:a-raw-identifier");
        rawClassification.addProperty("status", "explicit-no-action");
        rawClassification.addProperty("reason_code", "raw-syntax-only");
        rawClassification.add("navigation_outlink_ids", new JsonArray());
        rawClassification.add("contextual_action_ids", new JsonArray());
        classifications.add(rawClassification);

        JsonArray outlinks = json.getAsJsonArray("outlinks");
        outlinks.get(0).getAsJsonObject()
                .addProperty("source_region_id", "region:z-semantic-reference");
        outlinks.get(0).getAsJsonObject()
                .addProperty("destination_region_id", "region:z-semantic-reference");
        outlinks.get(1).getAsJsonObject()
                .addProperty("destination_region_id", "region:z-semantic-reference");

        SFMJavaInteractionMap.Result result = SFMJavaInteractionMapJsonCodec.decodeResult(json.toString());
        SFMJavaInteractionMapSpatialAdapter adapter =
                new SFMJavaInteractionMapSpatialAdapter(request.document().text(), result);

        var semantic = adapter.atUtf16(2).orElseThrow();
        assertEquals("definition", semantic.outlinks().get(0).relationKind());
        assertEquals("sfm:java-interaction-map/java-local-reference", semantic.providerBranch());
    }
}
