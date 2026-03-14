package ca.teamdman.sfm.gametest.tests.migrated;

import ca.teamdman.sfm.common.blockentity.PrintingPressBlockEntity;
import ca.teamdman.sfm.common.item.FormItem;
import ca.teamdman.sfm.common.registry.SFMBlocks;
import ca.teamdman.sfm.common.util.SFMItemUtils;
import ca.teamdman.sfm.gametest.SFMGameTest;
import ca.teamdman.sfm.gametest.SFMGameTestDefinition;
import ca.teamdman.sfm.gametest.SFMGameTestHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import static ca.teamdman.sfm.gametest.SFMGameTestMethodHelpers.assertTrue;

/**
 * Migrated from SFMCorrectnessGameTests.printing_press_insertion_extraction
 */
@SuppressWarnings({
        "RedundantSuppression",
        "DataFlowIssue",
        "OptionalGetWithoutIsPresent",
        "DuplicatedCode",
        "ArraysAsListWithZeroOrOneArgument"
})
@SFMGameTest
public class PrintingPressInsertionExtractionGameTest extends SFMGameTestDefinition {

    @Override
    public String template() {
        return "1x2x1";
    }

    @Override
    public void run(SFMGameTestHelper helper) {
        var pos = new BlockPos(0, 2, 0);
        helper.setBlock(pos, SFMBlocks.PRINTING_PRESS_BLOCK.get());
        var printingPress = (PrintingPressBlockEntity) helper.getBlockEntity(pos);
        var player = helper.makeMockPlayer();
        // put black dye in player hand
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BLACK_DYE, 23));
        // right click on printing press
        BlockState pressState = helper.getBlockState(pos);
        pressState.getBlock().use(
                pressState,
                helper.getLevel(),
                helper.absolutePos(pos),
                player,
                InteractionHand.MAIN_HAND,
                new BlockHitResult(
                        new Vec3(0.5, 0.5, 0.5),
                        Direction.UP,
                        helper.absolutePos(pos),
                        false
                )
        );
        // assert the ink was inserted
        assertTrue(!printingPress.getInk().isEmpty(), "Ink was not inserted");
        assertTrue(player.getMainHandItem().isEmpty(), "Ink was not taken from hand");
        // put book in player hand
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BOOK));
        // right click on printing press
        pressState.getBlock().use(
                pressState,
                helper.getLevel(),
                helper.absolutePos(pos),
                player,
                InteractionHand.MAIN_HAND,
                new BlockHitResult(
                        new Vec3(0.5, 0.5, 0.5),
                        Direction.UP,
                        helper.absolutePos(pos),
                        false
                )
        );
        // assert the book was inserted
        assertTrue(!printingPress.getPaper().isEmpty(), "Paper was not inserted");
        assertTrue(player.getMainHandItem().isEmpty(), "Paper was not taken from hand");
        // put form in player hand
        var form = FormItem.createFormFromReference(new ItemStack(Items.WRITTEN_BOOK));
        player.setItemInHand(InteractionHand.MAIN_HAND, form.copy());
        // right click on printing press
        pressState.getBlock().use(
                pressState,
                helper.getLevel(),
                helper.absolutePos(pos),
                player,
                InteractionHand.MAIN_HAND,
                new BlockHitResult(
                        new Vec3(0.5, 0.5, 0.5),
                        Direction.UP,
                        helper.absolutePos(pos),
                        false
                )
        );
        // assert the form was inserted
        assertTrue(!printingPress.getForm().isEmpty(), "Form was not inserted");
        assertTrue(player.getMainHandItem().isEmpty(), "Form was not taken from hand");

        // pull out item
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        // right click on printing press
        pressState.getBlock().use(
                pressState,
                helper.getLevel(),
                helper.absolutePos(pos),
                player,
                InteractionHand.MAIN_HAND,
                new BlockHitResult(
                        new Vec3(0.5, 0.5, 0.5),
                        Direction.UP,
                        helper.absolutePos(pos),
                        false
                )
        );
        // assert the paper was extracted
        assertTrue(printingPress.getPaper().isEmpty(), "Paper was not extracted");
        assertTrue(!player.getMainHandItem().isEmpty(), "Paper was not given to player");
        assertTrue(player.getMainHandItem().is(Items.BOOK), "Paper doesn't match");
        assertTrue(player.getMainHandItem().getCount() == 1, "Paper wrong count");

        // pull out an item
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        // right click on printing press
        pressState.getBlock().use(
                pressState,
                helper.getLevel(),
                helper.absolutePos(pos),
                player,
                InteractionHand.MAIN_HAND,
                new BlockHitResult(
                        new Vec3(0.5, 0.5, 0.5),
                        Direction.UP,
                        helper.absolutePos(pos),
                        false
                )
        );
        // assert the form was extracted
        assertTrue(printingPress.getForm().isEmpty(), "Form was not extracted");
        assertTrue(!player.getMainHandItem().isEmpty(), "Form was not given to player");
        assertTrue(SFMItemUtils.isSameItemSameTags(player.getMainHandItem(), form), "Form doesn't match");
        // pull out item
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        // right click on printing press
        pressState.getBlock().use(
                pressState,
                helper.getLevel(),
                helper.absolutePos(pos),
                player,
                InteractionHand.MAIN_HAND,
                new BlockHitResult(
                        new Vec3(0.5, 0.5, 0.5),
                        Direction.UP,
                        helper.absolutePos(pos),
                        false
                )
        );
        // assert the ink was extracted
        assertTrue(printingPress.getInk().isEmpty(), "Ink was not extracted");
        assertTrue(!player.getMainHandItem().isEmpty(), "Ink was not given to player");
        assertTrue(player.getMainHandItem().is(Items.BLACK_DYE), "Ink doesn't match");
        assertTrue(player.getMainHandItem().getCount() == 23, "Ink wrong count");
        // try to pull out another item
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        // right click on printing press
        pressState.getBlock().use(
                pressState,
                helper.getLevel(),
                helper.absolutePos(pos),
                player,
                InteractionHand.MAIN_HAND,
                new BlockHitResult(
                        new Vec3(0.5, 0.5, 0.5),
                        Direction.UP,
                        helper.absolutePos(pos),
                        false
                )
        );
        // assert nothing was extracted
        assertTrue(player.getMainHandItem().isEmpty(), "Nothing should have been extracted");
        helper.succeed();
    }
}
