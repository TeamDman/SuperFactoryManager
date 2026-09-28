package ca.teamdman.sfm.common.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Objects;
import java.util.Optional;

/** Geometry shared by the Touch Display renderer and its server-side hit mapping. */
public final class TouchDisplaySurface {
    /** The image covers the complete outward block face for seamless panels. */
    public static final float HALF_IMAGE_SIZE = 8F / 16F;
    private static final double IMAGE_SIZE = 2.0 * HALF_IMAGE_SIZE;
    /** The vanilla use packet transmits block-relative hit coordinates as floats. */
    private static final double HIT_TOLERANCE = 1.0e-5;

    private TouchDisplaySurface() {
    }

    /** U runs left to right and V top to bottom for a viewer facing this side. */
    public record UV(double u, double v) {
    }

    /** Right cross up is the outward face normal for each orientation. */
    public record Basis(int rightX, int rightY, int rightZ, int upX, int upY, int upZ) {
    }

    public static Basis basis(Direction face) {
        return switch (Objects.requireNonNull(face, "face")) {
            case NORTH -> new Basis(-1, 0, 0, 0, 1, 0);
            case SOUTH -> new Basis(1, 0, 0, 0, 1, 0);
            case WEST -> new Basis(0, 0, 1, 0, 1, 0);
            case EAST -> new Basis(0, 0, -1, 0, 1, 0);
            case UP -> new Basis(1, 0, 0, 0, 0, -1);
            case DOWN -> new Basis(1, 0, 0, 0, 0, 1);
        };
    }

    /**
     * Maps a server-received vanilla block hit to the visible full-face image.
     * Image edges are inclusive. Float-rounding within {@link #HIT_TOLERANCE}
     * of an edge is clamped; farther hits, other faces, and inside hits fail.
     * The caller must still rely on Minecraft's normal reach/world checks.
     */
    public static Optional<UV> uvForHit(BlockPos position, Direction face, BlockHitResult hit) {
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(face, "face");
        Objects.requireNonNull(hit, "hit");
        if (hit.getType() != HitResult.Type.BLOCK
            || !position.equals(hit.getBlockPos())
            || hit.getDirection() != face
            || hit.isInside()) {
            return Optional.empty();
        }

        Vec3 point = hit.getLocation();
        double x = point.x - position.getX() - 0.5;
        double y = point.y - position.getY() - 0.5;
        double z = point.z - position.getZ() - 0.5;
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
            return Optional.empty();
        }

        // The model is a full cube. Ray hits lie on its face, while the
        // renderer's quad sits slightly beyond it only to avoid z-fighting.
        double outward = x * face.getStepX() + y * face.getStepY() + z * face.getStepZ();
        if (Math.abs(outward - 0.5) > HIT_TOLERANCE) {
            return Optional.empty();
        }

        Basis basis = basis(face);
        double right = x * basis.rightX() + y * basis.rightY() + z * basis.rightZ();
        double up = x * basis.upX() + y * basis.upY() + z * basis.upZ();
        if (Math.abs(right) > HALF_IMAGE_SIZE + HIT_TOLERANCE
            || Math.abs(up) > HALF_IMAGE_SIZE + HIT_TOLERANCE) {
            return Optional.empty();
        }

        double u = (right + HALF_IMAGE_SIZE) / IMAGE_SIZE;
        double v = (HALF_IMAGE_SIZE - up) / IMAGE_SIZE;
        return Optional.of(new UV(clampUnit(u), clampUnit(v)));
    }

    private static double clampUnit(double coordinate) {
        return Math.max(0.0, Math.min(1.0, coordinate));
    }
}
