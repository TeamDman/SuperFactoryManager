package ca.teamdman.sfm.gametest.tests.compat.computercraft;

import ca.teamdman.sfm.gametest.SFMGameTest;
import ca.teamdman.sfm.gametest.SFMGameTestDefinition;
import ca.teamdman.sfm.gametest.SFMGameTestHelper;
import dan200.computercraft.ComputerCraft;
import dan200.computercraft.api.ComputerCraftAPI;
import dan200.computercraft.shared.Registry;
import net.minecraft.core.BlockPos;

@SFMGameTest
public class ComputerCraftDependencySmokeGameTest extends SFMGameTestDefinition {
    @Override
    public String template() {
        return "2x2x2";
    }

    @Override
    public void run(SFMGameTestHelper helper) {
        helper.assertTrue(
                "computercraft".equals(ComputerCraft.MOD_ID),
                "Unexpected CC:Tweaked mod id: " + ComputerCraft.MOD_ID
        );
        helper.assertTrue(
                ComputerCraftAPI.getInstalledVersion() != null,
                "CC:Tweaked API did not report an installed version"
        );

        var turtlePos = new BlockPos(0, 2, 0);
        var diskDrivePos = new BlockPos(1, 2, 0);
        helper.setBlock(turtlePos, Registry.ModBlocks.TURTLE_NORMAL.get());
        helper.setBlock(diskDrivePos, Registry.ModBlocks.DISK_DRIVE.get());

        helper.assertTrue(
                helper.getBlockState(turtlePos).is(Registry.ModBlocks.TURTLE_NORMAL.get()),
                "Failed to place a CC:Tweaked turtle"
        );
        helper.assertTrue(
                helper.getBlockState(diskDrivePos).is(Registry.ModBlocks.DISK_DRIVE.get()),
                "Failed to place a CC:Tweaked disk drive"
        );
        helper.succeed();
    }
}
