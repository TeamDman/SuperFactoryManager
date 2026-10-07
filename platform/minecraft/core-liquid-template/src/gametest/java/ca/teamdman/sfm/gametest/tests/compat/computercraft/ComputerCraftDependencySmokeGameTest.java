package ca.teamdman.sfm.gametest.tests.compat.computercraft;

import ca.teamdman.sfm.gametest.SFMGameTest;
import ca.teamdman.sfm.gametest.SFMGameTestDefinition;
import ca.teamdman.sfm.gametest.SFMGameTestHelper;
{% case minecraft_version %}
{% when '1.19.2' %}
import dan200.computercraft.ComputerCraft;
{% when '1.19.4', '1.20', '1.20.1' %}
import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
{% endcase %}
import dan200.computercraft.api.ComputerCraftAPI;
{% case minecraft_version %}
{% when '1.19.2' %}
import dan200.computercraft.shared.Registry;
{% when '1.19.4', '1.20', '1.20.1' %}
{% endcase %}
import net.minecraft.core.BlockPos;
{% case minecraft_version %}
{% when '1.19.2' %}
{% when '1.19.4', '1.20', '1.20.1' %}
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
{% endcase %}

@SFMGameTest
public class ComputerCraftDependencySmokeGameTest extends SFMGameTestDefinition {
    @Override
    public String template() {
        return "2x2x2";
    }

    @Override
{% case minecraft_version %}
{% when '1.19.2' %}
{% when '1.19.4' %}
    @MCVersionDependentBehaviour // CC:Tweaked 1.108.0+
{% when '1.20' %}
    @MCVersionDependentBehaviour // CC:Tweaked 1.105.0+
{% when '1.20.1' %}
    @MCVersionDependentBehaviour
{% endcase %}
    public void run(SFMGameTestHelper helper) {
        helper.assertTrue(
{% case minecraft_version %}
{% when '1.19.2' %}
                "computercraft".equals(ComputerCraft.MOD_ID),
                "Unexpected CC:Tweaked mod id: " + ComputerCraft.MOD_ID
{% when '1.19.4', '1.20', '1.20.1' %}
                "computercraft".equals(ComputerCraftAPI.MOD_ID),
                "Unexpected CC:Tweaked mod id: " + ComputerCraftAPI.MOD_ID
{% endcase %}
        );
        helper.assertTrue(
                ComputerCraftAPI.getInstalledVersion() != null,
                "CC:Tweaked API did not report an installed version"
        );

        var turtlePos = new BlockPos(0, 2, 0);
        var diskDrivePos = new BlockPos(1, 2, 0);
{% case minecraft_version %}
{% when '1.19.2' %}
        helper.setBlock(turtlePos, Registry.ModBlocks.TURTLE_NORMAL.get());
        helper.setBlock(diskDrivePos, Registry.ModBlocks.DISK_DRIVE.get());
{% when '1.19.4', '1.20', '1.20.1' %}
        var turtle = BuiltInRegistries.BLOCK.get(new ResourceLocation(ComputerCraftAPI.MOD_ID, "turtle_normal"));
        var diskDrive = BuiltInRegistries.BLOCK.get(new ResourceLocation(ComputerCraftAPI.MOD_ID, "disk_drive"));
        helper.assertTrue(turtle != Blocks.AIR, "CC:Tweaked did not register turtle_normal");
        helper.assertTrue(diskDrive != Blocks.AIR, "CC:Tweaked did not register disk_drive");
        helper.setBlock(turtlePos, turtle);
        helper.setBlock(diskDrivePos, diskDrive);
{% endcase %}

        helper.assertTrue(
{% case minecraft_version %}
{% when '1.19.2' %}
                helper.getBlockState(turtlePos).is(Registry.ModBlocks.TURTLE_NORMAL.get()),
{% when '1.19.4', '1.20', '1.20.1' %}
                helper.getBlockState(turtlePos).is(turtle),
{% endcase %}
                "Failed to place a CC:Tweaked turtle"
        );
        helper.assertTrue(
{% case minecraft_version %}
{% when '1.19.2' %}
                helper.getBlockState(diskDrivePos).is(Registry.ModBlocks.DISK_DRIVE.get()),
{% when '1.19.4', '1.20', '1.20.1' %}
                helper.getBlockState(diskDrivePos).is(diskDrive),
{% endcase %}
                "Failed to place a CC:Tweaked disk drive"
        );
        helper.succeed();
    }
}
