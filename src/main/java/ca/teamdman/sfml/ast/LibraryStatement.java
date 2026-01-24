package ca.teamdman.sfml.ast;

/**
 * Represents a library usage statement for referencing library blocks.
 * Example: use library "factory_config"
 */
public record LibraryStatement(
        String blockLabel
) implements ASTNode {

    @Override
    public String toString() {
        return "use library \"" + blockLabel + "\"";
    }
}
