package ca.teamdman.sfm.client.semantic;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SFMNavigationFramingPolicyTests {
    @Test
    void frozenLayoutCasesExecuteTheirFirstMidNarrowAndWideFramingPolicies() throws Exception {
        JsonObject fixture = JsonParser.parseString(Files.readString(
                fixture("layout-cases.json"), StandardCharsets.UTF_8)).getAsJsonObject();
        assertTrue(fixture.get("source").getAsString().contains("\t"));
        assertTrue(fixture.get("source").getAsString().contains("界🦀"));

        for (var element : fixture.getAsJsonArray("cases")) {
            JsonObject layoutCase = element.getAsJsonObject();
            String id = layoutCase.get("id").getAsString();
            double viewportWidth = layoutCase.getAsJsonArray("panel").get(0).getAsDouble();
            double viewportHeight = layoutCase.getAsJsonArray("panel").get(1).getAsDouble();
            double panX = layoutCase.getAsJsonArray("pan").get(0).getAsDouble();
            double panY = layoutCase.getAsJsonArray("pan").get(1).getAsDouble();
            double zoom = layoutCase.get("zoom").getAsDouble();
            boolean firstLine = id.startsWith("first-line");
            boolean narrow = id.startsWith("narrow-long-line");
            double lineTop = firstLine ? 0.0D : 600.0D;
            double lineRight = narrow ? 900.0D : 700.0D;
            double destinationLeft = narrow ? 620.0D : 120.0D;
            double destinationRight = narrow ? 650.0D : 160.0D;

            var request = new SFMNavigationFramingPolicy.Request(
                    "pane-" + id,
                    "file:///workspace/LayoutCase.java",
                    "sha256:" + id,
                    "region-" + id,
                    "start",
                    rect(0, 0, 900, 1000),
                    rect(0, lineTop, lineRight, lineTop + 10),
                    rect(destinationLeft, lineTop, destinationRight, lineTop + 10),
                    viewportWidth,
                    viewportHeight,
                    8,
                    new SFMSpatialSemanticContract.Camera(panX, panY, zoom)
            );
            var result = SFMNavigationFramingPolicy.choose(request);
            var replay = SFMNavigationFramingPolicy.choose(request);

            assertEquals(result, replay, () -> "Framing was not deterministic for " + id);
            assertTrue(result.observation().landmarkVisible(), () -> "Landmark was clipped for " + id);
            assertEquals(firstLine, result.observation().documentTopVisible(),
                    () -> "First/mid-file vertical policy disagreed for " + id);
            if (narrow) {
                assertFalse(result.observation().lineLeftVisible());
                assertEquals("line-left-does-not-fit", result.observation().clippingReason());
            } else {
                assertTrue(result.observation().lineLeftVisible(), () -> "Fit-capable line start hidden for " + id);
                assertNull(result.observation().clippingReason(), () -> "Unexpected clipping for " + id);
            }
        }
    }

    @Test
    void wideViewportKeepsDocumentAndLineLeftVisibleWithPositiveInset() {
        var result = choose(rect(0, 0, 500, 400), rect(0, 120, 300, 130), rect(120, 120, 160, 130), 400, 220, 2);

        assertTrue(result.observation().documentLeftVisible());
        assertTrue(result.observation().lineLeftVisible());
        assertTrue(result.observation().landmarkVisible());
        assertEquals(-4.0D, result.observation().viewportBounds().left(), 0.0001D);
        assertNull(result.observation().clippingReason());
    }

    @Test
    void narrowViewportKeepsDestinationAndMaximizesLeadingContext() {
        var result = choose(rect(0, 0, 900, 400), rect(0, 120, 700, 130), rect(620, 120, 650, 130), 240, 180, 1);

        assertFalse(result.observation().lineLeftVisible());
        assertTrue(result.observation().landmarkVisible());
        assertEquals("line-left-does-not-fit", result.observation().clippingReason());
        assertEquals(418.0D, result.observation().viewportBounds().left(), 0.0001D);
    }

    @Test
    void firstLinePreservesDocumentTopButMidFileDoesNotPretendLineOneMustFit() {
        var first = choose(rect(0, 0, 400, 1000), rect(0, 0, 200, 10), rect(20, 0, 40, 10), 300, 160, 1);
        var middle = choose(rect(0, 0, 400, 1000), rect(0, 600, 200, 610), rect(20, 600, 40, 610), 300, 160, 1);

        assertTrue(first.observation().documentTopVisible());
        assertFalse(middle.observation().documentTopVisible());
        assertTrue(middle.observation().landmarkVisible());
    }

    @Test
    void oversizedDestinationReportsTypedClippingWhileKeepingStartLandmark() {
        var result = choose(rect(0, 0, 1000, 1000), rect(0, 300, 1000, 310), rect(100, 300, 700, 310), 200, 120, 1);

        assertEquals("destination-wider-than-viewport", result.observation().clippingReason());
        assertTrue(result.observation().landmarkVisible());
    }

    private static SFMNavigationFramingPolicy.Result choose(
            SFMSpatialSemanticContract.Rectangle document,
            SFMSpatialSemanticContract.Rectangle line,
            SFMSpatialSemanticContract.Rectangle destination,
            double viewportWidth,
            double viewportHeight,
            double zoom
    ) {
        return SFMNavigationFramingPolicy.choose(new SFMNavigationFramingPolicy.Request(
                "pane-1",
                "file:///workspace/A.java",
                "sha256:test",
                "region-1",
                "start",
                document,
                line,
                destination,
                viewportWidth,
                viewportHeight,
                8,
                new SFMSpatialSemanticContract.Camera(17, 23, zoom)
        ));
    }

    private static SFMSpatialSemanticContract.Rectangle rect(double left, double top, double right, double bottom) {
        return new SFMSpatialSemanticContract.Rectangle(left, top, right, bottom);
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
