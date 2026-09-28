package ca.teamdman.sfm.common.config;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.event_bus.SFMSubscribeEvent;
import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
import com.electronwill.nightconfig.core.Config;
import com.electronwill.nightconfig.core.file.FileConfig;
import net.minecraft.server.MinecraftServer;
import net.neoforged.fml.config.ConfigTracker;
import net.neoforged.fml.config.IConfigSpec;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Set;

public class SFMConfigTracker {
    private static final HashMap<IConfigSpec<?>, Path> configPaths = new HashMap<>();

    public static @Nullable Path getPathForConfig(IConfigSpec<?> spec) {
        return configPaths.get(spec);
    }

    @MCVersionDependentBehaviour
    private static Set<ModConfig> getModConfigs(ModConfig.Type modConfigType) {
        return ConfigTracker.INSTANCE.configSets().get(modConfigType);
    }

    static @Nullable ModConfig getServerModConfig() {
        Set<ModConfig> modConfigs = getModConfigs(ModConfig.Type.SERVER);
        for (ModConfig modConfig : modConfigs) {
            // .equals() doesn't work here
            if (modConfig.getSpec() == SFMConfig.SERVER_CONFIG_SPEC) {
                return modConfig;
            }
        }
        return null;
    }

    static @Nullable ModConfig getClientModConfig() {
        Set<ModConfig> modConfigs = getModConfigs(ModConfig.Type.CLIENT);
        for (ModConfig modConfig : modConfigs) {
            // .equals() doesn't work here
            if (modConfig.getSpec() == SFMConfig.CLIENT_CONFIG_SPEC) {
                return modConfig;
            }
        }
        return null;
    }

        public static class ModConfigEventListeners {
        /**
         * Tracks when configs are loaded
         * <p>
         * See {@link ConfigTracker#openConfig(ModConfig, Path)}
         */
        @SuppressWarnings("JavadocReference")
        @SFMSubscribeEvent
        public static void onConfigLoaded(ModConfigEvent.Loading event) {
            handleConfigEvent(event);
        }

        @SFMSubscribeEvent
        public static void onConfigReloaded(ModConfigEvent.Reloading event) {
            handleConfigEvent(event);
        }

        private static void handleConfigEvent(ModConfigEvent event) {
            ModConfig modConfig = event.getConfig();
            if (modConfig.getModId().equals(SFM.MOD_ID)) {
                IConfigSpec<?> spec = modConfig.getSpec();
                Path path = getConfigPath(spec);
                if (path != null) {
                    configPaths.put(spec, path);
                }
            }
        }

        @MCVersionDependentBehaviour
        private static @Nullable Path getConfigPath(IConfigSpec<?> configSpec) {
            FileConfig fileConfig = getFileConfig(configSpec);
            if (fileConfig != null) {
                return fileConfig.getNioPath();
            }
            return null;
        }


        private static @Nullable FileConfig getFileConfig(IConfigSpec<?> configSpec) {
            Config config = getChildConfig(configSpec);
            if (config instanceof FileConfig fileConfig) {
                return fileConfig;
            }
            return null;
        }

        private static @Nullable Config getChildConfig(IConfigSpec<?> configSpec) {
            if (configSpec instanceof ModConfigSpec forgeConfigSpec) {
                try {
                    Field childConfigField = forgeConfigSpec.getClass().getDeclaredField("childConfig");
                    childConfigField.setAccessible(true);
                    return (Config) childConfigField.get(forgeConfigSpec);
                } catch (NoSuchFieldException | IllegalAccessException e) {
                    SFM.LOGGER.error("Failed to extract childConfig field", e);
                    return null;
                }
            }
            return null;
        }
    }

        public static class GameConfigEventListeners {
        /**
         * Tracks when configs are unloaded
         * <p>
         * See {@link ConfigTracker#unloadConfigs(ModConfig.Type, Path)}
         * which is called by {@link ServerLifecycleHooks#handleServerStopped(MinecraftServer)}
         */
        @SFMSubscribeEvent
        public static void onServerStopped(ServerStoppedEvent event) {
            configPaths.entrySet().removeIf(entry -> entry.getKey() == SFMConfig.SERVER_CONFIG_SPEC);
        }
    }
}
