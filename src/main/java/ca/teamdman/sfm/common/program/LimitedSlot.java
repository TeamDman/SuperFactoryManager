package ca.teamdman.sfm.common.program;

import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;

import ca.teamdman.sfm.common.resourcetype.ResourceTypeContainer.ResourceType;
import ca.teamdman.sfml.ast.Label;

public interface LimitedSlot<STACK, ITEM, CAP> {

    ResourceType<STACK, ITEM, CAP> getType();

    CAP getHandler();

    BlockPos getPos();

    Label getLabel();

    EnumFacing getDirection();

    int getSlot();
}
