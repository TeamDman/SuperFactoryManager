package ca.teamdman.sfm.client.action;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.StringRange;
import com.mojang.brigadier.context.SuggestionContext;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import com.mojang.brigadier.tree.CommandNode;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/** Shared Brigadier frontier, usage, and diagnostic analysis. */
public record SFMCommandFrontierAnalysis(
        CommandNode<SFMClientActionSource> parent,
        StringRange replacementRange,
        String frontierId,
        List<Suggestion> suggestions,
        List<Usage> usages,
        List<String> diagnostics,
        boolean executable
) {
    public SFMCommandFrontierAnalysis {
        parent = Objects.requireNonNull(parent, "parent");
        replacementRange = Objects.requireNonNull(replacementRange, "replacementRange");
        frontierId = Objects.requireNonNull(frontierId, "frontierId");
        suggestions = List.copyOf(suggestions);
        usages = List.copyOf(usages);
        diagnostics = List.copyOf(diagnostics);
    }

    static SFMCommandFrontierAnalysis analyze(
            String command,
            ParseResults<SFMClientActionSource> parsed,
            CommandDispatcher<SFMClientActionSource> dispatcher
    ) {
        SuggestionContext<SFMClientActionSource> context = parsed
                .getContext()
                .findSuggestionContext(command.length());
        Map<CommandNode<SFMClientActionSource>, String> smartUsage = dispatcher.getSmartUsage(
                context.parent,
                parsed.getContext().getSource()
        );
        List<Usage> usages = smartUsage.entrySet().stream()
                .filter(entry -> entry.getKey() instanceof ArgumentCommandNode<?, ?>)
                .sorted(Comparator.comparing(entry -> entry.getKey().getName()))
                .map(entry -> {
                    ArgumentCommandNode<?, ?> argument = (ArgumentCommandNode<?, ?>) entry.getKey();
                    return new Usage(argument.getName(), displayType(argument), entry.getValue());
                })
                .toList();
        List<String> diagnostics = parsed.getExceptions().values().stream()
                .map(exception -> exception.getRawMessage().getString())
                .filter(message -> !message.isBlank())
                .sorted()
                .distinct()
                .toList();
        String path = parsed.getContext().getNodes().stream()
                .filter(node -> node.getRange().getEnd() <= context.startPos)
                .map(node -> node.getNode().getName())
                .collect(Collectors.joining("/"));
        String frontierId = path + "@" + context.startPos + ":" + context.parent.getName();
        return new SFMCommandFrontierAnalysis(
                context.parent,
                StringRange.between(context.startPos, command.length()),
                frontierId,
                List.of(),
                usages,
                diagnostics,
                SFMClientActionExecutor.isExecutable(parsed)
        );
    }

    public SFMCommandFrontierAnalysis withSuggestions(Suggestions concrete) {
        Objects.requireNonNull(concrete, "concrete");
        return new SFMCommandFrontierAnalysis(
                parent,
                replacementRange,
                frontierId,
                concrete.getList(),
                usages,
                diagnostics,
                executable
        );
    }

    public List<Parameter> missingParameters() {
        ArrayList<Parameter> result = new ArrayList<>();
        for (Usage usage : usages) {
            Parameter parameter = new Parameter(usage.argumentName(), usage.displayType());
            if (!result.contains(parameter)) result.add(parameter);
        }
        return List.copyOf(result);
    }

    public List<String> usageDisplayRows() {
        return usages.stream()
                .map(usage -> "Expected " + usage.argumentName() + " (" + usage.displayType() + "): "
                        + usage.smartUsage())
                .toList();
    }

    private static String displayType(ArgumentCommandNode<?, ?> node) {
        if (node.getType() instanceof StringArgumentType string) {
            return switch (string.getType()) {
                case SINGLE_WORD -> "word";
                case QUOTABLE_PHRASE -> "quoted string";
                case GREEDY_PHRASE -> "string";
            };
        }
        String name = node.getType().getClass().getSimpleName();
        return name.endsWith("ArgumentType")
                ? name.substring(0, name.length() - "ArgumentType".length())
                : name;
    }

    public record Usage(String argumentName, String displayType, String smartUsage) {
    }

    public record Parameter(String name, String displayType) {
    }
}
