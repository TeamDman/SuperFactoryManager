package ca.teamdman.sfml.ast;

import ca.teamdman.sfm.common.program.ProgramContext;

import java.util.List;

public record TimerTrigger(
        Interval interval,
        Block block
) implements Trigger {
    @Override
    public Block getBlock() {
        return block;
    }

    @Override
    public boolean shouldTick(ProgramContext context) {
        if (context.getExecutionPolicy() == ProgramContext.ExecutionPolicy.EXPLORE_BRANCHES) return true;
        return context.getManager().getTick() % interval.getTicks() == 0;
    }

    @Override
    public void tick(ProgramContext context) {
        block.tick(context);
    }

    @Override
    public List<Statement> getStatements() {
        return List.of(block);
    }

    public boolean usesOnlyForgeEnergyResourceIO() {
        return getReferencedIOResourceIds().allMatch(id -> id.resourceTypeNamespace.equals("sfm")
                                                           && id.resourceTypeName.equals("forge_energy"));
    }

    @Override
    public String toString() {
        return "EVERY " + interval + " DO\n" + block.toString().indent(1).stripTrailing() + "\nEND";
    }
}
