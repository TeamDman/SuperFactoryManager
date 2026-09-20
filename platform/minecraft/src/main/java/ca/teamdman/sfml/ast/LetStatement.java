package ca.teamdman.sfml.ast;

import ca.teamdman.sfm.common.program.ProgramContext;

import java.util.Objects;

public record LetStatement(
        String variableName,
        ProgramValueExpression expression
) implements Statement {
    public LetStatement {
        Objects.requireNonNull(variableName);
        Objects.requireNonNull(expression);
    }

    @Override
    public void tick(ProgramContext context) {
        context.getVariableEnvironment().setRelation(variableName, expression.evaluate(context));
    }

    @Override
    public String toString() {
        return "LET " + variableName + " BE " + expression;
    }
}
