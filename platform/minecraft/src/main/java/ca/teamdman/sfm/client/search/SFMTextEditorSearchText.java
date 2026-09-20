package ca.teamdman.sfm.client.search;

import ca.teamdman.sfm.common.localization.LocalizationEntry;
import ca.teamdman.sfm.common.localization.SFMLocalizationDatagen;

public final class SFMTextEditorSearchText {
    private SFMTextEditorSearchText() { }
    @SFMLocalizationDatagen public static final LocalizationEntry QUERY = new LocalizationEntry(
            "gui.sfm.editor.search.query", "Set editor Find query");
    @SFMLocalizationDatagen public static final LocalizationEntry TOGGLE = new LocalizationEntry(
            "gui.sfm.editor.search.toggle", "Toggle editor Find option");
    @SFMLocalizationDatagen public static final LocalizationEntry SELECT = new LocalizationEntry(
            "gui.sfm.editor.search.select", "Select editor Find matches");
    @SFMLocalizationDatagen public static final LocalizationEntry REQUIRED = new LocalizationEntry(
            "gui.sfm.editor.search.required", "Focus a Text Editor V3 panel");
    @SFMLocalizationDatagen public static final LocalizationEntry DESCRIPTION = new LocalizationEntry(
            "gui.sfm.editor.search.description", "Find exact glyph surfaces in this document. Empty query uses the primary selected text literally.");
    @SFMLocalizationDatagen public static final LocalizationEntry PENDING = new LocalizationEntry(
            "gui.sfm.editor.search.pending", "Finding document matches…");
    @SFMLocalizationDatagen public static final LocalizationEntry STALE = new LocalizationEntry(
            "gui.sfm.editor.search.stale", "Find selection ignored: the document or selection changed");
    @SFMLocalizationDatagen public static final LocalizationEntry FAILED = new LocalizationEntry(
            "gui.sfm.editor.search.failed", "Find selection unchanged: %s");
    @SFMLocalizationDatagen public static final LocalizationEntry APPLIED = new LocalizationEntry(
            "gui.sfm.editor.search.applied", "%s ranges selected; %s matches in this document");
    @SFMLocalizationDatagen public static final LocalizationEntry ADD_NEXT = new LocalizationEntry(
            "gui.sfm.editor.search.add_next", "Add next occurrence to selection");
    @SFMLocalizationDatagen public static final LocalizationEntry ALL = new LocalizationEntry(
            "gui.sfm.editor.search.all", "Select all occurrences");
    @SFMLocalizationDatagen public static final LocalizationEntry EDIT_FAILED = new LocalizationEntry(
            "gui.sfm.editor.search.edit_failed", "Selected ranges unchanged: %s");
}
