package ca.teamdman.sfm.client.handler;

import ca.teamdman.sfm.client.screen.text_editor.SFMTextEditScreenV2;
import ca.teamdman.sfm.common.event_bus.SFMSubscribeEvent;
import ca.teamdman.sfm.common.util.SFMDist;
import net.minecraft.client.Minecraft;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1' %}
import net.minecraftforge.client.event.RenderGuiEvent;
{% when '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1', '26.1.2' %}
import net.neoforged.neoforge.client.event.RenderGuiEvent;
{% endcase %}

public class OverlayHider {
    @SFMSubscribeEvent(value = SFMDist.CLIENT)
    public static void onTryOverlay(RenderGuiEvent.Pre event) {
        if (Minecraft.getInstance().screen instanceof SFMTextEditScreenV2) {
            event.setCanceled(true);
        }
    }
}
