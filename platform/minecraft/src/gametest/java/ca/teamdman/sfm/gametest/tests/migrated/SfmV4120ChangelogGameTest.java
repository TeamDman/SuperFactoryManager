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

import java.util.Objects;

import static ca.teamdman.sfm.gametest.SFMGameTestMethodHelpers.assertTrue;

/**
 * Migrated from SFMCorrectnessGameTests.sfm_v4_12_0_changelog
 */
@SuppressWarnings({
        "RedundantSuppression",
        "DataFlowIssue",
        "OptionalGetWithoutIsPresent",
        "DuplicatedCode",
        "ArraysAsListWithZeroOrOneArgument"
})
@SFMGameTest
public class SfmV4120ChangelogGameTest extends SFMGameTestDefinition {

    @Override
    public String template() {
        return "3x2x1";
    }

    @Override
    public void run(SFMGameTestHelper helper) {
        helper.setBlock(new BlockPos(1, 2, 0), SFMBlocks.MANAGER_BLOCK.get());
        BlockPos rightPos = new BlockPos(0, 2, 0);
        helper.setBlock(rightPos, SFMBlocks.TEST_BARREL_BLOCK.get());
        BlockPos leftPos = new BlockPos(2, 2, 0);
        helper.setBlock(leftPos, SFMBlocks.TEST_BARREL_BLOCK.get());

        var rightChest = helper.getItemHandler(rightPos);
        var leftChest = helper.getItemHandler(leftPos);

        Item[] items = new Item[]{
                Items.NETHERITE_INGOT,
                Items.NETHERITE_INGOT,
                Items.GOLD_INGOT,
                Items.GOLD_INGOT,
                Items.COPPER_INGOT,
                Items.COPPER_INGOT,
                Items.SANDSTONE,
                Items.STONE,
                Items.COBBLESTONE,
                Items.OAK_LOG,
                Items.DARK_OAK_LOG,
                Items.ACACIA_LOG,
                };
        for (int i = 0; i < items.length; i++) {
            leftChest.insertItem(i, new ItemStack(items[i], 64), false);
        }

        ManagerBlockEntity manager = (ManagerBlockEntity) helper.getBlockEntity(new BlockPos(1, 2, 0));
        manager.setItem(0, new ItemStack(SFMItems.DISK_ITEM.get()));
        manager.setProgram("""
                                       NAME "SFM 4.12.0 change overview"
                                       EVERY 20 TICKS DO
                                           INPUT fluid:: FROM a
                                           INPUT 1 *log FROM a
                                           INPUT EXCEPT *log FROM a
                                           OUTPUT
                                               1 EACH minecraft:*ingot,
                                               1 EACH RETAIN 12 EACH minecraft:*stone,
                                               *log,
                                           EXCEPT cobblestone, iron_ingot,
                                           TO b
                                       END
                                   """.stripTrailing().stripIndent());

        // set the labels
        LabelPositionHolder.empty()
                .add("a", helper.absolutePos(leftPos))
                .add("b", helper.absolutePos(rightPos))
                .save(Objects.requireNonNull(manager.getDisk()));

        helper.succeedIfManagerDidThingWithoutLagging(manager, () -> {
            Item[] expected = new Item[]{
                    Items.NETHERITE_INGOT,
                    Items.GOLD_INGOT,
                    Items.COPPER_INGOT,
                    Items.SANDSTONE,
                    Items.STONE,
                    Items.OAK_LOG,
                    };
            int[] found = new int[expected.length];
            slots:
            for (int i = 0; i < rightChest.getSlots(); i++) {
                ItemStack stack = rightChest.getStackInSlot(i);
                if (stack.isEmpty()) continue;
                Item item = stack.getItem();
                for (int j = 0; j < expected.length; j++) {
                    if (item == expected[j]) {
                        found[j] += stack.getCount();
                        continue slots;
                    }
                }
                assertTrue(false, "Unexpected item in chest: " + item);
            }
            for (int i = 0; i < found.length; i++) {
                assertTrue(found[i] == 1, "Expected " + expected[i] + " to be 1, but was " + found[i]);
            }

        });
    }
}
