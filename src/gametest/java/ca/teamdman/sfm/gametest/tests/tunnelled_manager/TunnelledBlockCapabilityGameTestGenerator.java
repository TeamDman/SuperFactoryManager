package ca.teamdman.sfm.gametest.tests.tunnelled_manager;

import ca.teamdman.sfm.common.registry.SFMBlocks;
import ca.teamdman.sfm.common.util.SFMDirections;
import ca.teamdman.sfm.gametest.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.items.IItemHandler;

import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static ca.teamdman.sfm.gametest.SFMGameTestCountHelpers.assertCount;

/**
 * Generates game tests for all tunnelled block variants (manager, cable, fancy cable, and their facade variants)
 * testing item capability tunnelling in all 6 directions.
 * <p>
 * For each block variant and direction, the test:
 * <ul>
 *   <li>Places the tunnelled block at a central position</li>
 *   <li>Places a test barrel adjacent in the specified direction</li>
 *   <li>Queries the item handler capability from the opposite face of the tunnelled block</li>
 *   <li>Inserts a cobblestone through that capability</li>
 *   <li>Verifies the cobblestone appears in the barrel</li>
 * </ul>
 */
@SFMGameTestGenerator
public class TunnelledBlockCapabilityGameTestGenerator extends SFMGameTestGeneratorBase {

    /**
     * Record representing a tunnelled block variant to test.
     */
    private record TunnelledBlockVariant(
            String name,
            Supplier<Block> blockSupplier
    ) {
    }

    /**
     * All tunnelled block variants that should be tested.
     */
    private static final List<TunnelledBlockVariant> TUNNELLED_BLOCKS = List.of(
            new TunnelledBlockVariant("tunnelled_manager", SFMBlocks.TUNNELLED_MANAGER_BLOCK::get),
            new TunnelledBlockVariant("tunnelled_cable", SFMBlocks.TUNNELLED_CABLE_BLOCK::get),
            new TunnelledBlockVariant("tunnelled_cable_facade", SFMBlocks.TUNNELLED_CABLE_FACADE_BLOCK::get),
            new TunnelledBlockVariant("tunnelled_fancy_cable", SFMBlocks.TUNNELLED_FANCY_CABLE_BLOCK::get),
            new TunnelledBlockVariant("tunnelled_fancy_cable_facade", SFMBlocks.TUNNELLED_FANCY_CABLE_FACADE_BLOCK::get)
    );

    @Override
    public void generateTests(Consumer<SFMGameTestDefinition> testConsumer) {

        for (TunnelledBlockVariant variant : TUNNELLED_BLOCKS) {
            for (Direction direction : SFMDirections.DIRECTIONS_WITHOUT_NULL) {
                testConsumer.accept(new TunnelledBlockCapabilityTest(variant, direction));
            }
        }
    }

    /**
     * A game test definition that tests capability tunnelling for a specific block variant and direction.
     */
    private static class TunnelledBlockCapabilityTest extends SFMGameTestDefinition {
        private final TunnelledBlockVariant variant;
        private final Direction direction;

        public TunnelledBlockCapabilityTest(
                TunnelledBlockVariant variant,
                Direction direction
        ) {

            this.variant = variant;
            this.direction = direction;
        }

        @Override
        public String template() {

            return "3x3x3";
        }

        @Override
        public String testName() {

            return variant.name + "_capability_" + direction.name().toLowerCase(Locale.ROOT);
        }

        @Override
        public int maxTicks() {

            return 1;
        }

        @Override
        public void run(SFMGameTestHelper helper) {

            // Place the tunnelled block at the center of the test area
            BlockPos tunnelledPos = new BlockPos(1, 2, 1);
            // Place the barrel adjacent in the specified direction
            BlockPos barrelPos = tunnelledPos.relative(direction);

            // Set up blocks
            helper.setBlock(tunnelledPos, variant.blockSupplier.get());
            helper.setBlock(barrelPos, SFMBlocks.TEST_BARREL_BLOCK.get());

            // Get the item handler from the tunnelled block, querying from the opposite face
            // (i.e., if barrel is to the EAST, we query the tunnelled block from its WEST face)
            Direction queryFace = direction.getOpposite();
            IItemHandler tunnelledHandler = helper.getItemHandler(tunnelledPos, queryFace);

            // Insert a cobblestone through the tunnelled capability
            ItemStack toInsert = new ItemStack(Blocks.COBBLESTONE, 1);
            ItemStack remainder = tunnelledHandler.insertItem(0, toInsert, false);

            // Verify the insert succeeded (no remainder)
            SFMGameTestMethodHelpers.assertTrue(
                    remainder.isEmpty(),
                    "Expected cobblestone to be fully inserted through tunnelled block, but had remainder: " + remainder
            );

            // Get the barrel's item handler directly to verify the item arrived
            IItemHandler barrelHandler = helper.getItemHandler(barrelPos);

            // Assert the barrel now contains exactly 1 cobblestone
            assertCount(
                    barrelHandler,
                    Blocks.COBBLESTONE,
                    1,
                    "Barrel should contain 1 cobblestone after insertion through " + variant.name
                    + " from " + queryFace + " face"
            );

            helper.succeed();
        }
    }
}
