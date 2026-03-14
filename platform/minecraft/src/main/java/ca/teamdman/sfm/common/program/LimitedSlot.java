package ca.teamdman.sfm.common.program;

import ca.teamdman.sfm.common.resourcetype.ResourceType;
import ca.teamdman.sfml.ast.LabelExpression;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

public interface LimitedSlot<STACK, ITEM, CAP> {
    ResourceType<STACK, ITEM, CAP> getType();

    CAP getHandler();

    BlockPos getPos();

    LabelExpression getLabelExpression();

    Direction getDirection();

    int getSlot();
}
