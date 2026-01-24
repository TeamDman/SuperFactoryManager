package ca.teamdman.sfml.ast;

import java.util.List;
import java.util.Optional;

/**
 * Represents a protocol definition in the program.
 * Example:
 * protocol Smeltable
 *     input: sidequalifier slotqualifier
 *     fuel: sidequalifier slotqualifier
 *     output: sidequalifier slotqualifier
 * end
 */
public record ProtocolDefinition(
        String name,
        List<ProtocolField> fields
) implements ASTNode {

    /**
     * Gets a field by name from this protocol definition.
     */
    public Optional<ProtocolField> getField(String fieldName) {
        return fields.stream()
                .filter(f -> f.name().equals(fieldName))
                .findFirst();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("protocol ").append(name).append("\n");
        for (ProtocolField field : fields) {
            sb.append("    ").append(field).append("\n");
        }
        sb.append("end");
        return sb.toString();
    }
}
