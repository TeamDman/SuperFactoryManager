package ca.teamdman.sfm.common.program;

import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;

import org.jetbrains.annotations.Nullable;

import ca.teamdman.sfm.common.resourcetype.ResourceTypeContainer.ResourceType;
import ca.teamdman.sfm.common.util.Stored;
import ca.teamdman.sfml.ast.Label;

public class LimitedOutputSlot<STACK, ITEM, CAP> implements LimitedSlot<STACK, ITEM, CAP> {

    @SuppressWarnings("NotNullFieldNotInitialized") // done in init method in constructor
    public ResourceType<STACK, ITEM, CAP> type;
    @SuppressWarnings("NotNullFieldNotInitialized") // done in init method in constructor
    public CAP handler;
    @SuppressWarnings("NotNullFieldNotInitialized") // done in init method in constructor
    public BlockPos pos;
    @SuppressWarnings("NotNullFieldNotInitialized") // done in init method in constructor
    public Label label;
    public int slot;
    public boolean freed;
    @SuppressWarnings("NotNullFieldNotInitialized") // done in init method in constructor
    public IOutputResourceTracker tracker;
    @SuppressWarnings("NotNullFieldNotInitialized") // done in init method in constructor
    public EnumFacing direction;
    private @Nullable STACK stackInSlotCache = null;

    public LimitedOutputSlot(
                             Label label,
                             BlockPos pos,
                             EnumFacing direction,
                             int slot,
                             CAP handler,
                             IOutputResourceTracker tracker,
                             STACK stackCache,
                             ResourceType<STACK, ITEM, CAP> type) {
        this.init(handler, label, pos, direction, slot, tracker, stackCache, type);
    }

    @SuppressWarnings("RedundantIfStatement")
    public boolean isDone() {
        if (slot > type.getSlots(handler) - 1) {
            // composter block changes how many slots it has between insertions
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
                        boolean simulate) {
        if (!simulate) stackInSlotCache = null;
        return type.insert(handler, slot, stack, simulate);
    }

    @SuppressWarnings("DuplicatedCode")
    public void init(
                     CAP handler,
                     Label label,
                     @Stored BlockPos pos,
                     EnumFacing direction,
                     int slot,
                     IOutputResourceTracker tracker,
                     STACK stackCache,
                     ResourceType<STACK, ITEM, CAP> type) {
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
        return "LimitedOutputSlot{" + "label=" + label + ", pos=" + pos + ", direction=" + direction + ", slot=" +
                slot + ", cap=" + type.displayAsCapabilityClass() + ", tracker=" + tracker + '}';
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
    public EnumFacing getDirection() {
        return direction;
    }

    @Override
    public int getSlot() {
        return slot;
    }
}
