package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.client.screen.SFMCommandPaletteScreen;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenMultiplexer;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

final class PanelActionSupport {
    private PanelActionSupport() {
    }

    static SFMClientActionAvailability<SFMScreenMultiplexer> resolve(SFMClientActionContext context) {
        if (!context.originatingHostIsCurrent().getAsBoolean()) {
            return SFMClientActionAvailability.unavailable(SFMClientActionContext.ORIGINATING_HOST_CHANGED.getComponent());
        }
        if (!(context.originatingHost() instanceof SFMScreenMultiplexer workspace)) {
            return SFMClientActionAvailability.unavailable(
                    Component.literal("Panel actions require an SFM panel workspace"));
        }
        return SFMClientActionAvailability.available(workspace);
    }

    static int closePaletteAfter(int result) {
        if (result > 0 && Minecraft.getInstance().screen instanceof SFMCommandPaletteScreen palette) {
            palette.onClose();
        }
        return result;
    }
}
