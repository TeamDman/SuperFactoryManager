package ca.teamdman.sfm.gametest;

import ca.teamdman.sfm.SFM;
{% if features.command_history %}
import ca.teamdman.sfm.client.command.SFMCommandHistory;
import ca.teamdman.sfm.client.command.SFMCommandHistoryService;
{% endif %}
import ca.teamdman.sfm.common.event_bus.SFMSubscribeEvent;
{% case minecraft_version %}
{% when "1.19.2", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
{% when "1.19.4" %}
{% if features.client_automation_harness_annotations %}
import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
{% endif %}
{% endcase %}
import ca.teamdman.sfm.common.util.SFMDist;
{% if features.game_puppet_runtime %}
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppetHarness;
{% endif %}
{% if features.client_properties %}
import ca.teamdman.sfm.properties.SFMProperties;
{% endif %}
import com.mojang.brigadier.Command;
import net.minecraft.client.Minecraft;
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
{% when "26.1.2" %}
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
{% endcase %}
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
{% case minecraft_version %}
{% when "1.19.2" %}
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
{% when "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
import net.minecraft.core.registries.Registries;
{% when "26.1.2" %}
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
{% endcase %}
import net.minecraft.gametest.framework.GameTestInfo;
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4" %}
import net.minecraft.gametest.framework.GameTestRegistry;
{% when "1.21", "1.21.1", "26.1.2" %}
{% endcase %}
import net.minecraft.gametest.framework.GameTestRunner;
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
{% when "26.1.2" %}
import net.minecraft.gametest.framework.GameTestInstance;
{% endcase %}
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
{% endcase %}
import net.minecraft.network.chat.Component;
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
{% when "26.1.2" %}
import net.minecraft.resources.Identifier;
{% endcase %}
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
{% case minecraft_version %}
{% when "1.19.2" %}
import net.minecraft.world.level.DataPackConfig;
import net.minecraft.world.level.GameRules;
{% when "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
import net.minecraft.world.level.GameRules;
{% when "26.1.2" %}
{% endcase %}
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
{% case minecraft_version %}
{% when "1.19.2" %}
{% when "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
import net.minecraft.world.level.WorldDataConfiguration;
{% endcase %}
import net.minecraft.world.level.block.Rotation;
{% case minecraft_version %}
{% when "1.19.2" %}
import net.minecraft.world.level.levelgen.WorldGenSettings;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
{% when "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
import net.minecraft.world.level.levelgen.WorldOptions;
{% when "26.1.2" %}
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.WorldOptions;
{% endcase %}
import net.minecraft.world.level.levelgen.presets.WorldPresets;
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1" %}
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
{% when "1.20.2", "1.20.3", "1.20.4" %}
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.TickEvent;
{% when "1.21", "1.21.1", "26.1.2" %}
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
{% endcase %}
{% if features.client_automation_harness_annotations %}
import org.jetbrains.annotations.Nullable;
{% endif %}
import java.util.Collection;
import java.util.List;

public class SFMClientRunHarness {
{% if features.client_properties %}
{% else %}
    private static final String MODE_PROPERTY = "sfm.clientRun.mode";
    private static final String KEEP_OPEN_SECONDS_PROPERTY = "sfm.clientRun.keepOpenSeconds";
{% endif %}
    private static final String PUPPET_WORLD_ID = "sfm_client_puppet";
    private static final String PUPPET_WORLD_NAME = "SFM Client Puppet";

    private static boolean titleScreenHandled = false;
    private static boolean puppetWorldCreationStarted = false;
    private static boolean puppetTestsStarted = false;
    private static boolean puppetTestsCompleted = false;
{% if features.client_automation_pause_restore %}
    private static boolean pauseOnLostFocusCaptured = false;
    private static boolean pauseOnLostFocusBeforeAutomation = false;
{% endif %}
    private static boolean keepOpen = false;
    private static int exitTicksRemaining = -1;
    private static int exitCountdownSecondAnnounced = -1;
{% if features.client_automation_harness_annotations %}
    private static @Nullable MultipleTestTracker activeTracker = null;
{% else %}
    private static MultipleTestTracker activeTracker = null;
{% endif %}
    private static int activeRequiredCount = 0;
    private static int activeTotalCount = 0;

    @SFMSubscribeEvent(value = SFMDist.CLIENT)
    public static void onTitleScreenOpen(ScreenEvent.Opening event) {
{% if features.client_properties %}
        SFMProperties.ClientRunMode mode = SFMProperties.clientRunMode();
{% else %}
        Mode mode = mode();
{% endif %}
{% if features.client_properties %}
        if (mode == SFMProperties.ClientRunMode.NONE) {
{% else %}
        if (mode == Mode.NONE) {
{% endif %}
            return;
        }

        if (preventPuppetPauseScreen(event, mode)) {
            return;
        }

{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
{% if features.command_history %}
{% if features.game_puppet_runtime %}
{% if features.client_properties %}
        if (mode == SFMProperties.ClientRunMode.PUPPET
{% else %}
        if (mode == Mode.PUPPET
{% endif %}
{% if features.client_properties %}
                || mode == SFMProperties.ClientRunMode.GAME_PUPPET) {
{% else %}
                || mode == SFMProperties.ClientRunMode.GAME_PUPPET) {
{% endif %}
            // Automation must not read or mutate a developer's persistent
            // command history. Individual puppets can explicitly exercise
            // persistence through the service's injected store if needed.
            SFMCommandHistoryService.installForTests(SFMCommandHistory.inMemory());
        }

{% else %}
{% if features.client_properties %}
        if (mode == SFMProperties.ClientRunMode.PUPPET) {
{% else %}
        if (mode == Mode.PUPPET) {
{% endif %}
            // Automation must not read or mutate a developer's persistent
            // command history. Individual puppets can explicitly exercise
            // persistence through the service's injected store if needed.
            SFMCommandHistoryService.installForTests(SFMCommandHistory.inMemory());
        }

{% endif %}
{% endif %}
{% if features.game_puppet_runtime %}
        if (mode == SFMProperties.ClientRunMode.GAME_PUPPET && event.getNewScreen() instanceof TitleScreen) {
            SFMGamePuppetHarness.onTitleScreenOpened();
            return;
        }

{% endif %}
        if (titleScreenHandled || !(event.getNewScreen() instanceof TitleScreen)) {
{% when "26.1.2" %}
        if (titleScreenHandled) {
{% endcase %}
            return;
        }

{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
        titleScreenHandled = true;
{% if features.client_smoke_harness %}
{% else %}
{% if features.client_properties %}
        if (mode == SFMProperties.ClientRunMode.SMOKE) {
{% else %}
        if (mode == Mode.SMOKE) {
{% endif %}
            SFM.LOGGER.info("SFM_CLIENT_SMOKE_READY title_screen");
            Minecraft.getInstance().stop();
            return;
        }

{% endif %}
{% if features.client_properties %}
        if (mode == SFMProperties.ClientRunMode.PUPPET) {
{% else %}
        if (mode == Mode.PUPPET) {
{% endif %}
            SFM.LOGGER.info("SFM_CLIENT_PUPPET_TITLE_READY");
            startPuppetWorld();
{% when "26.1.2" %}
        if (event.getNewScreen() instanceof AccessibilityOnboardingScreen) {
            SFM.LOGGER.info("SFM_CLIENT_ONBOARDING_SKIPPED");
            Minecraft.getInstance().options.onboardingAccessibilityFinished();
            Minecraft.getInstance().options.save();
            event.setNewScreen(new TitleScreen());
            return;
{% endcase %}
        }
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
{% when "26.1.2" %}

{% if features.game_puppet_runtime %}
{% if features.client_properties %}
        if (mode == SFMProperties.ClientRunMode.GAME_PUPPET && event.getNewScreen() instanceof TitleScreen) {
{% else %}
        if (mode == SFMProperties.ClientRunMode.GAME_PUPPET && event.getNewScreen() instanceof TitleScreen) {
{% endif %}
            SFMGamePuppetHarness.onTitleScreenOpened();
            return;
        }

        if (titleScreenHandled || !(event.getNewScreen() instanceof TitleScreen)) {
            return;
        }

        titleScreenHandled = true;
{% if features.client_properties %}
        if (mode == SFMProperties.ClientRunMode.PUPPET) {
{% else %}
        if (mode == Mode.PUPPET) {
{% endif %}
            SFM.LOGGER.info("SFM_CLIENT_PUPPET_TITLE_READY");
            startPuppetWorld();
        }
{% else %}
        if (!(event.getNewScreen() instanceof TitleScreen)) {
            return;
        }

        continueFromClientMenu(mode);
{% endif %}
{% endcase %}
    }

{% if features.client_properties %}
    private static boolean preventPuppetPauseScreen(ScreenEvent.Opening event, SFMProperties.ClientRunMode mode) {
{% else %}
    private static boolean preventPuppetPauseScreen(ScreenEvent.Opening event, Mode mode) {
{% endif %}
{% if features.client_automation_pause_restore %}
{% if features.client_properties %}
        if (mode == SFMProperties.ClientRunMode.PUPPET
{% else %}
        if (mode == Mode.PUPPET
{% endif %}
            && isClientGameTestAutomationActive()
            && event.getNewScreen() instanceof PauseScreen) {
{% else %}
{% if features.client_properties %}
        if (mode == SFMProperties.ClientRunMode.PUPPET && event.getNewScreen() instanceof PauseScreen) {
{% else %}
        if (mode == Mode.PUPPET && event.getNewScreen() instanceof PauseScreen) {
{% endif %}
{% endif %}
            SFM.LOGGER.info("SFM_CLIENT_PUPPET_PREVENTING_PAUSE_SCREEN");
            event.setNewScreen(null);
            return true;
        }
{% if features.game_puppet_runtime %}
        if (mode == SFMProperties.ClientRunMode.GAME_PUPPET
            && SFMGamePuppetHarness.isAutomationActive()
            && event.getNewScreen() instanceof PauseScreen) {
            SFM.LOGGER.info("SFM_GAME_PUPPET_PREVENTING_PAUSE_SCREEN");
            event.setNewScreen(null);
            return true;
        }
{% endif %}
        return false;
    }

{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
{% when "26.1.2" %}
{% if features.client_properties %}
    private static void continueFromClientMenu(SFMProperties.ClientRunMode mode) {
{% else %}
    private static void continueFromClientMenu(Mode mode) {
{% endif %}
        titleScreenHandled = true;
{% if features.client_smoke_harness %}
{% else %}
{% if features.client_properties %}
{% if features.client_properties %}
        if (mode == SFMProperties.ClientRunMode.SMOKE) {
{% else %}
        if (mode == SFMProperties.ClientRunMode.SMOKE) {
{% endif %}
{% else %}
{% if features.client_properties %}
        if (mode == SFMProperties.ClientRunMode.SMOKE) {
{% else %}
        if (mode == Mode.SMOKE) {
{% endif %}
{% endif %}
            SFM.LOGGER.info("SFM_CLIENT_SMOKE_READY title_screen");
            Minecraft.getInstance().stop();
            return;
        }

{% endif %}
{% if features.client_properties %}
        if (mode == SFMProperties.ClientRunMode.PUPPET) {
{% else %}
        if (mode == Mode.PUPPET) {
{% endif %}
            SFM.LOGGER.info("SFM_CLIENT_PUPPET_TITLE_READY");
            startPuppetWorld();
        }
    }

{% endcase %}
    @SFMSubscribeEvent(value = SFMDist.CLIENT)
    public static void onRegisterCommands(RegisterCommandsEvent event) {
{% if features.client_properties %}
        if (SFMProperties.clientRunMode() != SFMProperties.ClientRunMode.PUPPET) {
{% else %}
        if (mode() != Mode.PUPPET) {
{% endif %}
            return;
        }

        event.getDispatcher().register(
                Commands.literal("sfm")
                        .then(Commands.literal("keep_open")
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
                                      .requires(source -> source.hasPermission(Commands.LEVEL_ALL))
{% when "26.1.2" %}
                                      .requires(Commands.hasPermission(Commands.LEVEL_ALL))
{% endcase %}
                                      .executes(context -> keepOpen(context.getSource())))
        );
    }

    @SFMSubscribeEvent(value = SFMDist.CLIENT)
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4" %}
    public static void onClientTick(TickEvent.ClientTickEvent event) {
{% if features.game_puppet_runtime %}
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

{% if features.client_properties %}
        SFMProperties.ClientRunMode mode = SFMProperties.clientRunMode();
{% else %}
        Mode mode = mode();
{% endif %}
{% if features.client_properties %}
        if (mode == SFMProperties.ClientRunMode.GAME_PUPPET) {
{% else %}
        if (mode == SFMProperties.ClientRunMode.GAME_PUPPET) {
{% endif %}
            SFMGamePuppetHarness.onClientTick();
            return;
        }
{% if features.client_properties %}
        if (mode != SFMProperties.ClientRunMode.PUPPET) {
{% else %}
        if (mode != Mode.PUPPET) {
{% endif %}
            return;
        }
{% else %}
{% if features.client_properties %}
        if (event.phase != TickEvent.Phase.END || SFMProperties.clientRunMode() != SFMProperties.ClientRunMode.PUPPET) {
{% else %}
        if (event.phase != TickEvent.Phase.END || mode() != Mode.PUPPET) {
{% endif %}
            return;
        }
{% endif %}
{% when "1.21", "1.21.1" %}
    public static void onClientTick(ClientTickEvent.Post event) {
{% if features.game_puppet_runtime %}
{% if features.client_properties %}
        SFMProperties.ClientRunMode mode = SFMProperties.clientRunMode();
{% else %}
        Mode mode = mode();
{% endif %}
{% if features.client_properties %}
        if (mode == SFMProperties.ClientRunMode.GAME_PUPPET) {
{% else %}
        if (mode == SFMProperties.ClientRunMode.GAME_PUPPET) {
{% endif %}
            SFMGamePuppetHarness.onClientTick();
            return;
        }
{% if features.client_properties %}
        if (mode != SFMProperties.ClientRunMode.PUPPET) {
{% else %}
        if (mode != Mode.PUPPET) {
{% endif %}
            return;
        }
{% else %}
{% if features.client_properties %}
        if (SFMProperties.clientRunMode() != SFMProperties.ClientRunMode.PUPPET) {
{% else %}
        if (mode() != Mode.PUPPET) {
{% endif %}
            return;
        }
{% endif %}
{% when "26.1.2" %}
    public static void onClientTick(ClientTickEvent.Post event) {
{% if features.game_puppet_runtime %}
{% if features.client_properties %}
        SFMProperties.ClientRunMode mode = SFMProperties.clientRunMode();
{% else %}
        Mode mode = mode();
{% endif %}
{% if features.client_properties %}
        if (mode == SFMProperties.ClientRunMode.GAME_PUPPET) {
{% else %}
        if (mode == SFMProperties.ClientRunMode.GAME_PUPPET) {
{% endif %}
            SFMGamePuppetHarness.onClientTick();
            return;
        }
{% if features.client_properties %}
        if (mode != SFMProperties.ClientRunMode.PUPPET) {
{% else %}
        if (mode != Mode.PUPPET) {
{% endif %}
            return;
        }
{% else %}
{% if features.client_properties %}
        SFMProperties.ClientRunMode mode = SFMProperties.clientRunMode();
{% else %}
        Mode mode = mode();
{% endif %}
{% if features.client_properties %}
        if (mode == SFMProperties.ClientRunMode.NONE) {
{% else %}
        if (mode == Mode.NONE) {
{% endif %}
            return;
        }
{% endif %}
{% endcase %}

        Minecraft minecraft = Minecraft.getInstance();
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
{% if features.client_automation_pause_restore %}
        if (isClientGameTestAutomationActive()) {
            keepPuppetRuntimeUnpaused(minecraft);
            dismissPuppetPauseScreen(minecraft);
        }
{% else %}
        keepPuppetRuntimeUnpaused(minecraft);
        dismissPuppetPauseScreen(minecraft);
{% endif %}
        IntegratedServer server = minecraft.getSingleplayerServer();
{% when "26.1.2" %}
        if (!titleScreenHandled && minecraft.screen instanceof TitleScreen) {
            continueFromClientMenu(mode);
        }

{% if features.game_puppet_runtime %}
{% else %}
{% if features.client_properties %}
        if (mode != SFMProperties.ClientRunMode.PUPPET) {
{% else %}
        if (mode != Mode.PUPPET) {
{% endif %}
            return;
        }

{% endif %}
{% if features.client_automation_pause_restore %}
        if (isClientGameTestAutomationActive()) {
            keepPuppetRuntimeUnpaused(minecraft);
            dismissPuppetPauseScreen(minecraft);
        }
        IntegratedServer server = minecraft.getSingleplayerServer();
{% else %}
        keepPuppetRuntimeUnpaused(minecraft);
        IntegratedServer server = minecraft.getSingleplayerServer();
        dismissPuppetPauseScreen(minecraft);
{% endif %}
{% endcase %}
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
{% if features.client_automation_pause_restore %}
        if (!pauseOnLostFocusCaptured) {
            pauseOnLostFocusCaptured = true;
            pauseOnLostFocusBeforeAutomation = minecraft.options.pauseOnLostFocus;
        }
{% endif %}
        if (minecraft.options.pauseOnLostFocus) {
            SFM.LOGGER.info("SFM_CLIENT_PUPPET_DISABLING_PAUSE_ON_LOST_FOCUS");
            minecraft.options.pauseOnLostFocus = false;
{% if features.client_automation_pause_restore %}
{% else %}
            minecraft.options.save();
{% endif %}
        }
    }
{% if features.client_automation_pause_restore %}

    private static void restorePuppetRuntimeOptions() {
        if (!pauseOnLostFocusCaptured) {
            return;
        }
        Minecraft.getInstance().options.pauseOnLostFocus = pauseOnLostFocusBeforeAutomation;
        pauseOnLostFocusCaptured = false;
    }
{% endif %}

    private static void dismissPuppetPauseScreen(Minecraft minecraft) {
        if (minecraft.screen instanceof PauseScreen) {
            SFM.LOGGER.info("SFM_CLIENT_PUPPET_DISMISSING_PAUSE_SCREEN");
            minecraft.setScreen(null);
        }
    }

{% case minecraft_version %}
{% when "1.19.2", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
{% when "1.19.4" %}
{% if features.client_automation_harness_annotations %}
    @MCVersionDependentBehaviour
{% endif %}
{% endcase %}
    private static void startPuppetWorld() {
        if (puppetWorldCreationStarted) {
            return;
        }
        puppetWorldCreationStarted = true;

        Minecraft minecraft = Minecraft.getInstance();
{% case minecraft_version %}
{% when "1.19.2" %}
{% if features.client_automation_registry_copy %}
        RegistryAccess registryAccess = RegistryAccess.builtinCopy();
{% else %}
        RegistryAccess.Frozen registryAccess = RegistryAccess.BUILTIN.get();
{% endif %}
        Registry<WorldPreset> presets = registryAccess.registryOrThrow(Registry.WORLD_PRESET_REGISTRY);
        WorldGenSettings worldGenSettings = presets
                .getOrCreateHolderOrThrow(WorldPresets.FLAT)
                .value()
                .createWorldGenSettings(0L, false, false);
{% when "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
{% when "26.1.2" %}
{% endcase %}

        LevelSettings levelSettings = new LevelSettings(
                PUPPET_WORLD_NAME,
                GameType.CREATIVE,
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
                false,
                Difficulty.HARD,
{% when "26.1.2" %}
                new LevelSettings.DifficultySettings(Difficulty.HARD, false, false),
{% endcase %}
                true,
{% case minecraft_version %}
{% when "1.19.2" %}
                createPuppetGameRules(null),
                DataPackConfig.DEFAULT
{% when "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
                createPuppetGameRules(null),
                WorldDataConfiguration.DEFAULT
{% when "26.1.2" %}
                WorldDataConfiguration.DEFAULT
{% endcase %}
        );
{% case minecraft_version %}
{% when "1.19.2", "26.1.2" %}
{% when "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
        WorldOptions worldOptions = new WorldOptions(0L, false, false);
{% endcase %}

        SFM.LOGGER.info("SFM_CLIENT_PUPPET_CREATING_WORLD id={}", PUPPET_WORLD_ID);
        minecraft.createWorldOpenFlows().createFreshLevel(
                PUPPET_WORLD_ID,
                levelSettings,
{% case minecraft_version %}
{% when "1.19.2" %}
                registryAccess,
                worldGenSettings
{% when "1.19.4", "1.20", "1.20.1", "1.20.2" %}
                worldOptions,
                registryAccess -> registryAccess
                        .registryOrThrow(Registries.WORLD_PRESET)
                        .getHolderOrThrow(WorldPresets.FLAT)
                        .value()
                        .createWorldDimensions()
{% when "1.20.3", "1.20.4", "1.21", "1.21.1" %}
                worldOptions,
                registryAccess -> registryAccess
                        .registryOrThrow(Registries.WORLD_PRESET)
                        .getHolderOrThrow(WorldPresets.FLAT)
                        .value()
                        .createWorldDimensions(),
                minecraft.screen
{% when "26.1.2" %}
                new WorldOptions(0L, false, false),
                WorldPresets::createFlatWorldDimensions,
                minecraft.screen
{% endcase %}
        );
    }

    private static void startPuppetTests(MinecraftServer server) {
        ServerLevel level = server.overworld();
        configurePuppetWorld(server, level);

{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
        List<TestFunction> tests = SFMGameTestDiscovery
{% when "26.1.2" %}
        List<Identifier> selectedTestIds = SFMGameTestDiscovery
{% endcase %}
                .gatherSelectedTests()
                .stream()
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
                .map(SFMGameTestDefinition::intoTestFunction)
{% when "26.1.2" %}
                .map(test -> Identifier.fromNamespaceAndPath(SFM.MOD_ID, test.testName()))
{% endcase %}
                .toList();
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4" %}
        activeTotalCount = tests.size();
        activeRequiredCount = (int) tests.stream().filter(TestFunction::isRequired).count();
{% when "1.21", "1.21.1" %}
        List<GameTestInfo> gameTestInfos = tests
                .stream()
                .map(test -> new GameTestInfo(test, Rotation.NONE, level, RetryOptions.noRetries()))
                .toList();
        activeTotalCount = gameTestInfos.size();
        activeRequiredCount = (int) tests.stream().filter(TestFunction::required).count();
{% when "26.1.2" %}
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
{% endcase %}
        if (activeTotalCount == 0 || activeRequiredCount == 0) {
            SFM.LOGGER.error(
                    "SFM_CLIENT_PUPPET_TESTS_FAILED required_failed=0 optional_failed=0 required={} total={} reason=no-tests",
                    activeRequiredCount,
                    activeTotalCount
            );
            schedulePuppetExit("SFM client puppet found no required tests.");
            return;
        }

{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
        BlockPos startPos = new BlockPos(0, level.getMinBuildHeight() + 4, 0);
{% when "26.1.2" %}
        BlockPos startPos = new BlockPos(0, level.dimensionType().minY() + 4, 0);
{% endcase %}
        GameTestTicker.SINGLETON.clear();
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4" %}
        GameTestRunner.clearMarkers(level);
        GameTestRegistry.forgetFailedTests();
        Collection<GameTestInfo> testsStarted = GameTestRunner.runTests(
                tests,
                startPos,
                Rotation.NONE,
                level,
                GameTestTicker.SINGLETON,
                8
        );
        activeTracker = new MultipleTestTracker(testsStarted);
{% when "1.21", "1.21.1" %}
        GameTestRunner.clearMarkers(level);
        activeTracker = new MultipleTestTracker(gameTestInfos);
{% when "26.1.2" %}
        activeTracker = new MultipleTestTracker(gameTestInfos);
{% endcase %}
        activeTracker.addFailureListener(test -> SFM.LOGGER.error(
                "SFM_CLIENT_PUPPET_TEST_FAILED required={} name={} error={}",
                test.isRequired(),
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
                test.getTestName(),
{% when "26.1.2" %}
                test.id(),
{% endcase %}
                test.getError() == null ? "<unknown>" : test.getError().toString()
        ));
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4" %}
{% when "1.21", "1.21.1", "26.1.2" %}
        GameTestRunner.Builder
                .fromInfo(gameTestInfos, level)
                .newStructureSpawner(new StructureGridSpawner(startPos, 8, false))
                .build()
                .start();
{% endcase %}
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
{% if features.client_automation_pause_restore %}
        puppetTestsCompleted = true;
        restorePuppetRuntimeOptions();
{% endif %}
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
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
        source.sendSuccess(Component.literal("SFM client puppet will remain open."), true);
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
        source.sendSuccess(() -> Component.literal("SFM client puppet will remain open."), true);
{% endcase %}
        return Command.SINGLE_SUCCESS;
    }

    private static void sendClientChat(String message) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            SFM.LOGGER.info("SFM_CLIENT_PUPPET_CHAT {}", message);
            return;
        }

{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
        minecraft.player.displayClientMessage(Component.literal(message), false);
{% when "26.1.2" %}
        minecraft.gui.getChat().addClientSystemMessage(Component.literal(message));
{% endcase %}
    }

    private static void configurePuppetWorld(
            MinecraftServer server,
            ServerLevel level
    ) {

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

{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
    private static GameRules createPuppetGameRules(MinecraftServer server) {
        GameRules rules = new GameRules();
        rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
        rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, server);
        rules.getRule(GameRules.RULE_DAYLIGHT).set(false, server);
        return rules;
    }

{% when "26.1.2" %}
{% endcase %}
    private static int keepOpenSeconds() {
{% if features.client_properties %}
{% if features.client_automation_keep_open_default %}
        return SFMProperties.clientRunKeepOpenSeconds(25);
{% else %}
        return SFMProperties.clientRunKeepOpenSeconds(10);
{% endif %}
{% else %}
{% if features.client_automation_keep_open_default %}
        return Integer.getInteger(KEEP_OPEN_SECONDS_PROPERTY, 25);
{% else %}
        return Integer.getInteger(KEEP_OPEN_SECONDS_PROPERTY, 10);
{% endif %}
{% endif %}
    }

{% if features.client_automation_pause_restore %}
    private static boolean isClientGameTestAutomationActive() {
        return !puppetTestsCompleted;
    }
{% endif %}
{% if features.client_properties %}
{% else %}
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
{% endif %}
}
