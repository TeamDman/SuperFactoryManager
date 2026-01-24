package ca.teamdman.sfml.ast;

import java.util.Map;
import java.util.Optional;

/**
 * Represents an instantiation of a struct with overrides.
 * Example: Furnace { label: "my furnaces" }
 */
public record StructInstance(
        String variableName,
        StructDefinition definition,
        Map<String, StructFieldValue> overrides
) implements ASTNode {

    /**
     * Resolves a field value, checking overrides first, then the definition.
     */
    public Optional<StructFieldValue> resolveField(String fieldName) {
        // Check overrides first
        if (overrides.containsKey(fieldName)) {
            return Optional.of(overrides.get(fieldName));
        }
        // Fall back to definition
        return definition.getField(fieldName).map(StructField::value);
    }

    /**
     * Gets the label for this struct instance.
     * The label must be provided as an override since it binds to actual blocks.
     */
    public Optional<Label> getLabel() {
        StructFieldValue labelValue = overrides.get("label");
        if (labelValue instanceof Label label) {
            return Optional.of(label);
        }
        return Optional.empty();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(definition.name()).append(" { ");
        boolean first = true;
        for (Map.Entry<String, StructFieldValue> entry : overrides.entrySet()) {
            if (!first) sb.append(", ");
            sb.append(entry.getKey()).append(": ").append(entry.getValue());
            first = false;
        }
        sb.append(" }");
        return sb.toString();
    }
}
