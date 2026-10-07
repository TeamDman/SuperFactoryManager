package ca.teamdman.sfm.gametest.puppet.definition;

import ca.teamdman.sfm.gametest.puppet.SFMGamePuppet;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppetHelper;
import ca.teamdman.sfm.gametest.puppet.action.InvokePacketEchoThroughTerminalPuppetAction;
import ca.teamdman.sfm.gametest.tests.general.PacketTerminalEchoGameTest;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/** Visible A4.1 proof of the terminal-driven external CLI packet round trip. */
@SFMGamePuppet(timeoutTicks = 20 * 3 * 60)
public final class InWorldPacketTerminalEchoGamePuppet {
    private InWorldPacketTerminalEchoGamePuppet() {
    }

    public static void run(SFMGamePuppetHelper puppet) {
        puppet.createFreshFlatWorld();
        PacketTerminalEchoGameTest test = new PacketTerminalEchoGameTest();
        String testName = test.testName();
        puppet.startGameTest(test);
        puppet.waitForPacketObservation(PacketTerminalEchoGameTest.FIXTURE_ID);

        puppet.openCommandPalette();
        puppet.executeCommandPalette("sfm action invoke sfm:panel/open sfm:terminal");
        puppet.waitTicks(20);
        puppet.startRustTerminalThroughUi();
        puppet.waitTicks(80);

        puppet.invokePacketEchoThroughTerminal();
        puppet.waitForTerminalLine(InvokePacketEchoThroughTerminalPuppetAction.SEND_STATUS_WITNESS);
        puppet.writeTerminalContent(
                "packet-list-schema",
                InvokePacketEchoThroughTerminalPuppetAction.LIST_SCHEMA_WITNESS,
                null
        );
        puppet.writeTerminalContent(
                "packet-send-status",
                InvokePacketEchoThroughTerminalPuppetAction.SEND_STATUS_WITNESS,
                null
        );
        puppet.capture(
                "terminal-packet-echo",
                Component.literal("SFM packet circuit ")
                        .withStyle(ChatFormatting.GOLD)
                        .append(Component.literal(
                                "The in-game terminal ran the checkout-local sfm.exe and returned a packet to the world."
                        ))
        );

        puppet.closeScreenNaturally();
        puppet.waitForGameTest(testName);
        puppet.captureContainerAt(
                "terminal-packet-destination",
                PacketTerminalEchoGameTest.DESTINATION,
                Component.literal("Terminal response packet delivered to the exact loaded chest.")
                        .withStyle(ChatFormatting.GREEN)
        );
    }
}
