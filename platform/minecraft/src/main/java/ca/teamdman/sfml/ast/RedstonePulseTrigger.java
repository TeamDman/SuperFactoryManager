package ca.teamdman.sfml.ast;

import ca.teamdman.sfm.common.program.ProgramContext;
import ca.teamdman.sfm.common.program.SimulateExploreAllPathsProgramBehaviour;

import java.util.List;

public record RedstonePulseTrigger(
        Block block
) implements Trigger, ToStringCondensed {
    @Override
    public Block getBlock() {
        return block;
    }

    @Override
    public List<? extends SfmlAstNode> getChildNodes() {

        return List.of(block);
    }

    @Override
    public void tick(ProgramContext context) {

        for (int i = 0; i < context.unhandledRedstonePulseCount().intValue(); i++) {
            block.tick(context);
        }

        if (context.behaviour() instanceof  SimulateExploreAllPathsProgramBehaviour simulation) {
            simulation.onTriggerDropped(context, this);
        }
    }

    @Override
    public boolean shouldTick(ProgramContext context) {

        if (context.behaviour() instanceof SimulateExploreAllPathsProgramBehaviour) return true;

        return context.manager().getUnprocessedRedstonePulseCount() > 0;
    }

    @Override
    public String toString() {
        return "EVERY REDSTONE PULSE DO\n" + block.toString().indent(1).stripTrailing() + "\nEND";
    }

    @Override
    public String toStringCondensed() {
        return "EVERY REDSTONE PULSE DO";
    }
}
