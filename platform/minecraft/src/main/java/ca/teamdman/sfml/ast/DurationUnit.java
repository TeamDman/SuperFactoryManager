package ca.teamdman.sfml.ast;

import java.util.List;

public enum DurationUnit implements SfmlAstNode {
    TICKS,
    SECONDS;

    public int toTicks(NumberExpression number) {

        return (int) switch (this) {
            case TICKS -> number.value();
            case SECONDS -> number.value() * 20;
        };
    }

    @Override
    public List<? extends SfmlAstNode> getChildNodes() {

        return List.of();
    }
}
