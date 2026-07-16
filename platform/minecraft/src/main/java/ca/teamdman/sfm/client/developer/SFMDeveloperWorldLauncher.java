package ca.teamdman.sfm.client.developer;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.event_bus.SFMEventBus;
import ca.teamdman.sfm.common.event_bus.SFMSubscribeEvent;
import ca.teamdman.sfm.common.util.SFMDist;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.DataPackConfig;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.levelgen.WorldGenSettings;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraftforge.event.TickEvent;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Creates persistent IDE developer worlds without any client-puppet automation behavior.
 */
public final class SFMDeveloperWorldLauncher {
    private static final String WORLD_ID_PREFIX = "sfm_dev_";
    private static final DateTimeFormatter WORLD_ID_TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private static PendingWorldCreation pendingWorldCreation;

    private SFMDeveloperWorldLauncher() {
    }

    public static void createDeveloperWorld(boolean runGameTests) {
        if (pendingWorldCreation != null) {
            SFM.LOGGER.warn("SFM developer world creation is already pending: {}", pendingWorldCreation.worldId());
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        String worldId = nextWorldId(minecraft);
        pendingWorldCreation = new PendingWorldCreation(worldId, runGameTests);

        RegistryAccess.Frozen registryAccess = RegistryAccess.BUILTIN.get();
        Registry<WorldPreset> presets = registryAccess.registryOrThrow(Registry.WORLD_PRESET_REGISTRY);
        WorldGenSettings worldGenSettings = presets
                .getOrCreateHolderOrThrow(WorldPresets.FLAT)
                .value()
                .createWorldGenSettings(0L, false, false);
        LevelSettings levelSettings = new LevelSettings(
                "SFM Dev: " + worldId,
                GameType.CREATIVE,
                false,
                Difficulty.HARD,
                true,
                createDeveloperGameRules(),
                DataPackConfig.DEFAULT
        );

        SFM.LOGGER.info("SFM_DEVELOPER_WORLD_CREATING id={} run_game_tests={}", worldId, runGameTests);
        try {
            minecraft.createWorldOpenFlows().createFreshLevel(
                    worldId,
                    levelSettings,
                    registryAccess,
                    worldGenSettings
            );
        } catch (RuntimeException exception) {
            pendingWorldCreation = null;
            throw exception;
        }
    }

    @SFMSubscribeEvent(value = SFMDist.CLIENT)
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || pendingWorldCreation == null) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        IntegratedServer server = minecraft.getSingleplayerServer();
        if (server == null || !server.isReady() || minecraft.player == null) {
            return;
        }

        PendingWorldCreation completedCreation = pendingWorldCreation;
        pendingWorldCreation = null;
        server.execute(() -> finishWorldCreation(server, completedCreation));
    }

    private static String nextWorldId(Minecraft minecraft) {
        Path savesDirectory = minecraft.gameDirectory.toPath().resolve("saves");
        String timestampId = WORLD_ID_PREFIX + WORLD_ID_TIMESTAMP_FORMAT.format(LocalDateTime.now());
        String worldId = timestampId;
        int duplicateIndex = 2;
        while (Files.exists(savesDirectory.resolve(worldId))) {
            worldId = timestampId + "-" + duplicateIndex++;
        }
        return worldId;
    }

    private static GameRules createDeveloperGameRules() {
        GameRules rules = new GameRules();
        rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
        rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, null);
        rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
        return rules;
    }

    private static void finishWorldCreation(IntegratedServer server, PendingWorldCreation completedCreation) {
        ServerLevel level = server.overworld();
        server.setDefaultGameType(GameType.CREATIVE);
        server.setDifficulty(Difficulty.HARD, false);
        level.setWeatherParameters(0, 0, false, false);
        level.setDayTime(6000L);
        GameRules rules = server.getGameRules();
        rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
        rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, server);
        rules.getRule(GameRules.RULE_DAYLIGHT).set(false, server);

        SFM.LOGGER.info(
                "SFM_DEVELOPER_WORLD_READY id={} run_game_tests={}",
                completedCreation.worldId(),
                completedCreation.runGameTests()
        );
        if (completedCreation.runGameTests()) {
            SFMEventBus.GAME_BUS.post(new SFMDeveloperWorldReadyEvent(server, level));
        }
    }

    private record PendingWorldCreation(String worldId, boolean runGameTests) {
    }
}
