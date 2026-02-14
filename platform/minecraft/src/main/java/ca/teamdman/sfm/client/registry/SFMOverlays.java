package ca.teamdman.sfm.client.registry;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.client.overlay.LabelGunReminderOverlay;
import ca.teamdman.sfm.client.overlay.NetworkToolReminderOverlay;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

@Mod.EventBusSubscriber(modid = SFM.MOD_ID, value = Side.CLIENT)
public class SFMOverlays {
    public static final LabelGunReminderOverlay LABEL_GUN_REMINDER_OVERLAY = new LabelGunReminderOverlay();
    public static final NetworkToolReminderOverlay NETWORK_TOOL_REMINDER_OVERLAY = new NetworkToolReminderOverlay();

    @SubscribeEvent
    public static void onRegisterOverlays(RenderGameOverlayEvent.Post event) {
        if (event.getType() == RenderGameOverlayEvent.ElementType.HOTBAR) {
            LABEL_GUN_REMINDER_OVERLAY.render(event.getPartialTicks(), event.getResolution().getScaledWidth(), event.getResolution().getScaledHeight());
            NETWORK_TOOL_REMINDER_OVERLAY.render(event.getPartialTicks(), event.getResolution().getScaledWidth(), event.getResolution().getScaledHeight());
        }

    }
}
