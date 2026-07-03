package ca.teamdman.sfm.gametest.tests.compat.industrialforegoing;

import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import ca.teamdman.sfm.common.capability.SFMWellKnownCapabilities;
import ca.teamdman.sfm.common.label.LabelPositionHolder;
import ca.teamdman.sfm.common.registry.registration.SFMBlocks;
import ca.teamdman.sfm.common.registry.registration.SFMItems;
import ca.teamdman.sfm.gametest.SFMGameTest;
import ca.teamdman.sfm.gametest.SFMGameTestDefinition;
import ca.teamdman.sfm.gametest.SFMGameTestHelper;
import com.buuz135.industrial.module.ModuleCore;
import com.buuz135.industrial.module.ModuleTransportStorage;
import com.buuz135.industrial.utils.BlockUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;




/**
 * Migrated from SFMIndustrialForegoingCompatGameTests.industrialforegoing_blackhole_empty
 */
@SuppressWarnings({
        "RedundantSuppression",
        "DataFlowIssue",
        "OptionalGetWithoutIsPresent",
        "DuplicatedCode",
        "ArraysAsListWithZeroOrOneArgument"
})
@SFMGameTest
public class IndustrialForegoingBlackholeEmptyGameTest extends SFMGameTestDefinition {

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
        helper.setBlock(managerPos, SFMBlocks.MANAGER.get());
        var manager = helper.getBlockEntity(managerPos, ManagerBlockEntity.class);

        // set up the program
        manager.setItem(0, new ItemStack(SFMItems.DISK.get()));
        manager.setProgram("""
                                   EVERY 20 TICKS DO
                                     INPUT FROM a NORTH SIDE
                                     OUTPUT TO b TOP SIDE
                                   END
                                   """.stripIndent());

        // set the labels
        LabelPositionHolder.empty()
                .add("a", helper.absolutePos(leftPos))
                .add("b", helper.absolutePos(rightPos))
                .save(manager.getDisk());

        int fullCount = BlockUtils.getStackAmountByRarity(ModuleCore.SUPREME_RARITY);
        helper.assertTrue(fullCount > 0, "expected full count to be greater than 0");
        helper.assertTrue(left.insertItem(0, new ItemStack(Items.COAL, 64), false).isEmpty(), "couldn't prep left");
        helper.succeedIfManagerDidThingWithoutLagging(manager, () -> {
            helper.assertTrue(left.getStackInSlot(0).isEmpty(), "Contents did not depart properly");
            helper.assertTrue(right.getStackInSlot(0).getCount() == 64, "Contents did not arrive");
        });
    }
}
