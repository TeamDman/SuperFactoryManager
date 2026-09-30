package ca.teamdman.sfm.common.program.linting;

import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import ca.teamdman.sfm.common.label.LabelPositionHolder;
import ca.teamdman.sfm.common.localization.LocalizationEntry;
import ca.teamdman.sfm.common.localization.SFMLocalizationDatagen;
import ca.teamdman.sfml.ast.IOStatement;
import ca.teamdman.sfml.ast.Program;
import ca.teamdman.sfml.ast.ResourceQuantity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

public class EachInIOWithoutPatternProgramLinter implements IProgramLinter {
    @SFMLocalizationDatagen
    public static final LocalizationEntry PROGRAM_WARNING_RESOURCE_EACH_WITHOUT_PATTERN = new LocalizationEntry(
            "program.sfm.warnings.each_without_pattern",
            "EACH used without a pattern, statement %s"
    );

    /// Example:
    /// ```sfm
    /// INPUT EACH stick FROM chest
    /// ```
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
            Program program,
            LabelPositionHolder labels,
            ManagerBlockEntity manager,
            Level level,
            ItemStack disk
    ) {
        // todo: rewrite by removing "each" keyword in applicable places
    }

}
