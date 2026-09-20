package ca.teamdman.sfm.gametest.tests.general;

import ca.teamdman.sfm.client.net.SFMPacketObservationLog;
import ca.teamdman.sfm.client.net.SFMPacketObservationRuntime;
import ca.teamdman.sfm.common.registry.registration.SFMPackets;
import ca.teamdman.sfm.common.util.SFMDist;
import ca.teamdman.sfm.common.value.SFMValue;
import ca.teamdman.sfm.gametest.SFMGameTest;
import ca.teamdman.sfm.gametest.SFMGameTestDefinition;
import ca.teamdman.sfm.gametest.SFMGameTestHelper;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@SFMGameTest(SFMDist.CLIENT)
public class PacketObservationLogGameTest extends SFMGameTestDefinition {
    @Override
    public String template() {
        return "1x1x1";
    }

    @Override
    public void run(SFMGameTestHelper helper) {
        List<ServerPlayer> players = helper.getLevel().getServer().getPlayerList().getPlayers();
        helper.assertTrue(players.size() == 1, "Integrated packet test requires exactly one player");
        ServerPlayer owner = players.get(0);
        SFMValue expected = SFMValue.object(Map.of(
                "arbitrary", SFMValue.array(List.of(
                        SFMValue.nullValue(),
                        SFMValue.of(true),
                        SFMValue.of(42)
                )),
                "type", SFMValue.of("not-an-application-schema")
        ));

        helper.assertTrue(
                SFMPackets.sendPacketObservation(owner, expected),
                "Private integrated owner should pass the server-side observation gate"
        );

        helper.succeedWhen(() -> {
            SFMPacketObservationLog.Page page = SFMPacketObservationRuntime.get()
                    .page(Optional.empty(), SFMPacketObservationLog.MAX_PAGE_SIZE)
                    .orElseThrow(() -> new AssertionError("Client observation session is not active"));
            long occurrences = page.entries().stream()
                    .map(SFMPacketObservationLog.Entry::value)
                    .filter(expected::equals)
                    .count();
            helper.assertTrue(occurrences == 1, "Expected exactly one packet observation in the client log");
        });
    }
}
