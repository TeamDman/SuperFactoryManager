package ca.teamdman.sfm.common.program;

import ca.teamdman.sfml.ast.LabelExpression;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

@FunctionalInterface
public interface CapabilityConsumer<T> {
    void accept(
            LabelExpression labelExpression,
            BlockPos pos,
            Direction direction,
            T cap
    );
}
