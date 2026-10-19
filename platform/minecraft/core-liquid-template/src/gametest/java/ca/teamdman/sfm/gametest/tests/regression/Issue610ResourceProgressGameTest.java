package ca.teamdman.sfm.gametest.tests.regression;

import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import ca.teamdman.sfm.common.program.*;
import ca.teamdman.sfm.common.registry.registration.SFMBlocks;
import ca.teamdman.sfm.common.registry.registration.SFMItems;
import ca.teamdman.sfm.common.registry.registration.SFMResourceTypes;
import ca.teamdman.sfm.common.resourcetype.ResourceType;
import ca.teamdman.sfm.gametest.*;
import ca.teamdman.sfml.ast.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/** Reduced real-transfer controls, distinct from the real Mekanism/JDT world fixtures. */
@SFMGameTest
public class Issue610ResourceProgressGameTest extends SFMGameTestDefinition {
    @Override public String template() { return "3x2x1"; }
    @Override public String testName() { return "issue_610_resource_progress"; }

    @Override public void run(SFMGameTestHelper helper) {
        var pos = new BlockPos(1, 2, 0);
        helper.setBlock(pos, SFMBlocks.MANAGER.get());
        var manager = helper.getBlockEntity(pos, ManagerBlockEntity.class);
        manager.setItem(0, new ItemStack(SFMItems.DISK.get()));
        manager.setProgram("EVERY 20 TICKS DO\nEND");
        helper.assertManagerRunning(manager);
        var context = new ProgramContext(Objects.requireNonNull(manager.getProgram()), manager, new ExecuteProgramBehaviour());

        var itemSource = new ItemStackHandler(1) {
            @Override public ItemStack extractItem(int slot, int amount, boolean simulate) {
                return amount == 1 ? ItemStack.EMPTY : super.extractItem(slot, amount, simulate);
            }
        };
        itemSource.setStackInSlot(0, new ItemStack(Items.PAPER, 8));
        var itemSmall = new ItemStackHandler(1);
        itemSmall.setStackInSlot(0, new ItemStack(Items.PAPER, 63));
        var itemLarge = new ItemStackHandler(1);
        progress(helper, context, SFMResourceTypes.ITEM.get(), itemSource, itemSmall, itemLarge, 8);

        var fluidSource = new FluidTank(1000) {
            @Override public FluidStack drain(FluidStack stack, FluidAction action) {
                return stack.getAmount() == 1 ? FluidStack.EMPTY : super.drain(stack, action);
            }
        };
        fluidSource.fill(new FluidStack(Fluids.WATER, 100), FluidAction.EXECUTE);
        var fluidSmall = new FluidTank(1000);
        fluidSmall.fill(new FluidStack(Fluids.WATER, 999), FluidAction.EXECUTE);
        var fluidLarge = new FluidTank(1000);
        progress(helper, context, SFMResourceTypes.FLUID.get(), fluidSource, fluidSmall, fluidLarge, 100);

        var energySource = new EnergyStorage(1000) {
            @Override public int extractEnergy(int amount, boolean simulate) {
                return super.extractEnergy(amount - amount % 2, simulate);
            }
        };
        energySource.receiveEnergy(100, false);
        var energySmall = new EnergyStorage(1000);
        energySmall.receiveEnergy(999, false);
        var energyLarge = new EnergyStorage(1000);
        progress(helper, context, SFMResourceTypes.FORGE_ENERGY.get(), energySource, energySmall, energyLarge, 100);

        int[] itemAttempts = {0};
        var refusingItems = new ItemStackHandler(1) {
            @Override public ItemStack extractItem(int slot, int amount, boolean simulate) {
                if (!simulate) itemAttempts[0]++;
                return ItemStack.EMPTY;
            }
        };
        refusingItems.setStackInSlot(0, new ItemStack(Items.PAPER, 8));
        refusalBound(helper, context, SFMResourceTypes.ITEM.get(), refusingItems,
                () -> new ItemStackHandler(1), itemAttempts, 8);

        int[] fluidAttempts = {0};
        var refusingFluids = new FluidTank(1000) {
            @Override public FluidStack drain(FluidStack stack, FluidAction action) {
                if (action == FluidAction.EXECUTE) fluidAttempts[0]++;
                return FluidStack.EMPTY;
            }
        };
        refusingFluids.fill(new FluidStack(Fluids.WATER, 100), FluidAction.EXECUTE);
        refusalBound(helper, context, SFMResourceTypes.FLUID.get(), refusingFluids,
                () -> new FluidTank(1000), fluidAttempts, 100);

        // Nonempty sources refusing everything get at most one attempt per destination.
        int[] attempts = {0};
        var refusing = new EnergyStorage(1000) {
            @Override public int extractEnergy(int amount, boolean simulate) {
                if (!simulate) attempts[0]++;
                return 0;
            }
        };
        refusing.receiveEnergy(100, false);
        refusalBound(helper, context, SFMResourceTypes.FORGE_ENERGY.get(), refusing,
                () -> new EnergyStorage(1000), attempts, 100);
        helper.assertTrue(input(SFMResourceTypes.FORGE_ENERGY.get(), new EnergyStorage(1000),
                               Limit.MAX_QUANTITY_NO_RETENTION).isDone(),
                "An initially empty source must be finished without attempting extraction");

        // Input quantity/retention limits remain binding across a failed small request.
        energySource.receiveEnergy(100, false);
        energyLarge.extractEnergy(1000, false);
        var input = input(SFMResourceTypes.FORGE_ENERGY.get(), energySource, limit(20, 80));
        OutputStatement.moveTo(context, input, output(SFMResourceTypes.FORGE_ENERGY.get(), energySmall));
        if (!input.isDone()) OutputStatement.moveTo(context, input, output(SFMResourceTypes.FORGE_ENERGY.get(), energyLarge));
        helper.assertTrue(energySource.getEnergyStored() == 80 && energyLarge.getEnergyStored() == 20 && input.isDone(),
                "Input quantity and retention must constrain later progress");
        helper.succeed();
    }

    private static <STACK, ITEM, CAP> void refusalBound(SFMGameTestHelper helper, ProgramContext context,
            ResourceType<STACK, ITEM, CAP> type, CAP source, Supplier<CAP> destinationFactory,
            int[] attempts, long initial) {
        var input = input(type, source, Limit.MAX_QUANTITY_NO_RETENTION);
        for (int i = 0; i < 256; i++) {
            CAP destination = destinationFactory.get();
            if (!input.isDone()) OutputStatement.moveTo(context, input, output(type, destination));
            helper.assertTrue(type.getAmount(type.getStackInSlot(destination, 0)) == 0,
                    "Refused extraction must not create destination resources: " + type);
        }
        helper.assertTrue(attempts[0] == 256 && type.getAmount(type.getStackInSlot(source, 0)) == initial,
                "Refusal must stay bounded by destination count and conserve the resource: " + type);
        helper.assertTrue(input.tracker.getMaxTransferable(type, type.getStackInSlot(source, 0)) == Long.MAX_VALUE,
                "Failed extraction must not consume the input quantity budget: " + type);
    }

    private static <STACK, ITEM, CAP> void progress(SFMGameTestHelper helper, ProgramContext context,
            ResourceType<STACK, ITEM, CAP> type, CAP source, CAP small, CAP large, long initial) {
        long before = type.getAmount(type.getStackInSlot(small, 0));
        var input = input(type, source, Limit.MAX_QUANTITY_NO_RETENTION);
        helper.assertTrue(type.getAmount(type.getStackInSlot(source, 0)) == initial && !input.isDone()
                        && input.tracker.matchesStack(type.getStackInSlot(source, 0)),
                "Fixture must select a nonempty source of the tested resource type: " + type);
        OutputStatement.moveTo(context, input, output(type, small));
        helper.assertTrue(type.getAmount(type.getStackInSlot(source, 0)) == initial
                        && type.getAmount(type.getStackInSlot(small, 0)) == before
                        && input.tracker.getMaxTransferable(type, type.getStackInSlot(source, 0)) == Long.MAX_VALUE,
                "Refused quantity must conserve source, destination and quantity budget: " + type);
        helper.assertTrue(!input.isDone(), "A refused quantity must not finish the whole source: " + type);
        OutputStatement.moveTo(context, input, output(type, large));
        long remaining = type.getAmount(type.getStackInSlot(source, 0));
        long moved = type.getAmount(type.getStackInSlot(small, 0)) - before
                     + type.getAmount(type.getStackInSlot(large, 0));
        helper.assertTrue(remaining == 0 && moved == initial && remaining + moved == initial && input.isDone(),
                "Later progress must conserve the resource and finish an exhausted source: " + type);
    }

    private static <STACK, ITEM, CAP> LimitedInputSlot<STACK, ITEM, CAP> input(
            ResourceType<STACK, ITEM, CAP> type, CAP handler, Limit limit) {
        return new LimitedInputSlot<>(new Label("source"), BlockPos.ZERO, null, 0, handler,
                new ResourceLimit(resourceIds(type), limit, With.ALWAYS_TRUE).createInputTracker(ResourceIdSet.EMPTY),
                type.getStackInSlot(handler, 0), type);
    }

    private static <STACK, ITEM, CAP> LimitedOutputSlot<STACK, ITEM, CAP> output(
            ResourceType<STACK, ITEM, CAP> type, CAP handler) {
        return new LimitedOutputSlot<>(new Label("destination"), BlockPos.ZERO, null, 0, handler,
                new ResourceLimit(resourceIds(type), Limit.MAX_QUANTITY_MAX_RETENTION, With.ALWAYS_TRUE)
                        .createOutputTracker(ResourceIdSet.EMPTY), type.getStackInSlot(handler, 0), type);
    }

    private static ResourceIdSet resourceIds(ResourceType<?, ?, ?> type) {
        var key = SFMResourceTypes.registry().getId(type);
        return new ResourceIdSet(List.of(new ResourceIdentifier<>(key.getNamespace(), key.getPath(), ".*", ".*")));
    }

    private static Limit limit(long quantity, long retention) {
        return new Limit(new ResourceQuantity(new ca.teamdman.sfml.ast.Number(quantity), ResourceQuantity.IdExpansionBehaviour.NO_EXPAND),
                new ResourceQuantity(new ca.teamdman.sfml.ast.Number(retention), ResourceQuantity.IdExpansionBehaviour.NO_EXPAND));
    }
}
