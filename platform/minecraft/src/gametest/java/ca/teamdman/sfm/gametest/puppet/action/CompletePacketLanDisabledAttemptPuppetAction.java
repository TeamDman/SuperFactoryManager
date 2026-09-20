package ca.teamdman.sfm.gametest.puppet.action;

import ca.teamdman.sfm.gametest.puppet.ISFMGamePuppetRuntime;
import ca.teamdman.sfm.gametest.tests.general.PacketLanDisabledGameTest;

/** Releases the server fixture after the terminal has exposed its disabled result. */
public final class CompletePacketLanDisabledAttemptPuppetAction implements SFMPuppetAction {
    @Override
    public String description() {
        return "complete the LAN-disabled terminal attempt";
    }

    @Override
    public boolean tick(ISFMGamePuppetRuntime runtime) {
        PacketLanDisabledGameTest.markTerminalAttemptComplete();
        return true;
    }
}
