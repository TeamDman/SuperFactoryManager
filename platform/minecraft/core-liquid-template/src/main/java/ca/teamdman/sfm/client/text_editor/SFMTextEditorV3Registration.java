package ca.teamdman.sfm.client.text_editor;

import ca.teamdman.sfm.client.screen.SFMScreenChangeHelpers;
import ca.teamdman.sfm.client.screen.SFMTextEditorV3Screen;
import ca.teamdman.sfm.client.screen.text_editor.ISFMTextEditScreen;
{% if features.editor_document_panels %}
import ca.teamdman.sfm.client.screen.text_editor.SFMTextEditorPanel;
{% endif %}
{% if features.editor_document_panels %}
import ca.teamdman.sfm.client.screen.workspace.SFMScreenPanel;
{% endif %}

/** Registration for the panel-capable Text Editor v3 implementation. */
public final class SFMTextEditorV3Registration implements ISFMTextEditorRegistration {
{% if features.editor_document_panels %}
    @Override
    public boolean supportsReadOnlyPanel() {
        return true;
    }

{% endif %}
    @Override
    public ISFMTextEditScreen createScreen(ISFMTextEditScreenOpenContext context) {
        return new SFMTextEditorV3Screen(
                context,
                SFMScreenChangeHelpers.getCurrentScreen(),
{% if features.editor_overlay_push %}
                context.preferPush()
{% else %}
                false
{% endif %}
        );
    }

{% if features.editor_document_panels %}
    @Override
    public SFMScreenPanel createPanel(SFMTextEditorPanelOpenContext context) {
        return SFMTextEditorPanel.textEditorV3(context);
    }
{% endif %}
}
