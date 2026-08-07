package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.SFM;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.context.StringRange;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentContents;
import net.minecraft.network.chat.contents.LiteralContents;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import org.simmetrics.StringDistance;
import org.simmetrics.metrics.StringDistances;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public final class SFMClientActionCommandTree {
    private static final String PALETTE_ACTION_PREFIX = "sfm action invoke ";
    private static final long SLOW_COMPLETION_NANOS = 100_000_000L;
    private static final int MAX_LITERAL_CONTINUATION_DEPTH = 8;
    private static final int MAX_LITERAL_CONTINUATION_CANDIDATES = 256;
    private static final StringDistance ACTION_DISTANCE = StringDistances.damerauLevenshtein();

    private final CommandDispatcher<SFMClientActionSource> dispatcher;
    private final Map<ResourceLocation, SFMClientAction<?>> actions;
    private final Map<ResourceLocation, ActionSearchMetadata> searchMetadata;
    private final List<String> paletteActionPrefixes;
    private final Map<String, ResourceLocation> paletteChoiceActions;

    SFMClientActionCommandTree(
            CommandDispatcher<SFMClientActionSource> dispatcher,
            Map<ResourceLocation, SFMClientAction<?>> actions
    ) {
        this(dispatcher, actions, List.of(PALETTE_ACTION_PREFIX));
    }

    private SFMClientActionCommandTree(
            CommandDispatcher<SFMClientActionSource> dispatcher,
            Map<ResourceLocation, SFMClientAction<?>> actions,
            List<String> paletteActionPrefixes
    ) {
        this(dispatcher, actions, paletteActionPrefixes, Map.of());
    }

    private SFMClientActionCommandTree(
            CommandDispatcher<SFMClientActionSource> dispatcher,
            Map<ResourceLocation, SFMClientAction<?>> actions,
            List<String> paletteActionPrefixes,
            Map<String, ResourceLocation> paletteChoiceActions
    ) {
        this.dispatcher = dispatcher;
        this.actions = Map.copyOf(actions);
        this.paletteActionPrefixes = List.copyOf(paletteActionPrefixes);
        this.paletteChoiceActions = Collections.unmodifiableMap(new LinkedHashMap<>(paletteChoiceActions));
        this.searchMetadata = this.actions.entrySet().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(
                Map.Entry::getKey,
                entry -> new ActionSearchMetadata(
                        entry.getKey().toString(),
                        entry.getKey().getPath(),
                        searchableComponentText(entry.getValue().title()),
                        searchableComponentText(entry.getValue().description())
                )
        ));
    }

    /**
     * Creates an isolated palette command surface without adding ephemeral
     * nodes to the process-wide client-action dispatcher.
     */
    public static SFMClientActionCommandTree isolatedPaletteSurface(
            CommandDispatcher<SFMClientActionSource> dispatcher,
            Map<ResourceLocation, SFMClientAction<?>> actions,
            String paletteActionPrefix,
            Map<String, ResourceLocation> paletteChoiceActions
    ) {
        return new SFMClientActionCommandTree(
                dispatcher, actions, List.of(paletteActionPrefix), paletteChoiceActions);
    }

    CommandDispatcher<SFMClientActionSource> dispatcher() {
        return dispatcher;
    }

    public ParseResults<SFMClientActionSource> parse(
            String command,
            SFMClientActionSource source
    ) {
        return dispatcher.parse(command, source);
    }

    public int execute(
            String command,
            SFMClientActionSource source
    ) throws CommandSyntaxException {
        return dispatcher.execute(command, source);
    }

    public int execute(ParseResults<SFMClientActionSource> parsed) throws CommandSyntaxException {
        return dispatcher.execute(parsed);
    }

    public CompletableFuture<Suggestions> getCompletionSuggestions(ParseResults<SFMClientActionSource> parsed) {
        SFMClientActionSource source = parsed.getContext().getSource();
        boolean parsedUnavailableAction = parsed
                .getContext()
                .getNodes()
                .stream()
                .map(node -> ResourceLocation.tryParse(node.getNode().getName()))
                .filter(actions::containsKey)
                .anyMatch(id -> !isAvailable(id, source));
        if (parsedUnavailableAction) {
            return Suggestions.empty();
        }
        long completionStarted = System.nanoTime();
        return dispatcher.getCompletionSuggestions(parsed).thenApply(suggestions -> {
            long elapsed = System.nanoTime() - completionStarted;
            if (elapsed > SLOW_COMPLETION_NANOS) {
                SFM.LOGGER.warn("Client-action completion took {} ms; inspect suggestion providers for synchronous work",
                        elapsed / 1_000_000L);
            }
            List<Suggestion> filtered = suggestions
                    .getList()
                    .stream()
                    .filter(suggestion -> {
                        ResourceLocation id = ResourceLocation.tryParse(suggestion.getText());
                        return !actions.containsKey(id) || isAvailable(id, source);
                    })
                    .toList();
            return new Suggestions(suggestions.getRange(), filtered);
        });
    }

    /**
     * Returns palette-only suggestions. Brigadier remains authoritative for
     * parsing and execution; this layer only broadens and ranks the action-id
     * candidates shown while the user is searching the palette.
     */
    public CompletableFuture<Suggestions> getPaletteSuggestions(
            String command,
            ParseResults<SFMClientActionSource> parsed
    ) {
        StringRange choiceRange = paletteChoiceRange(command);
        if (choiceRange != null) {
            String query = command.substring(choiceRange.getStart(), choiceRange.getEnd())
                    .toLowerCase(Locale.ROOT);
            SFMClientActionSource source = parsed.getContext().getSource();
            List<RankedChoice> ranked = new ArrayList<>();
            for (Map.Entry<String, ResourceLocation> choice : paletteChoiceActions.entrySet()) {
                if (!isAvailable(choice.getValue(), source)) continue;
                float score = choiceScore(query, choice.getKey(), searchMetadata.get(choice.getValue()));
                if (query.isBlank() || score <= 0.65f) {
                    ranked.add(new RankedChoice(
                            new Suggestion(choiceRange, choice.getKey()),
                            score,
                            choice.getKey()));
                }
            }
            if (!query.isBlank()) {
                ranked.sort(Comparator.comparingDouble(RankedChoice::score)
                        .thenComparing(RankedChoice::command));
            }
            return CompletableFuture.completedFuture(new Suggestions(
                    choiceRange,
                    ranked.stream().map(RankedChoice::suggestion).toList()));
        }
        return getCompletionSuggestions(parsed).thenApply(brigadierSuggestions -> {
            StringRange actionRange = actionIdRange(command);
            if (actionRange == null) {
                return fuzzyNestedLiteralSuggestions(command, parsed, brigadierSuggestions);
            }

            String query = command.substring(actionRange.getStart(), actionRange.getEnd())
                    .toLowerCase(Locale.ROOT);
            SFMClientActionSource source = parsed.getContext().getSource();
            List<RankedPaletteSuggestion> ranked = new ArrayList<>();
            for (Map.Entry<ResourceLocation, SFMClientAction<?>> action : actions.entrySet()) {
                if (!isAvailable(action.getKey(), source)) continue;
                float score = actionScore(query, searchMetadata.get(action.getKey()));
                if (query.isBlank() || score <= 0.65f) {
                    ranked.add(new RankedPaletteSuggestion(
                            new Suggestion(actionRange, action.getKey().toString()),
                            score,
                            action.getKey().toString()
                    ));
                }
            }
            if (!query.isBlank()) {
                ranked.addAll(literalContinuationSuggestions(
                        actionRange,
                        query,
                        parsed,
                        MAX_LITERAL_CONTINUATION_CANDIDATES));
            }
            ranked.sort(Comparator
                    .comparingDouble(RankedPaletteSuggestion::score)
                    .thenComparing(RankedPaletteSuggestion::command));
            return new Suggestions(
                    actionRange,
                    ranked.stream()
                            .distinct()
                            .limit(MAX_LITERAL_CONTINUATION_CANDIDATES)
                            .map(RankedPaletteSuggestion::suggestion)
                            .toList()
            );
        });
    }

    /**
     * Searches literal descendants without invoking argument suggestion
     * providers. This lets a query for a later grammar atom (for example
     * {@code term}) discover an executable continuation such as
     * {@code sfm:panel/open sfm:terminal}, while keeping completion bounded
     * and free of synchronous filesystem/network work.
     */
    private static List<RankedPaletteSuggestion> literalContinuationSuggestions(
            StringRange replacementRange,
            String query,
            ParseResults<SFMClientActionSource> parsed,
            int candidateLimit
    ) {
        List<com.mojang.brigadier.context.ParsedCommandNode<SFMClientActionSource>> nodes =
                parsed.getContext().getLastChild().getNodes();
        if (nodes.isEmpty()) return List.of();
        CommandNode<SFMClientActionSource> parent = nodes.get(nodes.size() - 1).getNode();
        SFMClientActionSource source = parsed.getContext().getSource();
        List<RankedPaletteSuggestion> result = new ArrayList<>();
        for (CommandNode<SFMClientActionSource> child : parent.getChildren()) {
            if (!(child instanceof LiteralCommandNode<SFMClientActionSource> actionLiteral)
                    || !actionLiteral.canUse(source)) continue;
            Set<CommandNode<SFMClientActionSource>> path = Collections.newSetFromMap(new IdentityHashMap<>());
            path.add(actionLiteral);
            collectMatchingLiteralContinuations(
                    actionLiteral,
                    actionLiteral.getLiteral(),
                    query,
                    replacementRange,
                    source,
                    0,
                    candidateLimit,
                    path,
                    result);
            if (result.size() >= candidateLimit) break;
        }
        return result;
    }

    private static void collectMatchingLiteralContinuations(
            LiteralCommandNode<SFMClientActionSource> parent,
            String commandPrefix,
            String query,
            StringRange replacementRange,
            SFMClientActionSource source,
            int depth,
            int candidateLimit,
            Set<CommandNode<SFMClientActionSource>> path,
            List<RankedPaletteSuggestion> result
    ) {
        if (depth >= MAX_LITERAL_CONTINUATION_DEPTH || result.size() >= candidateLimit) return;
        for (CommandNode<SFMClientActionSource> child : parent.getChildren()) {
            if (!(child instanceof LiteralCommandNode<SFMClientActionSource> literal)
                    || !literal.canUse(source)
                    || !path.add(literal)) continue;
            String continuation = commandPrefix + " " + literal.getLiteral();
            float score = literalScore(query, literal.getLiteral());
            if (score <= 0.65f) {
                result.add(new RankedPaletteSuggestion(
                        new Suggestion(replacementRange, continuation),
                        score + (depth + 1) * 0.001f,
                        continuation));
            }
            collectMatchingLiteralContinuations(
                    literal,
                    continuation,
                    query,
                    replacementRange,
                    source,
                    depth + 1,
                    candidateLimit,
                    path,
                    result);
            path.remove(literal);
            if (result.size() >= candidateLimit) return;
        }
    }

    /**
     * Brigadier only prefix-matches literal children. Keep its parser and
     * execution semantics authoritative, but broaden the palette's current
     * literal slot so nested resource IDs (notably panel scene IDs) receive
     * the same typo-tolerant discovery as top-level action IDs.
     */
    private static Suggestions fuzzyNestedLiteralSuggestions(
            String command,
            ParseResults<SFMClientActionSource> parsed,
            Suggestions brigadierSuggestions
    ) {
        int tokenStart = command.length();
        while (tokenStart > 0 && !Character.isWhitespace(command.charAt(tokenStart - 1))) tokenStart--;
        if (tokenStart == command.length()) return brigadierSuggestions;

        List<com.mojang.brigadier.context.ParsedCommandNode<SFMClientActionSource>> nodes =
                parsed.getContext().getLastChild().getNodes();
        if (nodes.isEmpty()) return brigadierSuggestions;
        var parent = nodes.get(nodes.size() - 1).getNode();
        String query = command.substring(tokenStart).toLowerCase(Locale.ROOT);
        StringRange range = StringRange.between(tokenStart, command.length());
        Map<String, RankedLiteral> candidates = new LinkedHashMap<>();
        for (var child : parent.getChildren()) {
            if (!(child instanceof LiteralCommandNode<SFMClientActionSource> literal)
                    || !literal.canUse(parsed.getContext().getSource())) continue;
            float score = literalScore(query, literal.getLiteral());
            if (score <= 0.65f) {
                candidates.put(literal.getLiteral(), new RankedLiteral(
                        new Suggestion(range, literal.getLiteral()),
                        score,
                        literal.getLiteral()
                ));
            }
        }
        if (candidates.isEmpty()) return brigadierSuggestions;

        for (Suggestion suggestion : brigadierSuggestions.getList()) {
            candidates.putIfAbsent(suggestion.getText(), new RankedLiteral(
                    new Suggestion(range, suggestion.getText(), suggestion.getTooltip()),
                    literalScore(query, suggestion.getText()),
                    suggestion.getText()
            ));
        }
        List<RankedLiteral> ranked = new ArrayList<>(candidates.values());
        ranked.sort(Comparator
                .comparingDouble(RankedLiteral::score)
                .thenComparing(RankedLiteral::literal));
        return new Suggestions(range, ranked.stream().map(RankedLiteral::suggestion).toList());
    }

    private StringRange actionIdRange(String command) {
        for (String prefix : paletteActionPrefixes) {
            if (!command.startsWith(prefix)) continue;
            int actionStart = prefix.length();
            for (int index = actionStart; index < command.length(); index++) {
                if (Character.isWhitespace(command.charAt(index))) return null;
            }
            return StringRange.between(actionStart, command.length());
        }
        return null;
    }

    private StringRange paletteChoiceRange(String command) {
        if (paletteChoiceActions.isEmpty()) return null;
        for (String prefix : paletteActionPrefixes) {
            if (command.startsWith(prefix)) {
                return StringRange.between(prefix.length(), command.length());
            }
        }
        return null;
    }

    private static float actionScore(String query, ActionSearchMetadata action) {
        if (query.isBlank()) return 0;
        String[] candidates = {
                action.id(),
                action.path(),
                action.title(),
                action.description()
        };
        float best = Float.MAX_VALUE;
        for (String candidate : candidates) {
            String normalized = candidate.toLowerCase(Locale.ROOT);
            float distance = ACTION_DISTANCE.distance(query, normalized)
                    / Math.max(1, Math.max(query.length(), normalized.length()));
            if (normalized.startsWith(query)) distance -= 0.05f;
            if (normalized.contains(query)) distance -= 0.5f;
            best = Math.min(best, distance);
        }
        return best;
    }

    private static float choiceScore(String query, String choice, ActionSearchMetadata action) {
        float score = actionScore(query, action);
        String normalized = choice.toLowerCase(Locale.ROOT);
        float choiceDistance = ACTION_DISTANCE.distance(query, normalized)
                / Math.max(1, Math.max(query.length(), normalized.length()));
        if (normalized.startsWith(query)) choiceDistance -= 0.05f;
        if (normalized.contains(query)) choiceDistance -= 0.5f;
        return Math.min(score, choiceDistance);
    }

    private static float literalScore(String query, String candidate) {
        String normalized = candidate.toLowerCase(Locale.ROOT);
        float distance = ACTION_DISTANCE.distance(query, normalized)
                / Math.max(1, Math.max(query.length(), normalized.length()));
        if (normalized.startsWith(query)) distance -= 0.05f;
        if (normalized.contains(query)) distance -= 0.5f;
        return distance;
    }

    /**
     * Extracts stable search material without resolving a translatable
     * component through Minecraft's global Language table. Completion-tree
     * construction also runs in headless/unit-test contexts where that table
     * is intentionally unavailable; the translation key remains useful
     * metadata because it contains the action's semantic words.
     */
    private static String searchableComponentText(Component component) {
        StringBuilder text = new StringBuilder();
        appendSearchableComponentText(component, text);
        return text.toString();
    }

    private static void appendSearchableComponentText(Component component, StringBuilder text) {
        ComponentContents contents = component.getContents();
        if (contents instanceof LiteralContents literal) {
            text.append(literal.text());
        } else if (contents instanceof TranslatableContents translatable) {
            text.append(translatable.getKey());
            for (Object argument : translatable.getArgs()) {
                if (argument instanceof Component child) appendSearchableComponentText(child, text);
            }
        } else {
            text.append(contents);
        }
        for (Component sibling : component.getSiblings()) {
            appendSearchableComponentText(sibling, text);
        }
    }

    private record RankedPaletteSuggestion(Suggestion suggestion, float score, String command) {
    }

    private record RankedLiteral(Suggestion suggestion, float score, String literal) {
    }

    private record RankedChoice(Suggestion suggestion, float score, String command) {
    }

    private record ActionSearchMetadata(String id, String path, String title, String description) {
    }

    private boolean isAvailable(
            ResourceLocation id,
            SFMClientActionSource source
    ) {
        SFMClientAction<?> action = actions.get(id);
        return action != null && action.requirement().resolve(source.context()).isAvailable();
    }
}
