package ca.teamdman.sfml.ast;

import java.util.List;

public record NumberParen(
        NumberExpression inner
) implements INumberExpression {
    @Override
    public String toString() {
        return "(" + inner + ")";
    }

    @Override
    public List<? extends SfmlAstNode> getChildNodes() {
        return List.of(inner);
    }
}
