package ca.teamdman.sfm.gametest.tests.water_tanks;

import ca.teamdman.sfm.common.blockentity.WaterTankBlockEntity;
import ca.teamdman.sfm.common.registry.SFMBlocks;
import ca.teamdman.sfm.gametest.SFMGameTest;
import ca.teamdman.sfm.gametest.SFMGameTestDefinition;
import ca.teamdman.sfm.gametest.SFMGameTestHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;

import static ca.teamdman.sfm.gametest.SFMGameTestMethodHelpers.assertTrue;

/**
 * Tests that water tanks correctly detect active state when surrounded by water sources.
 * <p>
 * Structure (7x2x5):
 * <pre>
 * xxxxxxx
 * xaaaaax
 * xbbbbbx
 * xaaaaax
 * xxxxxxx
 * </pre>
 * Where:
 * - x = stone (containment)
 * - a = water source
 * - b = water tank
 * <p>
 * Tests:
 * 1. All 5 tanks should be active when surrounded by water
 * 2. Removing water sources should deactivate tanks
 * 3. Restoring water sources should reactivate tanks
 */
@SuppressWarnings({
        "RedundantSuppression",
        "DataFlowIssue",
        "OptionalGetWithoutIsPresent",
        "DuplicatedCode"
})
@SFMGameTest
public class WaterTankRowWithWaterGameTest extends SFMGameTestDefinition {

    @Override
    public String template() {
        // 7 wide (x direction) x 2 tall (y direction) x 5 deep (z direction)
        return "7x2x5";
    }

    @Override
    public void run(SFMGameTestHelper helper) {
        // Build the containment structure with stone
        // Bottom layer (z=0): all stone
        for (int x = 0; x < 7; x++) {
            helper.setBlock(new BlockPos(x, 2, 0), Blocks.STONE);
        }
        // Top layer (z=4): all stone
        for (int x = 0; x < 7; x++) {
            helper.setBlock(new BlockPos(x, 2, 4), Blocks.STONE);
        }
        // Left wall (x=0): all stone
        for (int z = 0; z < 5; z++) {
            helper.setBlock(new BlockPos(0, 2, z), Blocks.STONE);
        }
        // Right wall (x=6): all stone
        for (int z = 0; z < 5; z++) {
            helper.setBlock(new BlockPos(6, 2, z), Blocks.STONE);
        }

        // Place water tanks in the middle row (z=2)
        BlockPos[] tankPositions = new BlockPos[5];
        for (int x = 1; x <= 5; x++) {
            BlockPos pos = new BlockPos(x, 2, 2);
            tankPositions[x - 1] = pos;
            helper.setBlock(pos, SFMBlocks.WATER_TANK_BLOCK.get());
        }

        // Place water sources in the rows above and below the tanks (z=1 and z=3)
        for (int x = 1; x <= 5; x++) {
            BlockPos above = new BlockPos(x, 2, 1);
            BlockPos below = new BlockPos(x, 2, 3);
            helper.setBlock(above, Blocks.WATER);
            helper.setBlock(below, Blocks.WATER);
        }

        // Verify all tanks are active (have 2 water sources touching them)
        for (int i = 0; i < 5; i++) {
            WaterTankBlockEntity tank = (WaterTankBlockEntity) helper.getBlockEntity(tankPositions[i]);
            assertTrue(tank != null, "Water tank block entity should exist at position " + i);
            assertTrue(tank.isActive(), "Water tank " + i + " should be active when surrounded by water");
        }

        // Verify tank capacity is correct (5 active members = 2^(5-1) * 1000 = 16000)
        int expectedCapacity = (int) Math.pow(2, 5 - 1) * 1000; // 16000
        for (int i = 0; i < 5; i++) {
            WaterTankBlockEntity tank = (WaterTankBlockEntity) helper.getBlockEntity(tankPositions[i]);
            assertTrue(
                    tank.TANK.getCapacity() == expectedCapacity,
                    "Water tank " + i + " should have capacity " + expectedCapacity + " but had " + tank.TANK.getCapacity()
            );
        }

        // Remove water sources from the top row (z=1)
        for (int x = 1; x <= 5; x++) {
            helper.setBlock(new BlockPos(x, 2, 1), Blocks.AIR);
        }

        // Tanks should now be inactive (only 1 water source touching each)
        for (int i = 0; i < 5; i++) {
            WaterTankBlockEntity tank = (WaterTankBlockEntity) helper.getBlockEntity(tankPositions[i]);
            assertTrue(!tank.isActive(), "Water tank " + i + " should be inactive with only 1 water source");
        }

        // Verify tank capacity is now 0 (0 active members)
        for (int i = 0; i < 5; i++) {
            WaterTankBlockEntity tank = (WaterTankBlockEntity) helper.getBlockEntity(tankPositions[i]);
            assertTrue(
                    tank.TANK.getCapacity() == 0,
                    "Water tank " + i + " should have capacity 0 when inactive but had " + tank.TANK.getCapacity()
            );
        }

        // Restore water sources to the top row
        for (int x = 1; x <= 5; x++) {
            helper.setBlock(new BlockPos(x, 2, 1), Blocks.WATER);
        }

        // Tanks should be active again
        for (int i = 0; i < 5; i++) {
            WaterTankBlockEntity tank = (WaterTankBlockEntity) helper.getBlockEntity(tankPositions[i]);
            assertTrue(tank.isActive(), "Water tank " + i + " should be active again after restoring water");
        }

        // Verify tank capacity is restored
        for (int i = 0; i < 5; i++) {
            WaterTankBlockEntity tank = (WaterTankBlockEntity) helper.getBlockEntity(tankPositions[i]);
            assertTrue(
                    tank.TANK.getCapacity() == expectedCapacity,
                    "Water tank " + i + " should have capacity " + expectedCapacity + " after restoring water but had " + tank.TANK.getCapacity()
            );
        }

        helper.succeed();
    }
}
