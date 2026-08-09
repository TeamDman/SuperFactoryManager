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
 * Tests that a library with a struct implementing input/output protocols
 * can be imported and used by a manager program at runtime.
 * <p>
 * The library defines:
 * - HasInput protocol with input field
 * - HasOutput protocol with output field
 * - IODevice struct implementing both protocols
 * <p>
 * The manager imports the library, creates an IODevice instance,
 * and uses it for item transfer via the struct's input/output fields.
 */
@SuppressWarnings({
        "RedundantSuppression",
        "DataFlowIssue",
        "OptionalGetWithoutIsPresent",
        "DuplicatedCode"
})
@SFMGameTest
public class LibraryWithStructIOProtocolsGameTest extends SFMGameTestDefinition {

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
        // Layout (y=2 front row): [source] - [Library] - [Manager] - [device] - [dest]
        // Layout (y=2 back row):  [cable]  - [cable]   - [cable]   - [cable]  - [cable]
        BlockPos sourcePos = new BlockPos(0, 2, 0);
        BlockPos libraryPos = new BlockPos(1, 2, 0);
        BlockPos managerPos = new BlockPos(2, 2, 0);
        BlockPos devicePos = new BlockPos(3, 2, 0);
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
        helper.setBlock(devicePos, SFMBlocks.TEST_BARREL.get());
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

        // Create library disk with protocols and struct
        ItemStack libraryDisk = new ItemStack(SFMItems.DISK.get());
        DiskItem.setProgram(libraryDisk, """
                NAME "io_lib"

                protocol HasInput
                    input: sidequalifier slotqualifier
                end

                protocol HasOutput
                    output: sidequalifier slotqualifier
                end

                struct IODevice : HasInput, HasOutput
                    input: EACH SIDE SLOTS 0-8
                    output: EACH SIDE SLOTS 9-17
                end
                """);
        library.setItem(0, libraryDisk);

        // Create manager disk that imports the library
        manager.setItem(0, new ItemStack(SFMItems.DISK.get()));
        manager.setProgram("""
                NAME "IO Manager"

                use library "io_lib"

                let device = IODevice

                every 20 ticks do
                    -- Move items from source to device's input slots
                    input from source_chest
                    output to device using input
                    forget

                    -- Move items from device's output slots to dest
                    input from device using output
                    output to dest_chest
                end
                """);

        // Setup labels
        LabelPositionHolder labelHolder = LabelPositionHolder.empty()
                .add("source_chest", helper.absolutePos(sourcePos))
                .add("device", helper.absolutePos(devicePos))
                .add("dest_chest", helper.absolutePos(destPos));
        labelHolder.save(manager.getDisk());

        // Put items in source chest
        var sourceHandler = helper.getItemHandler(sourcePos);
        sourceHandler.insertItem(0, new ItemStack(Blocks.DIRT, 32), false);

        // Put items in device's output slots (slots 9-17) to be moved to dest
        var deviceHandler = helper.getItemHandler(devicePos);
        deviceHandler.insertItem(9, new ItemStack(Blocks.STONE, 16), false);

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
                // Verify items were moved from source to device's input slots
                helper.assertTrue(
                        helper.count(sourceHandler, Blocks.DIRT) == 0,
                        "Source should be empty but has " + helper.count(sourceHandler, Blocks.DIRT) + " dirt"
                );
                helper.assertTrue(
                        helper.count(deviceHandler, Blocks.DIRT) == 32,
                        "Device should have 32 dirt in input slots but has " + helper.count(deviceHandler, Blocks.DIRT)
                );

                // Verify items were moved from device's output slots to dest
                var destHandler = helper.getItemHandler(destPos);
                helper.assertTrue(
                        helper.count(destHandler, Blocks.STONE) == 16,
                        "Dest should have 16 stone but has " + helper.count(destHandler, Blocks.STONE)
                );
            });
        });
    }
}
