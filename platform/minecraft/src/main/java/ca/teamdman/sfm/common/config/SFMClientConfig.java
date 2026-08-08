package ca.teamdman.sfm.common.config;

import net.minecraftforge.common.ForgeConfigSpec;

public class SFMClientConfig {
    public final ForgeConfigSpec.BooleanValue showLabelGunReminderOverlay;
    public final ForgeConfigSpec.BooleanValue showNetworkToolReminderOverlay;
    public final ForgeConfigSpec.BooleanValue commandPaletteHistoryEnabled;
    public final ForgeConfigSpec.ConfigValue<String> terminalRustServerAddress;
    public final ForgeConfigSpec.ConfigValue<String> terminalRustServerExecutable;

    SFMClientConfig(ForgeConfigSpec.Builder builder) {
        showLabelGunReminderOverlay = builder.define("showLabelGunReminderOverlay", true);
        showNetworkToolReminderOverlay = builder.define("showNetworkToolReminderOverlay", true);
        commandPaletteHistoryEnabled = builder.define("commandPaletteHistoryEnabled", true);
        terminalRustServerAddress = builder.define("terminalRustServerAddress", "127.0.0.1:63946");
        terminalRustServerExecutable = builder.define("terminalRustServerExecutable", "teamy-terminal.exe");
    }
}
