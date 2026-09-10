package ca.teamdman.sfm.client.text_editor;

/** Immutable per-editor settings; saving defaults must be an explicit operation. */
public record SFMTextEditorPointerSettings(boolean wheelZooms, boolean middlePans) {
    public static final SFMTextEditorPointerSettings DEFAULT = new SFMTextEditorPointerSettings(true, true);

    public int panButton() { return middlePans ? 2 : 1; }
    public int actionButton() { return middlePans ? 1 : 2; }
    public SFMTextEditorPointerSettings toggleWheel() {
        return new SFMTextEditorPointerSettings(!wheelZooms, middlePans);
    }
    public SFMTextEditorPointerSettings toggleButtons() {
        return new SFMTextEditorPointerSettings(wheelZooms, !middlePans);
    }
}
