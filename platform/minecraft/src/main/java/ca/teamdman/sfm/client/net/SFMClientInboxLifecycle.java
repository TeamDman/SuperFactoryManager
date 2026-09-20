package ca.teamdman.sfm.client.net;

import ca.teamdman.sfm.common.event_bus.SFMSubscribeEvent;
import ca.teamdman.sfm.common.util.SFMDist;
import net.minecraftforge.event.TickEvent;

/** Drops local inbox subscriptions after logout, dimension change, or LAN publication. */
public final class SFMClientInboxLifecycle {
    private SFMClientInboxLifecycle() {
    }

    @SFMSubscribeEvent(SFMDist.CLIENT)
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            SFMClientInboxTransport.observeCurrentWorld();
        }
    }
}
