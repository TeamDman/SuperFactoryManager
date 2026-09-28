package ca.teamdman.sfm.client.render;

import ca.teamdman.sfm.common.event_bus.SFMSubscribeEvent;
import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
import ca.teamdman.sfm.common.util.SFMDist;
import ca.teamdman.sfm.common.util.SFMResourceLocation;
import net.neoforged.neoforge.client.event.RegisterSpecialModelRendererEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;

public class FormItemExtensions implements IClientItemExtensions {

    @MCVersionDependentBehaviour // 1.21 this replaces FormItem#initializeClient
    @SFMSubscribeEvent(value = SFMDist.CLIENT)
    public static void registerSpecialRenderers(RegisterSpecialModelRendererEvent event) {
        event.register(
                SFMResourceLocation.fromSFMPath("form"),
                FormItemRenderer.Unbaked.MAP_CODEC
        );
    }
}
