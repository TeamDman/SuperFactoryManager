package ca.teamdman.sfml.ast;

import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import ca.teamdman.sfm.common.program.ProgramContext;

import java.util.List;
import java.util.Objects;

public record Interval(
        NumberExpression interval,

        DurationUnit intervalUnit,

        IntervalAlignment alignment,

        NumberExpression offset,

        DurationUnit offsetUnit
) implements SfmlAstNode {
    public boolean shouldTick(ProgramContext context) {

        final ManagerBlockEntity manager = context.manager();
        if (manager == null) return false;
        return switch (alignment) {
            case LOCAL -> manager.getTick() % intervalTicks() == offsetTicks();
            case GLOBAL -> Objects.requireNonNull(manager.getLevel()).getGameTime() % intervalTicks() == offsetTicks();
        };
    }

    public int intervalTicks() {

        return intervalUnit.toTicks(interval);
    }

    public int offsetTicks() {

        return offsetUnit.toTicks(offset);
    }

    @Override
    public String toString() {

        StringBuilder sb = new StringBuilder();
        sb.append(interval);
        if (alignment == IntervalAlignment.GLOBAL) {
            sb.append(" GLOBAL");
        }
        sb.append(" ").append(intervalUnit);
        if (offset.value() != 0) {
            sb.append(" OFFSET BY ").append(offset);
            if (offsetUnit != intervalUnit) {
                sb.append(" ").append(offsetUnit);
            }
        }
        return sb.toString();
    }

    @Override
    public List<? extends SfmlAstNode> getChildNodes() {

        return List.of(interval, intervalUnit, alignment, offset, offsetUnit);
    }

    public enum IntervalAlignment implements SfmlAstNode {
        LOCAL,
        GLOBAL;

        @Override
        public List<? extends SfmlAstNode> getChildNodes() {

            return List.of();
        }
    }

}
