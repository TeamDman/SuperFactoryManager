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
            // Check for wildcard pattern in string equality comparisons
            if (operator == ComparisonOperator.EQUALS && value instanceof NbtValue.NbtString strVal) {
                String pattern = strVal.value();
                if (pattern.contains("*")) {
                    return wildcardToJmesPath(path.toJmesPath(), pattern);
                }
            }
            return path.toJmesPath() + " " + toJmesPathOperator(operator) + " " + value.toJmesPath();
        }
        return path.toJmesPath();
    }

    /**
     * Convert a wildcard pattern to JMESPath function call.
     * - "prefix*" -> starts_with(path, 'prefix')
     * - "*suffix" -> ends_with(path, 'suffix')
     * - "*contains*" -> contains(path, 'contains')
     * - "*" -> path (existence check)
     */
    private String wildcardToJmesPath(String pathExpr, String pattern) {
        boolean startsWithStar = pattern.startsWith("*");
        boolean endsWithStar = pattern.endsWith("*");
        String content = pattern.replace("*", "");
        String escaped = content.replace("'", "\\'");

        if (content.isEmpty()) {
            // Just "*" means match anything (existence check)
            return pathExpr;
        }

        if (startsWithStar && endsWithStar) {
            // *contains* -> contains(path, 'contains')
            return "contains(" + pathExpr + ", '" + escaped + "')";
        } else if (startsWithStar) {
            // *suffix -> ends_with(path, 'suffix')
            return "ends_with(" + pathExpr + ", '" + escaped + "')";
        } else if (endsWithStar) {
            // prefix* -> starts_with(path, 'prefix')
            return "starts_with(" + pathExpr + ", '" + escaped + "')";
        } else {
            // No wildcards at edges but contains * in middle - treat as contains
            return "contains(" + pathExpr + ", '" + escaped + "')";
        }
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
