package ca.teamdman.sfm.client.screen.workspace.diagnostic;

import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
import net.minecraft.client.Minecraft;

import java.util.Objects;

/** Runtime boundary for obtaining the logical size shown by a display panel. */
@FunctionalInterface
public interface SFMSizeDisplayDimensionsSource {
    SFMSizeDisplayDimensions snapshot(Minecraft minecraft);

    static SFMSizeDisplayDimensionsSource minecraftLogicalSize() {
        return SFMSizeDisplayDimensionsSource::fromMinecraft;
    }

    @MCVersionDependentBehaviour
    private static SFMSizeDisplayDimensions fromMinecraft(Minecraft minecraft) {
        Objects.requireNonNull(minecraft);
        return new SFMSizeDisplayDimensions(
                minecraft.getWindow().getGuiScaledWidth(),
                minecraft.getWindow().getGuiScaledHeight()
        );
    }
}
