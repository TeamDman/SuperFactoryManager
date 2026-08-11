package ca.teamdman.sfm.client.handler;

import ca.teamdman.sfm.client.screen.text_editor.SFMTextEditScreenV2;
import ca.teamdman.sfm.common.event_bus.SFMSubscribeEvent;
import ca.teamdman.sfm.common.util.SFMDist;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

public class OverlayHider {
    @SFMSubscribeEvent(value = SFMDist.CLIENT)
    public static void onTryOverlay(RenderGuiEvent.Pre event) {
        if (Minecraft.getInstance().screen instanceof SFMTextEditScreenV2) {
            event.setCanceled(true);
        }
    }
}
