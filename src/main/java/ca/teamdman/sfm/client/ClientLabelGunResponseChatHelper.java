package ca.teamdman.sfm.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import ca.teamdman.sfm.client.registry.SFMKeyMappings;
import ca.teamdman.sfm.common.localization.LocalizationKeys;
import ca.teamdman.sfm.common.net.ClientboundLabelGunUseResponsePacket;

public class ClientLabelGunResponseChatHelper {

    public static void handle(
                              ClientboundLabelGunUseResponsePacket msg,
                              MessageContext context) {
        EntityPlayerSP player = Minecraft.getMinecraft().player;
        assert player != null;
        switch (msg.getBehaviour()) {
            case Pushed -> {
                player.sendStatusMessage(LocalizationKeys.LABEL_GUN_CHAT_PUSHED.getComponent(
                        SFMKeyMappings.getKeyDisplay(SFMKeyMappings.LABEL_GUN_PULL_MODIFIER_KEY)), false);
            }
            case Pulled -> {
                player.sendStatusMessage(LocalizationKeys.LABEL_GUN_CHAT_PULLED.getComponent(
                        SFMKeyMappings.getKeyDisplay(SFMKeyMappings.LABEL_GUN_PULL_MODIFIER_KEY)), false);
            }
        }
    }
}
