package ca.teamdman.sfml.ast;

import ca.teamdman.sfm.common.program.ProgramContext;

public record RedstoneTrigger(
        Block block
) implements Trigger, ShortStatement {
    @Override
    public Block getBlock() {
        return block;
    }

    @Override
    public void tick(ProgramContext context) {
        for (int i = 0; i < context.getRedstonePulses(); i++) {
            block.tick(context);
        }
    }

    @Override
    public boolean shouldTick(ProgramContext context) {
        if (context.getExecutionPolicy() == ProgramContext.ExecutionPolicy.EXPLORE_BRANCHES) return true;
        return context.getManager().getUnprocessedRedstonePulseCount() > 0;
    }

    @Override
    public String toString() {
        return "EVERY REDSTONE PULSE DO\n" + block.toString().indent(1).stripTrailing() + "\nEND";
    }

    @Override
    public String toStringShort() {
        return "EVERY REDSTONE PULSE DO";
    }
}
