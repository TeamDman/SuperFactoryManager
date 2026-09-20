package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.client.command.SFMCommandHistoryService;
import ca.teamdman.sfm.client.search.SFMFuzzyScorer;
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
import java.util.function.Supplier;

public final class SFMClientActionCommandTree {
    private static final String PALETTE_ACTION_PREFIX = "sfm action invoke ";
    private static final long SLOW_COMPLETION_NANOS = 100_000_000L;
    private static final int MAX_LITERAL_CONTINUATION_DEPTH = 8;
    private static final int MAX_LITERAL_CONTINUATION_CANDIDATES = 256;
    private static final float HISTORY_ACTION_BOOST = 0.02f;

    private final CommandDispatcher<SFMClientActionSource> dispatcher;
    private final Map<ResourceLocation, SFMClientAction<?>> actions;
    private final Map<ResourceLocation, ActionSearchMetadata> searchMetadata;
    private final List<String> paletteActionPrefixes;
    private final Map<String, ResourceLocation> paletteChoiceActions;
    private final Map<String, String> paletteChoiceDisplayTexts;
    private final Supplier<List<String>> historySuggestions;

    SFMClientActionCommandTree(
            CommandDispatcher<SFMClientActionSource> dispatcher,
            Map<ResourceLocation, SFMClientAction<?>> actions
    ) {
        this(dispatcher, actions, List.of(PALETTE_ACTION_PREFIX), Map.of(), Map.of(),
                SFMCommandHistoryService::suggestionsNewestFirst);
    }

    private SFMClientActionCommandTree(
            CommandDispatcher<SFMClientActionSource> dispatcher,
            Map<ResourceLocation, SFMClientAction<?>> actions,
            List<String> paletteActionPrefixes
    ) {
        this(dispatcher, actions, paletteActionPrefixes, Map.of(), Map.of(),
                SFMCommandHistoryService::suggestionsNewestFirst);
    }

    private SFMClientActionCommandTree(
            CommandDispatcher<SFMClientActionSource> dispatcher,
            Map<ResourceLocation, SFMClientAction<?>> actions,
            List<String> paletteActionPrefixes,
            Map<String, ResourceLocation> paletteChoiceActions
    ) {
        this(dispatcher, actions, paletteActionPrefixes, paletteChoiceActions, Map.of(),
                SFMCommandHistoryService::suggestionsNewestFirst);
    }

    SFMClientActionCommandTree(
            CommandDispatcher<SFMClientActionSource> dispatcher,
            Map<ResourceLocation, SFMClientAction<?>> actions,
            List<String> paletteActionPrefixes,
            Map<String, ResourceLocation> paletteChoiceActions,
            Supplier<List<String>> historySuggestions
    ) {
        this(dispatcher, actions, paletteActionPrefixes, paletteChoiceActions, Map.of(), historySuggestions);
    }

    SFMClientActionCommandTree(
            CommandDispatcher<SFMClientActionSource> dispatcher,
            Map<ResourceLocation, SFMClientAction<?>> actions,
            List<String> paletteActionPrefixes,
            Map<String, ResourceLocation> paletteChoiceActions,
            Map<String, String> paletteChoiceDisplayTexts,
            Supplier<List<String>> historySuggestions
    ) {
        this.dispatcher = dispatcher;
        this.actions = Map.copyOf(actions);
        this.paletteActionPrefixes = List.copyOf(paletteActionPrefixes);
        this.paletteChoiceActions = Collections.unmodifiableMap(new LinkedHashMap<>(paletteChoiceActions));
        this.paletteChoiceDisplayTexts = Collections.unmodifiableMap(new LinkedHashMap<>(paletteChoiceDisplayTexts));
        this.historySuggestions = historySuggestions;
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

    public static SFMClientActionCommandTree isolatedPaletteSurface(
            CommandDispatcher<SFMClientActionSource> dispatcher,
            Map<ResourceLocation, SFMClientAction<?>> actions,
            String paletteActionPrefix,
            Map<String, ResourceLocation> paletteChoiceActions,
            Map<String, String> paletteChoiceDisplayTexts
    ) {
        return new SFMClientActionCommandTree(
                dispatcher,
                actions,
                List.of(paletteActionPrefix),
                paletteChoiceActions,
                paletteChoiceDisplayTexts,
                SFMCommandHistoryService::suggestionsNewestFirst
        );
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
                        return id == null || !actions.containsKey(id) || isAvailable(id, source);
                    })
                    .toList();
            return new Suggestions(suggestions.getRange(), filtered);
        });
    }

    public SFMCommandFrontierAnalysis analyzeFrontier(
            String command,
            ParseResults<SFMClientActionSource> parsed
    ) {
        return SFMCommandFrontierAnalysis.analyze(command, parsed, dispatcher);
    }

    /**
     * Complete semantic frontier used by interactive palette guidance.  The
     * synchronous form remains useful for parse-only callers; this form adds
     * the concrete (possibly provider-backed) Brigadier suggestions for the
     * same parent/range/usage analysis instead of forcing UI callers to build a
     * parallel interpretation.
     */
    public CompletableFuture<SFMCommandFrontierAnalysis> analyzePaletteFrontier(
            String command,
            ParseResults<SFMClientActionSource> parsed
    ) {
        return getCompletionSuggestions(parsed).thenApply(suggestions ->
                analyzeFrontier(command, parsed).withSuggestions(suggestions));
    }

    /** Backwards-compatible Brigadier projection of the typed palette seam. */
    public CompletableFuture<Suggestions> getPaletteSuggestions(
            String command,
            ParseResults<SFMClientActionSource> parsed
    ) {
        return getPaletteCandidates(command, parsed).thenApply(candidates -> {
            List<Suggestion> suggestions = candidates.stream()
                    .filter(SFMPaletteCandidate::activatable)
                    .map(SFMPaletteCandidate::suggestion)
                    .distinct()
                    .toList();
            StringRange range = suggestions.isEmpty()
                    ? analyzeFrontier(command, parsed).replacementRange()
                    : suggestions.get(0).getRange();
            return new Suggestions(range, suggestions);
        });
    }

    /**
     * Returns typed palette candidates. Brigadier remains authoritative for
     * parsing, dynamic providers, replacement ranges, and execution; this
     * layer adds bounded fuzzy ranking, history, usage rows, and insertion
     * metadata for the palette presentation.
     */
    public CompletableFuture<List<SFMPaletteCandidate>> getPaletteCandidates(
            String command,
            ParseResults<SFMClientActionSource> parsed
    ) {
        return getPaletteCandidates(command, parsed, command.length());
    }

    public CompletableFuture<List<SFMPaletteCandidate>> getPaletteCandidates(
            String command, ParseResults<SFMClientActionSource> parsed, int cursor
    ) {
        // This seam is opt-in; it never probes arbitrary Brigadier providers or registry values.
        if (command.startsWith(PALETTE_ACTION_PREFIX)) {
            int end = command.indexOf(' ', PALETTE_ACTION_PREFIX.length());
            if (end > 0 && cursor > end) {
                ResourceLocation id = ResourceLocation.tryParse(command.substring(PALETTE_ACTION_PREFIX.length(), end));
                SFMClientAction<?> action = id == null ? null : actions.get(id);
                if (action instanceof SFMClientActionCompletion completion
                        && isAvailable(id, parsed.getContext().getSource())) {
                    var answer = completion.argumentCandidates(command, end + 1, cursor,
                            parsed.getContext().getSource().context());
                    if (answer.isPresent()) return CompletableFuture.completedFuture(answer.get().stream().limit(256).toList());
                }
            }
        }
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
            String frontier = "choice@" + choiceRange.getStart();
            return CompletableFuture.completedFuture(ranked.stream()
                    .map(choice -> SFMPaletteCandidate.activatable(
                            choice.suggestion(),
                            paletteChoiceDisplayTexts.getOrDefault(choice.command(), choice.command()),
                            SFMPaletteCandidate.Kind.ACTION_BOUNDARY,
                            SFMPaletteCandidate.Origin.CHOICE_SURFACE,
                            paletteChoiceActions.get(choice.command()),
                            frontier,
                            SFMPaletteCandidate.NO_HISTORY,
                            null
                    ))
                    .toList());
        }
        return getCompletionSuggestions(parsed).thenApply(brigadierSuggestions -> {
            SFMCommandFrontierAnalysis frontier = analyzeFrontier(command, parsed)
                    .withSuggestions(brigadierSuggestions);
            StringRange actionRange = actionIdRange(command);
            if (actionRange == null) {
                Suggestions fuzzy = fuzzyNestedLiteralSuggestions(command, parsed, brigadierSuggestions);
                return nestedCandidates(command, parsed, fuzzy, frontier.withSuggestions(fuzzy));
            }

            String query = command.substring(actionRange.getStart(), actionRange.getEnd())
                    .toLowerCase(Locale.ROOT);
            SFMClientActionSource source = parsed.getContext().getSource();
            List<RankedPaletteCandidate> ranked = new ArrayList<>();
            List<String> recentHistory = availableHistory(source);
            Map<ResourceLocation, Integer> actionHistoryRecency = new LinkedHashMap<>();
            for (int index = 0; index < recentHistory.size(); index++) {
                ResourceLocation actionId = historyActionId(recentHistory.get(index));
                if (actionId != null) actionHistoryRecency.putIfAbsent(actionId, index);
            }
            String frontierId = "action-id@" + actionRange.getStart();
            if (query.isBlank()) {
                for (int index = 0; index < recentHistory.size(); index++) {
                    String historyCommand = recentHistory.get(index);
                    ResourceLocation actionId = historyActionId(historyCommand);
                    Suggestion suggestion = new Suggestion(actionRange, historySuffix(historyCommand));
                    ranked.add(new RankedPaletteCandidate(
                            SFMPaletteCandidate.activatable(
                                    suggestion,
                                    SFMPaletteCandidate.Kind.COMPLETE_HISTORY_COMMAND,
                                    SFMPaletteCandidate.Origin.COMMAND_HISTORY,
                                    actionId,
                                    frontierId,
                                    index,
                                    null
                            ),
                            -1.0f + index * 0.0001f,
                            historyCommand));
                }
            } else {
                for (int index = 0; index < recentHistory.size(); index++) {
                    String historyCommand = recentHistory.get(index);
                    ResourceLocation actionId = historyActionId(historyCommand);
                    ActionSearchMetadata metadata = searchMetadata.get(actionId);
                    if (metadata == null) continue;
                    float score = actionScore(query, metadata);
                    if (score <= 0.65f) {
                        Suggestion suggestion = new Suggestion(actionRange, historySuffix(historyCommand));
                        ranked.add(new RankedPaletteCandidate(
                                SFMPaletteCandidate.activatable(
                                        suggestion,
                                        SFMPaletteCandidate.Kind.COMPLETE_HISTORY_COMMAND,
                                        SFMPaletteCandidate.Origin.COMMAND_HISTORY,
                                        actionId,
                                        frontierId,
                                        index,
                                        null
                                ),
                                score + Math.min(0.01f, index * 0.0001f),
                                historyCommand));
                    }
                }
            }
            for (Map.Entry<ResourceLocation, SFMClientAction<?>> action : actions.entrySet()) {
                if (!isAvailable(action.getKey(), source)) continue;
                float score = action.getKey().toString().equals(query)
                        ? -1.0f
                        : actionScore(query, searchMetadata.get(action.getKey()));
                if (query.isBlank() || score <= 0.65f) {
                    Integer recency = actionHistoryRecency.get(action.getKey());
                    float historyBoost = query.isBlank() || recency == null
                            ? 0.0f
                            : Math.max(0.001f, HISTORY_ACTION_BOOST - recency * 0.0001f);
                    ranked.add(new RankedPaletteCandidate(
                            SFMPaletteCandidate.activatable(
                                    new Suggestion(actionRange, action.getKey().toString()),
                                    SFMPaletteCandidate.Kind.ACTION_BOUNDARY,
                                    SFMPaletteCandidate.Origin.ACTION_REGISTRY,
                                    action.getKey(),
                                    frontierId,
                                    recency == null ? SFMPaletteCandidate.NO_HISTORY : recency,
                                    null
                            ),
                            score - historyBoost,
                            action.getKey().toString()
                    ));
                    if (!query.isBlank() && action.getValue() instanceof SFMClientActionCompletion completion) {
                        for (var continuation : completion.contextualContinuations(source.context()).stream().limit(256).toList()) {
                            String tail = action.getKey() + " " + continuation.arguments();
                            ranked.add(new RankedPaletteCandidate(SFMPaletteCandidate.activatable(
                                    new Suggestion(actionRange, tail), continuation.displayText(),
                                    SFMPaletteCandidate.Kind.LITERAL_CONTINUATION, SFMPaletteCandidate.Origin.LITERAL_DISCOVERY,
                                    action.getKey(), frontierId, SFMPaletteCandidate.NO_HISTORY, null), score + 0.01f, tail));
                        }
                    }
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
                    .comparingDouble(RankedPaletteCandidate::score)
                    .thenComparingInt(candidate -> candidateKindOrder(candidate.candidate().kind()))
                    .thenComparing(RankedPaletteCandidate::command));
            return distinctCandidates(ranked.stream()
                    .map(RankedPaletteCandidate::candidate)
                    .limit(MAX_LITERAL_CONTINUATION_CANDIDATES)
                    .toList());
        });
    }

    private List<SFMPaletteCandidate> nestedCandidates(
            String command,
            ParseResults<SFMClientActionSource> parsed,
            Suggestions brigadierSuggestions,
            SFMCommandFrontierAnalysis frontier
    ) {
        ResourceLocation actionId = parsedActionId(parsed);
        List<String> history = availableHistory(parsed.getContext().getSource());
        List<SFMClientActionArgumentHistory.HistoricalArgument> historical =
                SFMClientActionArgumentHistory.suggestions(
                        command,
                        parsed,
                        frontier,
                        dispatcher,
                        history
                );
        Map<String, Suggestion> brigadierByText = new LinkedHashMap<>();
        for (Suggestion suggestion : brigadierSuggestions.getList()) {
            brigadierByText.putIfAbsent(suggestion.getText(), suggestion);
        }
        ArrayList<SFMPaletteCandidate> result = new ArrayList<>();
        for (SFMClientActionArgumentHistory.HistoricalArgument argument : historical) {
            Suggestion registered = brigadierByText.get(argument.value());
            Suggestion suggestion = registered == null
                    ? new Suggestion(frontier.replacementRange(), argument.value())
                    : registered;
            result.add(SFMPaletteCandidate.activatable(
                    suggestion,
                    SFMPaletteCandidate.Kind.ARGUMENT_VALUE,
                    SFMPaletteCandidate.Origin.COMMAND_HISTORY,
                    actionId,
                    frontier.frontierId(),
                    argument.historyRecency(),
                    argument.familyId()
            ));
        }
        for (Suggestion suggestion : brigadierSuggestions.getList()) {
            boolean literal = frontier.parent().getChildren().stream()
                    .anyMatch(child -> child instanceof LiteralCommandNode<?>
                            && child.getName().equals(suggestion.getText()));
            result.add(SFMPaletteCandidate.activatable(
                    suggestion,
                    literal
                            ? SFMPaletteCandidate.Kind.LITERAL_CONTINUATION
                            : SFMPaletteCandidate.Kind.ARGUMENT_VALUE,
                    SFMPaletteCandidate.Origin.BRIGADIER,
                    actionId,
                    frontier.frontierId(),
                    SFMPaletteCandidate.NO_HISTORY,
                    null
            ));
        }
        for (String usage : frontier.usageDisplayRows()) {
            result.add(SFMPaletteCandidate.usageHint(
                    frontier.replacementRange(),
                    usage,
                    SFMPaletteCandidate.Origin.SMART_USAGE,
                    actionId,
                    frontier.frontierId()
            ));
        }
        if (result.isEmpty()) {
            for (String diagnostic : frontier.diagnostics()) {
                result.add(SFMPaletteCandidate.usageHint(
                        frontier.replacementRange(),
                        "Invalid: " + diagnostic,
                        SFMPaletteCandidate.Origin.PARSE_DIAGNOSTIC,
                        actionId,
                        frontier.frontierId()
                ));
            }
        }
        return distinctCandidates(result);
    }

    private ResourceLocation parsedActionId(ParseResults<SFMClientActionSource> parsed) {
        return parsed.getContext().getNodes().stream()
                .map(node -> ResourceLocation.tryParse(node.getNode().getName()))
                .filter(actions::containsKey)
                .findFirst()
                .orElse(null);
    }

    private static int candidateKindOrder(SFMPaletteCandidate.Kind kind) {
        return switch (kind) {
            case ACTION_BOUNDARY -> 0;
            case COMPLETE_HISTORY_COMMAND -> 1;
            case LITERAL_CONTINUATION -> 2;
            case ARGUMENT_VALUE -> 3;
            case USAGE_HINT -> 4;
        };
    }

    private static List<SFMPaletteCandidate> distinctCandidates(List<SFMPaletteCandidate> candidates) {
        LinkedHashMap<String, SFMPaletteCandidate> result = new LinkedHashMap<>();
        for (SFMPaletteCandidate candidate : candidates) {
            String key = candidate.activatable()
                    ? candidate.replacementRange().getStart() + ":"
                            + candidate.replacementRange().getEnd() + ":" + candidate.replacementText()
                    : "usage:" + candidate.displayText();
            result.putIfAbsent(key, candidate);
        }
        return List.copyOf(result.values());
    }

    /**
     * Searches literal descendants without invoking argument suggestion
     * providers. This lets a query for a later grammar atom (for example
     * {@code term}) discover an executable continuation such as
     * {@code sfm:panel/open sfm:terminal}, while keeping completion bounded
     * and free of synchronous filesystem/network work.
     */
    private static List<RankedPaletteCandidate> literalContinuationSuggestions(
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
        List<RankedPaletteCandidate> result = new ArrayList<>();
        String frontier = "literal-discovery@" + replacementRange.getStart();
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
                    frontier,
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
            String frontier,
            List<RankedPaletteCandidate> result
    ) {
        if (depth >= MAX_LITERAL_CONTINUATION_DEPTH || result.size() >= candidateLimit) return;
        for (CommandNode<SFMClientActionSource> child : parent.getChildren()) {
            if (!(child instanceof LiteralCommandNode<SFMClientActionSource> literal)
                    || !literal.canUse(source)
                    || !path.add(literal)) continue;
            String continuation = commandPrefix + " " + literal.getLiteral();
            float score = literalScore(query, literal.getLiteral());
            if (score <= 0.65f) {
                ResourceLocation actionId = firstResourceLocationToken(commandPrefix);
                result.add(new RankedPaletteCandidate(
                        SFMPaletteCandidate.activatable(
                                new Suggestion(replacementRange, continuation),
                                SFMPaletteCandidate.Kind.LITERAL_CONTINUATION,
                                SFMPaletteCandidate.Origin.LITERAL_DISCOVERY,
                                actionId,
                                frontier,
                                SFMPaletteCandidate.NO_HISTORY,
                                null
                        ),
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
                    frontier,
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
            best = Math.min(best, SFMFuzzyScorer.score(query, candidate));
        }
        return best;
    }

    private static float choiceScore(String query, String choice, ActionSearchMetadata action) {
        float score = actionScore(query, action);
        return Math.min(score, SFMFuzzyScorer.score(query, choice));
    }

    private static float literalScore(String query, String candidate) {
        return SFMFuzzyScorer.score(query, candidate);
    }

    private static ResourceLocation firstResourceLocationToken(String value) {
        int separator = 0;
        while (separator < value.length() && !Character.isWhitespace(value.charAt(separator))) separator++;
        return ResourceLocation.tryParse(value.substring(0, separator));
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

    private record RankedPaletteCandidate(SFMPaletteCandidate candidate, float score, String command) {
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

    private List<String> availableHistory(SFMClientActionSource source) {
        List<String> result = new ArrayList<>();
        Set<String> seen = new java.util.LinkedHashSet<>();
        for (String command : historySuggestions.get()) {
            if (!seen.add(command)) continue;
            ResourceLocation actionId = historyActionId(command);
            if (actionId == null || !isAvailable(actionId, source)) continue;
            ParseResults<SFMClientActionSource> parsed = parse(command, source);
            if (SFMClientActionExecutor.isExecutable(parsed)) result.add(command);
        }
        return result;
    }

    private static String historySuffix(String command) {
        int prefixEnd = command.indexOf("sfm action invoke ");
        return prefixEnd < 0 ? command : command.substring(prefixEnd + "sfm action invoke ".length());
    }

    private static ResourceLocation historyActionId(String command) {
        String suffix = historySuffix(command).stripLeading();
        int end = 0;
        while (end < suffix.length() && !Character.isWhitespace(suffix.charAt(end))) end++;
        if (end == 0) return null;
        return ResourceLocation.tryParse(suffix.substring(0, end));
    }

}
