package ca.teamdman.sfm.gametest.puppet.definition;

import ca.teamdman.sfm.gametest.puppet.SFMGamePuppet;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppetHelper;
import ca.teamdman.sfm.gametest.puppet.action.InvokePacketLanDisabledThroughTerminalPuppetAction;
import ca.teamdman.sfm.gametest.tests.general.PacketLanDisabledGameTest;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/** Visible A4.2 proof that LAN publication disables both packet effect boundaries. */
@SFMGamePuppet(timeoutTicks = 20 * 3 * 60)
public final class InWorldPacketLanDisabledGamePuppet {
    private InWorldPacketLanDisabledGamePuppet() {
    }

    public static void run(SFMGamePuppetHelper puppet) {
        puppet.createFreshFlatWorld();
        PacketLanDisabledGameTest test = new PacketLanDisabledGameTest();
        puppet.startGameTest(test);
        puppet.waitForPacketObservation(PacketLanDisabledGameTest.FIXTURE_ID);
        puppet.publishIntegratedServerToLan();

        puppet.openCommandPalette();
        puppet.executeCommandPalette("sfm action invoke sfm:panel/open sfm:terminal");
        puppet.waitTicks(20);
        puppet.startRustTerminalThroughUi();
        puppet.waitTicks(80);

        puppet.invokePacketLanDisabledThroughTerminal();
        puppet.waitForTerminalLine(InvokePacketLanDisabledThroughTerminalPuppetAction.STATUS_WITNESS);
        puppet.writeTerminalContent(
                "lan-send-status",
                InvokePacketLanDisabledThroughTerminalPuppetAction.STATUS_WITNESS,
                "SFM_A4_LAN_SEND_STATUS=send_attempted"
        );
        puppet.writeTerminalContent(
                "lan-local-accepted",
                InvokePacketLanDisabledThroughTerminalPuppetAction.ACCEPTED_WITNESS,
                "SFM_A4_LAN_LOCAL_ACCEPTED=true"
        );
        puppet.capture(
                "lan-effects-disabled",
                Component.literal("LAN boundary ")
                        .withStyle(ChatFormatting.RED)
                        .append(Component.literal(
                                "The real sfm.exe refused the send after the integrated world was published."
                        ))
        );

        puppet.completePacketLanDisabledAttempt();
        puppet.closeScreenNaturally();
        puppet.waitForGameTest(test.testName());
        puppet.captureContainerAt(
                "lan-empty-destination",
                PacketLanDisabledGameTest.DESTINATION,
                Component.literal("LAN-disabled send left the destination chest empty.")
                        .withStyle(ChatFormatting.GREEN)
        );
    }
}
