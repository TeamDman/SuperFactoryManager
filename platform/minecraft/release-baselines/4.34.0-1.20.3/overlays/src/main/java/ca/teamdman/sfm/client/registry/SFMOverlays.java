package ca.teamdman.sfm.client.registry;

import ca.teamdman.sfm.client.overlay.LabelGunReminderOverlay;
import ca.teamdman.sfm.client.overlay.NetworkToolReminderOverlay;
import ca.teamdman.sfm.common.event_bus.SFMSubscribeEvent;
import ca.teamdman.sfm.common.util.SFMDist;
import ca.teamdman.sfm.common.util.SFMResourceLocation;
import net.neoforged.neoforge.client.event.RegisterGuiOverlaysEvent;
import net.neoforged.neoforge.client.gui.overlay.VanillaGuiOverlay;
import net.neoforged.neoforge.common.util.Lazy;

public class SFMOverlays {
    public static final Lazy<LabelGunReminderOverlay> LABEL_GUN_REMINDER_OVERLAY = Lazy.of(LabelGunReminderOverlay::new);
    public static final Lazy<NetworkToolReminderOverlay> NETWORK_TOOL_REMINDER_OVERLAY = Lazy.of(NetworkToolReminderOverlay::new);

    @SFMSubscribeEvent(value = SFMDist.CLIENT)
    public static void onRegisterOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAbove(
                VanillaGuiOverlay.HOTBAR.id(),
                SFMResourceLocation.fromSFMPath("label_gun_reminder"),
                LABEL_GUN_REMINDER_OVERLAY.get()
        );
        event.registerAbove(
                VanillaGuiOverlay.HOTBAR.id(),
                SFMResourceLocation.fromSFMPath( "network_tool_reminder"),
                NETWORK_TOOL_REMINDER_OVERLAY.get()
        );
    }
}
