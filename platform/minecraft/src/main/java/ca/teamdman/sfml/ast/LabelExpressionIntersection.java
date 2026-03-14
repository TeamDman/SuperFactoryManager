package ca.teamdman.sfml.ast;

import ca.teamdman.sfm.common.label.LabelPositionHolder;
import net.minecraft.core.BlockPos;

import java.util.List;
import java.util.Set;

public record LabelExpressionIntersection(
        LabelExpression left,
        LabelExpression right
) implements LabelExpression {
    @Override
    public Set<BlockPos> getPositions(LabelPositionHolder labelPositionHolder) {
        Set<BlockPos> leftPositions = left().getPositions(labelPositionHolder);
        Set<BlockPos> rightPositions = right().getPositions(labelPositionHolder);
        leftPositions.retainAll(rightPositions);
        return leftPositions;
    }

    @Override
    public List<LabelExpression> getChildNodes() {

        return List.of(left, right);
    }

}
