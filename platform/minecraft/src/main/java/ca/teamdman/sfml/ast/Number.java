package ca.teamdman.sfml.ast;

import java.util.List;

public record Number(long value) implements SfmlAstNode {
    @Override
    public String toString() {
        return String.valueOf(value);
    }

    public Number add(Number number) {
        return new Number(value + number.value);
    }

    @Override
    public List<? extends SfmlAstNode> getChildNodes() {

        return List.of();
    }
}
