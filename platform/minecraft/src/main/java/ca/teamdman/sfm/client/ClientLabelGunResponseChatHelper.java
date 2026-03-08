package ca.teamdman.sfm.client;

import ca.teamdman.sfm.client.registry.SFMKeyMappings;
import ca.teamdman.sfm.common.localization.LocalizationEntry;
import ca.teamdman.sfm.common.localization.SFMLocalizationDatagen;
import ca.teamdman.sfm.common.net.ClientboundLabelGunUseResponsePacket;
import ca.teamdman.sfm.common.net.SFMPacketHandlingContext;
import ca.teamdman.sfm.common.registry.registration.SFMItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

public class ClientLabelGunResponseChatHelper {
    @SFMLocalizationDatagen
    public static final LocalizationEntry LABEL_GUN_CHAT_PULLED = new LocalizationEntry(
            () -> SFMItems.LABEL_GUN.get().getDescriptionId() + ".chat.pulled",
            () -> "Pulled labels from the manager. Release the label gun pull modifier key (%s) to push instead."
    );

    @SFMLocalizationDatagen
    public static final LocalizationEntry LABEL_GUN_CHAT_PUSHED = new LocalizationEntry(
            () -> SFMItems.LABEL_GUN.get().getDescriptionId() + ".chat.pushed",
            () -> "Pushed labels to the manager. Hold the label gun pull modifier key (%s) to pull instead."
    );

    public static void handle(
            ClientboundLabelGunUseResponsePacket msg,
            SFMPacketHandlingContext ignoredContext
    ) {

        LocalPlayer player = Minecraft.getInstance().player;
        assert player != null;
        switch (msg.behaviour()) {
            case Pushed -> player.sendSystemMessage(LABEL_GUN_CHAT_PUSHED.getComponent(
                    SFMKeyMappings.getKeyDisplay(SFMKeyMappings.LABEL_GUN_PULL_MODIFIER_KEY)
            ));
            case Pulled -> player.sendSystemMessage(LABEL_GUN_CHAT_PULLED.getComponent(
                    SFMKeyMappings.getKeyDisplay(SFMKeyMappings.LABEL_GUN_PULL_MODIFIER_KEY)
            ));
        }
    }

}
