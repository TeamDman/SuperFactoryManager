package ca.teamdman.sfm.gametest.tests.migrated;

import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import ca.teamdman.sfm.common.item.DiskItem;
import ca.teamdman.sfm.common.label.LabelPositionHolder;
import ca.teamdman.sfm.common.registry.SFMBlocks;
import ca.teamdman.sfm.common.registry.SFMItems;
import ca.teamdman.sfm.gametest.SFMGameTest;
import ca.teamdman.sfm.gametest.SFMGameTestDefinition;
import ca.teamdman.sfm.gametest.SFMGameTestHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;

import java.util.ArrayList;

import static ca.teamdman.sfm.gametest.SFMGameTestMethodHelpers.assertTrue;


/**
 * Migrated from SFMPerformanceGameTests.move_many_inventories
 */
@SuppressWarnings({
        "RedundantSuppression",
        "DataFlowIssue",
        "OptionalGetWithoutIsPresent",
        "DuplicatedCode",
        "ArraysAsListWithZeroOrOneArgument"
})
@SFMGameTest
public class MoveManyInventoriesGameTest extends SFMGameTestDefinition {

    @Override
    public String template() {
        return "25x3x25";
    }

    @Override
    public String batchName() {
        return "laggy";
    }

    @Override
    public void run(SFMGameTestHelper helper) {
        // fill the platform with cables and barrels
        var sourceBlocks = new ArrayList<BlockPos>();
        var destBlocks = new ArrayList<BlockPos>();
        for (int x = 0; x < 25; x++) {
//            for (int z = 0; z < 25; z++) {
            for (int z = 0; z < 24; z++) {
                helper.setBlock(new BlockPos(x, 2, z), SFMBlocks.CABLE_BLOCK.get());
                helper.setBlock(new BlockPos(x, 3, z), SFMBlocks.TEST_BARREL_BLOCK.get());
                if (z % 2 == 0) {
                    sourceBlocks.add(new BlockPos(x, 3, z));
                    // fill the source chests with ingots
                    BarrelBlockEntity barrel = (BarrelBlockEntity) helper.getBlockEntity(new BlockPos(x, 3, z));
                    for (int i = 0; i < barrel.getContainerSize(); i++) {
                        barrel.setItem(i, new ItemStack(Items.IRON_INGOT, 64));
                    }
                } else {
                    destBlocks.add(new BlockPos(x, 3, z));
                }
            }
        }

        // fill in the blocks needed for the test
        helper.setBlock(new BlockPos(0, 2, 0), SFMBlocks.MANAGER_BLOCK.get());
        ManagerBlockEntity manager = (ManagerBlockEntity) helper.getBlockEntity(new BlockPos(0, 2, 0));
        manager.setItem(0, new ItemStack(SFMItems.DISK_ITEM.get()));

        // create the program
        var program = """
                    NAME "many inventory lag test"
                                
                    EVERY 20 TICKS DO
                        INPUT FROM a
                        OUTPUT TO b
                    END
                """.stripTrailing().stripIndent();

        // set the labels
        LabelPositionHolder.empty()
                .addAll("a", sourceBlocks.stream().map(helper::absolutePos).toList())
                .addAll("b", destBlocks.stream().map(helper::absolutePos).toList())
                .save(manager.getDisk());

        // load the program
        manager.setProgram(program);
        assertTrue(
                manager.getState() == ManagerBlockEntity.State.RUNNING,
                "Program did not start running " + DiskItem.getErrors(manager.getDisk())
        );

        helper.succeedIfManagerDidThingWithoutLagging(manager, () -> {
            // ensure all the source chests are empty
            sourceBlocks.forEach(pos -> {
                BarrelBlockEntity barrel = (BarrelBlockEntity) helper.getBlockEntity(pos);
                for (int i = 0; i < barrel.getContainerSize(); i++) {
                    ItemStack found = barrel.getItem(i);
                    assertTrue(
                            found.isEmpty(),
                            "Items did not leave, pos=" + helper.absolutePos(pos) + " i=" + i + " found=" + found
                    );
                }
            });
            // ensure all the dest chests are full
            destBlocks.forEach(pos -> {
                BarrelBlockEntity barrel = (BarrelBlockEntity) helper.getBlockEntity(pos);
                for (int i = 0; i < barrel.getContainerSize(); i++) {
                    assertTrue(barrel.getItem(i).getCount() == 64, "Items did not arrive");
                }
            });


        });
    }
}
