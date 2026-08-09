package ca.teamdman.sfm.gametest.tests.migrated;

import ca.teamdman.sfm.common.registry.registration.SFMItems;
import ca.teamdman.sfm.gametest.SFMGameTest;
import ca.teamdman.sfm.gametest.SFMGameTestDefinition;
import ca.teamdman.sfm.gametest.SFMGameTestHelper;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;

/**
 * Migrated from SFMCorrectnessGameTests.disk_item_clientside_regression
 */
@SuppressWarnings({
        "RedundantSuppression",
        "DataFlowIssue",
        "OptionalGetWithoutIsPresent",
        "DuplicatedCode",
        "ArraysAsListWithZeroOrOneArgument"
})
@SFMGameTest
public class DiskItemClientsideRegressionGameTest extends SFMGameTestDefinition {

    @Override
    public String template() {
        return "1x2x1";
    }

    @Override
    public void run(SFMGameTestHelper helper) {
        var stack = new ItemStack(SFMItems.DISK.get());
        stack.getDisplayName();
        stack.getHoverName();
        stack.getItem().getName(stack);
        stack.getItem().appendHoverText(stack, helper.getLevel(), new ArrayList<>(), TooltipFlag.Default.NORMAL);
        Vec3 pos = helper.absoluteVec(new Vec3(0.5, 2, 0.5));
        ItemEntity itemEntity = new ItemEntity(helper.getLevel(), pos.x, pos.y, pos.z, stack, 0, 0, 0);
        helper.getLevel().addFreshEntity(itemEntity);
        helper.succeed();
    }
}
