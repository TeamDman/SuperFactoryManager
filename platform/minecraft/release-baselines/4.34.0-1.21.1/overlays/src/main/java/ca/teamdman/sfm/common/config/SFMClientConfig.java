package ca.teamdman.sfm.common.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public class SFMClientConfig {
    public final ModConfigSpec.BooleanValue showLabelGunReminderOverlay;
    public final ModConfigSpec.BooleanValue showNetworkToolReminderOverlay;

    SFMClientConfig(ModConfigSpec.Builder builder) {
        showLabelGunReminderOverlay = builder.define("showLabelGunReminderOverlay", true);
        showNetworkToolReminderOverlay = builder.define("showNetworkToolReminderOverlay", true);
    }
}
