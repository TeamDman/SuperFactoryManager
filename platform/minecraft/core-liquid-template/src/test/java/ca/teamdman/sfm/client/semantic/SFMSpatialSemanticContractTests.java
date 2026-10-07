package ca.teamdman.sfm.client.semantic;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SFMSpatialSemanticContractTests {
    @Test
    void canonicalBundleRoundTripsWithFrozenSnakeCaseSchemas() throws Exception {
        String expected = Files.readString(fixture("contract-expected.json"), StandardCharsets.UTF_8);
        SFMSpatialSemanticContract.Bundle bundle = SFMSpatialSemanticJsonCodec.decode(expected);
        String actual = SFMSpatialSemanticJsonCodec.encodePretty(bundle);

        assertEquals(JsonParser.parseString(expected), JsonParser.parseString(actual));
        assertEquals(bundle, SFMSpatialSemanticJsonCodec.decode(actual));
        JsonObject json = JsonParser.parseString(actual).getAsJsonObject();
        assertEquals(SFMSpatialSemanticContract.DOMAIN_SCHEMA,
                json.getAsJsonObject("domain").get("schema").getAsString());
        assertEquals(SFMSpatialSemanticContract.PROBE_SCHEMA,
                json.getAsJsonObject("probe").get("schema").getAsString());
        assertTrue(actual.contains("\"workspace_generation\""));
        assertFalse(actual.contains("workspaceGeneration"));
    }

    @Test
    void halfOpenBoundsOwnExactEdgesWithoutOverlap() {
        var left = region("left", 0, 10);
        var right = region("right", 10, 20);

        assertTrue(left.contains(List.of(0.0, 0.0)));
        assertTrue(left.contains(List.of(9.999, 4.999)));
        assertFalse(left.contains(List.of(10.0, 2.0)));
        assertTrue(right.contains(List.of(10.0, 2.0)));
        assertFalse(right.contains(List.of(20.0, 2.0)));
        assertThrows(IllegalArgumentException.class,
                () -> new SFMSpatialSemanticContract.AxisBound(2, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new SFMSpatialSemanticContract.AxisBound(Double.NaN, 1));
    }

    @Test
    void actionableNavigationCannotBeSatisfiedByInspectOnlyAction() {
        var certified = region("word", 0, 10);
        assertThrows(IllegalArgumentException.class, () -> new SFMSpatialSemanticContract.Probe(
                SFMSpatialSemanticContract.PROBE_SCHEMA,
                certified.domainId(),
                List.of(5.0, 2.0),
                SFMSpatialSemanticContract.Intent.NAVIGATE,
                certified,
                new SFMSpatialSemanticContract.Classification(
                        SFMSpatialSemanticContract.ClassificationStatus.ACTIONABLE, null),
                List.of(),
                List.of(),
                List.of(),
                1, 2, 3, 4
        ));
        assertThrows(IllegalArgumentException.class, () -> new SFMSpatialSemanticContract.Classification(
                SFMSpatialSemanticContract.ClassificationStatus.EXPLICIT_NO_ACTION, null));
    }

    @Test
    void corpusRetainsRequiredJavaAndLayoutWitnesses() throws Exception {
        String java = Files.readString(fixture("source/example/SpatialFixture.java"), StandardCharsets.UTF_8);
        String malformed = Files.readString(fixture("source/example/Malformed.java.txt"), StandardCharsets.UTF_8);
        String layout = Files.readString(fixture("layout-cases.json"), StandardCharsets.UTF_8);

        for (String witness : List.of(
                "import java.util.List", "@Deprecated", "final class", "class Nested",
                "int café", "String local", "render(List<String> values)", "{", "}", ";",
                "||", ">", "\"wide: 界; emoji: 🦀\"", "// A deliberate comment region.")) {
            assertTrue(java.contains(witness), () -> "Missing Java witness: " + witness);
        }
        assertTrue(malformed.contains("void incomplete( {"));
        JsonObject layoutJson = JsonParser.parseString(layout).getAsJsonObject();
        assertTrue(layoutJson.get("source").getAsString().contains("\r\n"));
        List<String> scales = new ArrayList<>();
        layoutJson.getAsJsonArray("required_gui_scales")
                .forEach(element -> scales.add(element.getAsString()));
        assertEquals(List.of("auto", "1", "2", "3", "4", "5", "6", "7", "8"), scales);
        assertTrue(layout.contains("narrow-long-line"));
        assertTrue(layout.contains("wide-scale"));
        assertTrue(layout.contains("\"pan\""));
        assertTrue(layout.contains("\"zoom\""));
    }

    @Test
    void reportKeepsSeparateDenominatorsAndFailedFileRows() throws Exception {
        var report = SFMSpatialSemanticJsonCodec.decode(
                Files.readString(fixture("contract-expected.json"))).coverageReport();
        assertEquals(List.of("classification", "navigation", "real-gesture", "branch-boundary", "reciprocity"),
                report.dimensions().stream().map(SFMSpatialSemanticContract.CoverageDimension::id).toList());
        assertEquals(2, report.files().size());
        assertEquals(SFMSpatialSemanticContract.FileState.PARSE_FAILED, report.files().get(1).state());
        assertFalse(report.files().get(1).diagnostic().isBlank());
    }

    private static SFMSpatialSemanticContract.Region region(String id, double left, double right) {
        return new SFMSpatialSemanticContract.Region(
                SFMSpatialSemanticContract.REGION_SCHEMA,
                id,
                "canvas:test",
                SFMSpatialSemanticContract.Representation.RECTANGLE,
                List.of(
                        new SFMSpatialSemanticContract.AxisBound(left, right),
                        new SFMSpatialSemanticContract.AxisBound(0, 5)
                ),
                "half-open",
                "test",
                "sfm:test",
                List.of()
        );
    }

    private static Path fixture(String relative) {
        Path cursor = Path.of("").toAbsolutePath();
        for (int depth = 0; depth < 8 && cursor != null; depth++, cursor = cursor.getParent()) {
            Path candidate = cursor.resolve("docs/architecture/fixtures/spatial-semantic-v1").resolve(relative);
            if (Files.isRegularFile(candidate)) return candidate;
        }
        throw new IllegalStateException("Unable to locate spatial semantic fixture " + relative);
    }
}
