package ca.teamdman.sfml.ast;

/**
 * Represents a let statement that binds a struct instance to a variable name.
 * Example: let smelter = Furnace { label: "my furnaces" }
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
