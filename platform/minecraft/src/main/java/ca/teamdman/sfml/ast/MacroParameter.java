package ca.teamdman.sfml.ast;

import org.jetbrains.annotations.Nullable;

/**
 * Represents a parameter in a macro definition.
 * Example: machine: Smeltable
 */
public record MacroParameter(
        String name,
        @Nullable String protocolConstraint
) implements ASTNode {

    @Override
    public String toString() {
        if (protocolConstraint != null) {
            return name + ": " + protocolConstraint;
        }
        return name;
    }
}
