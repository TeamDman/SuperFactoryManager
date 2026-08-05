package ca.teamdman.sfm.client.terminal;

import ca.teamdman.sfm.client.screen.workspace.SFMScreenPanelBounds;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspacePanelMetrics;
import org.junit.jupiter.api.Test;

import java.util.List;

import static ca.teamdman.sfm.client.terminal.SFMTerminalSelectionLayout.Cell;
import static ca.teamdman.sfm.client.terminal.SFMTerminalSelectionLayout.NativeGrid;
import static ca.teamdman.sfm.client.terminal.SFMTerminalSelectionLayout.PhysicalHighlight;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SFMTerminalSelectionLayoutTests {
    private static final NativeGrid GRID = new NativeGrid(5, 3, 10, 20, 50, 60);
    private static final SFMTerminalImageLayout IMAGE = new SFMTerminalImageLayout(4, 7, 50, 60);
    private static final SFMScreenPanelBounds UNBOUNDED_CLIP =
            new SFMScreenPanelBounds(Integer.MIN_VALUE / 2, Integer.MIN_VALUE / 2,
                    Integer.MAX_VALUE, Integer.MAX_VALUE);

    @Test
    void collapsedSelectionProducesNoHighlights() {
        assertEquals(List.of(), highlights(new Cell(2, 1), new Cell(2, 1)));
    }

    @Test
    void oneRowSelectionIncludesBothEndpoints() {
        assertEquals(
                List.of(new PhysicalHighlight(114, 227, 144, 247)),
                highlights(new Cell(1, 1), new Cell(3, 1))
        );
    }

    @Test
    void reverseOneRowSelectionMatchesForwardSelection() {
        assertEquals(
                highlights(new Cell(1, 1), new Cell(3, 1)),
                highlights(new Cell(3, 1), new Cell(1, 1))
        );
    }

    @Test
    void multiRowSelectionProducesFirstMiddleAndLastRowRuns() {
        assertEquals(
                List.of(
                        new PhysicalHighlight(134, 207, 154, 227),
                        new PhysicalHighlight(104, 227, 154, 247),
                        new PhysicalHighlight(104, 247, 124, 267)
                ),
                highlights(new Cell(3, 0), new Cell(1, 2))
        );
    }

    @Test
    void reverseMultiRowSelectionMatchesForwardSelection() {
        assertEquals(
                highlights(new Cell(3, 0), new Cell(1, 2)),
                highlights(new Cell(1, 2), new Cell(3, 0))
        );
    }

    @Test
    void endpointsAreClampedBeforeOrdering() {
        assertEquals(
                List.of(
                        new PhysicalHighlight(104, 207, 154, 227),
                        new PhysicalHighlight(104, 227, 154, 247),
                        new PhysicalHighlight(104, 247, 154, 267)
                ),
                highlights(new Cell(-20, -10), new Cell(80, 40))
        );
        assertEquals(
                List.of(),
                highlights(new Cell(-2, -4), new Cell(-1, -1))
        );
    }

    @Test
    void rectanglesAreClippedPerRowAndEmptyRowsAreOmitted() {
        SFMScreenPanelBounds clip = new SFMScreenPanelBounds(115, 235, 20, 20);
        assertEquals(
                List.of(
                        new PhysicalHighlight(115, 235, 135, 247),
                        new PhysicalHighlight(115, 247, 124, 255)
                ),
                SFMTerminalSelectionLayout.physicalHighlights(
                        new Cell(3, 0),
                        new Cell(1, 2),
                        GRID,
                        IMAGE,
                        metrics(IMAGE, 100, 200, 1.0D, 400, 300, 400, 300),
                        clip
                )
        );
    }

    @Test
    void guiAndPanelScaleAreRetainedByMeasuredImageBounds() {
        SFMTerminalImageLayout image = new SFMTerminalImageLayout(3, 5, 30, 20);
        NativeGrid grid = new NativeGrid(3, 2, 10, 10, 30, 20);
        SFMWorkspacePanelMetrics metrics = metrics(
                image,
                11,
                13,
                0.5D,
                3840,
                2160,
                960,
                540
        );

        assertEquals(new SFMScreenPanelBounds(50, 62, 60, 40), metrics.physicalPixelBounds());
        assertEquals(
                List.of(
                        new PhysicalHighlight(70, 62, 110, 82),
                        new PhysicalHighlight(50, 82, 110, 102)
                ),
                SFMTerminalSelectionLayout.physicalHighlights(
                        new Cell(1, 0),
                        new Cell(2, 1),
                        grid,
                        image,
                        metrics,
                        UNBOUNDED_CLIP
                )
        );
    }

    @Test
    void fractionalCellEdgesRoundOutward() {
        SFMTerminalImageLayout image = new SFMTerminalImageLayout(0, 0, 3, 3);
        NativeGrid grid = new NativeGrid(3, 3, 1, 1, 3, 3);
        SFMWorkspacePanelMetrics metrics = metrics(
                image,
                0,
                0,
                1.0D,
                10,
                10,
                3,
                3
        );

        assertEquals(new SFMScreenPanelBounds(0, 0, 10, 10), metrics.physicalPixelBounds());
        assertEquals(
                List.of(
                        new PhysicalHighlight(6, 0, 10, 4),
                        new PhysicalHighlight(0, 3, 10, 7),
                        new PhysicalHighlight(0, 6, 4, 10)
                ),
                SFMTerminalSelectionLayout.physicalHighlights(
                        new Cell(2, 0),
                        new Cell(0, 2),
                        grid,
                        image,
                        metrics,
                        UNBOUNDED_CLIP
                )
        );
    }

    @Test
    void endpointRoundingUsesTheExactTransformRatherThanRoundedOuterBounds() {
        SFMTerminalImageLayout image = new SFMTerminalImageLayout(1, 0, 10, 2);
        NativeGrid grid = new NativeGrid(10, 2, 1, 1, 10, 2);
        SFMWorkspacePanelMetrics metrics = metrics(
                image,
                0,
                0,
                0.7D,
                23,
                23,
                10,
                10
        );

        assertEquals(new SFMScreenPanelBounds(1, 0, 17, 4), metrics.physicalPixelBounds());
        assertEquals(
                List.of(new PhysicalHighlight(8, 0, 12, 2)),
                SFMTerminalSelectionLayout.physicalHighlights(
                        new Cell(4, 0),
                        new Cell(5, 0),
                        grid,
                        image,
                        metrics,
                        UNBOUNDED_CLIP
                )
        );
    }

    @Test
    void nativeImagePaddingIsNotHighlightedAsCellContent() {
        SFMTerminalImageLayout image = new SFMTerminalImageLayout(0, 0, 24, 12);
        NativeGrid grid = new NativeGrid(2, 1, 10, 10, 24, 12);
        SFMWorkspacePanelMetrics metrics = metrics(
                image,
                0,
                0,
                1.0D,
                48,
                24,
                24,
                12
        );

        assertEquals(
                List.of(new PhysicalHighlight(0, 0, 40, 20)),
                SFMTerminalSelectionLayout.physicalHighlights(
                        new Cell(0, 0),
                        new Cell(1, 0),
                        grid,
                        image,
                        metrics,
                        UNBOUNDED_CLIP
                )
        );
    }

    @Test
    void mismatchedMeasurementIsRejected() {
        SFMWorkspacePanelMetrics wrong = metrics(
                new SFMTerminalImageLayout(5, 7, 50, 60),
                100,
                200,
                1.0D,
                400,
                300,
                400,
                300
        );
        assertThrows(IllegalArgumentException.class, () ->
                SFMTerminalSelectionLayout.physicalHighlights(
                        new Cell(0, 0),
                        new Cell(1, 0),
                        GRID,
                        IMAGE,
                        wrong,
                        UNBOUNDED_CLIP
                ));
    }

    @Test
    void invalidNativeGridIsRejected() {
        assertThrows(IllegalArgumentException.class, () ->
                new NativeGrid(5, 3, 10, 20, 49, 60));
        assertThrows(IllegalArgumentException.class, () ->
                new NativeGrid(5, 3, 10, 20, 50, 59));
    }

    private static List<PhysicalHighlight> highlights(Cell anchor, Cell active) {
        return SFMTerminalSelectionLayout.physicalHighlights(
                anchor,
                active,
                GRID,
                IMAGE,
                metrics(IMAGE, 100, 200, 1.0D, 400, 300, 400, 300),
                UNBOUNDED_CLIP
        );
    }

    private static SFMWorkspacePanelMetrics metrics(
            SFMTerminalImageLayout image,
            int guiContentX,
            int guiContentY,
            double panelRenderScale,
            int framebufferWidth,
            int framebufferHeight,
            int guiWidth,
            int guiHeight
    ) {
        return SFMWorkspacePanelMetrics.map(
                image.bounds(),
                guiContentX,
                guiContentY,
                panelRenderScale,
                framebufferWidth,
                framebufferHeight,
                guiWidth,
                guiHeight
        );
    }
}
