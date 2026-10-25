package ca.teamdman.sfm.client.control;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.event_bus.SFMSubscribeEvent;
import ca.teamdman.sfm.common.util.SFMDist;
import net.minecraft.client.Minecraft;
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1" %}
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.client.event.ScreenEvent;
{% when "1.20.2", "1.20.3", "1.20.4" %}
import net.neoforged.neoforge.event.TickEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
{% else %}
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
{% endcase %}

import java.io.IOException;
import java.nio.file.Path;

/** Client-lifetime services; startup configuration never chooses a puppet or opens a screen. */
public final class SFMClientControlLifecycle {
    private static SFMClientActionRuntime runtime;
    private static boolean startupAttempted;

    private SFMClientControlLifecycle() { }

    public static synchronized SFMClientActionRuntime runtime() {
        if (runtime == null) {
            runtime = new SFMClientActionRuntime(Minecraft.getInstance());
            Runtime.getRuntime().addShutdownHook(new Thread(runtime::close, "sfm-action-services-shutdown"));
        }
        return runtime;
    }

    @SFMSubscribeEvent(value = SFMDist.CLIENT)
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4" %}
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
{% else %}
    public static void onClientTick(ClientTickEvent.Post event) {
{% endcase %}
        SFMClientActionRuntime services = runtime();
        if (!startupAttempted) {
            startupAttempted = true;
            try {
                SFMActionServiceSettings settings = SFMActionServiceSettings.fromEnvironment(System.getenv());
                if (settings.files()) {
                    try { services.enableFiles(); }
                    catch (Exception failure) { SFM.LOGGER.error("SFM_ACTION_FILES_START_FAILED", failure); }
                }
                if (settings.vox()) {
                    try { services.enableVox(); }
                    catch (Exception failure) { SFM.LOGGER.error("SFM_ACTION_VOX_START_FAILED", failure); }
                }
            } catch (IllegalArgumentException invalid) {
                SFM.LOGGER.error("SFM_ACTION_SERVICES_INVALID_ENVIRONMENT: {}", invalid.getMessage());
            }
        }
        services.tick();
    }

    @SFMSubscribeEvent(value = SFMDist.CLIENT)
    public static void onScreenOpening(ScreenEvent.Opening event) {
        runtime().sceneChanging();
    }

    /** Compatibility entry point: enabling files never enables Vox. */
    public static Path enableFileControl() throws IOException { return runtime().enableFiles(); }
}
