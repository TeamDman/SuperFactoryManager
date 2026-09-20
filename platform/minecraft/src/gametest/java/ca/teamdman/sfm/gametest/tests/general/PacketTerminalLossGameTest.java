package ca.teamdman.sfm.gametest.tests.general;

import ca.teamdman.sfm.common.item.PacketItem;
import ca.teamdman.sfm.common.registry.registration.SFMItems;
import ca.teamdman.sfm.common.registry.registration.SFMPackets;
import ca.teamdman.sfm.common.value.SFMValue;
import ca.teamdman.sfm.gametest.SFMGameTestDefinition;
import ca.teamdman.sfm.gametest.SFMGameTestHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraftforge.items.IItemHandler;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/** Puppet-owned external-CLI proof of best-effort loss and the absence of delayed retry. */
public final class PacketTerminalLossGameTest extends SFMGameTestDefinition {
    public static final BlockPos FULL_DESTINATION = new BlockPos(0, 2, 0);
    public static final BlockPos BARRIER_DESTINATION = new BlockPos(2, 2, 0);
    public static final BlockPos NO_HANDLER_DESTINATION = new BlockPos(4, 2, 0);
    public static final String FIXTURE_ID = "sfm-a4-terminal-loss-v1";
    public static final int REJECTED_ATTEMPTS = 4;

    private static final int REPLAY_OBSERVATION_TICKS = 40;
    private static final SFMValue BARRIER_VALUE = SFMValue.object(Map.of(
            "case", SFMValue.of("network-order-barrier"),
            "fixture", SFMValue.of(FIXTURE_ID)
    ));

    @Override
    public String template() {
        return "7x3x1";
    }

    @Override
    public int maxTicks() {
        return 20 * 120;
    }

    @Override
    public void run(SFMGameTestHelper helper) {
        helper.setBlock(FULL_DESTINATION, Blocks.CHEST);
        helper.setBlock(BARRIER_DESTINATION, Blocks.CHEST);
        helper.setBlock(NO_HANDLER_DESTINATION, Blocks.STONE);
        ChestBlockEntity fullChest = helper.getBlockEntity(FULL_DESTINATION, ChestBlockEntity.class);
        for (int slot = 0; slot < fullChest.getContainerSize(); slot++) {
            fullChest.setItem(slot, new ItemStack(Items.STONE, 64));
        }

        List<ServerPlayer> players = helper.getLevel().getServer().getPlayerList().getPlayers();
        helper.assertTrue(players.size() == 1, "Terminal loss fixture requires exactly one integrated-world player");
        BlockPos unloaded = helper.absolutePos(FULL_DESTINATION).offset(1_000_000, 0, 1_000_000);
        helper.assertTrue(!helper.getLevel().isLoaded(unloaded), "Loss fixture unloaded target was already loaded");

        ResourceLocation dimension = helper.getLevel().dimension().location();
        SFMValue request = SFMValue.object(Map.of(
                "fixture", SFMValue.of(FIXTURE_ID),
                "targets", SFMValue.object(Map.of(
                        "barrier", address(dimension, helper.absolutePos(BARRIER_DESTINATION)),
                        "full", address(dimension, helper.absolutePos(FULL_DESTINATION)),
                        "missing_dimension", address(
                                new ResourceLocation("sfm", "missing_dimension"),
                                helper.absolutePos(FULL_DESTINATION)
                        ),
                        "no_handler", address(dimension, helper.absolutePos(NO_HANDLER_DESTINATION)),
                        "unloaded", address(dimension, unloaded)
                )),
                "type", SFMValue.of("LossRequest")
        ));
        helper.assertTrue(
                SFMPackets.sendPacketObservation(players.get(0), request),
                "Private integrated owner should receive the terminal loss fixture request"
        );

        AtomicLong capacityOpenedAt = new AtomicLong(-1L);
        helper.succeedWhen(() -> {
            IItemHandler barrier = helper.getItemHandler(BARRIER_DESTINATION);
            helper.assertCount(
                    barrier,
                    PacketItem.create(BARRIER_VALUE),
                    1,
                    "Terminal loss barrier has not arrived"
            );
            helper.assertTrue(
                    SFMGameTestHelper.count(helper, barrier) == 1,
                    "Terminal loss barrier chest received an unexpected item"
            );

            long gameTime = helper.getLevel().getGameTime();
            if (capacityOpenedAt.compareAndSet(-1L, gameTime)) {
                assertRejectedState(helper, fullChest, unloaded, fullChest.getContainerSize() * 64);
                fullChest.setItem(0, ItemStack.EMPTY);
                fullChest.setChanged();
            }
            long openedAt = capacityOpenedAt.get();
            helper.assertTrue(
                    gameTime >= openedAt + REPLAY_OBSERVATION_TICKS,
                    "Waiting to prove the full-destination send is not retried after capacity appears"
            );
            assertRejectedState(helper, fullChest, unloaded, (fullChest.getContainerSize() - 1) * 64);
            helper.assertTrue(fullChest.getItem(0).isEmpty(), "Dropped full-destination send replayed into freed capacity");
        });
    }

    public static SFMValue barrierValue() {
        return BARRIER_VALUE;
    }

    private static SFMValue address(ResourceLocation dimension, BlockPos position) {
        return SFMValue.object(Map.of(
                "dimension", SFMValue.of(dimension.toString()),
                "x", SFMValue.of(position.getX()),
                "y", SFMValue.of(position.getY()),
                "z", SFMValue.of(position.getZ())
        ));
    }

    private static void assertRejectedState(
            SFMGameTestHelper helper,
            ChestBlockEntity fullChest,
            BlockPos unloaded,
            int expectedStone
    ) {
        helper.assertCount(
                helper.getItemHandler(FULL_DESTINATION),
                SFMItems.PACKET.get(),
                0,
                "Full destination accepted or later replayed a packet"
        );
        helper.assertCount(
                helper.getItemHandler(FULL_DESTINATION),
                Items.STONE,
                expectedStone,
                "Full destination contents changed unexpectedly"
        );
        helper.assertTrue(
                helper.getBlockState(NO_HANDLER_DESTINATION).is(Blocks.STONE),
                "Missing-handler send mutated its target block"
        );
        helper.assertTrue(!helper.getLevel().isLoaded(unloaded), "Unloaded send created a chunk ticket");
        helper.assertTrue(fullChest.getContainerSize() > 0, "Loss fixture chest became unavailable");
    }
}
