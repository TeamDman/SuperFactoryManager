package ca.teamdman.sfm.gametest.puppet.action;

import ca.teamdman.sfm.gametest.puppet.ISFMGamePuppetRuntime;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppetHelper;
import net.minecraft.client.gui.screens.Overlay;

import java.util.Objects;

/**
 * Waits for one named overlay type to be absent without constraining unrelated overlays.
 */
public final class WaitForOverlayToNotBePresentPuppetAction implements SFMPuppetAction {
    private final Class<? extends Overlay> overlayType;

    private int ticks;

    public WaitForOverlayToNotBePresentPuppetAction(Class<? extends Overlay> overlayType) {
        this.overlayType = Objects.requireNonNull(overlayType, "overlayType");
    }

    @Override
    public String description() {
        return "wait for " + overlayType.getSimpleName() + " to not be present";
    }

    @Override
    public boolean tick(ISFMGamePuppetRuntime runtime) {
        if (!runtime.isOverlay(overlayType)) {
            return true;
        }
        if (++ticks > SFMGamePuppetHelper.SCREEN_TIMEOUT_TICKS) {
            throw new IllegalStateException("Timed out waiting for " + overlayType.getName() + " to not be present");
        }
        return false;
    }
}
