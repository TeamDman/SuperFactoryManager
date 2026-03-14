package ca.teamdman.sfml.ast;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public record NumberSet(
        NumberRange[] ranges,

        NumberRange[] exclusions
) implements SfmlAstNode {
    public static final NumberSet MAX_RANGE = new NumberSet(
            new NumberRange[]{NumberRange.MAX_RANGE},
            new NumberRange[]{}
    );

    public boolean contains(int value) {

        for (NumberRange exclusion : exclusions) {
            if (exclusion.contains(value)) {
                return false;
            }
        }
        for (NumberRange range : ranges) {
            if (range.contains(value)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String toString() {

        StringBuilder sb = new StringBuilder();
        for (NumberRange range : ranges) {
            sb.append(range).append(',');
        }
        for (NumberRange range : exclusions) {
            sb.append("NOT ").append(range).append(',');
        }
        sb.deleteCharAt(sb.length() - 1);
        return sb.toString();
    }

    @Override
    public List<NumberRange> getChildNodes() {

        ArrayList<NumberRange> rtn = new ArrayList<>(ranges.length + exclusions.length);
        rtn.addAll(Arrays.asList(ranges));
        rtn.addAll(Arrays.asList(exclusions));
        return rtn;
    }
    
    /// Given something like 1,2,3,4-6 transform to 1-6
    public NumberSet compact() {
        return new NumberSet(NumberRange.compactRanges(ranges), NumberRange.compactRanges(exclusions));
    }

    public static NumberSet of(NumberRange[] ranges, NumberRange[] exclusions) {
        return new NumberSet(
                NumberRange.compactRanges(ranges),
                NumberRange.compactRanges(exclusions)
        );
    }

    public static NumberSet of(NumberRange[] ranges) {
        return of(ranges, new NumberRange[]{});
    }

    public static NumberSet of(NumberRange range) {
        return of(new NumberRange[]{range}, new NumberRange[]{});
    }

    public static NumberSet of(int... i) {
        NumberRange[] ranges = new NumberRange[i.length];
        for (int j = 0; j < i.length; j++) {
            ranges[j] = NumberRange.ofInclusive(
                    NumberExpression.fromLiteral(i[j]),
                    NumberExpression.fromLiteral(i[j])
            );
        }
        return of(ranges, new NumberRange[]{});
    }

}
