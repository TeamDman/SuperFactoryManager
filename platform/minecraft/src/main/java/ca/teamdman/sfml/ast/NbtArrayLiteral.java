package ca.teamdman.sfml.ast;

import java.util.List;
import java.util.stream.Collectors;

/**
 * AST node representing an array literal in NBT expressions.
 * Used in IN expressions like: potion_contents.potion IN ["minecraft:water", "minecraft:mundane"]
 */
public record NbtArrayLiteral(List<NbtValue> values) implements ASTNode {

    /**
     * Convert this array to its JMESPath representation.
     * JMESPath array literals: ['value1', 'value2'] for contains() function
     */
    public String toJmesPath() {
        String inner = values.stream()
                .map(this::valueToJmesPathArrayElement)
                .collect(Collectors.joining(", "));
        return "[" + inner + "]";
    }

    /**
     * Convert a value to JMESPath array element format.
     * Strings use single quotes, numbers and booleans use backticks.
     */
    private String valueToJmesPathArrayElement(NbtValue value) {
        if (value instanceof NbtValue.NbtString s) {
            // Strings in array use single quotes directly (not backtick-wrapped)
            return "'" + s.value().replace("'", "\\'") + "'";
        }
        // Numbers and booleans use the standard format
        return value.toJmesPath();
    }

    @Override
    public String toString() {
        String inner = values.stream()
                .map(NbtValue::toString)
                .collect(Collectors.joining(", "));
        return "[" + inner + "]";
    }
}
