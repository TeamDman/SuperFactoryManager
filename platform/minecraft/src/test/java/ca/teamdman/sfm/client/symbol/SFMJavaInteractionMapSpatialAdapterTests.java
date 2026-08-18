package ca.teamdman.sfm.client.symbol;

import ca.teamdman.sfm.client.semantic.SFMJavaInteractionMapSpatialAdapter;
import ca.teamdman.sfm.client.semantic.SFMSpatialSemanticContract;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    }

    @Test
    void rejectsAResultForDifferentDocumentBytes() {
        SFMJavaInteractionMap.Request request = SFMJavaInteractionMapProtocolTests.request();
        SFMJavaInteractionMap.Result result = SFMJavaInteractionMapJsonCodec.decodeResult(
                SFMJavaInteractionMapProtocolTests.resultJson(request).toString());

        assertThrows(IllegalArgumentException.class,
                () -> new SFMJavaInteractionMapSpatialAdapter("class B {}\n", result));
    }
}
