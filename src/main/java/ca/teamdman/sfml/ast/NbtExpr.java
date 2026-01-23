package ca.teamdman.sfml.ast;

import org.jetbrains.annotations.Nullable;

/**
 * AST node representing an NBT expression with optional comparison.
 * Examples: damage, damage > 10, productivebees:gene_group.purity == 100
 */
public record NbtExpr(
        NbtPath path,
        @Nullable ComparisonOperator operator,
        @Nullable NbtValue value
) implements ASTNode {

    /**
     * Create a path-only expression (existence check).
     */
    public static NbtExpr pathOnly(NbtPath path) {
        return new NbtExpr(path, null, null);
    }

    /**
     * Check if this expression has a comparison.
     */
    public boolean hasComparison() {
        return operator != null && value != null;
    }

    /**
     * Convert this expression to its JMESPath representation.
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
