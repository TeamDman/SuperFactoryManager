package ca.teamdman.sfm.client.overlay;

import ca.teamdman.sfm.client.overlay.scene.SFMClientOverlayRuntime;
import ca.teamdman.sfm.common.event_bus.SFMSubscribeEvent;
import ca.teamdman.sfm.common.util.SFMDist;
import net.minecraft.client.Minecraft;
import net.minecraftforge.event.TickEvent;

/** Client-thread lifecycle reconciliation for scene content and focus cleanup. */
public final class SFMClientOverlayLifecycle {
    private SFMClientOverlayLifecycle() {
    }

    @SFMSubscribeEvent(value = SFMDist.CLIENT)
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) SFMClientOverlayRuntime.get().tick(Minecraft.getInstance());
    }
}
