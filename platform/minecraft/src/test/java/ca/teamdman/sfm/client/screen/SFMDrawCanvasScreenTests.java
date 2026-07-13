package ca.teamdman.sfm.client.screen;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class SFMDrawCanvasScreenTests {
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
