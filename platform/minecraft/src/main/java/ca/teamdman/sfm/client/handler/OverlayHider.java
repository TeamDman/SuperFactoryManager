package ca.teamdman.sfm.client.handler;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.client.screen.text_editor.SFMTextEditScreenV2;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = SFM.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class OverlayHider {
    @SubscribeEvent
    public static void onTryOverlay(RenderGuiEvent.Pre event) {
        if (Minecraft.getInstance().screen instanceof SFMTextEditScreenV2) {
            event.setCanceled(true);
        }
    }
}
