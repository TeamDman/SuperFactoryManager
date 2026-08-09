package ca.teamdman.sfm.common.program;

import ca.teamdman.sfm.common.resourcetype.ResourceType;
import ca.teamdman.sfml.ast.Label;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.jetbrains.annotations.Nullable;

public class LimitedOutputSlot<STACK, ITEM, CAP> implements LimitedSlot<STACK, ITEM, CAP> {
    public ResourceType<STACK, ITEM, CAP> type;

    public CAP handler;

    public BlockPos pos;

    public Label label;

    public int slot;

    public boolean freed;

    public IOutputResourceTracker tracker;

    public Direction direction;

    private @Nullable STACK stackInSlotCache = null;

    public LimitedOutputSlot(
            Label label,
            BlockPos pos,
            Direction direction,
            int slot,
            CAP handler,
            IOutputResourceTracker tracker,
            STACK stackCache,
            ResourceType<STACK, ITEM, CAP> type
    ) {

        this.init(handler, label, pos, direction, slot, tracker, stackCache, type);
    }

    @SuppressWarnings("RedundantIfStatement")
    public boolean isDone() {

        if (slot > type.getSlots(handler) - 1) {
            // The composter block can change how many slots it has between insertions
            return true;
        }
        STACK stack = getStackInSlot();
        long count = type.getAmount(stack);
        if (count >= type.getMaxStackSizeForSlot(handler, slot)) {
            return true;
        }
        if (count != 0 && !tracker.matchesStack(stack)) {
            return true;
        }
        if (tracker.isDone(type, stack)) {
            return true;
        }
        return false;
    }

    public STACK getStackInSlot() {

        if (stackInSlotCache == null) {
            stackInSlotCache = type.getStackInSlot(handler, slot);
        }
        return stackInSlotCache;
    }

    public STACK insert(
            STACK stack,
            boolean simulate
    ) {

        if (!simulate) stackInSlotCache = null;
        return type.insert(handler, slot, stack, simulate);
    }

    @SuppressWarnings("DuplicatedCode")
    public void init(
            CAP handler,
            Label label,
            BlockPos pos,
            Direction direction,
            int slot,
            IOutputResourceTracker tracker,
            STACK stackCache,
            ResourceType<STACK, ITEM, CAP> type
    ) {

        this.stackInSlotCache = stackCache;
        this.handler = handler;
        this.tracker = tracker;
        this.slot = slot;
        this.pos = pos;
        this.label = label;
        this.direction = direction;
        this.freed = false;
        this.type = type;
    }

    @Override
    public String toString() {

        return "LimitedOutputSlot{"
               + "label=" + label
               + ", pos=" + pos
               + ", direction=" + direction
               + ", slot=" + slot
               + ", cap=" + type.displayAsCapabilityClass()
               + ", tracker=" + tracker
               + '}';
    }

    @Override
    public ResourceType<STACK, ITEM, CAP> getType() {

        return type;
    }

    @Override
    public CAP getHandler() {

        return handler;
    }

    @Override
    public BlockPos getPos() {

        return pos;
    }

    @Override
    public Label getLabel() {

        return label;
    }

    @Override
    public Direction getDirection() {

        return direction;
    }

    @Override
    public int getSlot() {

        return slot;
    }

}
