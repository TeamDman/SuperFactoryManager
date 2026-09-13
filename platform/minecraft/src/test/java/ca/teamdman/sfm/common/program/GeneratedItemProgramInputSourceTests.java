package ca.teamdman.sfm.common.program;

import ca.teamdman.sfm.common.label.LabelPositionHolder;
import ca.teamdman.sfm.common.blockentity.BufferBlockEntityContents;
import ca.teamdman.sfm.common.resourcetype.ResourceType;
import ca.teamdman.sfm.common.value.SFMValue;
import ca.teamdman.sfml.ast.ForgetStatement;
import ca.teamdman.sfml.ast.Label;
import ca.teamdman.sfml.ast.OutputStatement;
import ca.teamdman.sfml.ast.ResourceIdSet;
import ca.teamdman.sfml.ast.ResourceLimit;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeneratedItemProgramInputSourceTests {
    static {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private final TestItemResourceType itemResourceType = new TestItemResourceType();

    @Test
    void valueDemandMaterializesConstructorAndCarrierOnlyOnce() {
        ProgramContext context = context();
        AtomicInteger constructorCalls = new AtomicInteger();
        SFMValue expected = SFMValue.object(java.util.Map.of("job", SFMValue.of("same")));
        GeneratedItemProgramInputSource source = source(constructorCalls, expected, "create input sfm:packet row 1");

        assertFalse(source.isMaterialized());
        assertEquals(0, constructorCalls.get());

        SFMValue firstValue = source.value(context.getEphemeralResourceOwner());
        SFMValue secondValue = source.value(context.getEphemeralResourceOwner());
        LimitedInputSlot<ItemStack, Item, IItemHandler> firstSlot = gatherOne(source, context);
        LimitedInputSlot<ItemStack, Item, IItemHandler> secondSlot = gatherOne(source, context);

        assertSame(firstValue, secondValue);
        assertSame(firstSlot, secondSlot);
        assertSame(Items.PAPER, firstSlot.peekStackInSlot().getItem());
        assertEquals(1, constructorCalls.get());
        assertEquals(1, context.getEphemeralResourceOwner().size());
        assertTrue(firstSlot.isGeneratedSource());
        assertEquals("create input sfm:packet row 1", firstSlot.getGeneratedSourceDescription());

        context.free();
        assertTrue(firstSlot.getHandler().getStackInSlot(0).isEmpty());
    }

    @Test
    void resourceDemandFirstUsesTheSameMemoizedValue() {
        ProgramContext context = context();
        AtomicInteger constructorCalls = new AtomicInteger();
        SFMValue expected = SFMValue.of("resource-first");
        GeneratedItemProgramInputSource source = source(constructorCalls, expected, "resource-first occurrence");

        LimitedInputSlot<ItemStack, Item, IItemHandler> slot = gatherOne(source, context);
        SFMValue laterValue = source.value(context.getEphemeralResourceOwner());

        assertSame(expected, laterValue);
        assertSame(Items.PAPER, slot.peekStackInSlot().getItem());
        assertEquals(1, constructorCalls.get());

        context.free();
    }

    @Test
    void equalOccurrencesRemainIndependentAndAccumulate() {
        ProgramContext context = context();
        SFMValue equalValue = SFMValue.of("equal");
        GeneratedItemProgramInputSource first = source(new AtomicInteger(), equalValue, "row 1");
        GeneratedItemProgramInputSource second = source(new AtomicInteger(), equalValue, "row 2");
        context.addInput(first);
        context.addInput(second);

        LimitedInputSlot<ItemStack, Item, IItemHandler> firstSlot = gatherOne(first, context);
        LimitedInputSlot<ItemStack, Item, IItemHandler> secondSlot = gatherOne(second, context);

        assertNotSame(first, second);
        assertNotSame(firstSlot.getHandler(), secondSlot.getHandler());
        assertEquals(2, context.getInputs().size());
        assertEquals(2, context.getEphemeralResourceOwner().size());
        assertSame(Items.PAPER, firstSlot.peekStackInSlot().getItem());
        assertSame(Items.PAPER, secondSlot.peekStackInSlot().getItem());

        context.free();
    }

    @Test
    void ordinaryMoveDrainsAndReleasesOwnedGeneratedStorage() {
        ProgramContext context = context();
        SFMValue expected = SFMValue.object(java.util.Map.of("response", SFMValue.of(42L)));
        GeneratedItemProgramInputSource source = source(new AtomicInteger(), expected, "movable occurrence");
        assertSame(expected, source.value(context.getEphemeralResourceOwner()));
        LimitedInputSlot<ItemStack, Item, IItemHandler> input = gatherOne(source, context);
        ItemStackHandler destinationHandler = new ItemStackHandler(1);
        LimitedOutputSlot<ItemStack, Item, IItemHandler> output = new LimitedOutputSlot<>(
                new Label("destination"),
                BlockPos.ZERO,
                null,
                0,
                destinationHandler,
                new AcceptAllOutputTracker(),
                ItemStack.EMPTY,
                itemResourceType
        );

        OutputStatement.moveTo(context, input, output);

        assertSame(Items.PAPER, destinationHandler.getStackInSlot(0).getItem());
        assertTrue(input.peekStackInSlot().isEmpty());
        assertEquals(0, context.getEphemeralResourceOwner().size());

        context.free();
        assertSame(Items.PAPER, destinationHandler.getStackInSlot(0).getItem());
    }

    @Test
    void selectiveForgetRetainsGeneratedViewAndBareForgetOnlyDetachesIt() {
        ProgramContext context = context();
        GeneratedItemProgramInputSource source = source(
                new AtomicInteger(),
                SFMValue.of("forgotten"),
                "forgotten occurrence"
        );
        context.addInput(source);
        LimitedInputSlot<ItemStack, Item, IItemHandler> materializedSlot = gatherOne(source, context);

        new ForgetStatement(Set.of(new Label("world-input"))).tick(context);
        assertEquals(List.of(source), context.getInputs());
        assertEquals(1, context.getEphemeralResourceOwner().size());

        ForgetStatement.allInputsStatement().tick(context);
        assertTrue(context.getInputs().isEmpty());
        assertEquals(1, context.getEphemeralResourceOwner().size());
        assertFalse(materializedSlot.getHandler().getStackInSlot(0).isEmpty());

        context.free();
        assertTrue(materializedSlot.getHandler().getStackInSlot(0).isEmpty());
    }

    @Test
    void failedConstructionIsMemoizedInsteadOfRepeatingSideEffects() {
        ProgramContext context = context();
        AtomicInteger constructorCalls = new AtomicInteger();
        IllegalStateException failure = new IllegalStateException("construction failed");
        GeneratedItemProgramInputSource source = new GeneratedItemProgramInputSource(
                "failing occurrence",
                () -> {
                    constructorCalls.incrementAndGet();
                    throw failure;
                },
                this::testStack,
                () -> itemResourceType
        );

        assertSame(failure, assertThrows(
                IllegalStateException.class,
                () -> source.value(context.getEphemeralResourceOwner())
        ));
        assertSame(failure, assertThrows(
                IllegalStateException.class,
                () -> source.value(context.getEphemeralResourceOwner())
        ));
        assertEquals(1, constructorCalls.get());
        assertEquals(0, context.getEphemeralResourceOwner().size());

        context.free();
    }

    private GeneratedItemProgramInputSource source(
            AtomicInteger constructorCalls,
            SFMValue value,
            String description
    ) {
        return new GeneratedItemProgramInputSource(
                description,
                () -> {
                    constructorCalls.incrementAndGet();
                    return value;
                },
                this::testStack,
                () -> itemResourceType
        );
    }

    private ItemStack testStack(SFMValue value) {
        return new ItemStack(Items.PAPER);
    }

    @SuppressWarnings("unchecked")
    private static LimitedInputSlot<ItemStack, Item, IItemHandler> gatherOne(
            GeneratedItemProgramInputSource source,
            ProgramContext context
    ) {
        List<LimitedInputSlot<?, ?, ?>> gathered = new ArrayList<>();
        source.gatherSlots(context, gathered::add);
        assertEquals(1, gathered.size());
        return (LimitedInputSlot<ItemStack, Item, IItemHandler>) gathered.get(0);
    }

    private static ProgramContext context() {
        return ProgramContext.createSimulationContext(
                null,
                LabelPositionHolder.empty(),
                0,
                new SimulateExploreAllPathsProgramBehaviour()
        );
    }

    private static final class AcceptAllOutputTracker implements IOutputResourceTracker {
        private long transferred;

        @Override
        public ResourceLimit getResourceLimit() {
            return ResourceLimit.ACCEPT_ALL_WITHOUT_RESTRAINT;
        }

        @Override
        public ResourceIdSet getExclusions() {
            return ResourceIdSet.EMPTY;
        }

        @Override
        public <STACK, CAP, ITEM> boolean isDone(
                ResourceType<STACK, ITEM, CAP> type,
                STACK stack
        ) {
            return false;
        }

        @Override
        public <STACK, ITEM, CAP> void updateRetentionObservation(
                ResourceType<STACK, ITEM, CAP> type,
                STACK observed
        ) {
        }

        @Override
        public <STACK, ITEM, CAP> void trackTransfer(
                ResourceType<STACK, ITEM, CAP> resourceType,
                STACK key,
                long amount
        ) {
            transferred += amount;
        }

        @Override
        public <STACK, ITEM, CAP> long getMaxTransferable(
                ResourceType<STACK, ITEM, CAP> resourceType,
                STACK key
        ) {
            return Long.MAX_VALUE - transferred;
        }

        @Override
        public boolean matchesStack(Object stack) {
            return true;
        }
    }

    /** Avoids Forge capability bootstrap in the plain-JUnit runner. */
    private static final class TestItemResourceType extends ResourceType<ItemStack, Item, IItemHandler> {
        private static final ResourceLocation PAPER_ID = new ResourceLocation("minecraft", "paper");

        private TestItemResourceType() {
            super(null);
        }

        @Override
        public IItemHandler createHandlerForBufferBlock(BufferBlockEntityContents contents) {
            return new ItemStackHandler(1);
        }

        @Override
        public long getAmount(ItemStack stack) {
            return stack.getCount();
        }

        @Override
        public ItemStack getStackInSlot(IItemHandler handler, int slot) {
            return handler.getStackInSlot(slot);
        }

        @Override
        public ItemStack extract(IItemHandler handler, int slot, long amount, boolean simulate) {
            return handler.extractItem(slot, (int) Math.min(Integer.MAX_VALUE, amount), simulate);
        }

        @Override
        public int getSlots(IItemHandler handler) {
            return handler.getSlots();
        }

        @Override
        public long getMaxStackSize(ItemStack stack) {
            return stack.getMaxStackSize();
        }

        @Override
        public long getMaxStackSizeForSlot(IItemHandler handler, int slot) {
            return handler.getSlotLimit(slot);
        }

        @Override
        public ItemStack insert(IItemHandler handler, int slot, ItemStack stack, boolean simulate) {
            return handler.insertItem(slot, stack, simulate);
        }

        @Override
        public boolean isEmpty(ItemStack stack) {
            return stack.isEmpty();
        }

        @Override
        public ItemStack getEmptyStack() {
            return ItemStack.EMPTY;
        }

        @Override
        public boolean matchesStackType(Object value) {
            return value instanceof ItemStack;
        }

        @Override
        public boolean matchesCapabilityHandler(Object value) {
            return value instanceof IItemHandler;
        }

        @Override
        public Stream<ResourceLocation> getTagsForStack(ItemStack stack) {
            return Stream.empty();
        }

        @Override
        public boolean registryKeyExists(ResourceLocation location) {
            return PAPER_ID.equals(location);
        }

        @Override
        public ResourceLocation getRegistryKeyForStack(ItemStack stack) {
            return PAPER_ID;
        }

        @Override
        public ResourceLocation getRegistryKeyForItem(Item item) {
            return PAPER_ID;
        }

        @Override
        public Item getItemFromRegistryKey(ResourceLocation location) {
            return PAPER_ID.equals(location) ? Items.PAPER : null;
        }

        @Override
        public Set<ResourceLocation> getRegistryKeys() {
            return Set.of(PAPER_ID);
        }

        @Override
        public Iterable<Item> getItems() {
            return List.of(Items.PAPER);
        }

        @Override
        public Item getItem(ItemStack stack) {
            return stack.getItem();
        }

        @Override
        public ItemStack copy(ItemStack stack) {
            return stack.copy();
        }

        @Override
        public String displayAsCapabilityClass() {
            return "test item handler";
        }

        @Override
        protected ItemStack setCount(ItemStack stack, long amount) {
            stack.setCount((int) Math.min(Integer.MAX_VALUE, amount));
            return stack;
        }
    }
}
