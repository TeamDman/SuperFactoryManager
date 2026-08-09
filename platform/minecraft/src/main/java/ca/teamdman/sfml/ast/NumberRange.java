package ca.teamdman.sfml.ast;

public record NumberRange(
        long start,
        long end
) implements ASTNode {
    public static final NumberRange MAX_RANGE = new NumberRange(Long.MIN_VALUE, Long.MAX_VALUE);

    /**
     * Inclusive
     */
    public boolean contains(int value) {
        return value >= start && value <= end;
    }

    @Override
    public String toString() {
        if (start == end) return String.valueOf(start);
        return start + "-" + end;
    }
}
