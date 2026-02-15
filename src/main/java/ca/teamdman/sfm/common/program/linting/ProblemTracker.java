package ca.teamdman.sfm.common.program.linting;

import ca.teamdman.sfm.common.config.SFMConfig;
import ca.teamdman.sfm.common.util.TextComponentTranslationHashable;
import com.github.bsideup.jabel.Desugar;
import net.minecraft.util.text.TextComponentTranslation;

import java.util.HashSet;

/// Safety: use the {@link #add(TextComponentTranslation)} fn to avoid problems with large problem counts.
@Desugar
public record ProblemTracker(HashSet<TextComponentTranslation> problems) {
    public ProblemTracker() {

        this(new HashSet<>());
    }

    public AddProblemResult add(TextComponentTranslationHashable problem) {
        if (problems.size() >= SFMConfig.server.maxDiskProblems) {
            return AddProblemResult.TOO_MANY_PROBLEMS;
        }
        problems.add(problem);
        if (problems.size() < SFMConfig.server.maxDiskProblems) {
            return AddProblemResult.SUCCESS;
        }
        // signal to stop collecting problems
        return AddProblemResult.TOO_MANY_PROBLEMS;
    }

    public boolean isSaturated() {
        return problems.size() >= SFMConfig.server.maxDiskProblems;
    }

    public int size() {
        return problems.size();
    }

    public enum AddProblemResult {
        SUCCESS,
        TOO_MANY_PROBLEMS; // me_irl

        public boolean isSaturated() {
            return this == TOO_MANY_PROBLEMS;
        }
    }

}
