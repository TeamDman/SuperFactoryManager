package ca.teamdman.sfml.ast;

import java.util.Map;
import java.util.Optional;

/**
 * Represents an instantiation of a struct with optional field overrides.
 * The variable name from the let statement becomes the label automatically.
 * Example: Furnace
 * Example with overrides: Furnace WITH input: NORTH SIDE
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
        sb.append(definition.name());
        // Only show non-label overrides (label comes from variable name)
        boolean hasOverrides = overrides.entrySet().stream()
                .anyMatch(e -> !e.getKey().equals("label"));
        if (hasOverrides) {
            sb.append(" WITH ");
            boolean first = true;
            for (Map.Entry<String, StructFieldValue> entry : overrides.entrySet()) {
                if (entry.getKey().equals("label")) continue;
                if (!first) sb.append(", ");
                sb.append(entry.getKey()).append(": ").append(entry.getValue());
                first = false;
            }
        }
        return sb.toString();
    }
}
