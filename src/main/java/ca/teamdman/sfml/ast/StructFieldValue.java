package ca.teamdman.sfml.ast;

/**
 * Marker interface for values that can appear in struct field definitions.
 * Used for type safety in struct field value assignments.
 */
public sealed interface StructFieldValue extends ASTNode
        permits SideQualifier, NumberRangeSet, ResourceIdSet, Label, Number, CompositeFieldValue {
}
