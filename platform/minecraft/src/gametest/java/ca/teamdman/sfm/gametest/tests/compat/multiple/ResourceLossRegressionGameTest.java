package ca.teamdman.sfm.gametest.tests.compat.multiple;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import ca.teamdman.sfm.common.label.LabelPositionHolder;
import ca.teamdman.sfm.common.logging.TranslatableLogEvent;
import ca.teamdman.sfm.common.registry.SFMWellKnownRegistries;
import ca.teamdman.sfm.common.registry.registration.SFMBlocks;
import ca.teamdman.sfm.common.registry.registration.SFMItems;
import ca.teamdman.sfm.common.util.SFMResourceLocation;
import ca.teamdman.sfm.gametest.SFMGameTest;
import ca.teamdman.sfm.gametest.SFMGameTestDefinition;
import ca.teamdman.sfm.gametest.SFMGameTestHelper;
import cofh.thermal.expansion.block.entity.machine.MachineInsolatorTile;
import mekanism.common.registries.MekanismBlocks;
import mekanism.common.tile.TileEntityBin;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import org.apache.logging.log4j.Level;

import java.util.ArrayDeque;
import java.util.Objects;




/**
 * Migrated from SFMThermalMekanismGameTests.resource_loss_regression
 */
@SuppressWarnings({
        "RedundantSuppression",
        "DataFlowIssue",
        "OptionalGetWithoutIsPresent",
        "DuplicatedCode",
        "ArraysAsListWithZeroOrOneArgument"
})
@SFMGameTest
public class ResourceLossRegressionGameTest extends SFMGameTestDefinition {

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
        helper.setBlock(leftPos, MekanismBlocks.CREATIVE_BIN.getBlock());
        TileEntityBin bin = helper.getBlockEntity(leftPos, TileEntityBin.class);
        bin.setStackInSlot(0, new ItemStack(Items.WHEAT_SEEDS, Integer.MAX_VALUE));

        var phytoBlock = SFMWellKnownRegistries.BLOCKS.get(SFMResourceLocation.fromNamespaceAndPath("thermal", "machine_insolator"));
        assert phytoBlock != null;
        helper.setBlock(rightPos, phytoBlock);
        MachineInsolatorTile phyto = helper.getBlockEntity(rightPos, MachineInsolatorTile.class);
        phyto.getItemInv().set(0, new ItemStack(Items.WHEAT_SEEDS, 63));

        // create the manager block and add the disk
        helper.setBlock(managerPos, SFMBlocks.MANAGER.get());
        ManagerBlockEntity manager = helper.getBlockEntity(managerPos, ManagerBlockEntity.class);
        manager.setItem(0, new ItemStack(SFMItems.DISK.get()));

        // create the program
        var program = """
                    NAME "move on pulse"
                                
                    EVERY REDSTONE PULSE DO
                        INPUT FROM left BOTTOM SIDE
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
        manager.setLogLevel(Level.ERROR);
        helper.assertTrue(manager.logger.getLogLevel() == Level.ERROR, "Log level should be trace");

        helper.succeedIfManagerDidThingWithoutLagging(manager, () -> {
            ItemStack phytoInputStack = phyto.getItemInv().get(0);
            helper.assertTrue(phytoInputStack.getItem() == Items.WHEAT_SEEDS, "Item should be wheat seeds");
            helper.assertTrue(phytoInputStack.getCount() == 64, "Item should be 64 wheat seeds");

            ArrayDeque<TranslatableLogEvent> logs = manager.logger.getLogs();
            int foundErrors = 0;
            for (TranslatableLogEvent log : logs) {
                if (log.level() == Level.ERROR) {
                    foundErrors++;
                    SFM.LOGGER.warn(
                            "Found error when should be none in resource_loss_regression test: {}",
                            log.contents()
                    );
                }
            }
            helper.assertTrue(foundErrors == 0, "No errors should be found in logs, found " + foundErrors);
        });

        // create the button
        helper.setBlock(buttonPos, Blocks.STONE_BUTTON);
        // push the button
        helper.pressButton(buttonPos);
    }
}
