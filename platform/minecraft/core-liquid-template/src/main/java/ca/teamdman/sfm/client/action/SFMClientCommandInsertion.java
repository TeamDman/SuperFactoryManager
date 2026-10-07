package ca.teamdman.sfm.client.action;

import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.context.ParsedCommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
{% case minecraft_version %}
{% when "26.1.2" %}
import net.minecraft.resources.Identifier;
{% else %}
import net.minecraft.resources.ResourceLocation;
{% endcase %}

import java.util.List;

/** Shared caret policy for palette suggestions and shortcut command drafts. */
public final class SFMClientCommandInsertion {
    private SFMClientCommandInsertion() {
    }

    public static String appendRequiredArgumentSeparator(
            String command,
            ParseResults<SFMClientActionSource> parsed
    ) {
        if (command.isEmpty() || Character.isWhitespace(command.charAt(command.length() - 1))) return command;
        if (parsed.getReader().canRead() || !parsed.getExceptions().isEmpty()) return command;
        List<ParsedCommandNode<SFMClientActionSource>> nodes = parsed.getContext().getNodes();
        if (nodes.isEmpty()) return command;
        ParsedCommandNode<SFMClientActionSource> parsedNode = nodes.get(nodes.size() - 1);
        if (parsedNode.getRange().getEnd() != command.length()) return command;
        if (!(parsedNode.getNode() instanceof LiteralCommandNode<SFMClientActionSource> literal)) return command;
{% case minecraft_version %}
{% when "26.1.2" %}
        if (!literal.getLiteral().contains(":") || Identifier.tryParse(literal.getLiteral()) == null) return command;
{% else %}
        if (!literal.getLiteral().contains(":") || ResourceLocation.tryParse(literal.getLiteral()) == null) return command;
{% endcase %}
        if (literal.getCommand() != null || literal.getChildren().isEmpty()) return command;
        return command + " ";
    }

    public static String prepare(
            String command,
            SFMClientActionCommandTree tree,
            SFMClientActionSource source
    ) {
        return appendRequiredArgumentSeparator(command, tree.parse(command, source));
    }

    public static boolean isAwaitingRequiredArgument(
            String command,
            SFMClientActionCommandTree tree,
            SFMClientActionSource source
    ) {
        if (command.isEmpty() || !Character.isWhitespace(command.charAt(command.length() - 1))) return false;
        String withoutSeparator = command.stripTrailing();
        return prepare(withoutSeparator, tree, source).equals(withoutSeparator + " ");
    }
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}

    /**
     * Reports whether the parsed terminal node has literal child choices.
     * Required argument nodes are deliberately excluded: users may type those
     * values manually, while a literal scene/action choice should be selected
     * from the available suggestions first.
     */
    public static boolean hasAvailableLiteralChildren(ParseResults<SFMClientActionSource> parsed) {
        if (parsed == null) return false;
        List<ParsedCommandNode<SFMClientActionSource>> nodes = parsed.getContext().getNodes();
        if (nodes.isEmpty()) return false;
        return nodes.get(nodes.size() - 1).getNode().getChildren().stream()
                .anyMatch(child -> child instanceof LiteralCommandNode<?>);
    }
{% endcase %}
}
