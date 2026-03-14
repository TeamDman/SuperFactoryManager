package ca.teamdman.sfm.client;

import ca.teamdman.sfm.client.registry.SFMKeyMappings;
import ca.teamdman.sfm.common.localization.LocalizationKeys;
import ca.teamdman.sfm.common.net.ClientboundLabelGunUseResponsePacket;
import ca.teamdman.sfm.common.net.SFMPacketHandlingContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

public class ClientLabelGunResponseChatHelper {
    public static void handle(
            ClientboundLabelGunUseResponsePacket msg,
            SFMPacketHandlingContext context
    ) {
        LocalPlayer player = Minecraft.getInstance().player;
        assert player != null;
        switch (msg.behaviour()) {
            case Pushed -> {
                player.sendSystemMessage(LocalizationKeys.LABEL_GUN_CHAT_PUSHED.getComponent(
                        SFMKeyMappings.getKeyDisplay(SFMKeyMappings.LABEL_GUN_PULL_MODIFIER_KEY)
                ));
            }
            case Pulled -> {
                player.sendSystemMessage(LocalizationKeys.LABEL_GUN_CHAT_PULLED.getComponent(
                        SFMKeyMappings.getKeyDisplay(SFMKeyMappings.LABEL_GUN_PULL_MODIFIER_KEY)
                ));
            }
        }
    }
}
