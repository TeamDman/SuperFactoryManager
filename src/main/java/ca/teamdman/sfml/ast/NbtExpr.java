package ca.teamdman.sfml.ast;

import org.jetbrains.annotations.Nullable;

/**
 * AST node representing an NBT expression with optional comparison or IN check.
 * Examples: damage, damage > 10, productivebees:gene_group.purity == 100,
 *           potion_contents.potion IN ["minecraft:water", "minecraft:mundane"]
 */
public record NbtExpr(
        NbtPath path,
        @Nullable ComparisonOperator operator,
        @Nullable NbtValue value,
        @Nullable NbtArrayLiteral array
) implements ASTNode {

    /**
     * Create a path-only expression (existence check).
     */
    public static NbtExpr pathOnly(NbtPath path) {
        return new NbtExpr(path, null, null, null);
    }

    /**
     * Create a comparison expression.
     */
    public static NbtExpr comparison(NbtPath path, ComparisonOperator operator, NbtValue value) {
        return new NbtExpr(path, operator, value, null);
    }

    /**
     * Create an IN expression.
     */
    public static NbtExpr inArray(NbtPath path, NbtArrayLiteral array) {
        return new NbtExpr(path, null, null, array);
    }

    /**
     * Check if this expression has a comparison.
     */
    public boolean hasComparison() {
        return operator != null && value != null;
    }

    /**
     * Check if this expression is an IN check.
     */
    public boolean isInExpression() {
        return array != null;
    }

    /**
     * Convert this expression to its JMESPath representation.
     */
    public String toJmesPath() {
        if (isInExpression()) {
            // IN expression converts to: contains(array, path)
            return "contains(" + array.toJmesPath() + ", " + path.toJmesPath() + ")";
        }
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
        if (isInExpression()) {
            return path + " IN " + array;
        }
        if (hasComparison()) {
            return path + " " + operator + " " + value;
        }
        return path.toString();
    }
}
