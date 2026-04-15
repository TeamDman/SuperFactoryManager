package ca.teamdman.sfm.test;

import ca.teamdman.sfm.client.draw.SFMDrawBindingUtil;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class SFMDrawBindingUtilTests {
    @Test
    public void pointOnBoundsTowardProjectsToTheTargetEdge() {
        SFMDrawBindingUtil.Bounds bounds = new SFMDrawBindingUtil.Bounds(10.0D, 20.0D, 30.0D, 40.0D);

        SFMDrawBindingUtil.Point point = SFMDrawBindingUtil.pointOnBoundsToward(
                bounds,
                0.5D,
                0.5D,
                new SFMDrawBindingUtil.Point(60.0D, 30.0D)
        );

        assertEquals(30.0D, point.x(), 0.000001D);
        assertEquals(30.0D, point.y(), 0.000001D);
    }

    @Test
    public void closestPointAndNormalizedFocusClampToBounds() {
        SFMDrawBindingUtil.Bounds bounds = new SFMDrawBindingUtil.Bounds(10.0D, 20.0D, 30.0D, 40.0D);
        SFMDrawBindingUtil.Point outsidePoint = new SFMDrawBindingUtil.Point(35.0D, 10.0D);
        SFMDrawBindingUtil.Point insidePoint = new SFMDrawBindingUtil.Point(18.0D, 23.0D);

        SFMDrawBindingUtil.Point closestPoint = SFMDrawBindingUtil.closestPointOnBounds(bounds, insidePoint);

        assertEquals(18.0D, closestPoint.x(), 0.000001D);
        assertEquals(20.0D, closestPoint.y(), 0.000001D);
        assertEquals(1.0D, SFMDrawBindingUtil.normalizedFocusX(bounds, outsidePoint), 0.000001D);
        assertEquals(0.0D, SFMDrawBindingUtil.normalizedFocusY(bounds, outsidePoint), 0.000001D);
    }
}