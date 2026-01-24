package ca.teamdman.sfml.ast;

import org.jetbrains.annotations.Nullable;

/**
 * Represents a label access in a macro.
 * Can be either:
 * - A parameter reference (e.g., "source")
 * - A struct field access (e.g., "machine using input")
 */
public record MacroLabelAccess(
        String parameterOrVariable,
        @Nullable String fieldName,
        @Nullable SideQualifier sideOverride,
        @Nullable NumberRangeSet slotOverride
) implements ASTNode {

    /**
     * Creates a simple parameter reference.
     */
    public static MacroLabelAccess parameter(String parameterName) {
        return new MacroLabelAccess(parameterName, null, null, null);
    }

    /**
     * Creates a struct field access.
     */
    public static MacroLabelAccess structAccess(
            String parameterName,
            String fieldName,
            @Nullable SideQualifier sideOverride,
            @Nullable NumberRangeSet slotOverride
    ) {
        return new MacroLabelAccess(parameterName, fieldName, sideOverride, slotOverride);
    }

    /**
     * Returns true if this is a struct field access.
     */
    public boolean isStructAccess() {
        return fieldName != null;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(parameterOrVariable);
        if (fieldName != null) {
            sb.append(" using ").append(fieldName);
        }
        if (sideOverride != null) {
            sb.append(" ").append(sideOverride);
        }
        if (slotOverride != null && !slotOverride.equals(NumberRangeSet.MAX_RANGE)) {
            sb.append(" ").append(slotOverride);
        }
        return sb.toString();
    }
}
