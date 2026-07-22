package ca.teamdman.sfm.client.screen.workspace.diagnostic;

import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
import net.minecraft.client.Minecraft;

import java.util.Objects;

/** Narrow runtime boundary so the panel can be rendered and tested without owning window mutation. */
@FunctionalInterface
public interface SFMViewportDiagnosticsSource {
    SFMViewportDiagnostics snapshot(Minecraft minecraft);

    static SFMViewportDiagnosticsSource minecraftCurrent(String responsiveMode) {
        Objects.requireNonNull(responsiveMode);
        return minecraft -> fromMinecraft(minecraft, responsiveMode);
    }

    @MCVersionDependentBehaviour
    private static SFMViewportDiagnostics fromMinecraft(Minecraft minecraft, String responsiveMode) {
        var window = minecraft.getWindow();
        int requestedScale = minecraft.options.guiScale().get();
        return new SFMViewportDiagnostics(
                window.getScreenWidth(),
                window.getScreenHeight(),
                window.getWidth(),
                window.getHeight(),
                window.getGuiScaledWidth(),
                window.getGuiScaledHeight(),
                requestedScale == 0 ? "Auto" : Integer.toString(requestedScale),
                window.getGuiScale(),
                responsiveMode
        );
    }
}
