package ca.teamdman.sfm.gametest.tests.migrated;

import ca.teamdman.sfm.common.blockentity.PrintingPressBlockEntity;
import ca.teamdman.sfm.common.item.FormItem;
import ca.teamdman.sfm.common.registry.registration.SFMBlocks;
import ca.teamdman.sfm.common.util.SFMItemUtils;
import ca.teamdman.sfm.gametest.SFMGameTest;
import ca.teamdman.sfm.gametest.SFMGameTestDefinition;
import ca.teamdman.sfm.gametest.SFMGameTestHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.state.BlockState;



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
        var pos = new BlockPos(0, 1, 0);
        helper.setBlock(pos, SFMBlocks.PRINTING_PRESS.get());
        var printingPress = helper.getBlockEntity(pos, PrintingPressBlockEntity.class);
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        // put black dye in player hand
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BLACK_DYE, 23));
        // right click on printing press
        BlockState pressState = helper.getBlockState(pos);
        helper.useBlock(pos, player);
        // assert the ink was inserted
        helper.assertTrue(!printingPress.getInk().isEmpty(), "Ink was not inserted", helper.getTick());
        helper.assertTrue(player.getMainHandItem().isEmpty(), "Ink was not taken from hand", helper.getTick());
        // put book in player hand
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BOOK));
        // right click on printing press
        helper.useBlock(pos, player);
        // assert the book was inserted
        helper.assertTrue(!printingPress.getPaper().isEmpty(), "Paper was not inserted", helper.getTick());
        helper.assertTrue(player.getMainHandItem().isEmpty(), "Paper was not taken from hand", helper.getTick());
        // put form in player hand
        var form = FormItem.createFormFromReference(new ItemStack(Items.WRITTEN_BOOK));
        player.setItemInHand(InteractionHand.MAIN_HAND, form.copy());
        // right click on printing press
        helper.useBlock(pos, player);
        // assert the form was inserted
        helper.assertTrue(!printingPress.getForm().isEmpty(), "Form was not inserted", helper.getTick());
        helper.assertTrue(player.getMainHandItem().isEmpty(), "Form was not taken from hand", helper.getTick());

        // pull out item
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        // right click on printing press
        helper.useBlock(pos, player);
        // assert the paper was extracted
        helper.assertTrue(printingPress.getPaper().isEmpty(), "Paper was not extracted", helper.getTick());
        helper.assertTrue(!player.getMainHandItem().isEmpty(), "Paper was not given to player", helper.getTick());
        helper.assertTrue(player.getMainHandItem().is(Items.BOOK), "Paper doesn't match", helper.getTick());
        helper.assertTrue(player.getMainHandItem().getCount() == 1, "Paper wrong count", helper.getTick());

        // pull out an item
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        // right click on printing press
        helper.useBlock(pos, player);
        // assert the form was extracted
        helper.assertTrue(printingPress.getForm().isEmpty(), "Form was not extracted", helper.getTick());
        helper.assertTrue(!player.getMainHandItem().isEmpty(), "Form was not given to player", helper.getTick());
        helper.assertTrue(SFMItemUtils.isSameItemSameTags(player.getMainHandItem(), form), "Form doesn't match", helper.getTick());
        // pull out item
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        // right click on printing press
        helper.useBlock(pos, player);
        // assert the ink was extracted
        helper.assertTrue(printingPress.getInk().isEmpty(), "Ink was not extracted", helper.getTick());
        helper.assertTrue(!player.getMainHandItem().isEmpty(), "Ink was not given to player", helper.getTick());
        helper.assertTrue(player.getMainHandItem().is(Items.BLACK_DYE), "Ink doesn't match", helper.getTick());
        helper.assertTrue(player.getMainHandItem().getCount() == 23, "Ink wrong count", helper.getTick());
        // try to pull out another item
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        // right click on printing press
        helper.useBlock(pos, player);
        // assert nothing was extracted
        helper.assertTrue(player.getMainHandItem().isEmpty(), "Nothing should have been extracted", helper.getTick());
        helper.succeed();
    }
}
