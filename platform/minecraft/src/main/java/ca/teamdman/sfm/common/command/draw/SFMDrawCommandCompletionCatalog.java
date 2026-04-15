package ca.teamdman.sfm.common.command.draw;

import ca.teamdman.sfm.common.template.SFMDrawTemplate;
import ca.teamdman.sfm.common.template.SFMDrawTemplateRegistry;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

public final class SFMDrawCommandCompletionCatalog {
    private static final List<String> LOCAL_DRAW_COMMAND_NAMES = List.of(
        "help",
            "open",
            "move",
            "ls",
        "rectangle",
            "concatenate",
        "width",
        "name",
            "reset",
            "ollama"
    );

    private static final List<String> DRAW_HELP_USAGES = List.of(
        "/help",
        "/help <command-or-target>",
        "/echo <message>",
        "/open [path]",
        "/move <from> <to>",
        "/ls [path]",
        "/rectangle list",
        "/rectangle <x1> <y1> <x2> <y2>",
        "/concatenate <target> [delimiter]",
        "/width <target>",
        "/name <target> [new name]",
        "/reset chrome",
        "/ollama models",
        "/ollama run <model> <prompt>",
        "/player",
        "/player pos",
        "/player pos x",
        "/player pos y",
        "/player pos xy",
        "/player pos yx",
        "/player angle",
        "/player dimension",
        "/player look angle",
        "/player look hit",
        "/player look hit block",
        "/player look hit manager",
        "/player look x",
        "/player look y",
        "/player look z",
        "/player look xy",
        "/player look xz",
        "/player look yx",
        "/player look yz",
        "/player look zx",
        "/player look zy",
        "/player look xyz",
        "/player look xzy",
        "/player look yxz",
        "/player look yzx",
        "/player look zxy",
        "/player look zyx",
        "/player inv [slot]",
        "/player armor [slot]",
        "/player hand [mainhand|offhand]",
        "/camera pos",
        "/camera zoom",
        "/mouse pos",
        "/mouse screen_pos",
        "/template list",
        "/template open <template>"
    );

    private static final List<String> DRAW_HELP_TOPICS = buildHelpTopics();

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

    public static List<String> drawHelpUsages() {
        return DRAW_HELP_USAGES;
    }

    public static List<String> drawHelpUsages(String topic) {
        return filterHelpUsages(DRAW_HELP_USAGES, topic);
    }

    public static List<String> drawHelpTopics() {
        return DRAW_HELP_TOPICS;
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

    public static List<String> filterHelpUsages(
            List<String> usages,
            String topic
    ) {
        String normalizedTopic = normalizeHelpTopic(topic);
        if (normalizedTopic.isBlank()) {
            return usages;
        }

        List<String> filtered = new ArrayList<>();
        for (String usage : usages) {
            String normalizedUsage = usageCommandKey(usage);
            if (normalizedUsage.equals(normalizedTopic) || normalizedUsage.startsWith(normalizedTopic + " ")) {
                filtered.add(usage);
            }
        }
        return filtered;
    }

    public static String normalizeHelpTopic(String topic) {
        if (topic == null) {
            return "";
        }

        String normalized = topic.strip().toLowerCase(Locale.ROOT);
        if (normalized.startsWith("/sfm draw ")) {
            normalized = normalized.substring("/sfm draw ".length());
        } else if (normalized.equals("/sfm draw")) {
            normalized = "";
        } else if (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        return normalized;
    }

    private static List<String> buildHelpTopics() {
        LinkedHashSet<String> topics = new LinkedHashSet<>();
        for (String usage : DRAW_HELP_USAGES) {
            List<String> tokens = usageCommandTokens(usage);
            for (int index = 1; index <= tokens.size(); index++) {
                topics.add(String.join(" ", tokens.subList(0, index)));
            }
        }
        return List.copyOf(topics);
    }

    private static String usageCommandKey(String usage) {
        return String.join(" ", usageCommandTokens(usage));
    }

    private static List<String> usageCommandTokens(String usage) {
        String normalized = normalizeHelpTopic(usage);
        if (normalized.isBlank()) {
            return List.of();
        }

        List<String> tokens = new ArrayList<>();
        for (String token : normalized.split("\\s+")) {
            if (token.isBlank()) {
                continue;
            }
            if (token.startsWith("<") || token.startsWith("[")) {
                break;
            }
            tokens.add(token);
        }
        return tokens;
    }
}