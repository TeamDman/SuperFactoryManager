package ca.teamdman.sfm.common.program.linting;

import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import ca.teamdman.sfm.common.label.LabelPositionHolder;
import ca.teamdman.sfml.ast.Program;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraftforge.registries.IForgeRegistryEntry;
import org.jetbrains.annotations.Nullable;

import static ca.teamdman.sfm.common.localization.LocalizationKeys.PROGRAM_REMINDER_PUSH_LABELS;
import static ca.teamdman.sfm.common.localization.LocalizationKeys.PROGRAM_WARNING_UNDEFINED_LABEL;

public class LabelPresentButNotUsedProgramLinter extends IForgeRegistryEntry.Impl<IProgramLinter> implements IProgramLinter {
    @Override
    public void gatherWarnings(
            Program program,
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
            Program program,
            LabelPositionHolder labels,
            ManagerBlockEntity manager,
            World level,
            ItemStack disk
    ) {
        // remove labels not defined in code
        labels.removeIf(label -> !program.referencedLabels().contains(label));
    }

}
