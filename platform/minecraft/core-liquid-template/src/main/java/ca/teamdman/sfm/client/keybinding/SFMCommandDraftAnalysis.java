package ca.teamdman.sfm.client.keybinding;

import ca.teamdman.sfm.client.action.SFMClientActionCommandTree;
import ca.teamdman.sfm.client.action.SFMClientActionExecutor;
import ca.teamdman.sfm.client.action.SFMClientActionSource;
import ca.teamdman.sfm.client.action.SFMClientCommandInsertion;
{% if features.typed_command_palette %}
import ca.teamdman.sfm.client.action.SFMCommandFrontierAnalysis;
{% else %}
{% endif %}
import com.mojang.brigadier.ParseResults;
{% if features.typed_command_palette %}
{% else %}
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import com.mojang.brigadier.tree.CommandNode;
{% endif %}
import org.jetbrains.annotations.Nullable;

{% if features.typed_command_palette %}
{% else %}
import java.util.Comparator;
import java.util.List;

{% endif %}
/** Pure Brigadier-backed classification for a bound command draft. */
public record SFMCommandDraftAnalysis(
        State state,
        String preparedCommand,
        @Nullable MissingParameter missingParameter,
        String diagnostic
) {
    public enum State { COMPLETE, INCOMPLETE, INVALID }

    public static SFMCommandDraftAnalysis analyze(
            String commandDraft,
            SFMClientActionCommandTree tree,
            SFMClientActionSource source
    ) {
        String command = stripSlash(commandDraft).stripLeading();
        String prepared = SFMClientCommandInsertion.prepare(command, tree, source);
        ParseResults<SFMClientActionSource> parsed = tree.parse(prepared, source);
        if (SFMClientActionExecutor.isExecutable(parsed)) {
            return new SFMCommandDraftAnalysis(State.COMPLETE, prepared, null, "Ready to confirm");
        }
{% if features.typed_command_palette %}
        SFMCommandFrontierAnalysis frontier = tree.analyzeFrontier(prepared, parsed);
        MissingParameter missing = frontier.missingParameters().stream()
                .map(parameter -> new MissingParameter(parameter.name(), parameter.displayType()))
                .findFirst()
                .orElse(null);
{% else %}
        MissingParameter missing = nextArgument(parsed);
{% endif %}
        // Brigadier records the child argument's expected-input exception at EOF.
        // The parsed parent plus a concrete argument child is still a typed incomplete draft.
        boolean onlyTrailingWhitespaceRemains = !parsed.getReader().canRead()
                || parsed.getReader().getRemaining().isBlank();
        if (missing != null && onlyTrailingWhitespaceRemains) {
            return new SFMCommandDraftAnalysis(State.INCOMPLETE, prepared, missing,
                    "Provide " + missing.name() + " (" + missing.displayType() + ")");
        }
{% if features.typed_command_palette %}
        String diagnostic = frontier.diagnostics().stream()
{% else %}
        String diagnostic = parsed.getExceptions().values().stream()
                .map(exception -> exception.getRawMessage().getString())
                .sorted()
{% endif %}
                .findFirst()
                .orElseGet(() -> parsed.getReader().canRead()
                        ? "Unexpected input at character " + parsed.getReader().getCursor()
                        : "The command is incomplete, but its next input has no typed editor");
        return new SFMCommandDraftAnalysis(State.INVALID, prepared, null, diagnostic);
    }

{% if features.typed_command_palette %}
{% else %}
    private static @Nullable MissingParameter nextArgument(ParseResults<SFMClientActionSource> parsed) {
        List<com.mojang.brigadier.context.ParsedCommandNode<SFMClientActionSource>> nodes =
                parsed.getContext().getLastChild().getNodes();
        if (nodes.isEmpty()) return null;
        CommandNode<SFMClientActionSource> last = nodes.get(nodes.size() - 1).getNode();
        return last.getChildren().stream()
                .sorted(Comparator.comparing(CommandNode::getName))
                .filter(ArgumentCommandNode.class::isInstance)
                .map(node -> (ArgumentCommandNode<?, ?>) node)
                .map(node -> new MissingParameter(node.getName(), displayType(node)))
                .findFirst()
                .orElse(null);
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
        return name.endsWith("ArgumentType") ? name.substring(0, name.length() - "ArgumentType".length()) : name;
    }

{% endif %}
    private static String stripSlash(String command) {
        return command.startsWith("/") ? command.substring(1) : command;
    }

    public record MissingParameter(String name, String displayType) {
    }
}
