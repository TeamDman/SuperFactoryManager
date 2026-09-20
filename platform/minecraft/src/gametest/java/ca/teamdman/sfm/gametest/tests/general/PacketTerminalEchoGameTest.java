package ca.teamdman.sfm.gametest.tests.general;

import ca.teamdman.sfm.common.item.PacketItem;
import ca.teamdman.sfm.common.registry.registration.SFMPackets;
import ca.teamdman.sfm.common.value.SFMValue;
import ca.teamdman.sfm.gametest.SFMGameTestDefinition;
import ca.teamdman.sfm.gametest.SFMGameTestHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;

import java.util.List;
import java.util.Map;

/** Puppet-owned server fixture for the A4.1 terminal-driven external CLI echo. */
public final class PacketTerminalEchoGameTest extends SFMGameTestDefinition {
    public static final BlockPos DESTINATION = new BlockPos(0, 2, 0);
    public static final String FIXTURE_ID = "sfm-a4-terminal-echo-v1";
    public static final String JOB_ID = "a4-terminal-echo-job";
    public static final String PROMPT = "echo this packet through the in-game terminal";
    public static final String WORKER = "local-terminal-echo-demo";

    private static final SFMValue EXTRA = SFMValue.object(Map.of(
            "preserved", SFMValue.of(true),
            "source", SFMValue.of("packet-terminal-gametest")
    ));

    @Override
    public String template() {
        return "3x3x1";
    }

    @Override
    public int maxTicks() {
        return 20 * 90;
    }

    @Override
    public void run(SFMGameTestHelper helper) {
        helper.setBlock(DESTINATION, Blocks.CHEST);
        List<ServerPlayer> players = helper.getLevel().getServer().getPlayerList().getPlayers();
        helper.assertTrue(players.size() == 1,
                "Terminal packet echo requires exactly one integrated-world player");

        BlockPos absoluteDestination = helper.absolutePos(DESTINATION);
        String dimension = helper.getLevel().dimension().location().toString();
        SFMValue request = SFMValue.object(Map.of(
                "JobId", SFMValue.of(JOB_ID),
                "extra", EXTRA,
                "fixture", SFMValue.of(FIXTURE_ID),
                "prompt", SFMValue.of(PROMPT),
                "reply", SFMValue.object(Map.of(
                        "dimension", SFMValue.of(dimension),
                        "x", SFMValue.of(absoluteDestination.getX()),
                        "y", SFMValue.of(absoluteDestination.getY()),
                        "z", SFMValue.of(absoluteDestination.getZ())
                )),
                "type", SFMValue.of("Request")
        ));
        helper.assertTrue(
                SFMPackets.sendPacketObservation(players.get(0), request),
                "Private integrated owner should receive the terminal echo request"
        );

        helper.succeedWhen(() -> helper.assertCount(
                helper.getItemHandler(DESTINATION),
                PacketItem.create(expectedResponse()),
                1,
                "Terminal-driven sfm.exe response did not reach the exact destination chest"
        ));
    }

    public static SFMValue expectedResponse() {
        return SFMValue.object(Map.of(
                "JobId", SFMValue.of(JOB_ID),
                "extra", EXTRA,
                "text", SFMValue.of(PROMPT),
                "type", SFMValue.of("Response"),
                "worker", SFMValue.of(WORKER)
        ));
    }
}
