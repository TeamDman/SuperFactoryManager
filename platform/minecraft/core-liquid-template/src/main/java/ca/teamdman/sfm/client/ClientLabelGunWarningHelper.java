package ca.teamdman.sfm.client;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.client.screen.SFMConfirmationScreen;
import ca.teamdman.sfm.client.screen.SFMScreenChangeHelpers;
import ca.teamdman.sfm.common.label.LabelGunPlan;
import ca.teamdman.sfm.common.label.LabelGunPlanner;
import ca.teamdman.sfm.common.net.ServerboundLabelGunUsePacket;
import ca.teamdman.sfm.common.registry.registration.SFMPackets;
import ca.teamdman.sfm.common.util.ConfirmationParams;
import net.minecraft.world.entity.player.Player;

public class ClientLabelGunWarningHelper {
    public static void sendLabelGunUsePacketFromClientWithConfirmationIfNecessary(
            ServerboundLabelGunUsePacket msg,
            Player player
    ) {
        LabelGunPlan plan = LabelGunPlanner.getLabelGunPlan(player, msg, false);
        if (plan == null) {
            // No plan available, cannot proceed
            SFM.LOGGER.warn("No label gun plan available for packet: {}", msg);
            return;
        }
        ConfirmationParams confirmation = plan.getConfirmation();
        if (confirmation == null) {
            // No confirmation necessary for single updates
            SFMPackets.sendToServer(msg);
        } else {
            SFMScreenChangeHelpers.setOrPushScreen(new SFMConfirmationScreen(
                    confirmation,
                    10,
                    () -> SFMPackets.sendToServer(msg)
            ));
        }
    }
}
