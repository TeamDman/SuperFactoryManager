package ca.teamdman.sfml.ast;

import java.util.Arrays;
import java.util.List;

import ca.teamdman.sfm.common.registry.SFMResourceTypes;
import com.github.bsideup.jabel.Desugar;

import ca.teamdman.sfm.common.program.ProgramContext;
import ca.teamdman.sfm.common.program.SimulateExploreAllPathsProgramBehaviour;
import ca.teamdman.sfm.common.util.StringUtil;


@Desugar
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
        return Arrays.asList(block);
    }

    public boolean usesOnlyForgeEnergyResourceIO() {
        return getReferencedIOResourceIds().allMatch(id -> id.getResourceType() == SFMResourceTypes.FORGE_ENERGY);
    }

    @Override
    public String toString() {
        return "EVERY " + interval + " DO\n" + StringUtil.indentPonyfill(block.toString(), 1).trim() + "\nEND";
    }

    @Override
    public String toStringCondensed() {
        return "EVERY " + interval + " DO";
    }
}
