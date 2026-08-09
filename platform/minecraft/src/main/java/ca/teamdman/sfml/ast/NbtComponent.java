package ca.teamdman.sfml.ast;

import org.jetbrains.annotations.Nullable;

/**
 * AST node representing a component in an NBT path.
 * Can be either a simple identifier (e.g., "damage") or a namespaced component (e.g., "minecraft:custom_data").
 *
 * In JMESPath, identifiers containing special characters like colons must be quoted.
 */
public record NbtComponent(
        String name,
        @Nullable String namespace
) implements ASTNode {

    /**
     * Creates a simple component without namespace.
     */
    public static NbtComponent simple(String name) {
        return new NbtComponent(name, null);
    }

    /**
     * Creates a namespaced component.
     */
    public static NbtComponent namespaced(String namespace, String name) {
        return new NbtComponent(name, namespace);
    }

    /**
     * Check if this is a namespaced component.
     */
    public boolean hasNamespace() {
        return namespace != null;
    }

    /**
     * Get the full identifier including namespace if present.
     */
    public String getFullName() {
        return hasNamespace() ? namespace + ":" + name : name;
    }

    /**
     * Convert this component to its JMESPath representation.
     * Namespaced components need to be quoted in JMESPath.
     */
    public String toJmesPath() {
        if (hasNamespace()) {
            // Quote the full name for JMESPath
            return "\"" + namespace + ":" + name + "\"";
        }
        // Check if the name needs quoting (contains special characters)
        if (needsQuoting(name)) {
            return "\"" + name + "\"";
        }
        return name;
    }

    /**
     * Check if an identifier needs quoting in JMESPath.
     */
    private static boolean needsQuoting(String identifier) {
        if (identifier.isEmpty()) return true;
        // JMESPath identifiers must start with a letter or underscore
        // and contain only letters, digits, and underscores
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
        return getFullName();
    }
}
