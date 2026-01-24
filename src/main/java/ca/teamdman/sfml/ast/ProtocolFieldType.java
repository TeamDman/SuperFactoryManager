package ca.teamdman.sfml.ast;

/**
 * Represents the type of a field in a protocol definition.
 */
public enum ProtocolFieldType implements ASTNode {
    SIDE_QUALIFIER,
    SLOT_QUALIFIER,
    SIDE_AND_SLOT,
    LABEL,
    RESOURCE,
    NUMBER;

    /**
     * Checks if a struct field value matches this protocol field type.
     */
    public boolean matches(StructFieldValue value) {
        return switch (this) {
            case SIDE_QUALIFIER -> value instanceof SideQualifier;
            case SLOT_QUALIFIER -> value instanceof NumberRangeSet;
            case SIDE_AND_SLOT -> value instanceof CompositeFieldValue;
            case LABEL -> value instanceof Label;
            case RESOURCE -> value instanceof ResourceIdSet;
            case NUMBER -> value instanceof Number;
        };
    }

    @Override
    public String toString() {
        return switch (this) {
            case SIDE_QUALIFIER -> "sidequalifier";
            case SLOT_QUALIFIER -> "slotqualifier";
            case SIDE_AND_SLOT -> "sidequalifier slotqualifier";
            case LABEL -> "label";
            case RESOURCE -> "resource";
            case NUMBER -> "number";
        };
    }
}
