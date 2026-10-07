package ca.teamdman.sfm.gametest.puppet.definition;

import ca.teamdman.sfm.gametest.puppet.SFMGamePuppet;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppetHelper;
import ca.teamdman.sfm.gametest.puppet.action.InvokePacketLossThroughTerminalPuppetAction;
import ca.teamdman.sfm.gametest.tests.general.PacketTerminalLossGameTest;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/** Visible A4.2 proof that local send acceptance is not an inventory-delivery promise. */
@SFMGamePuppet(timeoutTicks = 20 * 3 * 60)
public final class InWorldPacketTerminalLossGamePuppet {
    private InWorldPacketTerminalLossGamePuppet() {
    }

    public static void run(SFMGamePuppetHelper puppet) {
        puppet.createFreshFlatWorld();
        PacketTerminalLossGameTest test = new PacketTerminalLossGameTest();
        puppet.startGameTest(test);
        puppet.waitForPacketObservation(PacketTerminalLossGameTest.FIXTURE_ID);

        puppet.openCommandPalette();
        puppet.executeCommandPalette("sfm action invoke sfm:panel/open sfm:terminal");
        puppet.waitTicks(20);
        puppet.startRustTerminalThroughUi();
        puppet.waitTicks(80);

        puppet.invokePacketLossThroughTerminal();
        puppet.waitTicks(100);
        puppet.writeTerminalContent("loss-command-output", null, null);
        puppet.waitForTerminalLine(InvokePacketLossThroughTerminalPuppetAction.DELIVERY_WITNESS);
        puppet.writeTerminalContent(
                "loss-attempt-count",
                InvokePacketLossThroughTerminalPuppetAction.ATTEMPTS_WITNESS,
                null
        );
        puppet.writeTerminalContent(
                "loss-local-status",
                InvokePacketLossThroughTerminalPuppetAction.STATUS_WITNESS,
                null
        );
        puppet.writeTerminalContent(
                "loss-delivery-contract",
                InvokePacketLossThroughTerminalPuppetAction.DELIVERY_WITNESS,
                null
        );
        puppet.capture(
                "loss-send-attempts",
                Component.literal("Best-effort boundary ")
                        .withStyle(ChatFormatting.GOLD)
                        .append(Component.literal(
                                "Four locally accepted sends report no delivery acknowledgement."
                        ))
        );

        puppet.closeScreenNaturally();
        puppet.waitForGameTest(test.testName());
        puppet.captureContainerAt(
                "loss-full-destination",
                PacketTerminalLossGameTest.FULL_DESTINATION,
                Component.literal("The full chest rejected the packet and did not replay it after capacity opened.")
                        .withStyle(ChatFormatting.GREEN)
        );
        puppet.captureContainerAt(
                "loss-ordering-barrier",
                PacketTerminalLossGameTest.BARRIER_DESTINATION,
                Component.literal("A final barrier packet proves every earlier lossy send was processed first.")
                        .withStyle(ChatFormatting.GREEN)
        );
    }
}
