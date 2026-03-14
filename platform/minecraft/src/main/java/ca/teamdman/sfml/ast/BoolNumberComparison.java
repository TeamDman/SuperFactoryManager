package ca.teamdman.sfml.ast;

import ca.teamdman.sfm.common.program.ProgramContext;

import java.util.List;

public record BoolNumberComparison(NumberExpression left, ComparisonOperator op, NumberExpression right) implements BoolExpr {
    @Override
    public boolean test(ProgramContext programContext) {
        long leftValue = left.value();
        long rightValue = right.value();

        return op.test(leftValue, rightValue);
    }

    @Override
    public String toString() {
        return left + " " + op + " " + right;
    }

    @Override
    public List<? extends SfmlAstNode> getChildNodes() {
        return List.of(left, right);
    }
}
