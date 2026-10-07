package ca.teamdman.sfm.gametest;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.client.developer.SFMDeveloperWorldReadyEvent;
import ca.teamdman.sfm.common.event_bus.SFMSubscribeEvent;
import ca.teamdman.sfm.common.util.SFMDist;
{% if features.developer_world_gametest_lifecycle %}
import net.minecraft.client.Minecraft;
{% endif %}
import net.minecraft.core.BlockPos;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
{% when '26.1.2' %}
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
{% endcase %}
import net.minecraft.gametest.framework.GameTestInfo;
{% if features.developer_world_gametest_lifecycle %}
import net.minecraft.gametest.framework.GameTestListener;
{% endif %}
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
import net.minecraft.gametest.framework.GameTestRegistry;
{% when '26.1.2' %}
import net.minecraft.gametest.framework.GameTestInstance;
{% endcase %}
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.gametest.framework.GameTestTicker;
{% if features.developer_world_gametest_lifecycle %}
import net.minecraft.gametest.framework.MultipleTestTracker;
{% endif %}
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}
import net.minecraft.gametest.framework.TestFunction;
{% when '1.21', '1.21.1' %}
import net.minecraft.gametest.framework.RetryOptions;
import net.minecraft.gametest.framework.StructureGridSpawner;
import net.minecraft.gametest.framework.TestFunction;
{% when '26.1.2' %}
import net.minecraft.gametest.framework.RetryOptions;
import net.minecraft.gametest.framework.StructureGridSpawner;
import net.minecraft.resources.Identifier;
{% endcase %}
{% if features.developer_world_gametest_lifecycle %}
import net.minecraft.network.chat.Component;
{% endif %}
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Rotation;
{% if features.developer_world_gametest_lifecycle %}
import net.minecraftforge.event.TickEvent;
import org.jetbrains.annotations.Nullable;
{% endif %}

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}
import java.util.Collection;
{% when '1.21', '1.21.1', '26.1.2' %}
{% endcase %}
import java.util.List;

/**
 * Game-test source-set bridge for the optional IDE developer-world action.
 */
public final class SFMDeveloperWorldGameTestRunner {
    private static final int TESTS_PER_ROW = 8;
{% if features.developer_world_gametest_lifecycle %}
    private static final int COUNTDOWN_SECONDS = 10;
    private static final int TICKS_PER_SECOND = 20;
{% endif %}

{% if features.developer_world_gametest_lifecycle %}
    private static volatile @Nullable PendingGameTestStart pendingGameTestStart;
    private static volatile int lastAnnouncedSeconds = -1;

{% endif %}
    private SFMDeveloperWorldGameTestRunner() {
    }

    @SFMSubscribeEvent(value = SFMDist.CLIENT)
    public static void onDeveloperWorldReady(SFMDeveloperWorldReadyEvent event) {
{% if features.developer_world_gametest_lifecycle %}
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
{% else %}
        event.server().execute(() -> runAllGameTests(event.server(), event.level()));
{% endif %}
    }

{% if features.developer_world_gametest_lifecycle %}
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

{% endif %}
    private static void runAllGameTests(MinecraftServer server, ServerLevel level) {
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
        List<TestFunction> tests = SFMGameTestDiscovery.gatherTests()
                .map(SFMGameTestDefinition::intoTestFunction)
{% when '26.1.2' %}
        List<Identifier> selectedTestIds = SFMGameTestDiscovery.gatherSelectedTests()
                .stream()
                .map(test -> Identifier.fromNamespaceAndPath(SFM.MOD_ID, test.testName()))
{% endcase %}
                .toList();
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
{% when '26.1.2' %}
        List<Holder.Reference<GameTestInstance>> tests = server
                .registryAccess()
                .lookupOrThrow(Registries.TEST_INSTANCE)
                .listElements()
                .filter(test -> selectedTestIds.contains(test.key().identifier()))
                .toList();
{% endcase %}
        if (tests.isEmpty()) {
            SFM.LOGGER.warn("SFM_DEVELOPER_WORLD_GAME_TESTS_SKIPPED reason=no-tests");
            return;
        }

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
        BlockPos startPos = new BlockPos(0, level.getMinBuildHeight() + 4, 0);
{% when '26.1.2' %}
        BlockPos startPos = new BlockPos(0, level.dimensionType().minY() + 4, 0);
{% endcase %}
        GameTestTicker.SINGLETON.clear();
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}
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
{% when '1.21', '1.21.1' %}
        GameTestRunner.clearMarkers(level);
        GameTestRegistry.forgetFailedTests();
        List<GameTestInfo> gameTestInfos = tests
                .stream()
                .map(test -> new GameTestInfo(test, Rotation.NONE, level, RetryOptions.noRetries()))
                .toList();
        GameTestRunner.Builder
                .fromInfo(gameTestInfos, level)
                .newStructureSpawner(new StructureGridSpawner(startPos, TESTS_PER_ROW, false))
                .build()
                .start();
{% when '26.1.2' %}
        List<GameTestInfo> gameTestInfos = tests
                .stream()
                .map(test -> new GameTestInfo(test, Rotation.NONE, level, RetryOptions.noRetries()))
                .toList();
        GameTestRunner.Builder
                .fromInfo(gameTestInfos, level)
                .newStructureSpawner(new StructureGridSpawner(startPos, TESTS_PER_ROW, false))
                .build()
                .start();
{% endcase %}
{% if features.developer_world_gametest_lifecycle %}
        MultipleTestTracker tracker = new MultipleTestTracker(started);
        tracker.addListener(new CompletionSummary(server, tracker));
{% endif %}
        SFM.LOGGER.info(
                "SFM_DEVELOPER_WORLD_GAME_TESTS_STARTED total={} started={}",
                tests.size(),
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}
                started.size()
{% when '1.21', '1.21.1', '26.1.2' %}
                gameTestInfos.size()
{% endcase %}
        );
    }
{% if features.developer_world_gametest_lifecycle %}

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
{% endif %}
}
