package ca.teamdman.sfml.ast;

import ca.teamdman.sfm.common.program.ProgramContext;

public record RedstoneTrigger(
        Block block
) implements Trigger {

    @Override
    public void tick(ProgramContext context) {
        for (int i = 0; i < context.getRedstonePulses(); i++) {
            block.tick(context);
        }
    }

    @Override
    public boolean shouldTick(ProgramContext manager) {
        return manager.getManager().getUnprocessedRedstonePulseCount() > 0;
    }
}
