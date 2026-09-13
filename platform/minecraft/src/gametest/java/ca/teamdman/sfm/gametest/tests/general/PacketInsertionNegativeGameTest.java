package ca.teamdman.sfm.gametest.tests.general;

import ca.teamdman.sfm.client.net.SFMClientPacketTransport;
import ca.teamdman.sfm.common.net.SFMPacketInventoryAddress;
import ca.teamdman.sfm.common.registry.registration.SFMBlocks;
import ca.teamdman.sfm.common.registry.registration.SFMItems;
import ca.teamdman.sfm.common.util.SFMDist;
import ca.teamdman.sfm.common.value.SFMValue;
import ca.teamdman.sfm.gametest.SFMGameTest;
import ca.teamdman.sfm.gametest.SFMGameTestDefinition;
import ca.teamdman.sfm.gametest.SFMGameTestHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraftforge.items.IItemHandler;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

@SFMGameTest(SFMDist.CLIENT)
public class PacketInsertionNegativeGameTest extends SFMGameTestDefinition {
    @Override
    public String template() {
        return "11x3x1";
    }

    @Override
    public void run(SFMGameTestHelper helper) {
        BlockPos fullChestPos = new BlockPos(0, 2, 0);
        BlockPos barrierChestPos = new BlockPos(2, 2, 0);
        BlockPos fallbackChestPos = new BlockPos(5, 2, 0);
        BlockPos tunnelPos = new BlockPos(6, 2, 0);
        BlockPos noHandlerPos = new BlockPos(9, 2, 0);
        helper.setBlock(fullChestPos, Blocks.CHEST);
        helper.setBlock(barrierChestPos, Blocks.CHEST);
        helper.setBlock(fallbackChestPos, Blocks.CHEST);
        helper.setBlock(tunnelPos, SFMBlocks.TUNNELLED_CABLE.get());
        helper.setBlock(noHandlerPos, Blocks.STONE);

        ChestBlockEntity fullChest = helper.getBlockEntity(fullChestPos, ChestBlockEntity.class);
        for (int slot = 0; slot < fullChest.getContainerSize(); slot++) {
            fullChest.setItem(slot, new ItemStack(Items.STONE, 64));
        }

        BlockPos unloadedPosition = helper.absolutePos(new BlockPos(0, 2, 0))
                .offset(1_000_000, 0, 1_000_000);
        helper.assertTrue(
                !helper.getLevel().isLoaded(unloadedPosition),
                "Chosen negative-test position was unexpectedly loaded"
        );

        SFMValue rejectedValue = SFMValue.of("must-not-arrive");
        SFMValue barrierValue = SFMValue.of("network-order-barrier");
        ResourceLocation dimension = helper.getLevel().dimension().location();
        List<SFMPacketInventoryAddress> rejectedTargets = List.of(
                new SFMPacketInventoryAddress(
                        new ResourceLocation("sfm", "missing_dimension"),
                        helper.absolutePos(fullChestPos),
                        Optional.empty()
                ),
                new SFMPacketInventoryAddress(dimension, unloadedPosition, Optional.empty()),
                PacketInsertionGameTest.address(helper, noHandlerPos, Optional.empty()),
                PacketInsertionGameTest.address(helper, fullChestPos, Optional.empty()),
                PacketInsertionGameTest.address(helper, tunnelPos, Optional.of(Direction.WEST)),
                PacketInsertionGameTest.address(helper, tunnelPos, Optional.empty())
        );
        SFMPacketInventoryAddress barrierTarget = PacketInsertionGameTest.address(
                helper,
                barrierChestPos,
                Optional.empty()
        );
        AtomicReference<String> clientFailure = new AtomicReference<>();

        Minecraft.getInstance().execute(() -> {
            try {
                for (SFMPacketInventoryAddress target : rejectedTargets) {
                    if (!SFMClientPacketTransport.sendInsertion(target, rejectedValue)) {
                        clientFailure.set("Client gate rejected a negative insertion request");
                        return;
                    }
                }
                if (!SFMClientPacketTransport.sendInsertion(barrierTarget, barrierValue)) {
                    clientFailure.set("Client gate rejected the ordering-barrier request");
                }
            } catch (RuntimeException failure) {
                clientFailure.set("Client send failed: " + failure);
            }
        });

        helper.succeedWhen(() -> {
            helper.assertTrue(clientFailure.get() == null, String.valueOf(clientFailure.get()));

            IItemHandler barrier = helper.getItemHandler(barrierChestPos);
            helper.assertCount(barrier, SFMItems.PACKET.get(), 1, "Ordering barrier has not arrived");
            helper.assertTrue(
                    SFMGameTestHelper.count(helper, barrier) == 1,
                    "Ordering barrier inserted an unexpected additional item"
            );

            helper.assertCount(
                    helper.getItemHandler(fallbackChestPos),
                    SFMItems.PACKET.get(),
                    0,
                    "Missing requested/unsided tunnel handler fell back to another face"
            );
            helper.assertCount(
                    helper.getItemHandler(fullChestPos),
                    SFMItems.PACKET.get(),
                    0,
                    "Full inventory accepted a packet"
            );
            helper.assertCount(
                    helper.getItemHandler(fullChestPos),
                    Items.STONE,
                    fullChest.getContainerSize() * 64,
                    "Rejected full-inventory insertion mutated existing contents"
            );
            helper.assertTrue(
                    helper.getBlockState(noHandlerPos).is(Blocks.STONE),
                    "Missing-handler request mutated its target block"
            );
            helper.assertTrue(
                    !helper.getLevel().isLoaded(unloadedPosition),
                    "Unloaded packet target was loaded as a side effect"
            );
        });
    }
}
