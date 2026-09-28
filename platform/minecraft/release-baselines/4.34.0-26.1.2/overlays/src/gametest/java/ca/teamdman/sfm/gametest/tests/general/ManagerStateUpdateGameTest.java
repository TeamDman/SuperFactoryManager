package ca.teamdman.sfm.gametest.tests.general;

import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import ca.teamdman.sfm.common.registry.registration.SFMBlocks;
import ca.teamdman.sfm.common.registry.registration.SFMItems;
import ca.teamdman.sfm.gametest.SFMGameTest;
import ca.teamdman.sfm.gametest.SFMGameTestDefinition;
import ca.teamdman.sfm.gametest.SFMGameTestHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;


/**
 * Ensure that the manager state gets updated as the disk is inserted and the program is set
 */
@SFMGameTest
public class ManagerStateUpdateGameTest extends SFMGameTestDefinition {
    @Override
    public String template() {
        return "1x2x1";
    }

    @Override
    public void run(SFMGameTestHelper helper) {
        helper.setBlock(new BlockPos(0, 2, 0), SFMBlocks.MANAGER.get());
        ManagerBlockEntity manager = helper.getBlockEntity(new BlockPos(0, 2, 0), ManagerBlockEntity.class);
        helper.assertTrue(manager.getState() == ManagerBlockEntity.State.NO_DISK, "Manager did not start with no disk");
        helper.assertTrue(manager.getDisk().isEmpty(), "Manager did not start with no disk");
        manager.setItem(0, new ItemStack(SFMItems.DISK.get()));
        helper.assertTrue(
                manager.getState() == ManagerBlockEntity.State.NO_PROGRAM,
                "Disk did not start with no program"
        );
        manager.setProgram("""
                                       EVERY 20 TICKS DO
                                           INPUT FROM a
                                           OUTPUT TO b
                                       END
                                   """.stripTrailing().stripIndent());
        helper.assertManagerRunning(manager);
        helper.succeed();
    }
}
