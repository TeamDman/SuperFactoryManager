package ca.teamdman.sfml.ast;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.config.SFMConfig;
import ca.teamdman.sfm.common.localization.LocalizationKeys;
import ca.teamdman.sfm.common.program.*;
import ca.teamdman.sfm.common.registry.SFMResourceTypes;
import ca.teamdman.sfm.common.resourcetype.ResourceType;
import ca.teamdman.sfm.common.util.Stored;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;

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
        STACK extractPotential = source.peekExtractPotential();
        long extractPotentialAmount = resourceType.getAmount(extractPotential);

        // ensure the output slot allows this item
        if (!destination.tracker.matchesStack(extractPotential)) {
            context
                    .getLogger()
                    .trace(x -> x.accept(LOG_PROGRAM_TICK_IO_STATEMENT_MOVE_TO_DESTINATION_TRACKER_REJECT.get()));
            return;
        }

        // begin counting how much we can move
        long amountAvailableToMove = extractPotentialAmount;

        // how many have we promised to RETAIN in this slot
        long promised_to_leave_in_this_slot = source.tracker.getRetentionObligationForSlot(
                resourceType,
                extractPotential,
                source.pos,
                source.slot
        );
        amountAvailableToMove -= promised_to_leave_in_this_slot;

        // how much remains until our RETAIN is satisfied
        long remainingObligation = source.tracker.getRemainingRetentionObligation(resourceType, extractPotential);

        // how much can we allocate towards satisfying the obligation
        long dedicatingToObligation = Math.min(remainingObligation, amountAvailableToMove);
        amountAvailableToMove -= dedicatingToObligation;

        // update the obligation tracker
        if (dedicatingToObligation > 0) {
            source.tracker.trackRetentionObligation(
                    resourceType,
                    extractPotential,
                    source.slot,
                    source.pos,
                    dedicatingToObligation
            );
            context
                    .getLogger()
                    .trace(x -> {
                        long newRemainingObligation = remainingObligation - dedicatingToObligation;
                        x.accept(LOG_PROGRAM_TICK_IO_STATEMENT_MOVE_TO_RETENTION_OBLIGATION.get(
                                promised_to_leave_in_this_slot,
                                newRemainingObligation
                        ));
                    });
        }

        // if we can't move anything after our retention obligations, we're done
        if (amountAvailableToMove <= 0) {
            context
                    .getLogger()
                    .trace(x -> x.accept(LOG_PROGRAM_TICK_IO_STATEMENT_MOVE_TO_RETENTION_OBLIGATION_NO_MOVE.get()));
            source.setDone();
            return;
        }

        // how many can we move before accounting for limits
        STACK potentialRemainder = destination.insert(extractPotential, true);
        long amountThatFitsInDestination = resourceType.getAmountDifference(extractPotential, potentialRemainder);
        if (amountThatFitsInDestination < 0) {
            throw new IllegalStateException(
                    "Potential insertion remainder amount exceeded the insertion amount! This should never happen. "
                    + "Tried inserting "
                    + extractPotential
                    + " into "
                    + destination
                    + " but got "
                    + potentialRemainder
                    + " remainder"
                    + " (diff="
                    + amountThatFitsInDestination
                    + ")."
            );
        } else if (amountThatFitsInDestination == 0) {
            context
                    .getLogger()
                    .trace(x -> x.accept(LOG_PROGRAM_TICK_IO_STATEMENT_MOVE_TO_ZERO_SIMULATED_MOVEMENT.get(
                            potentialRemainder,
                            extractPotential
                    )));
            return;
        }

        // we can only move as much as the destination can fit
        amountAvailableToMove = Math.min(amountThatFitsInDestination, amountAvailableToMove);

        // apply output constraints
        long destinationAmountLimit = destination.tracker.getMaxTransferable(resourceType, extractPotential);
        amountAvailableToMove = Math.min(amountAvailableToMove, destinationAmountLimit);

        // apply input constraints
        long sourceAmountLimit = source.tracker.getMaxTransferable(resourceType, extractPotential);
        amountAvailableToMove = Math.min(amountAvailableToMove, sourceAmountLimit);

        // apply stack size constraints
        long maxStackSize = resourceType.getMaxStackSize(extractPotential); // this is cap-agnostic, so source/dest doesn't matter
        amountAvailableToMove = Math.min(amountAvailableToMove, maxStackSize);
        {
            long logToMove = amountAvailableToMove;
            context
                    .getLogger()
                    .trace(x -> x.accept(LOG_PROGRAM_TICK_IO_STATEMENT_MOVE_TO_STACK_LIMIT_NEW_TO_MOVE.get(
                            destinationAmountLimit,
                            sourceAmountLimit,
                            maxStackSize,
                            logToMove
                    )));
        }

        // check if we can move anything at all
        if (amountAvailableToMove <= 0) {
            context.getLogger().trace(x -> x.accept(LOG_PROGRAM_TICK_IO_STATEMENT_MOVE_TO_ZERO_TO_MOVE.get()));
            return;
        }

        // extract item for real
        STACK extracted = source.extract(amountAvailableToMove);
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
                        resourceType.getRegistryKeyForStack(extracted),
                        source,
                        destination
                )));

        // If remainder exists, someone lied.
        // THIS SHOULD NEVER HAPPEN
        // will void items if it does
        if (!resourceType.isEmpty(extractedRemainder)) {
            ResourceLocation resourceTypeName = SFMResourceTypes.registry().getKey(resourceType);
            String stackName = resourceType.getItem(extractPotential).toString();
            Level level = context.getManager().getLevel();
            assert level != null;
            StringBuilder report = new StringBuilder();
            report.append("!!!RESOURCE LOSS HAS OCCURRED!!!");
            String currentLine = Thread.currentThread().getStackTrace()[1].toString();
            report.append("    ").append(currentLine).append("\n");
            report.append("=== Summary ===\n");
            int width = -32;
            report
                    .append(String.format("%" + width + "s", "Simulated extraction"))
                    .append(": ")
                    .append(extractPotential)
                    .append("\n");
            report
                    .append(String.format("%" + width + "s", "Simulated insertion remainder"))
                    .append(": ")
                    .append(potentialRemainder)
                    .append(" (moved=")
                    .append(resourceType.getAmountDifference(extractPotential, potentialRemainder))
                    .append(")")
                    .append(" <-- the output block lied here\n");
            report
                    .append(String.format("%" + width + "s", "Actual extraction"))
                    .append(": ")
                    .append(extracted)
                    .append("\n");
            report
                    .append(String.format("%" + width + "s", "Actual insertion"))
                    .append(": ")
                    .append(moved)
                    .append(" ")
                    .append(stackName)
                    .append("\n");
            report.append(String.format("%" + width + "s", "Actual insertion remainder")).append(": ")
                    .append(extractedRemainder)
                    .append(" (")
                    .append(resourceTypeName)
                    .append(":")
                    .append(stackName)
                    .append(") <-- this is what was lost\n");

            report.append("=== Manager ===\n");
            report
                    .append("Level: ")
                    .append(level.dimensionTypeId().location())
                    .append(" (")
                    .append(level)
                    .append(")\n");
            report.append("Position: ").append(context.getManager().getBlockPos()).append("\n");

            report.append("=== Input Slot ===\n");
            addSlotDetailsToReport(report, source, level);

            report.append("=== Output Slot ===\n");
            addSlotDetailsToReport(report, destination, level);

            context.getLogger().error(x -> x.accept(LOG_PROGRAM_VOIDED_RESOURCES.get(report.toString())));
            if (SFMConfig.SERVER_CONFIG.logResourceLossToConsole.get()) {
                report.append("\nThis can be silenced in the SFM config.\n");
                report.append(
                        "Operators can use `/sfm config edit` to open a GUI to change the SFM config while the game is running.\n");
                report.append(
                        "This can be caused by output inventory logic encountering an integer overflow when moving large quantities of items.\n");
                report
                        .append("The SFM issue tracker can be found at ")
                        .append(SFM.ISSUE_TRACKER_URL)
                        .append(" because this shouldn't be happening lol");
                SFM.LOGGER.error(report.toString());
            }
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
     * Each block in the world can have more than one label.
     * Each block can have a block entity.
     * Each block entity can have 0 or more slots.
     * <p>
     * We want to collect the slots from all the labelled blocks.
     */
    @SuppressWarnings({"unchecked"}) // basically impossible to make this method generic safe
    public void gatherSlots(
            ProgramContext context,
            Consumer<LimitedOutputSlot<?, ?, ?>> slotConsumer
    ) {
        context.getLogger().debug(x -> x.accept(LOG_PROGRAM_TICK_IO_STATEMENT_GATHER_SLOTS.get(toStringPretty())));

        if (!each) {
            context.getLogger().debug(x -> x.accept(LOG_PROGRAM_TICK_IO_STATEMENT_GATHER_SLOTS_NOT_EACH.get()));
            // create a single list of trackers to be shared between all limited slots
            List<IOutputResourceTracker> outputTracker = resourceLimits.createOutputTrackers(context);
            for (var resourceType : resourceLimits.getReferencedResourceTypes()) {
                context
                        .getLogger()
                        .debug(x -> x.accept(LOG_PROGRAM_TICK_IO_STATEMENT_GATHER_SLOTS_FOR_RESOURCE_TYPE.get(
                                resourceType.displayAsCapabilityClass(),
                                resourceType.displayAsCapabilityClass()
                        )));
                resourceType.forEachCapability(context, labelAccess, (
                        (label, pos, direction, cap) -> gatherSlotsForCap(
                                context,
                                (ResourceType<Object, Object, Object>) resourceType,
                                label,
                                pos,
                                direction,
                                cap,
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
                resourceType.forEachCapability(context, labelAccess, (label, pos, direction, cap) -> {
                    // create a new list of trackers for each limited slot
                    List<IOutputResourceTracker> outputTracker = resourceLimits.createOutputTrackers(context);
                    gatherSlotsForCap(
                            context,
                            (ResourceType<Object, Object, Object>) resourceType,
                            label,
                            pos,
                            direction,
                            cap,
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
        return "OUTPUT " + resourceLimits.toStringCondensed(Limit.MAX_QUANTITY_MAX_RETENTION) + " TO " + (
                each ? "EACH " : ""
        ) + labelAccess;
    }

    @Override
    public String toStringPretty() {
        StringBuilder sb = new StringBuilder();
        sb.append("OUTPUT");
        String rls = resourceLimits.toStringCondensed(Limit.MAX_QUANTITY_MAX_RETENTION);
        if (rls.lines().count() > 1) {
            sb.append("\n");
            sb.append(rls.lines().map(s -> "  " + s).collect(Collectors.joining("\n")));
            sb.append("\n");
        } else if (!rls.isEmpty()) {
            sb.append(" ");
            sb.append(rls);
            sb.append(" ");
        } else {
            sb.append(" ");
        }
        sb.append("TO ");
        sb.append(each ? "EACH " : "");
        sb.append(labelAccess);
        return sb.toString();
    }

    private static <STACK, ITEM, CAP> void addSlotDetailsToReport(
            StringBuilder report,
            LimitedSlot<STACK, ITEM, CAP> slot,
            Level level
    ) {
        report.append("Slot: ").append(slot.getSlot()).append("\n");
        report.append("Position: ").append(slot.getPos()).append("\n");
        report.append("Direction: ").append(slot.getDirection()).append("\n");
        report
                .append("Capability: ")
                .append(slot.getHandler())
                .append(" (")
                .append(slot.getHandler().getClass().getName())
                .append(")\n");
        BlockEntity inputBlockEntity = level.getBlockEntity(slot.getPos());
        if (inputBlockEntity != null) {
            ResourceLocation inputBlockEntityType = ForgeRegistries.BLOCK_ENTITY_TYPES.getKey(inputBlockEntity.getType());
            report
                    .append("Block Entity: ")
                    .append(inputBlockEntity.getClass().getName())
                    .append(" (")
                    .append(inputBlockEntityType)
                    .append(")\n");
        } else {
            report.append("Block Entity: null\n");
        }
        BlockState blockState = level.getBlockState(slot.getPos());
        ResourceLocation blockType = ForgeRegistries.BLOCKS.getKey(blockState.getBlock());
        report
                .append("Block: ")
                .append(blockState.getBlock().getClass().getName())
                .append(" (")
                .append(blockType)
                .append(")\n");
        report.append("Block State: ").append(blockState).append("\n");
    }

    private <STACK, ITEM, CAP> void gatherSlotsForCap(
            ProgramContext context,
            ResourceType<STACK, ITEM, CAP> type,
            Label label,
            @Stored BlockPos pos,
            Direction direction,
            CAP capability,
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
                        } else {
                            context
                                    .getLogger()
                                    .debug(x -> x.accept(LocalizationKeys.LOG_PROGRAM_TICK_IO_STATEMENT_GATHER_SLOTS_SLOT_SHOULD_NOT_CREATE.get(
                                            finalSlot,
                                            type.getAmount(stack)
                                            + " of "
                                            + type.getMaxStackSizeForSlot(capability, finalSlot)
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

    private <STACK, ITEM, CAP> boolean shouldCreateSlot(
            ResourceType<STACK, ITEM, CAP> type,
            CAP cap,
            STACK stack,
            int slot
    ) {
        // we check the stack limit on the capability
        // this is to accommodate drawers/bins/barrels/black hole units/whatever
        // those blocks hold many more items than normal in a single stack
        // we don't also test the tracker because we can deposit into empty slots
        return type.getAmount(stack) < type.getMaxStackSizeForSlot(cap, slot);
    }
}
