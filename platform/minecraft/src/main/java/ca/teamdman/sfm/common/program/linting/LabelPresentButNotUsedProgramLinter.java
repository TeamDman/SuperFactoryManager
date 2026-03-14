package ca.teamdman.sfm.common.program.linting;

import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import ca.teamdman.sfm.common.label.LabelPositionHolder;
import ca.teamdman.sfml.ast.SFMLProgram;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import static ca.teamdman.sfm.common.localization.LocalizationKeys.PROGRAM_REMINDER_PUSH_LABELS;
import static ca.teamdman.sfm.common.localization.LocalizationKeys.PROGRAM_WARNING_UNDEFINED_LABEL;

public class LabelPresentButNotUsedProgramLinter implements IProgramLinter {
    @Override
    public void gatherWarnings(
            SFMLProgram program,
            LabelPositionHolder labelPositionHolder,
            @Nullable ManagerBlockEntity managerBlockEntity,
            ProblemTracker tracker
    ) {
        int before = tracker.size();
        for (String x : labelPositionHolder.labels().keySet()) {
            if (!program.referencedLabels().contains(x)) {
                if (tracker.add(PROGRAM_WARNING_UNDEFINED_LABEL.get(x)).isSaturated()) {
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
            SFMLProgram program,
            LabelPositionHolder labels,
            ManagerBlockEntity manager,
            Level level,
            ItemStack disk
    ) {
        // remove labels not defined in code
        labels.removeIf(label -> !program.referencedLabels().contains(label));
    }

}
