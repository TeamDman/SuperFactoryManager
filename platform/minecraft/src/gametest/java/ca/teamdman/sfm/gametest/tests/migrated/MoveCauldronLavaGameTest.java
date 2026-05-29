package ca.teamdman.sfm.gametest.tests.migrated;

import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import ca.teamdman.sfm.common.label.LabelPositionHolder;
import ca.teamdman.sfm.common.registry.registration.SFMBlocks;
import ca.teamdman.sfm.common.registry.registration.SFMItems;
import ca.teamdman.sfm.gametest.SFMGameTest;
import ca.teamdman.sfm.gametest.SFMGameTestDefinition;
import ca.teamdman.sfm.gametest.SFMGameTestHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

import java.util.Objects;

import static ca.teamdman.sfm.gametest.SFMGameTestMethodHelpers.assertManagerRunning;


/**
 * Migrated from SFMCorrectnessGameTests.move_cauldron_lava
 */
@SuppressWarnings({
        "RedundantSuppression",
        "DataFlowIssue",
        "OptionalGetWithoutIsPresent",
        "DuplicatedCode",
        "ArraysAsListWithZeroOrOneArgument"
})
@SFMGameTest
public class MoveCauldronLavaGameTest extends SFMGameTestDefinition {

    @Override
    public String template() {
        return "3x2x1";
    }

    @Override
    public void run(SFMGameTestHelper helper) {
        // fill in the blocks needed for the test
        helper.setBlock(new BlockPos(1, 1, 0), SFMBlocks.MANAGER.get());
        BlockPos left = new BlockPos(2, 1, 0);
        helper.setBlock(left, Blocks.LAVA_CAULDRON.defaultBlockState());
        BlockPos right = new BlockPos(0, 1, 0);
        helper.setBlock(right, Blocks.CAULDRON);

        ManagerBlockEntity manager = helper.getBlockEntity(new BlockPos(1, 1, 0), ManagerBlockEntity.class);
        manager.setItem(0, new ItemStack(SFMItems.DISK.get()));

        // set the labels
        LabelPositionHolder.empty()
                .add("a", helper.absolutePos(left))
                .add("b", helper.absolutePos(right))
                .save(Objects.requireNonNull(manager.getDisk()));

        // load the program
        manager.setProgram("""
                                       NAME "cauldron lava test"
                                                                      
                                       EVERY 20 TICKS DO
                                           INPUT fluid:minecraft:lava FROM a
                                           OUTPUT fluid:*:* TO b
                                       END
                                   """.stripTrailing().stripIndent());

        assertManagerRunning(manager);
        helper.succeedIfManagerDidThingWithoutLagging(manager, () -> {
            helper.assertBlock(left, b -> b == Blocks.CAULDRON, _ -> Component.literal("cauldron didn't empty"));
            helper.assertBlockState(right, s -> s.getBlock() == Blocks.LAVA_CAULDRON, _ -> Component.literal("cauldron didn't fill"));

        });
    }
}
