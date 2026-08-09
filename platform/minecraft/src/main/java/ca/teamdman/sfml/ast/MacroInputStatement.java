package ca.teamdman.sfml.ast;

import org.jetbrains.annotations.Nullable;

/**
 * Represents an input statement within a macro body.
 * Example: {@code input 64 iron from source} or {@code input from each machine using input}
 *
 * @param labelAccess     The label access (parameter reference or struct field access)
 * @param resourceLimits  Optional resource limits (quantity, retention, resource patterns)
 * @param each            Whether to iterate over each matching inventory separately
 */
public record MacroInputStatement(
        MacroLabelAccess labelAccess,
        @Nullable ResourceLimits resourceLimits,
        boolean each
) implements MacroStatement {

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("input");
        if (resourceLimits != null) {
            sb.append(" ").append(resourceLimits);
        }
        sb.append(" from");
        if (each) {
            sb.append(" each");
        }
        sb.append(" ").append(labelAccess);
        return sb.toString();
    }
}
