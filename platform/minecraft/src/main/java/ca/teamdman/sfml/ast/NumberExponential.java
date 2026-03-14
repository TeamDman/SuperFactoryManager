package ca.teamdman.sfml.ast;

import java.util.List;

public record NumberExponential(
        NumberExpression left,
        NumberExpression right
) implements INumberExpression {
    @Override
    public String toString() {
        return left + " ^ " + right;
    }

    @Override
    public List<? extends SfmlAstNode> getChildNodes() {
        return List.of(left, right);
    }
}
