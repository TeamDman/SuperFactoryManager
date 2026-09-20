package ca.teamdman.sfml.ast;

import ca.teamdman.sfm.common.program.ProgramContext;

import java.util.Objects;

public record Interval(
        int ticks,
        IntervalAlignment alignment,
        int offset,
        boolean legacyOffsetSyntax
) implements ASTNode {
    public Interval(int ticks, IntervalAlignment alignment, int offset) {
        this(ticks, alignment, offset, false);
    }

    public boolean shouldTick(ProgramContext context) {
        return switch (alignment) {
            case LOCAL -> context.getManager().getTick() % ticks == offset;
            case GLOBAL -> Objects.requireNonNull(context.getManager().getLevel()).getGameTime() % ticks == offset;
        };
    }

    @Override
    public String toString() {
        return ticks
               + (alignment == IntervalAlignment.GLOBAL ? " GLOBAL" : "")
               + (ticks == 1 ? " TICK" : " TICKS")
               + (offset == 0 ? "" : " OFFSET BY " + offset + (offset == 1 ? " TICK" : " TICKS"));
    }

    public enum IntervalAlignment {
        LOCAL,
        GLOBAL
    }
}
