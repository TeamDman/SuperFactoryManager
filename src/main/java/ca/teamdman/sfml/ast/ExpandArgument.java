package ca.teamdman.sfml.ast;

/**
 * Represents an argument in an expand statement.
 * Can be either an identifier (struct variable or label) or a string literal.
 */
public record ExpandArgument(
        String value,
        boolean isStringLiteral
) implements ASTNode {

    /**
     * Creates an identifier argument.
     */
    public static ExpandArgument identifier(String value) {
        return new ExpandArgument(value, false);
    }

    /**
     * Creates a string literal argument.
     */
    public static ExpandArgument stringLiteral(String value) {
        return new ExpandArgument(value, true);
    }

    @Override
    public String toString() {
        if (isStringLiteral) {
            return "\"" + value + "\"";
        }
        return value;
    }
}
