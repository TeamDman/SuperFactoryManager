package ca.teamdman.sfm.common.program;

import ca.teamdman.sfm.common.resourcetype.ResourceType;
import ca.teamdman.sfml.ast.Label;
import net.minecraft.util.math.BlockPos;
import net.minecraft.core.Direction;

public interface LimitedSlot<STACK, ITEM, CAP> {
    ResourceType<STACK, ITEM, CAP> getType();

    CAP getHandler();

    BlockPos getPos();

    Label getLabel();

    Direction getDirection();

    int getSlot();
}
