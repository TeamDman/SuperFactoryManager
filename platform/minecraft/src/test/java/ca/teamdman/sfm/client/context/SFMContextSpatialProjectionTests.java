package ca.teamdman.sfm.client.context;

import ca.teamdman.sfm.client.explorer.SFMPath;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SFMContextSpatialProjectionTests {
    @Test
    void nonCentreScreenPointAndArbitraryRayRemainProviderNeutralData() {
        SFMContextSpatialProjection projection = new SFMContextSpatialProjection(
                "minecraft:physical-screen-pixels",
                Optional.of(new SFMContextSpatialProjection.ScreenPoint(17.5, 811.25)),
                Optional.of(new SFMContextSpatialProjection.Ray(
                        new SFMContextSpatialProjection.Vector3(10, 64, -3),
                        new SFMContextSpatialProjection.Vector3(0.25, -0.5, 1)
                )),
                Optional.of(new SFMContextSpatialProjection.Hit(
                        "sfm:test-hit-provider",
                        "block",
                        Optional.of(SFMPath.parse("test-world://overworld/10/63/-2"))
                ))
        );

        assertEquals(17.5, projection.screenPoint().orElseThrow().x());
        assertEquals(-0.5, projection.ray().orElseThrow().direction().y());
        assertEquals("sfm:test-hit-provider", projection.hit().orElseThrow().providerId());
        assertTrue(projection.hit().orElseThrow().address().isPresent());
    }

    @Test
    void spatialShapeRequiresARealCoordinateAndNonZeroFiniteRay() {
        assertThrows(IllegalArgumentException.class, () -> new SFMContextSpatialProjection(
                "screen", Optional.empty(), Optional.empty(), Optional.empty()
        ));
        assertThrows(IllegalArgumentException.class, () -> new SFMContextSpatialProjection.Ray(
                new SFMContextSpatialProjection.Vector3(0, 0, 0),
                new SFMContextSpatialProjection.Vector3(0, 0, 0)
        ));
        assertThrows(IllegalArgumentException.class, () ->
                new SFMContextSpatialProjection.ScreenPoint(Double.NaN, 0));
    }
}
