package ca.teamdman.sfm.gametest.tests.compat.mekanism;

import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import ca.teamdman.sfm.common.program.*;
import ca.teamdman.sfm.common.registry.registration.SFMBlocks;
import ca.teamdman.sfm.common.registry.registration.SFMItems;
import ca.teamdman.sfm.common.registry.registration.SFMResourceTypes;
import ca.teamdman.sfm.gametest.*;
import ca.teamdman.sfml.ast.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.energy.EnergyStorage;
import net.minecraftforge.energy.IEnergyStorage;

import java.util.Objects;

/**
 * Diagnostic characterization, not a claim that issue #610 is fixed.
 * The standalone pinned-1.21.1 probe establishes extraction granularity of two FE.
 * This reduced handler carries that observed behavior through SFM's real transfer code.
 * Once the production bug is fixed, replace the starvation expectation with progress.
 */
@SFMGameTest
public class Issue610QuantizedSourceCharacterizationGameTest extends SFMGameTestDefinition {
    @Override public String template() { return "3x2x1"; }

    @Override public void run(SFMGameTestHelper helper) {
        BlockPos managerPos = new BlockPos(1, 2, 0);
        helper.setBlock(managerPos, SFMBlocks.MANAGER.get());
        var manager = helper.getBlockEntity(managerPos, ManagerBlockEntity.class);
        manager.setItem(0, new ItemStack(SFMItems.DISK.get()));
        manager.setProgram("EVERY 20 TICKS DO\nEND");
        helper.assertManagerRunning(manager);
        var context = new ProgramContext(Objects.requireNonNull(manager.getProgram()), manager, new ExecuteProgramBehaviour());
        check(helper, context, 1, false, true);
        check(helper, context, 2, false, false);
        check(helper, context, 0, false, false);
        check(helper, context, 1, true, false);
        helper.succeed();
    }

    private static void check(SFMGameTestHelper helper, ProgramContext context, int firstGap,
                              boolean hungryFirst, boolean expectStarvation) {
        var type = SFMResourceTypes.FORGE_ENERGY.get();
        var source = new EnergyStorage(1000) {
            @Override public int extractEnergy(int requested, boolean simulate) {
                return super.extractEnergy(requested - requested % 2, simulate);
            }
        };
        source.receiveEnergy(100, false);
        var nearFull = new EnergyStorage(1000);
        nearFull.receiveEnergy(1000 - firstGap, false);
        var hungry = new EnergyStorage(1000);
        var ids = new ResourceIdSet(java.util.List.of(new ResourceIdentifier<>("sfm", "forge_energy", ".*", ".*")));
        var input = new LimitedInputSlot<Integer, Class<Integer>, IEnergyStorage>(
                new Label("source"), BlockPos.ZERO, null, 0, source,
                new ResourceLimit(ids, Limit.MAX_QUANTITY_NO_RETENTION, With.ALWAYS_TRUE).createInputTracker(ResourceIdSet.EMPTY),
                source.getEnergyStored(), type);
        var first = output(nearFull, ids);
        var second = output(hungry, ids);
        OutputStatement.moveTo(context, input, hungryFirst ? second : first);
        if (expectStarvation) {
            helper.assertTrue(input.isDone(), "Expected issue #610: zero small extraction marks the source done");
            helper.assertTrue(source.extractEnergy(100, true) == 100,
                    "Source must still be able to serve the later, larger request");
        }
        // The same guard as OutputStatement.tick: a done source is skipped for later outputs.
        if (!input.isDone()) OutputStatement.moveTo(context, input, hungryFirst ? first : second);
        int moved = nearFull.getEnergyStored() - (1000 - firstGap) + hungry.getEnergyStored();
        helper.assertTrue(moved + source.getEnergyStored() == 100, "Reduced case lost or duplicated energy");
        helper.assertTrue(hungry.getEnergyStored() == (expectStarvation ? 0 : hungryFirst ? 100 : 100 - firstGap),
                "Unexpected progress for gap=" + firstGap + ", hungryFirst=" + hungryFirst);
    }

    private static LimitedOutputSlot<Integer, Class<Integer>, IEnergyStorage> output(EnergyStorage handler, ResourceIdSet ids) {
        return new LimitedOutputSlot<>(new Label("destination"), BlockPos.ZERO, null, 0, handler,
                new ResourceLimit(ids, Limit.MAX_QUANTITY_MAX_RETENTION, With.ALWAYS_TRUE).createOutputTracker(ResourceIdSet.EMPTY),
                handler.getEnergyStored(), SFMResourceTypes.FORGE_ENERGY.get());
    }
}
