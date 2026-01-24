package ca.teamdman.sfml.ast;

import java.util.List;
import java.util.Optional;

/**
 * Represents a struct definition in the program.
 * Example:
 * struct Furnace
 *     input: TOP SIDE SLOTS 0
 *     fuel: BOTTOM SIDE SLOTS 1
 *     output: BOTTOM SIDE SLOTS 2
 * end
 */
public record StructDefinition(
        String name,
        List<StructField> fields
) implements ASTNode {

    /**
     * Gets a field by name from this struct definition.
     */
    public Optional<StructField> getField(String fieldName) {
        return fields.stream()
                .filter(f -> f.name().equals(fieldName))
                .findFirst();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("struct ").append(name).append("\n");
        for (StructField field : fields) {
            sb.append("    ").append(field).append("\n");
        }
        sb.append("end");
        return sb.toString();
    }
}
