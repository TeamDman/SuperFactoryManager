package ca.teamdman.sfm.gametest.tests.migrated;

import ca.teamdman.sfm.common.blockentity.PrintingPressBlockEntity;
import ca.teamdman.sfm.common.item.DiskItem;
import ca.teamdman.sfm.common.item.FormItem;
import ca.teamdman.sfm.common.registry.registration.SFMBlocks;
import ca.teamdman.sfm.common.registry.registration.SFMItems;
import ca.teamdman.sfm.gametest.SFMGameTest;
import ca.teamdman.sfm.gametest.SFMGameTestDefinition;
import ca.teamdman.sfm.gametest.SFMGameTestHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DirectionalBlock;

import java.util.Objects;




/**
 * Migrated from SFMCorrectnessGameTests.printing_press_clone_program
 */
@SuppressWarnings({
        "RedundantSuppression",
        "DataFlowIssue",
        "OptionalGetWithoutIsPresent",
        "DuplicatedCode",
        "ArraysAsListWithZeroOrOneArgument"
})
@SFMGameTest
public class PrintingPressCloneProgramGameTest extends SFMGameTestDefinition {

    @Override
    public String template() {
        return "3x4x3";
    }

    @Override
    public void run(SFMGameTestHelper helper) {
        // Positions
        var printingPos = new BlockPos(1, 1, 1);
        var pistonPos = new BlockPos(1, 3, 1);
        var woodPos = new BlockPos(0, 3, 1);
        var buttonPos = new BlockPos(0, 3, 0);
        var chestPos = new BlockPos(0, 1, 1);

        // Place blocks
        helper.setBlock(printingPos, SFMBlocks.PRINTING_PRESS.get());
        helper.setBlock(pistonPos, Blocks.PISTON.defaultBlockState().setValue(DirectionalBlock.FACING, Direction.DOWN));
        helper.setBlock(woodPos, Blocks.OAK_PLANKS);
        helper.setBlock(buttonPos, Blocks.STONE_BUTTON);
        helper.setBlock(chestPos, SFMBlocks.TEST_BARREL.get());

        // Get helper objects
        var printingPress = helper.getBlockEntity(printingPos, PrintingPressBlockEntity.class);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);

        // Place ink
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BLACK_DYE));
        helper.useBlock(printingPos, player);

        // Place paper
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(SFMItems.DISK.get()));
        helper.useBlock(printingPos, player);

        // Place form
        var disk = new ItemStack(SFMItems.DISK.get());
        DiskItem.setProgram(disk, """
                    EVERY 20 TICKS DO
                        INPUT FROM a TOP SIDE SLOTS 0,1,3-4,5
                        OUTPUT TO a SLOTS 2
                    END
                """.stripTrailing().stripIndent());
        ItemStack form = FormItem.createFormFromReference(disk);
        player.setItemInHand(InteractionHand.MAIN_HAND, form);
        helper.useBlock(printingPos, player);

        // Activate printing press
        helper.useBlock(buttonPos, player);

        // Completion criteria
        helper.runAfterDelay(5, () -> {
            // Pull out result
            helper.useBlock(printingPos, player);
            ItemStack held = player.getMainHandItem();

            // Fail if the result is not a perfect clone of the disk
            if (!held.is(SFMItems.DISK.get()) || !DiskItem.getProgramString(held).equals(DiskItem.getProgramString(disk))) {
                helper.fail("Disk was not cloned");
            }

            // Fail if the result is the same instance of ItemStack stored in the form
            ItemStack referenceStack = FormItem.getBorrowedReferenceFromForm(printingPress.getForm());
            if (Objects.equals(
                    System.identityHashCode(referenceStack),
                    System.identityHashCode(held)
            )) {
                helper.fail("cloned item shares the same ItemStack instance as form reference");
            }

            // Place result in chest
            var chest = helper.getItemHandler(chestPos);
            chest.insertItem(0, held, false);

            // Assert ingredient transformations
            helper.assertTrue(printingPress.getInk().isEmpty(), "Ink was not consumed");
            helper.assertTrue(printingPress.getPaper().isEmpty(), "Paper was not consumed");
            helper.assertTrue(!printingPress.getForm().isEmpty(), "Form should not be consumed");

            // Succeed test
            helper.succeed();
        });
    }
}
