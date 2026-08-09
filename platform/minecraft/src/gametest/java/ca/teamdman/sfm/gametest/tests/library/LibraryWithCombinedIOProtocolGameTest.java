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
 * Tests that a library with a combined I/O protocol (both input and output fields)
 * can be imported and used with a macro that processes items through a machine.
 * <p>
 * The library defines:
 * - Processable protocol with both input and output fields
 * - Machine struct implementing Processable
 * - process macro with Processable constraint
 * <p>
 * This tests the scenario where a single protocol contains both input and output
 * definitions, and a macro uses both fields from the same struct parameter.
 */
@SuppressWarnings({
        "RedundantSuppression",
        "DataFlowIssue",
        "OptionalGetWithoutIsPresent",
        "DuplicatedCode"
})
@SFMGameTest
public class LibraryWithCombinedIOProtocolGameTest extends SFMGameTestDefinition {

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
        // Layout (y=2 front row): [source] - [Library] - [Manager] - [machine] - [dest]
        // Layout (y=2 back row):  [cable]  - [cable]   - [cable]   - [cable]   - [cable]
        BlockPos sourcePos = new BlockPos(0, 2, 0);
        BlockPos libraryPos = new BlockPos(1, 2, 0);
        BlockPos managerPos = new BlockPos(2, 2, 0);
        BlockPos machinePos = new BlockPos(3, 2, 0);
        BlockPos destPos = new BlockPos(4, 2, 0);

        // Cable row behind to connect everything
        BlockPos cable0Pos = new BlockPos(0, 2, 1);
        BlockPos cable1Pos = new BlockPos(1, 2, 1);
        BlockPos cable2Pos = new BlockPos(2, 2, 1);
        BlockPos cable3Pos = new BlockPos(3, 2, 1);
        BlockPos cable4Pos = new BlockPos(4, 2, 1);

        // Place main blocks
        helper.setBlock(sourcePos, SFMBlocks.TEST_BARREL.get());
        helper.setBlock(libraryPos, SFMBlocks.LIBRARY_BLOCK.get());
        helper.setBlock(managerPos, SFMBlocks.MANAGER.get());
        helper.setBlock(machinePos, SFMBlocks.TEST_BARREL.get());
        helper.setBlock(destPos, SFMBlocks.TEST_BARREL.get());

        // Place cable row behind
        helper.setBlock(cable0Pos, SFMBlocks.CABLE.get());
        helper.setBlock(cable1Pos, SFMBlocks.CABLE.get());
        helper.setBlock(cable2Pos, SFMBlocks.CABLE.get());
        helper.setBlock(cable3Pos, SFMBlocks.CABLE.get());
        helper.setBlock(cable4Pos, SFMBlocks.CABLE.get());

        // Get block entities
        LibraryBlockEntity library = (LibraryBlockEntity) helper.getBlockEntity(libraryPos);
        ManagerBlockEntity manager = (ManagerBlockEntity) helper.getBlockEntity(managerPos);

        // Create library disk with combined protocol, struct, and macro
        ItemStack libraryDisk = new ItemStack(SFMItems.DISK.get());
        DiskItem.setProgram(libraryDisk, """
                NAME "combined_lib"

                protocol Processable
                    input: sidequalifier slotqualifier
                    output: sidequalifier slotqualifier
                end

                struct Machine : Processable
                    input: EACH SIDE SLOTS 0-8
                    output: EACH SIDE SLOTS 9-17
                end

                macro process(device: Processable, src, dst)
                    input from src
                    output to device using input
                    forget
                    input from device using output
                    output to dst
                end
                """);
        library.setItem(0, libraryDisk);

        // Create manager disk that imports the library
        manager.setItem(0, new ItemStack(SFMItems.DISK.get()));
        manager.setProgram("""
                NAME "Combined Manager"

                use library "combined_lib"

                let machine = Machine

                every 20 ticks do
                    DO process(machine, source, dest)
                end
                """);

        // Setup labels
        LabelPositionHolder labelHolder = LabelPositionHolder.empty()
                .add("source", helper.absolutePos(sourcePos))
                .add("machine", helper.absolutePos(machinePos))
                .add("dest", helper.absolutePos(destPos));
        labelHolder.save(manager.getDisk());

        // Put items in source
        var sourceHandler = helper.getItemHandler(sourcePos);
        sourceHandler.insertItem(0, new ItemStack(Blocks.COAL_ORE, 32), false);

        // Put processed items in machine's output slots
        var machineHandler = helper.getItemHandler(machinePos);
        machineHandler.insertItem(9, new ItemStack(Blocks.COAL_BLOCK, 4), false);

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
                // Verify items were moved from source to machine's input slots
                helper.assertTrue(
                        helper.count(sourceHandler, Blocks.COAL_ORE) == 0,
                        "Source should be empty but has " + helper.count(sourceHandler, Blocks.COAL_ORE) + " coal ore"
                );
                helper.assertTrue(
                        helper.count(machineHandler, Blocks.COAL_ORE) == 32,
                        "Machine should have 32 coal ore in input slots but has " + helper.count(machineHandler, Blocks.COAL_ORE)
                );

                // Verify items were moved from machine's output slots to dest
                var destHandler = helper.getItemHandler(destPos);
                helper.assertTrue(
                        helper.count(destHandler, Blocks.COAL_BLOCK) == 4,
                        "Dest should have 4 coal blocks but has " + helper.count(destHandler, Blocks.COAL_BLOCK)
                );
                helper.assertTrue(
                        helper.count(machineHandler, Blocks.COAL_BLOCK) == 0,
                        "Machine output slots should be empty but has " + helper.count(machineHandler, Blocks.COAL_BLOCK)
                );
            });
        });
    }
}
