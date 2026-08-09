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
 * Tests that a library with a macro using protocol constraints
 * can be imported and executed by a manager program at runtime.
 * <p>
 * The library defines:
 * - HasInput and HasOutput protocols
 * - Processor struct implementing both protocols
 * - transfer_through macro with protocol-constrained parameters
 * <p>
 * The manager imports the library and uses the macro to transfer items
 * through a processor device (input -> processor.input -> processor.output -> output).
 */
@SuppressWarnings({
        "RedundantSuppression",
        "DataFlowIssue",
        "OptionalGetWithoutIsPresent",
        "DuplicatedCode"
})
@SFMGameTest
public class LibraryWithTransferMacroGameTest extends SFMGameTestDefinition {

    @Override
    public String template() {
        return "5x3x3";
    }

    @Override
    public int maxTicks() {
        return 200;
    }

    @Override
    public void run(SFMGameTestHelper helper) {
        // Layout (y=2 front row): [input] - [Library] - [Manager] - [processor] - [output]
        // Layout (y=2 back row):  [cable] - [cable]   - [cable]   - [cable]     - [cable]
        BlockPos inputPos = new BlockPos(0, 2, 0);
        BlockPos libraryPos = new BlockPos(1, 2, 0);
        BlockPos managerPos = new BlockPos(2, 2, 0);
        BlockPos processorPos = new BlockPos(3, 2, 0);
        BlockPos outputPos = new BlockPos(4, 2, 0);

        // Cable row behind to connect everything
        BlockPos cable0Pos = new BlockPos(0, 2, 1);
        BlockPos cable1Pos = new BlockPos(1, 2, 1);
        BlockPos cable2Pos = new BlockPos(2, 2, 1);
        BlockPos cable3Pos = new BlockPos(3, 2, 1);
        BlockPos cable4Pos = new BlockPos(4, 2, 1);

        // Place main blocks
        helper.setBlock(inputPos, SFMBlocks.TEST_BARREL.get());
        helper.setBlock(libraryPos, SFMBlocks.LIBRARY_BLOCK.get());
        helper.setBlock(managerPos, SFMBlocks.MANAGER.get());
        helper.setBlock(processorPos, SFMBlocks.TEST_BARREL.get());
        helper.setBlock(outputPos, SFMBlocks.TEST_BARREL.get());

        // Place cable row behind
        helper.setBlock(cable0Pos, SFMBlocks.CABLE.get());
        helper.setBlock(cable1Pos, SFMBlocks.CABLE.get());
        helper.setBlock(cable2Pos, SFMBlocks.CABLE.get());
        helper.setBlock(cable3Pos, SFMBlocks.CABLE.get());
        helper.setBlock(cable4Pos, SFMBlocks.CABLE.get());

        // Get block entities
        LibraryBlockEntity library = (LibraryBlockEntity) helper.getBlockEntity(libraryPos);
        ManagerBlockEntity manager = (ManagerBlockEntity) helper.getBlockEntity(managerPos);

        // Create library disk with protocols, struct, and macro
        ItemStack libraryDisk = new ItemStack(SFMItems.DISK.get());
        DiskItem.setProgram(libraryDisk, """
                NAME "transfer_lib"

                protocol HasInput
                    input: sidequalifier slotqualifier
                end

                protocol HasOutput
                    output: sidequalifier slotqualifier
                end

                struct Processor : HasInput, HasOutput
                    input: EACH SIDE SLOTS 0-8
                    output: EACH SIDE SLOTS 9-17
                end

                macro transfer_through(machine: HasInput, machine2: HasOutput, src, dst)
                    input from src
                    output to machine using input
                    forget
                    input from machine2 using output
                    output to dst
                end
                """);
        library.setItem(0, libraryDisk);

        // Create manager disk that imports the library and uses the macro
        manager.setItem(0, new ItemStack(SFMItems.DISK.get()));
        manager.setProgram("""
                NAME "Transfer Manager"

                use library "transfer_lib"

                let processor = Processor

                every 20 ticks do
                    DO transfer_through(processor, processor, input_chest, output_chest)
                end
                """);

        // Setup labels
        LabelPositionHolder labelHolder = LabelPositionHolder.empty()
                .add("input_chest", helper.absolutePos(inputPos))
                .add("processor", helper.absolutePos(processorPos))
                .add("output_chest", helper.absolutePos(outputPos));
        labelHolder.save(manager.getDisk());

        // Put items in input chest
        var inputHandler = helper.getItemHandler(inputPos);
        inputHandler.insertItem(0, new ItemStack(Blocks.IRON_ORE, 64), false);

        // Simulate processed items in processor's output slots
        var processorHandler = helper.getItemHandler(processorPos);
        processorHandler.insertItem(9, new ItemStack(Blocks.IRON_BLOCK, 8), false);

        // Wait for the library to compile and manager to run
        helper.runAfterDelay(10, () -> {
            // Verify library disk has no errors
            ItemStack compiledLibraryDisk = library.getItem(0);
            helper.assertTrue(
                    DiskItem.getErrors(compiledLibraryDisk).isEmpty(),
                    "Library disk should have no errors but had: " + DiskItem.getErrors(compiledLibraryDisk)
            );

            // Verify manager disk has no errors
            helper.assertTrue(
                    DiskItem.getErrors(manager.getDisk()).isEmpty(),
                    "Manager disk should have no errors but had: " + DiskItem.getErrors(manager.getDisk())
            );

            helper.succeedIfManagerDidThingWithoutLagging(manager, () -> {
                // Verify items were moved from input to processor's input slots
                helper.assertTrue(
                        helper.count(inputHandler, Blocks.IRON_ORE) == 0,
                        "Input should be empty but has " + helper.count(inputHandler, Blocks.IRON_ORE) + " iron ore"
                );
                helper.assertTrue(
                        helper.count(processorHandler, Blocks.IRON_ORE) == 64,
                        "Processor should have 64 iron ore in input slots but has " + helper.count(processorHandler, Blocks.IRON_ORE)
                );

                // Verify items were moved from processor's output slots to output
                var outputHandler = helper.getItemHandler(outputPos);
                helper.assertTrue(
                        helper.count(outputHandler, Blocks.IRON_BLOCK) == 8,
                        "Output should have 8 iron blocks but has " + helper.count(outputHandler, Blocks.IRON_BLOCK)
                );
                helper.assertTrue(
                        helper.count(processorHandler, Blocks.IRON_BLOCK) == 0,
                        "Processor output slots should be empty but has " + helper.count(processorHandler, Blocks.IRON_BLOCK)
                );
            });
        });
    }
}
