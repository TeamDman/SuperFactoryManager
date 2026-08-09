package ca.teamdman.sfml.ast;

import java.util.List;

/**
 * AST node representing a path within an NBT filter expression.
 * Examples: @.id, id, @.level, name.first
 */
public record NbtFilterPath(
        boolean hasAt,
        List<String> identifiers
) implements ASTNode {

    /**
     * Convert this filter path to its JMESPath representation.
     */
    public String toJmesPath() {
        StringBuilder sb = new StringBuilder();
        if (hasAt) {
            sb.append("@");
        }
        for (int i = 0; i < identifiers.size(); i++) {
            String id = identifiers.get(i);
            if (i > 0 || hasAt) {
                sb.append(".");
            }
            // Quote if necessary
            if (needsQuoting(id)) {
                sb.append("\"").append(id).append("\"");
            } else {
                sb.append(id);
            }
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
        if (hasAt) {
            sb.append("@");
        }
        for (int i = 0; i < identifiers.size(); i++) {
            if (i > 0 || hasAt) {
                sb.append(".");
            }
            sb.append(identifiers.get(i));
        }
        return sb.toString();
    }
}
