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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;

import java.util.ArrayList;
import java.util.List;

import static ca.teamdman.sfm.gametest.SFMGameTestMethodHelpers.assertTrue;


/**
 * Migrated from SFMPerformanceGameTests.gather_supplies
 */
@SuppressWarnings({
        "RedundantSuppression",
        "DataFlowIssue",
        "OptionalGetWithoutIsPresent",
        "DuplicatedCode",
        "ArraysAsListWithZeroOrOneArgument"
})
@SFMGameTest
public class GatherSuppliesGameTest extends SFMGameTestDefinition {

    @Override
    public String template() {
        return "25x4x25";
    }

    @Override
    public void run(SFMGameTestHelper helper) {
        var items = new Item[]{
                Items.GOLD_INGOT,
                Items.GOLD_BLOCK,
                Items.IRON_INGOT,
                Items.IRON_BLOCK,
                Items.DIAMOND,
                Items.DIAMOND_BLOCK,
                Items.EMERALD,
                Items.EMERALD_BLOCK,
                Items.LAPIS_LAZULI,
                Items.LAPIS_BLOCK,
                Items.REDSTONE,
                Items.REDSTONE_BLOCK,
                Items.COAL,
                Items.COAL_BLOCK,
                Items.NETHERITE_INGOT,
                Items.NETHERITE_BLOCK,
                Items.TORCH,
                Items.BUCKET,
                Items.CHEST,
                Items.CRAFTING_TABLE,
                Items.FURNACE,
                Items.COBBLESTONE,
                Items.OAK_LOG,
                Items.OAK_PLANKS
        };

        // create a bunch of storage inventories
        List<BlockPos> storage = new ArrayList<>();
        int itemIndex = 0;
        for (int i = 0; i < 24; i++) {
            for (int j = 3; j < 25; j++) {
                for (int k = 0; k < 3; k++) {
                    BlockPos pos = new BlockPos(i, k + 2, j);
                    if (j == 3) {
                        if (k == 0) {
                            helper.setBlock(pos, SFMBlocks.CABLE_BLOCK.get());
                        }
                    } else {
                        if (i % 3 == 0 || i % 3 == 2) {
                            helper.setBlock(pos, SFMBlocks.TEST_BARREL_BLOCK.get());
                            // fill the barrel with some items
                            BarrelBlockEntity barrel = (BarrelBlockEntity) helper.getBlockEntity(pos);
                            for (int slot = 0; slot < barrel.getContainerSize(); slot++) {
                                barrel.setItem(slot, new ItemStack(items[itemIndex++ % items.length], 64));
                            }

                            storage.add(pos);
                        } else {
                            helper.setBlock(pos, SFMBlocks.CABLE_BLOCK.get());
                        }
                    }
                }
            }
        }

        // add the crafting station
        helper.setBlock(new BlockPos(0, 2, 1), Blocks.CRAFTING_TABLE);
        helper.setBlock(new BlockPos(0, 2, 0), SFMBlocks.TEST_BARREL_BLOCK.get());
        helper.setBlock(new BlockPos(1, 2, 0), SFMBlocks.CABLE_BLOCK.get());
        helper.setBlock(new BlockPos(1, 2, 1), SFMBlocks.CABLE_BLOCK.get());
        helper.setBlock(new BlockPos(1, 2, 2), SFMBlocks.CABLE_BLOCK.get());

        // add the manager
        helper.setBlock(new BlockPos(2, 2, 0), SFMBlocks.MANAGER_BLOCK.get());
        var manager = (ManagerBlockEntity) helper.getBlockEntity(new BlockPos(2, 2, 0));
        manager.setItem(0, new ItemStack(SFMItems.DISK_ITEM.get()));

        // create the program
        var program = """
                NAME "gather supplies"
                                
                EVERY 20 TICKS DO
                   INPUT
                       retain 64 gold_ingot,
                       retain 64 gold_block,
                       retain 64 iron_ingot,
                       retain 64 iron_block,
                       retain 64 diamond,
                       retain 64 diamond_block,
                       retain 64 emerald,
                       retain 64 emerald_block,
                       retain 64 lapis_lazuli,
                       retain 64 lapis_block,
                       retain 64 "redstone",
                       retain 64 redstone_block,
                       retain 64 coal,
                       retain 64 coal_block,
                       retain 64 netherite_ingot,
                       retain 64 netherite_block,
                       retain 64 torch,
                       retain 16 bucket,
                       retain 64 chest,
                       retain 64 crafting_table,
                       retain 64 furnace,
                       retain 64 cobblestone,
                       retain 64 *:*_log,
                       retain 64 *:*_planks
                    FROM chest
                    INPUT EXCEPT
                       gold_ingot,
                       gold_block,
                       iron_ingot,
                       iron_block,
                       diamond,
                       diamond_block,
                       emerald,
                       emerald_block,
                       lapis_lazuli,
                       lapis_block,
                       "redstone",
                       redstone_block,
                       coal,
                       coal_block,
                       netherite_ingot,
                       netherite_block,
                       torch,
                       bucket,
                       chest,
                       crafting_table,
                       furnace,
                       cobblestone,
                       *:*_log,
                       *:*_planks
                   FROM chest
                   OUTPUT TO storage
                END
                                
                EVERY 20 TICKS DO
                   INPUT FROM storage
                   OUTPUT
                       retain 64 gold_ingot,
                       retain 64 gold_block,
                       retain 64 iron_ingot,
                       retain 64 iron_block,
                       retain 64 diamond,
                       retain 64 diamond_block,
                       retain 64 emerald,
                       retain 64 emerald_block,
                       retain 64 lapis_lazuli,
                       retain 64 lapis_block,
                       retain 64 "redstone",
                       retain 64 redstone_block,
                       retain 64 coal,
                       retain 64 coal_block,
                       retain 64 netherite_ingot,
                       retain 64 netherite_block,
                       retain 64 torch,
                       retain 16 bucket,
                       retain 64 chest,
                       retain 64 crafting_table,
                       retain 64 furnace,
                       retain 64 cobblestone,
                       retain 64 *:*_log,
                       retain 64 *:*_planks
                   TO chest
                END
                """.stripTrailing().stripIndent();

        // set the labels
        LabelPositionHolder.empty()
                .addAll("storage", storage.stream().map(helper::absolutePos).toList())
                .add("chest", helper.absolutePos(new BlockPos(0, 2, 0)))
                .save(manager.getDisk());

        // load the program
        manager.setProgram(program);

        helper.succeedIfManagerDidThingWithoutLagging(manager, () -> {
            // the inventory should be stocked with a stack of each item
            BarrelBlockEntity barrel = (BarrelBlockEntity) helper.getBlockEntity(new BlockPos(0, 2, 0));
            for (Item item : items) {
                for (int slot = 0; slot < barrel.getContainerSize(); slot++) {
                    ItemStack stack = barrel.getItem(slot);
                    if (stack.getItem() == item) {
                        assertTrue(
                                stack.getCount() == stack.getMaxStackSize(),
                                "Item " + item + " is not fully stocked"
                        );
                    }
                }
            }
        });
    }
}
