package ca.teamdman.sfm.client.action;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.context.StringRange;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.Suggestions;
import net.minecraft.resources.ResourceLocation;
import org.simmetrics.StringDistance;
import org.simmetrics.metrics.StringDistances;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public final class SFMClientActionCommandTree {
    private static final String PALETTE_ACTION_PREFIX = "sfm action invoke ";
    private static final StringDistance ACTION_DISTANCE = StringDistances.damerauLevenshtein();

    private final CommandDispatcher<SFMClientActionSource> dispatcher;
    private final Map<ResourceLocation, SFMClientAction<?>> actions;

    SFMClientActionCommandTree(
            CommandDispatcher<SFMClientActionSource> dispatcher,
            Map<ResourceLocation, SFMClientAction<?>> actions
    ) {
        this.dispatcher = dispatcher;
        this.actions = Map.copyOf(actions);
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
        return dispatcher.getCompletionSuggestions(parsed).thenApply(suggestions -> {
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
        return getCompletionSuggestions(parsed).thenApply(brigadierSuggestions -> {
            StringRange actionRange = actionIdRange(command);
            if (actionRange == null) return brigadierSuggestions;

            String query = command.substring(actionRange.getStart(), actionRange.getEnd())
                    .toLowerCase(Locale.ROOT);
            SFMClientActionSource source = parsed.getContext().getSource();
            List<RankedAction> ranked = new ArrayList<>();
            for (Map.Entry<ResourceLocation, SFMClientAction<?>> action : actions.entrySet()) {
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

    private static StringRange actionIdRange(String command) {
        if (!command.startsWith(PALETTE_ACTION_PREFIX)) return null;
        int actionStart = PALETTE_ACTION_PREFIX.length();
        for (int index = actionStart; index < command.length(); index++) {
            if (Character.isWhitespace(command.charAt(index))) return null;
        }
        return StringRange.between(actionStart, command.length());
    }

    private static float actionScore(
            String query,
            Map.Entry<ResourceLocation, SFMClientAction<?>> action
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

    private record RankedAction(Suggestion suggestion, float score, String id) {
    }

    private boolean isAvailable(
            ResourceLocation id,
            SFMClientActionSource source
    ) {
        SFMClientAction<?> action = actions.get(id);
        return action != null && action.requirement().resolve(source.context()).isAvailable();
    }
}
