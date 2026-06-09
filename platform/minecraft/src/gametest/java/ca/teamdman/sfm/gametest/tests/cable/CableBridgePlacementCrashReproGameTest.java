package ca.teamdman.sfm.gametest.tests.cable;

import ca.teamdman.sfm.common.block_network.CableNetworkManager;
import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import ca.teamdman.sfm.common.capability.SFMWellKnownCapabilities;
import ca.teamdman.sfm.common.label.LabelPositionHolder;
import ca.teamdman.sfm.common.registry.registration.SFMBlocks;
import ca.teamdman.sfm.common.registry.registration.SFMItems;
import ca.teamdman.sfm.gametest.SFMGameTest;
import ca.teamdman.sfm.gametest.SFMGameTestDefinition;
import ca.teamdman.sfm.gametest.SFMGameTestHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

import java.util.Objects;


@SuppressWarnings({"DataFlowIssue", "OptionalGetWithoutIsPresent"})
@SFMGameTest
public class CableBridgePlacementCrashReproGameTest extends SFMGameTestDefinition {

    @Override
    public String template() {
        return "7x4x7";
    }

    @Override
    public void run(SFMGameTestHelper helper) {
        BlockPos managerPos = new BlockPos(1, 1, 3);
        BlockPos sourcePos = new BlockPos(1, 1, 2);
        BlockPos targetPos = new BlockPos(1, 1, 4);

        BlockPos[] ringWithoutBridge = new BlockPos[]{
                new BlockPos(2, 1, 2),
                new BlockPos(3, 1, 2),
                new BlockPos(4, 1, 2),
                new BlockPos(4, 1, 4),
                new BlockPos(3, 1, 4),
                new BlockPos(2, 1, 4),
                new BlockPos(2, 1, 3)
        };
        BlockPos bridgePos = new BlockPos(4, 1, 3);

        helper.setBlock(managerPos, SFMBlocks.MANAGER.get());
        helper.setBlock(sourcePos, SFMBlocks.TEST_BARREL.get());
        helper.setBlock(targetPos, SFMBlocks.TEST_BARREL.get());
        for (BlockPos cable : ringWithoutBridge) {
            helper.setBlock(cable, SFMBlocks.CABLE.get());
        }

        var source = helper.getItemHandler(sourcePos);
        var target = helper.getItemHandler(targetPos);
        source.insertItem(0, new ItemStack(Blocks.DIRT, 64), false);

        ManagerBlockEntity manager = helper.getBlockEntity(managerPos, ManagerBlockEntity.class);
        manager.setItem(0, new ItemStack(SFMItems.DISK.get()));
        LabelPositionHolder.empty()
                .add("a", helper.absolutePos(sourcePos))
                .add("b", helper.absolutePos(targetPos))
                .save(Objects.requireNonNull(manager.getDisk()));

        manager.setProgram("""
                                EVERY 20 TICKS DO
                                        INPUT FROM a TOP SIDE
                                        OUTPUT TO b TOP SIDE
                END
                """.stripTrailing().stripIndent());

        helper.assertManagerRunning(manager);

        helper.runAfterDelay(60, () -> {
            helper.assertTrue(source.getStackInSlot(0).isEmpty(), "Expected source barrel to be emptied before bridge placement");
            helper.assertTrue(target.getStackInSlot(0).getCount() == 64, "Expected target barrel to receive moved items");

            var networkBeforeBridge = CableNetworkManager
                    .getOrRegisterNetworkFromCablePosition(helper.getLevel(), helper.absolutePos(new BlockPos(2, 1, 3)))
                    .get();
            helper.assertTrue(
                    networkBeforeBridge.getLevelCapabilityCache().size() > 0,
                    "Expected capability cache to be populated before bridge placement"
            , helper.getTick());
            helper.assertTrue(
                    networkBeforeBridge
                            .getLevelCapabilityCache()
                            .getCapability(
                                    helper.absolutePos(sourcePos),
                                    SFMWellKnownCapabilities.ITEM_HANDLER,
                                    Direction.UP
                            )
                    != null,
                    "Expected directional (UP) item capability cache entry before bridge placement"
            , helper.getTick());

            // This is the critical placement: it touches the same existing network on two sides.
            // On buggy versions this can crash with listener double-registration during network merge.
            helper.getLevel().setBlockAndUpdate(
                    helper.absolutePos(bridgePos),
                    SFMBlocks.CABLE.get().defaultBlockState()
            );

            var mergedNetwork = CableNetworkManager
                    .getOrRegisterNetworkFromCablePosition(helper.getLevel(), helper.absolutePos(bridgePos))
                    .get();
            helper.assertTrue(
                    mergedNetwork.getCableCount() == 9,
                    "Expected ring + bridge to form a single 9-cable network"
            , helper.getTick());

            helper.succeed();
        });
    }
}
