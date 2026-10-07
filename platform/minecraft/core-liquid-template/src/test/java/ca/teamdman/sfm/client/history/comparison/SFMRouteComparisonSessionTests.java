package ca.teamdman.sfm.client.history.comparison;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static ca.teamdman.sfm.client.history.comparison.SFMRouteComparisonSession.Disposition;
import static ca.teamdman.sfm.client.history.comparison.SFMRouteComparisonSession.Mode;
import static ca.teamdman.sfm.client.history.comparison.SFMRouteComparisonSession.Side;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SFMRouteComparisonSessionTests {
    @Test
    void publicSessionSurfaceStoresOnlyAddressesReviewStateAndCursors() {
        assertEquals(List.of(
                        "schema", "id", "left", "right", "mode", "leftCursor", "rightCursor",
                        "leftLastPosition", "rightLastPosition", "leftDisposition", "rightDisposition", "revision"
                ), Arrays.stream(SFMRouteComparisonSession.class.getRecordComponents())
                        .map(component -> component.getName())
                        .toList());
        SFMRouteComparisonSession session = session();
        assertEquals(SFMRouteComparisonSession.SCHEMA, session.schema());
        assertEquals(Mode.LOCKSTEP, session.mode());
        assertEquals(0, session.revision());
    }

    @Test
    void identityAndDispositionInvariantsAreEnforced() {
        var left = address("plan-left", "route-left");
        assertThrows(IllegalArgumentException.class, () ->
                SFMRouteComparisonSession.create("same", left, left, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> SFMRouteComparisonSession.create(
                "cross-machine",
                left,
                new SFMRouteComparisonSession.RouteAddress("machine-2", "plan-right", "route-right"),
                1,
                1
        ));
        assertThrows(IllegalArgumentException.class, () -> new SFMRouteComparisonSession(
                SFMRouteComparisonSession.SCHEMA,
                "both-preferred",
                left,
                address("plan-right", "route-right"),
                Mode.LOCKSTEP,
                0,
                0,
                0,
                0,
                Disposition.PREFERRED,
                Disposition.PREFERRED,
                0
        ));
        assertThrows(IllegalArgumentException.class, () -> new SFMRouteComparisonSession.RouteAddress(
                " ", "plan", "route"));
        assertThrows(IllegalArgumentException.class, () -> new SFMRouteComparisonSession(
                SFMRouteComparisonSession.SCHEMA,
                "negative",
                left,
                address("plan-right", "route-right"),
                Mode.LOCKSTEP,
                -1,
                0,
                0,
                0,
                Disposition.UNDECIDED,
                Disposition.UNDECIDED,
                0
        ));
    }

    @Test
    void lockstepSeekingMapsUnequalLengthsFromEitherExactAnchor() {
        SFMRouteComparisonSession initial = session();

        SFMRouteComparisonSession fromLeft = SFMRouteComparisonKernel.seek(initial, Side.LEFT, 1);
        assertEquals(1, fromLeft.leftCursor());
        assertEquals(3, fromLeft.rightCursor(), "2.5 rounds upward deterministically");

        SFMRouteComparisonSession fromRight = SFMRouteComparisonKernel.seek(fromLeft, Side.RIGHT, 1);
        assertEquals(0, fromRight.leftCursor());
        assertEquals(1, fromRight.rightCursor());

        SFMRouteComparisonSession endpoint = SFMRouteComparisonKernel.seek(fromRight, Side.RIGHT, 5);
        assertEquals(2, endpoint.leftCursor());
        assertEquals(5, endpoint.rightCursor());
        assertEquals(3, endpoint.revision());

        assertEquals(0, SFMRouteComparisonKernel.mapCursor(0, 0, 7));
        assertEquals(0, SFMRouteComparisonKernel.mapCursor(2, 7, 0));
        assertEquals(7, SFMRouteComparisonKernel.mapCursor(2, 2, 7));
    }

    @Test
    void independentSeekingMovesOnlyTheRequestedCursorAndLockstepReanchorsFromLeft() {
        SFMRouteComparisonSession independent = SFMRouteComparisonKernel.setMode(session(), Mode.INDEPENDENT);
        independent = SFMRouteComparisonKernel.seek(independent, Side.LEFT, 2);
        assertEquals(2, independent.leftCursor());
        assertEquals(0, independent.rightCursor());
        independent = SFMRouteComparisonKernel.seek(independent, Side.RIGHT, 4);
        assertEquals(2, independent.leftCursor());
        assertEquals(4, independent.rightCursor());
        independent = SFMRouteComparisonKernel.seek(independent, Side.BOTH, 4);
        assertEquals(2, independent.leftCursor());
        assertEquals(4, independent.rightCursor());

        SFMRouteComparisonSession leftAnchored = SFMRouteComparisonKernel.setMode(independent, Mode.LOCKSTEP);
        assertEquals(2, leftAnchored.leftCursor());
        assertEquals(5, leftAnchored.rightCursor());
    }

    @Test
    void everySmallUnequalRoutePairMapsDeterministicallyForLeftRightAndBoth() {
        for (int leftLast = 0; leftLast <= 12; leftLast++) {
            for (int rightLast = 0; rightLast <= 12; rightLast++) {
                SFMRouteComparisonSession initial = SFMRouteComparisonSession.create(
                        "comparison-" + leftLast + "-" + rightLast,
                        address("plan-left", "route-left"),
                        address("plan-right", "route-right"),
                        leftLast,
                        rightLast
                );
                for (int left = 0; left <= leftLast; left++) {
                    SFMRouteComparisonSession sought = SFMRouteComparisonKernel.seek(initial, Side.LEFT, left);
                    assertEquals(left, sought.leftCursor());
                    assertEquals(SFMRouteComparisonKernel.mapCursor(left, leftLast, rightLast),
                            sought.rightCursor());
                }
                for (int right = 0; right <= rightLast; right++) {
                    SFMRouteComparisonSession sought = SFMRouteComparisonKernel.seek(initial, Side.RIGHT, right);
                    assertEquals(right, sought.rightCursor());
                    assertEquals(SFMRouteComparisonKernel.mapCursor(right, rightLast, leftLast),
                            sought.leftCursor());
                }
                int sharedLast = Math.max(leftLast, rightLast);
                for (int shared = 0; shared <= sharedLast; shared++) {
                    SFMRouteComparisonSession sought = SFMRouteComparisonKernel.seek(initial, Side.BOTH, shared);
                    assertEquals(SFMRouteComparisonKernel.mapCursor(shared, sharedLast, leftLast),
                            sought.leftCursor());
                    assertEquals(SFMRouteComparisonKernel.mapCursor(shared, sharedLast, rightLast),
                            sought.rightCursor());
                }
            }
        }
    }

    @Test
    void preferredIsExclusiveButBothRoutesMayBeRejected() {
        SFMRouteComparisonSession initial = session();
        SFMRouteComparisonSession preferLeft = SFMRouteComparisonKernel.setDisposition(
                initial, Side.LEFT, Disposition.PREFERRED);
        assertEquals(Disposition.PREFERRED, preferLeft.leftDisposition());
        assertEquals(Disposition.UNDECIDED, preferLeft.rightDisposition());

        SFMRouteComparisonSession preferRight = SFMRouteComparisonKernel.setDisposition(
                preferLeft, Side.RIGHT, Disposition.PREFERRED);
        assertEquals(Disposition.UNDECIDED, preferRight.leftDisposition());
        assertEquals(Disposition.PREFERRED, preferRight.rightDisposition());

        SFMRouteComparisonSession rejectLeft = SFMRouteComparisonKernel.setDisposition(
                preferRight, Side.LEFT, Disposition.REJECTED);
        SFMRouteComparisonSession rejectBoth = SFMRouteComparisonKernel.setDisposition(
                rejectLeft, Side.BOTH, Disposition.REJECTED);
        assertEquals(Disposition.REJECTED, rejectBoth.leftDisposition());
        assertEquals(Disposition.REJECTED, rejectBoth.rightDisposition());
        assertThrows(IllegalArgumentException.class, () -> SFMRouteComparisonKernel.setDisposition(
                rejectBoth, Side.BOTH, Disposition.PREFERRED));
    }

    @Test
    void everyTransitionIsNonDestructiveAndNoOpsPreserveIdentity() {
        SFMRouteComparisonSession initial = session();
        SFMRouteComparisonSession snapshot = initial;
        SFMRouteComparisonSession reviewed = SFMRouteComparisonKernel.setDisposition(
                initial, Side.LEFT, Disposition.REJECTED);
        SFMRouteComparisonSession sought = SFMRouteComparisonKernel.seek(reviewed, Side.RIGHT, 5);

        assertEquals(snapshot, initial);
        assertEquals(initial.left(), sought.left());
        assertEquals(initial.right(), sought.right());
        assertEquals(Disposition.REJECTED, sought.leftDisposition());
        assertEquals(Disposition.UNDECIDED, sought.rightDisposition());
        assertEquals(0, initial.leftCursor());
        assertEquals(0, initial.rightCursor());
        assertNotEquals(initial, sought);
        assertSame(sought, SFMRouteComparisonKernel.seek(sought, Side.RIGHT, 5));
        assertSame(sought, SFMRouteComparisonKernel.setDisposition(
                sought, Side.LEFT, Disposition.REJECTED));
    }

    @Test
    void resolvedBoundsFailRatherThanClampingOrSubstitutingRoutes() {
        assertThrows(IllegalArgumentException.class, () ->
                SFMRouteComparisonKernel.seek(session(), Side.LEFT, 3));
        assertThrows(IllegalArgumentException.class, () ->
                new SFMRouteComparisonSession(
                        SFMRouteComparisonSession.SCHEMA, "stale", address("p1", "r1"),
                        address("p2", "r2"), Mode.INDEPENDENT, 2, 5, 1, 5,
                        Disposition.UNDECIDED, Disposition.UNDECIDED, 0));
    }

    private static SFMRouteComparisonSession session() {
        return SFMRouteComparisonSession.create(
                "comparison-1",
                address("plan-left", "route-left"),
                address("plan-right", "route-right"),
                2,
                5
        );
    }

    private static SFMRouteComparisonSession.RouteAddress address(String plan, String route) {
        return new SFMRouteComparisonSession.RouteAddress("machine-1", plan, route);
    }
}
