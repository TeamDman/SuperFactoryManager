package ca.teamdman.sfm.client.text_editor;

public enum SFMTextEditorIntellisenseLevel {
    OFF,
    BASIC,
    ADVANCED,
    ;

    public boolean isResourceIntellisenseEnabled() {
        return this == ADVANCED;
    }

    public boolean isDisabled() {
        return this == OFF;
    }
}
