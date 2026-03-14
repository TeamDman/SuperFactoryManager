package ca.teamdman.sfm.gametest.tests.tunnelled_manager;

import ca.teamdman.sfm.common.registry.SFMBlocks;
import ca.teamdman.sfm.gametest.SFMGameTest;
import ca.teamdman.sfm.gametest.SFMGameTestDefinition;
import ca.teamdman.sfm.gametest.SFMGameTestHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraftforge.items.IItemHandler;

import static ca.teamdman.sfm.gametest.SFMGameTestCountHelpers.assertCount;

/**
 * Verifies that a hopper connected to a barrel through a chain of tunnelled managers only moves items when the full
 * chain is intact. Breaking any manager should pause transfers, while restoring the chain should immediately allow
 * the hopper to resume work once its cooldown is cleared.
 */
@SuppressWarnings({"DataFlowIssue"})
@SFMGameTest
public class TunnelledManagerHopperLongInterruptedGameTest extends SFMGameTestDefinition {
    private static final int MANAGER_COUNT = 6;

    private static final int INITIAL_ITEM_COUNT = 64;

    private static final int DISABLED_COOLDOWN = Integer.MAX_VALUE / 4;

    /**
     * Structure sized to accommodate a barrel, {@link #MANAGER_COUNT} tunnelled managers, and a hopper.
     */
    @Override
    public String template() {

        return (MANAGER_COUNT + 2) + "x2x1";
    }

    /**
     * Provides ample time for the repeated removal and restoration cycle.
     */
    @Override
    public int maxTicks() {

        return 210;
    }

    /**
     * Builds the tunnel, repeatedly breaks managers from left to right, and uses the hopper cooldown to force ticks so
     * that we can deterministically assert when transfers do or do not occur.
     */
    @Override
    public void run(SFMGameTestHelper helper) {

        BlockPos barrelPos = new BlockPos(0, 2, 0);
        BlockPos hopperPos = new BlockPos(MANAGER_COUNT + 1, 2, 0);

        helper.setBlock(barrelPos, SFMBlocks.TEST_BARREL_BLOCK.get());
        helper.setBlock(
                hopperPos,
                Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.WEST)
        );

        for (int index = 0; index < MANAGER_COUNT; index++) {
            helper.setBlock(managerPos(index), SFMBlocks.TUNNELLED_MANAGER_BLOCK.get());
        }

        IItemHandler barrel = helper.getItemHandler(barrelPos);
        HopperBlockEntity hopper = (HopperBlockEntity) helper.getBlockEntity(hopperPos);

        hopper.setCooldown(DISABLED_COOLDOWN);
        hopper.setItem(0, new ItemStack(Blocks.DIRT, INITIAL_ITEM_COUNT));

        int expectedHopper = INITIAL_ITEM_COUNT;
        int expectedBarrel = 0;
        int tickCursor = 1;

        // Prove that with every manager intact the hopper can move an item.
        int hopperAfterInitialMove = expectedHopper - 1;
        int barrelAfterInitialMove = expectedBarrel + 1;
        tickCursor = forceHopperTick(
                helper, hopper, tickCursor, () -> {
                    assertCount(
                            hopper,
                            Blocks.DIRT,
                            hopperAfterInitialMove,
                            "Initial move should reduce hopper stack by one"
                    );
                    assertCount(
                            barrel,
                            Blocks.DIRT,
                            barrelAfterInitialMove,
                            "Initial move should place one item in barrel"
                    );
                }
        );
        expectedHopper = hopperAfterInitialMove;
        expectedBarrel = barrelAfterInitialMove;

        for (int index = 0; index < MANAGER_COUNT; index++) {
            final int managerIndex = index;

            tickCursor = scheduleAction(
                    helper,
                    tickCursor,
                    () -> helper.setBlock(managerPos(managerIndex), Blocks.AIR)
            );

            final int hopperNoMove = expectedHopper;
            final int barrelNoMove = expectedBarrel;
            tickCursor = forceHopperTick(
                    helper, hopper, tickCursor, () -> {
                        assertCount(
                                hopper,
                                Blocks.DIRT,
                                hopperNoMove,
                                "Hopper should not move items while manager " + managerIndex + " is missing"
                        );
                        assertCount(
                                barrel,
                                Blocks.DIRT,
                                barrelNoMove,
                                "Barrel should not receive items while manager " + managerIndex + " is missing"
                        );
                    }
            );

            tickCursor = scheduleAction(
                    helper,
                    tickCursor,
                    () -> helper.setBlock(managerPos(managerIndex), SFMBlocks.TUNNELLED_MANAGER_BLOCK.get())
            );

            int hopperAfterRestore = expectedHopper - 1;
            int barrelAfterRestore = expectedBarrel + 1;
            tickCursor = forceHopperTick(
                    helper, hopper, tickCursor, () -> {
                        assertCount(
                                hopper,
                                Blocks.DIRT,
                                hopperAfterRestore,
                                "Hopper should resume moving items after restoring manager " + managerIndex
                        );
                        assertCount(
                                barrel,
                                Blocks.DIRT,
                                barrelAfterRestore,
                                "Barrel should receive an item after restoring manager " + managerIndex
                        );
                        if (managerIndex == MANAGER_COUNT - 1) {
                            helper.succeed();
                        }
                    }
            );

            expectedHopper = hopperAfterRestore;
            expectedBarrel = barrelAfterRestore;
        }
    }

    private static BlockPos managerPos(int index) {

        return new BlockPos(index + 1, 2, 0);
    }

    private static int scheduleAction(
            SFMGameTestHelper helper,
            int tickCursor,
            Runnable action
    ) {

        helper.runAfterDelay(tickCursor, action);
        return tickCursor + 1;
    }

    /**
     * Clears the hopper cooldown on consecutive ticks so that the vanilla logic attempts a transfer, then asserts the
     * supplied predicate on the following tick.
     */
    private static int forceHopperTick(
            SFMGameTestHelper helper,
            HopperBlockEntity hopper,
            int tickCursor,
            Runnable assertion
    ) {

        helper.runAfterDelay(tickCursor, () -> hopper.setCooldown(0));
        helper.runAfterDelay(tickCursor + 1, assertion);
        helper.runAfterDelay(tickCursor + 2, () -> hopper.setCooldown(DISABLED_COOLDOWN));
        return tickCursor + 3;
    }

}
