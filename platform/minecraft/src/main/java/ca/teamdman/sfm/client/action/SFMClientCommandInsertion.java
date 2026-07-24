package ca.teamdman.sfm.client.action;

import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.context.ParsedCommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.minecraft.resources.ResourceLocation;

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
        if (!literal.getLiteral().contains(":") || ResourceLocation.tryParse(literal.getLiteral()) == null) return command;
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
}
