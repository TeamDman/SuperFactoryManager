package ca.teamdman.sfm.gametest.tests.compat.ae2;

import appeng.blockentity.misc.InscriberBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import ca.teamdman.sfm.common.label.LabelPositionHolder;
import ca.teamdman.sfm.common.registry.registration.SFMBlocks;
import ca.teamdman.sfm.common.registry.registration.SFMItems;
import ca.teamdman.sfm.gametest.SFMGameTest;
import ca.teamdman.sfm.gametest.SFMGameTestDefinition;
import ca.teamdman.sfm.gametest.SFMGameTestHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StandingSignBlock;

import java.util.stream.Stream;



/**
 * Migrated from SFMAppliedEnergisticsCompatGameTests.ae2_inscribers
 */
@SuppressWarnings({
        "RedundantSuppression",
        "DataFlowIssue",
        "OptionalGetWithoutIsPresent",
        "DuplicatedCode",
        "ArraysAsListWithZeroOrOneArgument"
})
@SFMGameTest
public class Ae2InscribersGameTest extends SFMGameTestDefinition {

    @Override
    public String template() {
        return "7x3x3";
    }

    @Override
    public int maxTicks() {
        return 20 * 20;
    }

    @Override
    public void run(SFMGameTestHelper helper) {
        var managerPos = new BlockPos(0, 1, 1);

        helper.setBlock(managerPos, SFMBlocks.MANAGER.get());
        for (int i = 0; i < 6; i++) {
            helper.setBlock(new BlockPos(i + 1, 1, 1), SFMBlocks.CABLE.get());
        }

        var siliconPos1 = new BlockPos(4, 1, 1);
        var siliconPos2 = new BlockPos(5, 1, 1);
        var siliconPos3 = new BlockPos(6, 1, 1);
        var logicPos = new BlockPos(1, 1, 0);
        var engineeringPos = new BlockPos(1, 1, 0);
        var calculationPos = new BlockPos(2, 1, 0);
        var lastPos1 = new BlockPos(1, 1, 1);
        var lastPos2 = new BlockPos(2, 1, 1);
        var lastPos3 = new BlockPos(3, 1, 1);
        helper.setBlock(siliconPos1, AEBlocks.INSCRIBER.block());
        helper.setBlock(siliconPos2, AEBlocks.INSCRIBER.block());
        helper.setBlock(siliconPos3, AEBlocks.INSCRIBER.block());
        helper.setBlock(logicPos, AEBlocks.INSCRIBER.block());
        helper.setBlock(engineeringPos, AEBlocks.INSCRIBER.block());
        helper.setBlock(calculationPos, AEBlocks.INSCRIBER.block());
        helper.setBlock(lastPos1, AEBlocks.INSCRIBER.block());
        helper.setBlock(lastPos2, AEBlocks.INSCRIBER.block());
        helper.setBlock(lastPos3, AEBlocks.INSCRIBER.block());
        var silicon1 = helper.getBlockEntity(siliconPos1, InscriberBlockEntity.class);
        var silicon2 = helper.getBlockEntity(siliconPos2, InscriberBlockEntity.class);
        var silicon3 = helper.getBlockEntity(siliconPos3, InscriberBlockEntity.class);
        var logic = helper.getBlockEntity(logicPos, InscriberBlockEntity.class);
        var engineering = helper.getBlockEntity(engineeringPos, InscriberBlockEntity.class);
        var calculation = helper.getBlockEntity(calculationPos, InscriberBlockEntity.class);
        var last1 = helper.getBlockEntity(lastPos1, InscriberBlockEntity.class);
        var last2 = helper.getBlockEntity(lastPos2, InscriberBlockEntity.class);
        var last3 = helper.getBlockEntity(lastPos3, InscriberBlockEntity.class);
        helper.getItemHandler(siliconPos1)
                .insertItem(0, new ItemStack(AEItems.SILICON_PRESS), false);
        helper.getItemHandler(siliconPos2)
                .insertItem(0, new ItemStack(AEItems.SILICON_PRESS), false);
        helper.getItemHandler(siliconPos3)
                .insertItem(0, new ItemStack(AEItems.SILICON_PRESS), false);
        helper.getItemHandler(engineeringPos)
                .insertItem(0, new ItemStack(AEItems.ENGINEERING_PROCESSOR_PRESS), false);
        helper.getItemHandler(calculationPos)
                .insertItem(0, new ItemStack(AEItems.CALCULATION_PROCESSOR_PRESS), false);
        helper.getItemHandler(logicPos)
                .insertItem(0, new ItemStack(AEItems.LOGIC_PROCESSOR_PRESS), false);

        Stream
                .of(silicon1, silicon2, silicon3, logic, engineering, calculation, last1, last2, last3)
                .map(InscriberBlockEntity::getUpgrades)
                .forEach(upgradeInventory -> {
                    for (int slot = 0; slot < upgradeInventory.size(); slot++) {
                        upgradeInventory.insertItem(slot, new ItemStack(AEItems.SPEED_CARD), false);
                    }
                });

        var powerPos1 = new BlockPos(0, 1, 1);
        helper.setBlock(powerPos1, AEBlocks.CREATIVE_ENERGY_CELL.block());
        var powerPos2 = new BlockPos(4, 1, 0);
        helper.setBlock(powerPos2, AEBlocks.CREATIVE_ENERGY_CELL.block());

        var materialsPos = new BlockPos(6, 1, 0);
        var resultsPos = new BlockPos(5, 1, 0);
        helper.setBlock(materialsPos, SFMBlocks.TEST_BARREL.get());
        helper.setBlock(resultsPos, SFMBlocks.TEST_BARREL.get());
        //noinspection DataFlowIssue,OptionalGetWithoutIsPresent
        var materials = helper.getItemHandler(materialsPos);
        //noinspection DataFlowIssue,OptionalGetWithoutIsPresent
        var results = helper.getItemHandler(resultsPos);
        materials.insertItem(0, new ItemStack(Items.REDSTONE, 64), false);
        materials.insertItem(1, new ItemStack(Items.REDSTONE, 64), false);
        materials.insertItem(2, new ItemStack(Items.REDSTONE, 64), false);
        materials.insertItem(3, new ItemStack(Items.DIAMOND, 64), false);
        materials.insertItem(4, new ItemStack(Items.GOLD_INGOT, 64), false);
        materials.insertItem(5, new ItemStack(AEItems.CERTUS_QUARTZ_CRYSTAL, 64), false);
        materials.insertItem(6, new ItemStack(AEItems.SILICON, 64), false);
        materials.insertItem(7, new ItemStack(AEItems.SILICON, 64), false);
        materials.insertItem(8, new ItemStack(AEItems.SILICON, 64), false);

        // put signs on them lol
        BlockPos inputSignPos = materialsPos.offset(0, 0, 0);
        helper.setBlock(
                inputSignPos,
                Blocks.OAK_SIGN.defaultBlockState().setValue(StandingSignBlock.ROTATION, 8)
        );
        helper.setSignText(inputSignPos, Component.literal("input"));
        BlockPos outputSignPos = resultsPos.offset(0, 0, 0);
        helper.setBlock(
                outputSignPos,
                Blocks.OAK_SIGN.defaultBlockState().setValue(StandingSignBlock.ROTATION, 8)
        );
        helper.setSignText(outputSignPos, Component.literal("output"));

        var manager = helper.getBlockEntity(managerPos, ManagerBlockEntity.class);
        manager.setItem(0, new ItemStack(SFMItems.DISK.get()));
        LabelPositionHolder.empty()
                .add("silicon", helper.absolutePos(siliconPos1))
                .add("silicon", helper.absolutePos(siliconPos2))
                .add("silicon", helper.absolutePos(siliconPos3))
                .add("logic", helper.absolutePos(logicPos))
                .add("engineering", helper.absolutePos(engineeringPos))
                .add("calculation", helper.absolutePos(calculationPos))
                .add("last", helper.absolutePos(lastPos1))
                .add("last", helper.absolutePos(lastPos2))
                .add("last", helper.absolutePos(lastPos3))
                .add("materials", helper.absolutePos(materialsPos))
                .add("results", helper.absolutePos(resultsPos))
                .save(manager.getDisk());

        manager.setProgram("""
                                   NAME "AE2 Inscribers"
                                   
                                   -- labels:
                                   -- logic, engineering, calculation, silicon, last => inscribers
                                   -- materials, results => chests
                                   EVERY 20 TICKS DO
                                       INPUT FROM materials
                                   
                                       OUTPUT RETAIN 2 gold_ingot TO EACH logic SLOTS 2
                                       OUTPUT RETAIN 2 diamond TO EACH engineering SLOTS 2
                                       OUTPUT RETAIN 2 certus_quartz_crystal TO EACH calculation SLOTS 2
                                   
                                       OUTPUT RETAIN 2 silicon TO EACH silicon SLOTS 2
                                   
                                       OUTPUT RETAIN 2 redstone TO EACH last SLOTS 2
                                       OUTPUT RETAIN 2 printed_silicon TO EACH last SLOTS 1
                                   
                                       OUTPUT
                                           RETAIN 2 printed_calculation_processor,
                                           RETAIN 2 printed_engineering_processor,
                                           RETAIN 2 printed_logic_processor
                                       TO EACH last SLOTS 0
                                   FORGET
                                       INPUT FROM logic, engineering, calculation, silicon west side
                                       output to materials
                                   FORGET
                                       INPUT FROM last west SIDE
                                       OUTPUT TO results
                                   END
                                   """.stripTrailing().stripIndent());
        helper.succeedWhen(() -> {
            boolean hasCalculation = helper.count(results, AEItems.CALCULATION_PROCESSOR.asItem().asItem()) > 0;
            boolean hasEngineering = helper.count(results, AEItems.ENGINEERING_PROCESSOR.asItem().asItem()) > 0;
            boolean hasLogic = helper.count(results, AEItems.LOGIC_PROCESSOR.asItem().asItem()) > 0;
            if (hasCalculation && hasEngineering && hasLogic) {
                helper.succeed();
            } else {
                helper.fail("Missing processors: " + (hasCalculation ? "" : "calculation ") + (
                        hasEngineering
                        ? ""
                        : "engineering "
                ) + (hasLogic ? "" : "logic"));
            }
        });
    }
}
