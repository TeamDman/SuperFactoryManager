package ca.teamdman.sfm.gametest.puppet.definition;

import ca.teamdman.sfm.gametest.puppet.SFMGamePuppet;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppetHelper;
import ca.teamdman.sfm.gametest.puppet.action.InvokePacketLanguageWorkerThroughTerminalPuppetAction;
import ca.teamdman.sfm.gametest.tests.general.PacketLanguageTerminalWorkerGameTest;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/** Visible Slice D proof from language-created request through the external worker and response routing. */
@SFMGamePuppet(timeoutTicks = 20 * 4 * 60)
public final class InWorldPacketLanguageWorkerGamePuppet {
    private InWorldPacketLanguageWorkerGamePuppet() {
    }

    public static void run(SFMGamePuppetHelper puppet) {
        puppet.createFreshFlatWorld();
        PacketLanguageTerminalWorkerGameTest test = new PacketLanguageTerminalWorkerGameTest();
        String testName = test.testName();
        puppet.startGameTest(test);
        puppet.waitForPacketObservation(PacketLanguageTerminalWorkerGameTest.FIXTURE_ID);

        puppet.openCommandPalette();
        puppet.executeCommandPalette("sfm action invoke sfm:panel/open sfm:terminal");
        puppet.waitTicks(20);
        puppet.startRustTerminalThroughUi();
        puppet.waitTicks(80);
        puppet.invokePacketLanguageWorkerThroughTerminal();
        puppet.waitForTerminalLine(InvokePacketLanguageWorkerThroughTerminalPuppetAction.DUPLICATE_WITNESS);
        puppet.writeTerminalContent(
                "packet-language-worker",
                InvokePacketLanguageWorkerThroughTerminalPuppetAction.RESPONSE_STATUS_WITNESS,
                null
        );
        puppet.capture(
                "terminal-packet-language-worker",
                Component.literal("Packet language worker ")
                        .withStyle(ChatFormatting.GOLD)
                        .append(Component.literal(
                                "The checkout-local sfm.exe accepted one ACK and two deterministic responses."
                        ))
        );

        puppet.closeScreenNaturally();
        puppet.waitForGameTest(testName);
        puppet.captureContainerAt(
                "packet-language-request-archive",
                PacketLanguageTerminalWorkerGameTest.REQUEST_ARCHIVE,
                Component.literal("Language-created request and source disk archived together.")
                        .withStyle(ChatFormatting.GREEN)
        );
        puppet.captureContainerAt(
                "packet-language-ack",
                PacketLanguageTerminalWorkerGameTest.ACK_DESTINATION,
                Component.literal("Structurally matched ACK routed by an ordinary timed trigger.")
                        .withStyle(ChatFormatting.GREEN)
        );
        puppet.captureContainerAt(
                "packet-language-responses",
                PacketLanguageTerminalWorkerGameTest.RESPONSE_DESTINATION,
                Component.literal("Both equal Response occurrences retained and routed.")
                        .withStyle(ChatFormatting.GREEN)
        );
    }
}
