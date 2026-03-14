package ca.teamdman.sfm.common.program.linting;

import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import ca.teamdman.sfm.common.label.LabelPositionHolder;
import ca.teamdman.sfml.ast.IOStatement;
import ca.teamdman.sfml.ast.RoundRobin;
import ca.teamdman.sfml.ast.SFMLProgram;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import static ca.teamdman.sfm.common.localization.LocalizationKeys.PROGRAM_WARNING_ROUND_ROBIN_SMELLY_COUNT;
import static ca.teamdman.sfm.common.localization.LocalizationKeys.PROGRAM_WARNING_ROUND_ROBIN_SMELLY_EACH;
import static ca.teamdman.sfml.ast.RoundRobinBehaviour.BY_BLOCK;
import static ca.teamdman.sfml.ast.RoundRobinBehaviour.BY_LABEL;

public class RoundRobinProgramLinter implements IProgramLinter{
    // check "each" usage and round-robin usage in IO statements
    @Override
    public void gatherWarnings(
            SFMLProgram program,
            LabelPositionHolder labelPositionHolder,
            @Nullable ManagerBlockEntity managerBlockEntity,
            ProblemTracker tracker
    ) {
        program.getDescendantNodes()
                .filter(IOStatement.class::isInstance)
                .map(IOStatement.class::cast)
                .forEach(statement -> {
                    RoundRobin roundRobin = statement.resourceAccess().roundRobin();
                    if (roundRobin.behaviour() == BY_BLOCK && statement.each()) {
                        tracker.add(PROGRAM_WARNING_ROUND_ROBIN_SMELLY_EACH.get(statement.toStringPretty()));
                    } else if (roundRobin.behaviour() == BY_LABEL
                               && statement.resourceAccess().labelExpressions().size() == 1) {
                        tracker.add(PROGRAM_WARNING_ROUND_ROBIN_SMELLY_COUNT.get(statement.toStringPretty()));
                    }
                });
    }

    @Override
    public void fixWarnings(
            SFMLProgram program,
            LabelPositionHolder labels,
            ManagerBlockEntity manager,
            Level level,
            ItemStack disk
    ) {
        // TODO: rewrite by removing "each" keyword in applicable places
    }
}
