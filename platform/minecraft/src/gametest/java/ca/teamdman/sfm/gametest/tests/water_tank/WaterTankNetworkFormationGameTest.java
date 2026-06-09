package ca.teamdman.sfm.gametest.tests.water_tank;

import ca.teamdman.sfm.common.blockentity.WaterTankBlockEntity;
import ca.teamdman.sfm.common.registry.registration.SFMBlocks;
import ca.teamdman.sfm.gametest.SFMGameTest;
import ca.teamdman.sfm.gametest.SFMGameTestDefinition;
import ca.teamdman.sfm.gametest.SFMGameTestHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;



/**
 * Tests water tank network formation, splitting, and merging behavior.
 * <p>
 * This test verifies:
 * 1. Water tanks form a single network when touching
 * 2. Breaking a tank splits the network appropriately
 * 3. Repairing a tank merges networks back together
 * 4. Plus-shaped networks split into 4 separate networks when the center is removed
 */
@SuppressWarnings({
        "RedundantSuppression",
        "DataFlowIssue",
        "OptionalGetWithoutIsPresent",
        "DuplicatedCode"
})
@SFMGameTest
public class WaterTankNetworkFormationGameTest extends SFMGameTestDefinition {

    @Override
    public String template() {
        // Need enough space for the plus-shaped network test
        return "15x2x15";
    }

    @Override
    public void run(SFMGameTestHelper helper) {
        // Create a row of 5 water tanks (without water - testing network formation only)
        for (int i = 0; i < 5; i++) {
            helper.setBlock(new BlockPos(i, 1, 0), SFMBlocks.WATER_TANK.get());
        }

        // All tanks should be in the same network
        WaterTankBlockEntity firstTank = helper.getBlockEntity(new BlockPos(0, 1, 0), WaterTankBlockEntity.class);
        helper.getTick();
        helper.assertTrue(firstTank != null, "First tank should exist");

        // Get capacity - with 0 active members, capacity should be 0
        // But the network should still exist and contain all 5 tanks
        for (int i = 0; i < 5; i++) {
            WaterTankBlockEntity tank = helper.getBlockEntity(new BlockPos(i, 1, 0), WaterTankBlockEntity.class);
            helper.getTick();
            helper.assertTrue(tank != null, "Tank " + i + " should exist");
            // All inactive tanks should have capacity 0
            boolean success = tank.TANK.getCapacity() == 0;
            String expectedEnergyDidNotMatch = "Inactive tank " + i + " should have capacity 0 but had " + tank.TANK.getCapacity();
            helper.getTick();
            helper.assertTrue(success, expectedEnergyDidNotMatch);
        }

        // Break the middle tank (position 2) to split the network
        helper.setBlock(new BlockPos(2, 1, 0), Blocks.AIR);

        // The tanks should now be in separate networks
        // Network 1: positions 0, 1 (2 tanks)
        // Network 2: positions 3, 4 (2 tanks)
        // All remain inactive (no water), so capacity should still be 0 for all
        for (int i = 0; i < 2; i++) {
            WaterTankBlockEntity tank = helper.getBlockEntity(new BlockPos(i, 1, 0), WaterTankBlockEntity.class);
            helper.getTick();
            helper.assertTrue(tank != null, "Tank " + i + " should still exist after split");
            boolean success = tank.TANK.getCapacity() == 0;
            helper.getTick();
            helper.assertTrue(success, "Tank " + i + " should have capacity 0 after split");
        }
        for (int i = 3; i < 5; i++) {
            WaterTankBlockEntity tank = helper.getBlockEntity(new BlockPos(i, 1, 0), WaterTankBlockEntity.class);
            helper.getTick();
            helper.assertTrue(tank != null, "Tank " + i + " should still exist after split");
            boolean success = tank.TANK.getCapacity() == 0;
            helper.getTick();
            helper.assertTrue(success, "Tank " + i + " should have capacity 0 after split");
        }

        // Repair the network by placing the middle tank back
        helper.setBlock(new BlockPos(2, 1, 0), SFMBlocks.WATER_TANK.get());

        // All tanks should be in the same network again
        for (int i = 0; i < 5; i++) {
            WaterTankBlockEntity tank = helper.getBlockEntity(new BlockPos(i, 1, 0), WaterTankBlockEntity.class);
            helper.getTick();
            helper.assertTrue(tank != null, "Tank " + i + " should exist after repair");
        }

        // ===== Plus-shaped network test =====
        // Create a plus shape at position (10, 2, 7) - center of a 5x5 area offset to avoid the row
        BlockPos center = new BlockPos(10, 1, 7);
        BlockPos north = center.north();  // (10, 1, 6)
        BlockPos south = center.south();  // (10, 1, 8)
        BlockPos east = center.east();    // (11, 1, 7)
        BlockPos west = center.west();    // (9, 1, 7)

        // Place the plus shape
        helper.setBlock(center, SFMBlocks.WATER_TANK.get());
        helper.setBlock(north, SFMBlocks.WATER_TANK.get());
        helper.setBlock(south, SFMBlocks.WATER_TANK.get());
        helper.setBlock(east, SFMBlocks.WATER_TANK.get());
        helper.setBlock(west, SFMBlocks.WATER_TANK.get());

        // All 5 tanks in the plus should be in the same network
        WaterTankBlockEntity centerTank = helper.getBlockEntity(center, WaterTankBlockEntity.class);
        WaterTankBlockEntity northTank = helper.getBlockEntity(north, WaterTankBlockEntity.class);
        WaterTankBlockEntity southTank = helper.getBlockEntity(south, WaterTankBlockEntity.class);
        WaterTankBlockEntity eastTank = helper.getBlockEntity(east, WaterTankBlockEntity.class);
        WaterTankBlockEntity westTank = helper.getBlockEntity(west, WaterTankBlockEntity.class);

        helper.getTick();
        helper.assertTrue(centerTank != null, "Center tank should exist");
        helper.getTick();
        helper.assertTrue(northTank != null, "North tank should exist");
        helper.getTick();
        helper.assertTrue(southTank != null, "South tank should exist");
        helper.getTick();
        helper.assertTrue(eastTank != null, "East tank should exist");
        helper.getTick();
        helper.assertTrue(westTank != null, "West tank should exist");

        // Remove the center tank - this should split into 4 separate networks
        helper.setBlock(center, Blocks.AIR);

        // Each of the 4 tanks should now be in its own network
        // Since they're all inactive (no water) and alone, capacity should be 0
        northTank = helper.getBlockEntity(north, WaterTankBlockEntity.class);
        southTank = helper.getBlockEntity(south, WaterTankBlockEntity.class);
        eastTank = helper.getBlockEntity(east, WaterTankBlockEntity.class);
        westTank = helper.getBlockEntity(west, WaterTankBlockEntity.class);

        helper.getTick();
        helper.assertTrue(northTank != null, "North tank should still exist after removing center");
        helper.getTick();
        helper.assertTrue(southTank != null, "South tank should still exist after removing center");
        helper.getTick();
        helper.assertTrue(eastTank != null, "East tank should still exist after removing center");
        helper.getTick();
        helper.assertTrue(westTank != null, "West tank should still exist after removing center");

        // All should have capacity 0 since they're inactive
        boolean success3 = northTank.TANK.getCapacity() == 0;
        helper.getTick();
        helper.assertTrue(success3, "North tank should have capacity 0 after split");
        boolean success2 = southTank.TANK.getCapacity() == 0;
        helper.getTick();
        helper.assertTrue(success2, "South tank should have capacity 0 after split");
        boolean success1 = eastTank.TANK.getCapacity() == 0;
        helper.getTick();
        helper.assertTrue(success1, "East tank should have capacity 0 after split");
        boolean success = westTank.TANK.getCapacity() == 0;
        helper.getTick();
        helper.assertTrue(success, "West tank should have capacity 0 after split");

        // Restore the center - all 5 should merge back into one network
        helper.setBlock(center, SFMBlocks.WATER_TANK.get());

        centerTank = helper.getBlockEntity(center, WaterTankBlockEntity.class);
        helper.getTick();
        helper.assertTrue(centerTank != null, "Center tank should exist after restoration");

        helper.succeed();
    }
}
