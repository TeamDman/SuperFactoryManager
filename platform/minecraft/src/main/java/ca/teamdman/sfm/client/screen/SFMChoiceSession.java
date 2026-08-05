package ca.teamdman.sfm.client.screen;

import ca.teamdman.sfm.client.action.SFMClientAction;
import ca.teamdman.sfm.client.action.SFMClientActionCommandTree;
import ca.teamdman.sfm.client.action.SFMClientActionContext;
import ca.teamdman.sfm.client.action.SFMClientActionSource;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * One ephemeral, exact command surface used by a constrained command palette.
 * The global client-action dispatcher remains immutable; only this session's
 * canonical choices are compiled beneath {@code sfm choose <id>}.
 */
public final class SFMChoiceSession {
    private static final String ACTION_PREFIX = "sfm action invoke ";
    private static final SimpleCommandExceptionType STALE = new SimpleCommandExceptionType(
            Component.literal("This SFM choice session is no longer active"));
    private static final SimpleCommandExceptionType UNKNOWN = new SimpleCommandExceptionType(
            Component.literal("That command is not a valid choice in this SFM choice session"));

    private final long id;
    private final List<SFMActionChoice> candidates;
    private final SFMClientActionContext actionContext;
    private final Function<ResourceLocation, SFMClientAction<?>> actionLookup;
    private final SFMClientActionCommandTree actionTree;
    private List<SFMActionChoice> choices = List.of();
    private Map<String, SFMActionChoice> choicesBySurfaceCommand = Map.of();
    private Map<String, ResourceLocation> choiceActionsByTail = Map.of();
    private SFMClientActionCommandTree commandTree;
    private boolean activated;
    private volatile boolean invalidated;

    SFMChoiceSession(
            long id,
            List<SFMActionChoice> candidates,
            SFMClientActionContext actionContext,
            Function<ResourceLocation, SFMClientAction<?>> actionLookup,
            SFMClientActionCommandTree actionTree
    ) {
        this.id = id;
        this.candidates = List.copyOf(candidates);
        this.actionContext = Objects.requireNonNull(actionContext);
        this.actionLookup = Objects.requireNonNull(actionLookup);
        this.actionTree = Objects.requireNonNull(actionTree);
    }

    public long id() {
        return id;
    }

    public String prefix() {
        return "sfm choose " + id + " ";
    }

    public synchronized SFMClientActionCommandTree activate() {
        if (invalidated || !SFMChoiceSessionService.isCurrent(this)) {
            throw new IllegalStateException("Cannot activate stale SFM choice session " + id);
        }
        if (activated) return commandTree;

        choices = SFMActionChoiceCatalog.available(candidates, actionLookup, actionTree, actionContext);
        LinkedHashMap<String, SFMActionChoice> surfaceCommands = new LinkedHashMap<>();
        LinkedHashMap<ResourceLocation, SFMClientAction<?>> actions = new LinkedHashMap<>();
        LinkedHashMap<String, ResourceLocation> paletteChoices = new LinkedHashMap<>();
        for (SFMActionChoice choice : choices) {
            String command = surfaceCommand(choice);
            surfaceCommands.put(command, choice);
            SFMClientAction<?> action = actionLookup.apply(choice.actionId());
            if (action != null) actions.put(choice.actionId(), action);
            paletteChoices.put(choiceTail(choice), choice.actionId());
        }
        choicesBySurfaceCommand = Map.copyOf(surfaceCommands);
        choiceActionsByTail = Map.copyOf(paletteChoices);

        ChoicePath root = new ChoicePath();
        choices.forEach(choice -> root.add(choiceTail(choice), choice));
        CommandDispatcher<SFMClientActionSource> dispatcher = new CommandDispatcher<>();
        LiteralArgumentBuilder<SFMClientActionSource> sessionNode =
                LiteralArgumentBuilder.literal(Long.toString(id));
        root.children.forEach((literal, path) -> sessionNode.then(path.compile(literal)));
        dispatcher.register(LiteralArgumentBuilder.<SFMClientActionSource>literal("sfm")
                .then(LiteralArgumentBuilder.<SFMClientActionSource>literal("choose")
                        .then(sessionNode)));
        commandTree = SFMClientActionCommandTree.isolatedPaletteSurface(
                dispatcher, actions, prefix(), paletteChoices);
        activated = true;
        return commandTree;
    }

    public synchronized List<String> canonicalCommands() {
        return choices.stream().map(SFMActionChoice::command).toList();
    }

    public synchronized String surfaceCommand(String canonicalCommand) {
        return choicesBySurfaceCommand.entrySet().stream()
                .filter(entry -> entry.getValue().command().equals(canonicalCommand))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Choice session does not contain " + canonicalCommand));
    }

    public synchronized Optional<ResourceLocation> actionIdForSuggestion(String suggestion) {
        return Optional.ofNullable(choiceActionsByTail.get(suggestion));
    }

    void invalidate() {
        invalidated = true;
    }

    boolean invalidated() {
        return invalidated;
    }

    private int execute(String surfaceCommand, SFMClientActionSource source) throws CommandSyntaxException {
        SFMActionChoice choice;
        synchronized (this) {
            if (invalidated || !SFMChoiceSessionService.isCurrent(this)) throw STALE.create();
            choice = choicesBySurfaceCommand.get(surfaceCommand);
            if (choice == null) throw UNKNOWN.create();
        }
        int result = actionTree.execute(
                choice.command(),
                new SFMClientActionSource(actionContext, source.feedback()));
        if (result > 0) SFMChoiceSessionService.consume(this);
        return result;
    }

    private String surfaceCommand(SFMActionChoice choice) {
        return prefix() + choiceTail(choice);
    }

    private static String choiceTail(SFMActionChoice choice) {
        if (!choice.command().startsWith(ACTION_PREFIX)) {
            throw new IllegalArgumentException("Choice is not a canonical action invocation: " + choice.command());
        }
        return choice.command().substring(ACTION_PREFIX.length());
    }

    private final class ChoicePath {
        private final Map<String, ChoicePath> children = new LinkedHashMap<>();
        private SFMActionChoice choice;

        private void add(String tail, SFMActionChoice choice) {
            String[] literals = tail.split(" ");
            ChoicePath path = this;
            for (String literal : literals) {
                if (literal.isBlank()) {
                    throw new IllegalArgumentException("Choice contains an empty command literal: " + tail);
                }
                path = path.children.computeIfAbsent(literal, ignored -> new ChoicePath());
            }
            if (path.choice != null) {
                throw new IllegalArgumentException("Duplicate SFM choice command: " + tail);
            }
            path.choice = choice;
        }

        private LiteralArgumentBuilder<SFMClientActionSource> compile(String literal) {
            LiteralArgumentBuilder<SFMClientActionSource> node = LiteralArgumentBuilder.literal(literal);
            if (choice != null) {
                node.executes(context -> execute(surfaceCommand(choice), context.getSource()));
            }
            children.forEach((childLiteral, child) -> node.then(child.compile(childLiteral)));
            return node;
        }
    }
}
