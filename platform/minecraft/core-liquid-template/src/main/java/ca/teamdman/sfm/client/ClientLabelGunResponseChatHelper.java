package ca.teamdman.sfm.client;

import ca.teamdman.sfm.client.registry.SFMKeyMappings;
{% if features.release_version_4_35_0 %}
import ca.teamdman.sfm.common.config.SFMConfig;
{% endif %}
import ca.teamdman.sfm.common.localization.LocalizationEntry;
import ca.teamdman.sfm.common.localization.SFMLocalizationDatagen;
import ca.teamdman.sfm.common.net.ClientboundLabelGunUseResponsePacket;
import ca.teamdman.sfm.common.net.SFMPacketHandlingContext;
import ca.teamdman.sfm.common.registry.registration.SFMItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
{% if features.release_version_4_35_0 %}
import java.util.function.Consumer;
{% endif %}

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

{% if features.release_version_4_35_0 %}
{% issue 493 %}
        handle(
                msg.behaviour(),
                SFMConfig.getOrDefault(SFMConfig.CLIENT_CONFIG.showLabelGunPushPullMessages),
                message -> {
                    LocalPlayer player = Minecraft.getInstance().player;
                    assert player != null;
                    player.sendSystemMessage(message.getComponent(
                            SFMKeyMappings.getKeyDisplay(SFMKeyMappings.LABEL_GUN_PULL_MODIFIER_KEY)
                    ));
                }
        );
{% else %}
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
{% endif %}
    }
{% if features.release_version_4_35_0 %}

    static void handle(
            ClientboundLabelGunUseResponsePacket.Behaviour behaviour,
            boolean showPushPullMessages,
            Consumer<LocalizationEntry> messageSink
    ) {
        if (!showPushPullMessages) return;
        switch (behaviour) {
            case Pushed -> messageSink.accept(LABEL_GUN_CHAT_PUSHED);
            case Pulled -> messageSink.accept(LABEL_GUN_CHAT_PULLED);
        }
    }
{% endif %}

}
