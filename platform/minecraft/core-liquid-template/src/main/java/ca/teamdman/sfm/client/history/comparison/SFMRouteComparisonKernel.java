package ca.teamdman.sfm.client.history.comparison;

import java.util.Objects;

import static ca.teamdman.sfm.client.history.comparison.SFMRouteComparisonSession.Disposition;
import static ca.teamdman.sfm.client.history.comparison.SFMRouteComparisonSession.Mode;
import static ca.teamdman.sfm.client.history.comparison.SFMRouteComparisonSession.Side;

/** Pure state transitions for {@link SFMRouteComparisonSession}. */
public final class SFMRouteComparisonKernel {
    private SFMRouteComparisonKernel() {
    }

    /**
     * Seek one side. In lockstep mode the requested side remains exact and the
     * other side is mapped proportionally with deterministic half-up rounding.
     */
    public static SFMRouteComparisonSession seek(
            SFMRouteComparisonSession session,
            Side requestedSide,
            int requestedCursor
    ) {
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(requestedSide, "requestedSide");
        int requestedLastPosition = switch (requestedSide) {
            case LEFT -> session.leftLastPosition();
            case RIGHT -> session.rightLastPosition();
            case BOTH -> Math.max(session.leftLastPosition(), session.rightLastPosition());
        };
        requireCursorInRange(requestedCursor, requestedLastPosition,
                "requestedCursor");

        int leftCursor = session.leftCursor();
        int rightCursor = session.rightCursor();
        if (requestedSide == Side.BOTH) {
            leftCursor = mapCursor(requestedCursor, requestedLastPosition, session.leftLastPosition());
            rightCursor = mapCursor(requestedCursor, requestedLastPosition, session.rightLastPosition());
        } else if (session.mode() == Mode.INDEPENDENT) {
            if (requestedSide == Side.LEFT) leftCursor = requestedCursor;
            else rightCursor = requestedCursor;
        } else if (requestedSide == Side.LEFT) {
            leftCursor = requestedCursor;
            rightCursor = mapCursor(requestedCursor, session.leftLastPosition(), session.rightLastPosition());
        } else {
            rightCursor = requestedCursor;
            leftCursor = mapCursor(requestedCursor, session.rightLastPosition(), session.leftLastPosition());
        }
        return evolve(session, session.mode(), leftCursor, rightCursor,
                session.leftDisposition(), session.rightDisposition());
    }

    /**
     * Change cursor mode. Entering lockstep deterministically anchors the left
     * cursor; entering independent mode preserves both current cursors.
     */
    public static SFMRouteComparisonSession setMode(
            SFMRouteComparisonSession session,
            Mode mode
    ) {
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(mode, "mode");
        if (session.mode() == mode) return session;

        int leftCursor = session.leftCursor();
        int rightCursor = session.rightCursor();
        if (mode == Mode.LOCKSTEP) {
            rightCursor = mapCursor(leftCursor, session.leftLastPosition(), session.rightLastPosition());
        }
        return evolve(session, mode, leftCursor, rightCursor,
                session.leftDisposition(), session.rightDisposition());
    }

    /**
     * Set review disposition without touching either retained route or cursor.
     * Preferring one side demotes a previously preferred peer to undecided;
     * rejecting routes is independent, so both sides may be rejected.
     */
    public static SFMRouteComparisonSession setDisposition(
            SFMRouteComparisonSession session,
            Side side,
            Disposition disposition
    ) {
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(side, "side");
        Objects.requireNonNull(disposition, "disposition");

        Disposition left = session.leftDisposition();
        Disposition right = session.rightDisposition();
        switch (side) {
            case LEFT -> {
                left = disposition;
                if (disposition == Disposition.PREFERRED && right == Disposition.PREFERRED) {
                    right = Disposition.UNDECIDED;
                }
            }
            case RIGHT -> {
                right = disposition;
                if (disposition == Disposition.PREFERRED && left == Disposition.PREFERRED) {
                    left = Disposition.UNDECIDED;
                }
            }
            case BOTH -> {
                if (disposition == Disposition.PREFERRED) {
                    throw new IllegalArgumentException("Both compared routes cannot be preferred");
                }
                left = disposition;
                right = disposition;
            }
        }
        return evolve(session, session.mode(), session.leftCursor(), session.rightCursor(), left, right);
    }

    /**
     * Map a cursor between unequal inclusive ranges. Endpoints are exact and
     * midpoint ties round upward. A zero-length source maps to the target start.
     */
    public static int mapCursor(int sourceCursor, int sourceLastCursor, int targetLastCursor) {
        if (sourceLastCursor < 0) throw new IllegalArgumentException("sourceLastCursor must not be negative");
        if (targetLastCursor < 0) throw new IllegalArgumentException("targetLastCursor must not be negative");
        requireCursorInRange(sourceCursor, sourceLastCursor, "sourceCursor");
        if (sourceLastCursor == 0 || targetLastCursor == 0) return 0;
        long scaled = (long) sourceCursor * targetLastCursor;
        long quotient = scaled / sourceLastCursor;
        long remainder = scaled % sourceLastCursor;
        if (remainder * 2 >= sourceLastCursor) quotient++;
        return Math.toIntExact(quotient);
    }

    private static void requireCursorInRange(int cursor, int lastCursor, String label) {
        if (cursor < 0 || cursor > lastCursor) {
            throw new IllegalArgumentException(label + " " + cursor + " is outside [0," + lastCursor + "]");
        }
    }

    private static SFMRouteComparisonSession evolve(
            SFMRouteComparisonSession session,
            Mode mode,
            int leftCursor,
            int rightCursor,
            Disposition leftDisposition,
            Disposition rightDisposition
    ) {
        if (session.mode() == mode
                && session.leftCursor() == leftCursor
                && session.rightCursor() == rightCursor
                && session.leftDisposition() == leftDisposition
                && session.rightDisposition() == rightDisposition) {
            return session;
        }
        return new SFMRouteComparisonSession(
                session.schema(),
                session.id(),
                session.left(),
                session.right(),
                mode,
                leftCursor,
                rightCursor,
                session.leftLastPosition(),
                session.rightLastPosition(),
                leftDisposition,
                rightDisposition,
                Math.incrementExact(session.revision())
        );
    }
}
