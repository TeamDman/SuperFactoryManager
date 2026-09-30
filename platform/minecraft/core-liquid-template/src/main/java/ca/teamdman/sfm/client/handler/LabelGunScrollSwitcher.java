package ca.teamdman.sfm.client.handler;

import ca.teamdman.sfm.client.registry.SFMKeyMappings;
import ca.teamdman.sfm.common.event_bus.SFMSubscribeEvent;
import ca.teamdman.sfm.common.item.LabelGunItem;
import ca.teamdman.sfm.common.net.ServerboundLabelGunSetActiveLabelPacket;
import ca.teamdman.sfm.common.registry.registration.SFMPackets;
import ca.teamdman.sfm.common.util.SFMDist;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1' %}
import net.minecraftforge.client.event.InputEvent;
{% when '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1', '26.1.2' %}
import net.neoforged.neoforge.client.event.InputEvent;
{% endcase %}

public class LabelGunScrollSwitcher {
    @SFMSubscribeEvent(value = SFMDist.CLIENT)
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

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1' %}
        var next = LabelGunItem.getNextLabel(gun, event.getScrollDelta() < 0 ? -1 : 1);
{% when '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1', '26.1.2' %}
        var next = LabelGunItem.getNextLabel(gun, event.getScrollDeltaY() < 0 ? -1 : 1);
{% endcase %}
        SFMPackets.sendToServer(new ServerboundLabelGunSetActiveLabelPacket(
                next,
                hand
        ));

        event.setCanceled(true);
    }
}
