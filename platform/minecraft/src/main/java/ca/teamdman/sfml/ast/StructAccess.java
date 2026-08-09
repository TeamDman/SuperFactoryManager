package ca.teamdman.sfml.ast;

/**
 * Represents access to a struct field using the USING keyword.
 * Example: smelter using input
 *
 * This is used to track the original source of a struct field access
 * before it is resolved into a concrete LabelAccess.
 */
public record StructAccess(
        String variableName,
        String fieldName
) implements ASTNode {
    @Override
    public String toString() {
        return variableName + " using " + fieldName;
    }
}
