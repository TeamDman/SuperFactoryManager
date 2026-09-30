package ca.teamdman.sfml.ast;

import ca.teamdman.sfm.common.program.ProgramContext;
import ca.teamdman.sfm.common.program.SimulateExploreAllPathsProgramBehaviour;

import java.util.List;

public record TimerTrigger(
        Interval interval,
        Block block
) implements Trigger, ToStringCondensed {
    @Override
    public Block getBlock() {
        return block;
    }

    @Override
    public boolean shouldTick(ProgramContext context) {
        if (context.getBehaviour() instanceof SimulateExploreAllPathsProgramBehaviour) return true;
        return interval.shouldTick(context);
    }

    @Override
    public void tick(ProgramContext context) {
        block.tick(context);
        if (context.getBehaviour() instanceof SimulateExploreAllPathsProgramBehaviour simulation) {
            simulation.onTriggerDropped(context, this);
        }
    }

    @Override
    public List<Statement> getStatements() {
        return List.of(block);
    }

    public boolean usesOnlyForgeEnergyResourceIO() {
        return getReferencedIOResourceIds().allMatch(id -> id.resourceTypeNamespace.equals("sfm")
                                                           && (
                                                                   id.resourceTypeName.equals("forge_energy")
                                                                   || id.resourceTypeName.equals("mekanism_energy")
                                                           ));
    }

    @Override
    public String toString() {
        return "EVERY " + interval + " DO\n" + block.toString().indent(1).stripTrailing() + "\nEND";
    }

    @Override
    public String toStringCondensed() {
        return "EVERY " + interval + " DO";
    }
}
