package ca.teamdman.sfm.gametest.tests.migrated;

import ca.teamdman.sfm.common.blockentity.PrintingPressBlockEntity;
import ca.teamdman.sfm.common.item.DiskItem;
import ca.teamdman.sfm.common.item.FormItem;
import ca.teamdman.sfm.common.registry.SFMBlocks;
import ca.teamdman.sfm.common.registry.SFMItems;
import ca.teamdman.sfm.gametest.SFMGameTest;
import ca.teamdman.sfm.gametest.SFMGameTestDefinition;
import ca.teamdman.sfm.gametest.SFMGameTestHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Objects;

import static ca.teamdman.sfm.gametest.SFMGameTestMethodHelpers.assertTrue;


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
        var printingPos = new BlockPos(1, 2, 1);
        var pistonPos = new BlockPos(1, 4, 1);
        var woodPos = new BlockPos(0, 4, 1);
        var buttonPos = new BlockPos(0, 4, 0);
        var chestPos = new BlockPos(0, 2, 1);

        helper.setBlock(printingPos, SFMBlocks.PRINTING_PRESS_BLOCK.get());
        helper.setBlock(pistonPos, Blocks.PISTON.defaultBlockState().setValue(DirectionalBlock.FACING, Direction.DOWN));
        helper.setBlock(woodPos, Blocks.OAK_PLANKS);
        helper.setBlock(buttonPos, Blocks.STONE_BUTTON);
        helper.setBlock(chestPos, SFMBlocks.TEST_BARREL_BLOCK.get());

        var printingPress = (PrintingPressBlockEntity) helper.getBlockEntity(printingPos);
        Player player = helper.makeMockPlayer();
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BLACK_DYE));
        BlockState pressState = helper.getBlockState(printingPos);
        pressState.getBlock().use(
                pressState,
                helper.getLevel(),
                helper.absolutePos(printingPos),
                player,
                InteractionHand.MAIN_HAND,
                new BlockHitResult(
                        new Vec3(0.5, 0.5, 0.5),
                        Direction.UP,
                        helper.absolutePos(printingPos),
                        false
                )
        );
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(SFMItems.DISK_ITEM.get()));
        pressState.getBlock().use(
                pressState,
                helper.getLevel(),
                helper.absolutePos(printingPos),
                player,
                InteractionHand.MAIN_HAND,
                new BlockHitResult(
                        new Vec3(0.5, 0.5, 0.5),
                        Direction.UP,
                        helper.absolutePos(printingPos),
                        false
                )
        );
        var disk = new ItemStack(SFMItems.DISK_ITEM.get());
        DiskItem.setProgram(disk, """
                    EVERY 20 TICKS DO
                        INPUT FROM a TOP SIDE SLOTS 0,1,3-4,5
                        OUTPUT TO a SLOTS 2
                    END
                """.stripTrailing().stripIndent());
        player.setItemInHand(InteractionHand.MAIN_HAND, FormItem.createFormFromReference(disk));
        pressState.getBlock().use(
                pressState,
                helper.getLevel(),
                helper.absolutePos(printingPos),
                player,
                InteractionHand.MAIN_HAND,
                new BlockHitResult(
                        new Vec3(0.5, 0.5, 0.5),
                        Direction.UP,
                        helper.absolutePos(printingPos),
                        false
                )
        );

        BlockState buttonState = helper.getBlockState(buttonPos);
        buttonState.getBlock().use(
                buttonState,
                helper.getLevel(),
                helper.absolutePos(buttonPos),
                player,
                InteractionHand.MAIN_HAND,
                new BlockHitResult(
                        new Vec3(0.5, 0.5, 0.5),
                        Direction.UP,
                        helper.absolutePos(printingPos),
                        false
                )
        );

        helper.runAfterDelay(5, () -> {
            pressState.getBlock().use(
                    pressState,
                    helper.getLevel(),
                    helper.absolutePos(printingPos),
                    player,
                    InteractionHand.MAIN_HAND,
                    new BlockHitResult(
                            new Vec3(0.5, 0.5, 0.5),
                            Direction.UP,
                            helper.absolutePos(printingPos),
                            false
                    )
            );
            ItemStack held = player.getMainHandItem();

            // Fail if the result is not a perfect clone of the disk
            if (!held.is(SFMItems.DISK_ITEM.get()) || !DiskItem.getProgramString(held).equals(DiskItem.getProgramString(disk))) {
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
            assertTrue(printingPress.getInk().isEmpty(), "Ink was not consumed");
            assertTrue(printingPress.getPaper().isEmpty(), "Paper was not consumed");
            assertTrue(!printingPress.getForm().isEmpty(), "Form should not be consumed");

            // Succeed test
            helper.succeed();
        });
    }
}
