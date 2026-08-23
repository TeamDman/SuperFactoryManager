package ca.teamdman.sfm.client.screen.explorer;

import ca.teamdman.sfm.client.explorer.SFMPath;
import ca.teamdman.sfm.client.explorer.lazy.SFMExplorerEntry;
import ca.teamdman.sfm.client.explorer.lazy.SFMExplorerProjection;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenPanelBounds;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SFMExplorerPanelViewportTests {
    @Test
    public void listViewportSlicesWithoutMaterializingOffscreenRows() {
        List<SFMExplorerProjection.Row> rows = rows(100);
        SFMExplorerPanelViewport.Snapshot viewport = SFMExplorerPanelViewport.calculate(
                new SFMScreenPanelBounds(10, 20, 200, 100),
                SFMExplorerProjection.View.LIST,
                rows,
                17
        );

        assertEquals(1, viewport.columns());
        assertEquals(2, viewport.visibleGridRows());
        assertEquals(2, viewport.capacity());
        assertEquals(2, viewport.cells().size());
        assertEquals(17, viewport.firstVisibleIndex());
        assertEquals(List.of(17, 18), viewport.cells().stream()
                .map(SFMExplorerPanelViewport.Cell::absoluteIndex)
                .toList());
        assertTrue(viewport.cells().stream().allMatch(cell -> inside(
                cell.bounds(),
                viewport.layout().body()
        )));
    }

    @Test
    public void smallIconsBoundCapacityAndClampAnOversizedScrollRequest() {
        List<SFMExplorerProjection.Row> rows = rows(101);
        SFMExplorerPanelViewport.Snapshot viewport = SFMExplorerPanelViewport.calculate(
                new SFMScreenPanelBounds(0, 0, 300, 160),
                SFMExplorerProjection.View.SMALL_ICONS,
                rows,
                Integer.MAX_VALUE
        );

        assertEquals(3, viewport.columns());
        assertEquals(2, viewport.visibleGridRows());
        assertEquals(6, viewport.capacity());
        assertEquals(viewport.maximumScrollRow(), viewport.scrollRow());
        assertTrue(viewport.cells().size() <= 6);
        assertEquals(101, viewport.lastVisibleIndexExclusive());
        assertTrue(viewport.cells().stream().allMatch(cell -> inside(
                cell.bounds(),
                viewport.layout().body()
        )));
    }

    @Test
    public void oneViewportCanPresentFileAndItemRegistryRows() {
        SFMPath file = SFMPath.parse("file:///C:/project/A.java");
        SFMPath item = SFMPath.parse("registry://minecraft/item/minecraft/stone");
        List<SFMExplorerProjection.Row> rows = List.of(
                row(file, "A.java", false, Optional.of("minecraft:paper"), 0),
                row(item, "Stone", false, Optional.of("minecraft:stone"), 0)
        );

        SFMExplorerPanelViewport.Snapshot viewport = SFMExplorerPanelViewport.calculate(
                new SFMScreenPanelBounds(0, 0, 260, 160),
                SFMExplorerProjection.View.SMALL_ICONS,
                rows,
                0
        );

        assertEquals(List.of("file", "registry"), viewport.cells().stream()
                .map(cell -> cell.row().path().scheme())
                .toList());
        assertEquals(2, viewport.cells().size());
    }

    @Test
    public void locationControlConsumesTheWholeHeaderAtNarrowAndWideSizes() {
        for (SFMScreenPanelBounds bounds : List.of(
                new SFMScreenPanelBounds(0, 0, 90, 80),
                new SFMScreenPanelBounds(4, 7, 640, 360)
        )) {
            SFMExplorerPanelViewport.Snapshot viewport = SFMExplorerPanelViewport.calculate(
                    bounds,
                    SFMExplorerProjection.View.LIST,
                    rows(1),
                    0
            );
            assertTrue(inside(viewport.layout().locationControl(), viewport.layout().content()));
            assertTrue(inside(viewport.layout().revealControl(), viewport.layout().content()));
            assertEquals(viewport.layout().header().width(),
                    viewport.layout().locationControl().width() + viewport.layout().revealControl().width());
        }
    }

    @Test
    public void hiddenRevealControlReturnsItsSpaceAndHitRegionToTheLocationControl() {
        SFMScreenPanelBounds bounds = new SFMScreenPanelBounds(4, 7, 640, 360);
        SFMExplorerPanelViewport.Layout visible = SFMExplorerPanelViewport.layout(bounds, true);
        SFMExplorerPanelViewport.Layout hidden = SFMExplorerPanelViewport.layout(bounds, false);

        assertTrue(visible.revealControl().width() > 0);
        assertEquals(0, hidden.revealControl().width());
        assertEquals(hidden.header().width(), hidden.locationControl().width());
        assertEquals(visible.header(), hidden.header());
        assertEquals(visible.body(), hidden.body());
    }

    @Test
    public void reviewLensAndRevealControlsShareHeaderWithoutChangingTheBodyViewport() {
        SFMScreenPanelBounds bounds = new SFMScreenPanelBounds(10, 20, 360, 240);
        SFMExplorerPanelViewport.Layout ordinary = SFMExplorerPanelViewport.layout(bounds, false, true);
        SFMExplorerPanelViewport.Layout review = SFMExplorerPanelViewport.layout(bounds, true, true);

        assertTrue(review.lensControl().width() > 0);
        assertTrue(review.locationControl().width() < ordinary.locationControl().width());
        assertEquals(ordinary.revealControl(), review.revealControl());
        assertEquals(ordinary.bodyFrame(), review.bodyFrame());
        assertEquals(review.header().width(), review.locationControl().width()
                + review.lensControl().width() + review.revealControl().width());
    }

    @Test
    public void bodyViewportIsInsetOnAllFourEdgesSoRowsCannotPaintOverFocusChrome() {
        for (SFMScreenPanelBounds bounds : List.of(
                new SFMScreenPanelBounds(0, 0, 90, 80),
                new SFMScreenPanelBounds(0, 0, 160, 120),
                new SFMScreenPanelBounds(4, 7, 640, 360)
        )) {
            for (SFMExplorerProjection.View view : SFMExplorerProjection.View.values()) {
                for (int scroll : List.of(0, Integer.MAX_VALUE)) {
                    SFMExplorerPanelViewport.Snapshot viewport = SFMExplorerPanelViewport.calculate(
                            bounds,
                            view,
                            rows(100),
                            scroll
                    );
                    SFMExplorerPanelViewport.Rect frame = viewport.layout().bodyFrame();
                    SFMExplorerPanelViewport.Rect body = viewport.layout().body();
                    assertEquals(frame.x() + Math.min(1, frame.width()), body.x());
                    assertEquals(frame.y() + Math.min(1, frame.height()), body.y());
                    assertEquals(Math.max(0, frame.width() - 2), body.width());
                    assertEquals(Math.max(0, frame.height() - 2), body.height());
                    assertTrue(viewport.cells().stream().allMatch(cell -> inside(cell.bounds(), body)));
                    assertTrue(viewport.cells().stream().noneMatch(cell -> touchesOuterEdge(cell.bounds(), frame)));
                }
            }
        }
    }

    private static List<SFMExplorerProjection.Row> rows(int count) {
        ArrayList<SFMExplorerProjection.Row> answer = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            SFMPath path = SFMPath.parse("file:///C:/project/file-" + index + ".txt");
            answer.add(row(path, "file-" + index + ".txt", index % 10 == 0, Optional.empty(), index % 3));
        }
        return List.copyOf(answer);
    }

    private static SFMExplorerProjection.Row row(
            SFMPath path,
            String label,
            boolean expandable,
            Optional<String> icon,
            int depth
    ) {
        SFMExplorerEntry entry = SFMExplorerEntry.simple(path, label, expandable, icon);
        return new SFMExplorerProjection.Row(
                path,
                entry,
                depth,
                false,
                false,
                entry.sortKey(SFMExplorerEntry.SORT_NAME)
        );
    }

    private static boolean inside(
            SFMExplorerPanelViewport.Rect inner,
            SFMExplorerPanelViewport.Rect outer
    ) {
        return inner.x() >= outer.x()
                && inner.y() >= outer.y()
                && inner.x() + inner.width() <= outer.x() + outer.width()
                && inner.y() + inner.height() <= outer.y() + outer.height();
    }

    private static boolean touchesOuterEdge(
            SFMExplorerPanelViewport.Rect inner,
            SFMExplorerPanelViewport.Rect outer
    ) {
        return inner.x() <= outer.x()
                || inner.y() <= outer.y()
                || inner.x() + inner.width() >= outer.x() + outer.width()
                || inner.y() + inner.height() >= outer.y() + outer.height();
    }
}
