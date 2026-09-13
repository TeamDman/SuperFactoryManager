package ca.teamdman.sfm.gametest.tests.general;

import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import ca.teamdman.sfm.common.item.PacketItem;
import ca.teamdman.sfm.common.program.ExecuteProgramBehaviour;
import ca.teamdman.sfm.common.program.GeneratedItemProgramInputSource;
import ca.teamdman.sfm.common.program.LimitedInputSlot;
import ca.teamdman.sfm.common.program.LimitedOutputSlot;
import ca.teamdman.sfm.common.program.ProgramContext;
import ca.teamdman.sfm.common.program.ProgramResourceObservation;
import ca.teamdman.sfm.common.program.ProgramResourceObserver;
import ca.teamdman.sfm.common.registry.registration.SFMBlocks;
import ca.teamdman.sfm.common.registry.registration.SFMItems;
import ca.teamdman.sfm.common.registry.registration.SFMResourceTypes;
import ca.teamdman.sfm.common.value.SFMValue;
import ca.teamdman.sfm.gametest.SFMGameTest;
import ca.teamdman.sfm.gametest.SFMGameTestDefinition;
import ca.teamdman.sfm.gametest.SFMGameTestHelper;
import ca.teamdman.sfml.ast.ASTBuilder;
import ca.teamdman.sfml.ast.Label;
import ca.teamdman.sfml.ast.OutputStatement;
import ca.teamdman.sfml.ast.Program;
import ca.teamdman.sfml.ast.ResourceIdSet;
import ca.teamdman.sfml.ast.ResourceLimit;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.items.IItemHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

@SFMGameTest
public class GeneratedPacketInputSourceGameTest extends SFMGameTestDefinition {
    @Override
    public String template() {
        return "3x2x1";
    }

    @Override
    @SuppressWarnings("unchecked")
    public void run(SFMGameTestHelper helper) {
        BlockPos managerPos = new BlockPos(0, 1, 0);
        BlockPos destinationPos = new BlockPos(2, 1, 0);
        helper.setBlock(managerPos, SFMBlocks.MANAGER.get());
        helper.setBlock(destinationPos, Blocks.CHEST);
        ManagerBlockEntity manager = helper.getBlockEntity(managerPos, ManagerBlockEntity.class);
        manager.setItem(0, new ItemStack(SFMItems.DISK.get()));

        ProgramContext context = new ProgramContext(
                new Program(new ASTBuilder(), "generated packet source test", List.of(), Set.of(), Set.of()),
                manager,
                ExecuteProgramBehaviour::new
        );
        AtomicInteger constructorCalls = new AtomicInteger();
        SFMValue expected = SFMValue.object(Map.of(
                "JobId", SFMValue.of("b3-one-occurrence"),
                "type", SFMValue.of("Request")
        ));
        GeneratedItemProgramInputSource source = new GeneratedItemProgramInputSource(
                "generated packet GameTest occurrence",
                () -> {
                    constructorCalls.incrementAndGet();
                    return expected;
                }
        );
        context.addInput(source);

        SFMValue firstDemand = source.value(context);
        List<LimitedInputSlot<?, ?, ?>> gathered = new ArrayList<>();
        source.gatherSlots(context, gathered::add);
        source.gatherSlots(context, slot -> helper.assertTrue(
                slot == gathered.get(0),
                "Repeated resource demand must reuse the same generated slot"
        ));
        LimitedInputSlot<ItemStack, Item, IItemHandler> input =
                (LimitedInputSlot<ItemStack, Item, IItemHandler>) gathered.get(0);

        helper.assertTrue(firstDemand == source.value(context),
                          "Repeated value demand must reuse the same logical value");
        helper.assertTrue(constructorCalls.get() == 1, "Generated constructor must run exactly once");
        helper.assertTrue(context.getEphemeralResourceOwner().size() == 1,
                          "Materialized packet must be owned by the execution context");
        helper.assertTrue(PacketItem.getValue(input.peekStackInSlot()).orElseThrow().equals(expected),
                          "The generated carrier must contain the memoized value");

        ItemStack twoMoreOccurrences = PacketItem.create(expected);
        twoMoreOccurrences.setCount(2);
        helper.assertTrue(input.getHandler().insertItem(0, twoMoreOccurrences, false).isEmpty(),
                          "Equal packet values must stack in generated storage");
        List<ProgramResourceObservation> firstObservation = ProgramResourceObserver.observe(
                context,
                (type, stack) -> stack instanceof ItemStack itemStack
                                 && itemStack.getItem() == SFMItems.PACKET.get()
        );
        List<ProgramResourceObservation> secondObservation = ProgramResourceObserver.observe(
                context,
                (type, stack) -> stack instanceof ItemStack itemStack
                                 && itemStack.getItem() == SFMItems.PACKET.get()
        );
        helper.assertTrue(firstObservation.size() == 1 && firstObservation.get(0).amount() == 3,
                          "A stack of three packets must observe three packet occurrences");
        helper.assertTrue(secondObservation.size() == 1 && secondObservation.get(0).amount() == 3,
                          "Repeated observation must preserve the same three occurrences");
        helper.assertTrue(PacketItem.getValue((ItemStack) firstObservation.get(0).stack()).orElseThrow().equals(expected),
                          "Observed packet snapshots must retain the generated value");
        helper.assertTrue(input.peekStackInSlot().getCount() == 3,
                          "Observation must not consume generated packet items");

        IItemHandler destinationHandler = helper.getItemHandler(destinationPos);
        LimitedOutputSlot<ItemStack, Item, IItemHandler> output = new LimitedOutputSlot<>(
                new Label("destination"),
                helper.absolutePos(destinationPos),
                null,
                0,
                destinationHandler,
                ResourceLimit.ACCEPT_ALL_WITHOUT_RESTRAINT.createOutputTracker(ResourceIdSet.EMPTY),
                ItemStack.EMPTY,
                SFMResourceTypes.ITEM.get()
        );
        OutputStatement.moveTo(context, input, output);

        helper.assertTrue(PacketItem.getValue(destinationHandler.getStackInSlot(0)).orElseThrow().equals(expected),
                          "Ordinary resource movement must transfer the generated packet");
        helper.assertTrue(destinationHandler.getStackInSlot(0).getCount() == 3,
                          "Ordinary output must retain all three observed packet occurrences");
        helper.assertTrue(input.peekStackInSlot().isEmpty(), "Generated storage must be empty after transfer");
        helper.assertTrue(context.getEphemeralResourceOwner().size() == 0,
                          "Drained generated storage must release itself from the execution owner");

        context.free();
        helper.assertTrue(PacketItem.getValue(destinationHandler.getStackInSlot(0)).orElseThrow().equals(expected),
                          "Execution cleanup must not affect a packet already moved to world storage");
        helper.succeed();
    }
}
