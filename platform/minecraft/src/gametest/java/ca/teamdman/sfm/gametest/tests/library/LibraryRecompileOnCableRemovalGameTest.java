package ca.teamdman.sfm.gametest.tests.library;

import ca.teamdman.sfm.common.blockentity.LibraryBlockEntity;
import ca.teamdman.sfm.common.item.DiskItem;
import ca.teamdman.sfm.common.registry.registration.SFMBlocks;
import ca.teamdman.sfm.common.registry.registration.SFMItems;
import ca.teamdman.sfm.gametest.SFMGameTest;
import ca.teamdman.sfm.gametest.SFMGameTestDefinition;
import ca.teamdman.sfm.gametest.SFMGameTestHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;


/**
 * Tests that library disks are recompiled when the only network connection
 * (cable) next to the library is removed.
 * <p>
 * Setup:
 * - Library A has a disk that uses library "provider"
 * - Library B has a disk with NAME "provider" containing definitions
 * - A cable connects them
 * <p>
 * Test:
 * 1. Initially, Library A's disk should have no errors (can resolve "provider")
 * 2. Remove the cable connecting them
 * 3. After the 5-tick notification delay, Library A's disk should have errors
 *    (cannot resolve "provider" anymore, proving recompilation occurred)
 */
@SuppressWarnings({
        "RedundantSuppression",
        "DataFlowIssue",
        "OptionalGetWithoutIsPresent",
        "DuplicatedCode",
        "ArraysAsListWithZeroOrOneArgument"
})
@SFMGameTest
public class LibraryRecompileOnCableRemovalGameTest extends SFMGameTestDefinition {

    @Override
    public String template() {
        return "3x2x1";
    }

    @Override
    public void run(SFMGameTestHelper helper) {
        // Place two library blocks with a cable between them
        // Layout: [LibraryA] - [Cable] - [LibraryB]
        BlockPos libraryAPos = new BlockPos(0, 2, 0);
        BlockPos cablePos = new BlockPos(1, 2, 0);
        BlockPos libraryBPos = new BlockPos(2, 2, 0);

        helper.setBlock(libraryAPos, SFMBlocks.LIBRARY_BLOCK.get());
        helper.setBlock(cablePos, SFMBlocks.CABLE.get());
        helper.setBlock(libraryBPos, SFMBlocks.LIBRARY_BLOCK.get());

        // Get library block entities
        LibraryBlockEntity libraryA = (LibraryBlockEntity) helper.getBlockEntity(libraryAPos);
        LibraryBlockEntity libraryB = (LibraryBlockEntity) helper.getBlockEntity(libraryBPos);

        // Create disk for Library B with a named library definition
        ItemStack providerDisk = new ItemStack(SFMItems.DISK.get());
        DiskItem.setProgram(providerDisk, """
                NAME "provider"

                -- This library provides a simple macro
                macro greet()
                    -- just a placeholder
                end
                """);

        // Create disk for Library A that uses the provider library
        ItemStack consumerDisk = new ItemStack(SFMItems.DISK.get());
        DiskItem.setProgram(consumerDisk, """
                NAME "consumer"

                USE LIBRARY "provider"
                """);

        // Insert disks into libraries
        libraryB.setItem(0, providerDisk);
        libraryA.setItem(0, consumerDisk);

        // Wait a moment for initial compilation via the batched notification system
        helper.runAfterDelay(10, () -> {
            // Get the consumer disk and check it has no errors (library was found)
            ItemStack diskAfterConnect = libraryA.getItem(0);
            helper.assertTrue(
                    DiskItem.getErrors(diskAfterConnect).isEmpty(),
                    "Consumer disk should have no errors when connected to provider library"
            );

            // Now remove the cable, disconnecting Library A from Library B
            helper.setBlock(cablePos, Blocks.AIR);

            // Wait for the 5-tick notification delay plus a small buffer
            helper.runAfterDelay(10, () -> {
                // Get the consumer disk and verify it now has errors
                // (library "provider" cannot be resolved anymore)
                ItemStack diskAfterDisconnect = libraryA.getItem(0);
                helper.assertTrue(
                        !DiskItem.getErrors(diskAfterDisconnect).isEmpty(),
                        "Consumer disk should have errors after disconnection (library not found)"
                );

                helper.succeed();
            });
        });
    }
}
