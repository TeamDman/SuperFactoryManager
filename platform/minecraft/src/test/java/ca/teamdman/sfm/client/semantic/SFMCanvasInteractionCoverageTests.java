package ca.teamdman.sfm.client.semantic;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTimeout;
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

    @Test
    void incrementalMaximinPreservesTheFrozenReferenceOrder() {
        for (long seed : List.of(0L, 1L, 17L, 53L)) {
            assertEquals(
                    referenceMaximin(13, 7, seed, 41),
                    SFMSpatialSamplingPolicies.maximin().select(13, 7, seed, 41),
                    "seed=" + seed
            );
        }
    }

    @Test
    void declaredAutoScaleCoverageBudgetIsComputationallyBounded() {
        assertTimeout(Duration.ofSeconds(15), () ->
                assertEquals(4_096, SFMSpatialSamplingPolicies.adaptiveFailureSeeking()
                        .select(480, 267, 0, 4_096)
                        .size()));
    }

    private static List<SFMSpatialSamplingPolicies.Cell> referenceMaximin(
            int width,
            int height,
            long seed,
            int budget
    ) {
        ArrayList<SFMSpatialSamplingPolicies.Cell> remaining = new ArrayList<>();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) remaining.add(new SFMSpatialSamplingPolicies.Cell(x, y));
        }
        ArrayList<SFMSpatialSamplingPolicies.Cell> selected = new ArrayList<>();
        selected.add(remaining.remove((int) Math.floorMod(seed, remaining.size())));
        while (selected.size() < budget) {
            SFMSpatialSamplingPolicies.Cell next = remaining.stream().max(Comparator
                    .comparingDouble((SFMSpatialSamplingPolicies.Cell cell) -> selected.stream()
                            .mapToDouble(cell::distanceSquared).min().orElse(0))
                    .thenComparingLong(cell -> -cell.ordinal(width))).orElseThrow();
            remaining.remove(next);
            selected.add(next);
        }
        return List.copyOf(selected);
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
