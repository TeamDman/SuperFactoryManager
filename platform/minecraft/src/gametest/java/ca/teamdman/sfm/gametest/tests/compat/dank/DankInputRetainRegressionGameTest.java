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
import net.minecraftforge.items.ItemHandlerHelper;
import tfar.dankstorage.blockentity.DockBlockEntity;
import tfar.dankstorage.init.ModBlocks;
import tfar.dankstorage.init.ModItems;

import static ca.teamdman.sfm.common.registry.SFMBlocks.MANAGER_BLOCK;
import static ca.teamdman.sfm.common.registry.SFMBlocks.TEST_BARREL_BLOCK;
import static ca.teamdman.sfm.common.registry.SFMItems.DISK_ITEM;
import static ca.teamdman.sfm.gametest.SFMGameTestCountHelpers.count;
import static ca.teamdman.sfm.gametest.SFMGameTestMethodHelpers.assertTrue;

/// [Moving fluid with retain from Fluid Drawer to Industrial Foregoing Latex Processing Unit](https://discord.com/channels/967118679370264627/1372589927090487458)
/// <br/>
/// [SFM broken with dank storages](https://github.com/TeamDman/SuperFactoryManager/issues/181)
/// <br/>
/// [Issue when using RETAIN and inserting to create depot from functional storage drawers](https://github.com/TeamDman/SuperFactoryManager/issues/199)
/// <br/>
/// [RETAIN behaves very oddly](https://github.com/TeamDman/SuperFactoryManager/issues/200)
/// <br/>
/// [Add test using retain on input fluid tank](https://github.com/TeamDman/SuperFactoryManager/issues/297)
/// <br/>
/// [Bug: RETAIN on INPUT not working with numbers over 64.](https://github.com/TeamDman/SuperFactoryManager/issues/306)
@SuppressWarnings("DataFlowIssue")
@SFMGameTest
public class DankInputRetainRegressionGameTest extends SFMGameTestDefinition {
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
        var dankInventory = helper.getItemHandler(dankPos, Direction.DOWN); // must happen after addDank

        // Place manager block
        helper.setBlock(managerPos, MANAGER_BLOCK.get());
        var manager = (ManagerBlockEntity) helper.getBlockEntity(managerPos);
//        manager.setLogLevel(Level.DEBUG);
        manager.setItem(0, new ItemStack(DISK_ITEM.get()));

        // Place chest
        helper.setBlock(chestPos, TEST_BARREL_BLOCK.get());
        var chest = helper.getItemHandler(chestPos);

        // Fill the dank with 500 items
        var expectedRetain = 512;
        var expectedChest = 128;
        var toInsert = expectedRetain + expectedChest;
        while (toInsert > 0) {
            ItemHandlerHelper.insertItem(
                    dankInventory,
                    new ItemStack(Items.DIRT, Math.min(toInsert, 64)),
                    false
            );
            toInsert -= 64;
        }

        // Set program
        manager.setProgram("""
                                   EVERY 20 TICKS DO
                                       INPUT RETAIN %d FROM dank BOTTOM SIDE
                                       OUTPUT TO chest
                                   END
                                   """.stripTrailing().stripIndent().formatted(expectedRetain));

        // Set labels
        LabelPositionHolder.empty()
                .add("chest", helper.absolutePos(chestPos))
                .add("dank", helper.absolutePos(dankPos))
                .save(manager.getDisk());

        // Success check
        helper.succeedIfManagerDidThingWithoutLagging(manager, () -> {
            var chestCount = SFMGameTestCountHelpers.count(chest, Items.DIRT);
            assertTrue(
                    chestCount == expectedChest,
                    "Expected chest contain %d dirt, got %d".formatted(expectedChest, chestCount)
            );

            var dankCount = count(dankInventory, Items.DIRT);
            assertTrue(
                    dankCount == expectedRetain,
                    "Expected dank to retain %d dirt, got %d".formatted(expectedRetain, dankCount)
            );
        });
    }
}
