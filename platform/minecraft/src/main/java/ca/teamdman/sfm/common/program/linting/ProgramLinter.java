package ca.teamdman.sfm.common.program.linting;

import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import ca.teamdman.sfm.common.label.LabelPositionHolder;
import ca.teamdman.sfm.common.localization.LocalizationKeys;
import ca.teamdman.sfm.common.logging.TranslatableLogger;
import ca.teamdman.sfm.common.registry.SFMProgramLinters;
import ca.teamdman.sfm.common.timing.SFMInstant;
import ca.teamdman.sfml.ast.SFMLProgram;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.Collection;

public class ProgramLinter {

    /// Apply the registered linters until saturation.
    public static Collection<TranslatableContents> gatherWarnings(
            SFMLProgram program,
            LabelPositionHolder labelPositionHolder,
            @Nullable ManagerBlockEntity manager
    ) {
        // Identify logger if present
        final TranslatableLogger logger;
        if (manager == null) {
            logger = null;
        } else {
            logger = manager.logger;
        }

        // Log that we are starting the linting process
        if (logger != null) {
            logger.debug(x -> x.accept(LocalizationKeys.PROGRAM_LINTING_BEGIN.get()));
        }

        // Start the stopwatch
        SFMInstant start = SFMInstant.now();

        // Run the linters
        ProblemTracker tracker = new ProblemTracker();
        for (IProgramLinter linter : SFMProgramLinters.registry().values()) {
            // Start the linter-specific stopwatch
            SFMInstant linterStart = SFMInstant.now();

            // Count how many problems there are before the linter runs
            int sizeBefore = tracker.size();

            // Run the linter
            linter.gatherWarnings(
                    program,
                    labelPositionHolder,
                    manager,
                    tracker
            );

            // Count how many problems the linter discovered
            int sizeAfter = tracker.size();
            int foundProblemCount = sizeAfter - sizeBefore;

            // Stop the stopwatch
            Duration linterElapsed = linterStart.elapsed();

            // Log the linter details
            if (logger != null) {
                logger.debug(x -> x.accept(LocalizationKeys.PROGRAM_LINTING_LINTER_REPORT.get(
                        linter.getClass().getCanonicalName(),
                        foundProblemCount,
                        linterElapsed
                )));
            }

            // Stop if we have reached the limit for how many problems to report
            if (tracker.isSaturated()) {
                break;
            }
        }

        // Log how long the entire linting process took
        if (logger != null) {
            logger.debug(x -> x.accept(LocalizationKeys.PROGRAM_LINTING_FINISHED_WITH_PROBLEM_COUNT_AND_ELAPSED_DURATION.get(
                    tracker.size(),
                    start.elapsed()
            )));
        }

        // Return the discovered problems
        return tracker.problems();
    }

    /// Resolve warnings by modifying the labels in the disk and by modifying the program.
    ///
    /// This fn fixes all warnings, though we only display a limited number
    /// ({@link ca.teamdman.sfm.common.config.SFMServerConfig#maxDiskProblems}).
    public static void fixWarnings(
            ManagerBlockEntity manager,
            ItemStack disk,
            SFMLProgram program
    ) {

        LabelPositionHolder labels = LabelPositionHolder.from(disk);
        for (IProgramLinter linter : SFMProgramLinters.registry().values()) {
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
