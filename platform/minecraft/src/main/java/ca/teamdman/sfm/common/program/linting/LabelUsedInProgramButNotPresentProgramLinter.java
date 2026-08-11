package ca.teamdman.sfm.common.program.linting;

import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import ca.teamdman.sfm.common.label.LabelPositionHolder;
import ca.teamdman.sfm.common.localization.LocalizationEntry;
import ca.teamdman.sfm.common.localization.SFMLocalizationDatagen;
import ca.teamdman.sfml.ast.Program;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import static ca.teamdman.sfm.common.program.linting.LabelNotConnectedProgramLinter.PROGRAM_REMINDER_PUSH_LABELS;

public class LabelUsedInProgramButNotPresentProgramLinter implements IProgramLinter {
    @SFMLocalizationDatagen
    public static final LocalizationEntry PROGRAM_WARNING_UNUSED_LABEL = new LocalizationEntry(
            "program.sfm.warnings.unused_label",
            "Label \"%s\" is used in code but not assigned in the world."
    );

    @Override
    public void gatherWarnings(
            Program program,
            LabelPositionHolder labelPositionHolder,
            @Nullable ManagerBlockEntity managerBlockEntity,
            ProblemTracker tracker
    ) {

        int before = tracker.size();
        for (String label : program.referencedLabels()) {
            if (labelPositionHolder.getPositions(label).isEmpty()) {
                if (tracker.add(PROGRAM_WARNING_UNUSED_LABEL.get(label)).isSaturated()) {
                    break;
                }
            }
        }
        if (tracker.size() > before) {
            tracker.add(PROGRAM_REMINDER_PUSH_LABELS.get());
        }
    }

    @Override
    public void fixWarnings(
            Program program,
            LabelPositionHolder labels,
            ManagerBlockEntity manager,
            Level level,
            ItemStack disk
    ) {
        // remove the labels that are not defined in code
        labels.removeIf(label -> !program.referencedLabels().contains(label));
    }

}
