package ca.teamdman.sfm.gametest.tests.falling_anvil;

import ca.teamdman.sfm.common.item.DiskItem;
import ca.teamdman.sfm.common.item.FormItem;
import ca.teamdman.sfm.common.registry.SFMItems;
import ca.teamdman.sfm.common.util.SFMItemUtils;
import ca.teamdman.sfm.gametest.SFMGameTest;
import ca.teamdman.sfm.gametest.SFMGameTestDefinition;
import ca.teamdman.sfm.gametest.SFMGameTestHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Migrated from SFMCorrectnessGameTests.falling_anvil_program_form
 */
@SuppressWarnings({
        "RedundantSuppression",
        "DataFlowIssue",
        "OptionalGetWithoutIsPresent",
        "DuplicatedCode",
        "ArraysAsListWithZeroOrOneArgument"
})
@SFMGameTest
public class FallingAnvilProgramFormGameTest extends SFMGameTestDefinition {

    @Override
    public String template() {
        return "3x4x3";
    }

    @Override
    public void run(SFMGameTestHelper helper) {
        helper.setBlock(new BlockPos(1, 2, 1), Blocks.IRON_BLOCK);
        var pos = helper.absoluteVec(new Vec3(1.5, 3.5, 1.5));
        ItemStack disk = new ItemStack(SFMItems.DISK_ITEM.get());
        DiskItem.setProgram(disk, """
                    NAME "falling anvil test"
                    EVERY 20 TICKS DO
                        INPUT FROM a TOP SIDE SLOTS 0,1,3-4,5
                        OUTPUT TO a SLOTS 2
                    END
                """.stripTrailing().stripIndent());
        helper
                .getLevel()
                .addFreshEntity(new ItemEntity(
                        helper.getLevel(),
                        pos.x, pos.y, pos.z,
                        disk,
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
            if (found.stream().anyMatch(e -> SFMItemUtils.isSameItemSameTags(e.getItem(), FormItem.createFormFromReference(disk)))) {
                helper.succeed();
            } else {
                helper.fail("no form found");
            }
        });
    }
}
