package ca.teamdman.sfm.client.history.comparison;

import java.util.Objects;

/**
 * Immutable, persisted review state for comparing two retained trajectory routes.
 *
 * <p>The session deliberately stores route identities rather than projected frames,
 * comments, costs, outcomes, or trajectory-machine state. Those values remain owned
 * by their authoritative resolvers and are joined at presentation time.</p>
 */
public record SFMRouteComparisonSession(
        String schema,
        String id,
        RouteAddress left,
        RouteAddress right,
        Mode mode,
        int leftCursor,
        int rightCursor,
        int leftLastPosition,
        int rightLastPosition,
        Disposition leftDisposition,
        Disposition rightDisposition,
        long revision
) {
    public static final String SCHEMA = "sfm.route-comparison/1";

    public SFMRouteComparisonSession {
        if (!SCHEMA.equals(schema)) {
            throw new IllegalArgumentException("Unsupported route-comparison schema " + schema);
        }
        id = requireText(id, "session.id");
        Objects.requireNonNull(left, "left");
        Objects.requireNonNull(right, "right");
        if (left.equals(right)) {
            throw new IllegalArgumentException("A comparison requires two distinct retained-route addresses");
        }
        if (!left.machineId().equals(right.machineId())) {
            throw new IllegalArgumentException("X3 route comparison requires both routes to belong to one machine");
        }
        Objects.requireNonNull(mode, "mode");
        requireNonNegative(leftCursor, "leftCursor");
        requireNonNegative(rightCursor, "rightCursor");
        requireNonNegative(leftLastPosition, "leftLastPosition");
        requireNonNegative(rightLastPosition, "rightLastPosition");
        requireAtMost(leftCursor, leftLastPosition, "leftCursor", "leftLastPosition");
        requireAtMost(rightCursor, rightLastPosition, "rightCursor", "rightLastPosition");
        if (mode == Mode.LOCKSTEP
                && rightCursor != mapCursor(leftCursor, leftLastPosition, rightLastPosition)
                && leftCursor != mapCursor(rightCursor, rightLastPosition, leftLastPosition)) {
            throw new IllegalArgumentException("Lockstep cursors must represent progress anchored by one compared side");
        }
        Objects.requireNonNull(leftDisposition, "leftDisposition");
        Objects.requireNonNull(rightDisposition, "rightDisposition");
        if (leftDisposition == Disposition.PREFERRED && rightDisposition == Disposition.PREFERRED) {
            throw new IllegalArgumentException("At most one compared route may be preferred");
        }
        if (revision < 0) throw new IllegalArgumentException("revision must not be negative");
    }

    public static SFMRouteComparisonSession create(
            String id,
            RouteAddress left,
            RouteAddress right,
            int leftLastPosition,
            int rightLastPosition
    ) {
        return new SFMRouteComparisonSession(
                SCHEMA,
                id,
                left,
                right,
                Mode.LOCKSTEP,
                0,
                0,
                leftLastPosition,
                rightLastPosition,
                Disposition.UNDECIDED,
                Disposition.UNDECIDED,
                0
        );
    }

    public RouteAddress address(Side side) {
        return switch (Objects.requireNonNull(side, "side")) {
            case LEFT -> left;
            case RIGHT -> right;
            case BOTH -> throw new IllegalArgumentException("BOTH does not identify one route address");
        };
    }

    public int cursor(Side side) {
        return switch (Objects.requireNonNull(side, "side")) {
            case LEFT -> leftCursor;
            case RIGHT -> rightCursor;
            case BOTH -> throw new IllegalArgumentException("BOTH does not identify one cursor");
        };
    }

    public Disposition disposition(Side side) {
        return switch (Objects.requireNonNull(side, "side")) {
            case LEFT -> leftDisposition;
            case RIGHT -> rightDisposition;
            case BOTH -> throw new IllegalArgumentException("BOTH does not identify one disposition");
        };
    }

    /** The immutable identity of one route retained by a trajectory machine. */
    public record RouteAddress(String machineId, String planRevisionId, String routeId) {
        public RouteAddress {
            machineId = requireText(machineId, "route.machineId");
            planRevisionId = requireText(planRevisionId, "route.planRevisionId");
            routeId = requireText(routeId, "route.routeId");
        }
    }

    public enum Mode {
        LOCKSTEP,
        INDEPENDENT
    }

    public enum Side {
        LEFT,
        RIGHT,
        BOTH
    }

    /** Review-only metadata; it is neither approval nor trajectory selection. */
    public enum Disposition {
        UNDECIDED,
        PREFERRED,
        REJECTED
    }

    private static String requireText(String value, String label) {
        Objects.requireNonNull(value, label);
        if (value.isBlank()) throw new IllegalArgumentException(label + " must not be blank");
        return value;
    }

    private static void requireNonNegative(int value, String label) {
        if (value < 0) throw new IllegalArgumentException(label + " must not be negative");
    }

    private static void requireAtMost(int value, int maximum, String valueLabel, String maximumLabel) {
        if (value > maximum) {
            throw new IllegalArgumentException(valueLabel + " must not exceed " + maximumLabel);
        }
    }

    private static int mapCursor(int sourceCursor, int sourceLastCursor, int targetLastCursor) {
        if (sourceLastCursor == 0 || targetLastCursor == 0) return 0;
        long scaled = (long) sourceCursor * targetLastCursor;
        long quotient = scaled / sourceLastCursor;
        long remainder = scaled % sourceLastCursor;
        if (remainder * 2 >= sourceLastCursor) quotient++;
        return Math.toIntExact(quotient);
    }
}
