package ca.teamdman.sfm.gametest.tests.general;

import ca.teamdman.sfm.common.net.SFMPacketEffectGate;
import ca.teamdman.sfm.common.registry.registration.SFMItems;
import ca.teamdman.sfm.common.registry.registration.SFMPackets;
import ca.teamdman.sfm.common.value.SFMValue;
import ca.teamdman.sfm.gametest.SFMGameTestDefinition;
import ca.teamdman.sfm.gametest.SFMGameTestHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/** Puppet-owned live proof that opening the integrated world to LAN disables packet effects. */
public final class PacketLanDisabledGameTest extends SFMGameTestDefinition {
    public static final BlockPos DESTINATION = new BlockPos(0, 2, 0);
    public static final String FIXTURE_ID = "sfm-a4-lan-disabled-v1";
    public static final String RESPONSE_TEXT = "must not reach the LAN-published world";

    private static final AtomicBoolean TERMINAL_ATTEMPT_COMPLETE = new AtomicBoolean();

    @Override
    public String template() {
        return "3x3x1";
    }

    @Override
    public int maxTicks() {
        return 20 * 120;
    }

    @Override
    public void run(SFMGameTestHelper helper) {
        TERMINAL_ATTEMPT_COMPLETE.set(false);
        helper.setBlock(DESTINATION, Blocks.CHEST);
        List<ServerPlayer> players = helper.getLevel().getServer().getPlayerList().getPlayers();
        helper.assertTrue(players.size() == 1, "LAN packet fixture requires exactly one integrated-world player");
        ServerPlayer owner = players.get(0);
        helper.assertTrue(
                SFMPacketEffectGate.allowsServerEffects(owner),
                "LAN packet fixture must emit its request while the world is still private"
        );

        BlockPos absoluteDestination = helper.absolutePos(DESTINATION);
        SFMValue request = SFMValue.object(Map.of(
                "fixture", SFMValue.of(FIXTURE_ID),
                "reply", SFMValue.object(Map.of(
                        "dimension", SFMValue.of(helper.getLevel().dimension().location().toString()),
                        "x", SFMValue.of(absoluteDestination.getX()),
                        "y", SFMValue.of(absoluteDestination.getY()),
                        "z", SFMValue.of(absoluteDestination.getZ())
                )),
                "type", SFMValue.of("LanDisabledRequest")
        ));
        helper.assertTrue(
                SFMPackets.sendPacketObservation(owner, request),
                "Private integrated owner should receive the pre-publication LAN fixture request"
        );

        helper.succeedWhen(() -> {
            helper.assertTrue(
                    TERMINAL_ATTEMPT_COMPLETE.get(),
                    "Waiting for terminal to observe the disabled LAN send"
            );
            helper.assertTrue(
                    helper.getLevel().getServer().isPublished(),
                    "Integrated server was not published to LAN"
            );
            helper.assertTrue(
                    !SFMPacketEffectGate.allowsServerEffects(owner),
                    "Server packet effects remained enabled after LAN publication"
            );
            helper.assertCount(
                    helper.getItemHandler(DESTINATION),
                    SFMItems.PACKET.get(),
                    0,
                    "LAN-disabled terminal send inserted a packet"
            );
        });
    }

    public static void markTerminalAttemptComplete() {
        TERMINAL_ATTEMPT_COMPLETE.set(true);
    }
}
