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
 * Tests a plus-shaped water tank network with water, verifying that removing
 * the center tank splits the network into 4 separate networks, each with their
 * own capacity based on active member count.
 * <p>
 * Structure:
 * <pre>
 *     W
 *    WTW
 *   WTTTW
 *    WTW
 *     W
 * </pre>
 * Where T = water tank, W = water source (with appropriate containment)
 */
@SuppressWarnings({
        "RedundantSuppression",
        "DataFlowIssue",
        "OptionalGetWithoutIsPresent",
        "DuplicatedCode"
})
@SFMGameTest
public class WaterTankPlusSplitGameTest extends SFMGameTestDefinition {

    @Override
    public String template() {
        // Need space for plus shape with water around it and containment
        return "11x2x11";
    }

    @Override
    public void run(SFMGameTestHelper helper) {
        // Center of the structure
        int cx = 5;
        int cz = 5;
        int y = 2;

        // Build containment walls (stone ring around the entire structure)
        for (int x = 2; x <= 8; x++) {
            helper.setBlock(new BlockPos(x, y, 2), Blocks.STONE);
            helper.setBlock(new BlockPos(x, y, 8), Blocks.STONE);
        }
        for (int z = 2; z <= 8; z++) {
            helper.setBlock(new BlockPos(2, y, z), Blocks.STONE);
            helper.setBlock(new BlockPos(8, y, z), Blocks.STONE);
        }

        // Place water tanks in plus shape
        BlockPos center = new BlockPos(cx, y, cz);
        BlockPos north = new BlockPos(cx, y, cz - 1);
        BlockPos south = new BlockPos(cx, y, cz + 1);
        BlockPos east = new BlockPos(cx + 1, y, cz);
        BlockPos west = new BlockPos(cx - 1, y, cz);

        helper.setBlock(center, SFMBlocks.WATER_TANK_BLOCK.get());
        helper.setBlock(north, SFMBlocks.WATER_TANK_BLOCK.get());
        helper.setBlock(south, SFMBlocks.WATER_TANK_BLOCK.get());
        helper.setBlock(east, SFMBlocks.WATER_TANK_BLOCK.get());
        helper.setBlock(west, SFMBlocks.WATER_TANK_BLOCK.get());

        // Place water sources around each tank (need 2 per tank to activate)
        // Center tank already has 4 tank neighbors, so it needs water in diagonal positions
        // Actually, center tank touches 4 other tanks, not water. Let's place water at corners.

        // Water around the plus shape - filling in the gaps
        // North tank needs water at (cx, y, cz-2) and either (cx-1, y, cz-1) or (cx+1, y, cz-1)
        helper.setBlock(new BlockPos(cx, y, cz - 2), Blocks.WATER);     // north of north tank
        helper.setBlock(new BlockPos(cx - 1, y, cz - 1), Blocks.WATER); // west of north tank
        helper.setBlock(new BlockPos(cx + 1, y, cz - 1), Blocks.WATER); // east of north tank

        // South tank
        helper.setBlock(new BlockPos(cx, y, cz + 2), Blocks.WATER);     // south of south tank
        helper.setBlock(new BlockPos(cx - 1, y, cz + 1), Blocks.WATER); // west of south tank
        helper.setBlock(new BlockPos(cx + 1, y, cz + 1), Blocks.WATER); // east of south tank

        // East tank
        helper.setBlock(new BlockPos(cx + 2, y, cz), Blocks.WATER);     // east of east tank
        helper.setBlock(new BlockPos(cx + 1, y, cz - 1), Blocks.WATER); // already placed (north-east)
        helper.setBlock(new BlockPos(cx + 1, y, cz + 1), Blocks.WATER); // already placed (south-east)

        // West tank
        helper.setBlock(new BlockPos(cx - 2, y, cz), Blocks.WATER);     // west of west tank
        helper.setBlock(new BlockPos(cx - 1, y, cz - 1), Blocks.WATER); // already placed (north-west)
        helper.setBlock(new BlockPos(cx - 1, y, cz + 1), Blocks.WATER); // already placed (south-west)

        // Now verify tanks are active
        // North, South, East, West should each have 2 water sources
        // Center has 0 water sources (surrounded by other tanks), so it's inactive
        WaterTankBlockEntity centerTank = (WaterTankBlockEntity) helper.getBlockEntity(center);
        WaterTankBlockEntity northTank = (WaterTankBlockEntity) helper.getBlockEntity(north);
        WaterTankBlockEntity southTank = (WaterTankBlockEntity) helper.getBlockEntity(south);
        WaterTankBlockEntity eastTank = (WaterTankBlockEntity) helper.getBlockEntity(east);
        WaterTankBlockEntity westTank = (WaterTankBlockEntity) helper.getBlockEntity(west);

        assertTrue(centerTank != null, "Center tank should exist");
        assertTrue(northTank != null, "North tank should exist");
        assertTrue(southTank != null, "South tank should exist");
        assertTrue(eastTank != null, "East tank should exist");
        assertTrue(westTank != null, "West tank should exist");

        // Center is inactive (no water touching it directly)
        assertTrue(!centerTank.isActive(), "Center tank should be inactive (no water touching)");
        assertTrue(northTank.isActive(), "North tank should be active");
        assertTrue(southTank.isActive(), "South tank should be active");
        assertTrue(eastTank.isActive(), "East tank should be active");
        assertTrue(westTank.isActive(), "West tank should be active");

        // 4 active members in the network: capacity = 2^3 * 1000 = 8000
        int expectedCapacity = 8000;
        assertTrue(
                centerTank.TANK.getCapacity() == expectedCapacity,
                "Center tank should have capacity " + expectedCapacity + " but had " + centerTank.TANK.getCapacity()
        );
        assertTrue(
                northTank.TANK.getCapacity() == expectedCapacity,
                "North tank should have capacity " + expectedCapacity + " but had " + northTank.TANK.getCapacity()
        );

        // Remove the center tank - this splits into 4 networks of 1 active tank each
        helper.setBlock(center, Blocks.AIR);

        // Refresh references
        northTank = (WaterTankBlockEntity) helper.getBlockEntity(north);
        southTank = (WaterTankBlockEntity) helper.getBlockEntity(south);
        eastTank = (WaterTankBlockEntity) helper.getBlockEntity(east);
        westTank = (WaterTankBlockEntity) helper.getBlockEntity(west);

        // Each network now has 1 active member: capacity = 2^0 * 1000 = 1000
        int expectedCapacityAfterSplit = 1000;
        assertTrue(
                northTank.TANK.getCapacity() == expectedCapacityAfterSplit,
                "North tank should have capacity " + expectedCapacityAfterSplit + " after split but had " + northTank.TANK.getCapacity()
        );
        assertTrue(
                southTank.TANK.getCapacity() == expectedCapacityAfterSplit,
                "South tank should have capacity " + expectedCapacityAfterSplit + " after split but had " + southTank.TANK.getCapacity()
        );
        assertTrue(
                eastTank.TANK.getCapacity() == expectedCapacityAfterSplit,
                "East tank should have capacity " + expectedCapacityAfterSplit + " after split but had " + eastTank.TANK.getCapacity()
        );
        assertTrue(
                westTank.TANK.getCapacity() == expectedCapacityAfterSplit,
                "West tank should have capacity " + expectedCapacityAfterSplit + " after split but had " + westTank.TANK.getCapacity()
        );

        // Restore center tank - networks should merge back
        helper.setBlock(center, SFMBlocks.WATER_TANK_BLOCK.get());

        // Refresh references
        centerTank = (WaterTankBlockEntity) helper.getBlockEntity(center);
        northTank = (WaterTankBlockEntity) helper.getBlockEntity(north);

        // Back to 4 active members (center is still inactive): capacity = 8000
        assertTrue(
                centerTank.TANK.getCapacity() == expectedCapacity,
                "Center tank should have capacity " + expectedCapacity + " after merge but had " + centerTank.TANK.getCapacity()
        );
        assertTrue(
                northTank.TANK.getCapacity() == expectedCapacity,
                "North tank should have capacity " + expectedCapacity + " after merge but had " + northTank.TANK.getCapacity()
        );

        helper.succeed();
    }
}
