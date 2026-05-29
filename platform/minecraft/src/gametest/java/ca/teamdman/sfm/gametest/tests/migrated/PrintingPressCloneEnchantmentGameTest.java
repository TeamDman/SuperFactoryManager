package ca.teamdman.sfm.gametest.tests.migrated;

import ca.teamdman.sfm.common.blockentity.PrintingPressBlockEntity;
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
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Objects;

import static ca.teamdman.sfm.gametest.SFMGameTestMethodHelpers.assertTrue;


/**
 * Migrated from SFMCorrectnessGameTests.printing_press_clone_enchantment
 */
@SuppressWarnings({
        "RedundantSuppression",
        "DataFlowIssue",
        "OptionalGetWithoutIsPresent",
        "DuplicatedCode",
        "ArraysAsListWithZeroOrOneArgument",
        "deprecation"
})
@SFMGameTest
public class PrintingPressCloneEnchantmentGameTest extends SFMGameTestDefinition {

    @Override
    public String template() {

        return "3x4x3";
    }

    @Override
    public void run(SFMGameTestHelper helper) {

        var printingPos = new BlockPos(1, 1, 1);
        var pistonPos = new BlockPos(1, 3, 1);
        var woodPos = new BlockPos(0, 3, 1);
        var buttonPos = new BlockPos(0, 3, 0);
        var chestPos = new BlockPos(0, 1, 1);

        helper.setBlock(printingPos, SFMBlocks.PRINTING_PRESS.get());
        helper.setBlock(pistonPos, Blocks.PISTON.defaultBlockState().setValue(DirectionalBlock.FACING, Direction.DOWN));
        helper.setBlock(woodPos, Blocks.OAK_PLANKS);
        helper.setBlock(buttonPos, Blocks.STONE_BUTTON);
        helper.setBlock(chestPos, SFMBlocks.TEST_BARREL.get());

        var printingPress = helper.getBlockEntity(printingPos, PrintingPressBlockEntity.class);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);

        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(SFMItems.EXPERIENCE_GOOP.get(), 10));
        BlockState pressState = helper.getBlockState(printingPos);
        helper.useBlock(printingPos, player);

        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BOOK));
        helper.useBlock(printingPos, player);

        ItemStack reference = helper.createEnchantmentEntry(Enchantments.SHARPNESS, 3)
                .createEnchantedBook();

        player.setItemInHand(InteractionHand.MAIN_HAND, FormItem.createFormFromReference(reference));
        helper.useBlock(printingPos, player);

        // Activate printing press
        helper.useBlock(buttonPos, player);

        helper.runAfterDelay(
                5, () -> {
                    helper.useBlock(printingPos, player);
                    ItemStack held = player.getMainHandItem();

                    // Fail if result is not a clone of the enchantment
                    if (!ItemStack.isSameItemSameComponents(held, reference)) {
                        helper.fail("cloned item wasn't same");
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
                    assertTrue(printingPress.getInk().getCount() == 9, "Ink was not consumed properly");
                    assertTrue(printingPress.getPaper().isEmpty(), "Paper was not consumed");
                    assertTrue(!printingPress.getForm().isEmpty(), "Form should not be consumed");

                    // Succeed test
                    helper.succeed();
                }
        );
    }

}
