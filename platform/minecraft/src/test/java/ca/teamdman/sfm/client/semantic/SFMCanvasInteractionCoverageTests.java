package ca.teamdman.sfm.client.semantic;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SFMCanvasInteractionCoverageTests {
    @Test
    void exactSharedEdgeBelongsToOneRegionAcrossLayoutScaleAndPanTransforms() {
        var left = region("left", 0, 8);
        var right = region("right", 8, 16);
        for (double scale : List.of(0.5D, 1.0D, 2.0D, 8.0D)) {
            for (double pan : List.of(-24.0D, 0.0D, 91.0D)) {
                double physicalEdge = (8.0D - pan) * scale;
                double canvas = physicalEdge / scale + pan;
                assertFalse(left.contains(List.of(canvas, 2.0)), "scale=" + scale + " pan=" + pan);
                assertTrue(right.contains(List.of(canvas, 2.0)), "scale=" + scale + " pan=" + pan);
            }
        }
    }

    @Test
    void maximinSpreadsFiniteBudgetFartherThanSequentialPrefix() {
        List<SFMSpatialSamplingPolicies.Cell> exhaustivePrefix =
                SFMSpatialSamplingPolicies.exhaustive().select(100, 100, 0, 4);
        List<SFMSpatialSamplingPolicies.Cell> maximin =
                SFMSpatialSamplingPolicies.maximin().select(100, 100, 0, 4);
        assertTrue(minimumPairDistance(maximin) > minimumPairDistance(exhaustivePrefix));
        assertEquals(maximin, SFMSpatialSamplingPolicies.maximin().select(100, 100, 0, 4));
    }

    private static double minimumPairDistance(List<SFMSpatialSamplingPolicies.Cell> cells) {
        double minimum = Double.POSITIVE_INFINITY;
        for (int left = 0; left < cells.size(); left++) {
            for (int right = left + 1; right < cells.size(); right++) {
                minimum = Math.min(minimum, cells.get(left).distanceSquared(cells.get(right)));
            }
        }
        return minimum;
    }

    private static SFMSpatialSemanticContract.Region region(String id, double left, double right) {
        return new SFMSpatialSemanticContract.Region(
                SFMSpatialSemanticContract.REGION_SCHEMA, id, "canvas:test",
                SFMSpatialSemanticContract.Representation.RECTANGLE,
                List.of(new SFMSpatialSemanticContract.AxisBound(left, right),
                        new SFMSpatialSemanticContract.AxisBound(0, 5)),
                "half-open", "fixture", "sfm:test", List.of());
    }
}
