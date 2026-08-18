package ca.teamdman.sfm.client.semantic;

import java.util.Objects;

/** Explicit projection of a destination region; never an ambient cursor offset. */
public record SFMNavigationProjection(Kind kind, double percentage, int index, String landmark) {
    public enum Kind {
        START, END, CENTRE, PERCENTAGE, NTH_SOURCE_ROW, NTH_SOURCE_LINE, NTH_CHILD, NAMED_LANDMARK
    }

    public SFMNavigationProjection {
        Objects.requireNonNull(kind, "kind");
        if (kind == Kind.PERCENTAGE && (!Double.isFinite(percentage) || percentage < 0 || percentage > 1)) {
            throw new IllegalArgumentException("percentage must be finite and in [0, 1]");
        }
        if ((kind == Kind.NTH_SOURCE_ROW || kind == Kind.NTH_SOURCE_LINE || kind == Kind.NTH_CHILD)
                && index < 0) {
            throw new IllegalArgumentException("projection index must be non-negative");
        }
        if (kind == Kind.NAMED_LANDMARK && (landmark == null || landmark.isBlank())) {
            throw new IllegalArgumentException("named landmark must not be blank");
        }
    }

    public static SFMNavigationProjection start() {
        return new SFMNavigationProjection(Kind.START, 0, 0, null);
    }

    public static SFMNavigationProjection end() {
        return new SFMNavigationProjection(Kind.END, 1, 0, null);
    }

    public static SFMNavigationProjection centre() {
        return new SFMNavigationProjection(Kind.CENTRE, 0.5, 0, null);
    }

    public static SFMNavigationProjection percentage(double value) {
        return new SFMNavigationProjection(Kind.PERCENTAGE, value, 0, null);
    }

    public static SFMNavigationProjection indexed(Kind kind, int index) {
        return new SFMNavigationProjection(kind, 0, index, null);
    }

    public static SFMNavigationProjection named(String landmark) {
        return new SFMNavigationProjection(Kind.NAMED_LANDMARK, 0, 0, landmark);
    }
}
