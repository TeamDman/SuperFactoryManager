package ca.teamdman.sfml.ast;

import ca.teamdman.sfm.common.program.ProgramContext;

import java.util.Objects;

public record Interval(
        int ticks,
        IntervalAlignment alignment,
        int offset
) implements ASTNode {
    public boolean shouldTick(ProgramContext context) {
        return switch (alignment) {
            case LOCAL -> context.getManager().getTick() % ticks == offset;
            case GLOBAL -> Objects.requireNonNull(context.getManager().getLevel()).getGameTime() % ticks == offset;
        };
    }

    @Override
    public String toString() {
        return ticks + " TICKS";
    }

    public enum IntervalAlignment {
        LOCAL,
        GLOBAL
    }
}
