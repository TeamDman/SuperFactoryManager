package ca.teamdman.sfm.gametest.tests.compat.dank;

import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import ca.teamdman.sfm.common.label.LabelPositionHolder;
import ca.teamdman.sfm.gametest.SFMGameTest;
import ca.teamdman.sfm.gametest.SFMGameTestCountHelpers;
import ca.teamdman.sfm.gametest.SFMGameTestDefinition;
import ca.teamdman.sfm.gametest.SFMGameTestHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import tfar.dankstorage.blockentity.DockBlockEntity;
import tfar.dankstorage.init.ModBlocks;
import tfar.dankstorage.init.ModItems;

import static ca.teamdman.sfm.common.registry.SFMBlocks.MANAGER_BLOCK;
import static ca.teamdman.sfm.common.registry.SFMBlocks.TEST_BARREL_BLOCK;
import static ca.teamdman.sfm.common.registry.SFMItems.DISK_ITEM;
import static ca.teamdman.sfm.gametest.SFMGameTestCountHelpers.count;
import static ca.teamdman.sfm.gametest.SFMGameTestMethodHelpers.assertTrue;

@SuppressWarnings("DataFlowIssue")
@SFMGameTest
public class DankOutputRetainGameTest extends SFMGameTestDefinition {
    @Override
    public String template() {
        return "3x2x1";
    }

    @Override
    public void run(SFMGameTestHelper helper) {
        // Declare positions
        BlockPos dankPos = new BlockPos(2, 2, 0);
        BlockPos managerPos = new BlockPos(1, 2, 0);
        BlockPos chestPos = new BlockPos(0, 2, 0);

        // Place dank storage dock
        helper.setBlock(dankPos, ModBlocks.dock);
        DockBlockEntity dankBlockEntity = (DockBlockEntity) helper.getBlockEntity(dankPos);
        dankBlockEntity.addDank(new ItemStack(ModItems.DANKS.get(0)));

        // Place manager block
        helper.setBlock(managerPos, MANAGER_BLOCK.get());
        var manager = (ManagerBlockEntity) helper.getBlockEntity(managerPos);
        manager.setItem(0, new ItemStack(DISK_ITEM.get()));

        // Place chest
        helper.setBlock(chestPos, TEST_BARREL_BLOCK.get());
        var chest = helper.getItemHandler(chestPos);

        // Fill the chest with items
        for (int slot = 0; slot < chest.getSlots(); slot++) {
            chest.insertItem(slot, new ItemStack(Blocks.DIRT, 64), false);
        }

        // Set program
        manager.setProgram("""
                                   EVERY 20 TICKS DO
                                       INPUT FROM chest
                                       OUTPUT RETAIN 300 TO dank TOP SIDE
                                   END
                                   """.stripTrailing().stripIndent());

        // Set labels
        LabelPositionHolder.empty()
                .add("chest", helper.absolutePos(chestPos))
                .add("dank", helper.absolutePos(dankPos))
                .save(manager.getDisk());

        // Success check
        helper.succeedIfManagerDidThingWithoutLagging(manager, () -> {
            var dirtCount = SFMGameTestCountHelpers.count(chest, Items.DIRT);
            assertTrue(
                    dirtCount == chest.getSlots() * 64 - 300,
                    "Expected chest to be full, sans 300 dirt"
            );

            var dankInventory = helper.getItemHandler(dankPos, Direction.DOWN);
            var dankCount = count(dankInventory, Items.DIRT);
            assertTrue(
                    dankCount == 300,
                    "Expected dank to have 300 dirt, but got " + dankCount
            );
        });
    }
}
