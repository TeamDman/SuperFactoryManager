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
    private static boolean runtimeOptionsCaptured;
    private static boolean pauseOnLostFocusBeforeAutomation;
    private static boolean vsyncBeforeAutomation;
    private static int framerateLimitBeforeAutomation;
    private static List<SFMDiscoveredGamePuppet> selectedPuppets = List.of();
    private static List<PuppetExecution> selectedExecutions = new ArrayList<>();
    private static SFMGamePuppetViewportSelection viewportSelection;
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
                        "SFM_GAME_PUPPET_RETURNED_TO_TITLE puppet={} variant={} success={}",
                        activePuppet.definition.puppetName(),
                        activePuppet.viewportVariant.id(),
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
                viewportSelection = SFMGamePuppetViewportSelection.parse(SFMProperties.gamePuppetViewportSelection());
                List<PuppetExecution> executions = new ArrayList<>();
                for (SFMDiscoveredGamePuppet puppet : selectedPuppets) {
                    for (SFMGamePuppetViewportVariant variant : viewportSelection.resolve(
                            puppet.viewportProfile(),
                            Minecraft.getInstance().getWindow().getScreenWidth(),
                            Minecraft.getInstance().getWindow().getScreenHeight()
                    )) executions.add(new PuppetExecution(puppet, variant));
                }
                selectedExecutions = executions;
                SFM.LOGGER.info("SFM_GAME_PUPPET_TITLE_READY selected={} executions={} viewport_selection={}", selectedPuppets.size(), selectedExecutions.size(), viewportSelection.kind());
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
        try {
            if (!active.viewportPrepared) {
                if (!active.viewportController.tick(minecraft, active)) return;
                active.viewportPrepared = true;
            }
            if (!active.declared) {
                active.definition.declare(active.helper);
                active.helper.validate();
                active.declared = true;
                SFM.LOGGER.info("SFM_GAME_PUPPET_STARTED puppet={} variant={} location={}", active.definition.puppetName(), active.viewportVariant.id(), active.definition.location());
            }
        } catch (Throwable throwable) {
            failActivePuppet(active, throwable);
            returnToTitle(minecraft);
            return;
        }
        int timeoutTicks = active.definition.timeoutTicks();
        if (++active.totalActionTicks > timeoutTicks) {
            failActivePuppet(
                    active,
                    new IllegalStateException("Timed out after " + timeoutTicks + " client ticks")
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
        SFMGamePuppetRenderHarness.clear();
        if (nextPuppetIndex >= selectedExecutions.size()) {
            finishRun();
            return;
        }

        PuppetExecution execution = selectedExecutions.get(nextPuppetIndex++);
        SFMDiscoveredGamePuppet definition = execution.definition();
        SFMGamePuppetHelper helper = new SFMGamePuppetHelper();
        ActivePuppet active = new ActivePuppet(definition, helper, execution.variant());
        activePuppet = active;
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
        expandNumericVariantsAfterAutoProbe(minecraft, active);
        if (nextPuppetIndex < selectedExecutions.size()) {
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

    private static void expandNumericVariantsAfterAutoProbe(Minecraft minecraft, ActivePuppet active) {
        if (viewportSelection.kind() != SFMGamePuppetViewportSelection.Kind.DECLARED
            || active.definition.viewportProfile() == SFMGamePuppetViewportProfile.CURRENT
            || active.definition.viewportProfile() == SFMGamePuppetViewportProfile.FIXED_1280X720_AUTO
            || active.viewportVariant.guiScale() != 0) return;
        int maximumScale = SFMGamePuppetViewportController.maximumScale(minecraft);
        List<PuppetExecution> numeric = new ArrayList<>();
        for (int scale = 1; scale <= maximumScale; scale++) {
            numeric.add(new PuppetExecution(active.definition, new SFMGamePuppetViewportVariant(active.viewportVariant.width(), active.viewportVariant.height(), scale)));
        }
        selectedExecutions.addAll(nextPuppetIndex, numeric);
        SFM.LOGGER.info("SFM_GAME_PUPPET_VIEWPORT_EXPANDED puppet={} variant={} numeric_scales={} execution_total={}", active.definition.puppetName(), active.viewportVariant.id(), maximumScale, selectedExecutions.size());
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
        SFMGamePuppetRenderHarness.clear();
        completed = true;
        SFMGamePuppetViewportController.requestRestore(Minecraft.getInstance());
        restoreRuntimeOptions(Minecraft.getInstance());
        SFM.LOGGER.info(
                "SFM_GAME_PUPPET_COMPLETE failed={} total={}",
                failedPuppetCount,
                selectedExecutions.size()
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
        if (!SFMGamePuppetViewportController.tickRestore(Minecraft.getInstance())) {
            return;
        }
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
        SFMGamePuppetRenderHarness.clear();
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
        if (!runtimeOptionsCaptured) {
            runtimeOptionsCaptured = true;
            pauseOnLostFocusBeforeAutomation = minecraft.options.pauseOnLostFocus;
            vsyncBeforeAutomation = minecraft.options.enableVsync().get();
            framerateLimitBeforeAutomation = minecraft.options.framerateLimit().get();
            SFM.LOGGER.info(
                    "SFM_GAME_PUPPET_RUNTIME_OPTIONS_CAPTURED pause_on_lost_focus={} vsync={} max_fps={}",
                    pauseOnLostFocusBeforeAutomation,
                    vsyncBeforeAutomation,
                    framerateLimitBeforeAutomation
            );
        }
        if (minecraft.options.pauseOnLostFocus) {
            minecraft.options.pauseOnLostFocus = false;
        }
        if (minecraft.options.enableVsync().get()) {
            minecraft.options.enableVsync().set(false);
        }
        if (minecraft.options.framerateLimit().get() != 260) {
            minecraft.options.framerateLimit().set(260);
        }
    }

    private static void restoreRuntimeOptions(Minecraft minecraft) {
        if (!runtimeOptionsCaptured) {
            return;
        }
        minecraft.options.pauseOnLostFocus = pauseOnLostFocusBeforeAutomation;
        minecraft.options.enableVsync().set(vsyncBeforeAutomation);
        minecraft.options.framerateLimit().set(framerateLimitBeforeAutomation);
        runtimeOptionsCaptured = false;
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

    private record PuppetExecution(SFMDiscoveredGamePuppet definition, SFMGamePuppetViewportVariant variant) {
    }
}
