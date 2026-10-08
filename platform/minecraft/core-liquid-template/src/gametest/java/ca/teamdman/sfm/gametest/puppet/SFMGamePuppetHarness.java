package ca.teamdman.sfm.gametest.puppet;

import ca.teamdman.sfm.SFM;
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
import ca.teamdman.sfm.gametest.SFMGameTestDefinition;
import ca.teamdman.sfm.gametest.SFMGameTestDiscovery;
{% when "26.1.2" %}
{% endcase %}
import ca.teamdman.sfm.properties.SFMProperties;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
{% when "26.1.2" %}
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
{% endcase %}
import net.minecraft.gametest.framework.GameTestInfo;
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
import net.minecraft.gametest.framework.GameTestRegistry;
{% when "26.1.2" %}
import net.minecraft.gametest.framework.GameTestInstance;
{% endcase %}
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.gametest.framework.GameTestTicker;
import net.minecraft.gametest.framework.MultipleTestTracker;
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4" %}
import net.minecraft.gametest.framework.TestFunction;
{% when "1.21", "1.21.1" %}
import net.minecraft.gametest.framework.RetryOptions;
import net.minecraft.gametest.framework.StructureGridSpawner;
import net.minecraft.gametest.framework.TestFunction;
{% when "26.1.2" %}
import net.minecraft.gametest.framework.RetryOptions;
import net.minecraft.gametest.framework.StructureGridSpawner;
import net.minecraft.resources.Identifier;
{% endcase %}
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
import net.minecraft.world.level.GameRules;
{% when "26.1.2" %}
{% endcase %}
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Rotation;
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
{% when "26.1.2" %}
import net.minecraft.world.level.gamerules.GameRules;
{% endcase %}
import java.util.ArrayList;
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4" %}
import java.util.Collection;
{% when "1.21", "1.21.1", "26.1.2" %}
{% endcase %}
import java.util.List;

/**
 * Client lifecycle for the declarative {@link SFMGamePuppet} system.
 * Every selected definition begins on the title screen and returns there before
 * the next one begins, while the same client process remains alive.
 */
public final class SFMGamePuppetHarness {
    public static final String WORLD_ID_PREFIX = "sfm_game_puppet_";
    public static final String WORLD_NAME_PREFIX = "SFM Game Puppet: ";
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
    public static final int ACTION_TIMEOUT_TICKS = 20 * 60;
{% endcase %}
    public static final int SCREENSHOT_TIMEOUT_TICKS = 20 * 20;
    public static final int CAPTION_HORIZONTAL_PADDING = 12;
    public static final int CAPTION_VERTICAL_PADDING = 10;

    private static boolean initialized;
    private static boolean awaitingTitleScreen;
    private static boolean completed;
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
    private static boolean completionReported;
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
{% endcase %}
    private static int nextPuppetIndex;
    private static int failedPuppetCount;
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
    private static long finalWorldHoldTicksRemaining = -1;
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
    private static int finalWorldHoldTicksRemaining = -1;
{% endcase %}
    private static int exitTicksRemaining = -1;
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
    private static boolean runtimeOptionsCaptured;
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
    private static boolean pauseOnLostFocusCaptured;
{% endcase %}
    private static boolean pauseOnLostFocusBeforeAutomation;
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
    private static boolean vsyncBeforeAutomation;
    private static int framerateLimitBeforeAutomation;
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
{% endcase %}
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
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
        int timeoutTicks = active.definition.timeoutTicks();
        if (++active.totalActionTicks > timeoutTicks) {
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
        if (++active.totalActionTicks > ACTION_TIMEOUT_TICKS) {
{% endcase %}
            failActivePuppet(
                    active,
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
                    new IllegalStateException("Timed out after " + timeoutTicks + " client ticks")
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
                    new IllegalStateException("Timed out after " + ACTION_TIMEOUT_TICKS + " client ticks")
{% endcase %}
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
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
        SFMGamePuppetRenderHarness.clear();
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
{% endcase %}
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
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1" %}
        minecraft.clearLevel(new TitleScreen());
{% when "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
        minecraft.disconnect(new TitleScreen());
{% when "26.1.2" %}
        minecraft.disconnect(new TitleScreen(), true);
{% endcase %}
    }

    private static void completeSuccessfulPuppet(Minecraft minecraft, ActivePuppet active) {
        expandNumericVariantsAfterAutoProbe(minecraft, active);
        if (nextPuppetIndex < selectedExecutions.size()) {
            returnToTitle(minecraft);
            return;
        }

        int keepOpenSeconds = SFMProperties.clientRunKeepOpenSeconds(0);
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
        // Completion describes assertions, not how long the user inspects the final scene.
        // Report it exactly once, before either kind of hold; ordinary shutdown while
        // holding must not turn a finished run into a missing-completion failure.
        reportCompletion();
        if (keepOpenSeconds != 0) {
            SFMGamePuppetViewportObservation viewport = active.viewportObservation;
            SFM.LOGGER.info(
                    "SFM_GAME_PUPPET_VIEWPORT_RETAINED keep_open_seconds={} variant={} actual_width={} actual_height={} framebuffer_width={} framebuffer_height={} requested_gui_scale={} effective_gui_scale={} logical_width={} logical_height={}",
                    keepOpenSeconds, active.viewportVariant.id(), viewport.windowWidth(), viewport.windowHeight(),
                    viewport.framebufferWidth(), viewport.framebufferHeight(), active.viewportVariant.requestedScaleName(),
                    viewport.effectiveGuiScale(), viewport.logicalWidth(), viewport.logicalHeight()
            );
        }
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
{% endcase %}
        if (keepOpenSeconds < 0) {
            activePuppet = null;
            completed = true;
            restoreRuntimeOptions(minecraft);
            SFM.LOGGER.info("SFM_GAME_PUPPET_KEEP_FINAL_WORLD_OPEN");
            return;
        }
        if (keepOpenSeconds > 0) {
            activePuppet = null;
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
            finalWorldHoldTicksRemaining = keepOpenSeconds * 20L;
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
            finalWorldHoldTicksRemaining = keepOpenSeconds * 20;
{% endcase %}
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
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
            || active.definition.viewportProfile() == SFMGamePuppetViewportProfile.FIXED_1280X720_AUTO
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
{% endcase %}
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
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
        SFMGamePuppetRenderHarness.clear();
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
{% endcase %}
        completed = true;
        SFMGamePuppetViewportController.requestRestore(Minecraft.getInstance());
        restoreRuntimeOptions(Minecraft.getInstance());
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
        reportCompletion();
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
        SFM.LOGGER.info(
                "SFM_GAME_PUPPET_COMPLETE failed={} total={}",
                failedPuppetCount,
                selectedExecutions.size()
        );
{% endcase %}
        int titleExitSeconds = SFMProperties.clientRunTitleExitSeconds(25);
        if (titleExitSeconds < 0) {
            SFM.LOGGER.info("SFM_GAME_PUPPET_KEEP_TITLE_OPEN");
            return;
        }
        exitTicksRemaining = titleExitSeconds * 20;
        SFM.LOGGER.info("SFM_GAME_PUPPET_EXIT_PENDING seconds={}", titleExitSeconds);
    }

{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
    private static void reportCompletion() {
        if (completionReported) return;
        completionReported = true;
        SFM.LOGGER.info("SFM_GAME_PUPPET_COMPLETE failed={} total={}",
                failedPuppetCount, selectedExecutions.size());
    }

{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
{% endcase %}
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
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
        SFMGamePuppetRenderHarness.clear();
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
{% endcase %}
        active.failureRecorded = true;
        active.success = false;
        failedPuppetCount++;
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
        try {
            active.helper.abortCurrentAction();
        } catch (Throwable cleanupFailure) {
            if (cleanupFailure != throwable) throwable.addSuppressed(cleanupFailure);
        }
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
{% endcase %}
        SFM.LOGGER.error(
                "SFM_GAME_PUPPET_FAILED puppet={} action={} error={}",
                active.definition.puppetName(),
                active.helper.currentActionDescription(),
                throwable.toString(),
                throwable
        );
    }

    private static void keepRuntimeUnpaused(Minecraft minecraft) {
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
        if (!runtimeOptionsCaptured) {
            runtimeOptionsCaptured = true;
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
        if (!pauseOnLostFocusCaptured) {
            pauseOnLostFocusCaptured = true;
{% endcase %}
            pauseOnLostFocusBeforeAutomation = minecraft.options.pauseOnLostFocus;
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
            vsyncBeforeAutomation = minecraft.options.enableVsync().get();
            framerateLimitBeforeAutomation = minecraft.options.framerateLimit().get();
            SFM.LOGGER.info(
                    "SFM_GAME_PUPPET_RUNTIME_OPTIONS_CAPTURED pause_on_lost_focus={} vsync={} max_fps={}",
                    pauseOnLostFocusBeforeAutomation,
                    vsyncBeforeAutomation,
                    framerateLimitBeforeAutomation
            );
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
{% endcase %}
        }
        if (minecraft.options.pauseOnLostFocus) {
            minecraft.options.pauseOnLostFocus = false;
        }
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
        if (minecraft.options.enableVsync().get()) {
            minecraft.options.enableVsync().set(false);
        }
        if (minecraft.options.framerateLimit().get() != 260) {
            minecraft.options.framerateLimit().set(260);
        }
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
{% endcase %}
    }

    private static void restoreRuntimeOptions(Minecraft minecraft) {
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
        if (!runtimeOptionsCaptured) {
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
        if (!pauseOnLostFocusCaptured) {
{% endcase %}
            return;
        }
        minecraft.options.pauseOnLostFocus = pauseOnLostFocusBeforeAutomation;
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
        minecraft.options.enableVsync().set(vsyncBeforeAutomation);
        minecraft.options.framerateLimit().set(framerateLimitBeforeAutomation);
        runtimeOptionsCaptured = false;
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
        pauseOnLostFocusCaptured = false;
{% endcase %}
    }

    public static boolean isAutomationActive() {
        return initialized && !completed;
    }

{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
    public static GameRules createWorldGameRules(MinecraftServer server) {
        GameRules rules = new GameRules();
        rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
        rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, server);
        rules.getRule(GameRules.RULE_DAYLIGHT).set(false, server);
        return rules;
    }

{% when "26.1.2" %}
{% endcase %}
    public static void configureWorld(MinecraftServer server, ServerLevel level) {
        server.setDefaultGameType(GameType.CREATIVE);
        server.setDifficulty(Difficulty.HARD, false);
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
        level.setWeatherParameters(0, 0, false, false);
        level.setDayTime(6000L);
{% when "26.1.2" %}
        server.setWeatherParameters(0, 0, false, false);
        server.getWorldData().overworldData().setDayTimeFraction(0.25F);
        server.getWorldData().overworldData().setDayTimePerTick(0.0F);
{% endcase %}
        GameRules rules = server.getGameRules();
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
        rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
        rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, server);
        rules.getRule(GameRules.RULE_DAYLIGHT).set(false, server);
{% when "26.1.2" %}
        rules.set(GameRules.SPAWN_MOBS, false, server);
        rules.set(GameRules.SPAWN_MONSTERS, false, server);
        rules.set(GameRules.ADVANCE_WEATHER, false, server);
        rules.set(GameRules.ADVANCE_TIME, false, server);
{% endcase %}
    }

    public static void startGameTest(ActivePuppet active, MinecraftServer server, String testName) {
        try {
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
            List<SFMGameTestDefinition> matches = SFMGameTestDiscovery.gatherTests()
                    .filter(test -> test.testName().equals(testName))
{% when "26.1.2" %}
            ServerLevel level = server.overworld();
            configureWorld(server, level);
            Identifier testId = Identifier.fromNamespaceAndPath(SFM.MOD_ID, testName);
            List<Holder.Reference<GameTestInstance>> matches = server
                    .registryAccess()
                    .lookupOrThrow(Registries.TEST_INSTANCE)
                    .listElements()
                    .filter(test -> test.key().identifier().equals(testId))
{% endcase %}
                    .toList();
            if (matches.size() != 1) {
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
                throw new IllegalStateException("Expected exactly one SFM GameTest named " + testName + ", found " + matches.size());
{% when "26.1.2" %}
                throw new IllegalStateException("Expected exactly one SFM GameTest named " + testId + ", found " + matches.size());
{% endcase %}
            }
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
            startGameTest(active, server, matches.get(0));
        } catch (Throwable throwable) {
            active.gameTestStartFailure = throwable;
        }
    }

    public static void startGameTest(
            ActivePuppet active,
            MinecraftServer server,
            SFMGameTestDefinition definition
    ) {
        try {
            String testName = definition.testName();
            ServerLevel level = server.overworld();
            configureWorld(server, level);
            TestFunction test = definition.intoTestFunction();
            BlockPos startPos = new BlockPos(0, level.getMinBuildHeight() + 4, 0);
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
            ServerLevel level = server.overworld();
            configureWorld(server, level);
            TestFunction test = matches.get(0).intoTestFunction();
            BlockPos startPos = new BlockPos(0, level.getMinBuildHeight() + 4, 0);
{% when "26.1.2" %}
            BlockPos startPos = new BlockPos(0, level.dimensionType().minY() + 4, 0);
{% endcase %}
            GameTestTicker.SINGLETON.clear();
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4" %}
            GameTestRunner.clearMarkers(level);
            GameTestRegistry.forgetFailedTests();
            Collection<GameTestInfo> started = GameTestRunner.runTests(
                    List.of(test),
                    startPos,
                    Rotation.NONE,
                    level,
                    GameTestTicker.SINGLETON,
                    1
{% when "1.21", "1.21.1" %}
            GameTestRunner.clearMarkers(level);
            GameTestRegistry.forgetFailedTests();
            List<GameTestInfo> gameTestInfos = List.of(
                    new GameTestInfo(test, Rotation.NONE, level, RetryOptions.noRetries())
{% when "26.1.2" %}
            List<GameTestInfo> gameTestInfos = List.of(
                    new GameTestInfo(matches.getFirst(), Rotation.NONE, level, RetryOptions.noRetries())
{% endcase %}
            );
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4" %}
            if (started.size() != 1) {
                throw new IllegalStateException("Expected one started GameTest, got " + started.size());
            }
            GameTestInfo info = new ArrayList<>(started).get(0);
            active.gameTestOrigin = info.getStructureBlockPos();
{% when "1.21", "1.21.1" %}
            GameTestRunner.Builder
                    .fromInfo(gameTestInfos, level)
                    .newStructureSpawner(new StructureGridSpawner(startPos, 1, false))
                    .build()
                    .start();
            GameTestInfo info = gameTestInfos.get(0);
            active.gameTestOrigin = info.getStructureBlockPos();
{% when "26.1.2" %}
            GameTestRunner.Builder
                    .fromInfo(gameTestInfos, level)
                    .newStructureSpawner(new StructureGridSpawner(startPos, 1, false))
                    .build()
                    .start();
            GameTestInfo info = gameTestInfos.get(0);
            active.gameTestOrigin = info.getTestOrigin();
{% endcase %}
            active.gameTestInfo = info;
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
            active.gameTestBounds = info.getStructureBounds();
            active.gameTestTracker = new MultipleTestTracker(started);
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4" %}
            active.gameTestTracker = new MultipleTestTracker(started);
{% when "1.21", "1.21.1", "26.1.2" %}
            active.gameTestTracker = new MultipleTestTracker(gameTestInfos);
{% endcase %}
            active.gameTestTracker.addFailureListener(failed -> SFM.LOGGER.error(
                    "SFM_GAME_PUPPET_GAME_TEST_FAILED puppet={} test={} error={}",
                    active.definition.puppetName(),
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
                    failed.getTestName(),
{% when "26.1.2" %}
                    failed.id(),
{% endcase %}
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
