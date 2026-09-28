package ca.teamdman.sfm.gametest;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.event_bus.SFMSubscribeEvent;
import ca.teamdman.sfm.common.util.SFMDist;
import com.mojang.brigadier.Command;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.gametest.framework.GameTestTicker;
import net.minecraft.gametest.framework.MultipleTestTracker;
import net.minecraft.gametest.framework.RetryOptions;
import net.minecraft.gametest.framework.StructureGridSpawner;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.Collection;
import java.util.List;

public class SFMClientRunHarness {
    private static final String MODE_PROPERTY = "sfm.clientRun.mode";
    private static final String KEEP_OPEN_SECONDS_PROPERTY = "sfm.clientRun.keepOpenSeconds";
    private static final String PUPPET_WORLD_ID = "sfm_client_puppet";
    private static final String PUPPET_WORLD_NAME = "SFM Client Puppet";

    private static boolean titleScreenHandled = false;
    private static boolean puppetWorldCreationStarted = false;
    private static boolean puppetTestsStarted = false;
    private static boolean puppetTestsCompleted = false;
    private static boolean keepOpen = false;
    private static int exitTicksRemaining = -1;
    private static int exitCountdownSecondAnnounced = -1;
    private static MultipleTestTracker activeTracker = null;
    private static int activeRequiredCount = 0;
    private static int activeTotalCount = 0;

    @SFMSubscribeEvent(value = SFMDist.CLIENT)
    public static void onTitleScreenOpen(ScreenEvent.Opening event) {
        Mode mode = mode();
        if (mode == Mode.NONE) {
            return;
        }

        if (preventPuppetPauseScreen(event, mode)) {
            return;
        }

        if (titleScreenHandled || !(event.getNewScreen() instanceof TitleScreen)) {
            return;
        }

        titleScreenHandled = true;
        if (mode == Mode.SMOKE) {
            SFM.LOGGER.info("SFM_CLIENT_SMOKE_READY title_screen");
            Minecraft.getInstance().stop();
            return;
        }

        if (mode == Mode.PUPPET) {
            SFM.LOGGER.info("SFM_CLIENT_PUPPET_TITLE_READY");
            startPuppetWorld();
        }
    }

    private static boolean preventPuppetPauseScreen(ScreenEvent.Opening event, Mode mode) {
        if (mode == Mode.PUPPET && event.getNewScreen() instanceof PauseScreen) {
            SFM.LOGGER.info("SFM_CLIENT_PUPPET_PREVENTING_PAUSE_SCREEN");
            event.setNewScreen(null);
            return true;
        }
        return false;
    }

    @SFMSubscribeEvent(value = SFMDist.CLIENT)
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        if (mode() != Mode.PUPPET) {
            return;
        }

        event.getDispatcher().register(
                Commands.literal("sfm")
                        .then(Commands.literal("keep_open")
                                      .requires(source -> source.hasPermission(Commands.LEVEL_ALL))
                                      .executes(context -> keepOpen(context.getSource())))
        );
    }

    @SFMSubscribeEvent(value = SFMDist.CLIENT)
    public static void onClientTick(ClientTickEvent.Post event) {
        if (mode() != Mode.PUPPET) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        keepPuppetRuntimeUnpaused(minecraft);
        dismissPuppetPauseScreen(minecraft);
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
        if (minecraft.options.pauseOnLostFocus) {
            SFM.LOGGER.info("SFM_CLIENT_PUPPET_DISABLING_PAUSE_ON_LOST_FOCUS");
            minecraft.options.pauseOnLostFocus = false;
            minecraft.options.save();
        }
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
                false,
                Difficulty.HARD,
                true,
                createPuppetGameRules(null),
                WorldDataConfiguration.DEFAULT
        );
        WorldOptions worldOptions = new WorldOptions(0L, false, false);

        SFM.LOGGER.info("SFM_CLIENT_PUPPET_CREATING_WORLD id={}", PUPPET_WORLD_ID);
        minecraft.createWorldOpenFlows().createFreshLevel(
                PUPPET_WORLD_ID,
                levelSettings,
                worldOptions,
                registryAccess -> registryAccess
                        .registryOrThrow(Registries.WORLD_PRESET)
                        .getHolderOrThrow(WorldPresets.FLAT)
                        .value()
                        .createWorldDimensions(),
                minecraft.screen
        );
    }

    private static void startPuppetTests(MinecraftServer server) {
        ServerLevel level = server.overworld();
        configurePuppetWorld(server, level);

        List<TestFunction> tests = SFMGameTestDiscovery
                .gatherSelectedTests()
                .stream()
                .map(SFMGameTestDefinition::intoTestFunction)
                .toList();
        List<GameTestInfo> gameTestInfos = tests
                .stream()
                .map(test -> new GameTestInfo(test, Rotation.NONE, level, RetryOptions.noRetries()))
                .toList();
        activeTotalCount = gameTestInfos.size();
        activeRequiredCount = (int) tests.stream().filter(TestFunction::required).count();
        if (activeTotalCount == 0 || activeRequiredCount == 0) {
            SFM.LOGGER.error(
                    "SFM_CLIENT_PUPPET_TESTS_FAILED required_failed=0 optional_failed=0 required={} total={} reason=no-tests",
                    activeRequiredCount,
                    activeTotalCount
            );
            schedulePuppetExit("SFM client puppet found no required tests.");
            return;
        }

        BlockPos startPos = new BlockPos(0, level.getMinBuildHeight() + 4, 0);
        GameTestTicker.SINGLETON.clear();
        GameTestRunner.clearMarkers(level);
        activeTracker = new MultipleTestTracker(gameTestInfos);
        activeTracker.addFailureListener(test -> SFM.LOGGER.error(
                "SFM_CLIENT_PUPPET_TEST_FAILED required={} name={} error={}",
                test.isRequired(),
                test.getTestName(),
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

        minecraft.player.displayClientMessage(Component.literal(message), false);
    }

    private static void configurePuppetWorld(
            MinecraftServer server,
            ServerLevel level
    ) {

        server.setDefaultGameType(GameType.CREATIVE);
        server.setDifficulty(Difficulty.HARD, false);
        level.setWeatherParameters(0, 0, false, false);
        level.setDayTime(6000L);

        GameRules rules = server.getGameRules();
        rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
        rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, server);
        rules.getRule(GameRules.RULE_DAYLIGHT).set(false, server);
    }

    private static GameRules createPuppetGameRules(MinecraftServer server) {
        GameRules rules = new GameRules();
        rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
        rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, server);
        rules.getRule(GameRules.RULE_DAYLIGHT).set(false, server);
        return rules;
    }

    private static int keepOpenSeconds() {
        return Integer.getInteger(KEEP_OPEN_SECONDS_PROPERTY, 10);
    }

    private static Mode mode() {
        return switch (System.getProperty(MODE_PROPERTY, "")) {
            case "smoke" -> Mode.SMOKE;
            case "puppet" -> Mode.PUPPET;
            default -> Mode.NONE;
        };
    }

    private enum Mode {
        NONE,
        SMOKE,
        PUPPET
    }
}
