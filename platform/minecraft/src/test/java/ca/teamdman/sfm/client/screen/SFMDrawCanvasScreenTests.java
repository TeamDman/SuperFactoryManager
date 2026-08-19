package ca.teamdman.sfm.client.screen;

import ca.teamdman.sfm.client.context.SFMContextTextCoordinates;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SFMDrawCanvasScreenTests {
    @Test
    public void projectedLfHoverCoordinatesMapToExactCrlfBaselineWithoutAddressingItsInterior() {
        String projected = "first\nsecond\nemoji \uD83D\uDE80 target";
        String baseline = "first\r\nsecond\r\nemoji \uD83D\uDE80 target";

        assertEquals(
                SFMContextTextCoordinates.atLineColumn(baseline, 1, 0),
                SFMDrawCanvasScreen.contextPositionAtProjectedUtf16Offset(
                        projected,
                        baseline,
                        projected.indexOf("second")
                ).orElseThrow()
        );
        assertEquals(
                SFMContextTextCoordinates.atLineColumn(baseline, 2, 8),
                SFMDrawCanvasScreen.contextPositionAtProjectedUtf16Offset(
                        projected,
                        baseline,
                        projected.indexOf("target")
                ).orElseThrow()
        );
    }

    @Test
    public void incompatibleProjectedCoordinateIsReportedAsAbsentInsteadOfCrashingInputHandling() {
        assertTrue(SFMDrawCanvasScreen.contextPositionAtProjectedUtf16Offset(
                "first\nsecond",
                "first",
                "first\nsecond".indexOf("second")
        ).isEmpty());
    }

    @Test
    public void resizingViewportKeepsCanvasOriginAtSameScreenOffset() {
        double initialCamera = 427.0D / 2.0D - 32.0D;
        double resizedCamera = SFMDrawCanvasScreen.resizeCameraAxis(
                initialCamera,
                1.0D,
                427,
                103
        );

        assertEquals(32.0D, -initialCamera + 427.0D / 2.0D);
        assertEquals(32.0D, -resizedCamera + 103.0D / 2.0D);
    }

    @Test
    public void unionRectsMergesOverlappingRectangles() {
        assertEquals(
                Set.of(new SFMDrawCanvasScreen.CanvasRect(0, 0, 15, 10)),
                new HashSet<>(SFMDrawCanvasScreen.unionRects(List.of(
                        new SFMDrawCanvasScreen.CanvasRect(0, 0, 10, 10),
                        new SFMDrawCanvasScreen.CanvasRect(5, 0, 15, 10)
                )))
        );
    }

    @Test
    public void unionRectsKeepsSeparateRectanglesSeparate() {
        assertEquals(
                Set.of(
                        new SFMDrawCanvasScreen.CanvasRect(0, 0, 10, 10),
                        new SFMDrawCanvasScreen.CanvasRect(20, 0, 30, 10)
                ),
                new HashSet<>(SFMDrawCanvasScreen.unionRects(List.of(
                        new SFMDrawCanvasScreen.CanvasRect(0, 0, 10, 10),
                        new SFMDrawCanvasScreen.CanvasRect(20, 0, 30, 10)
                )))
        );
    }

    @Test
    public void unionRectsPreservesLShapedSelectionMask() {
        assertEquals(
                Set.of(
                        new SFMDrawCanvasScreen.CanvasRect(0, 0, 10, 10),
                        new SFMDrawCanvasScreen.CanvasRect(0, 10, 20, 20)
                ),
                new HashSet<>(SFMDrawCanvasScreen.unionRects(List.of(
                        new SFMDrawCanvasScreen.CanvasRect(0, 0, 10, 10),
                        new SFMDrawCanvasScreen.CanvasRect(0, 10, 10, 20),
                        new SFMDrawCanvasScreen.CanvasRect(10, 10, 20, 20)
                )))
        );
    }

    @Test
    public void unionRectsDoesNotOverlapAroundPartialIntersection() {
        assertEquals(
                Set.of(
                        new SFMDrawCanvasScreen.CanvasRect(0, 0, 10, 5),
                        new SFMDrawCanvasScreen.CanvasRect(0, 5, 15, 10),
                        new SFMDrawCanvasScreen.CanvasRect(5, 10, 15, 15)
                ),
                new HashSet<>(SFMDrawCanvasScreen.unionRects(List.of(
                        new SFMDrawCanvasScreen.CanvasRect(0, 0, 10, 10),
                        new SFMDrawCanvasScreen.CanvasRect(5, 5, 15, 15)
                )))
        );
    }
}
