package ca.teamdman.sfm.common.config;

{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1" %}
import net.minecraftforge.common.ForgeConfigSpec;
{% else %}
import net.neoforged.neoforge.common.ModConfigSpec;
{% endcase %}

public class SFMClientConfig {
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1" %}
    public final ForgeConfigSpec.BooleanValue showLabelGunReminderOverlay;
    public final ForgeConfigSpec.BooleanValue showNetworkToolReminderOverlay;
{% if features.command_history %}
    public final ForgeConfigSpec.BooleanValue commandPaletteHistoryEnabled;
{% endif %}
{% if features.terminal_remote or features.terminal_vox or features.terminal_vox_runtime or features.terminal_properties %}
    public final ForgeConfigSpec.ConfigValue<String> terminalRustServerAddress;
    public final ForgeConfigSpec.ConfigValue<String> terminalRustServerExecutable;
{% endif %}
{% else %}
    public final ModConfigSpec.BooleanValue showLabelGunReminderOverlay;
    public final ModConfigSpec.BooleanValue showNetworkToolReminderOverlay;
{% if features.command_history %}
    public final ModConfigSpec.BooleanValue commandPaletteHistoryEnabled;
{% endif %}
{% if features.terminal_remote or features.terminal_vox or features.terminal_vox_runtime or features.terminal_properties %}
    public final ModConfigSpec.ConfigValue<String> terminalRustServerAddress;
    public final ModConfigSpec.ConfigValue<String> terminalRustServerExecutable;
{% endif %}
{% endcase %}

{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1" %}
    SFMClientConfig(ForgeConfigSpec.Builder builder) {
{% else %}
    SFMClientConfig(ModConfigSpec.Builder builder) {
{% endcase %}
        showLabelGunReminderOverlay = builder.define("showLabelGunReminderOverlay", true);
        showNetworkToolReminderOverlay = builder.define("showNetworkToolReminderOverlay", true);
{% if features.command_history %}
        commandPaletteHistoryEnabled = builder.define("commandPaletteHistoryEnabled", true);
{% endif %}
{% if features.terminal_remote or features.terminal_vox or features.terminal_vox_runtime or features.terminal_properties %}
        terminalRustServerAddress = builder.define("terminalRustServerAddress", "127.0.0.1:63946");
        terminalRustServerExecutable = builder.define("terminalRustServerExecutable", "teamy-terminal.exe");
{% endif %}
    }
}
