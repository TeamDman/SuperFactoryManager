package ca.teamdman.sfml.ast;

/**
 * Represents a field within a struct definition.
 * Example: input: TOP SIDE SLOTS 0
 */
public record StructField(
        String name,
        StructFieldValue value
) implements ASTNode {
    @Override
    public String toString() {
        return name + ": " + value;
    }
}
