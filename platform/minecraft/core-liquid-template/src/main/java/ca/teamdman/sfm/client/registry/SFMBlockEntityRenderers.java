package ca.teamdman.sfm.client.registry;

import ca.teamdman.sfm.client.render.PrintingPressBlockEntityRenderer;
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
{% if features.touch_display %}
import ca.teamdman.sfm.client.render.TouchDisplayBlockEntityRenderer;
{% endif %}
{% endcase %}
import ca.teamdman.sfm.common.event_bus.SFMSubscribeEvent;
import ca.teamdman.sfm.common.registry.registration.SFMBlockEntities;
import ca.teamdman.sfm.common.util.SFMDist;
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1" %}
import net.minecraftforge.client.event.EntityRenderersEvent;
{% when "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
{% endcase %}

public class SFMBlockEntityRenderers {
    @SFMSubscribeEvent(value = SFMDist.CLIENT)
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(
                SFMBlockEntities.PRINTING_PRESS.get(),
                PrintingPressBlockEntityRenderer::new
        );
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
{% if features.touch_display %}
        event.registerBlockEntityRenderer(
                SFMBlockEntities.TOUCH_DISPLAY.get(),
                TouchDisplayBlockEntityRenderer::new
        );
{% endif %}
{% endcase %}
    }
}
