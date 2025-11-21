package ca.teamdman.sfml.ast;

import static java.util.Objects.requireNonNull;

import com.github.bsideup.jabel.Desugar;

import ca.teamdman.sfm.common.program.ProgramContext;

@Desugar
public record Interval(
                       int ticks,
                       IntervalAlignment alignment,
                       int offset)
        implements ASTNode {

    public boolean shouldTick(ProgramContext context) {
        return switch (alignment) {
            case LOCAL -> context.getManager().getTick() % ticks == offset;
            case GLOBAL -> requireNonNull(context.getManager().getWorld()).getWorldTime() % ticks == offset;
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
