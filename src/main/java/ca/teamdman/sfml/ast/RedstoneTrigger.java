package ca.teamdman.sfml.ast;

import ca.teamdman.sfm.common.program.ProgramContext;

public record RedstoneTrigger(
        Block block
) implements Trigger {

    @Override
    public void tick(ProgramContext context) {
        context.getManager().trackRedstonePulseProcessed();
        block.tick(context);
    }

    @Override
    public boolean shouldTick(ProgramContext manager) {
        return manager.getManager().getUnprocessedRedstonePulseCount() > 0;
    }
}
