package ca.teamdman.sfm.client.screen.workspace.diagnostic;

import ca.teamdman.sfm.client.screen.workspace.SFMScreenMultiplexer;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspaceLayout;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspaceSide;
import net.minecraft.client.gui.screens.Screen;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/** Reusable split fixtures showing allocated regions with distinct solid colours. */
public final class SFMSizeDisplayWorkspace {
    private static final int BLUE = 0xFF2563EB;
    private static final int ORANGE = 0xFFF97316;
    private static final int GREEN = 0xFF16A34A;
    private static final int PURPLE = 0xFF9333EA;
    private static final int TEAL = 0xFF0F766E;

    private SFMSizeDisplayWorkspace() {
    }

    public static SFMScreenMultiplexer create(@Nullable Screen previous, Allocation allocation) {
        return create(previous, allocation, SFMSizeDisplayDimensionsSource.minecraftLogicalSize());
    }

    /** Allows puppet fixtures and pure tests to supply a stable logical-size source. */
    public static SFMScreenMultiplexer create(
            @Nullable Screen previous,
            Allocation allocation,
            SFMSizeDisplayDimensionsSource dimensionsSource
    ) {
        return SFMScreenMultiplexer.create(previous, layout(allocation, dimensionsSource));
    }

    public static SFMWorkspaceLayout layout(Allocation allocation) {
        return layout(allocation, SFMSizeDisplayDimensionsSource.minecraftLogicalSize());
    }

    public static SFMWorkspaceLayout layout(
            Allocation allocation,
            SFMSizeDisplayDimensionsSource dimensionsSource
    ) {
        Objects.requireNonNull(allocation);
        Objects.requireNonNull(dimensionsSource);
        return switch (allocation) {
            case FULL -> SFMWorkspaceLayout.single(panel("full", BLUE, dimensionsSource));
            case HALF -> SFMWorkspaceLayout.sideBySide(
                    panel("half left", BLUE, dimensionsSource),
                    panel("half right", ORANGE, dimensionsSource));
            case THIRD -> thirds(dimensionsSource);
            case NESTED -> nested(dimensionsSource);
        };
    }

    private static SFMWorkspaceLayout thirds(SFMSizeDisplayDimensionsSource dimensionsSource) {
        SFMWorkspaceLayout layout = SFMWorkspaceLayout.sideBySide(
                panel("third left", BLUE, dimensionsSource),
                panel("third middle", GREEN, dimensionsSource));
        var second = layout.panels().get(1).id();
        layout.insert(second, SFMWorkspaceSide.RIGHT, panel("third right", ORANGE, dimensionsSource));
        for (var entry : layout.panels()) layout.configurePanel(entry.id(), 1.0, 1);
        return layout;
    }

    private static SFMWorkspaceLayout nested(SFMSizeDisplayDimensionsSource dimensionsSource) {
        SFMWorkspaceLayout layout = SFMWorkspaceLayout.sideBySide(
                panel("nested left", PURPLE, dimensionsSource),
                panel("nested top right", TEAL, dimensionsSource));
        var right = layout.panels().get(1).id();
        layout.insert(right, SFMWorkspaceSide.BELOW, panel("nested bottom right", ORANGE, dimensionsSource));
        return layout;
    }

    private static SFMSizeDisplayPanel panel(
            String label,
            int backgroundColour,
            SFMSizeDisplayDimensionsSource dimensionsSource
    ) {
        return new SFMSizeDisplayPanel(label, backgroundColour, dimensionsSource);
    }

    public enum Allocation {
        FULL,
        HALF,
        THIRD,
        NESTED
    }
}
