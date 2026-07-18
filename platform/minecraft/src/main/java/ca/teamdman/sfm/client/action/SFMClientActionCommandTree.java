package ca.teamdman.sfm.client.action;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.Suggestions;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public final class SFMClientActionCommandTree {
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

    private boolean isAvailable(
            ResourceLocation id,
            SFMClientActionSource source
    ) {
        SFMClientAction<?> action = actions.get(id);
        return action != null && action.requirement().resolve(source.context()).isAvailable();
    }
}
