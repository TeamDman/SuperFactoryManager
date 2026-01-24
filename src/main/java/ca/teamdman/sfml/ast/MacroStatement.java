package ca.teamdman.sfml.ast;

/**
 * Marker interface for statements that can appear inside a macro body.
 * Sealed to MacroInputStatement, MacroOutputStatement, MacroIfStatement, MacroForgetStatement.
 */
public sealed interface MacroStatement extends ASTNode
        permits MacroInputStatement, MacroOutputStatement, MacroIfStatement, MacroForgetStatement {
}
