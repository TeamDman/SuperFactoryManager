package ca.teamdman.sfm.client.registry;

import ca.teamdman.sfm.client.overlay.LabelGunReminderOverlay;
import ca.teamdman.sfm.client.overlay.NetworkToolReminderOverlay;
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
{% if features.client_overlay_scenes and features.workspace_panels %}
import ca.teamdman.sfm.client.overlay.SFMClientOverlayHost;
{% endif %}
{% endcase %}
import ca.teamdman.sfm.common.event_bus.SFMSubscribeEvent;
import ca.teamdman.sfm.common.util.SFMDist;
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1" %}
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.common.util.Lazy;
{% when "1.20.2", "1.20.3" %}
import ca.teamdman.sfm.common.util.SFMResourceLocation;
import net.neoforged.neoforge.client.event.RegisterGuiOverlaysEvent;
import net.neoforged.neoforge.client.gui.overlay.VanillaGuiOverlay;
import net.neoforged.neoforge.common.util.Lazy;
{% when "1.20.4" %}
import ca.teamdman.sfm.common.util.SFMResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.RegisterGuiOverlaysEvent;
import net.neoforged.neoforge.client.gui.overlay.VanillaGuiOverlay;
import net.neoforged.neoforge.common.util.Lazy;
{% when "1.21", "1.21.1", "26.1.2" %}
import ca.teamdman.sfm.common.util.SFMResourceLocation;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.common.util.Lazy;
{% endcase %}

public class SFMOverlays {
    public static final Lazy<LabelGunReminderOverlay> LABEL_GUN_REMINDER_OVERLAY = Lazy.of(LabelGunReminderOverlay::new);
    public static final Lazy<NetworkToolReminderOverlay> NETWORK_TOOL_REMINDER_OVERLAY = Lazy.of(NetworkToolReminderOverlay::new);
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
{% if features.client_overlay_scenes and features.workspace_panels %}
    public static final Lazy<SFMClientOverlayHost> CLIENT_SCENE_OVERLAY = Lazy.of(SFMClientOverlayHost::new);
{% endif %}
{% endcase %}

    @SFMSubscribeEvent(value = SFMDist.CLIENT)
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4" %}
    public static void onRegisterOverlays(RegisterGuiOverlaysEvent event) {
{% when "1.21", "1.21.1", "26.1.2" %}
    public static void onRegisterOverlays(RegisterGuiLayersEvent event) {
{% endcase %}
        event.registerAbove(
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1" %}
                VanillaGuiOverlay.HOTBAR.id(),
                "label_gun_reminder",
{% when "1.20.2", "1.20.3", "1.20.4" %}
                VanillaGuiOverlay.HOTBAR.id(),
                SFMResourceLocation.fromSFMPath("label_gun_reminder"),
{% when "1.21", "1.21.1", "26.1.2" %}
                VanillaGuiLayers.HOTBAR,
                SFMResourceLocation.fromSFMPath("label_gun_reminder"),
{% endcase %}
                LABEL_GUN_REMINDER_OVERLAY.get()
        );
        event.registerAbove(
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1" %}
                VanillaGuiOverlay.HOTBAR.id(),
                "network_tool_reminder",
{% when "1.20.2", "1.20.3", "1.20.4" %}
                VanillaGuiOverlay.HOTBAR.id(),
                SFMResourceLocation.fromSFMPath( "network_tool_reminder"),
{% when "1.21", "1.21.1", "26.1.2" %}
                VanillaGuiLayers.HOTBAR,
                SFMResourceLocation.fromSFMPath( "network_tool_reminder"),
{% endcase %}
                NETWORK_TOOL_REMINDER_OVERLAY.get()
        );
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
{% if features.client_overlay_scenes and features.workspace_panels %}
        event.registerAbove(
                VanillaGuiOverlay.HOTBAR.id(),
                "client_scene",
                CLIENT_SCENE_OVERLAY.get()
        );
{% endif %}
{% endcase %}
    }
}
