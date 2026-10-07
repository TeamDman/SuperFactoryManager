package ca.teamdman.sfm.client.tooltip;

import ca.teamdman.sfm.common.event_bus.SFMSubscribeEvent;
import ca.teamdman.sfm.common.util.SFMDist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;

/** A palette closing or null-player connection transition is not the end of a player session. */
public final class SFMTooltipModeLifecycle {
    private SFMTooltipModeLifecycle() {}

    @SFMSubscribeEvent(SFMDist.CLIENT)
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        resetAfterLogout(SFMTooltipModeService.INSTANCE, event.getPlayer() != null);
    }

    /** Keep policy independently testable without manufacturing a native client/player instance. */
    static void resetAfterLogout(SFMTooltipModeService service, boolean hadPlayer) {
        if (hadPlayer) service.setMode(SFMTooltipModeService.Mode.AUTO);
    }
}
