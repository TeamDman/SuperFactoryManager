package ca.teamdman.sfml.ast;

import java.util.Arrays;
import java.util.stream.Collectors;

public record NumberRangeSet(NumberRange[] ranges) implements ASTNode {
    public static final NumberRangeSet MAX_RANGE = new NumberRangeSet(new NumberRange[]{NumberRange.MAX_RANGE});
    public boolean contains(int value) {
        for (NumberRange range : ranges) {
            if (range.contains(value)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String toString() {
        return "[" + (this.equals(MAX_RANGE) ? "ALL" : Arrays.stream(ranges).map(NumberRange::toString).collect(Collectors.joining(","))) + "]";
    }
}
