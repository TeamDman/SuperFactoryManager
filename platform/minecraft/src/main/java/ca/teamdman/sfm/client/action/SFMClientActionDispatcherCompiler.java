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
import net.minecraft.resources.ResourceLocation;

import java.util.Comparator;
import java.util.Map;
import java.util.TreeMap;

public final class SFMClientActionDispatcherCompiler {
    private SFMClientActionDispatcherCompiler() {
    }

    public static CommandDispatcher<SFMClientActionSource> compile(
            Iterable<? extends Map.Entry<ResourceLocation, ? extends SFMClientAction<?>>> registrations
    ) {
        return compileCommandTree(registrations).dispatcher();
    }

    public static SFMClientActionCommandTree compileCommandTree(
            Iterable<? extends Map.Entry<ResourceLocation, ? extends SFMClientAction<?>>> registrations
    ) {
        TreeMap<ResourceLocation, SFMClientAction<?>> actions = new TreeMap<>(Comparator.comparing(ResourceLocation::toString));
        for (Map.Entry<ResourceLocation, ? extends SFMClientAction<?>> registration : registrations) {
            SFMClientAction<?> replaced = actions.putIfAbsent(registration.getKey(), registration.getValue());
            if (replaced != null) {
                throw new IllegalArgumentException("Duplicate client action id: " + registration.getKey());
            }
        }

        LiteralArgumentBuilder<SFMClientActionSource> invoke = LiteralArgumentBuilder.literal("invoke");
        for (Map.Entry<ResourceLocation, SFMClientAction<?>> action : actions.entrySet()) {
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
            // This provider reads the immutable compiled action map only. Any
            // filesystem, network, or repository scan belongs in an explicit
            // preloaded snapshot and must never run during palette completion.
            for (ResourceLocation id : actions.keySet()) {
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
        return new SFMClientActionCommandTree(dispatcher, actions);
    }

    private static int listActions(
            CommandContext<SFMClientActionSource> context,
            Map<ResourceLocation, SFMClientAction<?>> actions,
            boolean availableOnly
    ) {
        int count = 0;
        for (Map.Entry<ResourceLocation, SFMClientAction<?>> action : actions.entrySet()) {
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
            Map<ResourceLocation, SFMClientAction<?>> actions
    ) throws CommandSyntaxException {
        String rawId = StringArgumentType.getString(context, "action").trim();
        ResourceLocation id = ResourceLocation.tryParse(rawId);
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
