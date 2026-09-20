package ca.teamdman.sfm.gametest;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.client.developer.SFMDeveloperWorldReadyEvent;
import ca.teamdman.sfm.common.event_bus.SFMSubscribeEvent;
import ca.teamdman.sfm.common.util.SFMDist;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRegistry;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.gametest.framework.GameTestTicker;
import net.minecraft.gametest.framework.MultipleTestTracker;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Rotation;
import net.minecraftforge.event.TickEvent;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

/**
 * Game-test source-set bridge for the optional IDE developer-world action.
 */
public final class SFMDeveloperWorldGameTestRunner {
    private static final int TESTS_PER_ROW = 8;
    private static final int COUNTDOWN_SECONDS = 10;
    private static final int TICKS_PER_SECOND = 20;

    private static volatile @Nullable PendingGameTestStart pendingGameTestStart;
    private static volatile int lastAnnouncedSeconds = -1;

    private SFMDeveloperWorldGameTestRunner() {
    }

    @SFMSubscribeEvent(value = SFMDist.CLIENT)
    public static void onDeveloperWorldReady(SFMDeveloperWorldReadyEvent event) {
        lastAnnouncedSeconds = -1;
        pendingGameTestStart = new PendingGameTestStart(
                event.server(),
                event.level(),
                COUNTDOWN_SECONDS * TICKS_PER_SECOND
        );
        SFM.LOGGER.info(
                "SFM_DEVELOPER_WORLD_GAME_TESTS_COUNTDOWN seconds={}",
                COUNTDOWN_SECONDS
        );
    }

    @SFMSubscribeEvent(value = SFMDist.CLIENT)
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        PendingGameTestStart countdown = pendingGameTestStart;
        if (countdown == null) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.getSingleplayerServer() != countdown.server()) {
            pendingGameTestStart = null;
            lastAnnouncedSeconds = -1;
            SFM.LOGGER.info("SFM_DEVELOPER_WORLD_GAME_TESTS_CANCELLED reason=world_unloaded");
            return;
        }

        if (countdown.ticksRemaining() <= 0) {
            pendingGameTestStart = null;
            lastAnnouncedSeconds = -1;
            countdown.server().execute(() -> runAllGameTests(countdown.server(), countdown.level()));
            return;
        }

        int secondsRemaining = secondsRemaining(countdown.ticksRemaining());
        if (secondsRemaining != lastAnnouncedSeconds) {
            lastAnnouncedSeconds = secondsRemaining;
            announceCountdown(countdown.server(), secondsRemaining);
        }
        pendingGameTestStart = countdown.withTicksRemaining(countdown.ticksRemaining() - 1);
    }

    static int secondsRemaining(int ticksRemaining) {
        return Math.max(1, (ticksRemaining + TICKS_PER_SECOND - 1) / TICKS_PER_SECOND);
    }

    private static void announceCountdown(MinecraftServer server, int secondsRemaining) {
        server.getPlayerList().broadcastSystemMessage(
                Component.literal("Running SFM GameTests in " + secondsRemaining + "..."),
                false
        );
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
        MultipleTestTracker tracker = new MultipleTestTracker(started);
        tracker.addListener(new CompletionSummary(server, tracker));
        SFM.LOGGER.info(
                "SFM_DEVELOPER_WORLD_GAME_TESTS_STARTED total={} started={}",
                tests.size(),
                started.size()
        );
    }

    private static final class CompletionSummary implements GameTestListener {
        private final MinecraftServer server;
        private final MultipleTestTracker tracker;
        private boolean announced;

        private CompletionSummary(MinecraftServer server, MultipleTestTracker tracker) {
            this.server = server;
            this.tracker = tracker;
        }

        @Override
        public void testStructureLoaded(GameTestInfo test) {
        }

        @Override
        public void testPassed(GameTestInfo test) {
            announceIfDone();
        }

        @Override
        public void testFailed(GameTestInfo test) {
            announceIfDone();
        }

        private void announceIfDone() {
            if (announced || !tracker.isDone()) {
                return;
            }

            announced = true;
            int total = tracker.getTotalCount();
            int failedRequired = tracker.getFailedRequiredCount();
            int failedOptional = tracker.getFailedOptionalCount();
            int failed = failedRequired + failedOptional;
            int passed = total - failed;
            String summary = "SFM GameTests complete: "
                    + passed + " passed, " + failed + " failed (" + total + " total).";
            server.getPlayerList().broadcastSystemMessage(Component.literal(summary), false);
            SFM.LOGGER.info(
                    "SFM_DEVELOPER_WORLD_GAME_TESTS_COMPLETE total={} passed={} failed={} required_failed={} optional_failed={}",
                    total,
                    passed,
                    failed,
                    failedRequired,
                    failedOptional
            );
        }
    }

    private record PendingGameTestStart(
            MinecraftServer server,
            ServerLevel level,
            int ticksRemaining
    ) {
        private PendingGameTestStart withTicksRemaining(int ticks) {
            return new PendingGameTestStart(server, level, ticks);
        }
    }
}
