package ca.teamdman.sfm.client.registry;

import ca.teamdman.sfm.client.overlay.LabelGunReminderOverlay;
import ca.teamdman.sfm.client.overlay.NetworkToolReminderOverlay;
import ca.teamdman.sfm.common.event_bus.SFMSubscribeEvent;
import ca.teamdman.sfm.common.util.SFMDist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.common.util.Lazy;

public class SFMOverlays {
    public static final Lazy<LabelGunReminderOverlay> LABEL_GUN_REMINDER_OVERLAY = Lazy.of(LabelGunReminderOverlay::new);
    public static final Lazy<NetworkToolReminderOverlay> NETWORK_TOOL_REMINDER_OVERLAY = Lazy.of(NetworkToolReminderOverlay::new);

    @SFMSubscribeEvent(value = SFMDist.CLIENT)
    public static void onRegisterOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAbove(
                VanillaGuiOverlay.HOTBAR.id(),
                "label_gun_reminder",
                LABEL_GUN_REMINDER_OVERLAY.get()
        );
        event.registerAbove(
                VanillaGuiOverlay.HOTBAR.id(),
                "network_tool_reminder",
                NETWORK_TOOL_REMINDER_OVERLAY.get()
        );
    }
}
