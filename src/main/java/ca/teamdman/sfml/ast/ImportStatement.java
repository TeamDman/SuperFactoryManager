package ca.teamdman.sfml.ast;

/**
 * Represents an import statement for file imports.
 * Example: import "machines.sfml"
 */
public record ImportStatement(
        String path
) implements ASTNode {

    @Override
    public String toString() {
        return "import \"" + path + "\"";
    }
}
