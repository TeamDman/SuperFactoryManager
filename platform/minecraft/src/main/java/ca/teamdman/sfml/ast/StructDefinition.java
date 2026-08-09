package ca.teamdman.sfml.ast;

import java.util.List;
import java.util.Optional;

/**
 * Represents a struct definition in the program.
 * Example:
 * struct Furnace : Smeltable
 *     input: TOP SIDE SLOTS 0
 *     fuel: BOTTOM SIDE SLOTS 1
 *     output: BOTTOM SIDE SLOTS 2
 * end
 */
public record StructDefinition(
        String name,
        List<String> implementedProtocols,
        List<StructField> fields
) implements ASTNode {

    /**
     * Constructs a StructDefinition without protocols for backwards compatibility.
     */
    public StructDefinition(String name, List<StructField> fields) {
        this(name, List.of(), fields);
    }

    /**
     * Gets a field by name from this struct definition.
     */
    public Optional<StructField> getField(String fieldName) {
        return fields.stream()
                .filter(f -> f.name().equals(fieldName))
                .findFirst();
    }

    /**
     * Checks if this struct implements the given protocol.
     */
    public boolean implementsProtocol(String protocolName) {
        return implementedProtocols.contains(protocolName);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("struct ").append(name);
        if (!implementedProtocols.isEmpty()) {
            sb.append(" : ").append(String.join(", ", implementedProtocols));
        }
        sb.append("\n");
        for (StructField field : fields) {
            sb.append("    ").append(field).append("\n");
        }
        sb.append("end");
        return sb.toString();
    }
}
