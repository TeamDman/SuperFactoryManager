package ca.teamdman.sfm.gametest.tests.library;

import ca.teamdman.sfm.common.blockentity.LibraryBlockEntity;
import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import ca.teamdman.sfm.common.item.DiskItem;
import ca.teamdman.sfm.common.label.LabelPositionHolder;
import ca.teamdman.sfm.common.registry.registration.SFMBlocks;
import ca.teamdman.sfm.common.registry.registration.SFMItems;
import ca.teamdman.sfm.gametest.SFMGameTest;
import ca.teamdman.sfm.gametest.SFMGameTestDefinition;
import ca.teamdman.sfm.gametest.SFMGameTestHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;


/**
 * Tests chained library imports where multiple libraries depend on each other.
 * <p>
 * Setup:
 * - base_protocols library: defines HasInput and HasOutput protocols
 * - struct_lib library: imports base_protocols, defines Furnace struct
 * - macro_lib library: imports base_protocols and struct_lib, defines smelt macro
 * - Manager: imports all three libraries and uses the smelt macro
 * <p>
 * This tests that:
 * 1. Libraries can import other libraries
 * 2. Definitions are properly resolved across the chain
 * 3. Protocol constraints work with chained imports
 */
@SuppressWarnings({
        "RedundantSuppression",
        "DataFlowIssue",
        "OptionalGetWithoutIsPresent",
        "DuplicatedCode"
})
@SFMGameTest
public class LibraryChainedImportsGameTest extends SFMGameTestDefinition {

    @Override
    public String template() {
        return "7x3x3";
    }

    @Override
    public int maxTicks() {
        return 200;
    }

    @Override
    public void run(SFMGameTestHelper helper) {
        // Layout (y=2 front row): [ore] - [baseLib] - [structLib] - [Manager] - [macroLib] - [furnace] - [ingots]
        // Layout (y=2 back row):  [cable] - [cable] - [cable]     - [cable]   - [cable]    - [cable]   - [cable]
        BlockPos orePos = new BlockPos(0, 2, 0);
        BlockPos baseLibPos = new BlockPos(1, 2, 0);
        BlockPos structLibPos = new BlockPos(2, 2, 0);
        BlockPos managerPos = new BlockPos(3, 2, 0);
        BlockPos macroLibPos = new BlockPos(4, 2, 0);
        BlockPos furnacePos = new BlockPos(5, 2, 0);
        BlockPos ingotsPos = new BlockPos(6, 2, 0);

        // Cable row behind to connect everything
        BlockPos cable0Pos = new BlockPos(0, 2, 1);
        BlockPos cable1Pos = new BlockPos(1, 2, 1);
        BlockPos cable2Pos = new BlockPos(2, 2, 1);
        BlockPos cable3Pos = new BlockPos(3, 2, 1);
        BlockPos cable4Pos = new BlockPos(4, 2, 1);
        BlockPos cable5Pos = new BlockPos(5, 2, 1);
        BlockPos cable6Pos = new BlockPos(6, 2, 1);

        // Place main blocks
        helper.setBlock(orePos, SFMBlocks.TEST_BARREL.get());
        helper.setBlock(baseLibPos, SFMBlocks.LIBRARY_BLOCK.get());
        helper.setBlock(structLibPos, SFMBlocks.LIBRARY_BLOCK.get());
        helper.setBlock(managerPos, SFMBlocks.MANAGER.get());
        helper.setBlock(macroLibPos, SFMBlocks.LIBRARY_BLOCK.get());
        helper.setBlock(furnacePos, SFMBlocks.TEST_BARREL.get());
        helper.setBlock(ingotsPos, SFMBlocks.TEST_BARREL.get());

        // Place cable row behind
        helper.setBlock(cable0Pos, SFMBlocks.CABLE.get());
        helper.setBlock(cable1Pos, SFMBlocks.CABLE.get());
        helper.setBlock(cable2Pos, SFMBlocks.CABLE.get());
        helper.setBlock(cable3Pos, SFMBlocks.CABLE.get());
        helper.setBlock(cable4Pos, SFMBlocks.CABLE.get());
        helper.setBlock(cable5Pos, SFMBlocks.CABLE.get());
        helper.setBlock(cable6Pos, SFMBlocks.CABLE.get());

        // Get block entities
        LibraryBlockEntity baseLib = (LibraryBlockEntity) helper.getBlockEntity(baseLibPos);
        LibraryBlockEntity structLib = (LibraryBlockEntity) helper.getBlockEntity(structLibPos);
        LibraryBlockEntity macroLib = (LibraryBlockEntity) helper.getBlockEntity(macroLibPos);
        ManagerBlockEntity manager = (ManagerBlockEntity) helper.getBlockEntity(managerPos);

        // Create base_protocols library disk
        ItemStack baseLibDisk = new ItemStack(SFMItems.DISK.get());
        DiskItem.setProgram(baseLibDisk, """
                NAME "base_protocols"

                protocol HasInput
                    input: sidequalifier slotqualifier
                end

                protocol HasOutput
                    output: sidequalifier slotqualifier
                end
                """);
        baseLib.setItem(0, baseLibDisk);

        // Create struct_lib library disk
        ItemStack structLibDisk = new ItemStack(SFMItems.DISK.get());
        DiskItem.setProgram(structLibDisk, """
                NAME "struct_lib"
                use library "base_protocols"

                struct Furnace : HasInput, HasOutput
                    input: EACH SIDE SLOTS 0-8
                    output: EACH SIDE SLOTS 9-17
                end
                """);
        structLib.setItem(0, structLibDisk);

        // Create macro_lib library disk
        ItemStack macroLibDisk = new ItemStack(SFMItems.DISK.get());
        DiskItem.setProgram(macroLibDisk, """
                NAME "macro_lib"
                use library "base_protocols"
                use library "struct_lib"

                macro smelt(machine: HasInput, machine2: HasOutput, src, dst)
                    input from src
                    output to machine using input
                    forget
                    input from machine2 using output
                    output to dst
                end
                """);
        macroLib.setItem(0, macroLibDisk);

        // Create manager disk that imports all libraries
        manager.setItem(0, new ItemStack(SFMItems.DISK.get()));
        manager.setProgram("""
                NAME "Chained Manager"
                use library "base_protocols"
                use library "struct_lib"
                use library "macro_lib"

                let furnace = Furnace

                every 20 ticks do
                    DO smelt(furnace, furnace, ore_chest, ingot_chest)
                end
                """);

        // Setup labels
        LabelPositionHolder labelHolder = LabelPositionHolder.empty()
                .add("ore_chest", helper.absolutePos(orePos))
                .add("furnace", helper.absolutePos(furnacePos))
                .add("ingot_chest", helper.absolutePos(ingotsPos));
        labelHolder.save(manager.getDisk());

        // Put ore in source
        var oreHandler = helper.getItemHandler(orePos);
        oreHandler.insertItem(0, new ItemStack(Blocks.GOLD_ORE, 32), false);

        // Put ingots in furnace output (simulating smelting)
        var furnaceHandler = helper.getItemHandler(furnacePos);
        furnaceHandler.insertItem(9, new ItemStack(Blocks.GOLD_BLOCK, 4), false);

        // Wait for libraries to compile and manager to run
        helper.runAfterDelay(20, () -> {
            // Verify all library disks have no errors
            helper.assertTrue(
                    DiskItem.getErrors(baseLib.getItem(0)).isEmpty(),
                    "base_protocols library should have no errors but had: " + DiskItem.getErrors(baseLib.getItem(0))
            );
            helper.assertTrue(
                    DiskItem.getErrors(structLib.getItem(0)).isEmpty(),
                    "struct_lib library should have no errors but had: " + DiskItem.getErrors(structLib.getItem(0))
            );
            helper.assertTrue(
                    DiskItem.getErrors(macroLib.getItem(0)).isEmpty(),
                    "macro_lib library should have no errors but had: " + DiskItem.getErrors(macroLib.getItem(0))
            );

            // Verify manager disk has no errors
            helper.assertTrue(
                    DiskItem.getErrors(manager.getDisk()).isEmpty(),
                    "Manager disk should have no errors but had: " + DiskItem.getErrors(manager.getDisk())
            );

            helper.succeedIfManagerDidThingWithoutLagging(manager, () -> {
                // Verify ore was moved from source to furnace input
                helper.assertTrue(
                        helper.count(oreHandler, Blocks.GOLD_ORE) == 0,
                        "Ore chest should be empty but has " + helper.count(oreHandler, Blocks.GOLD_ORE) + " gold ore"
                );
                helper.assertTrue(
                        helper.count(furnaceHandler, Blocks.GOLD_ORE) == 32,
                        "Furnace should have 32 gold ore in input slots but has " + helper.count(furnaceHandler, Blocks.GOLD_ORE)
                );

                // Verify ingots were moved from furnace output to ingot chest
                var ingotsHandler = helper.getItemHandler(ingotsPos);
                helper.assertTrue(
                        helper.count(ingotsHandler, Blocks.GOLD_BLOCK) == 4,
                        "Ingot chest should have 4 gold blocks but has " + helper.count(ingotsHandler, Blocks.GOLD_BLOCK)
                );
                helper.assertTrue(
                        helper.count(furnaceHandler, Blocks.GOLD_BLOCK) == 0,
                        "Furnace output should be empty but has " + helper.count(furnaceHandler, Blocks.GOLD_BLOCK)
                );
            });
        });
    }
}
