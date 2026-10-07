package ca.teamdman.sfm.client.search;

import ca.teamdman.sfm.common.localization.LocalizationEntry;
import ca.teamdman.sfm.common.localization.SFMLocalizationDatagen;
import net.minecraft.locale.Language;

/** Shared language entries for Find/Filter and the new review source affordances. */
public final class SFMExplorerSearchText {
    @SFMLocalizationDatagen
    public static final LocalizationEntry FIND_PREFIX = new LocalizationEntry(
            "gui.sfm.explorer.search.find_prefix", "Find: ");
    @SFMLocalizationDatagen
    public static final LocalizationEntry FILTER_PREFIX = new LocalizationEntry(
            "gui.sfm.explorer.search.filter_prefix", "Filter: ");
    @SFMLocalizationDatagen
    public static final LocalizationEntry FIND_PLACEHOLDER = new LocalizationEntry(
            "gui.sfm.explorer.search.find_placeholder", "find without hiding");
    @SFMLocalizationDatagen
    public static final LocalizationEntry FILTER_PLACEHOLDER = new LocalizationEntry(
            "gui.sfm.explorer.search.filter_placeholder", "filter entries");
    @SFMLocalizationDatagen
    public static final LocalizationEntry CLEAR = new LocalizationEntry(
            "gui.sfm.explorer.search.clear", "Clear input");
    @SFMLocalizationDatagen
    public static final LocalizationEntry ADD_NEXT = new LocalizationEntry(
            "gui.sfm.explorer.search.add_next", "Add next unselected match (Alt+J)");
    @SFMLocalizationDatagen
    public static final LocalizationEntry SELECT_ALL = new LocalizationEntry(
            "gui.sfm.explorer.search.select_all", "Select all matches in this scope (Ctrl+Shift+Alt+J)");
    @SFMLocalizationDatagen
    public static final LocalizationEntry NEXT = new LocalizationEntry(
            "gui.sfm.explorer.search.next", "Next match");
    @SFMLocalizationDatagen
    public static final LocalizationEntry PREVIOUS = new LocalizationEntry(
            "gui.sfm.explorer.search.previous", "Previous match");
    @SFMLocalizationDatagen
    public static final LocalizationEntry NEXT_WRAPPING = new LocalizationEntry(
            "gui.sfm.explorer.search.next_wrapping", "Next match (wrapping)");
    @SFMLocalizationDatagen
    public static final LocalizationEntry PREVIOUS_WRAPPING = new LocalizationEntry(
            "gui.sfm.explorer.search.previous_wrapping", "Previous match (wrapping)");
    @SFMLocalizationDatagen
    public static final LocalizationEntry SCOPE_COMPLETE = new LocalizationEntry(
            "gui.sfm.explorer.search.scope_complete", "Find scope: complete domain");
    @SFMLocalizationDatagen
    public static final LocalizationEntry SCOPE_MATERIALIZED = new LocalizationEntry(
            "gui.sfm.explorer.search.scope_materialized", "Find scope: loaded entries only");
    @SFMLocalizationDatagen
    public static final LocalizationEntry CASE = new LocalizationEntry(
            "gui.sfm.explorer.search.case", "Match case");
    @SFMLocalizationDatagen
    public static final LocalizationEntry WHOLE_WORD = new LocalizationEntry(
            "gui.sfm.explorer.search.whole_word", "Match whole word");
    @SFMLocalizationDatagen
    public static final LocalizationEntry REGEX = new LocalizationEntry(
            "gui.sfm.explorer.search.regex", "Regular expression");
    @SFMLocalizationDatagen
    public static final LocalizationEntry FUZZY = new LocalizationEntry(
            "gui.sfm.explorer.search.fuzzy", "Fuzzy matching (Alt+F)");
    @SFMLocalizationDatagen
    public static final LocalizationEntry DOT_ALL = new LocalizationEntry(
            "gui.sfm.explorer.search.dot_all", "Dot-all (dot includes line breaks)");
    @SFMLocalizationDatagen
    public static final LocalizationEntry HIGHLIGHT = new LocalizationEntry(
            "gui.sfm.explorer.search.highlight", "Highlight matched glyphs (Alt+H)");
    @SFMLocalizationDatagen
    public static final LocalizationEntry ON = new LocalizationEntry(
            "gui.sfm.explorer.search.on", "on");
    @SFMLocalizationDatagen
    public static final LocalizationEntry OFF = new LocalizationEntry(
            "gui.sfm.explorer.search.off", "off");
    @SFMLocalizationDatagen
    public static final LocalizationEntry DISABLE = new LocalizationEntry(
            "gui.sfm.explorer.search.disable", "on → off");
    @SFMLocalizationDatagen
    public static final LocalizationEntry ENABLE = new LocalizationEntry(
            "gui.sfm.explorer.search.enable", "off → on");
    @SFMLocalizationDatagen
    public static final LocalizationEntry REGEX_ONLY = new LocalizationEntry(
            "gui.sfm.explorer.search.regex_only", "Available only in Regex mode");
    @SFMLocalizationDatagen
    public static final LocalizationEntry ACTIONS_FIND = new LocalizationEntry(
            "gui.sfm.explorer.search.actions_find", "Search actions: options, clear input, next/previous and scope");
    @SFMLocalizationDatagen
    public static final LocalizationEntry ACTIONS_FILTER = new LocalizationEntry(
            "gui.sfm.explorer.search.actions_filter", "Search actions: options and clear input");
    @SFMLocalizationDatagen
    public static final LocalizationEntry FILTER_HINT = new LocalizationEntry(
            "gui.sfm.explorer.search.filter_hint", "Filter · %s. Right-click for options and Clear input");
    @SFMLocalizationDatagen
    public static final LocalizationEntry LITERAL = new LocalizationEntry(
            "gui.sfm.explorer.search.literal", "literal");
    @SFMLocalizationDatagen
    public static final LocalizationEntry MODE_REGEX = new LocalizationEntry(
            "gui.sfm.explorer.search.mode_regex", "regex");
    @SFMLocalizationDatagen
    public static final LocalizationEntry MODE_FUZZY = new LocalizationEntry(
            "gui.sfm.explorer.search.mode_fuzzy", "fuzzy");
    @SFMLocalizationDatagen
    public static final LocalizationEntry ACTION_FOCUS = new LocalizationEntry(
            "gui.sfm.explorer.search.action_focus", "Focus Explorer search");
    @SFMLocalizationDatagen
    public static final LocalizationEntry ACTION_TOGGLE = new LocalizationEntry(
            "gui.sfm.explorer.search.action_toggle", "Toggle Explorer search option");
    @SFMLocalizationDatagen
    public static final LocalizationEntry ACTION_CLEAR = new LocalizationEntry(
            "gui.sfm.explorer.search.action_clear", "Clear Explorer search input");
    @SFMLocalizationDatagen
    public static final LocalizationEntry ACTION_MOVE = new LocalizationEntry(
            "gui.sfm.explorer.search.action_move", "Select Explorer find match");
    @SFMLocalizationDatagen
    public static final LocalizationEntry ACTION_SCOPE = new LocalizationEntry(
            "gui.sfm.explorer.search.action_scope", "Set Explorer find scope");
    @SFMLocalizationDatagen
    public static final LocalizationEntry ACTION_SELECT = new LocalizationEntry(
            "gui.sfm.explorer.search.action_select", "Select Explorer matches");
    @SFMLocalizationDatagen
    public static final LocalizationEntry ACTION_ROW = new LocalizationEntry(
            "gui.sfm.explorer.search.action_row", "Select an Explorer row");
    @SFMLocalizationDatagen
    public static final LocalizationEntry ACTION_DESCRIPTION = new LocalizationEntry(
            "gui.sfm.explorer.search.action_description", "Control Find and Filter in this exact Explorer panel");
    @SFMLocalizationDatagen
    public static final LocalizationEntry FOCUS_REQUIRED = new LocalizationEntry(
            "gui.sfm.explorer.search.focus_required", "Focus an Explorer panel first");
    @SFMLocalizationDatagen
    public static final LocalizationEntry FRESH_CHECKING = new LocalizationEntry(
            "gui.sfm.explorer.search.fresh_checking", "Checking for changes outside this review…");
    @SFMLocalizationDatagen
    public static final LocalizationEntry FRESH_CURRENT = new LocalizationEntry(
            "gui.sfm.explorer.search.fresh_current", "No newer source found at last check");
    @SFMLocalizationDatagen
    public static final LocalizationEntry FRESH_OUTDATED = new LocalizationEntry(
            "gui.sfm.explorer.search.fresh_outdated", "This pinned review excludes newer source changes");
    @SFMLocalizationDatagen
    public static final LocalizationEntry FRESH_UNKNOWN = new LocalizationEntry(
            "gui.sfm.explorer.search.fresh_unknown", "Review freshness unavailable — inspect details");
    @SFMLocalizationDatagen
    public static final LocalizationEntry NOT_CHECKED = new LocalizationEntry(
            "gui.sfm.explorer.search.not_checked", "not checked");
    @SFMLocalizationDatagen
    public static final LocalizationEntry CHECK_AGE = new LocalizationEntry(
            "gui.sfm.explorer.search.check_age", "checked %ss ago");
    @SFMLocalizationDatagen
    public static final LocalizationEntry CAPTURE_CREATE = new LocalizationEntry(
            "gui.sfm.explorer.search.capture_create", "Create working-tree review");
    @SFMLocalizationDatagen
    public static final LocalizationEntry CAPTURE_DESCRIPTION = new LocalizationEntry(
            "gui.sfm.explorer.search.capture_description", "Capture scoped disk bytes, including allowed untracked files, into a new portable review without committing");
    @SFMLocalizationDatagen
    public static final LocalizationEntry CAPTURE_BASELINE = new LocalizationEntry(
            "gui.sfm.explorer.search.capture_baseline", "Create working-tree review from this baseline… · %s · choose source scope");
    @SFMLocalizationDatagen
    public static final LocalizationEntry CAPTURE_LABEL = new LocalizationEntry(
            "gui.sfm.explorer.search.capture_label", "working tree %s · captured %s");
    @SFMLocalizationDatagen
    public static final LocalizationEntry FIND_DEFAULT = new LocalizationEntry(
            "gui.sfm.explorer.search.find_default", "Complete domain; Enter selects, Shift+Enter selects previous");
    @SFMLocalizationDatagen
    public static final LocalizationEntry PENDING = new LocalizationEntry(
            "gui.sfm.explorer.search.pending", "Search pending");
    @SFMLocalizationDatagen
    public static final LocalizationEntry FIND_CLEARED = new LocalizationEntry(
            "gui.sfm.explorer.search.find_cleared", "Find cleared; selection unchanged");
    @SFMLocalizationDatagen
    public static final LocalizationEntry SEARCHING = new LocalizationEntry(
            "gui.sfm.explorer.search.searching", "Searching %s…");
    @SFMLocalizationDatagen
    public static final LocalizationEntry COMPLETE = new LocalizationEntry(
            "gui.sfm.explorer.search.complete", "complete domain");
    @SFMLocalizationDatagen
    public static final LocalizationEntry MATERIALIZED = new LocalizationEntry(
            "gui.sfm.explorer.search.materialized", "materialized only");
    @SFMLocalizationDatagen
    public static final LocalizationEntry WITHIN_FILTER = new LocalizationEntry(
            "gui.sfm.explorer.search.within_filter", " · within Filter");
    @SFMLocalizationDatagen
    public static final LocalizationEntry INCOMPLETE = new LocalizationEntry(
            "gui.sfm.explorer.search.incomplete", " · INCOMPLETE");
    @SFMLocalizationDatagen
    public static final LocalizationEntry SOURCE_CHANGED = new LocalizationEntry(
            "gui.sfm.explorer.search.source_changed", "Find source changed; focus Find again to search the new roots");
    @SFMLocalizationDatagen
    public static final LocalizationEntry DOMAIN_UNAVAILABLE = new LocalizationEntry(
            "gui.sfm.explorer.search.domain_unavailable", "Complete-domain Find unavailable");
    @SFMLocalizationDatagen
    public static final LocalizationEntry TRY_MATERIALIZED = new LocalizationEntry(
            "gui.sfm.explorer.search.try_materialized", "; right-click Find to search materialized entries");
    @SFMLocalizationDatagen
    public static final LocalizationEntry MATCH_COUNT = new LocalizationEntry(
            "gui.sfm.explorer.search.match_count", "%s matches · %s");
    @SFMLocalizationDatagen
    public static final LocalizationEntry ENTER_QUERY = new LocalizationEntry(
            "gui.sfm.explorer.search.enter_query", "Enter a Find query before selecting matches");
    @SFMLocalizationDatagen
    public static final LocalizationEntry WAIT_COMPLETE = new LocalizationEntry(
            "gui.sfm.explorer.search.wait_complete", "Waiting for complete search before changing selection");
    @SFMLocalizationDatagen
    public static final LocalizationEntry SELECTION_INCOMPLETE = new LocalizationEntry(
            "gui.sfm.explorer.search.selection_incomplete", "Selection unchanged: search is incomplete or unavailable");
    @SFMLocalizationDatagen
    public static final LocalizationEntry NO_MATCHES = new LocalizationEntry(
            "gui.sfm.explorer.search.no_matches", "No matches; selection unchanged");
    @SFMLocalizationDatagen
    public static final LocalizationEntry SELECTED_ALL = new LocalizationEntry(
            "gui.sfm.explorer.search.selected_all", "Selected all %s matches · %s");
    @SFMLocalizationDatagen
    public static final LocalizationEntry ALREADY_SELECTED = new LocalizationEntry(
            "gui.sfm.explorer.search.already_selected", "All matching paths are already selected");
    @SFMLocalizationDatagen
    public static final LocalizationEntry NO_FURTHER = new LocalizationEntry(
            "gui.sfm.explorer.search.no_further", "No further match; selection unchanged");
    @SFMLocalizationDatagen
    public static final LocalizationEntry NO_FURTHER_KNOWN = new LocalizationEntry(
            "gui.sfm.explorer.search.no_further_known", "Search incomplete; no further known match");
    @SFMLocalizationDatagen
    public static final LocalizationEntry TARGET_OUTSIDE = new LocalizationEntry(
            "gui.sfm.explorer.search.target_outside", "Find target is outside the current roots");
    @SFMLocalizationDatagen
    public static final LocalizationEntry REVEAL_UNAVAILABLE = new LocalizationEntry(
            "gui.sfm.explorer.search.reveal_unavailable", "Find reveal unavailable: %s");
    @SFMLocalizationDatagen
    public static final LocalizationEntry ADDED = new LocalizationEntry(
            "gui.sfm.explorer.search.added", "Added match; %s paths selected");
    @SFMLocalizationDatagen
    public static final LocalizationEntry PAUSED = new LocalizationEntry(
            "gui.sfm.explorer.search.paused", " · navigation paused; focus Find to resume");

    /** Retain readable English in headless model tests and before client language loading. */
    public static String value(LocalizationEntry entry, Object... arguments) {
        String pattern = Language.getInstance().has(entry.key().get())
                ? Language.getInstance().getOrDefault(entry.key().get()) : entry.getStub();
        // These entries contain text-only printf placeholders. Format to text
        // here rather than passing unconstrained objects to TranslatableContents.
        return String.format(java.util.Locale.ROOT, pattern, arguments);
    }

    public static LocalizationEntry option(String option) {
        return switch (option) {
            case "case" -> CASE; case "whole-word" -> WHOLE_WORD; case "regex" -> REGEX;
            case "fuzzy" -> FUZZY; case "dot-all" -> DOT_ALL; case "highlight" -> HIGHLIGHT;
            default -> throw new IllegalArgumentException("Unknown search option: " + option);
        };
    }

    public static String mode(SFMTextMatchOptions options) {
        return value(switch (options.mode()) {
            case LITERAL -> LITERAL; case REGEX -> MODE_REGEX; case FUZZY -> MODE_FUZZY;
        });
    }
    private SFMExplorerSearchText() { }
}
