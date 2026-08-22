package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.client.search.SFMFuzzyScorer;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.ParsedCommandNode;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import com.mojang.brigadier.tree.CommandNode;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Bounded parsed projection of raw command history for explicitly compatible
 * semantic argument families. It never tokenizes history on whitespace.
 */
final class SFMClientActionArgumentHistory {
    static final String PANEL_OPEN_SCENE_FAMILY = "sfm:panel/open#scene";
    private static final int MAX_HISTORY_COMMANDS = 128;
    private static final float MAX_FUZZY_SCORE = 0.65f;
    private static final Family PANEL_OPEN_SCENES = new Family(
            PANEL_OPEN_SCENE_FAMILY,
            Set.of(
                    new ResourceLocation("sfm", "panel/open"),
                    new ResourceLocation("sfm", "panel/open/left"),
                    new ResourceLocation("sfm", "panel/open/right"),
                    new ResourceLocation("sfm", "panel/open/above"),
                    new ResourceLocation("sfm", "panel/open/below")
            )
    );
    private static final List<Family> FAMILIES = List.of(PANEL_OPEN_SCENES);

    private SFMClientActionArgumentHistory() {
    }

    static List<HistoricalArgument> suggestions(
            String command,
            ParseResults<SFMClientActionSource> parsed,
            SFMCommandFrontierAnalysis frontier,
            CommandDispatcher<SFMClientActionSource> dispatcher,
            List<String> availableHistory
    ) {
        ResourceLocation currentAction = ResourceLocation.tryParse(frontier.parent().getName());
        Family family = familyFor(currentAction);
        if (family == null) return List.of();
        String query = command.substring(
                frontier.replacementRange().getStart(),
                frontier.replacementRange().getEnd()
        );
        Map<String, HistoricalArgument> newestByValue = new LinkedHashMap<>();
        int limit = Math.min(MAX_HISTORY_COMMANDS, availableHistory.size());
        for (int historyIndex = 0; historyIndex < limit; historyIndex++) {
            String historyCommand = availableHistory.get(historyIndex);
            ParseResults<SFMClientActionSource> historyParse = dispatcher.parse(
                    historyCommand,
                    parsed.getContext().getSource()
            );
            ParsedValue parsedValue = firstFamilyValue(historyCommand, historyParse, family);
            if (parsedValue == null) continue;
            CommandNode<SFMClientActionSource> currentSlot = compatibleCurrentSlot(
                    frontier.parent(), parsedValue);
            if (currentSlot == null
                    || !slotFingerprint(currentSlot).equals(slotFingerprint(parsedValue.slot()))) continue;
            float score = query.isBlank() ? 0.0f : SFMFuzzyScorer.score(query, parsedValue.value());
            if (!query.isBlank() && score > MAX_FUZZY_SCORE) continue;
            newestByValue.putIfAbsent(parsedValue.value(), new HistoricalArgument(
                    parsedValue.value(),
                    historyIndex,
                    family.id(),
                    score
            ));
        }
        ArrayList<HistoricalArgument> result = new ArrayList<>(newestByValue.values());
        result.sort(Comparator.comparingInt(HistoricalArgument::historyRecency)
                .thenComparingDouble(HistoricalArgument::score)
                .thenComparing(HistoricalArgument::value));
        return List.copyOf(result);
    }

    private static Family familyFor(ResourceLocation actionId) {
        if (actionId == null) return null;
        return FAMILIES.stream().filter(family -> family.actionIds().contains(actionId)).findFirst().orElse(null);
    }

    private static ParsedValue firstFamilyValue(
            String command,
            ParseResults<SFMClientActionSource> parsed,
            Family family
    ) {
        List<ParsedCommandNode<SFMClientActionSource>> nodes = parsed.getContext().getNodes();
        for (int index = 0; index + 1 < nodes.size(); index++) {
            CommandNode<SFMClientActionSource> node = nodes.get(index).getNode();
            ResourceLocation actionId = ResourceLocation.tryParse(node.getName());
            if (!family.actionIds().contains(actionId)) continue;
            ParsedCommandNode<SFMClientActionSource> valueNode = nodes.get(index + 1);
            int start = valueNode.getRange().getStart();
            int end = valueNode.getRange().getEnd();
            if (start < 0 || end < start || end > command.length()) return null;
            return new ParsedValue(
                    command.substring(start, end),
                    valueNode.getNode()
            );
        }
        return null;
    }

    private static CommandNode<SFMClientActionSource> compatibleCurrentSlot(
            CommandNode<SFMClientActionSource> parent,
            ParsedValue parsedValue
    ) {
        CommandNode<SFMClientActionSource> exact = parent.getChild(parsedValue.value());
        if (exact != null) return exact;
        for (CommandNode<SFMClientActionSource> child : parent.getChildren()) {
            if (child.getName().equals(parsedValue.slot().getName())
                    && slotFingerprint(child).equals(slotFingerprint(parsedValue.slot()))) return child;
        }
        return null;
    }

    private static String slotFingerprint(CommandNode<SFMClientActionSource> node) {
        String type = node.getClass().getName();
        if (node instanceof ArgumentCommandNode<?, ?> argument) {
            type += ":" + argumentTypeFingerprint(argument);
        }
        return type;
    }

    private static String argumentTypeFingerprint(ArgumentCommandNode<?, ?> argument) {
        if (argument.getType() instanceof StringArgumentType string) {
            return StringArgumentType.class.getName() + ":" + string.getType().name();
        }
        return argument.getType().getClass().getName() + ":" + argument.getType();
    }

    record HistoricalArgument(
            String value,
            int historyRecency,
            String familyId,
            float score
    ) {
    }

    private record Family(String id, Set<ResourceLocation> actionIds) {
    }

    private record ParsedValue(String value, CommandNode<SFMClientActionSource> slot) {
    }
}
