package ca.teamdman.sfm.gametest.tests.migrated;

import ca.teamdman.sfm.common.util.SFMItemUtils;
import ca.teamdman.sfm.gametest.SFMGameTest;
import ca.teamdman.sfm.gametest.SFMGameTestDefinition;
import ca.teamdman.sfm.gametest.SFMGameTestHelper;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.items.wrapper.InvWrapper;



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
                boolean success3 = SFMItemUtils.isSameItemSameAmount(insertParam, insertParamCopy);
                helper.getTick();
                helper.assertTrue(
                        success3, "stackSize="
                                  + stackSize
                                  + " insert param should not be modified after insertion, is now "
                                  + insertParam
                );
                boolean success2 = inv.getStackInSlot(0) != insertParam;
                helper.getTick();
                helper.assertTrue(
                        success2, "stackSize="
                                  + stackSize
                                  + " the inventory shouldn't take ownership of the reference after insertion"
                );
                ItemStack extractResult = inv.extractItem(0, stackSize, false);
                boolean success1 = SFMItemUtils.isSameItemSameAmount(insertParam, insertParamCopy);
                helper.getTick();
                helper.assertTrue(
                        success1, "stackSize="
                                  + stackSize
                                  + " insert param should not be modified after extraction, is now "
                                  + insertParam
                );
                boolean success = SFMItemUtils.isSameItemSameAmount(insertParam, extractResult);
                helper.getTick();
                helper.assertTrue(success, "stackSize=" + stackSize + " extract result should match insertion param");
            }
        } catch (GameTestAssertException e) {
            helper.succeed();
            // we expect this to fail because it is taking ownership on insertion when stack fits in slot
            // this isn't correct behaviour but we have to succeed the test when our expectations are met
        }
    }
}
