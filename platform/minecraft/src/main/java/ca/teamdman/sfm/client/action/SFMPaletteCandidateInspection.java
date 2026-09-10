package ca.teamdman.sfm.client.action;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable evidence captured when a command-palette candidate is inspected.
 * The snapshot prevents a later asynchronous suggestion refresh from changing
 * what a tooltip described or what a contextual copy action copies.
 */
public record SFMPaletteCandidateInspection(
        String displayText,
        boolean activatable,
        SFMPaletteCandidate.Kind kind,
        SFMPaletteCandidate.Origin origin,
        @Nullable String actionId,
        String inputBefore,
        int replacementStart,
        int replacementEnd,
        String replacementText,
        @Nullable String surfaceText,
        @Nullable String canonicalCommand,
        String completionFrontier,
        int historyRecency,
        @Nullable String historyFamily,
        SFMPaletteCandidate.InsertionIntent insertionIntent,
        String deliberateSeparator,
        @Nullable String helpTitle,
        @Nullable String helpDescription,
        @Nullable String iconLabel,
        List<String> keyBindings
) {
    public static final String SCHEMA = "sfm.palette-candidate-inspection/1";

    public SFMPaletteCandidateInspection {
        displayText = Objects.requireNonNull(displayText, "displayText");
        kind = Objects.requireNonNull(kind, "kind");
        origin = Objects.requireNonNull(origin, "origin");
        inputBefore = Objects.requireNonNull(inputBefore, "inputBefore");
        replacementText = Objects.requireNonNull(replacementText, "replacementText");
        completionFrontier = Objects.requireNonNull(completionFrontier, "completionFrontier");
        insertionIntent = Objects.requireNonNull(insertionIntent, "insertionIntent");
        deliberateSeparator = Objects.requireNonNull(deliberateSeparator, "deliberateSeparator");
        keyBindings = List.copyOf(keyBindings);
        if (replacementStart < 0 || replacementEnd < replacementStart) {
            throw new IllegalArgumentException("Invalid replacement range ["
                    + replacementStart + "," + replacementEnd + ")");
        }
        if (activatable != (surfaceText != null)) {
            throw new IllegalArgumentException("Only activatable candidates have surface text");
        }
        if (!activatable && canonicalCommand != null) {
            throw new IllegalArgumentException("Non-activatable candidates cannot have a canonical command");
        }
    }

    public static SFMPaletteCandidateInspection capture(
            SFMPaletteCandidate candidate,
            String inputBefore,
            @Nullable String canonicalCommand,
            @Nullable String actionId,
            @Nullable String helpTitle,
            @Nullable String helpDescription,
            @Nullable String iconLabel,
            List<String> keyBindings
    ) {
        Objects.requireNonNull(candidate, "candidate");
        Objects.requireNonNull(inputBefore, "inputBefore");
        String surfaceText = candidate.activatable()
                ? candidate.apply(inputBefore).afterValue()
                : null;
        return new SFMPaletteCandidateInspection(
                candidate.displayText(),
                candidate.activatable(),
                candidate.kind(),
                candidate.origin(),
                actionId,
                inputBefore,
                candidate.replacementRange().getStart(),
                candidate.replacementRange().getEnd(),
                candidate.replacementText(),
                surfaceText,
                canonicalCommand,
                candidate.completionFrontier(),
                candidate.historyRecency(),
                candidate.historyFamily(),
                candidate.insertionIntent(),
                candidate.insertionIntent().deliberateSeparator(),
                helpTitle,
                helpDescription,
                iconLabel,
                keyBindings
        );
    }

    public String displayTextPayload() {
        return displayText;
    }

    public String replacementSurfacePayload() {
        return "replacement-text: " + quote(replacementText) + System.lineSeparator()
                + "surface-text: " + optionalQuoted(surfaceText);
    }

    public Optional<String> canonicalCommandPayload() {
        return Optional.ofNullable(canonicalCommand);
    }

    /** A stable, line-oriented payload suitable for clipboard diagnostics and future parsers. */
    public String detailsPayload() {
        ArrayList<String> lines = new ArrayList<>();
        lines.add("schema: " + SCHEMA);
        lines.add("display-text: " + quote(displayText));
        lines.add("activatable: " + activatable);
        lines.add("kind: " + kind);
        lines.add("origin: " + origin);
        lines.add("action-id: " + optionalQuoted(actionId));
        lines.add("input-before: " + quote(inputBefore));
        lines.add("replacement-range: [" + replacementStart + "," + replacementEnd + ")");
        lines.add("replacement-text: " + quote(replacementText));
        lines.add("surface-text: " + optionalQuoted(surfaceText));
        lines.add("canonical-command: " + optionalQuoted(canonicalCommand));
        lines.add("completion-frontier: " + quote(completionFrontier));
        lines.add("history-recency: " + historyRecency);
        lines.add("history-family: " + optionalQuoted(historyFamily));
        lines.add("insertion-intent: " + insertionIntent);
        lines.add("deliberate-separator: " + quote(deliberateSeparator));
        lines.add("help-title: " + optionalQuoted(helpTitle));
        lines.add("help-description: " + optionalQuoted(helpDescription));
        lines.add("icon-label: " + optionalQuoted(iconLabel));
        lines.add("key-bindings: " + keyBindings.size());
        for (int index = 0; index < keyBindings.size(); index++) {
            lines.add("key-binding[" + index + "]: " + quote(keyBindings.get(index)));
        }
        return String.join(System.lineSeparator(), lines);
    }

    /** The complete accessible description used for a truncated row's whole-row tooltip. */
    public List<String> accessibleDescriptionLines() {
        ArrayList<String> lines = new ArrayList<>();
        lines.add("Candidate: " + displayText);
        lines.addAll(detailsPayload().lines().toList());
        return List.copyOf(lines);
    }

    private static String optionalQuoted(@Nullable String value) {
        return value == null ? "unavailable" : quote(value);
    }

    private static String quote(String value) {
        StringBuilder answer = new StringBuilder(value.length() + 2).append('"');
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '\\' -> answer.append("\\\\");
                case '"' -> answer.append("\\\"");
                case '\n' -> answer.append("\\n");
                case '\r' -> answer.append("\\r");
                case '\t' -> answer.append("\\t");
                default -> {
                    if (Character.isISOControl(character)) {
                        answer.append(String.format("\\u%04x", (int) character));
                    } else {
                        answer.append(character);
                    }
                }
            }
        }
        return answer.append('"').toString();
    }
}
