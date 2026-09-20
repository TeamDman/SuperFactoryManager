package ca.teamdman.sfm.client.screen;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SFMDrawCanvasDocumentIndexTests {
    @Test
    void linearReplacementPreservesUnicodeCrLfBlankLinesAndTabs() {
        String source = "class Café {\r\n\tString emoji = \"😀\";\r\n\r\n}\n";
        SFMDrawCanvasModel model = new SFMDrawCanvasModel();

        model.replaceText(source, glyph -> glyph.equals(" ") ? 1 : 2, 9);

        assertEquals(source.replace("\r\n", "\n").stripTrailing(), model.projectedText(1, 9));
        assertTrue(model.glyphs().stream().anyMatch(glyph -> glyph.text().equals("😀")));
    }

    @Test
    void explicitSpacesRemainAddressableAtTheDocumentTail() {
        SFMDrawCanvasModel model = new SFMDrawCanvasModel();

        model.replaceText("hello ", ignored -> 1, 9);
        assertEquals("hello ", model.projectedText(1, 9));
        assertEquals(" ", model.glyphs().get(model.glyphs().size() - 1).text());

        model.typeGlyph("w", 1, 9);
        assertEquals("hello w", model.projectedText(1, 9));
    }

    @Test
    void visibleQueryIncludesBoundaryGlyphsAndExcludesOffscreenRows() {
        SFMDrawCanvasModel model = new SFMDrawCanvasModel();
        model.replaceText("abcdef\nsecond\nthird", ignored -> 2, 10);
        SFMDrawCanvasDocumentIndex index = model.documentIndex(1, 10);

        var first = index.visible(new SFMDrawCanvasDocumentIndex.Viewport(2, 0, 6, 9));
        var second = index.visible(new SFMDrawCanvasDocumentIndex.Viewport(0, 10, 100, 19));

        assertEquals(List.of("b", "c"), first.glyphs().stream().map(SFMDrawCanvasModel.CanvasGlyph::text).toList());
        assertEquals("second", second.glyphs().stream().map(SFMDrawCanvasModel.CanvasGlyph::text).reduce("", String::concat));
        assertEquals(1, first.rowsVisited());
        assertTrue(first.glyphsVisited() < index.orderedGlyphs().size());
    }

    @Test
    void pointLookupAndOffsetsRemainExactAtEdges() {
        SFMDrawCanvasModel model = new SFMDrawCanvasModel();
        model.replaceText("a 😀 z", glyph -> glyph.equals(" ") ? 1 : 3, 9);
        SFMDrawCanvasDocumentIndex index = model.documentIndex(1, 9);
        SFMDrawCanvasModel.CanvasGlyph emoji = model.glyphs().stream()
                .filter(glyph -> glyph.text().equals("😀"))
                .findFirst()
                .orElseThrow();

        assertSame(emoji, index.glyphAt(emoji.x(), emoji.y()).orElseThrow());
        assertSame(emoji, index.glyphAt(emoji.x() + emoji.width() - 0.01D, emoji.y() + 8.99D).orElseThrow());
        assertEquals(" ", index.glyphAt(emoji.x() + emoji.width(), emoji.y()).orElseThrow().text(),
                "the right-open emoji edge belongs to the explicit following space glyph");
        assertEquals("a ".length(), index.utf16OffsetOf(emoji).orElseThrow());
        assertEquals(index.glyphOrdinalOf(emoji).orElseThrow(), index.glyphOrdinalOrMinusOne(emoji));
        assertEquals(-1, index.glyphOrdinalOrMinusOne(
                new SFMDrawCanvasModel.CanvasGlyph("x", 0, 0, 1)
        ));
    }

    @Test
    void modelReusesProjectionUntilAnyGlyphMutation() {
        SFMDrawCanvasModel model = new SFMDrawCanvasModel();
        model.replaceText("abc", ignored -> 1, 9);

        SFMDrawCanvasDocumentIndex first = model.documentIndex(1, 9);
        SFMDrawCanvasDocumentIndex again = model.documentIndex(1, 9);
        assertSame(first, again);
        assertEquals(1, model.documentIndexBuildCount());

        model.typeGlyph("d", 1, 9);
        SFMDrawCanvasDocumentIndex changed = model.documentIndex(1, 9);
        assertFalse(first == changed);
        assertEquals(2, model.documentIndexBuildCount());

        model.glyphs().add(new SFMDrawCanvasModel.CanvasGlyph("x", 20, 0, 1));
        assertFalse(changed == model.documentIndex(1, 9));
        assertEquals(3, model.documentIndexBuildCount());
    }

    @Test
    void pannedAndZoomedCanvasBoundsUseTheSameSpatialQuery() {
        SFMDrawCanvasModel model = new SFMDrawCanvasModel();
        model.replaceText("left\ncenter\nright", ignored -> 4, 10);
        SFMDrawCanvasDocumentIndex index = model.documentIndex(1, 10);

        var zoomed = index.visible(new SFMDrawCanvasDocumentIndex.Viewport(8, 10, 16, 19));
        assertEquals(List.of("n", "t"), zoomed.glyphs().stream().map(SFMDrawCanvasModel.CanvasGlyph::text).toList());
        assertEquals(1, zoomed.rowsVisited());
    }

    @Test
    void performanceTrackerBoundsSamplesAndReportsInputToNextFrame() {
        SFMDrawCanvasPerformanceTracker tracker = new SFMDrawCanvasPerformanceTracker();
        var visible = new SFMDrawCanvasDocumentIndex.VisibleSlice(List.of(), 1, 3, 100);
        for (int i = 0; i < 500; i++) tracker.frame(i * 10L, i * 10L + i, visible, i % 2 == 0);
        tracker.inputReceived(10_000L);
        tracker.inputApplied(10_007L);
        tracker.frame(10_010L, 10_025L, visible, false, tracker.allocationCheckpoint());
        tracker.contextCapture(77L);
        tracker.syntaxWorkerSubmitted();
        tracker.viewportResolved(11L);
        tracker.styleProjection(13L);
        tracker.selectionGeometry(true, 17L);
        tracker.selectionGeometry(false, 3L);
        tracker.openTargetGeometry(19L);

        var snapshot = tracker.snapshot();
        assertEquals(501, snapshot.frames());
        assertEquals(25, snapshot.inputToFrameMedianNanos());
        assertEquals(25, snapshot.inputToFrameP95Nanos());
        assertEquals(1, snapshot.inputToFrameSamples());
        assertEquals(25, snapshot.latestInputToFrameNanos());
        assertEquals(1, snapshot.inputEvents());
        assertEquals(1, snapshot.inputApplications());
        assertEquals(7, snapshot.inputApplyP95Nanos());
        assertEquals(1, snapshot.contextCaptures());
        assertEquals(77, snapshot.contextCaptureNanos());
        assertEquals(1, snapshot.syntaxWorkerSubmissions());
        assertEquals(11, snapshot.viewportResolveNanos());
        assertEquals(1, snapshot.styleProjectionRebuilds());
        assertEquals(13, snapshot.styleProjectionNanos());
        assertEquals(1, snapshot.selectionGeometryRebuilds());
        assertEquals(1, snapshot.selectionGeometryCacheHits());
        assertEquals(20, snapshot.selectionGeometryNanos());
        assertEquals(1, snapshot.openTargetGeometryBuilds());
        assertEquals(19, snapshot.openTargetGeometryNanos());
        assertEquals(snapshot.frameAllocationMeasurementAvailable() ? 1 : 0,
                snapshot.frameAllocationSamples());
        assertTrue(snapshot.frameP95Nanos() >= snapshot.frameMedianNanos());
    }

    @Test
    void unavailableAllocationMeasurementIsExplicitRatherThanAFalseZeroByteSample() {
        SFMDrawCanvasPerformanceTracker tracker = new SFMDrawCanvasPerformanceTracker(null);
        var visible = new SFMDrawCanvasDocumentIndex.VisibleSlice(List.of(), 1, 0, 0);

        tracker.frame(1, 2, visible, false, tracker.allocationCheckpoint());

        var snapshot = tracker.snapshot();
        assertFalse(snapshot.frameAllocationMeasurementAvailable());
        assertEquals(0, snapshot.frameAllocationSamples());
        assertEquals(0, snapshot.frameAllocatedMedianBytes());
        assertEquals(0, snapshot.frameAllocatedP95Bytes());
        assertEquals(0, snapshot.frameAllocatedMaximumBytes());
    }

    @Test
    void warmMeasurementRetainsColdCostAndDropsHiddenSurfaceSamples() {
        SFMDrawCanvasPerformanceTracker tracker = new SFMDrawCanvasPerformanceTracker(null);
        var visible = new SFMDrawCanvasDocumentIndex.VisibleSlice(List.of(), 1, 0, 0);
        tracker.coldLoad(91L);
        tracker.inputReceived(1L);
        tracker.inputApplied(2L);
        tracker.frame(3L, 103L, visible, true);
        tracker.contextCapture(7L);

        tracker.beginWarmMeasurement();

        var reset = tracker.snapshot();
        assertEquals(91L, reset.coldLoadNanos());
        assertEquals(0L, reset.frames());
        assertEquals(0L, reset.inputEvents());
        assertEquals(0L, reset.inputToFrameSamples());
        assertEquals(0L, reset.latestInputToFrameNanos());
        assertEquals(0L, reset.inputToFrameMaximumNanos());
        assertEquals(0L, reset.contextCaptures());

        tracker.inputReceived(200L);
        tracker.inputApplied(205L);
        tracker.frame(210L, 220L, visible, false);
        var warm = tracker.snapshot();
        assertEquals(20L, warm.inputToFrameP95Nanos());
        assertEquals(1L, warm.inputToFrameSamples());
        assertEquals(20L, warm.latestInputToFrameNanos());
        assertEquals(5L, warm.inputApplyP95Nanos());
        assertEquals(1L, warm.frames());
    }
}
