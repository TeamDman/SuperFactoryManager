package ca.teamdman.sfm.gametest.tests.general;

import ca.teamdman.sfm.client.net.SFMClientPacketTransport;
import ca.teamdman.sfm.common.item.PacketItem;
import ca.teamdman.sfm.common.net.SFMPacketInventoryAddress;
import ca.teamdman.sfm.common.registry.registration.SFMBlocks;
import ca.teamdman.sfm.common.util.SFMDist;
import ca.teamdman.sfm.common.value.SFMValue;
import ca.teamdman.sfm.gametest.SFMGameTest;
import ca.teamdman.sfm.gametest.SFMGameTestDefinition;
import ca.teamdman.sfm.gametest.SFMGameTestHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

@SFMGameTest(SFMDist.CLIENT)
public class PacketInsertionGameTest extends SFMGameTestDefinition {
    @Override
    public String template() {
        return "5x3x1";
    }

    @Override
    public void run(SFMGameTestHelper helper) {
        BlockPos directChestPos = new BlockPos(0, 2, 0);
        BlockPos sidedChestPos = new BlockPos(2, 2, 0);
        BlockPos tunnelPos = new BlockPos(3, 2, 0);
        helper.setBlock(directChestPos, Blocks.CHEST);
        helper.setBlock(sidedChestPos, Blocks.CHEST);
        helper.setBlock(tunnelPos, SFMBlocks.TUNNELLED_CABLE.get());

        List<ServerPlayer> players = helper.getLevel().getServer().getPlayerList().getPlayers();
        helper.assertTrue(players.size() == 1, "Integrated packet test requires exactly one player");
        ServerPlayer owner = players.get(0);
        helper.assertTrue(
                owner.containerMenu == owner.inventoryMenu,
                "Packet insertion proof must run without an open inventory menu"
        );

        SFMValue directValue = SFMValue.object(Map.of(
                "route", SFMValue.of("unsided"),
                "value", SFMValue.of(17)
        ));
        SFMValue sidedValue = SFMValue.object(Map.of(
                "route", SFMValue.of("east-face"),
                "value", SFMValue.of(23)
        ));
        SFMPacketInventoryAddress directTarget = address(
                helper,
                directChestPos,
                Optional.empty()
        );
        SFMPacketInventoryAddress sidedTarget = address(
                helper,
                tunnelPos,
                Optional.of(Direction.EAST)
        );
        AtomicReference<String> clientFailure = new AtomicReference<>();

        Minecraft.getInstance().execute(() -> {
            try {
                if (!SFMClientPacketTransport.sendInsertion(directTarget, directValue)) {
                    clientFailure.set("Client gate rejected the unsided insertion request");
                } else if (!SFMClientPacketTransport.sendInsertion(sidedTarget, sidedValue)) {
                    clientFailure.set("Client gate rejected the sided insertion request");
                }
            } catch (RuntimeException failure) {
                clientFailure.set("Client send failed: " + failure);
            }
        });

        helper.succeedWhen(() -> {
            helper.assertTrue(clientFailure.get() == null, String.valueOf(clientFailure.get()));
            helper.assertCount(
                    helper.getItemHandler(directChestPos),
                    PacketItem.create(directValue),
                    1,
                    "Unsided request did not insert exactly one packet"
            );
            helper.assertCount(
                    helper.getItemHandler(sidedChestPos),
                    PacketItem.create(sidedValue),
                    1,
                    "Requested tunnel face did not insert exactly one packet"
            );
        });
    }

    static SFMPacketInventoryAddress address(
            SFMGameTestHelper helper,
            BlockPos localPosition,
            Optional<Direction> side
    ) {
        return new SFMPacketInventoryAddress(
                helper.getLevel().dimension().location(),
                helper.absolutePos(localPosition),
                side
        );
    }
}
