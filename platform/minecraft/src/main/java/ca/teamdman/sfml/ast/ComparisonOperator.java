package ca.teamdman.sfml.ast;

import java.util.Locale;
import java.util.function.BiPredicate;

public enum ComparisonOperator implements ASTNode, BiPredicate<Long, Long>, ToStringPretty {
    GREATER((a, b) -> a > b),
    LESSER((a, b) -> a < b),
    EQUALS(Long::equals),
    LESSER_OR_EQUAL((a, b) -> a <= b),
    GREATER_OR_EQUAL((a, b) -> a >= b);

    private final BiPredicate<Long, Long> PRED;

    ComparisonOperator(BiPredicate<Long, Long> pred) {
        this.PRED = pred;
    }

    public static ComparisonOperator from(String text) {
        return switch (text.toUpperCase(Locale.ROOT)) {
            case "GT", ">" -> GREATER;
            case "LT", "<" -> LESSER;
            case "EQ", "=" -> EQUALS;
            case "LE", "<=" -> LESSER_OR_EQUAL;
            case "GE", ">=" -> GREATER_OR_EQUAL;
            default -> throw new IllegalArgumentException("Invalid comparison operator: " + text);
        };
    }

    @Override
    public String toString() {
        return switch (this) {
            case GREATER -> ">";
            case LESSER -> "<";
            case EQUALS -> "=";
            case LESSER_OR_EQUAL -> "<=";
            case GREATER_OR_EQUAL -> ">=";
        };
    }

    @Override
    public boolean test(Long a, Long b) {
        return PRED.test(a, b);
    }
}
