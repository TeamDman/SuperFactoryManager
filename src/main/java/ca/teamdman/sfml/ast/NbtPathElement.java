package ca.teamdman.sfml.ast;

import org.jetbrains.annotations.Nullable;

/**
 * AST node representing an element in an NBT path after the initial component.
 * Examples: .field, .field[0], [0]
 */
public record NbtPathElement(
        @Nullable String field,
        @Nullable ArrayIndex arrayIndex
) implements ASTNode {

    /**
     * Create a field-only element.
     */
    public static NbtPathElement field(String field) {
        return new NbtPathElement(field, null);
    }

    /**
     * Create a field with array index element.
     */
    public static NbtPathElement fieldWithIndex(String field, ArrayIndex index) {
        return new NbtPathElement(field, index);
    }

    /**
     * Create an array-only element.
     */
    public static NbtPathElement arrayOnly(ArrayIndex index) {
        return new NbtPathElement(null, index);
    }

    /**
     * Check if this element has a field.
     */
    public boolean hasField() {
        return field != null;
    }

    /**
     * Check if this element has an array index.
     */
    public boolean hasArrayIndex() {
        return arrayIndex != null;
    }

    /**
     * Convert this path element to its JMESPath representation.
     */
    public String toJmesPath() {
        StringBuilder sb = new StringBuilder();
        if (hasField()) {
            if (needsQuoting(field)) {
                sb.append("\"").append(field).append("\"");
            } else {
                sb.append(field);
            }
        }
        if (hasArrayIndex()) {
            sb.append("[").append(arrayIndex.toJmesPath()).append("]");
        }
        return sb.toString();
    }

    /**
     * Check if an identifier needs quoting in JMESPath.
     */
    private static boolean needsQuoting(String identifier) {
        if (identifier.isEmpty()) return true;
        char first = identifier.charAt(0);
        if (!Character.isLetter(first) && first != '_') return true;
        for (int i = 1; i < identifier.length(); i++) {
            char c = identifier.charAt(i);
            if (!Character.isLetterOrDigit(c) && c != '_') return true;
        }
        return false;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        if (hasField()) {
            sb.append(field);
        }
        if (hasArrayIndex()) {
            sb.append("[").append(arrayIndex).append("]");
        }
        return sb.toString();
    }
}
