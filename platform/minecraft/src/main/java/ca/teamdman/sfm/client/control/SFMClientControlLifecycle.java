package ca.teamdman.sfm.client.control;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.event_bus.SFMSubscribeEvent;
import ca.teamdman.sfm.common.util.SFMDist;
import net.minecraft.client.Minecraft;
import net.minecraftforge.event.TickEvent;

import java.io.IOException;
import java.nio.file.Path;

/** Owns the live-game control endpoint for the lifetime of the Minecraft client. */
public final class SFMClientControlLifecycle {
    private static SFMClientControlServer server;
    private static boolean startupAttempted;
    private static boolean shutdownHookRegistered;

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
                startServerIfNecessary();
            } catch (Exception exception) {
                SFM.LOGGER.error("SFM_CLIENT_CONTROL_START_FAILED", exception);
            }
        }

        if (server != null) {
            server.observeClientTick();
        }
    }

    /** Enables file-driven control for the current client without a relaunch. */
    public static synchronized Path enableFileControl() throws IOException {
        startServerIfNecessary();
        startupAttempted = true;
        return server.enableFileControl();
    }

    private static synchronized void startServerIfNecessary() throws IOException {
        SFMClientControlLogBuffer.install();
        if (server != null) {
            return;
        }
        if (!shutdownHookRegistered) {
            Runtime.getRuntime().addShutdownHook(new Thread(
                    SFMClientControlLifecycle::stop,
                    "sfm-client-control-shutdown"
            ));
            shutdownHookRegistered = true;
        }
        server = SFMClientControlServer.start(Minecraft.getInstance());
    }

    private static synchronized void stop() {
        if (server == null) {
            return;
        }
        server.close();
        server = null;
    }
}
