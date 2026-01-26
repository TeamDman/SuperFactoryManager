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
 * Tests that water tank capacity scales correctly with active member count.
 * <p>
 * Capacity formula: 2^(activeMemberCount - 1) * 1000
 * - 0 active: 0 capacity
 * - 1 active: 1000 capacity
 * - 2 active: 2000 capacity
 * - 3 active: 4000 capacity
 * - 4 active: 8000 capacity
 * - 5 active: 16000 capacity
 * <p>
 * This test places 5 water tanks in a row and progressively activates them
 * by adding water sources, verifying capacity at each step.
 */
@SuppressWarnings({
        "RedundantSuppression",
        "DataFlowIssue",
        "OptionalGetWithoutIsPresent",
        "DuplicatedCode"
})
@SFMGameTest
public class WaterTankCapacityScalingGameTest extends SFMGameTestDefinition {

    @Override
    public String template() {
        // 9 wide x 2 tall x 5 deep to contain water properly
        return "9x2x5";
    }

    @Override
    public void run(SFMGameTestHelper helper) {
        // Build containment walls
        // Stone walls on all sides to prevent water flow
        for (int x = 0; x < 9; x++) {
            helper.setBlock(new BlockPos(x, 2, 0), Blocks.STONE);
            helper.setBlock(new BlockPos(x, 2, 4), Blocks.STONE);
        }
        for (int z = 0; z < 5; z++) {
            helper.setBlock(new BlockPos(0, 2, z), Blocks.STONE);
            helper.setBlock(new BlockPos(8, 2, z), Blocks.STONE);
        }

        // Place 5 water tanks in a row (x=2 to x=6, z=2)
        BlockPos[] tankPositions = new BlockPos[5];
        for (int i = 0; i < 5; i++) {
            BlockPos pos = new BlockPos(i + 2, 2, 2);
            tankPositions[i] = pos;
            helper.setBlock(pos, SFMBlocks.WATER_TANK_BLOCK.get());
        }

        // Initially all tanks are inactive, capacity should be 0
        assertAllTanksHaveCapacity(helper, tankPositions, 0, "initial state (0 active)");

        // Activate first tank by adding 2 water sources around it
        helper.setBlock(new BlockPos(2, 2, 1), Blocks.WATER);  // above tank 0
        helper.setBlock(new BlockPos(2, 2, 3), Blocks.WATER);  // below tank 0

        // 1 active member: capacity = 2^0 * 1000 = 1000
        assertAllTanksHaveCapacity(helper, tankPositions, 1000, "1 active member");
        assertTankActive(helper, tankPositions[0], true, "tank 0 should be active");
        for (int i = 1; i < 5; i++) {
            assertTankActive(helper, tankPositions[i], false, "tank " + i + " should be inactive");
        }

        // Activate second tank
        helper.setBlock(new BlockPos(3, 2, 1), Blocks.WATER);  // above tank 1
        helper.setBlock(new BlockPos(3, 2, 3), Blocks.WATER);  // below tank 1

        // 2 active members: capacity = 2^1 * 1000 = 2000
        assertAllTanksHaveCapacity(helper, tankPositions, 2000, "2 active members");

        // Activate third tank
        helper.setBlock(new BlockPos(4, 2, 1), Blocks.WATER);  // above tank 2
        helper.setBlock(new BlockPos(4, 2, 3), Blocks.WATER);  // below tank 2

        // 3 active members: capacity = 2^2 * 1000 = 4000
        assertAllTanksHaveCapacity(helper, tankPositions, 4000, "3 active members");

        // Activate fourth tank
        helper.setBlock(new BlockPos(5, 2, 1), Blocks.WATER);  // above tank 3
        helper.setBlock(new BlockPos(5, 2, 3), Blocks.WATER);  // below tank 3

        // 4 active members: capacity = 2^3 * 1000 = 8000
        assertAllTanksHaveCapacity(helper, tankPositions, 8000, "4 active members");

        // Activate fifth tank
        helper.setBlock(new BlockPos(6, 2, 1), Blocks.WATER);  // above tank 4
        helper.setBlock(new BlockPos(6, 2, 3), Blocks.WATER);  // below tank 4

        // 5 active members: capacity = 2^4 * 1000 = 16000
        assertAllTanksHaveCapacity(helper, tankPositions, 16000, "5 active members");

        // Now deactivate tanks one by one by removing water
        // Remove water from tank 4 (last one)
        helper.setBlock(new BlockPos(6, 2, 1), Blocks.AIR);
        helper.setBlock(new BlockPos(6, 2, 3), Blocks.AIR);

        // Back to 4 active members: capacity = 8000
        assertAllTanksHaveCapacity(helper, tankPositions, 8000, "back to 4 active members");

        // Remove water from tank 0 (first one)
        helper.setBlock(new BlockPos(2, 2, 1), Blocks.AIR);
        helper.setBlock(new BlockPos(2, 2, 3), Blocks.AIR);

        // 3 active members: capacity = 4000
        assertAllTanksHaveCapacity(helper, tankPositions, 4000, "3 active members after removing first");

        helper.succeed();
    }

    private void assertAllTanksHaveCapacity(
            SFMGameTestHelper helper,
            BlockPos[] tankPositions,
            int expectedCapacity,
            String context
    ) {
        for (int i = 0; i < tankPositions.length; i++) {
            WaterTankBlockEntity tank = (WaterTankBlockEntity) helper.getBlockEntity(tankPositions[i]);
            assertTrue(
                    tank != null,
                    "Tank " + i + " should exist (" + context + ")"
            );
            assertTrue(
                    tank.TANK.getCapacity() == expectedCapacity,
                    "Tank " + i + " should have capacity " + expectedCapacity + " but had " + tank.TANK.getCapacity() + " (" + context + ")"
            );
        }
    }

    private void assertTankActive(
            SFMGameTestHelper helper,
            BlockPos pos,
            boolean expectedActive,
            String message
    ) {
        WaterTankBlockEntity tank = (WaterTankBlockEntity) helper.getBlockEntity(pos);
        assertTrue(tank != null, "Tank should exist for active check");
        assertTrue(tank.isActive() == expectedActive, message);
    }
}
