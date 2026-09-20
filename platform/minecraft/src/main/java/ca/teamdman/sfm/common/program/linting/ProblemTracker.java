package ca.teamdman.sfm.common.program.linting;

import ca.teamdman.sfm.common.config.SFMConfig;
import net.minecraft.network.chat.contents.TranslatableContents;

import java.util.HashSet;

/// Safety: use the {@link #add(TranslatableContents)} fn to avoid problems with large problem counts.
public record ProblemTracker(HashSet<TranslatableContents> problems) {
    public ProblemTracker() {

        this(new HashSet<>());
    }

    public AddProblemResult add(TranslatableContents problem) {
        int size = problems.size();
        int maxProblems = SFMConfig.getOrDefault(SFMConfig.SERVER_CONFIG.maxDiskProblems);
        if (size >= maxProblems) {
            return AddProblemResult.TOO_MANY_PROBLEMS;
        }
        problems.add(problem);
        if (size < maxProblems) {
            return AddProblemResult.SUCCESS;
        }
        // signal to stop collecting problems
        return AddProblemResult.TOO_MANY_PROBLEMS;
    }

    public boolean isSaturated() {
        return problems.size() >= SFMConfig.getOrDefault(SFMConfig.SERVER_CONFIG.maxDiskProblems);
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
