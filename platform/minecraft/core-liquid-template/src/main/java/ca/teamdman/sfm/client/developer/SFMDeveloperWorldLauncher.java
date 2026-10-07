package ca.teamdman.sfm.client.developer;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.event_bus.SFMEventBus;
import ca.teamdman.sfm.common.event_bus.SFMSubscribeEvent;
import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
import ca.teamdman.sfm.common.util.SFMDist;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
{% case minecraft_version %}
{% when "1.19.2" %}
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
{% when "26.1.2" %}
{% else %}
import net.minecraft.core.registries.Registries;
{% endcase %}
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
{% case minecraft_version %}
{% when "1.19.2" %}
import net.minecraft.world.level.DataPackConfig;
import net.minecraft.world.level.GameRules;
{% when "26.1.2" %}
{% else %}
import net.minecraft.world.level.GameRules;
{% endcase %}
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
{% case minecraft_version %}
{% when "1.19.2" %}
import net.minecraft.world.level.levelgen.WorldGenSettings;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
{% else %}
import net.minecraft.world.level.WorldDataConfiguration;
{% case minecraft_version %}
{% when "26.1.2" %}
import net.minecraft.world.level.gamerules.GameRules;
{% endcase %}
import net.minecraft.world.level.levelgen.WorldOptions;
{% endcase %}
import net.minecraft.world.level.levelgen.presets.WorldPresets;
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1" %}
import net.minecraftforge.event.TickEvent;
{% when "1.20.2", "1.20.3", "1.20.4" %}
import net.neoforged.neoforge.event.TickEvent;
{% else %}
import net.neoforged.neoforge.client.event.ClientTickEvent;
{% endcase %}

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

    @MCVersionDependentBehaviour
    public static void createDeveloperWorld(boolean runGameTests) {
        if (pendingWorldCreation != null) {
            SFM.LOGGER.warn("SFM developer world creation is already pending: {}", pendingWorldCreation.worldId());
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        String worldId = nextWorldId(minecraft);
        pendingWorldCreation = new PendingWorldCreation(worldId, runGameTests);

{% case minecraft_version %}
{% when "1.19.2" %}
        // Forge mutates biome metadata while starting a server.  A fresh copy
        // is required here so a later developer world does not reuse biomes
        // that were already modified by the previous integrated server.
        RegistryAccess registryAccess = RegistryAccess.builtinCopy();
        Registry<WorldPreset> presets = registryAccess.registryOrThrow(Registry.WORLD_PRESET_REGISTRY);
        WorldGenSettings worldGenSettings = presets
                .getOrCreateHolderOrThrow(WorldPresets.FLAT)
                .value()
                .createWorldGenSettings(0L, false, false);
{% endcase %}
        LevelSettings levelSettings = new LevelSettings(
                "SFM Dev: " + worldId,
                GameType.CREATIVE,
{% case minecraft_version %}
{% when "26.1.2" %}
                new LevelSettings.DifficultySettings(Difficulty.HARD, false, false),
{% else %}
                false,
                Difficulty.HARD,
{% endcase %}
                true,
{% case minecraft_version %}
{% when "26.1.2" %}
{% else %}
                createDeveloperGameRules(),
{% endcase %}
{% case minecraft_version %}
{% when "1.19.2" %}
                DataPackConfig.DEFAULT
{% else %}
                WorldDataConfiguration.DEFAULT
{% endcase %}
        );
{% case minecraft_version %}
{% when "1.19.2" %}
{% else %}
        WorldOptions worldOptions = new WorldOptions(0L, false, false);
{% endcase %}

        SFM.LOGGER.info("SFM_DEVELOPER_WORLD_CREATING id={} run_game_tests={}", worldId, runGameTests);
        try {
            minecraft.createWorldOpenFlows().createFreshLevel(
                    worldId,
                    levelSettings,
{% case minecraft_version %}
{% when "1.19.2" %}
                    registryAccess,
                    worldGenSettings
{% when "26.1.2" %}
                    worldOptions,
                    WorldPresets::createFlatWorldDimensions,
                    minecraft.screen
{% else %}
                    worldOptions,
                    registryAccess -> registryAccess
                            .registryOrThrow(Registries.WORLD_PRESET)
                            .getHolderOrThrow(WorldPresets.FLAT)
                            .value()
{% case minecraft_version %}
{% when "1.19.4", "1.20", "1.20.1", "1.20.2" %}
                            .createWorldDimensions()
{% else %}
                            .createWorldDimensions(),
                    minecraft.screen
{% endcase %}
{% endcase %}
            );
        } catch (RuntimeException exception) {
            pendingWorldCreation = null;
            throw exception;
        }
    }

    @MCVersionDependentBehaviour
    @SFMSubscribeEvent(value = SFMDist.CLIENT)
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4" %}
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || pendingWorldCreation == null) {
{% else %}
    public static void onClientTick(ClientTickEvent.Post event) {
        if (pendingWorldCreation == null) {
{% endcase %}
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

{% case minecraft_version %}
{% when "26.1.2" %}
{% else %}
    @MCVersionDependentBehaviour
    private static GameRules createDeveloperGameRules() {
        GameRules rules = new GameRules();
        rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
        rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, null);
        rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
        return rules;
    }

{% endcase %}
    @MCVersionDependentBehaviour
    private static void finishWorldCreation(IntegratedServer server, PendingWorldCreation completedCreation) {
        ServerLevel level = server.overworld();
        server.setDefaultGameType(GameType.CREATIVE);
        server.setDifficulty(Difficulty.HARD, false);
{% case minecraft_version %}
{% when "26.1.2" %}
        server.setWeatherParameters(0, 0, false, false);
        server.getWorldData().overworldData().setDayTimeFraction(0.25F);
        server.getWorldData().overworldData().setDayTimePerTick(0.0F);
{% else %}
        level.setWeatherParameters(0, 0, false, false);
        level.setDayTime(6000L);
{% endcase %}
        GameRules rules = server.getGameRules();
{% case minecraft_version %}
{% when "26.1.2" %}
        rules.set(GameRules.SPAWN_MOBS, false, server);
        rules.set(GameRules.SPAWN_MONSTERS, false, server);
        rules.set(GameRules.ADVANCE_WEATHER, false, server);
        rules.set(GameRules.ADVANCE_TIME, false, server);
{% else %}
        rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
        rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, server);
        rules.getRule(GameRules.RULE_DAYLIGHT).set(false, server);
{% endcase %}

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
