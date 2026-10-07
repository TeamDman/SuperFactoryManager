package ca.teamdman.sfm.gametest.puppet.definition;

import ca.teamdman.sfm.gametest.puppet.SFMGamePuppet;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppetHelper;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppetViewportProfile;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/** Framework-owned probe proving that a full declaration is repeated for every selected viewport. */
@SFMGamePuppet(viewportProfile = SFMGamePuppetViewportProfile.COMMON_RESPONSIVE)
public final class ViewportSweepProbeGamePuppet {
    private ViewportSweepProbeGamePuppet() {
    }

    public static void run(SFMGamePuppetHelper puppet) {
        puppet.waitTicks(SFMGamePuppetHelper.RENDER_SETTLE_TICKS);
        puppet.capture(
                "stable-title-screen",
                Component.literal("Responsive viewport framework probe.").withStyle(ChatFormatting.AQUA)
        );
    }
}
