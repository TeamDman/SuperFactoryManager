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
        boolean success17 = !printingPress.getInk().isEmpty();
        helper.getTick();
        helper.assertTrue(success17, "Ink was not inserted");
        boolean success16 = player.getMainHandItem().isEmpty();
        helper.getTick();
        helper.assertTrue(success16, "Ink was not taken from hand");
        // put book in player hand
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BOOK));
        // right click on printing press
        helper.useBlock(pos, player);
        // assert the book was inserted
        boolean success15 = !printingPress.getPaper().isEmpty();
        helper.getTick();
        helper.assertTrue(success15, "Paper was not inserted");
        boolean success14 = player.getMainHandItem().isEmpty();
        helper.getTick();
        helper.assertTrue(success14, "Paper was not taken from hand");
        // put form in player hand
        var form = FormItem.createFormFromReference(new ItemStack(Items.WRITTEN_BOOK));
        player.setItemInHand(InteractionHand.MAIN_HAND, form.copy());
        // right click on printing press
        helper.useBlock(pos, player);
        // assert the form was inserted
        boolean success13 = !printingPress.getForm().isEmpty();
        helper.getTick();
        helper.assertTrue(success13, "Form was not inserted");
        boolean success12 = player.getMainHandItem().isEmpty();
        helper.getTick();
        helper.assertTrue(success12, "Form was not taken from hand");

        // pull out item
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        // right click on printing press
        helper.useBlock(pos, player);
        // assert the paper was extracted
        boolean success11 = printingPress.getPaper().isEmpty();
        helper.getTick();
        helper.assertTrue(success11, "Paper was not extracted");
        boolean success10 = !player.getMainHandItem().isEmpty();
        helper.getTick();
        helper.assertTrue(success10, "Paper was not given to player");
        boolean success9 = player.getMainHandItem().is(Items.BOOK);
        helper.getTick();
        helper.assertTrue(success9, "Paper doesn't match");
        boolean success8 = player.getMainHandItem().getCount() == 1;
        helper.getTick();
        helper.assertTrue(success8, "Paper wrong count");

        // pull out an item
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        // right click on printing press
        helper.useBlock(pos, player);
        // assert the form was extracted
        boolean success7 = printingPress.getForm().isEmpty();
        helper.getTick();
        helper.assertTrue(success7, "Form was not extracted");
        boolean success6 = !player.getMainHandItem().isEmpty();
        helper.getTick();
        helper.assertTrue(success6, "Form was not given to player");
        boolean success5 = SFMItemUtils.isSameItemSameTags(player.getMainHandItem(), form);
        helper.getTick();
        helper.assertTrue(success5, "Form doesn't match");
        // pull out item
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        // right click on printing press
        helper.useBlock(pos, player);
        // assert the ink was extracted
        boolean success4 = printingPress.getInk().isEmpty();
        helper.getTick();
        helper.assertTrue(success4, "Ink was not extracted");
        boolean success3 = !player.getMainHandItem().isEmpty();
        helper.getTick();
        helper.assertTrue(success3, "Ink was not given to player");
        boolean success2 = player.getMainHandItem().is(Items.BLACK_DYE);
        helper.getTick();
        helper.assertTrue(success2, "Ink doesn't match");
        boolean success1 = player.getMainHandItem().getCount() == 23;
        helper.getTick();
        helper.assertTrue(success1, "Ink wrong count");
        // try to pull out another item
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        // right click on printing press
        helper.useBlock(pos, player);
        // assert nothing was extracted
        boolean success = player.getMainHandItem().isEmpty();
        helper.getTick();
        helper.assertTrue(success, "Nothing should have been extracted");
        helper.succeed();
    }
}
