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
        if (intent instanceof SFMWorkspacePanelIntent.OpenAsTab open) {
            SFMScreenPanel panel = open.panel();
            SFMWorkspacePanelId inserted = layout.pushToStack(source, panel, open.metadata());
            return new Outcome(SFMWorkspacePanelIntentResult.APPLIED, inserted, null, null, false);
        }
        if (intent instanceof SFMWorkspacePanelIntent.OpenToSide open) {
            SFMWorkspacePanelId inserted = layout.insert(source, open.side(), open.panel(), open.metadata());
            return new Outcome(SFMWorkspacePanelIntentResult.APPLIED, inserted, null, null, false);
        }
        if (intent instanceof SFMWorkspacePanelIntent.Move move) {
            return layout.move(source, move.side())
                    ? new Outcome(SFMWorkspacePanelIntentResult.APPLIED, null, null, null, true)
                    : Outcome.unavailable();
        }
        if (intent instanceof SFMWorkspacePanelIntent.MoveToStack move) {
            return layout.moveToStack(source, move.destination())
                    ? new Outcome(SFMWorkspacePanelIntentResult.APPLIED, null, null, null, true)
                    : Outcome.unavailable();
        }
        layout.remove(source);
        return new Outcome(SFMWorkspacePanelIntentResult.APPLIED, null, source, sourcePanel, false);
    }

    public record Outcome(
            SFMWorkspacePanelIntentResult result,
            @Nullable SFMWorkspacePanelId inserted,
            @Nullable SFMWorkspacePanelId removed,
            @Nullable SFMScreenPanel removedPanel,
            boolean moved
    ) {
        private static Outcome unavailable() {
            return new Outcome(SFMWorkspacePanelIntentResult.UNAVAILABLE, null, null, null, false);
        }

        private static Outcome unsupported() {
            return new Outcome(SFMWorkspacePanelIntentResult.UNSUPPORTED, null, null, null, false);
        }
    }
}
