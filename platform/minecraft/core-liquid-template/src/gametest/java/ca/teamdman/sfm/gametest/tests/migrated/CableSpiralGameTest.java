package ca.teamdman.sfm.gametest.tests.migrated;

import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
{% when '26.1.2' %}
import ca.teamdman.sfm.common.blockentity.TestBarrelBlockEntity;
{% endcase %}
import ca.teamdman.sfm.common.label.LabelPositionHolder;
import ca.teamdman.sfm.common.registry.registration.SFMBlocks;
import ca.teamdman.sfm.common.registry.registration.SFMItems;
import ca.teamdman.sfm.gametest.SFMGameTest;
import ca.teamdman.sfm.gametest.SFMGameTestDefinition;
import ca.teamdman.sfm.gametest.SFMGameTestHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Objects;


/**
 * Migrated from SFMCorrectnessGameTests.cable_spiral
 */
@SuppressWarnings({
        "RedundantSuppression",
        "DataFlowIssue",
        "OptionalGetWithoutIsPresent",
        "DuplicatedCode",
        "ArraysAsListWithZeroOrOneArgument"
})
@SFMGameTest
public class CableSpiralGameTest extends SFMGameTestDefinition {

    @Override
    public String template() {
        return "25x4x25";
    }

    @Override
    public void run(SFMGameTestHelper helper) {
        BlockPos start = new BlockPos(0, 2, 0);
        BlockPos end = new BlockPos(12, 2, 12);

        var len = 24;
        var dir = Direction.EAST;
        var current = start;
        while (len > 0) {
            // fill len blocks
            for (int i = 0; i < len; i++) {
                helper.setBlock(current, SFMBlocks.CABLE.get());
                current = current.relative(dir);
            }
            // turn right
            dir = dir.getClockWise();
            len -= 1;
        }

        // fill in the blocks needed for the test
        helper.setBlock(new BlockPos(1, 2, 0), SFMBlocks.MANAGER.get());
        helper.setBlock(start, SFMBlocks.TEST_BARREL.get());
        helper.setBlock(end, SFMBlocks.TEST_BARREL.get());

        // add some items
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
        Container startChest = (Container) helper.getBlockEntity(start);
{% when '26.1.2' %}
        Container startChest = helper.getBlockEntity(start, TestBarrelBlockEntity.class);
{% endcase %}
        startChest.setItem(0, new ItemStack(Items.IRON_INGOT, 64));
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
        Container endChest = (Container) helper.getBlockEntity(end);
{% when '26.1.2' %}
        Container endChest = helper.getBlockEntity(end, TestBarrelBlockEntity.class);
{% endcase %}


        ManagerBlockEntity manager = helper.getBlockEntity(new BlockPos(1, 2, 0), ManagerBlockEntity.class);
        manager.setItem(0, new ItemStack(SFMItems.DISK.get()));

        // set the labels
        LabelPositionHolder.empty()
                .add("a", helper.absolutePos(start))
                .add("b", helper.absolutePos(end))
                .save(Objects.requireNonNull(manager.getDisk()));

        // load the program
        manager.setProgram("""
                                       NAME "long cable test"
                                                                      
                                       EVERY 20 TICKS DO
                                           INPUT FROM a
                                           OUTPUT TO b
                                       END
                                   """.stripTrailing().stripIndent());

        helper.assertManagerRunning(manager);
        helper.succeedIfManagerDidThingWithoutLagging(manager, () -> {
            // ensure item arrived
            helper.assertTrue(endChest.getItem(0).getCount() == 64, "Items did not move");
            // ensure item left
            helper.assertTrue(startChest.getItem(0).isEmpty(), "Items did not leave");

        });
    }
}
