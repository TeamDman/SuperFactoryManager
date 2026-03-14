package ca.teamdman.sfml.ast;

import ca.teamdman.sfm.common.label.LabelPositionHolder;
import ca.teamdman.sfm.common.util.SFMEnvironmentUtils;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

public enum RoundRobinBehaviour implements SfmlAstNode {
    /// Any position satisfying any {@link LabelExpression} in the list of expressions.
    UNMODIFIED,
    /// Exactly one position that satisfies any {@link LabelExpression} in the list of expressions.
    BY_BLOCK,
    /// Exactly one {@link LabelExpression} in the list will be chosen for evaluation from the list of expressions.
    BY_LABEL;

    @Override
    public List<? extends SfmlAstNode> getChildNodes() {

        return List.of();
    }


    /// @param tick determines what index the round-robin grabs
    public ArrayList<BlockPos> getPositions(
            List<LabelExpression> labelExpressions,
            LabelPositionHolder labelPositionHolder,
            int tick
    ) {

        if (SFMEnvironmentUtils.isInIDE()) {
            if (labelExpressions.isEmpty()) {
                throw new IllegalStateException("There must be at least one label expression to round-robin");
            }
        }

        ArrayList<BlockPos> rtn = new ArrayList<>();
        switch (this) {
            case UNMODIFIED -> {
                for (LabelExpression labelExpression : labelExpressions) {
                    rtn.addAll(labelExpression.getPositions(labelPositionHolder));
                }
            }
            case BY_BLOCK -> {
                LongOpenHashSet candidates = new LongOpenHashSet();
                for (LabelExpression labelExpression : labelExpressions) {
                    Set<BlockPos> satisfying = labelExpression.getPositions(labelPositionHolder);
                    for (BlockPos blockPos : satisfying) {
                        candidates.add(blockPos.asLong());
                    }
                }

                long[] candidatesArray = candidates.toLongArray();
                // CORRECTNESS: must be sorted for consistent order when gathered on separate ticks
                Arrays.sort(candidatesArray);

                if (!candidates.isEmpty()) {
                    int index = tick % candidates.size();
                    rtn.add(BlockPos.of(candidatesArray[index]));
                }
            }
            case BY_LABEL -> {
                LabelExpression expression = labelExpressions.get(tick % labelExpressions.size());
                rtn.addAll(expression.getPositions(labelPositionHolder));
            }
        }
        return rtn;
    }
}
