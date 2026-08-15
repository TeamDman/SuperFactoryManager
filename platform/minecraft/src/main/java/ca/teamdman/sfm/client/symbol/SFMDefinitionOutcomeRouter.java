package ca.teamdman.sfm.client.symbol;

import java.util.List;
import java.util.Objects;

/** Pure deterministic decision before any panel or palette mutation occurs. */
public final class SFMDefinitionOutcomeRouter {
    public sealed interface Decision permits Open, Choose, Report {
    }

    public record Open(SFMDefinitionResult.Definition definition) implements Decision {
        public Open {
            Objects.requireNonNull(definition, "definition");
        }
    }

    public record Choose(List<SFMDefinitionResult.Definition> definitions) implements Decision {
        public Choose {
            definitions = List.copyOf(definitions);
            if (definitions.size() < 2) throw new IllegalArgumentException("Choose requires ambiguity");
        }
    }

    public record Report(String message, boolean incomplete) implements Decision {
        public Report {
            Objects.requireNonNull(message, "message");
            if (message.isBlank()) throw new IllegalArgumentException("message must not be blank");
        }
    }

    private SFMDefinitionOutcomeRouter() {
    }

    public static Decision route(SFMDefinitionResult result) {
        Objects.requireNonNull(result, "result");
        List<SFMDefinitionResult.Definition> definitions =
                SFMDefinitionChoiceSessionService.stableDefinitions(result.definitions());
        if (definitions.size() == 1) return new Open(definitions.get(0));
        if (definitions.size() > 1) return new Choose(definitions);
        return new Report(message(result),
                result.completeness() == SFMDefinitionResult.Completeness.INCOMPLETE);
    }

    static String message(SFMDefinitionResult result) {
        String message = switch (result.outcome()) {
            case NO_SYMBOL -> "No symbol is present at the captured editor position";
            case NO_DEFINITION -> "No definition was found for the captured symbol";
            case AMBIGUOUS -> "The symbol worker reported ambiguity without navigable definitions";
            case STALE_DOCUMENT -> "The captured document changed before definition lookup completed";
            case INVALID_REQUEST -> "The symbol worker rejected the captured editor context";
            case UNAVAILABLE -> "Definition lookup is unavailable";
            case SUCCESS -> "Definition lookup returned no navigable target";
        };
        if (result.completeness() == SFMDefinitionResult.Completeness.INCOMPLETE) {
            message += " (dependency/source index is incomplete)";
        }
        return message;
    }
}
