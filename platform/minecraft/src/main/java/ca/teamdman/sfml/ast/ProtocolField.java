package ca.teamdman.sfml.ast;

/**
 * Represents a field in a protocol definition.
 * Example: input: sidequalifier slotqualifier
 */
public record ProtocolField(
        String name,
        ProtocolFieldType type
) implements ASTNode {

    @Override
    public String toString() {
        return name + ": " + type;
    }
}
