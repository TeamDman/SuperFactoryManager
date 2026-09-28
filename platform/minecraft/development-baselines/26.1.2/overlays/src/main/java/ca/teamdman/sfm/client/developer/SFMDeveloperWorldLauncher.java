package ca.teamdman.sfm.client.developer;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.event_bus.SFMEventBus;
import ca.teamdman.sfm.common.event_bus.SFMSubscribeEvent;
import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
import ca.teamdman.sfm.common.util.SFMDist;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.neoforge.client.event.ClientTickEvent;

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

        LevelSettings levelSettings = new LevelSettings(
                "SFM Dev: " + worldId,
                GameType.CREATIVE,
                new LevelSettings.DifficultySettings(Difficulty.HARD, false, false),
                true,
                WorldDataConfiguration.DEFAULT
        );
        WorldOptions worldOptions = new WorldOptions(0L, false, false);

        SFM.LOGGER.info("SFM_DEVELOPER_WORLD_CREATING id={} run_game_tests={}", worldId, runGameTests);
        try {
            minecraft.createWorldOpenFlows().createFreshLevel(
                    worldId,
                    levelSettings,
                    worldOptions,
                    WorldPresets::createFlatWorldDimensions,
                    minecraft.screen
            );
        } catch (RuntimeException exception) {
            pendingWorldCreation = null;
            throw exception;
        }
    }

    @MCVersionDependentBehaviour
    @SFMSubscribeEvent(value = SFMDist.CLIENT)
    public static void onClientTick(ClientTickEvent.Post event) {
        if (pendingWorldCreation == null) {
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

    @MCVersionDependentBehaviour
    private static void finishWorldCreation(IntegratedServer server, PendingWorldCreation completedCreation) {
        ServerLevel level = server.overworld();
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
