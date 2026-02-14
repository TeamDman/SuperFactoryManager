package ca.teamdman.sfm.common.program.linting;

import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import ca.teamdman.sfm.common.label.LabelPositionHolder;
import ca.teamdman.sfml.ast.IOStatement;
import ca.teamdman.sfml.ast.Program;
import ca.teamdman.sfml.ast.RoundRobin;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraftforge.registries.IForgeRegistryEntry;
import org.jetbrains.annotations.Nullable;

import static ca.teamdman.sfm.common.localization.LocalizationKeys.PROGRAM_WARNING_ROUND_ROBIN_SMELLY_COUNT;
import static ca.teamdman.sfm.common.localization.LocalizationKeys.PROGRAM_WARNING_ROUND_ROBIN_SMELLY_EACH;
import static ca.teamdman.sfml.ast.RoundRobin.Behaviour.BY_BLOCK;
import static ca.teamdman.sfml.ast.RoundRobin.Behaviour.BY_LABEL;

public class RoundRobinProgramLinter extends IForgeRegistryEntry.Impl<IProgramLinter> implements IProgramLinter{
    // check "each" usage and round-robin usage in IO statements
    @Override
    public void gatherWarnings(
            Program program,
            LabelPositionHolder labelPositionHolder,
            @Nullable ManagerBlockEntity managerBlockEntity,
            ProblemTracker tracker
    ) {
        program.getDescendantStatements()
                .filter(IOStatement.class::isInstance)
                .map(IOStatement.class::cast)
                .forEach(statement -> {
                    RoundRobin roundRobin = statement.labelAccess().roundRobin();
                    if (roundRobin.getBehaviour() == BY_BLOCK && statement.each()) {
                        tracker.add(PROGRAM_WARNING_ROUND_ROBIN_SMELLY_EACH.get(statement.toStringPretty()));
                    } else if (roundRobin.getBehaviour() == BY_LABEL
                               && statement.labelAccess().labels().size() == 1) {
                        tracker.add(PROGRAM_WARNING_ROUND_ROBIN_SMELLY_COUNT.get(statement.toStringPretty()));
                    }
                });
    }

    @Override
    public void fixWarnings(
            Program program,
            LabelPositionHolder labels,
            ManagerBlockEntity manager,
            World level,
            ItemStack disk
    ) {
        // TODO: rewrite by removing "each" keyword in applicable places
    }
}
