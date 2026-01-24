package ca.teamdman.sfml.ast;

import org.jetbrains.annotations.Nullable;

/**
 * Represents an output statement within a macro body.
 * Example: {@code output 64 iron to dest} or {@code output retain 2 to each machine using output}
 *
 * @param labelAccess     The label access (parameter reference or struct field access)
 * @param resourceLimits  Optional resource limits (quantity, retention, resource patterns)
 * @param each            Whether to iterate over each matching inventory separately
 */
public record MacroOutputStatement(
        MacroLabelAccess labelAccess,
        @Nullable ResourceLimits resourceLimits,
        boolean each
) implements MacroStatement {

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("output");
        if (resourceLimits != null) {
            sb.append(" ").append(resourceLimits);
        }
        sb.append(" to");
        if (each) {
            sb.append(" each");
        }
        sb.append(" ").append(labelAccess);
        return sb.toString();
    }
}
