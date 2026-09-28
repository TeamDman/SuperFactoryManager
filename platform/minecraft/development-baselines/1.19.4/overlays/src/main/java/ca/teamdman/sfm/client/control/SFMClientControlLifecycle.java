package ca.teamdman.sfm.client.control;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.event_bus.SFMSubscribeEvent;
import ca.teamdman.sfm.common.util.SFMDist;
import net.minecraft.client.Minecraft;
import net.minecraftforge.event.TickEvent;

/** Owns the live-game control endpoint for the lifetime of the Minecraft client. */
public final class SFMClientControlLifecycle {
    private static SFMClientControlServer server;
    private static boolean startupAttempted;

    private SFMClientControlLifecycle() {
    }

    @SFMSubscribeEvent(value = SFMDist.CLIENT)
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        if (!startupAttempted) {
            startupAttempted = true;
            try {
                server = SFMClientControlServer.start(Minecraft.getInstance());
                Runtime.getRuntime().addShutdownHook(new Thread(
                        SFMClientControlLifecycle::stop,
                        "sfm-client-control-shutdown"
                ));
            } catch (Exception exception) {
                SFM.LOGGER.error("SFM_CLIENT_CONTROL_START_FAILED", exception);
            }
        }

        if (server != null) {
            server.observeClientTick();
        }
    }

    private static synchronized void stop() {
        if (server == null) {
            return;
        }
        server.close();
        server = null;
    }
}
