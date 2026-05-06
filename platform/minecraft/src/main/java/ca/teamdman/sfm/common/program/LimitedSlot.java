package ca.teamdman.sfm.common.program;

import ca.teamdman.sfm.common.resourcetype.ResourceType;
import ca.teamdman.sfml.ast.Label;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.resource.ResourceStack;

public interface LimitedSlot<STACK, ITEM, CAP> {
    ResourceType<STACK, ITEM, CAP> getType();

    CAP getHandler();

    BlockPos getPos();

    Label getLabel();

    Direction getDirection();

    int getSlot();
}
