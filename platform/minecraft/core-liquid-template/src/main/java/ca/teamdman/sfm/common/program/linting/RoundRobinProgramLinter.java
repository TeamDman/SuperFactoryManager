package ca.teamdman.sfm.common.program.linting;

import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import ca.teamdman.sfm.common.label.LabelPositionHolder;
import ca.teamdman.sfm.common.localization.LocalizationEntry;
import ca.teamdman.sfm.common.localization.SFMLocalizationDatagen;
import ca.teamdman.sfml.ast.IOStatement;
import ca.teamdman.sfml.ast.Program;
import ca.teamdman.sfml.ast.RoundRobin;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import static ca.teamdman.sfml.ast.RoundRobin.Behaviour.BY_BLOCK;
import static ca.teamdman.sfml.ast.RoundRobin.Behaviour.BY_LABEL;

public class RoundRobinProgramLinter implements IProgramLinter {
    @SFMLocalizationDatagen
    public static final LocalizationEntry PROGRAM_WARNING_ROUND_ROBIN_SMELLY_EACH = new LocalizationEntry(
            "program.sfm.warnings.round_robin_smelly_each",
            "Round robin by block shouldn't be used with EACH, statement %s"
    );

    @SFMLocalizationDatagen
    public static final LocalizationEntry PROGRAM_WARNING_ROUND_ROBIN_SMELLY_COUNT = new LocalizationEntry(
            "program.sfm.warnings.round_robin_smelly_count",
            "Round robin by label should be used with more than one label, statement %s"
    );

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
            Level level,
            ItemStack disk
    ) {
        // TODO: rewrite by removing "each" keyword in applicable places
    }

}
