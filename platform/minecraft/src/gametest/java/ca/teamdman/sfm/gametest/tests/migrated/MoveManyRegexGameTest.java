package ca.teamdman.sfm.gametest.tests.migrated;

import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import ca.teamdman.sfm.common.label.LabelPositionHolder;
import ca.teamdman.sfm.common.registry.SFMBlocks;
import ca.teamdman.sfm.common.registry.SFMItems;
import ca.teamdman.sfm.gametest.SFMGameTest;
import ca.teamdman.sfm.gametest.SFMGameTestDefinition;
import ca.teamdman.sfm.gametest.SFMGameTestHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static ca.teamdman.sfm.gametest.SFMGameTestMethodHelpers.assertTrue;


/**
 * Migrated from SFMPerformanceGameTests.move_many_regex
 */
@SuppressWarnings({
        "RedundantSuppression",
        "DataFlowIssue",
        "OptionalGetWithoutIsPresent",
        "DuplicatedCode",
        "ArraysAsListWithZeroOrOneArgument"
})
@SFMGameTest
public class MoveManyRegexGameTest extends SFMGameTestDefinition {

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

        var ironIngots = new AtomicInteger(0);
        var goldIngots = new AtomicInteger(0);
        var diamonds = new AtomicInteger(0);

        for (int x = 0; x < 25; x++) {
            for (int z = 0; z < 24; z++) { // make sure we have an even number to split
                // place a cable below
                helper.setBlock(new BlockPos(x, 2, z), SFMBlocks.CABLE_BLOCK.get());
                // place the barrel on top
                helper.setBlock(new BlockPos(x, 3, z), SFMBlocks.TEST_BARREL_BLOCK.get());
                if (z % 2 == 0) {
                    sourceBlocks.add(new BlockPos(x, 3, z));
                    BarrelBlockEntity barrel = (BarrelBlockEntity) helper.getBlockEntity(new BlockPos(x, 3, z));
                    for (int i = 0; i < barrel.getContainerSize(); i++) {
                        if (i % 3 == 0) {
                            barrel.setItem(i, new ItemStack(Items.IRON_INGOT, 64));
                            ironIngots.addAndGet(64);
                        } else if (i % 3 == 1) {
                            barrel.setItem(i, new ItemStack(Items.GOLD_INGOT, 64));
                            goldIngots.addAndGet(64);
                        } else {
                            barrel.setItem(i, new ItemStack(Items.DIAMOND, 64));
                            diamonds.addAndGet(64);
                        }
                    }
                } else {
                    destBlocks.add(new BlockPos(x, 3, z));
                }
            }
        }

        // create the manager block and add the disk
        helper.setBlock(new BlockPos(0, 2, 0), SFMBlocks.MANAGER_BLOCK.get());
        ManagerBlockEntity manager = (ManagerBlockEntity) helper.getBlockEntity(new BlockPos(0, 2, 0));
        manager.setItem(0, new ItemStack(SFMItems.DISK_ITEM.get()));

        // create the program
        var program = """
                    NAME "move many regex"
                                
                    EVERY 20 TICKS DO
                        INPUT *:*_ingot FROM a
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
        helper.succeedIfManagerDidThingWithoutLagging(manager, () -> {
            // ensure the source chests only have the non-ingot items
            sourceBlocks.forEach(pos -> {
                BarrelBlockEntity barrel = (BarrelBlockEntity) helper.getBlockEntity(pos);
                for (int i = 0; i < barrel.getContainerSize(); i++) {
                    if (i % 3 == 0) {
                        assertTrue(barrel.getItem(i).isEmpty(), "Items did not depart");
                    } else if (i % 3 == 1) {
                        assertTrue(barrel.getItem(i).isEmpty(), "Items did not depart");
                    } else {
                        assertTrue(barrel.getItem(i).getItem() == Items.DIAMOND, "Non-matching didn't stay");
                    }
                }
            });
            // ensure the destination chests only have the ingot items
            int diamondStart = diamonds.get();
            destBlocks.forEach(pos -> {
                BarrelBlockEntity barrel = (BarrelBlockEntity) helper.getBlockEntity(pos);
                for (int i = 0; i < barrel.getContainerSize(); i++) {
                    Item item = barrel.getItem(i).getItem();
                    if (item == Items.IRON_INGOT) {
                        ironIngots.addAndGet(-barrel.getItem(i).getCount());
                    } else if (item == Items.GOLD_INGOT) {
                        goldIngots.addAndGet(-barrel.getItem(i).getCount());
                    } else if (item == Items.DIAMOND) {
                        diamonds.addAndGet(-barrel.getItem(i).getCount());
                    }
                }
            });
            assertTrue(ironIngots.get() == 0, "Iron ingots did not arrive");
            assertTrue(goldIngots.get() == 0, "Gold ingots did not arrive");
            assertTrue(diamonds.get() == diamondStart, "Diamonds did not stay");
        });
    }
}
