package ca.teamdman.sfml.ast;

/**
 * Represents a forget statement within a macro body.
 */
public record MacroForgetStatement() implements MacroStatement {

    @Override
    public String toString() {
        return "forget";
    }
}
