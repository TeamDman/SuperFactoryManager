package ca.teamdman.sfml.ast;

import java.util.List;
import java.util.Locale;

public enum ComparisonOperator implements SfmlAstNode, ToStringPretty, BiLongPredicate {
    GREATER((a, b) -> a > b),
    LESSER((a, b) -> a < b),
    EQUALS((a, b) -> a == b),
    NOT_EQUAL((a, b) -> a != b),
    LESSER_OR_EQUAL((a, b) -> a <= b),
    GREATER_OR_EQUAL((a, b) -> a >= b);

    private final BiLongPredicate predicate;

    ComparisonOperator(BiLongPredicate predicate) {

        this.predicate = predicate;
    }

    public static ComparisonOperator from(String text) {

        return switch (text.toUpperCase(Locale.ROOT)) {
            case "GT", ">" -> GREATER;
            case "LT", "<" -> LESSER;
            case "EQ", "=" -> EQUALS;
            case "NE", "!=", "<>" -> NOT_EQUAL;
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
            case NOT_EQUAL -> "!=";
            case LESSER_OR_EQUAL -> "<=";
            case GREATER_OR_EQUAL -> ">=";
        };
    }

    @Override
    public boolean test(
            long a,
            long b
    ) {

        return predicate.test(a, b);
    }

    @Override
    public List<? extends SfmlAstNode> getChildNodes() {

        return List.of();
    }

}
