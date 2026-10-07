package ca.teamdman.sfm.client.text_editor;

import ca.teamdman.sfm.client.screen.text_editor.ISFMTextEditScreen;
{% if features.editor_document_panels %}
import ca.teamdman.sfm.client.screen.text_editor.SFMTextEditorPanel;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenPanel;
{% endif %}

public interface ISFMTextEditorRegistration {

{% if features.editor_document_panels %}
    /** Whether this editor enforces {@code readOnly=true} throughout its UI. */
    default boolean supportsReadOnlyPanel() {
        return false;
    }

{% endif %}
    /**
     * Create, but do not display, an editor screen for the given context.
     */
    ISFMTextEditScreen createScreen(ISFMTextEditScreenOpenContext context);
{% if features.editor_document_panels %}

    /** Create a panel-capable projection of this editor implementation. */
    default SFMScreenPanel createPanel(SFMTextEditorPanelOpenContext context) {
        return SFMTextEditorPanel.legacy(context, this::createScreen);
    }
{% endif %}
}
