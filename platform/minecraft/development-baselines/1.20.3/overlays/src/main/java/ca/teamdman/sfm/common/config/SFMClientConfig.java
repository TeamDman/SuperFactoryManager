package ca.teamdman.sfm.common.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public class SFMClientConfig {
    public final ModConfigSpec.BooleanValue showLabelGunReminderOverlay;
    public final ModConfigSpec.BooleanValue showNetworkToolReminderOverlay;
    public final ModConfigSpec.ConfigValue<String> terminalRustServerAddress;
    public final ModConfigSpec.ConfigValue<String> terminalRustServerExecutable;

    SFMClientConfig(ModConfigSpec.Builder builder) {
        showLabelGunReminderOverlay = builder.define("showLabelGunReminderOverlay", true);
        showNetworkToolReminderOverlay = builder.define("showNetworkToolReminderOverlay", true);
        terminalRustServerAddress = builder.define("terminalRustServerAddress", "127.0.0.1:63946");
        terminalRustServerExecutable = builder.define("terminalRustServerExecutable", "teamy-terminal.exe");
    }
}
