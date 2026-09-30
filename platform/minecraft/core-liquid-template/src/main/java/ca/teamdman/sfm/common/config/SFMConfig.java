package ca.teamdman.sfm.common.config;

import ca.teamdman.sfm.common.util.SFMEnvironmentUtils;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1' %}
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
{% when '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1', '26.1.2' %}
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;
{% endcase %}


/*
2024-11-12
- SFM currently uses COMMON when it seems like it should be SERVER
- SERVER configs are automatically sent to clients
- Search discord for "send config" and "ConfigTracker" to find discussions

- SFM currently sends a packet and receives a packet to display the server config, this should be replaced with showing the config synced from the server using built-in behaviour
- SFM wants to send the updated config TOML but the handler is stubbed. Config needs to be updated from toml, saved, and resent to clients.

MehVahdJukaar — 03/19/2021 7:25 PM
https://discord.com/channels/313125603924639766/725850371834118214/822611868275310592
so I've managed to sync the common config file by sending to the client its data and then
using CONFIG.setConfig(TomlFormat.instance().createParser().parse(data)) like it's done in
ConfigTracker class. However I would like to be able to load the original client side config
file (still common) back up in case I want to edit it. How can I do that?

sleepy sci, on graveyard duty — 03/19/2021 7:42 PM
https://discord.com/channels/313125603924639766/725850371834118214/822615931510718514
the common config is meant for config settings which do not impact any game logic, but would be useful to store/have on both sides (and which can be separate)
server config is for server-controlled values
client config is for client only player-controlled values
common is anything else

sleepy sci, on graveyard duty — 03/19/2021 7:42 PM
https://discord.com/channels/313125603924639766/725850371834118214/822616037417549835
data defined by the server that affects client-side ...
then it should be server config

 */
public class SFMConfig {
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1' %}
    public static final ForgeConfigSpec SERVER_CONFIG_SPEC;
{% when '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1', '26.1.2' %}
    public static final ModConfigSpec SERVER_CONFIG_SPEC;
{% endcase %}
    public static final SFMServerConfig SERVER_CONFIG;

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1' %}
    public static final ForgeConfigSpec CLIENT_CONFIG_SPEC;
{% when '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1', '26.1.2' %}
    public static final ModConfigSpec CLIENT_CONFIG_SPEC;
{% endcase %}
    public static final SFMClientConfig CLIENT_CONFIG;

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1' %}
    public static final ForgeConfigSpec CLIENT_TEXT_EDITOR_CONFIG_SPEC;
{% when '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1', '26.1.2' %}
    public static final ModConfigSpec CLIENT_TEXT_EDITOR_CONFIG_SPEC;
{% endcase %}
    public static final SFMClientTextEditorConfig CLIENT_TEXT_EDITOR_CONFIG;

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1' %}
    public static final ForgeConfigSpec AI_CONFIG_SPEC;
{% when '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1', '26.1.2' %}
    public static final ModConfigSpec AI_CONFIG_SPEC;
{% endcase %}
    public static final SFMAIConfig AI_CONFIG;

    static {
        {
            var pair =
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1' %}
                    new ForgeConfigSpec.Builder().configure(SFMServerConfig::new);
{% when '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1', '26.1.2' %}
                    new ModConfigSpec.Builder().configure(SFMServerConfig::new);
{% endcase %}
            SERVER_CONFIG_SPEC = pair.getRight();
            SERVER_CONFIG = pair.getLeft();
        }
        {
            var pair =
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1' %}
                    new ForgeConfigSpec.Builder().configure(SFMClientConfig::new);
{% when '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1', '26.1.2' %}
                    new ModConfigSpec.Builder().configure(SFMClientConfig::new);
{% endcase %}
            CLIENT_CONFIG_SPEC = pair.getRight();
            CLIENT_CONFIG = pair.getLeft();
        }
        {
            var pair =
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1' %}
                    new ForgeConfigSpec.Builder().configure(SFMClientTextEditorConfig::new);
{% when '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1', '26.1.2' %}
                    new ModConfigSpec.Builder().configure(SFMClientTextEditorConfig::new);
{% endcase %}
            CLIENT_TEXT_EDITOR_CONFIG_SPEC = pair.getRight();
            CLIENT_TEXT_EDITOR_CONFIG = pair.getLeft();
        }
        {
            var pair =
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1' %}
                    new ForgeConfigSpec.Builder().configure(SFMAIConfig::new);
{% when '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1', '26.1.2' %}
                    new ModConfigSpec.Builder().configure(SFMAIConfig::new);
{% endcase %}
            AI_CONFIG_SPEC = pair.getRight();
            AI_CONFIG = pair.getLeft();
        }
    }

    /**
     * Get a config value in a way that doesn't fail when running tests
     */
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1' %}
    public static <T> T getOrDefault(ForgeConfigSpec.ConfigValue<T> configValue) {
{% when '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1', '26.1.2' %}
    public static <T> T getOrDefault(ModConfigSpec.ConfigValue<T> configValue) {
{% endcase %}
        try {
            return configValue.get();
        } catch (Exception e) {
            return configValue.getDefault();
        }
    }
    /**
     * Get a config value in a way that doesn't fail when running tests
     */
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1' %}
    public static <T> T getOrFallback(ForgeConfigSpec.ConfigValue<T> configValue, T fallback) {
{% when '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1', '26.1.2' %}
    public static <T> T getOrFallback(ModConfigSpec.ConfigValue<T> configValue, T fallback) {
{% endcase %}
        try {
            return configValue.get();
        } catch (Exception e) {
            return fallback;
        }
    }

    public static void register(ModLoadingContext context) {
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}
        context.registerConfig(ModConfig.Type.SERVER, SFMConfig.SERVER_CONFIG_SPEC);
        context.registerConfig(ModConfig.Type.CLIENT, SFMConfig.CLIENT_CONFIG_SPEC);
        context.registerConfig(ModConfig.Type.CLIENT, SFMConfig.CLIENT_TEXT_EDITOR_CONFIG_SPEC, "sfm-client-program-editor.toml");
{% when '1.21', '1.21.1', '26.1.2' %}
        context.getActiveContainer().registerConfig(ModConfig.Type.SERVER, SFMConfig.SERVER_CONFIG_SPEC);
        context.getActiveContainer().registerConfig(ModConfig.Type.CLIENT, SFMConfig.CLIENT_CONFIG_SPEC);
        context.getActiveContainer().registerConfig(ModConfig.Type.CLIENT, SFMConfig.CLIENT_TEXT_EDITOR_CONFIG_SPEC, "sfm-client-program-editor.toml");
{% endcase %}
        if (SFMEnvironmentUtils.isInIDE()) {
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}
            context.registerConfig(ModConfig.Type.COMMON, SFMConfig.AI_CONFIG_SPEC, "sfm-ai.toml");
{% when '1.21', '1.21.1', '26.1.2' %}
            context.getActiveContainer().registerConfig(ModConfig.Type.COMMON, SFMConfig.AI_CONFIG_SPEC, "sfm-ai.toml");
{% endcase %}
        }
    }
}
