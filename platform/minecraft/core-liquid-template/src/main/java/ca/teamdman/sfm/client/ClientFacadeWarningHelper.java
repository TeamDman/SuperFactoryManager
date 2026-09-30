package ca.teamdman.sfm.client;

import ca.teamdman.sfm.client.screen.SFMConfirmationScreen;
import ca.teamdman.sfm.client.screen.SFMScreenChangeHelpers;
import ca.teamdman.sfm.common.facade.FacadePlanner;
import ca.teamdman.sfm.common.facade.IFacadePlan;
import ca.teamdman.sfm.common.net.ServerboundFacadePacket;
import ca.teamdman.sfm.common.registry.registration.SFMPackets;
import ca.teamdman.sfm.common.util.ConfirmationParams;
import ca.teamdman.sfm.common.util.SFMEntityUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

public class ClientFacadeWarningHelper {
    public static void sendFacadePacketFromClientWithConfirmationIfNecessary(ServerboundFacadePacket msg) {
        // Given the incentives for a single cable network to be used,
        // we want to protect users from accidentally clobbering their designs in a single action
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        assert player != null;
        Level level = SFMEntityUtils.getLevel(player);

        IFacadePlan facadePlan = FacadePlanner.getFacadePlan(
                player,
                level,
                msg
        );
        if (facadePlan == null) return;
        ConfirmationParams warning = facadePlan.computeWarning(level);
        if (warning == null) {
            // No confirmation necessary for single updates
            SFMPackets.sendToServer(msg);
            // Perform eager update
            facadePlan.apply(level);
        } else {
            SFMScreenChangeHelpers.setOrPushScreen(new SFMConfirmationScreen(
                    warning,
                    10,
                    () -> {
                        // Send packet
                        SFMPackets.sendToServer(msg);
                        // Perform eager update
                        facadePlan.apply(level);
                    }
            ));
        }
    }
}
