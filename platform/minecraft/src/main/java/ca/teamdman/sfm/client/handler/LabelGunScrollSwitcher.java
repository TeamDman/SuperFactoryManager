package ca.teamdman.sfm.client.handler;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.client.registry.SFMKeyMappings;
import ca.teamdman.sfm.common.item.LabelGunItem;
import ca.teamdman.sfm.common.net.ServerboundLabelGunSetActiveLabelPacket;
import ca.teamdman.sfm.common.registry.SFMPackets;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = SFM.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class LabelGunScrollSwitcher {
    @SubscribeEvent
    public static void onScroll(InputEvent.MouseScrollingEvent event) {
        var player = Minecraft.getInstance().player;
        if (player == null) return;
        if (!SFMKeyMappings.isKeyDown(SFMKeyMappings.LABEL_GUN_SCROLL_MODIFIER_KEY)) return;
        var gun = player.getMainHandItem();
        var hand = InteractionHand.MAIN_HAND;
        if (!(gun.getItem() instanceof LabelGunItem)) {
            gun = player.getOffhandItem();
            hand = InteractionHand.OFF_HAND;
        }
        if (!(gun.getItem() instanceof LabelGunItem)) return;

        var next = LabelGunItem.getNextLabel(gun, event.getScrollDelta() < 0 ? -1 : 1);
        SFMPackets.sendToServer(new ServerboundLabelGunSetActiveLabelPacket(
                next,
                hand
        ));

        event.setCanceled(true);
    }
}
