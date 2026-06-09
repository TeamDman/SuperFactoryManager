package ca.teamdman.sfm.gametest.tests.migrated;

import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import ca.teamdman.sfm.common.label.LabelPositionHolder;
import ca.teamdman.sfm.common.registry.registration.SFMBlocks;
import ca.teamdman.sfm.common.registry.registration.SFMItems;
import ca.teamdman.sfm.gametest.SFMGameTest;
import ca.teamdman.sfm.gametest.SFMGameTestDefinition;
import ca.teamdman.sfm.gametest.SFMGameTestHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

import java.util.Objects;



/**
 * Migrated from SFMCorrectnessGameTests.pattern_cache_regression_2
 */
@SuppressWarnings({
        "RedundantSuppression",
        "DataFlowIssue",
        "OptionalGetWithoutIsPresent",
        "DuplicatedCode",
        "ArraysAsListWithZeroOrOneArgument"
})
@SFMGameTest
public class PatternCacheRegression2GameTest extends SFMGameTestDefinition {

    @Override
    public String template() {
        return "3x2x1";
    }

    @Override
    public void run(SFMGameTestHelper helper) {
        helper.setBlock(new BlockPos(1, 1, 0), SFMBlocks.MANAGER.get());
        BlockPos rightPos = new BlockPos(0, 1, 0);
        helper.setBlock(rightPos, SFMBlocks.TEST_BARREL.get());
        BlockPos leftPos = new BlockPos(2, 1, 0);
        helper.setBlock(leftPos, SFMBlocks.TEST_BARREL.get());

        var rightChest = helper.getItemHandler(rightPos);
        var leftChest = helper.getItemHandler(leftPos);

        leftChest.insertItem(0, new ItemStack(Blocks.IRON_BLOCK, 64), false);

        ManagerBlockEntity manager = helper.getBlockEntity(new BlockPos(1, 1, 0), ManagerBlockEntity.class);
        manager.setItem(0, new ItemStack(SFMItems.DISK.get()));
        manager.setProgram("""
                                       EVERY 20 TICKS DO
                                           -- pattern caching behaviour should not short circuit this to match all
                                           -- since underscores wouldn't be matched by this
                                           INPUT "[a-zA-Z]*" FROM a
                                           OUTPUT TO b
                                       END
                                   """.stripTrailing().stripIndent());

        // set the labels
        LabelPositionHolder.empty()
                .add("a", helper.absolutePos(leftPos))
                .add("b", helper.absolutePos(rightPos))
                .save(Objects.requireNonNull(manager.getDisk()));

        helper.succeedIfManagerDidThingWithoutLagging(manager, () -> {
            boolean success1 = leftChest.getStackInSlot(0).getCount() == 64;
            helper.getTick();
            helper.assertTrue(success1, "should not depart");
            boolean success = rightChest.getStackInSlot(0).isEmpty();
            helper.getTick();
            helper.assertTrue(success, "should not arrive");

        });
    }
}
