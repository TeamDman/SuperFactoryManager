package ca.teamdman.sfml.ast;

/**
 * Marker interface for number expression AST nodes.
 * These nodes represent the structure of a numeric expression, allowing for pretty printing
 * while still supporting eager evaluation for constant expressions.
 */
public interface INumberExpression extends SfmlAstNode, ToStringPretty {
}
