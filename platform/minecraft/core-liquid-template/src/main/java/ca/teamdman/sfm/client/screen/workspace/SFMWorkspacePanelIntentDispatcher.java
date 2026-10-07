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
{% if features.workspace_stack_controls %}
        if (intent instanceof SFMWorkspacePanelIntent.OpenAsTab open) {
            SFMScreenPanel panel = open.panel();
            SFMWorkspacePanelId inserted = layout.pushToStack(source, panel, open.metadata());
            return new Outcome(SFMWorkspacePanelIntentResult.APPLIED, inserted, null, null, false);
        }
{% else %}
        if (intent instanceof SFMWorkspacePanelIntent.OpenAsTab) return Outcome.unsupported();
{% endif %}
        if (intent instanceof SFMWorkspacePanelIntent.OpenToSide open) {
{% if features.workspace_panel_metadata %}
            SFMWorkspacePanelId inserted = layout.insert(source, open.side(), open.panel(), open.metadata());
{% else %}
            SFMWorkspacePanelId inserted = layout.insert(source, open.side(), open.panel());
{% endif %}
{% if features.workspace_stack_controls %}
            return new Outcome(SFMWorkspacePanelIntentResult.APPLIED, inserted, null, null, false);
{% else %}
            return new Outcome(SFMWorkspacePanelIntentResult.APPLIED, inserted, null, null);
{% endif %}
        }
{% if features.workspace_stack_controls %}
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
{% endif %}
        layout.remove(source);
{% if features.workspace_stack_controls %}
        return new Outcome(SFMWorkspacePanelIntentResult.APPLIED, null, source, sourcePanel, false);
{% else %}
        return new Outcome(SFMWorkspacePanelIntentResult.APPLIED, null, source, sourcePanel);
{% endif %}
    }

    public record Outcome(
            SFMWorkspacePanelIntentResult result,
            @Nullable SFMWorkspacePanelId inserted,
            @Nullable SFMWorkspacePanelId removed,
{% if features.workspace_stack_controls %}
            @Nullable SFMScreenPanel removedPanel,
            boolean moved
{% else %}
            @Nullable SFMScreenPanel removedPanel
{% endif %}
    ) {
        private static Outcome unavailable() {
{% if features.workspace_stack_controls %}
            return new Outcome(SFMWorkspacePanelIntentResult.UNAVAILABLE, null, null, null, false);
{% else %}
            return new Outcome(SFMWorkspacePanelIntentResult.UNAVAILABLE, null, null, null);
{% endif %}
        }

        private static Outcome unsupported() {
{% if features.workspace_stack_controls %}
            return new Outcome(SFMWorkspacePanelIntentResult.UNSUPPORTED, null, null, null, false);
{% else %}
            return new Outcome(SFMWorkspacePanelIntentResult.UNSUPPORTED, null, null, null);
{% endif %}
        }
    }
}
