package ca.teamdman.sfml.ast;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.localization.LocalizationKeys;
import ca.teamdman.sfm.common.program.*;
import ca.teamdman.sfm.common.registry.SFMResourceTypes;
import ca.teamdman.sfm.common.resourcetype.ResourceType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static ca.teamdman.sfm.common.localization.LocalizationKeys.*;

public class OutputStatement implements IOStatement {
    private final LabelAccess labelAccess;
    private final ResourceLimits resourceLimits;
    private final boolean each;

    private int lastInputCapacity = 32;
    private int lastOutputCapacity = 32;

    public OutputStatement(
            LabelAccess labelAccess,
            ResourceLimits resourceLimits,
            boolean each
    ) {
        this.labelAccess = labelAccess;
        this.resourceLimits = resourceLimits;
        this.each = each;
    }

    /**
     * Juicy method function here.
     * Given two slots, move as much as possible from one to the other.
     *
     * @param <STACK>     the stack type
     * @param <ITEM>      the item type
     * @param <CAP>       the capability type
     * @param context     program execution context
     * @param source      The slot to pull from
     * @param destination the slot to push to
     */
    public static <STACK, ITEM, CAP> void moveTo(
            ProgramContext context,
            LimitedInputSlot<STACK, ITEM, CAP> source,
            LimitedOutputSlot<STACK, ITEM, CAP> destination
    ) {
        context.getLogger().trace(x -> x.accept(LOG_PROGRAM_TICK_IO_STATEMENT_MOVE_TO_BEGIN.get(source, destination)));
        // always ensure types match
        // items and fluids are incompatible, etc
        if (!source.type.equals(destination.type)) {
            context.getLogger().trace(x -> x.accept(LOG_PROGRAM_TICK_IO_STATEMENT_MOVE_TO_TYPE_MISMATCH.get()));
            return;
        }
        ResourceType<STACK, ITEM, CAP> resourceType = source.type;


        // find out what we can pull out
        // should never be empty by the time we get here
        STACK potential = source.peekExtractPotential();
        // ensure the output slot allows this item
        if (!destination.tracker.matchesStack(potential)) {
            context
                    .getLogger()
                    .trace(x -> x.accept(LOG_PROGRAM_TICK_IO_STATEMENT_MOVE_TO_DESTINATION_TRACKER_REJECT.get()));
            return;
        }
        // find out how much we can fit
        STACK potentialRemainder = destination.insert(potential, true);

        // how many can we move before accounting for limits
        long toMove = source.type.getAmountDifference(potential, potentialRemainder);
        if (toMove <= 0) {
            context
                    .getLogger()
                    .trace(x -> x.accept(LOG_PROGRAM_TICK_IO_STATEMENT_MOVE_TO_ZERO_SIMULATED_MOVEMENT.get(
                            potentialRemainder,
                            potential
                    )));
            return;
        }

        // how many have we promised to RETAIN in this slot
        long promised_to_leave_in_this_slot = source.tracker.getRetentionObligationForSlot(
                resourceType,
                potential,
                source.pos,
                source.slot
        );
        toMove -= promised_to_leave_in_this_slot;
        // how many more need we are obligated to leave to satisfy the remainder of the RETAIN limit
        long remainingObligation = source.tracker.getRemainingRetentionObligation(resourceType, potential);
        remainingObligation = Long.min(toMove, remainingObligation);
        toMove -= remainingObligation;

        // update the obligation tracker
        if (remainingObligation > 0) {
            source.tracker.trackRetentionObligation(
                    resourceType,
                    potential,
                    source.slot,
                    source.pos,
                    remainingObligation
            );
        }

        long logRemainingObligation = remainingObligation;
        context
                .getLogger()
                .trace(x -> x.accept(LOG_PROGRAM_TICK_IO_STATEMENT_MOVE_TO_RETENTION_OBLIGATION.get(
                        promised_to_leave_in_this_slot,
                        logRemainingObligation
                )));

        // if we can't move anything after our retention obligations, we're done
        if (toMove <= 0) {
            context
                    .getLogger()
                    .trace(x -> x.accept(LOG_PROGRAM_TICK_IO_STATEMENT_MOVE_TO_RETENTION_OBLIGATION_NO_MOVE.get()));
            source.setDone();
            return;
        }

        // apply output constraints
        long destinationMaxTransferable = destination.tracker.getMaxTransferable(resourceType, potential);
        toMove = Math.min(toMove, destinationMaxTransferable);

        // apply input constraints
        long sourceMaxTransferable = source.tracker.getMaxTransferable(resourceType, potential);
        toMove = Math.min(toMove, sourceMaxTransferable);

        // apply resource constraints
        long maxStackSize = resourceType.getMaxStackSize(potential); // this is cap-agnostic, so source/dest doesn't matter
        toMove = Math.min(toMove, maxStackSize);

        long logToMove = toMove;
        context
                .getLogger()
                .trace(x -> x.accept(LOG_PROGRAM_TICK_IO_STATEMENT_MOVE_TO_STACK_LIMIT_NEW_TO_MOVE.get(
                        destinationMaxTransferable,
                        sourceMaxTransferable,
                        maxStackSize,
                        logToMove
                )));
        if (toMove <= 0) {
            context.getLogger().trace(x -> x.accept(LOG_PROGRAM_TICK_IO_STATEMENT_MOVE_TO_ZERO_TO_MOVE.get()));
            return;
        }

        // extract item for real
        STACK extracted = source.extract(toMove);
        context
                .getLogger()
                .debug(x -> x.accept(LOG_PROGRAM_TICK_IO_STATEMENT_MOVE_TO_EXTRACTED.get(extracted, source)));

        // insert item for real
        STACK extractedRemainder = destination.insert(extracted, false);

        // track transfer amounts
        var moved = resourceType.getAmountDifference(extracted, extractedRemainder);
        source.tracker.trackTransfer(resourceType, extracted, moved);
        destination.tracker.trackTransfer(resourceType, extracted, moved);

        // log
        context
                .getLogger()
                .info(x -> x.accept(LOG_PROGRAM_TICK_IO_STATEMENT_MOVE_TO_END.get(
                        moved,
                        destination.type.getRegistryKey(
                                extracted),
                        source,
                        destination
                )));

        // If remainder exists, someone lied.
        // THIS SHOULD NEVER HAPPEN
        // will void items if it does
        if (!destination.type.isEmpty(extractedRemainder)) {
            context.getLogger().error(x -> x.accept(LOG_PROGRAM_VOIDED_RESOURCES.get(
                    potential,
                    SFMResourceTypes.DEFERRED_TYPES.getKey(source.type),
                    destination.type.getRegistryKey(potential),
                    extracted,
                    extractedRemainder
            )));
            SFM.LOGGER.error(
                    "!!!RESOURCE LOSS HAS OCCURRED!!! Manager at {} in {} failed to move all promised items, found {} {}:{}, took {} but had {} left over after insertion.",
                    context.getManager().getBlockPos(),
                    context.getManager().getLevel(),
                    potential,
                    SFMResourceTypes.DEFERRED_TYPES.getKey(source.type),
                    destination.type.getRegistryKey(potential),
                    extracted,
                    extractedRemainder
            );
        }
    }

    /**
     * Input slots are freed when the input statement falls out of scope, see: {@link InputStatement#freeSlots()}
     * <p/>
     * Output slots are freed immediately once done in this method.
     */
    @Override
    public void tick(ProgramContext context) {
        // Log the output statement
        context
                .getLogger()
                .debug(x -> x.accept(LocalizationKeys.LOG_PROGRAM_TICK_OUTPUT_STATEMENT.get(this.toString())));

        // Skip if simulating
        if (context.getBehaviour() instanceof SimulateExploreAllPathsProgramBehaviour behaviour) {
            behaviour.onOutputStatementExecution(context, this);
            return;
        }


        /* ################
             INPUT SLOTS
           ################ */

        // gather the input slots from all the input statements, +27 to hopefully avoid resizing
        //noinspection rawtypes
        ArrayDeque<LimitedInputSlot> inputSlots = new ArrayDeque<>(lastInputCapacity + 27);
        for (var inputStatement : context.getInputs()) {
            inputStatement.gatherSlots(context, inputSlots::add);
        }

        // Update allocation hint
        lastInputCapacity = inputSlots.size();

        // Log the number of input slots
        context
                .getLogger()
                .info(x -> x.accept(LOG_PROGRAM_TICK_OUTPUT_STATEMENT_DISCOVERED_INPUT_SLOT_COUNT.get(inputSlots.size())));

        // Short-circuit if we have nothing to move
        if (inputSlots.isEmpty()) {
            // Log the short-circuit
            context
                    .getLogger()
                    .debug(x -> x.accept(LOG_PROGRAM_TICK_OUTPUT_STATEMENT_SHORT_CIRCUIT_NO_INPUT_SLOTS.get()));

            // Stop processing
            return;
        }

        /* ################
             OUTPUT SLOTS
           ################ */

        // collect the output slots, +27 to hopefully avoid resizing
        //noinspection rawtypes
        ArrayDeque<LimitedOutputSlot> outputSlots = new ArrayDeque<>(lastOutputCapacity + 27);
        gatherSlots(context, outputSlots::add);

        // Update allocation hint
        lastOutputCapacity = outputSlots.size();

        // Log the number of output slots
        context
                .getLogger()
                .info(x -> x.accept(LOG_PROGRAM_TICK_OUTPUT_STATEMENT_DISCOVERED_OUTPUT_SLOT_COUNT.get(outputSlots.size())));

        // Short-circuit if we have nothing to move
        if (outputSlots.isEmpty()) {
            // Log the short-circuit
            context
                    .getLogger()
                    .debug(x -> x.accept(LOG_PROGRAM_TICK_OUTPUT_STATEMENT_SHORT_CIRCUIT_NO_OUTPUT_SLOTS.get()));

            // Free the output slots (we acquired no slots but the assertion is still valid)
            LimitedOutputSlotObjectPool.release(outputSlots);

            // Stop processing
            return;
        }


        /* ################
                 MOVE
           ################ */

        // try and move resources from input slots to output slots
        for (var inputSlot : inputSlots) {
            // Get an input slot
            if (inputSlot.isDone()) {
                continue;
            }

            // Try to move into every output slot
            var outputSlotIter = outputSlots.iterator();
            while (outputSlotIter.hasNext()) {
                // Get an output slot
                var outputSlot = outputSlotIter.next();
                if (outputSlot.isDone()) {
                    // Make sure we don't process this slot again
                    outputSlotIter.remove(); // IMPORTANT!!!!! DONT FREE SLOTS TWICE WHEN FREEING REMAINDER BELOW
                    // Release it
                    LimitedOutputSlotObjectPool.release(outputSlot);
                    // Try again
                    continue;
                }

                // Attempt a move
                //noinspection unchecked
                moveTo(context, inputSlot, outputSlot);

                // Continue to the next input slot when the current one is finished
                if (inputSlot.isDone()) break;
            }
            // Stop processing when no output slots are left
            if (outputSlots.isEmpty()) break;
        }


        /* ################
                FINISH
           ################ */

        // Release remaining slot objects
        LimitedOutputSlotObjectPool.release(outputSlots);
    }

    /**
     * The output statement contains labels.
     * Each block in the world can have more than one programString.
     * Each block can have a block entity.
     * Each block entity can have 0 or more slots.
     * <p>
     * We want collect the slots from all the labelled blocks.
     */
    @SuppressWarnings({"rawtypes", "unchecked"}) // basically impossible to make this method generic safe
    public void gatherSlots(
            ProgramContext context,
            Consumer<LimitedOutputSlot<?, ?, ?>> slotConsumer
    ) {
        context.getLogger().debug(x -> x.accept(LOG_PROGRAM_TICK_IO_STATEMENT_GATHER_SLOTS.get(toStringPretty())));

        if (!each) {
            context.getLogger().debug(x -> x.accept(LOG_PROGRAM_TICK_IO_STATEMENT_GATHER_SLOTS_NOT_EACH.get()));
            // create a single list of trackers to be shared between all limited slots
            List<IOutputResourceTracker> outputTracker = resourceLimits.createOutputTrackers();
            for (var resourceType : resourceLimits.getReferencedResourceTypes()) {
                context
                        .getLogger()
                        .debug(x -> x.accept(LOG_PROGRAM_TICK_IO_STATEMENT_GATHER_SLOTS_FOR_RESOURCE_TYPE.get(
                                resourceType.displayAsCapabilityClass(),
                                resourceType.displayAsCapabilityClass()
                        )));
                resourceType.forEachCapability(context, labelAccess, (
                        (label, pos, direction, cap, count) -> gatherSlotsForCap(
                                context,
                                (ResourceType<Object, Object, Object>) resourceType,
                                label,
                                pos,
                                direction,
                                cap,
                                count,
                                outputTracker,
                                slotConsumer
                        )
                ));
            }
        } else {
            context.getLogger().debug(x -> x.accept(LOG_PROGRAM_TICK_IO_STATEMENT_GATHER_SLOTS_EACH.get()));
            for (var resourceType : resourceLimits.getReferencedResourceTypes()) {
                context
                        .getLogger()
                        .debug(x -> x.accept(LOG_PROGRAM_TICK_IO_STATEMENT_GATHER_SLOTS_FOR_RESOURCE_TYPE.get(
                                resourceType.displayAsCapabilityClass(),
                                resourceType.displayAsCapabilityClass()
                        )));
                resourceType.forEachCapability(context, labelAccess, (label, pos, direction, cap, count) -> {
                    // create a new list of trackers for each limited slot
                    List<IOutputResourceTracker> outputTracker = resourceLimits.createOutputTrackers();
                    gatherSlotsForCap(
                            context,
                            (ResourceType<Object, Object, Object>) resourceType,
                            label,
                            pos,
                            direction,
                            cap,
                            count,
                            outputTracker,
                            slotConsumer
                    );
                });
            }
        }
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
        var that = (OutputStatement) obj;
        return Objects.equals(this.labelAccess, that.labelAccess) && Objects.equals(
                this.resourceLimits,
                that.resourceLimits
        ) && this.each == that.each;
    }

    @Override
    public int hashCode() {
        return Objects.hash(labelAccess, resourceLimits, each);
    }

    @Override
    public String toString() {
        return "OUTPUT " + resourceLimits.toStringPretty(Limit.MAX_QUANTITY_MAX_RETENTION) + " TO " + (
                each ? "EACH " : ""
        ) + labelAccess;
    }

    @Override
    public String toStringPretty() {
        StringBuilder sb = new StringBuilder();
        sb.append("OUTPUT");
        String rls = resourceLimits.toStringPretty(Limit.MAX_QUANTITY_MAX_RETENTION);
        if (rls.lines().count() > 1) {
            sb.append("\n");
            sb.append(rls.lines().map(s -> "  " + s).collect(Collectors.joining("\n")));
            sb.append("\n");
        } else {
            sb.append(" ");
            sb.append(rls);
            sb.append(" ");
        }
        sb.append("TO ");
        sb.append(each ? "EACH " : "");
        sb.append(labelAccess);
        return sb.toString();
    }

    private <STACK, ITEM, CAP> void gatherSlotsForCap(
            ProgramContext context,
            ResourceType<STACK, ITEM, CAP> type,
            Label label,
            BlockPos pos,
            Direction direction,
            CAP capability,
            int count,
            List<IOutputResourceTracker> trackers,
            Consumer<LimitedOutputSlot<?, ?, ?>> acceptor
    ) {
        context
                .getLogger()
                .debug(x -> x.accept(LocalizationKeys.LOG_PROGRAM_TICK_IO_STATEMENT_GATHER_SLOTS_RANGE.get(labelAccess.slots())));
        for (int slot = 0; slot < type.getSlots(capability); slot++) {
            int finalSlot = slot;
            if (labelAccess.slots().contains(slot)) {
                STACK stack = type.getStackInSlot(capability, slot);
                boolean shouldCreateSlot = shouldCreateSlot(type, capability, stack, slot);
                for (IOutputResourceTracker tracker : trackers) {
                    if (tracker.matchesCapabilityType(capability)) {
                        //always update retention observations even if !shouldCreateSlot
                        tracker.updateRetentionObservation(type, stack);

                        if (shouldCreateSlot) {
                            context
                                    .getLogger()
                                    .debug(x -> x.accept(LocalizationKeys.LOG_PROGRAM_TICK_IO_STATEMENT_GATHER_SLOTS_SLOT_CREATED.get(
                                            finalSlot,
                                            stack,
                                            tracker.toString()
                                    )));
                            for (int i = 0; i < count; i++) {
                                acceptor.accept(LimitedOutputSlotObjectPool.acquire(
                                        label,
                                        pos,
                                        direction,
                                        slot,
                                        capability,
                                        tracker,
                                        stack,
                                        type
                                ));
                            }
                        } else {
                            context
                                    .getLogger()
                                    .debug(x -> x.accept(LocalizationKeys.LOG_PROGRAM_TICK_IO_STATEMENT_GATHER_SLOTS_SLOT_SHOULD_NOT_CREATE.get(
                                            finalSlot,
                                            type.getAmount(stack)
                                                    + " of "
                                                    + Math.min(type.getMaxStackSize(stack), type.getMaxStackSizeForSlot(capability, finalSlot))
                                                    + " "
                                                    + type.getItem(stack)
                                    )));
                        }
                    }
                }
            } else {
                context
                        .getLogger()
                        .debug(x -> x.accept(LocalizationKeys.LOG_PROGRAM_TICK_IO_STATEMENT_GATHER_SLOTS_SLOT_NOT_IN_RANGE.get(
                                finalSlot)));
            }
        }
    }

    @SuppressWarnings("RedundantIfStatement")
    private <STACK, ITEM, CAP> boolean shouldCreateSlot(
            ResourceType<STACK, ITEM, CAP> type,
            CAP cap,
            STACK stack,
            int slot
    ) {
        // Chest holding dirt: maxStackSizeForStack=64 maxStackSizeForSlot=99
        // Bin holding sticks: maxStackSizeForStack=64 maxStackSizeForSlot=102400
        long amount = type.getAmount(stack);
        long maxStackSizeForSlot = type.getMaxStackSizeForSlot(cap, slot);
        if (maxStackSizeForSlot > 99) {
            // If the slot is bigger than normal, ignore stack size
            // This is for barrels/bins/drawers
            return amount < maxStackSizeForSlot;
        }
        if (amount >= maxStackSizeForSlot) {
            // Respect traditional slot limits
            return false;
        }
        long maxStackSizeForStack = type.getMaxStackSize(stack);
        if (amount >= maxStackSizeForStack) {
            // Respect stack limits
            return false;
        }
        return true;
    }
}
