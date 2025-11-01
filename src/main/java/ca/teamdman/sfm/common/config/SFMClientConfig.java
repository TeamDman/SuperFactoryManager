package ca.teamdman.sfm.common.config;

import net.minecraftforge.common.ForgeConfigSpec;

public class SFMClientConfig {
    public final ForgeConfigSpec.BooleanValue showLabelGunReminderOverlay;
    public final ForgeConfigSpec.BooleanValue showNetworkToolReminderOverlay;

    SFMClientConfig(ForgeConfigSpec.Builder builder) {
        showLabelGunReminderOverlay = builder.define("showLabelGunReminderOverlay", true);
        showNetworkToolReminderOverlay = builder.define("showNetworkToolReminderOverlay", true);
    }
}
