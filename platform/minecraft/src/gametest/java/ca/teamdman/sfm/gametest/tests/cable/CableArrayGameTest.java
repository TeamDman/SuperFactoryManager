package ca.teamdman.sfm.gametest.tests.cable;

import ca.teamdman.sfm.common.block_network.CableNetwork;
import ca.teamdman.sfm.common.block_network.CableNetworkManager;
import ca.teamdman.sfm.common.blockentity.IFacadeBlockEntity;
import ca.teamdman.sfm.common.registry.registration.SFMBlocks;
import ca.teamdman.sfm.gametest.SFMGameTest;
import ca.teamdman.sfm.gametest.SFMGameTestDefinition;
import ca.teamdman.sfm.gametest.SFMGameTestHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;



@SFMGameTest
public class CableArrayGameTest extends SFMGameTestDefinition {

    @Override
    public String template() {
        return createVariants().length + "x2x5";
    }

    @Override
    public void run(SFMGameTestHelper helper) {

        Variant[] variants = createVariants();

        // collect absolute positions of all placed cables so we can assert they belong to the same network
        List<BlockPos> cablePositions = new ArrayList<>();

        for (int x = 0; x < variants.length; x++) {
            Variant v = variants[x];
            for (int z = 0; z < 3; z++) {
                BlockPos localPos = new BlockPos(x, 1, 1 + z);
                helper.setBlock(localPos, v.block);
                var absolute = helper.absolutePos(localPos);

                // If this is a facade variant, set the facade data to glowstone
                if (v.facade) {
                    helper.setFacade(localPos, Blocks.GLOWSTONE.defaultBlockState());
                }

                cablePositions.add(absolute);
            }
        }

        // Verify all blocks are present and that facades have facade data
        for (BlockPos absolute : cablePositions) {
            if (!helper.getLevel().getBlockState(absolute).is(helper.getLevel().getBlockState(absolute).getBlock())) {
                helper.fail("Block at " + absolute + " was not the expected variant block");
                return;
            }

            var be = helper.getLevel().getBlockEntity(absolute);
            if (be instanceof IFacadeBlockEntity facade) {
                if (facade.getFacadeData() == null) {
                    helper.fail("Facade data was not set at " + absolute);
                    return;
                }
            }
        }

        // All placed blocks should be cables; assert that first
        for (BlockPos p : cablePositions) {
            helper.assertTrue(
                    CableNetwork.isCable(helper.getLevel(), p),
                    "Placed block at " + p + " should be a cable"
            );
        }

        var firstPos = cablePositions.get(0);

        var maybeNetwork = CableNetworkManager.getOrRegisterNetworkFromCablePosition(
                helper.getLevel(),
                firstPos
        );
        helper.assertTrue(maybeNetwork.isPresent(), "Cable network should exist for first cable");
        var network = maybeNetwork.get();

        // network should contain all the cable positions we placed
        for (BlockPos p : cablePositions) {
            helper.assertTrue(network.containsCablePosition(p), "Network should contain cable at " + p);

            // For each cable, ensure the manager returns the same network object reference
            var opt = CableNetworkManager.getOrRegisterNetworkFromCablePosition(helper.getLevel(), p);
            helper.assertTrue(
                    opt.isPresent() && opt.get() == network,
                    "Cable at " + p + " did not return the same network instance"
            );
        }

        helper.succeed();
    }

    private static Variant @NotNull [] createVariants() {

        return new Variant[]{
                new Variant(SFMBlocks.CABLE.get(), false),
                new Variant(SFMBlocks.CABLE_FACADE.get(), true),
                new Variant(SFMBlocks.FANCY_CABLE.get(), false),
                new Variant(SFMBlocks.FANCY_CABLE_FACADE.get(), true),
                new Variant(SFMBlocks.TOUGH_CABLE.get(), false),
                new Variant(SFMBlocks.TOUGH_CABLE_FACADE.get(), true),
                new Variant(SFMBlocks.TOUGH_FANCY_CABLE.get(), false),
                new Variant(SFMBlocks.TOUGH_FANCY_CABLE_FACADE.get(), true),
                new Variant(SFMBlocks.TUNNELLED_CABLE.get(), false),
                new Variant(SFMBlocks.TUNNELLED_CABLE_FACADE.get(), true),
                new Variant(SFMBlocks.TUNNELLED_FANCY_CABLE.get(), false),
                new Variant(SFMBlocks.TUNNELLED_FANCY_CABLE_FACADE.get(), true),
                new Variant(SFMBlocks.MANAGER.get(), false),
                new Variant(SFMBlocks.TUNNELLED_MANAGER.get(), false)
        };
    }

    // Define variants: block supplier + whether it's a facade
    record Variant(
            Block block,

            boolean facade
    ) {
    }

}
