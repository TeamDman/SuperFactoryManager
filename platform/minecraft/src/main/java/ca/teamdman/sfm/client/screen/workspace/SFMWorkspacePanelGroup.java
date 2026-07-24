package ca.teamdman.sfm.client.screen.workspace;

import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.function.BiFunction;

/**
 * Typed, atomically-opened group of model-sharing panels.
 *
 * <p>The policy receives logical GUI bounds. It may change the tree shape, but
 * must return the same panel instances so layout identities and panel state are
 * retained across responsive transitions.</p>
 */
public final class SFMWorkspacePanelGroup {
    private final BiFunction<SFMScreenPanelBounds, @Nullable SFMScreenPanel, SFMWorkspaceLayout.LayoutSpec> policy;
    private @Nullable SFMScreenPanel maximized;
    private long revision;

    public SFMWorkspacePanelGroup(
            BiFunction<SFMScreenPanelBounds, @Nullable SFMScreenPanel, SFMWorkspaceLayout.LayoutSpec> policy
    ) {
        this.policy = Objects.requireNonNull(policy);
    }

    public SFMWorkspaceLayout.LayoutSpec layout(SFMScreenPanelBounds bounds) {
        SFMWorkspaceLayout.LayoutSpec requested = Objects.requireNonNull(policy.apply(bounds, maximized));
        return maximized == null ? requested : SFMWorkspaceLayout.panel(maximized);
    }

    public void toggleMaximize(SFMScreenPanel panel) {
        maximized = maximized == panel ? null : Objects.requireNonNull(panel);
        revision++;
    }

    public void changed() {
        revision++;
    }

    public long revision() {
        return revision;
    }

    public boolean maximized() {
        return maximized != null;
    }
}
