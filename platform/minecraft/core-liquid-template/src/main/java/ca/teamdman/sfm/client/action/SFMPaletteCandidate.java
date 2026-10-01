package ca.teamdman.sfm.client.action;

import com.mojang.brigadier.context.StringRange;
import com.mojang.brigadier.suggestion.Suggestion;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;

/**
 * Typed, bounded palette candidate shared by the current one-shot palette and
 * the later streaming accumulator. Brigadier still owns parsing and the exact
 * replacement range; this record adds presentation and insertion semantics.
 */
public record SFMPaletteCandidate(
        Suggestion suggestion,
        String displayText,
        Kind kind,
        Origin origin,
        @Nullable ResourceLocation actionId,
        String completionFrontier,
        int historyRecency,
        @Nullable String historyFamily,
        InsertionIntent insertionIntent
) {
    public static final int NO_HISTORY = -1;

    public enum Kind {
        ACTION_BOUNDARY,
        COMPLETE_HISTORY_COMMAND,
        LITERAL_CONTINUATION,
        ARGUMENT_VALUE,
        USAGE_HINT
    }

    public enum Origin {
        ACTION_REGISTRY,
        COMMAND_HISTORY,
        BRIGADIER,
        LITERAL_DISCOVERY,
        SMART_USAGE,
        PARSE_DIAGNOSTIC,
        CHOICE_SURFACE
    }

    public enum InsertionIntent {
        EXACT_REPLACEMENT(""),
        APPEND_ARGUMENT_SEPARATOR(" ");

        private final String deliberateSeparator;

        InsertionIntent(String deliberateSeparator) {
            this.deliberateSeparator = deliberateSeparator;
        }

        public String deliberateSeparator() {
            return deliberateSeparator;
        }
    }

    public SFMPaletteCandidate {
        suggestion = Objects.requireNonNull(suggestion, "suggestion");
        displayText = Objects.requireNonNull(displayText, "displayText");
        kind = Objects.requireNonNull(kind, "kind");
        origin = Objects.requireNonNull(origin, "origin");
        completionFrontier = Objects.requireNonNull(completionFrontier, "completionFrontier");
        insertionIntent = Objects.requireNonNull(insertionIntent, "insertionIntent");
        if (historyRecency < NO_HISTORY) {
            throw new IllegalArgumentException("historyRecency must be -1 or a non-negative index");
        }
    }

    public static SFMPaletteCandidate activatable(
            Suggestion suggestion,
            Kind kind,
            Origin origin,
            @Nullable ResourceLocation actionId,
            String completionFrontier,
            int historyRecency,
            @Nullable String historyFamily
    ) {
        return activatable(
                suggestion,
                suggestion.getText(),
                kind,
                origin,
                actionId,
                completionFrontier,
                historyRecency,
                historyFamily
        );
    }

    public static SFMPaletteCandidate activatable(
            Suggestion suggestion,
            String displayText,
            Kind kind,
            Origin origin,
            @Nullable ResourceLocation actionId,
            String completionFrontier,
            int historyRecency,
            @Nullable String historyFamily
    ) {
        return new SFMPaletteCandidate(
                suggestion,
                displayText,
                kind,
                origin,
                actionId,
                completionFrontier,
                historyRecency,
                historyFamily,
                InsertionIntent.EXACT_REPLACEMENT
        );
    }

    public static SFMPaletteCandidate usageHint(
            StringRange range,
            String displayText,
            Origin origin,
            @Nullable ResourceLocation actionId,
            String completionFrontier
    ) {
        return new SFMPaletteCandidate(
                new Suggestion(range, ""),
                displayText,
                Kind.USAGE_HINT,
                origin,
                actionId,
                completionFrontier,
                NO_HISTORY,
                null,
                InsertionIntent.EXACT_REPLACEMENT
        );
    }

    public boolean activatable() {
        return kind != Kind.USAGE_HINT;
    }

    public StringRange replacementRange() {
        return suggestion.getRange();
    }

    public String replacementText() {
        return suggestion.getText();
    }

    public SFMCompletionApplication apply(String beforeValue) {
        if (!activatable()) {
            throw new IllegalStateException("Usage hints cannot be applied as completions");
        }
        String replaced = suggestion.apply(beforeValue);
        String separator = insertionIntent.deliberateSeparator();
        String after = separator.isEmpty() || replaced.endsWith(separator)
                ? replaced
                : replaced + separator;
        return new SFMCompletionApplication(
                beforeValue,
                replacementRange(),
                replacementText(),
                separator,
                kind,
                origin,
                after
        );
    }

    /**
     * Finds the candidate a repeated forward Tab should actually apply. Exact
     * no-op action boundaries prefer a strict action-id descendant before any
     * historical command leaf; otherwise ordinary list order is preserved.
     */
    public static OptionalInt progressingIndex(
            List<SFMPaletteCandidate> candidates,
            int selectedIndex,
            String currentValue
    ) {
        if (selectedIndex < 0 || selectedIndex >= candidates.size()) return OptionalInt.empty();
        SFMPaletteCandidate selected = candidates.get(selectedIndex);
        if (selected.activatable()
                && selected.kind() == Kind.ACTION_BOUNDARY
                && selected.apply(currentValue).afterValue().equals(currentValue)) {
            String descendantPrefix = selected.replacementText() + "/";
            for (int index = 0; index < candidates.size(); index++) {
                SFMPaletteCandidate candidate = candidates.get(index);
                if (candidate.activatable()
                        && candidate.kind() == Kind.ACTION_BOUNDARY
                        && candidate.replacementRange().equals(selected.replacementRange())
                        && candidate.replacementText().startsWith(descendantPrefix)
                        && !candidate.apply(currentValue).afterValue().equals(currentValue)) {
                    return OptionalInt.of(index);
                }
            }
        }
        for (int offset = 1; offset <= candidates.size(); offset++) {
            int index = (selectedIndex + offset) % candidates.size();
            SFMPaletteCandidate candidate = candidates.get(index);
            if (candidate.activatable()
                    && !candidate.apply(currentValue).afterValue().equals(currentValue)) {
                return OptionalInt.of(index);
            }
        }
        return OptionalInt.empty();
    }
}
