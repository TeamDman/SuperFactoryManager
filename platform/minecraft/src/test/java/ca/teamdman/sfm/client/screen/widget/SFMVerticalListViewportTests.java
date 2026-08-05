package ca.teamdman.sfm.client.screen.widget;

import org.junit.jupiter.api.Test;

import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SFMVerticalListViewportTests {
    @Test
    void selectionAlwaysBecomesVisible() {
        SFMVerticalListViewport viewport = viewport(20, 5);

        viewport.select(12);
        assertEquals(8, viewport.firstVisibleRow());
        assertEquals(13, viewport.lastVisibleRowExclusive());

        viewport.select(2);
        assertEquals(2, viewport.firstVisibleRow());
        assertEquals(2, viewport.selectedRow());
    }

    @Test
    void wheelAndManualViewportMovementClampSelectionIntoView() {
        SFMVerticalListViewport viewport = viewport(30, 5);
        viewport.selectFirst();

        assertTrue(viewport.scrollWheel(-1.0d));
        assertEquals(3, viewport.firstVisibleRow());
        assertEquals(3, viewport.selectedRow());

        assertTrue(viewport.scrollWheel(100.0d));
        assertEquals(0, viewport.firstVisibleRow());
        assertEquals(3, viewport.selectedRow());
        assertFalse(viewport(4, 5).scrollWheel(-1.0d));
    }

    @Test
    void pageHomeAndEndMoveSelectionWithClampedViewport() {
        SFMVerticalListViewport viewport = viewport(23, 5);
        viewport.selectFirst();

        assertTrue(viewport.pageSelection(1));
        assertEquals(5, viewport.selectedRow());
        assertEquals(1, viewport.firstVisibleRow());
        assertTrue(viewport.pageSelection(10));
        assertEquals(22, viewport.selectedRow());
        assertEquals(18, viewport.firstVisibleRow());
        assertTrue(viewport.selectFirst());
        assertEquals(0, viewport.firstVisibleRow());
        assertTrue(viewport.selectLast());
        assertEquals(18, viewport.firstVisibleRow());
    }

    @Test
    void rowHitTestingUsesHalfOpenBoundsAndRejectsGaps() {
        SFMVerticalListViewport viewport = viewport(10, 3);
        SFMVerticalListViewport.Bounds rows = new SFMVerticalListViewport.Bounds(20, 64, 100, 72);

        assertEquals(OptionalInt.of(0), viewport.rowAt(20, 64, rows, 24, 22));
        assertEquals(OptionalInt.of(0), viewport.rowAt(119.99, 85.99, rows, 24, 22));
        assertEquals(OptionalInt.empty(), viewport.rowAt(20, 86, rows, 24, 22));
        assertEquals(OptionalInt.empty(), viewport.rowAt(20, 87.99, rows, 24, 22));
        assertEquals(OptionalInt.of(1), viewport.rowAt(20, 88, rows, 24, 22));
        assertEquals(OptionalInt.empty(), viewport.rowAt(19.99, 64, rows, 24, 22));
        assertEquals(OptionalInt.empty(), viewport.rowAt(120, 64, rows, 24, 22));
        assertEquals(OptionalInt.empty(), viewport.rowAt(20, 63.99, rows, 24, 22));
        assertEquals(OptionalInt.empty(), viewport.rowAt(20, 136, rows, 24, 22));
    }

    @Test
    void scrollbarGeometryIsProportionalAndClamped() {
        SFMVerticalListViewport viewport = viewport(100, 10);
        SFMVerticalListViewport.Bounds track = new SFMVerticalListViewport.Bounds(90, 20, 6, 100);

        SFMVerticalListViewport.ScrollbarGeometry top = viewport.scrollbarGeometry(track, 12);
        assertTrue(top.visible());
        assertEquals(new SFMVerticalListViewport.Bounds(90, 20, 6, 12), top.thumb());

        viewport.scrollRows(45);
        assertEquals(new SFMVerticalListViewport.Bounds(90, 64, 6, 12), viewport.scrollbarGeometry(track, 12).thumb());

        SFMVerticalListViewport shortList = viewport(4, 5);
        assertFalse(shortList.scrollbarGeometry(track, 12).visible());
    }

    @Test
    void trackClicksAndThumbDraggingReachTheWholeList() {
        SFMVerticalListViewport viewport = viewport(100, 10);
        SFMVerticalListViewport.Bounds track = new SFMVerticalListViewport.Bounds(90, 20, 6, 100);
        SFMVerticalListViewport.ScrollbarGeometry geometry = viewport.scrollbarGeometry(track, 12);

        assertTrue(viewport.mouseClickedScrollbar(92, 70, 0, geometry));
        assertEquals(45, viewport.firstVisibleRow());

        geometry = viewport.scrollbarGeometry(track, 12);
        assertTrue(viewport.mouseClickedScrollbar(92, geometry.thumb().y() + 3, 0, geometry));
        assertTrue(viewport.isScrollbarDragActive());
        assertTrue(viewport.mouseDraggedScrollbar(200, 0, geometry));
        assertEquals(90, viewport.firstVisibleRow());
        assertTrue(viewport.mouseReleasedScrollbar(0));
        assertFalse(viewport.isScrollbarDragActive());
    }

    @Test
    void resizeAndFilterChangesClampAllState() {
        SFMVerticalListViewport viewport = viewport(100, 10);
        viewport.selectLast();
        assertEquals(90, viewport.firstVisibleRow());

        viewport.configure(12, 5);
        assertEquals(11, viewport.selectedRow());
        assertEquals(7, viewport.firstVisibleRow());

        viewport.configure(12, 20);
        assertEquals(0, viewport.firstVisibleRow());
        assertEquals(11, viewport.selectedRow());

        viewport.configure(0, 0);
        assertEquals(SFMVerticalListViewport.NO_SELECTION, viewport.selectedRow());
        assertEquals(0, viewport.firstVisibleRow());
        assertEquals(0, viewport.lastVisibleRowExclusive());
    }

    @Test
    void rejectsInvalidCountsRowsAndBounds() {
        SFMVerticalListViewport viewport = new SFMVerticalListViewport();
        assertThrows(IllegalArgumentException.class, () -> viewport.configure(-1, 1));
        assertThrows(IllegalArgumentException.class, () -> viewport.configure(1, -1));
        assertThrows(IllegalArgumentException.class,
                () -> new SFMVerticalListViewport.Bounds(0, 0, -1, 1));
        viewport.configure(1, 1);
        assertThrows(IllegalArgumentException.class,
                () -> viewport.rowAt(0, 0, new SFMVerticalListViewport.Bounds(0, 0, 1, 1), 0, 1));
        assertThrows(IllegalArgumentException.class,
                () -> viewport.rowAt(0, 0, new SFMVerticalListViewport.Bounds(0, 0, 1, 1), 2, 3));
    }

    private static SFMVerticalListViewport viewport(int itemCount, int visibleRows) {
        SFMVerticalListViewport viewport = new SFMVerticalListViewport();
        viewport.configure(itemCount, visibleRows);
        return viewport;
    }
}
