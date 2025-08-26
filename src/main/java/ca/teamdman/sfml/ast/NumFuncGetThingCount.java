package ca.teamdman.sfml.ast;

import ca.teamdman.sfm.common.program.ProgramContext;
import ca.teamdman.sfm.common.resourcetype.ResourceType;
import com.mojang.datafixers.util.Pair;
import net.minecraft.core.BlockPos;

import java.util.concurrent.atomic.AtomicLong;

public final class NumFuncGetThingCount implements NumExpr {
    private final LabelAccess labelAccess;
    private final ResourceIdSet resourceIds; // optional; empty => match all

    public NumFuncGetThingCount(LabelAccess labelAccess, ResourceIdSet resourceIds) {
        this.labelAccess = labelAccess;
        this.resourceIds = resourceIds;
    }

    @Override
    public long eval(ProgramContext context) {
        AtomicLong total = new AtomicLong(0);
        var positions = labelAccess.getLabelledPositions(context.getLabelPositionHolder());
        for (Pair<Label, BlockPos> entry : positions) {
            BlockPos pos = entry.getSecond();
            for (ResourceType<?, ?, ?> resourceType : resourceIds.getReferencedResourceTypes()) {
                accumulate(context, pos, total, resourceType);
            }
        }
        return total.get();
    }

    private <STACK, ITEM, CAP> void accumulate(
            ProgramContext programContext,
            BlockPos pos,
            AtomicLong accumulator,
            ResourceType<STACK, ITEM, CAP> resourceType
    ) {
        resourceType.forEachDirectionalCapability(
                programContext,
                labelAccess.directions(),
                pos,
                (direction, cap) -> resourceType.getStacksInSlots(cap, labelAccess.slots()).forEach(stack -> {
                    if (this.resourceIds.getMatchingFromStack(stack) != null) {
                        accumulator.addAndGet(resourceType.getAmount(stack));
                    }
                })
        );
    }

    @Override
    public String toString() {
        if (resourceIds.isEmpty()) {
            return "get_thing_count(" + labelAccess + ")";
        }
        return "get_thing_count(" + labelAccess + ", " + resourceIds.toStringCondensed() + ")";
    }
}
