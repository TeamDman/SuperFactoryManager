package ca.teamdman.sfml.ast;

import org.jetbrains.annotations.Nullable;

/**
 * AST node representing a filter expression inside array brackets [?...].
 * Examples: [?id == "minecraft:sharpness"], [?lvl > 3]
 */
public record NbtFilterExpr(
        NbtFilterPath path,
        @Nullable ComparisonOperator operator,
        @Nullable NbtValue value
) implements ASTNode {

    /**
     * Check if this filter has a comparison.
     */
    public boolean hasComparison() {
        return operator != null && value != null;
    }

    /**
     * Convert this filter expression to its JMESPath representation.
     */
    public String toJmesPath() {
        if (hasComparison()) {
            return path.toJmesPath() + " " + toJmesPathOperator(operator) + " " + value.toJmesPath();
        }
        return path.toJmesPath();
    }

    /**
     * Convert comparison operator to JMESPath syntax.
     */
    private static String toJmesPathOperator(ComparisonOperator op) {
        return switch (op) {
            case GREATER -> ">";
            case LESSER -> "<";
            case EQUALS -> "==";
            case LESSER_OR_EQUAL -> "<=";
            case GREATER_OR_EQUAL -> ">=";
        };
    }

    @Override
    public String toString() {
        if (hasComparison()) {
            return path + " " + operator + " " + value;
        }
        return path.toString();
    }
}
