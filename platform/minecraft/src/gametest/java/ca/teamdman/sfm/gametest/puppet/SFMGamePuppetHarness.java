package ca.teamdman.sfm.gametest.puppet;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.gametest.SFMGameTestDefinition;
import ca.teamdman.sfm.gametest.SFMGameTestDiscovery;
import ca.teamdman.sfm.properties.SFMProperties;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestRegistry;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.gametest.framework.GameTestTicker;
import net.minecraft.gametest.framework.MultipleTestTracker;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Rotation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Client lifecycle for the declarative {@link SFMGamePuppet} system.
 * Every selected definition begins on the title screen and returns there before
 * the next one begins, while the same client process remains alive.
 */
public final class SFMGamePuppetHarness {
    public static final String WORLD_ID_PREFIX = "sfm_game_puppet_";
    public static final String WORLD_NAME_PREFIX = "SFM Game Puppet: ";
    public static final int ACTION_TIMEOUT_TICKS = 20 * 60;
    public static final int SCREENSHOT_TIMEOUT_TICKS = 20 * 20;
    public static final int CAPTION_HORIZONTAL_PADDING = 12;
    public static final int CAPTION_VERTICAL_PADDING = 10;

    private static boolean initialized;
    private static boolean awaitingTitleScreen;
    private static boolean completed;
    private static int nextPuppetIndex;
    private static int failedPuppetCount;
    private static int finalWorldHoldTicksRemaining = -1;
    private static int exitTicksRemaining = -1;
    private static boolean pauseOnLostFocusCaptured;
    private static boolean pauseOnLostFocusBeforeAutomation;
    private static List<SFMDiscoveredGamePuppet> selectedPuppets = List.of();
    private static ActivePuppet activePuppet;

    private SFMGamePuppetHarness() {
    }

    public static void onTitleScreenOpened() {
        if (completed) {
            return;
        }

        if (awaitingTitleScreen) {
            if (activePuppet != null) {
                SFM.LOGGER.info(
                        "SFM_GAME_PUPPET_RETURNED_TO_TITLE puppet={} success={}",
                        activePuppet.definition.puppetName(),
                        activePuppet.success
                );
            }
            activePuppet = null;
            awaitingTitleScreen = false;
        }

        try {
            if (!initialized) {
                initialized = true;
                selectedPuppets = List.copyOf(SFMGamePuppetDiscovery.gatherSelectedPuppets());
                SFM.LOGGER.info("SFM_GAME_PUPPET_TITLE_READY selected={}", selectedPuppets.size());
            }
            startNextPuppet();
        } catch (Throwable throwable) {
            failBeforeWorldCreation("<discovery>", "discover selected puppets", throwable);
            finishRun();
        }
    }

    public static void onClientTick() {
        Minecraft minecraft = Minecraft.getInstance();
        if (!completed) {
            keepRuntimeUnpaused(minecraft);
        }

        if (completed) {
            tickAutoExit();
            return;
        }
        if (finalWorldHoldTicksRemaining >= 0) {
            tickFinalWorldHold(minecraft);
            return;
        }
        if (awaitingTitleScreen || activePuppet == null) {
            return;
        }

        ActivePuppet active = activePuppet;
        if (++active.totalActionTicks > ACTION_TIMEOUT_TICKS) {
            failActivePuppet(
                    active,
                    new IllegalStateException("Timed out after " + ACTION_TIMEOUT_TICKS + " client ticks")
            );
            returnToTitle(minecraft);
            return;
        }

        try {
            if (active.helper.tick(new SFMGamePuppetMinecraftRuntime(active, minecraft))) {
                active.success = true;
                SFM.LOGGER.info("SFM_GAME_PUPPET_SUCCEEDED puppet={}", active.definition.puppetName());
                completeSuccessfulPuppet(minecraft, active);
            }
        } catch (Throwable throwable) {
            failActivePuppet(active, throwable);
            returnToTitle(minecraft);
        }
    }

    private static void startNextPuppet() {
        if (activePuppet != null || awaitingTitleScreen || completed) {
            return;
        }
        if (nextPuppetIndex >= selectedPuppets.size()) {
            finishRun();
            return;
        }

        SFMDiscoveredGamePuppet definition = selectedPuppets.get(nextPuppetIndex++);
        SFMGamePuppetHelper helper = new SFMGamePuppetHelper();
        ActivePuppet active = new ActivePuppet(definition, helper);
        activePuppet = active;
        try {
            definition.declare(helper);
            helper.validate();
            SFM.LOGGER.info(
                    "SFM_GAME_PUPPET_STARTED puppet={} location={}",
                    definition.puppetName(),
                    definition.location()
            );
        } catch (Throwable throwable) {
            failActivePuppet(active, throwable);
            activePuppet = null;
            startNextPuppet();
        }
    }

    private static void returnToTitle(Minecraft minecraft) {
        if (awaitingTitleScreen) {
            return;
        }
        awaitingTitleScreen = true;
        if (minecraft.level == null) {
            minecraft.setScreen(new TitleScreen());
            return;
        }
        IntegratedServer server = minecraft.getSingleplayerServer();
        if (server != null) {
            server.halt(true);
        }
        minecraft.clearLevel(new TitleScreen());
    }

    private static void completeSuccessfulPuppet(Minecraft minecraft, ActivePuppet active) {
        if (nextPuppetIndex < selectedPuppets.size()) {
            returnToTitle(minecraft);
            return;
        }

        int keepOpenSeconds = SFMProperties.clientRunKeepOpenSeconds(0);
        if (keepOpenSeconds < 0) {
            activePuppet = null;
            completed = true;
            restoreRuntimeOptions(minecraft);
            SFM.LOGGER.info("SFM_GAME_PUPPET_KEEP_FINAL_WORLD_OPEN");
            return;
        }
        if (keepOpenSeconds > 0) {
            activePuppet = null;
            finalWorldHoldTicksRemaining = keepOpenSeconds * 20;
            SFM.LOGGER.info("SFM_GAME_PUPPET_FINAL_WORLD_HOLD_PENDING seconds={}", keepOpenSeconds);
            return;
        }
        // A final non-interactive run does not need to rebuild the title screen
        // solely to wait for process exit.  Retaining the active world until
        // Minecraft shuts down also avoids invoking unrelated title-screen
        // listeners after their client configuration has already been unloaded.
        activePuppet = null;
        finishRun();
    }

    private static void tickFinalWorldHold(Minecraft minecraft) {
        if (finalWorldHoldTicksRemaining-- > 0) {
            return;
        }
        finalWorldHoldTicksRemaining = -1;
        SFM.LOGGER.info("SFM_GAME_PUPPET_FINAL_WORLD_HOLD_COMPLETE");
        returnToTitle(minecraft);
    }

    private static void finishRun() {
        if (completed) {
            return;
        }
        completed = true;
        restoreRuntimeOptions(Minecraft.getInstance());
        SFM.LOGGER.info(
                "SFM_GAME_PUPPET_COMPLETE failed={} total={}",
                failedPuppetCount,
                selectedPuppets.size()
        );
        int titleExitSeconds = SFMProperties.clientRunTitleExitSeconds(25);
        if (titleExitSeconds < 0) {
            SFM.LOGGER.info("SFM_GAME_PUPPET_KEEP_TITLE_OPEN");
            return;
        }
        exitTicksRemaining = titleExitSeconds * 20;
        SFM.LOGGER.info("SFM_GAME_PUPPET_EXIT_PENDING seconds={}", titleExitSeconds);
    }

    private static void tickAutoExit() {
        if (exitTicksRemaining < 0) {
            return;
        }
        if (exitTicksRemaining-- > 0) {
            return;
        }
        SFM.LOGGER.info("SFM_GAME_PUPPET_EXITING");
        Minecraft.getInstance().stop();
    }

    @SuppressWarnings("SameParameterValue")
    private static void failBeforeWorldCreation(String puppetName, String action, Throwable throwable) {
        failedPuppetCount++;
        SFM.LOGGER.error(
                "SFM_GAME_PUPPET_FAILED puppet={} action={} error={}",
                puppetName,
                action,
                throwable.toString(),
                throwable
        );
    }

    private static void failActivePuppet(ActivePuppet active, Throwable throwable) {
        if (active.failureRecorded) {
            return;
        }
        active.failureRecorded = true;
        active.success = false;
        failedPuppetCount++;
        SFM.LOGGER.error(
                "SFM_GAME_PUPPET_FAILED puppet={} action={} error={}",
                active.definition.puppetName(),
                active.helper.currentActionDescription(),
                throwable.toString(),
                throwable
        );
    }

    private static void keepRuntimeUnpaused(Minecraft minecraft) {
        if (!pauseOnLostFocusCaptured) {
            pauseOnLostFocusCaptured = true;
            pauseOnLostFocusBeforeAutomation = minecraft.options.pauseOnLostFocus;
        }
        if (minecraft.options.pauseOnLostFocus) {
            minecraft.options.pauseOnLostFocus = false;
        }
    }

    private static void restoreRuntimeOptions(Minecraft minecraft) {
        if (!pauseOnLostFocusCaptured) {
            return;
        }
        minecraft.options.pauseOnLostFocus = pauseOnLostFocusBeforeAutomation;
        pauseOnLostFocusCaptured = false;
    }

    public static boolean isAutomationActive() {
        return initialized && !completed;
    }

    public static GameRules createWorldGameRules(MinecraftServer server) {
        GameRules rules = new GameRules();
        rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
        rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, server);
        rules.getRule(GameRules.RULE_DAYLIGHT).set(false, server);
        return rules;
    }

    public static void configureWorld(MinecraftServer server, ServerLevel level) {
        server.setDefaultGameType(GameType.CREATIVE);
        server.setDifficulty(Difficulty.HARD, false);
        level.setWeatherParameters(0, 0, false, false);
        level.setDayTime(6000L);
        GameRules rules = server.getGameRules();
        rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
        rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, server);
        rules.getRule(GameRules.RULE_DAYLIGHT).set(false, server);
    }

    public static void startGameTest(ActivePuppet active, MinecraftServer server, String testName) {
        try {
            List<SFMGameTestDefinition> matches = SFMGameTestDiscovery.gatherTests()
                    .filter(test -> test.testName().equals(testName))
                    .toList();
            if (matches.size() != 1) {
                throw new IllegalStateException("Expected exactly one SFM GameTest named " + testName + ", found " + matches.size());
            }
            ServerLevel level = server.overworld();
            configureWorld(server, level);
            TestFunction test = matches.get(0).intoTestFunction();
            BlockPos startPos = new BlockPos(0, level.getMinBuildHeight() + 4, 0);
            GameTestTicker.SINGLETON.clear();
            GameTestRunner.clearMarkers(level);
            GameTestRegistry.forgetFailedTests();
            Collection<GameTestInfo> started = GameTestRunner.runTests(
                    List.of(test),
                    startPos,
                    Rotation.NONE,
                    level,
                    GameTestTicker.SINGLETON,
                    1
            );
            if (started.size() != 1) {
                throw new IllegalStateException("Expected one started GameTest, got " + started.size());
            }
            GameTestInfo info = new ArrayList<>(started).get(0);
            active.gameTestOrigin = info.getStructureBlockPos();
            active.gameTestInfo = info;
            active.gameTestTracker = new MultipleTestTracker(started);
            active.gameTestTracker.addFailureListener(failed -> SFM.LOGGER.error(
                    "SFM_GAME_PUPPET_GAME_TEST_FAILED puppet={} test={} error={}",
                    active.definition.puppetName(),
                    failed.getTestName(),
                    failed.getError() == null ? "<unknown>" : failed.getError().toString()
            ));
            SFM.LOGGER.info(
                    "SFM_GAME_PUPPET_GAME_TEST_STARTED puppet={} test={} origin={}",
                    active.definition.puppetName(),
                    testName,
                    active.gameTestOrigin
            );
        } catch (Throwable throwable) {
            active.gameTestStartFailure = throwable;
        }
    }
}
