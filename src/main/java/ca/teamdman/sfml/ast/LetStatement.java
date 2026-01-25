package ca.teamdman.sfml.ast;

/**
 * Represents a let statement that binds a struct instance to a variable name.
 * The variable name automatically becomes the label for the struct instance.
 * Example: let furnaces = Furnace
 * Example with overrides: let furnaces = Furnace WITH input: NORTH SIDE
 */
public record LetStatement(
        String variableName,
        StructInstance instance
) implements ASTNode {
    @Override
    public String toString() {
        return "let " + variableName + " = " + instance;
    }
}
