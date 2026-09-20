package ca.teamdman.sfm.common.program;

import ca.teamdman.sfm.common.resourcetype.ResourceType;
import ca.teamdman.sfml.ast.Label;
import ca.teamdman.sfml.ast.Limit;
import ca.teamdman.sfml.ast.OutputStatement;
import ca.teamdman.sfml.ast.ResourceIdSet;
import ca.teamdman.sfml.ast.ResourceIdentifier;
import ca.teamdman.sfml.ast.ResourceLimit;
import ca.teamdman.sfml.ast.ResourceQuantity;
import ca.teamdman.sfml.ast.With;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProgramResourceObserverTests {
    static {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private final GeneratedItemProgramInputSourceTests.TestItemResourceType itemResourceType =
            new GeneratedItemProgramInputSourceTests.TestItemResourceType();

    @Test
    void observationRespectsLimitsAndRetentionWithoutSpendingOutputBudget() {
        ProgramContext context = context();
        ItemStackHandler sourceHandler = handlerWithPaper(8);
        IInputResourceTracker tracker = resourceLimit(3, 2).createInputTracker(ResourceIdSet.EMPTY);
        LimitedInputSlot<ItemStack, Item, IItemHandler> input = inputSlot(sourceHandler, tracker, BlockPos.ZERO);
        context.addInput(new FixedSlotsInputSource(List.of(input)));

        List<ProgramResourceObservation> first = ProgramResourceObserver.observe(context, (type, stack) -> true);
        List<ProgramResourceObservation> second = ProgramResourceObserver.observe(context, (type, stack) -> true);

        assertEquals(1, first.size());
        assertEquals(3, first.get(0).amount());
        assertEquals(3, second.get(0).amount());
        assertEquals(8, sourceHandler.getStackInSlot(0).getCount());
        assertEquals(3, tracker.getMaxTransferable(itemResourceType, sourceHandler.getStackInSlot(0)));
        assertEquals(2, tracker.getRemainingRetentionObligation(itemResourceType, sourceHandler.getStackInSlot(0)));

        ItemStackHandler destinationHandler = new ItemStackHandler(1);
        OutputStatement.moveTo(context, input, outputSlot(destinationHandler));

        assertEquals(3, destinationHandler.getStackInSlot(0).getCount());
        assertEquals(5, sourceHandler.getStackInSlot(0).getCount());
        assertTrue(ProgramResourceObserver.observe(context, (type, stack) -> true).isEmpty());
        context.free();
    }

    @Test
    void duplicateHandlesDoNotDuplicatePhysicalUnitsButEqualStacksInDifferentHandlersRemainDistinct() {
        ProgramContext context = context();
        ItemStackHandler firstHandler = handlerWithPaper(4);
        ItemStackHandler secondHandler = handlerWithPaper(4);
        LimitedInputSlot<ItemStack, Item, IItemHandler> firstMatch = inputSlot(
                firstHandler,
                resourceLimit(2, 0).createInputTracker(ResourceIdSet.EMPTY),
                BlockPos.ZERO
        );
        LimitedInputSlot<ItemStack, Item, IItemHandler> overlappingMatch = inputSlot(
                firstHandler,
                resourceLimit(3, 0).createInputTracker(ResourceIdSet.EMPTY),
                BlockPos.ZERO
        );
        LimitedInputSlot<ItemStack, Item, IItemHandler> equalDistinctStack = inputSlot(
                secondHandler,
                resourceLimit(3, 0).createInputTracker(ResourceIdSet.EMPTY),
                new BlockPos(1, 1, 1)
        );
        context.addInput(new FixedSlotsInputSource(List.of(firstMatch, overlappingMatch, equalDistinctStack)));

        List<ProgramResourceObservation> observations = ProgramResourceObserver.observe(
                context,
                (type, stack) -> true
        );

        assertEquals(2, observations.size());
        assertEquals(List.of(3L, 3L), observations.stream().map(ProgramResourceObservation::amount).toList());
        assertEquals(4, firstHandler.getStackInSlot(0).getCount());
        assertEquals(4, secondHandler.getStackInSlot(0).getCount());
        context.free();
    }

    @Test
    void rejectedResourcesDoNotSpendTheObservationQuantity() {
        ProgramContext context = context();
        ItemStackHandler sourceHandler = new ItemStackHandler(2);
        sourceHandler.setStackInSlot(0, new ItemStack(Items.BOOK, 1));
        sourceHandler.setStackInSlot(1, new ItemStack(Items.PAPER, 1));
        IInputResourceTracker sharedTracker = resourceLimit(1, 0).createInputTracker(ResourceIdSet.EMPTY);
        LimitedInputSlot<ItemStack, Item, IItemHandler> ignoredBook = inputSlot(
                sourceHandler,
                sharedTracker,
                BlockPos.ZERO,
                0
        );
        LimitedInputSlot<ItemStack, Item, IItemHandler> selectedPaper = inputSlot(
                sourceHandler,
                sharedTracker,
                BlockPos.ZERO,
                1
        );
        context.addInput(new FixedSlotsInputSource(List.of(ignoredBook, selectedPaper)));

        List<ProgramResourceObservation> observations = ProgramResourceObserver.observe(
                context,
                (type, stack) -> ((ItemStack) stack).getItem() == Items.PAPER
        );

        assertEquals(1, observations.size());
        assertEquals(1, observations.get(0).amount());
        assertEquals(Items.PAPER, ((ItemStack) observations.get(0).stack()).getItem());
        assertEquals(1, sharedTracker.getMaxTransferable(itemResourceType, sourceHandler.getStackInSlot(1)));
        context.free();
    }

    @Test
    void expandedQuantityKeepsIndependentPerItemObservationBudgets() {
        ResourceQuantity.IdExpansionBehaviour expanded = ResourceQuantity.IdExpansionBehaviour.EXPAND;
        ResourceQuantity.IdExpansionBehaviour shared = ResourceQuantity.IdExpansionBehaviour.NO_EXPAND;

        assertEquals(List.of(2L), observePaperAndBook(resourceLimit(2, shared, 0, shared)));
        assertEquals(List.of(2L, 2L), observePaperAndBook(resourceLimit(2, expanded, 0, shared)));
    }

    @Test
    void expandedRetentionKeepsIndependentPerItemObservationObligations() {
        ResourceQuantity.IdExpansionBehaviour expanded = ResourceQuantity.IdExpansionBehaviour.EXPAND;
        ResourceQuantity.IdExpansionBehaviour shared = ResourceQuantity.IdExpansionBehaviour.NO_EXPAND;

        assertEquals(List.of(2L), observeOnePaperAndThreeBooks(resourceLimit(4, shared, 2, shared)));
        assertEquals(List.of(1L), observeOnePaperAndThreeBooks(resourceLimit(4, shared, 2, expanded)));
    }

    private ProgramContext context() {
        return ProgramContext.createDetachedTestContext(null, new ExecuteProgramBehaviour());
    }

    private ResourceLimit resourceLimit(long quantity, long retention) {
        ResourceQuantity.IdExpansionBehaviour shared = ResourceQuantity.IdExpansionBehaviour.NO_EXPAND;
        return resourceLimit(quantity, shared, retention, shared);
    }

    private ResourceLimit resourceLimit(
            long quantity,
            ResourceQuantity.IdExpansionBehaviour quantityExpansion,
            long retention,
            ResourceQuantity.IdExpansionBehaviour retentionExpansion
    ) {
        ResourceIdentifier<ItemStack, Item, IItemHandler> paper = identifier("paper");
        ResourceIdentifier<ItemStack, Item, IItemHandler> book = identifier("book");
        return new ResourceLimit(
                new ResourceIdSet(List.of(paper, book)),
                new Limit(
                        new ResourceQuantity(new ca.teamdman.sfml.ast.Number(quantity), quantityExpansion),
                        new ResourceQuantity(new ca.teamdman.sfml.ast.Number(retention), retentionExpansion)
                ),
                With.ALWAYS_TRUE
        );
    }

    private ResourceIdentifier<ItemStack, Item, IItemHandler> identifier(String itemName) {
        ResourceIdentifier<ItemStack, Item, IItemHandler> identifier = new ResourceIdentifier<>(
                "sfm",
                "item",
                "minecraft",
                itemName
        );
        identifier.setResourceTypeCache(itemResourceType);
        return identifier;
    }

    private List<Long> observePaperAndBook(ResourceLimit limit) {
        return observeTwoSlots(limit, new ItemStack(Items.PAPER, 3), new ItemStack(Items.BOOK, 3));
    }

    private List<Long> observeOnePaperAndThreeBooks(ResourceLimit limit) {
        return observeTwoSlots(limit, new ItemStack(Items.PAPER, 1), new ItemStack(Items.BOOK, 3));
    }

    private List<Long> observeTwoSlots(
            ResourceLimit limit,
            ItemStack first,
            ItemStack second
    ) {
        ProgramContext context = context();
        ItemStackHandler handler = new ItemStackHandler(2);
        handler.setStackInSlot(0, first);
        handler.setStackInSlot(1, second);
        IInputResourceTracker tracker = limit.createInputTracker(ResourceIdSet.EMPTY);
        context.addInput(new FixedSlotsInputSource(List.of(
                inputSlot(handler, tracker, BlockPos.ZERO, 0),
                inputSlot(handler, tracker, BlockPos.ZERO, 1)
        )));

        List<Long> amounts = ProgramResourceObserver.observe(context, (type, stack) -> true)
                .stream()
                .map(ProgramResourceObservation::amount)
                .toList();
        context.free();
        return amounts;
    }

    private LimitedInputSlot<ItemStack, Item, IItemHandler> inputSlot(
            ItemStackHandler handler,
            IInputResourceTracker tracker,
            BlockPos position
    ) {
        return inputSlot(handler, tracker, position, 0);
    }

    private LimitedInputSlot<ItemStack, Item, IItemHandler> inputSlot(
            ItemStackHandler handler,
            IInputResourceTracker tracker,
            BlockPos position,
            int slot
    ) {
        return LimitedInputSlotObjectPool.acquire(
                new Label("source"),
                position,
                null,
                slot,
                handler,
                tracker,
                handler.getStackInSlot(slot),
                itemResourceType
        );
    }

    private LimitedOutputSlot<ItemStack, Item, IItemHandler> outputSlot(ItemStackHandler handler) {
        return new LimitedOutputSlot<>(
                new Label("destination"),
                BlockPos.ZERO,
                null,
                0,
                handler,
                new GeneratedItemProgramInputSourceTests.AcceptAllOutputTracker(),
                ItemStack.EMPTY,
                itemResourceType
        );
    }

    private static ItemStackHandler handlerWithPaper(int count) {
        ItemStackHandler handler = new ItemStackHandler(1);
        handler.setStackInSlot(0, new ItemStack(Items.PAPER, count));
        return handler;
    }

    private static final class FixedSlotsInputSource implements ProgramInputSource {
        private final List<LimitedInputSlot<?, ?, ?>> slots;

        private FixedSlotsInputSource(List<LimitedInputSlot<?, ?, ?>> slots) {
            this.slots = new ArrayList<>(slots);
        }

        @Override
        public void gatherSlots(
                ProgramContext context,
                Consumer<LimitedInputSlot<?, ?, ?>> slotConsumer
        ) {
            slots.forEach(slotConsumer);
        }

        @Override
        public @Nullable ProgramInputSource forget(
                ProgramContext context,
                ProgramInputForgetRequest request
        ) {
            return request.allInputs() ? null : this;
        }

        @Override
        public void free() {
            LimitedInputSlotObjectPool.release(slots);
            slots.clear();
        }
    }
}
