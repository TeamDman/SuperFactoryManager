package ca.teamdman.sfm.gametest.tests.compat.computercraft;

import ca.teamdman.sfm.gametest.SFMGameTest;
import ca.teamdman.sfm.gametest.SFMGameTestDefinition;
import ca.teamdman.sfm.gametest.SFMGameTestHelper;
import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
import dan200.computercraft.api.ComputerCraftAPI;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;

@SFMGameTest
public class ComputerCraftDependencySmokeGameTest extends SFMGameTestDefinition {
    @Override
    public String template() {
        return "2x2x2";
    }

    @Override
    @MCVersionDependentBehaviour // CC:Tweaked 1.105.0+
    public void run(SFMGameTestHelper helper) {
        helper.assertTrue(
                "computercraft".equals(ComputerCraftAPI.MOD_ID),
                "Unexpected CC:Tweaked mod id: " + ComputerCraftAPI.MOD_ID
        );
        helper.assertTrue(
                ComputerCraftAPI.getInstalledVersion() != null,
                "CC:Tweaked API did not report an installed version"
        );

        var turtlePos = new BlockPos(0, 2, 0);
        var diskDrivePos = new BlockPos(1, 2, 0);
        var turtle = BuiltInRegistries.BLOCK.get(new ResourceLocation(ComputerCraftAPI.MOD_ID, "turtle_normal"));
        var diskDrive = BuiltInRegistries.BLOCK.get(new ResourceLocation(ComputerCraftAPI.MOD_ID, "disk_drive"));
        helper.assertTrue(turtle != Blocks.AIR, "CC:Tweaked did not register turtle_normal");
        helper.assertTrue(diskDrive != Blocks.AIR, "CC:Tweaked did not register disk_drive");
        helper.setBlock(turtlePos, turtle);
        helper.setBlock(diskDrivePos, diskDrive);

        helper.assertTrue(
                helper.getBlockState(turtlePos).is(turtle),
                "Failed to place a CC:Tweaked turtle"
        );
        helper.assertTrue(
                helper.getBlockState(diskDrivePos).is(diskDrive),
                "Failed to place a CC:Tweaked disk drive"
        );
        helper.succeed();
    }
}
