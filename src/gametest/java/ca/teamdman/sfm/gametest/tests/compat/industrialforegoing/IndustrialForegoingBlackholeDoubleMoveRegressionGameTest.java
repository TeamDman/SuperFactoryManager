package ca.teamdman.sfm.gametest.tests.compat.industrialforegoing;

import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import ca.teamdman.sfm.common.capability.SFMWellKnownCapabilities;
import ca.teamdman.sfm.common.label.LabelPositionHolder;
import ca.teamdman.sfm.common.registry.SFMBlocks;
import ca.teamdman.sfm.common.registry.SFMItems;
import ca.teamdman.sfm.gametest.SFMGameTest;
import ca.teamdman.sfm.gametest.SFMGameTestDefinition;
import ca.teamdman.sfm.gametest.SFMGameTestHelper;
import com.buuz135.industrial.module.ModuleCore;
import com.buuz135.industrial.module.ModuleTransportStorage;
import com.buuz135.industrial.utils.BlockUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import static ca.teamdman.sfm.gametest.SFMGameTestMethodHelpers.assertTrue;


/// In {@link ca.teamdman.sfml.ast.InputStatement} there was a correctness bug due to accidentally duplicated logic
/// ```java
///         // do we have a cached result?
///         if (limitedInputSlotsCache != null) {
///             // log cache hit
///             context.getLogger().trace(x -> x.accept(LOG_PROGRAM_TICK_IO_STATEMENT_GATHER_SLOTS_CACHE_HIT.get()));
///             // return cached results
///             for (var slot : limitedInputSlotsCache) {
///                 slotConsumer.accept(slot);
///             }
///             limitedInputSlotsCache.forEach(slotConsumer); // <-- correctness problem here
///             return;
///         }
/// ```
/// This results in the list of input slots being gathered twice.
/// This only happens when the cache is present.
/// Reproducable by
/// ```sfml
/// EVERY redstone pulse DO
///   INPUT FROM a NORTH SIDE
///   OUTPUT 0 TO b TOP SIDE -- populate cache
///   OUTPUT TO b TOP SIDE -- double move operation happens here
/// END
/// ```
@SuppressWarnings({
        "RedundantSuppression",
        "DataFlowIssue",
        "OptionalGetWithoutIsPresent",
        "DuplicatedCode",
        "ArraysAsListWithZeroOrOneArgument"
})
@SFMGameTest
public class IndustrialForegoingBlackholeDoubleMoveRegressionGameTest extends SFMGameTestDefinition {

    @Override
    public String template() {
        return "3x2x1";
    }

    @Override
    public void run(SFMGameTestHelper helper) {
        // designate positions
        var leftPos = new BlockPos(2, 2, 0);
        var rightPos = new BlockPos(0, 2, 0);
        var managerPos = new BlockPos(1, 2, 0);

        // set up the world
        helper.setBlock(leftPos, ModuleTransportStorage.BLACK_HOLE_UNIT_SUPREME.getLeft().get());
        var left = helper.getBlockEntity(leftPos).getCapability(SFMWellKnownCapabilities.ITEM_HANDLER.capabilityKind()).orElse(null);
        helper.setBlock(rightPos, ModuleTransportStorage.BLACK_HOLE_UNIT_SUPREME.getLeft().get());
        var right = helper.getBlockEntity(rightPos).getCapability(SFMWellKnownCapabilities.ITEM_HANDLER.capabilityKind()).orElse(null);
        helper.setBlock(managerPos, SFMBlocks.MANAGER_BLOCK.get());
        var manager = ((ManagerBlockEntity) helper.getBlockEntity(managerPos));

        // set up the program
        manager.setItem(0, new ItemStack(SFMItems.DISK_ITEM.get()));
        manager.setProgram("""
                                   EVERY 20 TICKS DO
                                     INPUT FROM a NORTH SIDE
                                     OUTPUT 0 TO b TOP SIDE
                                     OUTPUT TO b TOP SIDE
                                   END
                                   """.stripIndent());

        // set the labels
        LabelPositionHolder.empty()
                .add("a", helper.absolutePos(leftPos))
                .add("b", helper.absolutePos(rightPos))
                .save(manager.getDisk());

        int fullCount = BlockUtils.getStackAmountByRarity(ModuleCore.SUPREME_RARITY);
        assertTrue(fullCount > 0, "expected full count to be greater than 0");
        assertTrue(left.insertItem(0, new ItemStack(Items.COAL, 64), false).isEmpty(), "couldn't prep left");
        assertTrue(left.insertItem(0, new ItemStack(Items.COAL, 1), false).isEmpty(), "couldn't prep left");
        assertTrue(right.insertItem(0, new ItemStack(Items.COAL, 64), false).isEmpty(), "couldn't prep left");
        helper.succeedIfManagerDidThingWithoutLagging(manager, () -> {
            assertTrue(left.getStackInSlot(0).getCount() == 1, "Contents did not depart properly");
            assertTrue(right.getStackInSlot(0).getCount() == 128, "Contents did not arrive properly");
        });
    }
}