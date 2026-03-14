package ca.teamdman.sfml.ast;

import java.util.List;

public record NumberLiteral(long value) implements INumberExpression {
    @Override
    public String toString() {
        return String.valueOf(value);
    }

    @Override
    public List<? extends SfmlAstNode> getChildNodes() {
        return List.of();
    }
}
