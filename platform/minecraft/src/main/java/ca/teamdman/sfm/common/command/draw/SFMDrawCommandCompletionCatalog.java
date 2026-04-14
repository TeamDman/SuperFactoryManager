package ca.teamdman.sfm.common.command.draw;

import ca.teamdman.sfm.common.template.SFMDrawTemplate;
import ca.teamdman.sfm.common.template.SFMDrawTemplateRegistry;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

public final class SFMDrawCommandCompletionCatalog {
    private static final List<String> LOCAL_DRAW_COMMAND_NAMES = List.of(
            "open",
            "move",
            "ls",
            "box",
            "concatenate",
            "ollama"
    );

    private static final List<String> OLLAMA_MODEL_FALLBACKS = List.of(
            "llama3.2",
            "qwen3:8b",
            "gemma3:4b"
    );

    private SFMDrawCommandCompletionCatalog() {
    }

    public static List<String> localDrawCommandNames() {
        return LOCAL_DRAW_COMMAND_NAMES;
    }

    public static List<String> ollamaModelFallbacks() {
        return OLLAMA_MODEL_FALLBACKS;
    }

    public static List<String> canvasPathSuggestions() {
        LinkedHashSet<String> suggestions = new LinkedHashSet<>();
        suggestions.add("canvas");
        suggestions.add("/user/home");
        suggestions.add("/user/home/canvas");
        return List.copyOf(suggestions);
    }

    public static List<String> browsePathSuggestions() {
        LinkedHashSet<String> suggestions = new LinkedHashSet<>();
        suggestions.add("/user/home");
        suggestions.add("/user/home/examples");
        suggestions.add("examples");
        for (SFMDrawTemplate template : SFMDrawTemplateRegistry.gatherAll()) {
            suggestions.add("/user/home/examples/" + template.key() + ".sfml");
            suggestions.add("examples/" + template.key() + ".sfml");
        }
        return List.copyOf(suggestions);
    }

    public static List<String> filterByPrefix(
            List<String> values,
            String prefix
    ) {
        String normalizedPrefix = prefix == null ? "" : prefix.toLowerCase(Locale.ROOT);
        if (normalizedPrefix.isBlank()) {
            return values;
        }

        List<String> filtered = new ArrayList<>();
        for (String value : values) {
            if (value.toLowerCase(Locale.ROOT).startsWith(normalizedPrefix)) {
                filtered.add(value);
            }
        }
        return filtered;
    }
}