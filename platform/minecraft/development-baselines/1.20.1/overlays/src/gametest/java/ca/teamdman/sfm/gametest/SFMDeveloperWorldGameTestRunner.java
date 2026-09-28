package ca.teamdman.sfm.gametest;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.client.developer.SFMDeveloperWorldReadyEvent;
import ca.teamdman.sfm.common.event_bus.SFMSubscribeEvent;
import ca.teamdman.sfm.common.util.SFMDist;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestRegistry;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.gametest.framework.GameTestTicker;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Rotation;

import java.util.Collection;
import java.util.List;

/**
 * Game-test source-set bridge for the optional IDE developer-world action.
 */
public final class SFMDeveloperWorldGameTestRunner {
    private static final int TESTS_PER_ROW = 8;

    private SFMDeveloperWorldGameTestRunner() {
    }

    @SFMSubscribeEvent(value = SFMDist.CLIENT)
    public static void onDeveloperWorldReady(SFMDeveloperWorldReadyEvent event) {
        event.server().execute(() -> runAllGameTests(event.server(), event.level()));
    }

    private static void runAllGameTests(MinecraftServer server, ServerLevel level) {
        List<TestFunction> tests = SFMGameTestDiscovery.gatherTests()
                .map(SFMGameTestDefinition::intoTestFunction)
                .toList();
        if (tests.isEmpty()) {
            SFM.LOGGER.warn("SFM_DEVELOPER_WORLD_GAME_TESTS_SKIPPED reason=no-tests");
            return;
        }

        BlockPos startPos = new BlockPos(0, level.getMinBuildHeight() + 4, 0);
        GameTestTicker.SINGLETON.clear();
        GameTestRunner.clearMarkers(level);
        GameTestRegistry.forgetFailedTests();
        Collection<GameTestInfo> started = GameTestRunner.runTests(
                tests,
                startPos,
                Rotation.NONE,
                level,
                GameTestTicker.SINGLETON,
                TESTS_PER_ROW
        );
        SFM.LOGGER.info(
                "SFM_DEVELOPER_WORLD_GAME_TESTS_STARTED total={} started={}",
                tests.size(),
                started.size()
        );
    }
}
