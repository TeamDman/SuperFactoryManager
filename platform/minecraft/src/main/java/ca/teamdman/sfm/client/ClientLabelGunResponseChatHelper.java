package ca.teamdman.sfm.client;

import ca.teamdman.sfm.common.net.SFMPacketHandlingContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import ca.teamdman.sfm.client.registry.SFMKeyMappings;
import ca.teamdman.sfm.common.localization.LocalizationKeys;
import ca.teamdman.sfm.common.net.ClientboundLabelGunUseResponsePacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

public class ClientLabelGunResponseChatHelper {
    public static void handle(
            ClientboundLabelGunUseResponsePacket msg,
            SFMPacketHandlingContext context
    ) {
        EntityPlayerSP player = Minecraft.getMinecraft().player;
        assert player != null;
        switch (msg.behaviour()) {
            case Pushed -> {
                player.sendStatusMessage(LocalizationKeys.LABEL_GUN_CHAT_PUSHED.getComponent(
                        SFMKeyMappings.getKeyDisplay(SFMKeyMappings.LABEL_GUN_PULL_MODIFIER_KEY)
                ), false);
            }
            case Pulled -> {
                player.sendStatusMessage(LocalizationKeys.LABEL_GUN_CHAT_PULLED.getComponent(
                        SFMKeyMappings.getKeyDisplay(SFMKeyMappings.LABEL_GUN_PULL_MODIFIER_KEY)
                ), false
                );
            }
        }
    }
}
