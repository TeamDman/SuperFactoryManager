package ca.teamdman.sfm.client.text_editor;

/** Typed input supplied when an editor implementation is embedded in a panel. */
public record SFMTextEditorPanelOpenContext(
        String editorId,
        SFMTextDocumentSnapshot document,
        boolean readOnly,
        String title,
        SFMTextDocumentSaveHandler saveHandler
) {
    public SFMTextEditorPanelOpenContext {
        if (editorId == null || editorId.isBlank()) throw new IllegalArgumentException("editorId must not be blank");
        if (document == null) throw new IllegalArgumentException("document must not be null");
        readOnly = readOnly || document.readOnly();
        if (title == null || title.isBlank()) throw new IllegalArgumentException("title must not be blank");
        if (saveHandler == null) throw new IllegalArgumentException("saveHandler must not be null");
    }

    public String initialValue() {
        return document.displayText();
    }

    public SFMTextEditorPanelOpenContext(
            String editorId,
            String initialValue,
            boolean readOnly,
            String title,
            SFMTextDocumentSaveHandler saveHandler
    ) {
        this(editorId, literalSnapshot(initialValue), readOnly, title, saveHandler);
    }

    public SFMTextEditorPanelOpenContext(
            String editorId,
            String initialValue,
            boolean readOnly,
            String title
    ) {
        this(editorId, literalSnapshot(initialValue), readOnly, title,
                SFMTextDocumentSaveHandler.discard());
    }

    private static SFMTextDocumentSnapshot literalSnapshot(String initialValue) {
        if (initialValue == null) throw new IllegalArgumentException("initialValue must not be null");
        return SFMTextDocumentSnapshot.literal(initialValue);
    }
}
