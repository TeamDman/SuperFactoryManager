package ca.teamdman.sfm.gametest;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.event_bus.SFMSubscribeEvent;
import ca.teamdman.sfm.common.util.SFMDist;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppetHarness;
import ca.teamdman.sfm.properties.SFMProperties;
import com.mojang.brigadier.Command;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.gametest.framework.GameTestInstance;
import net.minecraft.gametest.framework.GameTestTicker;
import net.minecraft.gametest.framework.MultipleTestTracker;
import net.minecraft.gametest.framework.RetryOptions;
import net.minecraft.gametest.framework.StructureGridSpawner;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

public class SFMClientRunHarness {
    private static final String PUPPET_WORLD_ID = "sfm_client_puppet";
    private static final String PUPPET_WORLD_NAME = "SFM Client Puppet";

    private static boolean titleScreenHandled = false;
    private static boolean puppetWorldCreationStarted = false;
    private static boolean puppetTestsStarted = false;
    private static boolean puppetTestsCompleted = false;
    private static boolean pauseOnLostFocusCaptured = false;
    private static boolean pauseOnLostFocusBeforeAutomation = false;
    private static boolean keepOpen = false;
    private static int exitTicksRemaining = -1;
    private static int exitCountdownSecondAnnounced = -1;
    private static @Nullable MultipleTestTracker activeTracker = null;
    private static int activeRequiredCount = 0;
    private static int activeTotalCount = 0;

    @SFMSubscribeEvent(value = SFMDist.CLIENT)
    public static void onTitleScreenOpen(ScreenEvent.Opening event) {
        SFMProperties.ClientRunMode mode = SFMProperties.clientRunMode();
        if (mode == SFMProperties.ClientRunMode.NONE) {
            return;
        }

        if (preventPuppetPauseScreen(event, mode)) {
            return;
        }

        if (titleScreenHandled) {
            return;
        }

        if (event.getNewScreen() instanceof AccessibilityOnboardingScreen) {
            SFM.LOGGER.info("SFM_CLIENT_ONBOARDING_SKIPPED");
            Minecraft.getInstance().options.onboardingAccessibilityFinished();
            Minecraft.getInstance().options.save();
            event.setNewScreen(new TitleScreen());
            return;
        }

        if (mode == SFMProperties.ClientRunMode.GAME_PUPPET && event.getNewScreen() instanceof TitleScreen) {
            SFMGamePuppetHarness.onTitleScreenOpened();
            return;
        }

        if (titleScreenHandled || !(event.getNewScreen() instanceof TitleScreen)) {
            return;
        }

        titleScreenHandled = true;
        if (mode == SFMProperties.ClientRunMode.PUPPET) {
            SFM.LOGGER.info("SFM_CLIENT_PUPPET_TITLE_READY");
            startPuppetWorld();
        }
    }

    private static boolean preventPuppetPauseScreen(ScreenEvent.Opening event, SFMProperties.ClientRunMode mode) {
        if (mode == SFMProperties.ClientRunMode.PUPPET
            && isClientGameTestAutomationActive()
            && event.getNewScreen() instanceof PauseScreen) {
            SFM.LOGGER.info("SFM_CLIENT_PUPPET_PREVENTING_PAUSE_SCREEN");
            event.setNewScreen(null);
            return true;
        }
        if (mode == SFMProperties.ClientRunMode.GAME_PUPPET
            && SFMGamePuppetHarness.isAutomationActive()
            && event.getNewScreen() instanceof PauseScreen) {
            SFM.LOGGER.info("SFM_GAME_PUPPET_PREVENTING_PAUSE_SCREEN");
            event.setNewScreen(null);
            return true;
        }
        return false;
    }

    private static void continueFromClientMenu(SFMProperties.ClientRunMode mode) {
        titleScreenHandled = true;
        if (mode == SFMProperties.ClientRunMode.PUPPET) {
            SFM.LOGGER.info("SFM_CLIENT_PUPPET_TITLE_READY");
            startPuppetWorld();
        }
    }

    @SFMSubscribeEvent(value = SFMDist.CLIENT)
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        if (SFMProperties.clientRunMode() != SFMProperties.ClientRunMode.PUPPET) {
            return;
        }

        event.getDispatcher().register(
                Commands.literal("sfm")
                        .then(Commands.literal("keep_open")
                                      .requires(Commands.hasPermission(Commands.LEVEL_ALL))
                                      .executes(context -> keepOpen(context.getSource())))
        );
    }

    @SFMSubscribeEvent(value = SFMDist.CLIENT)
    public static void onClientTick(ClientTickEvent.Post event) {
        SFMProperties.ClientRunMode mode = SFMProperties.clientRunMode();
        if (mode == SFMProperties.ClientRunMode.GAME_PUPPET) {
            SFMGamePuppetHarness.onClientTick();
            return;
        }
        if (mode != SFMProperties.ClientRunMode.PUPPET) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (!titleScreenHandled && minecraft.screen instanceof TitleScreen) {
            continueFromClientMenu(mode);
        }

        if (isClientGameTestAutomationActive()) {
            keepPuppetRuntimeUnpaused(minecraft);
            dismissPuppetPauseScreen(minecraft);
        }
        IntegratedServer server = minecraft.getSingleplayerServer();
        if (puppetWorldCreationStarted && !puppetTestsStarted && server != null && server.isReady() && minecraft.player != null) {
            puppetTestsStarted = true;
            server.execute(() -> startPuppetTests(server));
        }

        if (activeTracker != null && !puppetTestsCompleted && activeTracker.isDone()) {
            puppetTestsCompleted = true;
            finishPuppetTests();
        }

        tickAutoExit();
    }

    private static void keepPuppetRuntimeUnpaused(Minecraft minecraft) {
        if (!pauseOnLostFocusCaptured) {
            pauseOnLostFocusCaptured = true;
            pauseOnLostFocusBeforeAutomation = minecraft.options.pauseOnLostFocus;
        }
        if (minecraft.options.pauseOnLostFocus) {
            SFM.LOGGER.info("SFM_CLIENT_PUPPET_DISABLING_PAUSE_ON_LOST_FOCUS");
            minecraft.options.pauseOnLostFocus = false;
        }
    }

    private static void restorePuppetRuntimeOptions() {
        if (!pauseOnLostFocusCaptured) {
            return;
        }
        Minecraft.getInstance().options.pauseOnLostFocus = pauseOnLostFocusBeforeAutomation;
        pauseOnLostFocusCaptured = false;
    }

    private static void dismissPuppetPauseScreen(Minecraft minecraft) {
        if (minecraft.screen instanceof PauseScreen) {
            SFM.LOGGER.info("SFM_CLIENT_PUPPET_DISMISSING_PAUSE_SCREEN");
            minecraft.setScreen(null);
        }
    }

    private static void startPuppetWorld() {
        if (puppetWorldCreationStarted) {
            return;
        }
        puppetWorldCreationStarted = true;

        Minecraft minecraft = Minecraft.getInstance();
        LevelSettings levelSettings = new LevelSettings(
                PUPPET_WORLD_NAME,
                GameType.CREATIVE,
                new LevelSettings.DifficultySettings(Difficulty.HARD, false, false),
                true,
                WorldDataConfiguration.DEFAULT
        );

        SFM.LOGGER.info("SFM_CLIENT_PUPPET_CREATING_WORLD id={}", PUPPET_WORLD_ID);
        minecraft.createWorldOpenFlows().createFreshLevel(
                PUPPET_WORLD_ID,
                levelSettings,
                new WorldOptions(0L, false, false),
                WorldPresets::createFlatWorldDimensions,
                minecraft.screen
        );
    }

    private static void startPuppetTests(MinecraftServer server) {
        ServerLevel level = server.overworld();
        configurePuppetWorld(server, level);

        List<Identifier> selectedTestIds = SFMGameTestDiscovery
                .gatherSelectedTests()
                .stream()
                .map(test -> Identifier.fromNamespaceAndPath(SFM.MOD_ID, test.testName()))
                .toList();
        List<Holder.Reference<GameTestInstance>> tests = server
                .registryAccess()
                .lookupOrThrow(Registries.TEST_INSTANCE)
                .listElements()
                .filter(test -> selectedTestIds.contains(test.key().identifier()))
                .toList();
        List<GameTestInfo> gameTestInfos = tests
                .stream()
                .map(test -> new GameTestInfo(test, Rotation.NONE, level, RetryOptions.noRetries()))
                .toList();
        activeTotalCount = gameTestInfos.size();
        activeRequiredCount = (int) gameTestInfos.stream().filter(GameTestInfo::isRequired).count();
        if (activeTotalCount == 0 || activeRequiredCount == 0) {
            SFM.LOGGER.error(
                    "SFM_CLIENT_PUPPET_TESTS_FAILED required_failed=0 optional_failed=0 required={} total={} reason=no-tests",
                    activeRequiredCount,
                    activeTotalCount
            );
            schedulePuppetExit("SFM client puppet found no required tests.");
            return;
        }

        BlockPos startPos = new BlockPos(0, level.dimensionType().minY() + 4, 0);
        GameTestTicker.SINGLETON.clear();
        activeTracker = new MultipleTestTracker(gameTestInfos);
        activeTracker.addFailureListener(test -> SFM.LOGGER.error(
                "SFM_CLIENT_PUPPET_TEST_FAILED required={} name={} error={}",
                test.isRequired(),
                test.id(),
                test.getError() == null ? "<unknown>" : test.getError().toString()
        ));
        GameTestRunner.Builder
                .fromInfo(gameTestInfos, level)
                .newStructureSpawner(new StructureGridSpawner(startPos, 8, false))
                .build()
                .start();
        SFM.LOGGER.info(
                "SFM_CLIENT_PUPPET_TESTS_STARTED required={} total={}",
                activeRequiredCount,
                activeTotalCount
        );
    }

    private static void finishPuppetTests() {
        int failedRequired = activeTracker.getFailedRequiredCount();
        int failedOptional = activeTracker.getFailedOptionalCount();
        int passedRequired = activeRequiredCount - failedRequired;
        if (failedRequired > 0) {
            SFM.LOGGER.error(
                    "SFM_CLIENT_PUPPET_TESTS_FAILED required_failed={} optional_failed={} required={} total={}",
                    failedRequired,
                    failedOptional,
                    activeRequiredCount,
                    activeTotalCount
            );
            schedulePuppetExit("SFM client puppet tests failed.");
            return;
        }

        if (failedOptional > 0) {
            SFM.LOGGER.warn(
                    "SFM_CLIENT_PUPPET_OPTIONAL_TESTS_FAILED optional_failed={} required={} total={}",
                    failedOptional,
                    activeRequiredCount,
                    activeTotalCount
            );
        }

        SFM.LOGGER.info(
                "SFM_CLIENT_PUPPET_TESTS_PASSED required={} total={}",
                passedRequired,
                activeTotalCount
        );
        schedulePuppetExit("SFM client puppet tests passed.");
    }

    private static void schedulePuppetExit(String resultMessage) {
        puppetTestsCompleted = true;
        restorePuppetRuntimeOptions();
        int keepOpenSeconds = keepOpenSeconds();
        if (keepOpenSeconds < 0) {
            keepOpen = true;
            exitTicksRemaining = -1;
            exitCountdownSecondAnnounced = -1;
            sendClientChat(resultMessage + " Client will remain open.");
            SFM.LOGGER.info("SFM_CLIENT_PUPPET_KEEP_OPEN");
            return;
        }
        exitTicksRemaining = keepOpenSeconds * 20;
        exitCountdownSecondAnnounced = -1;
        sendClientChat(resultMessage + " Run /sfm keep_open within "
                       + keepOpenSeconds
                       + " seconds to keep this client open.");
        SFM.LOGGER.info(
                "SFM_CLIENT_PUPPET_EXIT_PENDING seconds={} command=/sfm keep_open",
                keepOpenSeconds
        );
    }

    private static void tickAutoExit() {
        if (exitTicksRemaining < 0 || keepOpen) {
            return;
        }
        if (exitTicksRemaining > 0) {
            announceExitCountdown();
            exitTicksRemaining--;
            return;
        }
        sendClientChat("SFM client puppet closing.");
        SFM.LOGGER.info("SFM_CLIENT_PUPPET_EXITING");
        Minecraft.getInstance().stop();
    }

    private static void announceExitCountdown() {
        int secondsRemaining = (exitTicksRemaining + 19) / 20;
        if (secondsRemaining < 1 || secondsRemaining > 3 || secondsRemaining == exitCountdownSecondAnnounced) {
            return;
        }

        exitCountdownSecondAnnounced = secondsRemaining;
        sendClientChat("SFM client puppet closing in " + secondsRemaining + "...");
    }

    private static int keepOpen(CommandSourceStack source) {
        keepOpen = true;
        exitTicksRemaining = -1;
        exitCountdownSecondAnnounced = -1;
        SFM.LOGGER.info("SFM_CLIENT_PUPPET_KEEP_OPEN");
        source.sendSuccess(() -> Component.literal("SFM client puppet will remain open."), true);
        return Command.SINGLE_SUCCESS;
    }

    private static void sendClientChat(String message) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            SFM.LOGGER.info("SFM_CLIENT_PUPPET_CHAT {}", message);
            return;
        }

        minecraft.gui.getChat().addClientSystemMessage(Component.literal(message));
    }

    private static void configurePuppetWorld(
            MinecraftServer server,
            ServerLevel level
    ) {

        server.setDefaultGameType(GameType.CREATIVE);
        server.setDifficulty(Difficulty.HARD, false);
        server.setWeatherParameters(0, 0, false, false);
        server.getWorldData().overworldData().setDayTimeFraction(0.25F);
        server.getWorldData().overworldData().setDayTimePerTick(0.0F);

        GameRules rules = server.getGameRules();
        rules.set(GameRules.SPAWN_MOBS, false, server);
        rules.set(GameRules.SPAWN_MONSTERS, false, server);
        rules.set(GameRules.ADVANCE_WEATHER, false, server);
        rules.set(GameRules.ADVANCE_TIME, false, server);
    }

    private static int keepOpenSeconds() {
        return SFMProperties.clientRunKeepOpenSeconds(25);
    }

    private static boolean isClientGameTestAutomationActive() {
        return !puppetTestsCompleted;
    }
}
