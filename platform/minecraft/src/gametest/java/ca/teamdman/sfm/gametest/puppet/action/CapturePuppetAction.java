package ca.teamdman.sfm.gametest.puppet.action;

import ca.teamdman.sfm.gametest.puppet.ISFMGamePuppetRuntime;
import net.minecraft.network.chat.Component;

public record CapturePuppetAction(
        String captureName,

        Component caption
) implements SFMPuppetAction {
    @Override
    public String description() {

        return "capture " + captureName;
    }

    @Override
    public boolean tick(ISFMGamePuppetRuntime runtime) {

        return runtime.capture(captureName, caption);
    }

}
