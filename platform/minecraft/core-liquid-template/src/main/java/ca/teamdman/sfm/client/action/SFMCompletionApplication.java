package ca.teamdman.sfm.client.action;

import com.mojang.brigadier.context.StringRange;

import java.util.Objects;

/**
 * Exact, immutable evidence for one palette completion application.
 * Document history can consume this without reconstructing intent from two
 * unrelated EditBox values.
 */
public record SFMCompletionApplication(
        String beforeValue,
        StringRange replacementRange,
        String replacementText,
        String deliberateSeparator,
        SFMPaletteCandidate.Kind candidateKind,
        SFMPaletteCandidate.Origin candidateOrigin,
        String afterValue
) {
    public SFMCompletionApplication {
        beforeValue = Objects.requireNonNull(beforeValue, "beforeValue");
        replacementRange = Objects.requireNonNull(replacementRange, "replacementRange");
        replacementText = Objects.requireNonNull(replacementText, "replacementText");
        deliberateSeparator = Objects.requireNonNull(deliberateSeparator, "deliberateSeparator");
        candidateKind = Objects.requireNonNull(candidateKind, "candidateKind");
        candidateOrigin = Objects.requireNonNull(candidateOrigin, "candidateOrigin");
        afterValue = Objects.requireNonNull(afterValue, "afterValue");
    }
}
