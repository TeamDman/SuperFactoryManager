package ca.teamdman.sfm.client.action;

{% if features.client_action_completion_diagnostics %}
import ca.teamdman.sfm.SFM;
{% endif %}
{% if features.command_history %}
import ca.teamdman.sfm.client.command.SFMCommandHistoryService;
{% endif %}
{% if features.typed_command_palette %}
import ca.teamdman.sfm.client.search.SFMFuzzyScorer;
{% endif %}
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
{% if features.command_palette %}
import com.mojang.brigadier.context.StringRange;
{% endif %}
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.Suggestions;
{% if features.typed_command_palette %}
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentContents;
import net.minecraft.network.chat.contents.LiteralContents;
import net.minecraft.network.chat.contents.TranslatableContents;
{% endif %}
{% case minecraft_version %}
{% when "26.1.2" %}
import net.minecraft.resources.Identifier;
{% else %}
import net.minecraft.resources.ResourceLocation;
{% endcase %}
{% if features.command_palette %}
{% if features.typed_command_palette %}
{% else %}
import org.simmetrics.StringDistance;
import org.simmetrics.metrics.StringDistances;
{% endif %}
{% endif %}

{% if features.command_palette %}
import java.util.ArrayList;
{% endif %}
{% if features.typed_command_palette %}
import java.util.Collections;
{% endif %}
{% if features.command_palette %}
import java.util.Comparator;
{% endif %}
{% if features.typed_command_palette %}
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
{% endif %}
import java.util.List;
{% if features.command_palette %}
import java.util.Locale;
{% endif %}
import java.util.Map;
{% if features.typed_command_palette %}
import java.util.Set;
{% endif %}
import java.util.concurrent.CompletableFuture;
{% if features.command_history %}
import java.util.function.Supplier;
{% endif %}

public final class SFMClientActionCommandTree {
{% if features.command_palette %}
    private static final String PALETTE_ACTION_PREFIX = "sfm action invoke ";
{% endif %}
{% if features.client_action_completion_diagnostics %}
    private static final long SLOW_COMPLETION_NANOS = 100_000_000L;
{% endif %}
{% if features.typed_command_palette %}
    private static final int MAX_LITERAL_CONTINUATION_DEPTH = 8;
    private static final int MAX_LITERAL_CONTINUATION_CANDIDATES = 256;
{% endif %}
{% if features.command_history %}
    private static final float HISTORY_ACTION_BOOST = 0.02f;
{% endif %}
{% if features.command_palette %}
{% if features.typed_command_palette %}
{% else %}
    private static final StringDistance ACTION_DISTANCE = StringDistances.damerauLevenshtein();
{% endif %}
{% endif %}

    private final CommandDispatcher<SFMClientActionSource> dispatcher;
{% case minecraft_version %}
{% when "26.1.2" %}
    private final Map<Identifier, SFMClientAction<?>> actions;
{% else %}
    private final Map<ResourceLocation, SFMClientAction<?>> actions;
{% endcase %}
{% if features.typed_command_palette %}
{% case minecraft_version %}
{% when "26.1.2" %}
    private final Map<Identifier, ActionSearchMetadata> searchMetadata;
{% else %}
    private final Map<ResourceLocation, ActionSearchMetadata> searchMetadata;
{% endcase %}
    private final List<String> paletteActionPrefixes;
{% endif %}
{% if features.context_actions %}
{% case minecraft_version %}
{% when "26.1.2" %}
    private final Map<String, Identifier> paletteChoiceActions;
{% else %}
    private final Map<String, ResourceLocation> paletteChoiceActions;
{% endcase %}
    private final Map<String, String> paletteChoiceDisplayTexts;
{% endif %}
{% if features.command_history %}
    private final Supplier<List<String>> historySuggestions;
{% endif %}

    SFMClientActionCommandTree(
            CommandDispatcher<SFMClientActionSource> dispatcher,
{% case minecraft_version %}
{% when "26.1.2" %}
            Map<Identifier, SFMClientAction<?>> actions
{% else %}
            Map<ResourceLocation, SFMClientAction<?>> actions
{% endcase %}
    ) {
{% if features.typed_command_palette %}
{% if features.context_actions %}
{% if features.command_history %}
        this(dispatcher, actions, List.of(PALETTE_ACTION_PREFIX), Map.of(), Map.of(),
                SFMCommandHistoryService::suggestionsNewestFirst);
{% else %}
        this(dispatcher, actions, List.of(PALETTE_ACTION_PREFIX), Map.of(), Map.of());
{% endif %}
{% else %}
{% if features.command_history %}
        this(dispatcher, actions, List.of(PALETTE_ACTION_PREFIX),
                SFMCommandHistoryService::suggestionsNewestFirst);
{% else %}
        this(dispatcher, actions, List.of(PALETTE_ACTION_PREFIX));
{% endif %}
{% endif %}
{% else %}
        this.dispatcher = dispatcher;
        this.actions = Map.copyOf(actions);
{% endif %}
    }

{% if features.typed_command_palette %}
{% if features.context_actions %}
    private SFMClientActionCommandTree(
            CommandDispatcher<SFMClientActionSource> dispatcher,
{% case minecraft_version %}
{% when "26.1.2" %}
            Map<Identifier, SFMClientAction<?>> actions,
{% else %}
            Map<ResourceLocation, SFMClientAction<?>> actions,
{% endcase %}
            List<String> paletteActionPrefixes
    ) {
{% if features.command_history %}
        this(dispatcher, actions, paletteActionPrefixes, Map.of(), Map.of(),
                SFMCommandHistoryService::suggestionsNewestFirst);
{% else %}
        this(dispatcher, actions, paletteActionPrefixes, Map.of(), Map.of());
{% endif %}
    }

    private SFMClientActionCommandTree(
            CommandDispatcher<SFMClientActionSource> dispatcher,
{% case minecraft_version %}
{% when "26.1.2" %}
            Map<Identifier, SFMClientAction<?>> actions,
{% else %}
            Map<ResourceLocation, SFMClientAction<?>> actions,
{% endcase %}
            List<String> paletteActionPrefixes,
{% case minecraft_version %}
{% when "26.1.2" %}
            Map<String, Identifier> paletteChoiceActions
{% else %}
            Map<String, ResourceLocation> paletteChoiceActions
{% endcase %}
    ) {
{% if features.command_history %}
        this(dispatcher, actions, paletteActionPrefixes, paletteChoiceActions, Map.of(),
                SFMCommandHistoryService::suggestionsNewestFirst);
{% else %}
        this(dispatcher, actions, paletteActionPrefixes, paletteChoiceActions, Map.of());
{% endif %}
    }

{% if features.command_history %}
    SFMClientActionCommandTree(
            CommandDispatcher<SFMClientActionSource> dispatcher,
{% case minecraft_version %}
{% when "26.1.2" %}
            Map<Identifier, SFMClientAction<?>> actions,
{% else %}
            Map<ResourceLocation, SFMClientAction<?>> actions,
{% endcase %}
            List<String> paletteActionPrefixes,
{% case minecraft_version %}
{% when "26.1.2" %}
            Map<String, Identifier> paletteChoiceActions,
{% else %}
            Map<String, ResourceLocation> paletteChoiceActions,
{% endcase %}
            Supplier<List<String>> historySuggestions
    ) {
        this(dispatcher, actions, paletteActionPrefixes, paletteChoiceActions, Map.of(), historySuggestions);
    }

{% endif %}
{% endif %}
    SFMClientActionCommandTree(
            CommandDispatcher<SFMClientActionSource> dispatcher,
{% case minecraft_version %}
{% when "26.1.2" %}
            Map<Identifier, SFMClientAction<?>> actions,
{% else %}
            Map<ResourceLocation, SFMClientAction<?>> actions,
{% endcase %}
{% if features.context_actions %}
            List<String> paletteActionPrefixes,
{% case minecraft_version %}
{% when "26.1.2" %}
            Map<String, Identifier> paletteChoiceActions,
{% else %}
            Map<String, ResourceLocation> paletteChoiceActions,
{% endcase %}
{% if features.command_history %}
            Map<String, String> paletteChoiceDisplayTexts,
            Supplier<List<String>> historySuggestions
{% else %}
            Map<String, String> paletteChoiceDisplayTexts
{% endif %}
{% else %}
{% if features.command_history %}
            List<String> paletteActionPrefixes,
            Supplier<List<String>> historySuggestions
{% else %}
            List<String> paletteActionPrefixes
{% endif %}
{% endif %}
    ) {
        this.dispatcher = dispatcher;
        this.actions = Map.copyOf(actions);
        this.paletteActionPrefixes = List.copyOf(paletteActionPrefixes);
{% if features.context_actions %}
        this.paletteChoiceActions = Collections.unmodifiableMap(new LinkedHashMap<>(paletteChoiceActions));
        this.paletteChoiceDisplayTexts = Collections.unmodifiableMap(new LinkedHashMap<>(paletteChoiceDisplayTexts));
{% endif %}
{% if features.command_history %}
        this.historySuggestions = historySuggestions;
{% endif %}
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

{% endif %}
{% if features.context_actions %}
    /**
     * Creates an isolated palette command surface without adding ephemeral
     * nodes to the process-wide client-action dispatcher.
     */
    public static SFMClientActionCommandTree isolatedPaletteSurface(
            CommandDispatcher<SFMClientActionSource> dispatcher,
{% case minecraft_version %}
{% when "26.1.2" %}
            Map<Identifier, SFMClientAction<?>> actions,
{% else %}
            Map<ResourceLocation, SFMClientAction<?>> actions,
{% endcase %}
            String paletteActionPrefix,
{% case minecraft_version %}
{% when "26.1.2" %}
            Map<String, Identifier> paletteChoiceActions
{% else %}
            Map<String, ResourceLocation> paletteChoiceActions
{% endcase %}
    ) {
        return new SFMClientActionCommandTree(
                dispatcher, actions, List.of(paletteActionPrefix), paletteChoiceActions);
    }

    public static SFMClientActionCommandTree isolatedPaletteSurface(
            CommandDispatcher<SFMClientActionSource> dispatcher,
{% case minecraft_version %}
{% when "26.1.2" %}
            Map<Identifier, SFMClientAction<?>> actions,
{% else %}
            Map<ResourceLocation, SFMClientAction<?>> actions,
{% endcase %}
            String paletteActionPrefix,
{% case minecraft_version %}
{% when "26.1.2" %}
            Map<String, Identifier> paletteChoiceActions,
{% else %}
            Map<String, ResourceLocation> paletteChoiceActions,
{% endcase %}
            Map<String, String> paletteChoiceDisplayTexts
    ) {
        return new SFMClientActionCommandTree(
                dispatcher,
                actions,
                List.of(paletteActionPrefix),
                paletteChoiceActions,
{% if features.command_history %}
                paletteChoiceDisplayTexts,
                SFMCommandHistoryService::suggestionsNewestFirst
{% else %}
                paletteChoiceDisplayTexts
{% endif %}
        );
    }

{% endif %}
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
{% case minecraft_version %}
{% when "26.1.2" %}
                .map(node -> Identifier.tryParse(node.getNode().getName()))
{% else %}
                .map(node -> ResourceLocation.tryParse(node.getNode().getName()))
{% endcase %}
                .filter(actions::containsKey)
                .anyMatch(id -> !isAvailable(id, source));
        if (parsedUnavailableAction) {
            return Suggestions.empty();
        }
{% if features.client_action_completion_diagnostics %}
        long completionStarted = System.nanoTime();
{% endif %}
        return dispatcher.getCompletionSuggestions(parsed).thenApply(suggestions -> {
{% if features.client_action_completion_diagnostics %}
            long elapsed = System.nanoTime() - completionStarted;
            if (elapsed > SLOW_COMPLETION_NANOS) {
                SFM.LOGGER.warn("Client-action completion took {} ms; inspect suggestion providers for synchronous work",
                        elapsed / 1_000_000L);
            }
{% endif %}
            List<Suggestion> filtered = suggestions
                    .getList()
                    .stream()
                    .filter(suggestion -> {
{% case minecraft_version %}
{% when "26.1.2" %}
                        Identifier id = Identifier.tryParse(suggestion.getText());
{% else %}
                        ResourceLocation id = ResourceLocation.tryParse(suggestion.getText());
{% endcase %}
{% if features.client_action_nullable_suggestions %}
                        return id == null || !actions.containsKey(id) || isAvailable(id, source);
{% else %}
                        return !actions.containsKey(id) || isAvailable(id, source);
{% endif %}
                    })
                    .toList();
            return new Suggestions(suggestions.getRange(), filtered);
        });
    }

{% if features.command_palette %}
{% if features.typed_command_palette %}
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
{% case minecraft_version %}
{% when "26.1.2" %}
                Identifier id = Identifier.tryParse(command.substring(PALETTE_ACTION_PREFIX.length(), end));
{% else %}
                ResourceLocation id = ResourceLocation.tryParse(command.substring(PALETTE_ACTION_PREFIX.length(), end));
{% endcase %}
                SFMClientAction<?> action = id == null ? null : actions.get(id);
                if (action instanceof SFMClientActionCompletion completion
                        && isAvailable(id, parsed.getContext().getSource())) {
                    var answer = completion.argumentCandidates(command, end + 1, cursor,
                            parsed.getContext().getSource().context());
                    if (answer.isPresent()) return CompletableFuture.completedFuture(answer.get().stream().limit(256).toList());
                }
            }
        }
{% if features.context_actions %}
        StringRange choiceRange = paletteChoiceRange(command);
        if (choiceRange != null) {
            String query = command.substring(choiceRange.getStart(), choiceRange.getEnd())
                    .toLowerCase(Locale.ROOT);
            SFMClientActionSource source = parsed.getContext().getSource();
            List<RankedChoice> ranked = new ArrayList<>();
{% case minecraft_version %}
{% when "26.1.2" %}
            for (Map.Entry<String, Identifier> choice : paletteChoiceActions.entrySet()) {
{% else %}
            for (Map.Entry<String, ResourceLocation> choice : paletteChoiceActions.entrySet()) {
{% endcase %}
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
{% endif %}
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
{% if features.command_history %}
            List<String> recentHistory = availableHistory(source);
{% case minecraft_version %}
{% when "26.1.2" %}
            Map<Identifier, Integer> actionHistoryRecency = new LinkedHashMap<>();
{% else %}
            Map<ResourceLocation, Integer> actionHistoryRecency = new LinkedHashMap<>();
{% endcase %}
            for (int index = 0; index < recentHistory.size(); index++) {
{% case minecraft_version %}
{% when "26.1.2" %}
                Identifier actionId = historyActionId(recentHistory.get(index));
{% else %}
                ResourceLocation actionId = historyActionId(recentHistory.get(index));
{% endcase %}
                if (actionId != null) actionHistoryRecency.putIfAbsent(actionId, index);
            }
{% endif %}
            String frontierId = "action-id@" + actionRange.getStart();
{% if features.command_history %}
            if (query.isBlank()) {
                for (int index = 0; index < recentHistory.size(); index++) {
                    String historyCommand = recentHistory.get(index);
{% case minecraft_version %}
{% when "26.1.2" %}
                    Identifier actionId = historyActionId(historyCommand);
{% else %}
                    ResourceLocation actionId = historyActionId(historyCommand);
{% endcase %}
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
{% case minecraft_version %}
{% when "26.1.2" %}
                    Identifier actionId = historyActionId(historyCommand);
{% else %}
                    ResourceLocation actionId = historyActionId(historyCommand);
{% endcase %}
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
{% endif %}
{% case minecraft_version %}
{% when "26.1.2" %}
            for (Map.Entry<Identifier, SFMClientAction<?>> action : actions.entrySet()) {
{% else %}
            for (Map.Entry<ResourceLocation, SFMClientAction<?>> action : actions.entrySet()) {
{% endcase %}
                if (!isAvailable(action.getKey(), source)) continue;
                float score = action.getKey().toString().equals(query)
                        ? -1.0f
                        : actionScore(query, searchMetadata.get(action.getKey()));
                if (query.isBlank() || score <= 0.65f) {
{% if features.command_history %}
                    Integer recency = actionHistoryRecency.get(action.getKey());
                    float historyBoost = query.isBlank() || recency == null
                            ? 0.0f
                            : Math.max(0.001f, HISTORY_ACTION_BOOST - recency * 0.0001f);
{% endif %}
                    ranked.add(new RankedPaletteCandidate(
                            SFMPaletteCandidate.activatable(
                                    new Suggestion(actionRange, action.getKey().toString()),
                                    SFMPaletteCandidate.Kind.ACTION_BOUNDARY,
                                    SFMPaletteCandidate.Origin.ACTION_REGISTRY,
                                    action.getKey(),
                                    frontierId,
{% if features.command_history %}
                                    recency == null ? SFMPaletteCandidate.NO_HISTORY : recency,
{% else %}
                                    SFMPaletteCandidate.NO_HISTORY,
{% endif %}
                                    null
                            ),
{% if features.command_history %}
                            score - historyBoost,
{% else %}
                            score,
{% endif %}
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
{% case minecraft_version %}
{% when "26.1.2" %}
        Identifier actionId = parsedActionId(parsed);
{% else %}
        ResourceLocation actionId = parsedActionId(parsed);
{% endcase %}
{% if features.command_history %}
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
{% endif %}
        ArrayList<SFMPaletteCandidate> result = new ArrayList<>();
{% if features.command_history %}
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
{% endif %}
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

{% case minecraft_version %}
{% when "26.1.2" %}
    private Identifier parsedActionId(ParseResults<SFMClientActionSource> parsed) {
{% else %}
    private ResourceLocation parsedActionId(ParseResults<SFMClientActionSource> parsed) {
{% endcase %}
        return parsed.getContext().getNodes().stream()
{% case minecraft_version %}
{% when "26.1.2" %}
                .map(node -> Identifier.tryParse(node.getNode().getName()))
{% else %}
                .map(node -> ResourceLocation.tryParse(node.getNode().getName()))
{% endcase %}
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
{% case minecraft_version %}
{% when "26.1.2" %}
                Identifier actionId = firstIdentifierToken(commandPrefix);
{% else %}
                ResourceLocation actionId = firstResourceLocationToken(commandPrefix);
{% endcase %}
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

{% else %}
    /**
     * Returns palette-only suggestions. Brigadier remains authoritative for
     * parsing and execution; this layer only broadens and ranks the action-id
     * candidates shown while the user is searching the palette.
     */
    public CompletableFuture<Suggestions> getPaletteSuggestions(
            String command,
            ParseResults<SFMClientActionSource> parsed
    ) {
        return getCompletionSuggestions(parsed).thenApply(brigadierSuggestions -> {
            StringRange actionRange = actionIdRange(command);
            if (actionRange == null) return brigadierSuggestions;

            String query = command.substring(actionRange.getStart(), actionRange.getEnd())
                    .toLowerCase(Locale.ROOT);
            SFMClientActionSource source = parsed.getContext().getSource();
            List<RankedAction> ranked = new ArrayList<>();
{% case minecraft_version %}
{% when "26.1.2" %}
            for (Map.Entry<Identifier, SFMClientAction<?>> action : actions.entrySet()) {
{% else %}
            for (Map.Entry<ResourceLocation, SFMClientAction<?>> action : actions.entrySet()) {
{% endcase %}
                if (!isAvailable(action.getKey(), source)) continue;
                float score = actionScore(query, action);
                if (query.isBlank() || score <= 0.65f) {
                    ranked.add(new RankedAction(
                            new Suggestion(actionRange, action.getKey().toString()),
                            score,
                            action.getKey().toString()
                    ));
                }
            }
            ranked.sort(Comparator
                    .comparingDouble(RankedAction::score)
                    .thenComparing(RankedAction::id));
            return new Suggestions(
                    actionRange,
                    ranked.stream().map(RankedAction::suggestion).toList()
            );
        });
    }

{% endif %}
{% endif %}
{% if features.command_palette %}
{% if features.typed_command_palette %}
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

{% else %}
    private static StringRange actionIdRange(String command) {
        if (!command.startsWith(PALETTE_ACTION_PREFIX)) return null;
        int actionStart = PALETTE_ACTION_PREFIX.length();
        for (int index = actionStart; index < command.length(); index++) {
            if (Character.isWhitespace(command.charAt(index))) return null;
        }
        return StringRange.between(actionStart, command.length());
    }

{% endif %}
{% endif %}
{% if features.context_actions %}
    private StringRange paletteChoiceRange(String command) {
        if (paletteChoiceActions.isEmpty()) return null;
        for (String prefix : paletteActionPrefixes) {
            if (command.startsWith(prefix)) {
                return StringRange.between(prefix.length(), command.length());
            }
        }
        return null;
    }

{% endif %}
{% if features.command_palette %}
{% if features.typed_command_palette %}
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

{% else %}
    private static float actionScore(
            String query,
{% case minecraft_version %}
{% when "26.1.2" %}
            Map.Entry<Identifier, SFMClientAction<?>> action
{% else %}
            Map.Entry<ResourceLocation, SFMClientAction<?>> action
{% endcase %}
    ) {
        if (query.isBlank()) return 0;
        String[] candidates = {
                action.getKey().toString(),
                action.getKey().getPath(),
                action.getValue().title().getString(),
                action.getValue().description().getString()
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

{% endif %}
{% endif %}
{% if features.context_actions %}
    private static float choiceScore(String query, String choice, ActionSearchMetadata action) {
        float score = actionScore(query, action);
        return Math.min(score, SFMFuzzyScorer.score(query, choice));
    }

{% endif %}
{% if features.typed_command_palette %}
    private static float literalScore(String query, String candidate) {
        return SFMFuzzyScorer.score(query, candidate);
    }

{% case minecraft_version %}
{% when "26.1.2" %}
    private static Identifier firstIdentifierToken(String value) {
{% else %}
    private static ResourceLocation firstResourceLocationToken(String value) {
{% endcase %}
        int separator = 0;
        while (separator < value.length() && !Character.isWhitespace(value.charAt(separator))) separator++;
{% case minecraft_version %}
{% when "26.1.2" %}
        return Identifier.tryParse(value.substring(0, separator));
{% else %}
        return ResourceLocation.tryParse(value.substring(0, separator));
{% endcase %}
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

{% if features.context_actions %}
    private record RankedChoice(Suggestion suggestion, float score, String command) {
    }

{% endif %}
    private record ActionSearchMetadata(String id, String path, String title, String description) {
    }

{% else %}
{% if features.command_palette %}
    private record RankedAction(Suggestion suggestion, float score, String id) {
    }

{% endif %}
{% endif %}
    private boolean isAvailable(
{% case minecraft_version %}
{% when "26.1.2" %}
            Identifier id,
{% else %}
            ResourceLocation id,
{% endcase %}
            SFMClientActionSource source
    ) {
        SFMClientAction<?> action = actions.get(id);
        return action != null && action.requirement().resolve(source.context()).isAvailable();
    }
{% if features.typed_command_palette %}

{% endif %}
{% if features.command_history %}
    private List<String> availableHistory(SFMClientActionSource source) {
        List<String> result = new ArrayList<>();
        Set<String> seen = new java.util.LinkedHashSet<>();
        for (String command : historySuggestions.get()) {
            if (!seen.add(command)) continue;
{% case minecraft_version %}
{% when "26.1.2" %}
            Identifier actionId = historyActionId(command);
{% else %}
            ResourceLocation actionId = historyActionId(command);
{% endcase %}
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

{% case minecraft_version %}
{% when "26.1.2" %}
    private static Identifier historyActionId(String command) {
{% else %}
    private static ResourceLocation historyActionId(String command) {
{% endcase %}
        String suffix = historySuffix(command).stripLeading();
        int end = 0;
        while (end < suffix.length() && !Character.isWhitespace(suffix.charAt(end))) end++;
        if (end == 0) return null;
{% case minecraft_version %}
{% when "26.1.2" %}
        return Identifier.tryParse(suffix.substring(0, end));
{% else %}
        return ResourceLocation.tryParse(suffix.substring(0, end));
{% endcase %}
    }

{% endif %}
}
