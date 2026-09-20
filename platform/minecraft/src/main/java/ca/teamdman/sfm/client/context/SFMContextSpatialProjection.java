package ca.teamdman.sfm.client.context;

import ca.teamdman.sfm.client.explorer.SFMPath;

import java.util.Objects;
import java.util.Optional;

/**
 * Reserved provider-neutral spatial context for future screen-point/ray
 * contributors. Capturing this DTO performs no hit test or world inspection.
 */
public record SFMContextSpatialProjection(
        String coordinateSpaceId,
        Optional<ScreenPoint> screenPoint,
        Optional<Ray> ray,
        Optional<Hit> hit
) implements SFMContextProjection {
    public SFMContextSpatialProjection {
        coordinateSpaceId = requireId(coordinateSpaceId, "coordinateSpaceId");
        screenPoint = Objects.requireNonNull(screenPoint, "screenPoint");
        ray = Objects.requireNonNull(ray, "ray");
        hit = Objects.requireNonNull(hit, "hit");
        if (screenPoint.isEmpty() && ray.isEmpty()) {
            throw new IllegalArgumentException("Spatial context requires a screen point or ray");
        }
    }

    public record ScreenPoint(double x, double y) {
        public ScreenPoint {
            requireFinite(x, "screen x");
            requireFinite(y, "screen y");
        }
    }

    public record Vector3(double x, double y, double z) {
        public Vector3 {
            requireFinite(x, "vector x");
            requireFinite(y, "vector y");
            requireFinite(z, "vector z");
        }

        public boolean isZero() {
            return x == 0 && y == 0 && z == 0;
        }
    }

    public record Ray(Vector3 origin, Vector3 direction) {
        public Ray {
            Objects.requireNonNull(origin, "origin");
            Objects.requireNonNull(direction, "direction");
            if (direction.isZero()) throw new IllegalArgumentException("Ray direction must not be zero");
        }
    }

    /** Optional already-resolved hit metadata supplied by its named provider. */
    public record Hit(
            String providerId,
            String kind,
            Optional<SFMPath> address
    ) {
        public Hit {
            providerId = requireId(providerId, "providerId");
            kind = requireId(kind, "kind");
            address = Objects.requireNonNull(address, "address");
        }
    }

    private static String requireId(String value, String label) {
        Objects.requireNonNull(value, label);
        if (value.isBlank() || value.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException(label + " must be printable and non-blank");
        }
        return value;
    }

    private static void requireFinite(double value, String label) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException(label + " must be finite");
    }
}
