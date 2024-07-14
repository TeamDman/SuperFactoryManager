package ca.teamdman.sfml.ast;

import ca.teamdman.sfm.common.Constants;
import ca.teamdman.sfm.common.program.*;
import ca.teamdman.sfm.common.resourcetype.ResourceType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static ca.teamdman.sfm.common.Constants.LocalizationKeys.*;

public final class InputStatement implements IOStatement {
    private final LabelAccess labelAccess;
    private final ResourceLimits resourceLimits;
    private final boolean each;
    private @Nullable ArrayDeque<LimitedInputSlot<?, ?, ?>> limitedInputSlotsCache = null;

    public InputStatement(
            LabelAccess labelAccess,
            ResourceLimits resourceLimits,
            boolean each
    ) {
        this.labelAccess = labelAccess;
        this.resourceLimits = resourceLimits;
        this.each = each;
    }

    @Override
    public void tick(ProgramContext context) {
        context.addInput(this);
        context.getLogger().debug(x -> x.accept(LOG_PROGRAM_TICK_INPUT_STATEMENT.get(toString())));

        // Track simulation
        if (context.getBehaviour() instanceof SimulateExploreAllPathsProgramBehaviour simulation) {
            simulation.onInputStatementExecution(context, this);
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"}) // basically impossible to make this method generic safe
    public void gatherSlots(
            ProgramContext context,
            Consumer<LimitedInputSlot<?, ?, ?>> slotConsumer
    ) {
        context.getLogger().debug(x -> x.accept(LOG_PROGRAM_TICK_IO_STATEMENT_GATHER_SLOTS.get(toStringPretty())));

        // do we have a cached result?
        if (limitedInputSlotsCache != null) {
            // log cache hit
            context.getLogger().trace(x -> x.accept(LOG_PROGRAM_TICK_IO_STATEMENT_GATHER_SLOTS_CACHE_HIT.get()));
            // return cached results
            for (var slot : limitedInputSlotsCache) {
                slotConsumer.accept(slot);
            }
            limitedInputSlotsCache.forEach(slotConsumer);
            return;
        }

        // log cache miss
        context.getLogger().trace(x -> x.accept(LOG_PROGRAM_TICK_IO_STATEMENT_GATHER_SLOTS_CACHE_MISS.get()));

        // prepare cache state
        limitedInputSlotsCache = new ArrayDeque<>();

        // monkey patch the results acceptor to update the cache before returning results
        {
            var original = slotConsumer;
            slotConsumer = slot -> {
                limitedInputSlotsCache.add(slot);
                original.accept(slot);
            };
        }

        // identify distinct resource types for capability gathering
        Set<ResourceType> referencedResourceTypes = resourceLimits
                .getReferencedResourceTypes()
                .collect(Collectors.toSet());

        if (!each) {
            // log not each
            context.getLogger().debug(x -> x.accept(LOG_PROGRAM_TICK_IO_STATEMENT_GATHER_SLOTS_NOT_EACH.get()));

            // create a single matcher to be shared by all capabilities
            List<InputResourceTracker<?, ?, ?>> inputTrackers = resourceLimits.createInputTrackers();
            for (var type : referencedResourceTypes) {
                // log gather for resource type
                context
                        .getLogger()
                        .debug(x -> x.accept(LOG_PROGRAM_TICK_IO_STATEMENT_GATHER_SLOTS_FOR_RESOURCE_TYPE.get(
                                type.displayAsCapabilityClass(),
                                type.displayAsCapabilityClass()
                        )));

                // gather slots for each capability found for positions tagged by a provided label
                Consumer<LimitedInputSlot<?, ?, ?>> finalSlotConsumer = slotConsumer;
                type.forEachCapability(context, labelAccess, (label, pos, direction, cap) -> gatherSlotsForCap(
                        context,
                        (ResourceType<Object, Object, Object>) type,
                        label, pos, direction, cap,
                        inputTrackers,
                        finalSlotConsumer
                ));
            }
        } else {
            // log yes each
            context.getLogger().debug(x -> x.accept(LOG_PROGRAM_TICK_IO_STATEMENT_GATHER_SLOTS_EACH.get()));

            for (ResourceType type : referencedResourceTypes) {
                // log gather for resource type
                context
                        .getLogger()
                        .debug(x -> x.accept(LOG_PROGRAM_TICK_IO_STATEMENT_GATHER_SLOTS_FOR_RESOURCE_TYPE.get(
                                type.displayAsCapabilityClass(),
                                type.displayAsCapabilityClass()
                        )));

                // gather slots for each capability found for positions tagged by a provided label
                Consumer<LimitedInputSlot<?, ?, ?>> finalSlotConsumer = slotConsumer;
                type.forEachCapability(context, labelAccess, (label, pos, direction, cap) -> {
                    List<InputResourceTracker<?, ?, ?>> inputTrackers = resourceLimits.createInputTrackers();
                    gatherSlotsForCap(
                            context,
                            (ResourceType<Object, Object, Object>) type,
                            label, pos, direction, cap,
                            inputTrackers,
                            finalSlotConsumer
                    );
                });
            }
        }
    }

    @Override
    public String toString() {
        return "INPUT " + resourceLimits.toStringPretty(Limit.MAX_QUANTITY_NO_RETENTION) + " FROM " + (
                each ? "EACH " : ""
        ) + labelAccess;
    }

    @Override
    public String toStringPretty() {
        StringBuilder sb = new StringBuilder();
        sb.append("INPUT");
        String rls = resourceLimits.toStringPretty(Limit.MAX_QUANTITY_NO_RETENTION);
        if (rls.lines().count() > 1) {
            sb.append("\n");
            sb.append(rls.lines().map(s -> "  " + s).collect(Collectors.joining("\n")));
            sb.append("\n");
        } else {
            sb.append(" ");
            sb.append(rls);
            sb.append(" ");
        }
        sb.append("FROM ");
        sb.append(each ? "EACH " : "");
        sb.append(labelAccess);
        return sb.toString();
    }

    @Override
    public LabelAccess labelAccess() {
        return labelAccess;
    }

    @Override
    public ResourceLimits resourceLimits() {
        return resourceLimits;
    }

    @Override
    public boolean each() {
        return each;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (InputStatement) obj;
        return Objects.equals(this.labelAccess, that.labelAccess) && Objects.equals(
                this.resourceLimits,
                that.resourceLimits
        ) && this.each == that.each;
    }

    @Override
    public int hashCode() {
        return Objects.hash(labelAccess, resourceLimits, each);
    }

    /**
     * Release the slots acquired by this statement
     * </p>
     * This was separated from {@link OutputStatement#tick(ProgramContext)} because we need input statements
     * to keep their counts when used by multiple output statements.
     */
    public void freeSlots() {
        if (limitedInputSlotsCache != null) {
            LimitedInputSlotObjectPool.release(limitedInputSlotsCache);
            limitedInputSlotsCache = null;
        }
    }

    public void freeSlotsIf(Predicate<LimitedInputSlot<?,?,?>> condition) {
        if (limitedInputSlotsCache != null) {
            Iterator<LimitedInputSlot<?, ?, ?>> iterator = limitedInputSlotsCache.iterator();
            while (iterator.hasNext()) {
                LimitedInputSlot<?, ?, ?> slot = iterator.next();
                if (condition.test(slot)) {
                    iterator.remove();
                    LimitedInputSlotObjectPool.release(slot);
                }
            }
            if (limitedInputSlotsCache.isEmpty()) {
                limitedInputSlotsCache = null;
            }
        }
    }

    public void transferSlotsTo(InputStatement other) {
        if (limitedInputSlotsCache != null) {
            if (other.limitedInputSlotsCache == null) {
                other.limitedInputSlotsCache = new ArrayDeque<>();
            }
            other.limitedInputSlotsCache.addAll(limitedInputSlotsCache);
        }
        limitedInputSlotsCache = null;
    }

    private <STACK, ITEM, CAP> void gatherSlotsForCap(
            ProgramContext context,
            ResourceType<STACK, ITEM, CAP> type,
            Label label,
            BlockPos pos,
            Direction direction,
            CAP capability,
            List<InputResourceTracker<?, ?, ?>> trackers,
            Consumer<LimitedInputSlot<?, ?, ?>> acceptor
    ) {
        context
                .getLogger()
                .debug(x -> x.accept(Constants.LocalizationKeys.LOG_PROGRAM_TICK_IO_STATEMENT_GATHER_SLOTS_RANGE.get(
                        labelAccess.slots())));
        for (int slot = 0; slot < type.getSlots(capability); slot++) {
            int finalSlot = slot;
            if (labelAccess.slots().contains(slot)) {
                STACK stack = type.getStackInSlot(capability, slot);
                if (shouldCreateSlot(type, stack)) {
                    for (InputResourceTracker<?, ?, ?> tracker : trackers) {
                        if (tracker.matchesCapabilityType(capability) && tracker.test(stack)) {
                            context
                                    .getLogger()
                                    .debug(x -> x.accept(Constants.LocalizationKeys.LOG_PROGRAM_TICK_IO_STATEMENT_GATHER_SLOTS_SLOT_CREATED.get(
                                            finalSlot,
                                            stack,
                                            tracker.toString()
                                    )));
                            //noinspection unchecked
                            acceptor.accept(LimitedInputSlotObjectPool.acquire(
                                    label, pos, direction, slot, capability,
                                    (InputResourceTracker<STACK, ITEM, CAP>) tracker,
                                    stack
                            ));
                        }
                    }
                } else {
                    context
                            .getLogger()
                            .debug(x -> x.accept(LOG_PROGRAM_TICK_IO_STATEMENT_GATHER_SLOTS_SLOT_SHOULD_NOT_CREATE.get(
                                    finalSlot,
                                    stack
                            )));
                }
            } else {
                context
                        .getLogger()
                        .debug(x -> x.accept(LOG_PROGRAM_TICK_IO_STATEMENT_GATHER_SLOTS_SLOT_NOT_IN_RANGE.get(finalSlot)));
            }
        }
    }

    private <STACK, ITEM, CAP> boolean shouldCreateSlot(
            ResourceType<STACK, ITEM, CAP> type,
            STACK stack
    ) {
        // make sure there are items to move
        return !type.isEmpty(stack);
    }

}
