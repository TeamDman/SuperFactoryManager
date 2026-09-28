package ca.teamdman.sfm.common.config;

import ca.teamdman.sfm.common.util.SFMEnvironmentUtils;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;


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
    public static final ModConfigSpec SERVER_CONFIG_SPEC;
    public static final SFMServerConfig SERVER_CONFIG;

    public static final ModConfigSpec CLIENT_CONFIG_SPEC;
    public static final SFMClientConfig CLIENT_CONFIG;

    public static final ModConfigSpec CLIENT_TEXT_EDITOR_CONFIG_SPEC;
    public static final SFMClientTextEditorConfig CLIENT_TEXT_EDITOR_CONFIG;

    public static final ModConfigSpec AI_CONFIG_SPEC;
    public static final SFMAIConfig AI_CONFIG;

    static {
        {
            var pair =
                    new ModConfigSpec.Builder().configure(SFMServerConfig::new);
            SERVER_CONFIG_SPEC = pair.getRight();
            SERVER_CONFIG = pair.getLeft();
        }
        {
            var pair =
                    new ModConfigSpec.Builder().configure(SFMClientConfig::new);
            CLIENT_CONFIG_SPEC = pair.getRight();
            CLIENT_CONFIG = pair.getLeft();
        }
        {
            var pair =
                    new ModConfigSpec.Builder().configure(SFMClientTextEditorConfig::new);
            CLIENT_TEXT_EDITOR_CONFIG_SPEC = pair.getRight();
            CLIENT_TEXT_EDITOR_CONFIG = pair.getLeft();
        }
        {
            var pair =
                    new ModConfigSpec.Builder().configure(SFMAIConfig::new);
            AI_CONFIG_SPEC = pair.getRight();
            AI_CONFIG = pair.getLeft();
        }
    }

    /**
     * Get a config value in a way that doesn't fail when running tests
     */
    public static <T> T getOrDefault(ModConfigSpec.ConfigValue<T> configValue) {
        try {
            return configValue.get();
        } catch (Exception e) {
            return configValue.getDefault();
        }
    }
    /**
     * Get a config value in a way that doesn't fail when running tests
     */
    public static <T> T getOrFallback(ModConfigSpec.ConfigValue<T> configValue, T fallback) {
        try {
            return configValue.get();
        } catch (Exception e) {
            return fallback;
        }
    }

    public static void register(ModLoadingContext context) {
        context.registerConfig(ModConfig.Type.SERVER, SFMConfig.SERVER_CONFIG_SPEC);
        context.registerConfig(ModConfig.Type.CLIENT, SFMConfig.CLIENT_CONFIG_SPEC);
        context.registerConfig(ModConfig.Type.CLIENT, SFMConfig.CLIENT_TEXT_EDITOR_CONFIG_SPEC, "sfm-client-program-editor.toml");
        if (SFMEnvironmentUtils.isInIDE()) {
            context.registerConfig(ModConfig.Type.COMMON, SFMConfig.AI_CONFIG_SPEC, "sfm-ai.toml");
        }
    }
}
