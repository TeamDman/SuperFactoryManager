package ca.teamdman.sfm.common.config;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.event_bus.SFMSubscribeEvent;
import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1" %}
import com.electronwill.nightconfig.core.Config;
import com.electronwill.nightconfig.core.file.FileConfig;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.fml.config.ConfigTracker;
import net.minecraftforge.fml.config.IConfigSpec;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.server.ServerLifecycleHooks;
{% when "1.20.2", "1.20.3", "1.20.4" %}
import com.electronwill.nightconfig.core.Config;
import com.electronwill.nightconfig.core.file.FileConfig;
import net.minecraft.server.MinecraftServer;
import net.neoforged.fml.config.ConfigTracker;
import net.neoforged.fml.config.IConfigSpec;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
{% else %}
import net.minecraft.server.MinecraftServer;
import net.neoforged.fml.config.ConfigTracker;
import net.neoforged.fml.config.IConfigSpec;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
{% endcase %}
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.HashMap;
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4" %}
{% else %}
import java.util.Map;
{% endcase %}
import java.util.Set;

public class SFMConfigTracker {
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4" %}
    private static final HashMap<IConfigSpec<?>, Path> configPaths = new HashMap<>();
{% else %}
    private static final HashMap<IConfigSpec, Path> configPaths = new HashMap<>();
{% endcase %}

{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4" %}
    public static @Nullable Path getPathForConfig(IConfigSpec<?> spec) {
{% else %}
    public static @Nullable Path getPathForConfig(IConfigSpec spec) {
{% endcase %}
        return configPaths.get(spec);
    }

{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4" %}
    @MCVersionDependentBehaviour
    private static Set<ModConfig> getModConfigs(ModConfig.Type modConfigType) {
        return ConfigTracker.INSTANCE.configSets().get(modConfigType);
    }

{% else %}
    @SuppressWarnings({"unchecked", "UnstableApiUsage"})
    @MCVersionDependentBehaviour
    private static Set<ModConfig> getModConfigs(ModConfig.Type modConfigType) {
        ConfigTracker configTracker = ConfigTracker.INSTANCE;
        try {
            Field configSetsField = configTracker.getClass().getDeclaredField("configSets");
            configSetsField.setAccessible(true);
            Map<ModConfig.Type, Set<ModConfig>> configSets = (Map<ModConfig.Type, Set<ModConfig>>) configSetsField.get(configTracker);
            return configSets.get(modConfigType);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

{% endcase %}
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

{% if features.command_history %}
    /** Persist a client-config mutation made by an in-game action. */
    public static boolean saveClientConfig() {
        ModConfig modConfig = getClientModConfig();
        if (modConfig == null) {
            SFM.LOGGER.warn("Unable to save SFM client config because it is not registered");
            return false;
        }
        modConfig.save();
        return true;
    }

{% endif %}
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
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4" %}
                IConfigSpec<?> spec = modConfig.getSpec();
{% else %}
                IConfigSpec spec = modConfig.getSpec();
{% endcase %}
                Path path = getConfigPath(spec);
                if (path != null) {
                    configPaths.put(spec, path);
                }
            }
        }

{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4" %}
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
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1" %}
            if (configSpec instanceof ForgeConfigSpec forgeConfigSpec) {
{% else %}
            if (configSpec instanceof ModConfigSpec forgeConfigSpec) {
{% endcase %}
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
{% else %}
        @MCVersionDependentBehaviour
        private static @Nullable Path getConfigPath(IConfigSpec configSpec) {
            IConfigSpec.ILoadedConfig loadedConfig;
            try {
                Field loadedConfigField = configSpec.getClass().getDeclaredField("loadedConfig");
                loadedConfigField.setAccessible(true);
                loadedConfig = (IConfigSpec.ILoadedConfig) loadedConfigField.get(configSpec);
            } catch (NoSuchFieldException | IllegalAccessException e) {
                SFM.LOGGER.error("Failed to extract loadedConfig field", e);
                return null;
            }
            try {
                Class<?> loadedConfigClass = loadedConfig.getClass();
                Field pathField = loadedConfigClass.getDeclaredField("path");
                pathField.setAccessible(true);
                return (Path) pathField.get(loadedConfig);
            } catch (NoSuchFieldException | IllegalAccessException e) {
                SFM.LOGGER.error("Failed to extract path field", e);
                return null;
            }
        }
{% endcase %}
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
