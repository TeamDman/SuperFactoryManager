package ca.teamdman.sfm.common.program;

import ca.teamdman.sfm.common.resourcetype.ResourceType;
import ca.teamdman.sfml.ast.Label;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.jetbrains.annotations.Nullable;

public class LimitedInputSlot<STACK, ITEM, CAP> implements LimitedSlot<STACK, ITEM, CAP> {
    public ResourceType<STACK, ITEM, CAP> type;

    public CAP handler;

    public BlockPos pos;

    public Label label;

    public Direction direction;

    public int slot;

    public boolean freed;

    public IInputResourceTracker tracker;

    private @Nullable STACK stackInSlotCache = null;

    private boolean done = false;

    public LimitedInputSlot(
            Label label,
            BlockPos pos,
            Direction direction,
            int slot,
            CAP handler,
            IInputResourceTracker tracker,
            STACK stackCache,
            ResourceType<STACK, ITEM, CAP> type
    ) {

        this.init(handler, label, pos, direction, slot, tracker, stackCache, type);
    }

    public boolean isDone() {

        if (done) return true;

        // Below, we set `this.done = true` because this slot is cached for use in later OUTPUT statements

        if (slot > type.getSlots(handler) - 1) {
            // The composter block can change how many slots it has between insertions
            done = true;
            return true;
        }

        STACK stack = this.peekStackInSlot();
        if (type.isEmpty(stack)) {
            done = true;
            return true;
        }
        if (!tracker.matchesStack(stack)) {
            done = true;
            return true;
        }
        if (tracker.isDone(type, stack)) {
            done = true;
            return true;
        }
        return false;
    }

    public void setDone() {

        this.done = true;
    }

    public STACK extract(long amount) {

        stackInSlotCache = null;
        return type.extract(handler, slot, amount, false);
    }

    /// The content of the slot, this may exceed the max stack size.
    ///
    /// For example, a dank storage dock can have 256 items in a slot, but if we queried extraction, it would say 64.
    ///
    /// Some slots are insert-only and may report as having a stack in the slot but return nothing during extraction.
    ///
    /// Note well the difference between these two:
    /// ```java
    /// type.getStackInSlot(handler, slot);                // can be greater than max-stack-size
    /// type.extract(handler, slot, Long.MAX_VALUE, true); // can be zero when the above is non-zero
    /// ```
    public STACK peekStackInSlot() {
        if (stackInSlotCache == null) {
            stackInSlotCache = type.getStackInSlot(handler, slot);
        }
        return stackInSlotCache;
    }

    @SuppressWarnings("DuplicatedCode")
    public void init(
            CAP handler,
            Label label,
            BlockPos pos,
            Direction direction,
            int slot,
            IInputResourceTracker tracker,
            STACK stackCache,
            ResourceType<STACK, ITEM, CAP> type
    ) {

        this.done = false;
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

        return "LimitedInputSlot{"
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
