package ca.teamdman.sfm.gametest.tests.migrated;

import ca.teamdman.sfm.common.util.SFMItemUtils;
import ca.teamdman.sfm.gametest.SFMGameTest;
import ca.teamdman.sfm.gametest.SFMGameTestDefinition;
import ca.teamdman.sfm.gametest.SFMGameTestHelper;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.items.wrapper.InvWrapper;

import static ca.teamdman.sfm.gametest.SFMGameTestMethodHelpers.assertTrue;

/**
 * Migrated from SFMCorrectnessGameTests.inv_wrapper_investigation
 */
@SuppressWarnings({
        "RedundantSuppression",
        "DataFlowIssue",
        "OptionalGetWithoutIsPresent",
        "DuplicatedCode",
        "ArraysAsListWithZeroOrOneArgument"
})
@SFMGameTest
public class InvWrapperInvestigationGameTest extends SFMGameTestDefinition {

    @Override
    public String template() {
        return "1x1x1";
    }

    @Override
    public void run(SFMGameTestHelper helper) {
        try {
            for (int stackSize : new int[]{200, 64}) {
                InvWrapper inv = new InvWrapper(new SimpleContainer(1));
                ItemStack insertParam = new ItemStack(Items.DIRT, stackSize);
                ItemStack insertParamCopy = insertParam.copy();
                ItemStack ignoredInsertResult = inv.insertItem(0, insertParam, false);
                assertTrue(
                        SFMItemUtils.isSameItemSameAmount(insertParam, insertParamCopy),
                        "stackSize="
                        + stackSize
                        + " insert param should not be modified after insertion, is now "
                        + insertParam
                );
                assertTrue(
                        inv.getStackInSlot(0) != insertParam,
                        "stackSize="
                        + stackSize
                        + " the inventory shouldn't take ownership of the reference after insertion"
                );
                ItemStack extractResult = inv.extractItem(0, stackSize, false);
                assertTrue(
                        SFMItemUtils.isSameItemSameAmount(insertParam, insertParamCopy),
                        "stackSize="
                        + stackSize
                        + " insert param should not be modified after extraction, is now "
                        + insertParam
                );
                assertTrue(
                        SFMItemUtils.isSameItemSameAmount(insertParam, extractResult),
                        "stackSize=" + stackSize + " extract result should match insertion param"
                );
            }
        } catch (GameTestAssertException e) {
            helper.succeed();
            // we expect this to fail because it is taking ownership on insertion when stack fits in slot
            // this isn't correct behaviour but we have to succeed the test when our expectations are met
        }
    }
}
