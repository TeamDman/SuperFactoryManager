package ca.teamdman.sfm.common.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The same top-left UV contract must hold for every world-facing display. */
class TouchDisplaySurfaceTests {
    private static final BlockPos POSITION = new BlockPos(12, 64, -7);
{% if features.touch_display_seamless_surface %}
    private static final double INSET = 8.0 / 16.0;
{% else %}
    private static final double INSET = 7.0 / 16.0;
{% endif %}

    @Test
    void eachFaceHasAnExplicitRightAndUpBasis() {
        Map<Direction, TouchDisplaySurface.Basis> expected = Map.of(
                Direction.NORTH, new TouchDisplaySurface.Basis(-1, 0, 0, 0, 1, 0),
                Direction.SOUTH, new TouchDisplaySurface.Basis(1, 0, 0, 0, 1, 0),
                Direction.WEST, new TouchDisplaySurface.Basis(0, 0, 1, 0, 1, 0),
                Direction.EAST, new TouchDisplaySurface.Basis(0, 0, -1, 0, 1, 0),
                Direction.UP, new TouchDisplaySurface.Basis(1, 0, 0, 0, 0, -1),
                Direction.DOWN, new TouchDisplaySurface.Basis(1, 0, 0, 0, 0, 1)
        );
        for (Direction face : Direction.values()) {
            assertEquals(expected.get(face), TouchDisplaySurface.basis(face), face.toString());
        }
    }

    @Test
    void allRotationsUseTopLeftOriginAndAcceptExactImageEdges() {
        for (Direction face : Direction.values()) {
            assertUv(face, 0.25, 0.75);
            assertUv(face, 0.0, 0.0);
            assertUv(face, 1.0, 0.0);
            assertUv(face, 0.0, 1.0);
            assertUv(face, 1.0, 1.0);
        }
    }

    @Test
    void rejectsWrongFacePositionInsideHitPlaneAndOutsideImage() {
        for (Direction face : Direction.values()) {
            BlockHitResult valid = hit(face, 0.5, 0.5);
            assertTrue(TouchDisplaySurface.uvForHit(POSITION, face,
                    new BlockHitResult(valid.getLocation(), face.getOpposite(), POSITION, false)).isEmpty(),
                    face + " wrong face");
            assertTrue(TouchDisplaySurface.uvForHit(POSITION, face,
                    new BlockHitResult(valid.getLocation(), face, POSITION.offset(1, 0, 0), false)).isEmpty(),
                    face + " wrong position");
            assertTrue(TouchDisplaySurface.uvForHit(POSITION, face,
                    new BlockHitResult(valid.getLocation(), face, POSITION, true)).isEmpty(),
                    face + " inside block");

            Vec3 offPlane = valid.getLocation().add(
                    face.getStepX() * 0.001,
                    face.getStepY() * 0.001,
                    face.getStepZ() * 0.001
            );
            assertTrue(TouchDisplaySurface.uvForHit(POSITION, face,
                    new BlockHitResult(offPlane, face, POSITION, false)).isEmpty(),
                    face + " off face plane");

            double outside = 0.5 + (INSET + 0.01) / (2 * INSET);
            assertTrue(TouchDisplaySurface.uvForHit(POSITION, face,
                    hit(face, outside, 0.5)).isEmpty(), face + " outside horizontal image edge");
            assertTrue(TouchDisplaySurface.uvForHit(POSITION, face,
                    hit(face, 0.5, outside)).isEmpty(), face + " outside vertical image edge");
        }
    }

    @Test
    void floatHitToleranceClampsOnlyNearThePlaneAndImageEdges() {
        for (Direction face : Direction.values()) {
            BlockHitResult center = hit(face, 0.5, 0.5);
            Vec3 nearPlane = center.getLocation().add(
                    face.getStepX() * 0.000_005,
                    face.getStepY() * 0.000_005,
                    face.getStepZ() * 0.000_005
            );
            assertTrue(TouchDisplaySurface.uvForHit(POSITION, face,
                    new BlockHitResult(nearPlane, face, POSITION, false)).isPresent(),
                    face + " near-plane float rounding");
            double nearEdge = 1.0 + 0.000_005 / (2 * INSET);
            TouchDisplaySurface.UV clamped = TouchDisplaySurface.uvForHit(
                    POSITION, face, hit(face, nearEdge, 0.5)).orElseThrow();
            assertEquals(1.0, clamped.u(), face + " near-edge clamp");
            double beyondEdge = 1.0 + 0.000_020 / (2 * INSET);
            assertTrue(TouchDisplaySurface.uvForHit(POSITION, face,
                    hit(face, beyondEdge, 0.5)).isEmpty(), face + " outside tolerance");
        }
    }

    @Test
    void rejectsNonFiniteHitCoordinates() {
        BlockHitResult center = hit(Direction.NORTH, 0.5, 0.5);
        for (double invalid : new double[] {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            for (int coordinate = 0; coordinate < 3; coordinate++) {
                Vec3 valid = center.getLocation();
                Vec3 point = new Vec3(
                        coordinate == 0 ? invalid : valid.x,
                        coordinate == 1 ? invalid : valid.y,
                        coordinate == 2 ? invalid : valid.z
                );
                assertTrue(TouchDisplaySurface.uvForHit(POSITION, Direction.NORTH,
                        new BlockHitResult(point, Direction.NORTH, POSITION, false)).isEmpty(),
                        "non-finite coordinate " + coordinate);
            }
        }
    }

    private static void assertUv(Direction face, double u, double v) {
        TouchDisplaySurface.UV result = TouchDisplaySurface.uvForHit(POSITION, face, hit(face, u, v))
                .orElseThrow(() -> new AssertionError(face + " rejected " + u + ", " + v));
        assertEquals(u, result.u(), 1e-9, face + " u");
        assertEquals(v, result.v(), 1e-9, face + " v");
    }

    private static BlockHitResult hit(Direction face, double u, double v) {
        TouchDisplaySurface.Basis basis = TouchDisplaySurface.basis(face);
        double right = (u - 0.5) * 2 * INSET;
        double up = (0.5 - v) * 2 * INSET;
        Vec3 location = new Vec3(
                POSITION.getX() + 0.5 + face.getStepX() * 0.5 + basis.rightX() * right + basis.upX() * up,
                POSITION.getY() + 0.5 + face.getStepY() * 0.5 + basis.rightY() * right + basis.upY() * up,
                POSITION.getZ() + 0.5 + face.getStepZ() * 0.5 + basis.rightZ() * right + basis.upZ() * up
        );
        return new BlockHitResult(location, face, POSITION, false);
    }
}
