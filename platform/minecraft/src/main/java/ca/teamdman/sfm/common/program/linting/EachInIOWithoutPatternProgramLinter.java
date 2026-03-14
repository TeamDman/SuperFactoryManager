package ca.teamdman.sfm.common.program.linting;

import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import ca.teamdman.sfm.common.label.LabelPositionHolder;
import ca.teamdman.sfml.ast.IOStatement;
import ca.teamdman.sfml.ast.ResourceQuantity;
import ca.teamdman.sfml.ast.SFMLProgram;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import static ca.teamdman.sfm.common.localization.LocalizationKeys.PROGRAM_WARNING_RESOURCE_EACH_WITHOUT_PATTERN;

public class EachInIOWithoutPatternProgramLinter implements IProgramLinter {
    /// Example:
    /// ```sfm
    /// INPUT EACH stick FROM chest
    /// ```
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
                    boolean smells = statement
                            .resourceLimits()
                            .resourceLimitList()
                            .stream()
                            .anyMatch(rl ->
                                              rl.limit().quantity().idExpansionBehaviour()
                                              == ResourceQuantity.IdExpansionBehaviour.EXPAND
                                              && !rl.resourceIds().couldMatchMoreThanOne()
                            );
                    if (smells) {
                        tracker.add(PROGRAM_WARNING_RESOURCE_EACH_WITHOUT_PATTERN.get(statement.toStringPretty()));
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
        // todo: rewrite by removing "each" keyword in applicable places
    }
}
