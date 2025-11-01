package ca.teamdman.sfm.common.config;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.localization.LocalizationKeys;
import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.electronwill.nightconfig.core.io.WritingMode;
import com.electronwill.nightconfig.toml.TomlFormat;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.Bindings;
import net.minecraftforge.fml.config.IConfigEvent;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.network.ConfigSync;
import net.minecraftforge.network.HandshakeHandler;
import net.minecraftforge.network.HandshakeMessages;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Supplier;

@SuppressWarnings("BooleanMethodIsAlwaysInverted")
public class SFMConfigReadWriter {
    /**
     * SERVER configs are synced at login to servers, which can serve as inspiration for how we should update the configs on our own.
     * <p>
     * <p>
     * The {@link ConfigSync#syncConfigs(boolean)} method provides a list of {@link HandshakeMessages.S2CConfigData}
     * Those packets are registered in {@link net.minecraftforge.network.NetworkInitialization#getHandshakeChannel()}
     * Those packets have handler {@link HandshakeHandler#handleConfigSync(HandshakeMessages.S2CConfigData, Supplier)}
     * which calls {@link ConfigSync#receiveSyncedConfig(HandshakeMessages.S2CConfigData, Supplier)}
     * which calls {@link ModConfig#acceptSyncedConfig(byte[])}
     */
    @SuppressWarnings("JavadocReference")
    public static ConfigSyncResult updateAndSyncServerConfig(String newConfigToml) {
        try {
            SFM.LOGGER.debug("Received server config for update and sync:\n{}", newConfigToml);
            CommentedConfig config = parseConfigToml(newConfigToml, SFMConfig.SERVER_CONFIG_SPEC);
            if (config == null) {
                SFM.LOGGER.error("Received invalid server config from player");
                return ConfigSyncResult.INVALID_CONFIG;
            }
            if (!writeServerConfig(config)) {
                SFM.LOGGER.error("Failed to write server config");
                return ConfigSyncResult.INTERNAL_FAILURE;
            }
            // Here is where SFM would distribute the new config to players.
            // For now, we don't care if the client doesn't have the latest server config.
            return ConfigSyncResult.SUCCESS;
        } catch (Throwable t) {
            SFM.LOGGER.error("Failed to update and sync server config", t);
            return ConfigSyncResult.INTERNAL_FAILURE;
        }
    }

    public static ConfigSyncResult updateClientConfig(String newConfigToml) {
        try {
            SFM.LOGGER.debug("Received client config for update and sync:\n{}", newConfigToml);
            CommentedConfig config = parseConfigToml(newConfigToml, SFMConfig.CLIENT_CONFIG_SPEC);
            if (config == null) {
                SFM.LOGGER.error("Received invalid config");
                return ConfigSyncResult.INVALID_CONFIG;
            }
            if (!writeClientConfig(config)) {
                SFM.LOGGER.error("Failed to write client config");
                return ConfigSyncResult.INTERNAL_FAILURE;
            }
            // Here is where SFM would distribute the new config to players.
            // For now, we don't care if the client doesn't have the latest server config.
            return ConfigSyncResult.SUCCESS;
        } catch (Throwable t) {
            SFM.LOGGER.error("Failed to update and sync client config", t);
            return ConfigSyncResult.INTERNAL_FAILURE;
        }
    }

    public static @Nullable Path getConfigBasePath() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return null;
        }
        try {
            Method getServerConfigPath = ServerLifecycleHooks.class.getDeclaredMethod(
                    "getServerConfigPath",
                    MinecraftServer.class
            );
            getServerConfigPath.setAccessible(true);
            return (Path) getServerConfigPath.invoke(null, server);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            return null;
        }
    }

    @SuppressWarnings("unused")
    @MCVersionDependentBehaviour
    public static boolean updateActiveConfigAndFireReloadedEvent(
            ModConfig modConfig,
            Path configBasePath,
            Path configPath,
            CommentedConfig newConfig
    ) {
        final CommentedFileConfig fileConfig = modConfig.getHandler()
                .reader(configBasePath)
                .apply(modConfig);
        SFM.LOGGER.info("Setting up new config data for {}", modConfig.getFileName());
        if (!setConfigData(modConfig, fileConfig)) {
            SFM.LOGGER.warn("Failed to set new config data for {}", modConfig.getFileName());
            return false;
        }

        SFM.LOGGER.info("Firing config changed event for {}", modConfig.getFileName());
        try {
            Method fireEvent = ModConfig.class.getDeclaredMethod("fireEvent", IConfigEvent.class);
            fireEvent.setAccessible(true);
            IConfigEvent event = Bindings.getConfigConfiguration().get().reloading().apply(modConfig);
            fireEvent.invoke(modConfig, event);
        } catch (InvocationTargetException | IllegalAccessException | NoSuchMethodException e) {
            SFM.LOGGER.warn("Failed to fire changed event for {}", modConfig.getFileName(), e);
            return false;
        }
        return true;
    }

    public static @Nullable CommentedConfig parseConfigToml(
            String configToml,
            ForgeConfigSpec configSpec
    ) {
        CommentedConfig config = TomlFormat.instance().createParser().parse(configToml);
        if (!configSpec.isCorrect(config)) {
            return null;
        }
        return config;
    }

    public static @Nullable String getConfigToml(ForgeConfigSpec configSpec) {
        Path configPath = SFMConfigTracker.getPathForConfig(configSpec);
        if (configPath == null) {
            SFM.LOGGER.error("Failed to get config path when trying to get config TOML contents");
            return null;
        }
        try {
            return Files.readString(configPath, StandardCharsets.UTF_8);
        } catch (IOException e) {
            SFM.LOGGER.error("Failed reading config contents", e);
            return null;
        }
    }

    private static boolean writeServerConfig(CommentedConfig config) {
        // Get the config base path
        Path configBasePath = getConfigBasePath();
        if (configBasePath == null) {
            SFM.LOGGER.warn("Failed to get server config base path");
            return false;
        }

        // Get the config path
        Path configPath = SFMConfigTracker.getPathForConfig(SFMConfig.SERVER_CONFIG_SPEC);
        if (configPath == null) {
            SFM.LOGGER.warn("Failed to get server config path");
            return false;
        }

        // Get the mod config obj
        ModConfig modConfig = SFMConfigTracker.getServerModConfig();
        if (modConfig == null) {
            SFM.LOGGER.warn("Failed to get server mod config");
            return false;
        }

        // We do not have to close the config before changing.
        // If you were to do so, it would break the file watching because the unload method unwatches the whole dir.
        // This causes "Failed to remove config {} from tracker!" warnings vvv
        // java.lang.NullPointerException: Cannot read field "watchedFileCount" because "watchedDir" is null
        // So, we do nothing to close the old config
        // modConfig.getHandler().unload(configBasePath, modConfig);

        // Write the new config
        TomlFormat.instance().createWriter().write(config, configPath, WritingMode.REPLACE);

        // Load the new config
        return updateActiveConfigAndFireReloadedEvent(modConfig, configBasePath, configPath, config);
    }

    private static boolean writeClientConfig(CommentedConfig config) {
        // Get the config base path
        Path configBasePath = getConfigBasePath();
        if (configBasePath == null) {
            SFM.LOGGER.warn("Failed to get client config base path");
            return false;
        }

        // Get the config path
        Path configPath = SFMConfigTracker.getPathForConfig(SFMConfig.CLIENT_CONFIG_SPEC);
        if (configPath == null) {
            SFM.LOGGER.warn("Failed to get client config path");
            return false;
        }

        // Get the mod config obj
        ModConfig modConfig = SFMConfigTracker.getClientModConfig();
        if (modConfig == null) {
            SFM.LOGGER.warn("Failed to get client mod config");
            return false;
        }

        // We do not have to close the config before changing.
        // If you were to do so, it would break the file watching because the unload method unwatches the whole dir.
        // This causes "Failed to remove config {} from tracker!" warnings vvv
        // java.lang.NullPointerException: Cannot read field "watchedFileCount" because "watchedDir" is null
        // So, we do nothing to close the old config
        // modConfig.getHandler().unload(configBasePath, modConfig);

        // Write the new config
        TomlFormat.instance().createWriter().write(config, configPath, WritingMode.REPLACE);

        // Load the new config
        return updateActiveConfigAndFireReloadedEvent(modConfig, configBasePath, configPath, config);
    }

    private static boolean setConfigData(
            ModConfig modConfig,
            CommentedConfig configData
    ) {
        try {
            Method setConfigData = ModConfig.class.getDeclaredMethod("setConfigData", CommentedConfig.class);
            setConfigData.setAccessible(true);
            setConfigData.invoke(modConfig, configData);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            SFM.LOGGER.warn("Failed to set new config data", e);
            return false;
        }
        return true;
    }

    public enum ConfigSyncResult {
        SUCCESS,
        INVALID_CONFIG,
        FAILED_TO_FIND,
        INTERNAL_FAILURE;

        public MutableComponent component() {
            return switch (this) {
                case SUCCESS -> LocalizationKeys.CONFIG_UPDATE_AND_SYNC_RESULT_SUCCESS.getComponent();
                case INVALID_CONFIG -> LocalizationKeys.CONFIG_UPDATE_AND_SYNC_RESULT_INVALID_CONFIG.getComponent();
                case FAILED_TO_FIND -> LocalizationKeys.CONFIG_UPDATE_AND_SYNC_RESULT_FAILED_TO_FIND.getComponent();
                case INTERNAL_FAILURE -> LocalizationKeys.CONFIG_UPDATE_AND_SYNC_RESULT_INTERNAL_FAILURE.getComponent();
            };
        }
    }
}
