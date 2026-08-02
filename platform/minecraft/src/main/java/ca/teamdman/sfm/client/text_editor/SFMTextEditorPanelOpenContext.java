package ca.teamdman.sfm.client.text_editor;

/** Typed input supplied when an editor implementation is embedded in a panel. */
public record SFMTextEditorPanelOpenContext(
        String editorId,
        String initialValue,
        boolean readOnly,
        String title
) {
    public SFMTextEditorPanelOpenContext {
        if (editorId == null || editorId.isBlank()) throw new IllegalArgumentException("editorId must not be blank");
        if (initialValue == null) throw new IllegalArgumentException("initialValue must not be null");
        if (title == null || title.isBlank()) throw new IllegalArgumentException("title must not be blank");
    }
}
