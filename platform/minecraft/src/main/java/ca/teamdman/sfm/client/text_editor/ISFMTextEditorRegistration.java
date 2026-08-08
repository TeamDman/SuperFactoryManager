package ca.teamdman.sfm.client.text_editor;

import ca.teamdman.sfm.client.screen.text_editor.ISFMTextEditScreen;
import ca.teamdman.sfm.client.screen.text_editor.SFMTextEditorPanel;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenPanel;

public interface ISFMTextEditorRegistration {

    /** Whether this editor enforces {@code readOnly=true} throughout its UI. */
    default boolean supportsReadOnlyPanel() {
        return false;
    }

    /**
     * Create, but do not display, an editor screen for the given context.
     */
    ISFMTextEditScreen createScreen(ISFMTextEditScreenOpenContext context);

    /** Create a panel-capable projection of this editor implementation. */
    default SFMScreenPanel createPanel(SFMTextEditorPanelOpenContext context) {
        return SFMTextEditorPanel.legacy(context, createScreen(new ISFMTextEditScreenOpenContext() {
            @Override public String initialValue() { return context.initialValue(); }
            @Override public boolean readOnly() { return context.readOnly(); }
            @Override public java.util.function.Consumer<String> saveWriter() { return ignored -> { }; }
            @Override public ca.teamdman.sfm.common.label.LabelPositionHolder labelPositionHolder() {
                return ca.teamdman.sfm.common.label.LabelPositionHolder.empty();
            }
        }).asScreen());
    }
}
