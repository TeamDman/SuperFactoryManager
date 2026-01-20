package ca.teamdman.sfm.gametest.tests.general;

import ca.teamdman.sfm.gametest.SFMGameTest;
import ca.teamdman.sfm.gametest.SFMGameTestDefinition;
import ca.teamdman.sfm.gametest.SFMGameTestHelper;
import ca.teamdman.sfml.ast.Side;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ObserverBlock;
import net.minecraft.world.level.block.state.BlockState;

import static ca.teamdman.sfm.gametest.SFMGameTestMethodHelpers.assertTrue;

/**
 * Validates the fix for #445 where UP and DOWN facing blocks would throw exceptions
 * when trying to resolve relative sides (LEFT, RIGHT, FRONT, BACK) since those
 * directions cannot be rotated horizontally.
 * <p>
 * This test places observers facing all 6 directions and resolves all Side variants
 * for each, ensuring no exceptions are thrown.
 */
@SuppressWarnings({
        "RedundantSuppression",
        "DataFlowIssue",
        "OptionalGetWithoutIsPresent",
        "DuplicatedCode",
        "ArraysAsListWithZeroOrOneArgument"
})
@SFMGameTest
public class SideResolveDirectionGameTest extends SFMGameTestDefinition {

    @Override
    public String template() {

        return Direction.values().length + "x1x1";
    }

    @Override
    public void run(SFMGameTestHelper helper) {
        // Place observers facing each of the 6 directions
        Direction[] directions = Direction.values();
        for (int i = 0; i < directions.length; i++) {
            Direction dir = directions[i];
            BlockPos pos = new BlockPos(i, 1, 0);
            BlockState observerState = Blocks.OBSERVER.defaultBlockState()
                    .setValue(ObserverBlock.FACING, dir);
            helper.setBlock(pos, observerState);
        }

        // Now resolve all Side variants for each observer
        for (int i = 0; i < directions.length; i++) {
            Direction facing = directions[i];
            BlockPos pos = new BlockPos(i, 1, 0);
            BlockState blockState = helper.getBlockState(pos);

            // Verify the observer is placed correctly
            Direction actualFacing = blockState.getValue(ObserverBlock.FACING);
            assertTrue(
                    actualFacing == facing,
                    "Observer at " + pos + " should be facing " + facing + " but is facing " + actualFacing
            );

            // Try to resolve all Side variants - this should not throw any exceptions
            // The fix for #445 ensures that UP/DOWN facing blocks return null for
            // relative sides (LEFT, RIGHT, FRONT, BACK) instead of throwing an exception
            for (Side side : Side.values()) {
                try {
                    Direction resolved = side.resolve(blockState);

                    // Validate expected results for absolute directions
                    switch (side) {
                        case TOP -> assertTrue(
                                resolved == Direction.UP,
                                "Side.TOP should resolve to Direction.UP, got " + resolved
                        );
                        case BOTTOM -> assertTrue(
                                resolved == Direction.DOWN,
                                "Side.BOTTOM should resolve to Direction.DOWN, got " + resolved
                        );
                        case NORTH -> assertTrue(
                                resolved == Direction.NORTH,
                                "Side.NORTH should resolve to Direction.NORTH, got " + resolved
                        );
                        case SOUTH -> assertTrue(
                                resolved == Direction.SOUTH,
                                "Side.SOUTH should resolve to Direction.SOUTH, got " + resolved
                        );
                        case EAST -> assertTrue(
                                resolved == Direction.EAST,
                                "Side.EAST should resolve to Direction.EAST, got " + resolved
                        );
                        case WEST -> assertTrue(
                                resolved == Direction.WEST,
                                "Side.WEST should resolve to Direction.WEST, got " + resolved
                        );
                        case NULL -> assertTrue(
                                resolved == null,
                                "Side.NULL should resolve to null, got " + resolved
                        );
                        // For relative sides (LEFT, RIGHT, FRONT, BACK), we just ensure no exception
                        // was thrown. The fix for #445 makes LEFT/RIGHT return null for UP/DOWN facing blocks.
                        case LEFT, RIGHT -> {
                            // For horizontally-facing observers, these should resolve to a direction
                            // For UP/DOWN facing observers, these should return null (fix for #445)
                            if (facing == Direction.UP || facing == Direction.DOWN) {
                                assertTrue(
                                        resolved == null,
                                        "Side."
                                        + side
                                        + " should resolve to null for "
                                        + facing
                                        + " facing observer, got "
                                        + resolved
                                );
                            } else {
                                assertTrue(
                                        resolved != null,
                                        "Side."
                                        + side
                                        + " should resolve to a direction for "
                                        + facing
                                        + " facing observer, but got null"
                                );
                            }
                        }
                        case FRONT, BACK -> {
                            // FRONT and BACK should always resolve since they don't involve rotation
                            assertTrue(
                                    resolved != null,
                                    "Side."
                                    + side
                                    + " should resolve for "
                                    + facing
                                    + " facing observer, but got null"
                            );
                            if (side == Side.FRONT) {
                                assertTrue(
                                        resolved == facing,
                                        "Side.FRONT should resolve to " + facing + " for " + facing + " facing observer, got " + resolved
                                );
                            } else {
                                assertTrue(
                                        resolved == facing.getOpposite(),
                                        "Side.BACK should resolve to " + facing.getOpposite() + " for " + facing + " facing observer, got " + resolved
                                );
                            }
                        }
                    }
                } catch (Exception e) {
                    helper.fail(
                            "Side."
                            + side
                            + ".resolve() threw an exception for "
                            + facing
                            + " facing observer: "
                            + e.getMessage(),
                            pos
                    );
                }
            }
        }

        helper.succeed();
    }

}
