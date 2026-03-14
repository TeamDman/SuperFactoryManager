package ca.teamdman.sfm.gametest.tests.migrated;

import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import ca.teamdman.sfm.common.label.LabelPositionHolder;
import ca.teamdman.sfm.common.registry.SFMBlocks;
import ca.teamdman.sfm.common.registry.SFMItems;
import ca.teamdman.sfm.gametest.SFMGameTest;
import ca.teamdman.sfm.gametest.SFMGameTestDefinition;
import ca.teamdman.sfm.gametest.SFMGameTestHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import org.apache.logging.log4j.Level;

import java.util.Objects;

import static ca.teamdman.sfm.gametest.SFMGameTestMethodHelpers.assertTrue;


/**
 * Migrated from SFMCorrectnessGameTests.move_on_pulse
 */
@SuppressWarnings({
        "RedundantSuppression",
        "DataFlowIssue",
        "OptionalGetWithoutIsPresent",
        "DuplicatedCode",
        "ArraysAsListWithZeroOrOneArgument"
})
@SFMGameTest
public class MoveOnPulseGameTest extends SFMGameTestDefinition {

    @Override
    public String template() {
        return "3x4x3";
    }

    @Override
    public void run(SFMGameTestHelper helper) {
        var managerPos = new BlockPos(1, 2, 1);
        var buttonPos = managerPos.offset(Direction.NORTH.getNormal());
        var leftPos = new BlockPos(2, 2, 1);
        var rightPos = new BlockPos(0, 2, 1);

        // place and fill the chests
        helper.setBlock(leftPos, SFMBlocks.TEST_BARREL_BLOCK.get());
        helper.setBlock(rightPos, SFMBlocks.TEST_BARREL_BLOCK.get());
        var left = (BarrelBlockEntity) helper.getBlockEntity(leftPos);
        var right = (BarrelBlockEntity) helper.getBlockEntity(rightPos);
        left.setItem(0, new ItemStack(Items.IRON_INGOT, 64));

        // create the manager block and add the disk
        helper.setBlock(managerPos, SFMBlocks.MANAGER_BLOCK.get());
        ManagerBlockEntity manager = (ManagerBlockEntity) helper.getBlockEntity(managerPos);
        manager.setItem(0, new ItemStack(SFMItems.DISK_ITEM.get()));

        // create the program
        var program = """
                    NAME "move on pulse"
                                
                    EVERY REDSTONE PULSE DO
                        INPUT FROM left
                        OUTPUT TO right
                    END
                """.stripTrailing().stripIndent();

        // set the labels
        LabelPositionHolder.empty()
                .add("left", helper.absolutePos(leftPos))
                .add("right", helper.absolutePos(rightPos))
                .save(Objects.requireNonNull(manager.getDisk()));

        // load the program
        manager.setProgram(program);
        manager.setLogLevel(Level.TRACE);
        assertTrue(manager.logger.getLogLevel() == Level.TRACE, "Log level should be trace");

        helper.succeedIfManagerDidThingWithoutLagging(manager, () -> {
            assertTrue(left.getItem(0).isEmpty(), "Iron should depart");
            assertTrue(right.getItem(0).getCount() == 64, "Iron should arrive");
        });

        // create the button
        helper.setBlock(buttonPos, Blocks.STONE_BUTTON);
        // push the button
        helper.pressButton(buttonPos);
    }
}
