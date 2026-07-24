package ca.teamdman.sfm.client.screen.workspace.diagnostic;

import ca.teamdman.sfm.client.screen.workspace.SFMScreenMultiplexer;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspaceLayout;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspaceSide;
import net.minecraft.client.gui.screens.Screen;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/** Reviewed panel-tree fixtures for proving calibration in common allocation shapes. */
public final class SFMViewportCalibrationWorkspace {
    private SFMViewportCalibrationWorkspace() {
    }

    public static SFMScreenMultiplexer create(@Nullable Screen previous, Allocation allocation) {
        return create(previous, allocation, SFMViewportDiagnosticsSource.minecraftCurrent("diagnostic"));
    }

    /** Track A can inject authoritative requested/actual variant measurements through this seam. */
    public static SFMScreenMultiplexer create(
            @Nullable Screen previous,
            Allocation allocation,
            SFMViewportDiagnosticsSource diagnosticsSource
    ) {
        return SFMScreenMultiplexer.create(previous, layout(allocation, diagnosticsSource));
    }

    public static SFMWorkspaceLayout layout(Allocation allocation) {
        return layout(allocation, SFMViewportDiagnosticsSource.minecraftCurrent("diagnostic"));
    }

    public static SFMWorkspaceLayout layout(
            Allocation allocation,
            SFMViewportDiagnosticsSource diagnosticsSource
    ) {
        Objects.requireNonNull(allocation);
        Objects.requireNonNull(diagnosticsSource);
        return switch (allocation) {
            case FULL -> SFMWorkspaceLayout.single(panel("full screen", diagnosticsSource));
            case HALF -> SFMWorkspaceLayout.sideBySide(
                    panel("half left", diagnosticsSource),
                    panel("half right", diagnosticsSource));
            case THIRD -> thirds(diagnosticsSource);
            case NESTED -> nested(diagnosticsSource);
        };
    }

    private static SFMWorkspaceLayout thirds(SFMViewportDiagnosticsSource diagnosticsSource) {
        SFMWorkspaceLayout layout = SFMWorkspaceLayout.sideBySide(
                panel("third 1/3", diagnosticsSource),
                panel("third 2/3", diagnosticsSource));
        var second = layout.panels().get(1).id();
        layout.insert(second, SFMWorkspaceSide.RIGHT, panel("third 3/3", diagnosticsSource));
        for (var entry : layout.panels()) layout.configurePanel(entry.id(), 1.0, 1);
        return layout;
    }

    private static SFMWorkspaceLayout nested(SFMViewportDiagnosticsSource diagnosticsSource) {
        SFMWorkspaceLayout layout = SFMWorkspaceLayout.sideBySide(
                panel("nested left", diagnosticsSource),
                panel("nested top-right", diagnosticsSource));
        var right = layout.panels().get(1).id();
        layout.insert(right, SFMWorkspaceSide.BELOW, panel("nested bottom-right", diagnosticsSource));
        return layout;
    }

    private static SFMViewportCalibrationPanel panel(
            String label,
            SFMViewportDiagnosticsSource diagnosticsSource
    ) {
        return new SFMViewportCalibrationPanel(label, diagnosticsSource);
    }

    public enum Allocation {
        FULL,
        HALF,
        THIRD,
        NESTED
    }
}
