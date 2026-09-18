package ca.teamdman.sfm.common.program.linting;

import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import ca.teamdman.sfm.common.label.LabelPositionHolder;
import ca.teamdman.sfm.common.localization.LocalizationEntry;
import ca.teamdman.sfm.common.localization.SFMLocalizationDatagen;
import ca.teamdman.sfml.ast.Program;
import ca.teamdman.sfml.ast.TimerTrigger;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/** Migration warning: the old plus-offset is accepted but prints as OFFSET BY. */
public final class LegacyIntervalOffsetProgramLinter implements IProgramLinter {
    @SFMLocalizationDatagen
    public static final LocalizationEntry PROGRAM_WARNING_LEGACY_INTERVAL_OFFSET = new LocalizationEntry(
            "program.sfm.warnings.legacy_interval_offset",
            "The legacy + interval offset is ambiguous; use OFFSET BY with an explicit unit: %s"
    );

    @Override
    public void gatherWarnings(
            Program program,
            LabelPositionHolder labelPositionHolder,
            @Nullable ManagerBlockEntity managerBlockEntity,
            ProblemTracker tracker
    ) {
        program.triggers().stream()
                .filter(TimerTrigger.class::isInstance)
                .map(TimerTrigger.class::cast)
                .filter(trigger -> trigger.interval().legacyOffsetSyntax())
                .forEach(trigger -> tracker.add(PROGRAM_WARNING_LEGACY_INTERVAL_OFFSET.get(
                        trigger.interval().toString()
                )));
    }

    @Override
    public void fixWarnings(
            Program program,
            LabelPositionHolder labels,
            ManagerBlockEntity manager,
            Level level,
            ItemStack disk
    ) {
        // No automatic source rewrite: retain comments and author formatting.
    }
}
