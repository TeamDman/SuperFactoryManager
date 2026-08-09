package ca.teamdman.sfm.gametest.tests.library;

import ca.teamdman.sfm.common.blockentity.LibraryBlockEntity;
import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import ca.teamdman.sfm.common.item.DiskItem;
import ca.teamdman.sfm.common.registry.registration.SFMBlocks;
import ca.teamdman.sfm.common.registry.registration.SFMItems;
import ca.teamdman.sfm.gametest.SFMGameTest;
import ca.teamdman.sfm.gametest.SFMGameTestDefinition;
import ca.teamdman.sfm.gametest.SFMGameTestHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;


/**
 * Tests that protocol constraints in macros are validated at compile time.
 * <p>
 * The library defines:
 * - IOCapable protocol with input and output fields
 * - ValidDevice struct that implements IOCapable
 * - InvalidDevice struct that does NOT implement IOCapable
 * - transfer macro with IOCapable constraint
 * <p>
 * Test verifies:
 * 1. Using ValidDevice with the macro compiles successfully
 * 2. Using InvalidDevice with the macro produces compile errors
 */
@SuppressWarnings({
        "RedundantSuppression",
        "DataFlowIssue",
        "OptionalGetWithoutIsPresent",
        "DuplicatedCode"
})
@SFMGameTest
public class LibraryProtocolConstraintValidationGameTest extends SFMGameTestDefinition {

    @Override
    public String template() {
        return "3x3x3";
    }

    @Override
    public int maxTicks() {
        return 100;
    }

    @Override
    public void run(SFMGameTestHelper helper) {
        // Layout (y=2 front row): [Library] - [Manager] - [empty]
        // Layout (y=2 back row):  [cable]   - [cable]   - [empty]
        BlockPos libraryPos = new BlockPos(0, 2, 0);
        BlockPos managerPos = new BlockPos(1, 2, 0);

        // Cable row behind to connect
        BlockPos cable0Pos = new BlockPos(0, 2, 1);
        BlockPos cable1Pos = new BlockPos(1, 2, 1);

        // Place main blocks
        helper.setBlock(libraryPos, SFMBlocks.LIBRARY_BLOCK.get());
        helper.setBlock(managerPos, SFMBlocks.MANAGER.get());

        // Place cable row behind
        helper.setBlock(cable0Pos, SFMBlocks.CABLE.get());
        helper.setBlock(cable1Pos, SFMBlocks.CABLE.get());

        // Get block entities
        LibraryBlockEntity library = (LibraryBlockEntity) helper.getBlockEntity(libraryPos);
        ManagerBlockEntity manager = (ManagerBlockEntity) helper.getBlockEntity(managerPos);

        // Create library disk with protocol, valid struct, invalid struct, and macro
        ItemStack libraryDisk = new ItemStack(SFMItems.DISK.get());
        DiskItem.setProgram(libraryDisk, """
                NAME "constrained_lib"

                protocol IOCapable
                    input: sidequalifier slotqualifier
                    output: sidequalifier slotqualifier
                end

                struct ValidDevice : IOCapable
                    input: EACH SIDE SLOTS 0-8
                    output: EACH SIDE SLOTS 9-17
                end

                struct InvalidDevice
                    storage: SLOTS 0-26
                end

                macro transfer(device: IOCapable, src, dst)
                    input from src
                    output to device using input
                end
                """);
        library.setItem(0, libraryDisk);

        // Wait for library to compile
        helper.runAfterDelay(10, () -> {
            // Verify library disk has no errors
            ItemStack compiledLibraryDisk = library.getItem(0);
            helper.assertTrue(
                    DiskItem.getErrors(compiledLibraryDisk).isEmpty(),
                    "Library disk should have no errors but had: " + DiskItem.getErrors(compiledLibraryDisk)
            );

            // Test 1: Valid usage - struct implements required protocol
            manager.setItem(0, new ItemStack(SFMItems.DISK.get()));
            manager.setProgram("""
                    NAME "Valid Usage"
                    use library "constrained_lib"

                    let device = ValidDevice

                    every 20 ticks do
                        DO transfer(device, a, b)
                    end
                    """);

            helper.runAfterDelay(5, () -> {
                helper.assertTrue(
                        DiskItem.getErrors(manager.getDisk()).isEmpty(),
                        "Valid usage should have no errors but had: " + DiskItem.getErrors(manager.getDisk())
                );

                // Test 2: Invalid usage - struct doesn't implement required protocol
                manager.setProgram("""
                        NAME "Invalid Usage"
                        use library "constrained_lib"

                        let device = InvalidDevice

                        every 20 ticks do
                            DO transfer(device, a, b)
                        end
                        """);

                helper.runAfterDelay(5, () -> {
                    helper.assertTrue(
                            !DiskItem.getErrors(manager.getDisk()).isEmpty(),
                            "Invalid usage should have compile errors for using struct that doesn't implement protocol"
                    );

                    helper.succeed();
                });
            });
        });
    }
}
