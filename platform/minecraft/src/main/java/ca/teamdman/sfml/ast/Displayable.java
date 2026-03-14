package ca.teamdman.sfml.ast;

/// An expression that resolves to a string.
/// Used in {@link LogExpression}.
public interface Displayable extends SfmlAstNode {
    String display();
}
