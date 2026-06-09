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
        helper.setBlock(new BlockPos(0, 1, 0), SFMBlocks.MANAGER.get());
        ManagerBlockEntity manager = helper.getBlockEntity(new BlockPos(0, 1, 0), ManagerBlockEntity.class);
        assert manager != null;
        boolean success2 = manager.getState() == ManagerBlockEntity.State.NO_DISK;
        helper.getTick();
        helper.assertTrue(success2, "Manager did not start with no disk");
        boolean success1 = manager.getDisk().isEmpty();
        helper.getTick();
        helper.assertTrue(success1, "Manager did not start with no disk");
        manager.setItem(0, new ItemStack(SFMItems.DISK.get()));
        boolean success = manager.getState() == ManagerBlockEntity.State.NO_PROGRAM;
        helper.getTick();
        helper.assertTrue(success, "Disk did not start with no program");
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
