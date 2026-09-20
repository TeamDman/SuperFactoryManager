package ca.teamdman.sfm.client.net;

import ca.teamdman.sfm.common.event_bus.SFMSubscribeEvent;
import ca.teamdman.sfm.common.util.SFMDist;
import net.minecraft.client.Minecraft;
import net.minecraftforge.event.TickEvent;

/** Clears or rotates the observation session as integrated servers change. */
public final class SFMPacketObservationLifecycle {
    private SFMPacketObservationLifecycle() {
    }

    @SFMSubscribeEvent(value = SFMDist.CLIENT)
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        SFMPacketObservationRuntime.get().observeSessionIdentity(
                Minecraft.getInstance().getSingleplayerServer()
        );
    }
}
