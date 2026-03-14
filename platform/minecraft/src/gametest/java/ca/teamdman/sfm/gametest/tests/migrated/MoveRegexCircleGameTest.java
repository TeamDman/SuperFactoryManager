package ca.teamdman.sfm.gametest.tests.migrated;

import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
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



/**
 * Migrated from SFMPerformanceGameTests.move_regex_circle
 */
@SuppressWarnings({
        "RedundantSuppression",
        "DataFlowIssue",
        "OptionalGetWithoutIsPresent",
        "DuplicatedCode",
        "ArraysAsListWithZeroOrOneArgument"
})
@SFMGameTest
public class MoveRegexCircleGameTest extends SFMGameTestDefinition {

    @Override
    public String template() {
        return "3x4x3";
    }

    @Override
    public String batchName() {
        return "laggy";
    }

    @Override
    public void run(SFMGameTestHelper helper) {
        var managerPos = new BlockPos(1, 2, 1);
        var aPos = new BlockPos(1, 2, 0);
        var bPos = new BlockPos(2, 2, 1);
        var cPos = new BlockPos(1, 2, 2);
        var dPos = new BlockPos(0, 2, 1);

        // place and fill the chests
        helper.setBlock(aPos, SFMBlocks.TEST_BARREL_BLOCK.get());
        helper.setBlock(bPos, SFMBlocks.TEST_BARREL_BLOCK.get());
        helper.setBlock(cPos, SFMBlocks.TEST_BARREL_BLOCK.get());
        helper.setBlock(dPos, SFMBlocks.TEST_BARREL_BLOCK.get());
        var a = (BarrelBlockEntity) helper.getBlockEntity(aPos);
        var b = (BarrelBlockEntity) helper.getBlockEntity(bPos);
        var c = (BarrelBlockEntity) helper.getBlockEntity(cPos);
        var d = (BarrelBlockEntity) helper.getBlockEntity(dPos);
        for (int i = 0; i < 27; i++) {
            if (i < 9) {
                a.setItem(i, new ItemStack(Items.IRON_INGOT, 64));
                b.setItem(i, new ItemStack(Items.GOLD_INGOT, 64));
                c.setItem(i, new ItemStack(Items.DIAMOND, 64));
            }
            d.setItem(i, new ItemStack(Items.COBBLESTONE, 64));
        }
        d.setItem(26, new ItemStack(Items.COPPER_INGOT, 64));

        // create the manager block and add the disk
        helper.setBlock(managerPos, SFMBlocks.MANAGER_BLOCK.get());
        ManagerBlockEntity manager = (ManagerBlockEntity) helper.getBlockEntity(managerPos);
        manager.setItem(0, new ItemStack(SFMItems.DISK_ITEM.get()));

        // create the program
        var program = """
                    NAME "move regex circle"
                                
                    EVERY 20 TICKS DO
                        INPUT *:*_ingot FROM a
                        OUTPUT TO b
                    END
                    EVERY 20 TICKS DO
                        INPUT *:*_ingot FROM b
                        OUTPUT TO c
                    END
                    EVERY 20 TICKS DO
                        INPUT *:*_ingot FROM c
                        OUTPUT TO d
                    END
                    EVERY 20 TICKS DO
                        INPUT *:*_ingot FROM d
                        OUTPUT TO a
                    END
                """.stripTrailing().stripIndent();

        // set the labels
        LabelPositionHolder.empty()
                .add("a", helper.absolutePos(aPos))
                .add("b", helper.absolutePos(bPos))
                .add("c", helper.absolutePos(cPos))
                .add("d", helper.absolutePos(dPos))
                .save(manager.getDisk());

        // load the program
        manager.setProgram(program);

        helper.succeedIfManagerDidThingWithoutLagging(manager, () -> {

        });
    }
}
