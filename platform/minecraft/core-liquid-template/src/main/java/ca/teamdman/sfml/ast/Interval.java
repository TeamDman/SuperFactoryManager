package ca.teamdman.sfml.ast;

import ca.teamdman.sfm.common.program.ProgramContext;

import java.util.Objects;

public record Interval(
        int ticks,
        IntervalAlignment alignment,
{% if features.sfml_worded_intervals %}
        int offset,
        boolean legacyOffsetSyntax
{% else %}
        int offset
{% endif %}
) implements ASTNode {
{% if features.sfml_worded_intervals %}
    public Interval(int ticks, IntervalAlignment alignment, int offset) {
        this(ticks, alignment, offset, false);
    }

{% endif %}
    public boolean shouldTick(ProgramContext context) {
        return switch (alignment) {
            case LOCAL -> context.getManager().getTick() % ticks == offset;
            case GLOBAL -> Objects.requireNonNull(context.getManager().getLevel()).getGameTime() % ticks == offset;
        };
    }

    @Override
    public String toString() {
{% if features.sfml_worded_intervals %}
        return ticks
               + (alignment == IntervalAlignment.GLOBAL ? " GLOBAL" : "")
               + (ticks == 1 ? " TICK" : " TICKS")
               + (offset == 0 ? "" : " OFFSET BY " + offset + (offset == 1 ? " TICK" : " TICKS"));
{% else %}
        return ticks + " TICKS";
{% endif %}
    }

    public enum IntervalAlignment {
        LOCAL,
        GLOBAL
    }
}
