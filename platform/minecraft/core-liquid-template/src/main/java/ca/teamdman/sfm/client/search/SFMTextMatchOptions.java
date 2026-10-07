package ca.teamdman.sfm.client.search;

import java.util.Objects;

/** Predicate options shared by Find and Filter. Highlight visibility belongs to the view. */
public record SFMTextMatchOptions(Mode mode, boolean matchCase, boolean wholeWord, boolean dotAll) {
    public enum Mode { LITERAL, FUZZY, REGEX }
    public SFMTextMatchOptions { Objects.requireNonNull(mode, "mode"); }
    public static SFMTextMatchOptions defaults() { return new SFMTextMatchOptions(Mode.LITERAL, false, false, false); }
    public static SFMTextMatchOptions legacyFuzzy() { return new SFMTextMatchOptions(Mode.FUZZY, false, false, false); }
    public SFMTextMatchOptions withMode(Mode mode) { return new SFMTextMatchOptions(mode, matchCase, wholeWord, dotAll); }
    public SFMTextMatchOptions toggleFuzzy() { return withMode(mode == Mode.FUZZY ? Mode.LITERAL : Mode.FUZZY); }
    public SFMTextMatchOptions toggleRegex() { return withMode(mode == Mode.REGEX ? Mode.LITERAL : Mode.REGEX); }
    public SFMTextMatchOptions withCase(boolean value) { return new SFMTextMatchOptions(mode, value, wholeWord, dotAll); }
    public SFMTextMatchOptions withWholeWord(boolean value) { return new SFMTextMatchOptions(mode, matchCase, value, dotAll); }
    public SFMTextMatchOptions withDotAll(boolean value) { return new SFMTextMatchOptions(mode, matchCase, wholeWord, value); }
}
