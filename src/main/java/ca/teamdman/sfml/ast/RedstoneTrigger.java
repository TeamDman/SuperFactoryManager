package ca.teamdman.sfml.ast;

import ca.teamdman.sfm.common.program.ProgramContext;
import ca.teamdman.sfm.common.program.SimulateExploreAllPathsProgramBehaviour;
import ca.teamdman.sfm.common.util.StringUtil;
import com.github.bsideup.jabel.Desugar;

@Desugar public record RedstoneTrigger(
        Block block
) implements Trigger, ToStringCondensed {
    @Override
    public Block getBlock() {
        return block;
    }

    @Override
    public void tick(ProgramContext context) {
        for (int i = 0; i < context.getRedstonePulses(); i++) {
            block.tick(context);
        }
        if (context.getBehaviour() instanceof  SimulateExploreAllPathsProgramBehaviour simulation) {
            simulation.onTriggerDropped(context, this);
        }
    }

    @Override
    public boolean shouldTick(ProgramContext context) {
        if (context.getBehaviour() instanceof SimulateExploreAllPathsProgramBehaviour) return true;
        return context.getManager().getUnprocessedRedstonePulseCount() > 0;
    }

    @Override
    public String toString() {
        return "EVERY REDSTONE PULSE DO\n" + StringUtil.indentPonyfill(block.toString(), 1) + "END";
    }

    @Override
    public String toStringCondensed() {
        return "EVERY REDSTONE PULSE DO";
    }
}
