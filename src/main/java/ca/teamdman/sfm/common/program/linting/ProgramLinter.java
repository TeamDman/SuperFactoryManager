package ca.teamdman.sfm.common.program.linting;

import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import ca.teamdman.sfm.common.label.LabelPositionHolder;
import ca.teamdman.sfm.common.registry.SFMWellKnownRegistries;
import ca.teamdman.sfml.ast.Program;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.TextComponentTranslation;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public class ProgramLinter {

    /// Apply the registered linters until saturation.
    public static Collection<TextComponentTranslation> gatherWarnings(
            Program program,
            LabelPositionHolder labelPositionHolder,
            @Nullable ManagerBlockEntity manager
    ) {

        ProblemTracker tracker = new ProblemTracker();
        for (IProgramLinter linter : SFMWellKnownRegistries.PROGRAM_LINTERS.values()) {
            linter.gatherWarnings(
                    program,
                    labelPositionHolder,
                    manager,
                    tracker
            );
            if (tracker.isSaturated()) {
                break;
            }
        }
        return tracker.problems();
    }

    /// Resolve warnings by modifying the labels in the disk and by modifying the program.
    ///
    /// This fn fixes all warnings, though we only display a limited number
    /// ({@link ca.teamdman.sfm.common.config.SFMServerConfig#maxDiskProblems}).
    public static void fixWarnings(
            ManagerBlockEntity manager,
            ItemStack disk,
            Program program
    ) {

        LabelPositionHolder labels = LabelPositionHolder.from(disk);
        for (IProgramLinter linter : SFMWellKnownRegistries.PROGRAM_LINTERS.values()) {
            assert manager.getLevel() != null;
            linter.fixWarnings(
                    program,
                    labels,
                    manager,
                    manager.getLevel(),
                    disk
            );
        }
        manager.rebuildProgramAndUpdateDisk();
    }

}
