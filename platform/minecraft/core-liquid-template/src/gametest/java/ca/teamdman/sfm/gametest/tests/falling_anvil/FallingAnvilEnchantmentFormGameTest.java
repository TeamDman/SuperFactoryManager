package ca.teamdman.sfm.gametest.tests.falling_anvil;

import ca.teamdman.sfm.common.enchantment.SFMEnchantmentCollection;
import ca.teamdman.sfm.common.item.FormItem;
import ca.teamdman.sfm.common.util.SFMItemUtils;
import ca.teamdman.sfm.gametest.SFMGameTest;
import ca.teamdman.sfm.gametest.SFMGameTestDefinition;
import ca.teamdman.sfm.gametest.SFMGameTestHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Migrated from SFMCorrectnessGameTests.falling_anvil_enchantment_form
 */
@SuppressWarnings({
        "RedundantSuppression",
        "DataFlowIssue",
        "OptionalGetWithoutIsPresent",
        "DuplicatedCode",
        "ArraysAsListWithZeroOrOneArgument"
})
@SFMGameTest
public class FallingAnvilEnchantmentFormGameTest extends SFMGameTestDefinition {

    @Override
    public String template() {
        return "3x4x3";
    }

    @Override
    public void run(SFMGameTestHelper helper) {
        helper.setBlock(new BlockPos(1, 2, 1), Blocks.IRON_BLOCK);
        var pos = helper.absoluteVec(new Vec3(1.5, 3.5, 1.5));
        SFMEnchantmentCollection enchantments = new SFMEnchantmentCollection();
        enchantments.add(helper.createEnchantmentEntry(Enchantments.SHARPNESS, 3));
        ItemStack enchantedBookStack = enchantments.createEnchantedBook();

        helper
                .getLevel()
                .addFreshEntity(new ItemEntity(
                        helper.getLevel(),
                        pos.x, pos.y, pos.z,
                        enchantedBookStack,
                        0, 0, 0
                ));
        helper.setBlock(new BlockPos(1, 4, 1), Blocks.ANVIL);
        helper.runAfterDelay(20, () -> {
            List<ItemEntity> found = helper
                    .getLevel()
                    .getEntitiesOfClass(
                            ItemEntity.class,
                            new AABB(helper.absolutePos(new BlockPos(1, 4, 1))).inflate(3)
                    );
            if (found
                    .stream()
                    .anyMatch(e -> SFMItemUtils.isSameItemSameTags(e.getItem(), FormItem.createFormFromReference(enchantedBookStack)))) {
                helper.succeed();
            } else {
                helper.fail("no form found");
            }
        });
    }
}
