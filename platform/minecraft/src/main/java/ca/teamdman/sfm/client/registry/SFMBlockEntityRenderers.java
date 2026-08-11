package ca.teamdman.sfm.client.registry;

import ca.teamdman.sfm.client.render.PrintingPressBlockEntityRenderer;
import ca.teamdman.sfm.common.event_bus.SFMSubscribeEvent;
import ca.teamdman.sfm.common.registry.registration.SFMBlockEntities;
import ca.teamdman.sfm.common.util.SFMDist;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

public class SFMBlockEntityRenderers {
    @SFMSubscribeEvent(value = SFMDist.CLIENT)
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(
                SFMBlockEntities.PRINTING_PRESS.get(),
                PrintingPressBlockEntityRenderer::new
        );
    }
}
