package ca.teamdman.sfml.ast;

import ca.teamdman.sfm.common.label.LabelPositionHolder;
import net.minecraft.core.BlockPos;

import java.util.List;
import java.util.Set;

public record LabelExpressionSingle(Label label) implements LabelExpression {
    @Override
    public Set<BlockPos> getPositions(LabelPositionHolder labelPositionHolder) {

        return labelPositionHolder.getPositions(label.value());
    }

    @Override
    public List<Label> getChildNodes() {

        return List.of(label);
    }

}
