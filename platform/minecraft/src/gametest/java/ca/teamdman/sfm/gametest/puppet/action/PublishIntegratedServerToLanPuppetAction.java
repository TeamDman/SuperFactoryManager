package ca.teamdman.sfm.gametest.puppet.action;

import ca.teamdman.sfm.gametest.puppet.ISFMGamePuppetRuntime;

/** Performs and observes the integrated server's real LAN-publication transition. */
public final class PublishIntegratedServerToLanPuppetAction implements SFMPuppetAction {
    @Override
    public String description() {
        return "publish the integrated server to LAN";
    }

    @Override
    public boolean tick(ISFMGamePuppetRuntime runtime) {
        return runtime.publishIntegratedServerToLan();
    }
}
