package ca.teamdman.sfm.client.screen.workspace;

import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/** Applies host intents to the pure layout model; the Screen host owns lifecycle notifications. */
public final class SFMWorkspacePanelIntentDispatcher {
    private SFMWorkspacePanelIntentDispatcher() {
    }

    public static Outcome apply(
            SFMWorkspaceLayout layout,
            SFMWorkspacePanelId source,
            SFMWorkspacePanelIntent intent
    ) {
        Objects.requireNonNull(layout);
        Objects.requireNonNull(source);
        Objects.requireNonNull(intent);
        SFMScreenPanel sourcePanel = layout.panel(source);
        if (sourcePanel == null) return Outcome.unavailable();
        if (intent instanceof SFMWorkspacePanelIntent.OpenAsTab) return Outcome.unsupported();
        if (intent instanceof SFMWorkspacePanelIntent.OpenToSide open) {
            SFMWorkspacePanelId inserted = layout.insert(source, open.side(), open.panel());
            return new Outcome(SFMWorkspacePanelIntentResult.APPLIED, inserted, null, null);
        }
        layout.remove(source);
        return new Outcome(SFMWorkspacePanelIntentResult.APPLIED, null, source, sourcePanel);
    }

    public record Outcome(
            SFMWorkspacePanelIntentResult result,
            @Nullable SFMWorkspacePanelId inserted,
            @Nullable SFMWorkspacePanelId removed,
            @Nullable SFMScreenPanel removedPanel
    ) {
        private static Outcome unavailable() {
            return new Outcome(SFMWorkspacePanelIntentResult.UNAVAILABLE, null, null, null);
        }

        private static Outcome unsupported() {
            return new Outcome(SFMWorkspacePanelIntentResult.UNSUPPORTED, null, null, null);
        }
    }
}
