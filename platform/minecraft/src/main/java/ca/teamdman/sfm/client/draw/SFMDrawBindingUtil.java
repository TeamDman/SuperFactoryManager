package ca.teamdman.sfm.client.draw;

import net.minecraft.util.Mth;

public final class SFMDrawBindingUtil {
    private static final double EPSILON = 0.000001D;

    private SFMDrawBindingUtil() {
    }

    public static Point clampToBounds(
            Bounds bounds,
            Point point
    ) {
        return new Point(
                Mth.clamp(point.x(), bounds.minX(), bounds.maxX()),
                Mth.clamp(point.y(), bounds.minY(), bounds.maxY())
        );
    }

    public static Point pointAtNormalizedFocus(
            Bounds bounds,
            double focusX,
            double focusY
    ) {
        return new Point(
                bounds.minX() + Mth.clamp(focusX, 0.0D, 1.0D) * bounds.width(),
                bounds.minY() + Mth.clamp(focusY, 0.0D, 1.0D) * bounds.height()
        );
    }

    public static Point pointOnBoundsToward(
            Bounds bounds,
            double focusX,
            double focusY,
            Point towardPoint
    ) {
        Point focusPoint = clampToBounds(bounds, pointAtNormalizedFocus(bounds, focusX, focusY));
        double dx = towardPoint.x() - focusPoint.x();
        double dy = towardPoint.y() - focusPoint.y();
        if (Math.abs(dx) <= EPSILON && Math.abs(dy) <= EPSILON) {
            return closestPointOnBounds(bounds, towardPoint);
        }

        double t = Double.POSITIVE_INFINITY;
        if (dx > EPSILON) {
            t = Math.min(t, (bounds.maxX() - focusPoint.x()) / dx);
        } else if (dx < -EPSILON) {
            t = Math.min(t, (bounds.minX() - focusPoint.x()) / dx);
        }
        if (dy > EPSILON) {
            t = Math.min(t, (bounds.maxY() - focusPoint.y()) / dy);
        } else if (dy < -EPSILON) {
            t = Math.min(t, (bounds.minY() - focusPoint.y()) / dy);
        }

        if (!Double.isFinite(t) || t < 0.0D) {
            return closestPointOnBounds(bounds, towardPoint);
        }
        return new Point(focusPoint.x() + dx * t, focusPoint.y() + dy * t);
    }

    public static double normalizedFocusX(
            Bounds bounds,
            Point point
    ) {
        if (bounds.width() <= EPSILON) {
            return 0.5D;
        }
        Point clamped = clampToBounds(bounds, point);
        return (clamped.x() - bounds.minX()) / bounds.width();
    }

    public static double normalizedFocusY(
            Bounds bounds,
            Point point
    ) {
        if (bounds.height() <= EPSILON) {
            return 0.5D;
        }
        Point clamped = clampToBounds(bounds, point);
        return (clamped.y() - bounds.minY()) / bounds.height();
    }

    public static Point closestPointOnBounds(
            Bounds bounds,
            Point point
    ) {
        Point clamped = clampToBounds(bounds, point);
        double leftDistance = Math.abs(clamped.x() - bounds.minX());
        double rightDistance = Math.abs(bounds.maxX() - clamped.x());
        double topDistance = Math.abs(clamped.y() - bounds.minY());
        double bottomDistance = Math.abs(bounds.maxY() - clamped.y());

        double bestDistance = leftDistance;
        Point bestPoint = new Point(bounds.minX(), clamped.y());
        if (rightDistance < bestDistance) {
            bestDistance = rightDistance;
            bestPoint = new Point(bounds.maxX(), clamped.y());
        }
        if (topDistance < bestDistance) {
            bestDistance = topDistance;
            bestPoint = new Point(clamped.x(), bounds.minY());
        }
        if (bottomDistance < bestDistance) {
            bestPoint = new Point(clamped.x(), bounds.maxY());
        }
        return bestPoint;
    }

    public record Point(
            double x,
            double y
    ) {
    }

    public record Bounds(
            double minX,
            double minY,
            double maxX,
            double maxY
    ) {
        public double width() {
            return maxX - minX;
        }

        public double height() {
            return maxY - minY;
        }
    }
}
