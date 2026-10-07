package ca.teamdman.sfm.common.program;

import ca.teamdman.sfm.common.resourcetype.ResourceType;
import ca.teamdman.sfml.ast.Label;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.jetbrains.annotations.Nullable;

public class LimitedInputSlot<STACK, ITEM, CAP> implements LimitedSlot<STACK, ITEM, CAP> {
    public ResourceType<STACK, ITEM, CAP> type;

    public CAP handler;

{% if features.packet_computation %}
    public @Nullable BlockPos pos;
{% else %}
    public BlockPos pos;
{% endif %}

{% if features.packet_computation %}
    public @Nullable Label label;
{% else %}
    public Label label;
{% endif %}

{% if features.packet_computation %}
    public @Nullable Direction direction;
{% else %}
    public Direction direction;
{% endif %}

{% if features.packet_computation %}
    private @Nullable String generatedSourceDescription;

{% else %}
{% endif %}
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

{% if features.packet_computation %}
    public LimitedInputSlot(
            String generatedSourceDescription,
            int slot,
            CAP handler,
            IInputResourceTracker tracker,
            STACK stackCache,
            ResourceType<STACK, ITEM, CAP> type
    ) {
        this.initGenerated(handler, generatedSourceDescription, slot, tracker, stackCache, type);
    }

{% else %}
{% endif %}
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
{% if features.packet_computation %}
        this.generatedSourceDescription = null;
{% else %}
{% endif %}
        this.freed = false;
        this.type = type;
    }

{% if features.packet_computation %}
    public void initGenerated(
            CAP handler,
            String generatedSourceDescription,
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
        this.pos = null;
        this.label = null;
        this.direction = null;
        this.generatedSourceDescription = generatedSourceDescription;
        this.freed = false;
        this.type = type;
    }

    public boolean isGeneratedSource() {
        return generatedSourceDescription != null;
    }

    public @Nullable String getGeneratedSourceDescription() {
        return generatedSourceDescription;
    }

{% else %}
{% endif %}
    @Override
    public String toString() {

        return "LimitedInputSlot{"
{% if features.packet_computation %}
               + (generatedSourceDescription == null ? "" : "source=" + generatedSourceDescription + ", ")
{% else %}
{% endif %}
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
{% if features.packet_computation %}
    public @Nullable BlockPos getPos() {
{% else %}
    public BlockPos getPos() {
{% endif %}

        return pos;
    }

    @Override
{% if features.packet_computation %}
    public @Nullable Label getLabel() {
{% else %}
    public Label getLabel() {
{% endif %}

        return label;
    }

    @Override
{% if features.packet_computation %}
    public @Nullable Direction getDirection() {
{% else %}
    public Direction getDirection() {
{% endif %}

        return direction;
    }

    @Override
    public int getSlot() {

        return slot;
    }

}
