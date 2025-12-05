package ca.teamdman.sfm.client.handler;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.client.registry.SFMKeyMappings;
import ca.teamdman.sfm.common.item.LabelGunItem;
import ca.teamdman.sfm.common.net.ServerboundLabelGunSetActiveLabelPacket;
import ca.teamdman.sfm.common.registry.SFMPackets;
import net.minecraft.client.Minecraft;
import net.minecraft.util.EnumHand;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@Mod.EventBusSubscriber(modid = SFM.MOD_ID, value = Side.CLIENT)
public class LabelGunScrollSwitcher {
    @SideOnly(Side.CLIENT)
    @SubscribeEvent
    public static void onScroll(MouseEvent event) {
        if (event.getDwheel() == 0) return;
        var player = Minecraft.getMinecraft().player;
        if (player == null) return;
        if (!SFMKeyMappings.isKeyDown(SFMKeyMappings.LABEL_GUN_SCROLL_MODIFIER_KEY)) return;
        var gun = player.getHeldItemMainhand();
        var hand = EnumHand.MAIN_HAND;
        if (!(gun.getItem() instanceof LabelGunItem)) {
            gun = player.getHeldItemOffhand();
            hand = EnumHand.OFF_HAND;
        }
        if (!(gun.getItem() instanceof LabelGunItem)) return;

        var next = LabelGunItem.getNextLabel(gun, event.getDwheel() < 0 ? -1 : 1);
        SFMPackets.sendToServer(new ServerboundLabelGunSetActiveLabelPacket(
                next,
                hand
        ));
        LabelGunKeyMappingHandler.setExternalDebounce();

        event.setCanceled(true);
    }
}