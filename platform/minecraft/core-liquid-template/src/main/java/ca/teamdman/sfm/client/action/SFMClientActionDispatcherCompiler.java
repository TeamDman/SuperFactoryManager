package ca.teamdman.sfm.client.action;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
{% case minecraft_version %}
{% when "26.1.2" %}
import net.minecraft.resources.Identifier;
{% else %}
import net.minecraft.resources.ResourceLocation;
{% endcase %}

import java.util.Comparator;
import java.util.Map;
import java.util.TreeMap;
{% if features.command_history %}
import java.util.List;
import java.util.function.Supplier;
{% endif %}

public final class SFMClientActionDispatcherCompiler {
    private SFMClientActionDispatcherCompiler() {
    }

    public static CommandDispatcher<SFMClientActionSource> compile(
{% case minecraft_version %}
{% when "26.1.2" %}
            Iterable<? extends Map.Entry<Identifier, ? extends SFMClientAction<?>>> registrations
{% else %}
            Iterable<? extends Map.Entry<ResourceLocation, ? extends SFMClientAction<?>>> registrations
{% endcase %}
    ) {
        return compileCommandTree(registrations).dispatcher();
    }

    public static SFMClientActionCommandTree compileCommandTree(
{% case minecraft_version %}
{% when "26.1.2" %}
            Iterable<? extends Map.Entry<Identifier, ? extends SFMClientAction<?>>> registrations
{% else %}
            Iterable<? extends Map.Entry<ResourceLocation, ? extends SFMClientAction<?>>> registrations
{% endcase %}
    ) {
{% if features.command_history %}
        return compileCommandTree(registrations, ca.teamdman.sfm.client.command.SFMCommandHistoryService::suggestionsNewestFirst);
    }

    static SFMClientActionCommandTree compileCommandTree(
{% case minecraft_version %}
{% when "26.1.2" %}
            Iterable<? extends Map.Entry<Identifier, ? extends SFMClientAction<?>>> registrations,
{% else %}
            Iterable<? extends Map.Entry<ResourceLocation, ? extends SFMClientAction<?>>> registrations,
{% endcase %}
            Supplier<List<String>> historySuggestions
    ) {
{% endif %}
{% case minecraft_version %}
{% when "26.1.2" %}
        TreeMap<Identifier, SFMClientAction<?>> actions = new TreeMap<>(Comparator.comparing(Identifier::toString));
{% else %}
        TreeMap<ResourceLocation, SFMClientAction<?>> actions = new TreeMap<>(Comparator.comparing(ResourceLocation::toString));
{% endcase %}
{% case minecraft_version %}
{% when "26.1.2" %}
        for (Map.Entry<Identifier, ? extends SFMClientAction<?>> registration : registrations) {
{% else %}
        for (Map.Entry<ResourceLocation, ? extends SFMClientAction<?>> registration : registrations) {
{% endcase %}
            SFMClientAction<?> replaced = actions.putIfAbsent(registration.getKey(), registration.getValue());
            if (replaced != null) {
                throw new IllegalArgumentException("Duplicate client action id: " + registration.getKey());
            }
        }

        LiteralArgumentBuilder<SFMClientActionSource> invoke = LiteralArgumentBuilder.literal("invoke");
{% case minecraft_version %}
{% when "26.1.2" %}
        for (Map.Entry<Identifier, SFMClientAction<?>> action : actions.entrySet()) {
{% else %}
        for (Map.Entry<ResourceLocation, SFMClientAction<?>> action : actions.entrySet()) {
{% endcase %}
            invoke.then(action.getValue().createCommandNode(action.getKey()));
        }

        LiteralArgumentBuilder<SFMClientActionSource> list = LiteralArgumentBuilder.<SFMClientActionSource>literal("list")
                .executes(context -> listActions(context, actions, true));
        list.then(LiteralArgumentBuilder.<SFMClientActionSource>literal("available")
                                 .executes(context -> listActions(context, actions, true)));
        list.then(LiteralArgumentBuilder.<SFMClientActionSource>literal("all")
                                 .executes(context -> listActions(context, actions, false)));

        LiteralArgumentBuilder<SFMClientActionSource> help = LiteralArgumentBuilder.literal("help");
        SuggestionProvider<SFMClientActionSource> actionIdSuggestions = (context, builder) -> {
{% if features.client_action_completion_diagnostics %}
            // This provider reads the immutable compiled action map only. Any
            // filesystem, network, or repository scan belongs in an explicit
            // preloaded snapshot and must never run during palette completion.
{% endif %}
{% case minecraft_version %}
{% when "26.1.2" %}
            for (Identifier id : actions.keySet()) {
{% else %}
            for (ResourceLocation id : actions.keySet()) {
{% endcase %}
                builder.suggest(id.toString());
            }
            return builder.buildFuture();
        };
        help.then(com.mojang.brigadier.builder.RequiredArgumentBuilder.<SFMClientActionSource, String>argument(
                                  "action",
                                  StringArgumentType.greedyString()
                          )
                          .suggests(actionIdSuggestions)
                          .executes(context -> showHelp(context, actions)));

        CommandDispatcher<SFMClientActionSource> dispatcher = new CommandDispatcher<>();
        dispatcher.register(LiteralArgumentBuilder.<SFMClientActionSource>literal("sfm")
                                    .then(LiteralArgumentBuilder.<SFMClientActionSource>literal("action")
                                                  .then(list)
                                                  .then(help)
                                                  .then(invoke)));
{% if features.command_history %}
        return new SFMClientActionCommandTree(
                dispatcher,
                actions,
                List.of("sfm action invoke "),
{% if features.context_actions %}
                Map.of(),
{% endif %}
                historySuggestions);
{% else %}
        return new SFMClientActionCommandTree(dispatcher, actions);
{% endif %}
    }

    private static int listActions(
            CommandContext<SFMClientActionSource> context,
{% case minecraft_version %}
{% when "26.1.2" %}
            Map<Identifier, SFMClientAction<?>> actions,
{% else %}
            Map<ResourceLocation, SFMClientAction<?>> actions,
{% endcase %}
            boolean availableOnly
    ) {
        int count = 0;
{% case minecraft_version %}
{% when "26.1.2" %}
        for (Map.Entry<Identifier, SFMClientAction<?>> action : actions.entrySet()) {
{% else %}
        for (Map.Entry<ResourceLocation, SFMClientAction<?>> action : actions.entrySet()) {
{% endcase %}
            SFMClientActionAvailability<?> availability = action.getValue()
                    .requirement()
                    .resolve(context.getSource().context());
            if (availableOnly && !availability.isAvailable()) continue;
            MutableComponent line = Component.literal(action.getKey().toString())
                    .withStyle(ChatFormatting.AQUA)
                    .append(Component.literal(" — "))
                    .append(action.getValue().title())
                    .append(Component.literal(" — "))
                    .append(action.getValue().description());
            if (!availability.isAvailable()) {
                line = line.append(Component.literal(" (unavailable: "))
                        .append(availability.unavailableReason())
                        .append(Component.literal(")"));
            }
            context.getSource().sendFeedback(line);
            count++;
        }
        return count;
    }

    private static int showHelp(
            CommandContext<SFMClientActionSource> context,
{% case minecraft_version %}
{% when "26.1.2" %}
            Map<Identifier, SFMClientAction<?>> actions
{% else %}
            Map<ResourceLocation, SFMClientAction<?>> actions
{% endcase %}
    ) throws CommandSyntaxException {
        String rawId = StringArgumentType.getString(context, "action").trim();
{% case minecraft_version %}
{% when "26.1.2" %}
        Identifier id = Identifier.tryParse(rawId);
{% else %}
        ResourceLocation id = ResourceLocation.tryParse(rawId);
{% endcase %}
        SFMClientAction<?> action = id == null ? null : actions.get(id);
        if (action == null) {
            throw new SimpleCommandExceptionType(Component.literal("Unknown client action: " + rawId)).create();
        }
        SFMClientActionAvailability<?> availability = action.requirement().resolve(context.getSource().context());
        context.getSource().sendFeedback(Component.literal(id.toString()).withStyle(ChatFormatting.AQUA));
        context.getSource().sendFeedback(action.title());
        context.getSource().sendFeedback(action.description());
        if (!availability.isAvailable()) {
            context.getSource().sendFeedback(
                    Component.literal("Unavailable: ").withStyle(ChatFormatting.RED)
                            .append(availability.unavailableReason())
            );
        }
        return 1;
    }
}
