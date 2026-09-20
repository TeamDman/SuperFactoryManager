package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.client.explorer.SFMEntitySelector;
import ca.teamdman.sfm.client.explorer.SFMExplorerRuntime;
import ca.teamdman.sfm.client.explorer.SFMPathExpression;
import ca.teamdman.sfm.client.explorer.action.SFMExplorerActionRequest;
import ca.teamdman.sfm.client.explorer.action.SFMExplorerActionResult;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.network.chat.Component;

import java.util.Locale;

/** Atomically replaces one or more explorer locations at an expected revision. */
public final class SFMExplorerLocationSetAction implements SFMClientAction<SFMClientActionContext> {
    @Override
    public Component title() {
        return Component.literal("Set explorer location");
    }

    @Override
    public Component description() {
        return Component.literal("Replace an explorer path expression without granting new path authority");
    }

    @Override
    public SFMClientActionRequirement<SFMClientActionContext> requirement() {
        return context -> context.originatingHostIsCurrent().getAsBoolean()
                ? SFMClientActionAvailability.available(context)
                : SFMClientActionAvailability.unavailable(
                        SFMClientActionContext.ORIGINATING_HOST_CHANGED.getComponent()
                );
    }

    @Override
    public void configureCommandNode(LiteralArgumentBuilder<SFMClientActionSource> node) {
        RequiredArgumentBuilder<SFMClientActionSource, String> selector = RequiredArgumentBuilder
                .<SFMClientActionSource, String>argument(
                        "explorer_selector",
                        SFMCanonicalTokenArgument.token()
                )
                .suggests(SFMExplorerLocationSetAction::suggestExplorers);
        RequiredArgumentBuilder<SFMClientActionSource, String> expression = RequiredArgumentBuilder
                .<SFMClientActionSource, String>argument(
                        "path_expression",
                        SFMCanonicalTokenArgument.token()
                )
                .suggests((context, builder) -> {
                    builder.suggest("registry://minecraft/item/");
                    return builder.buildFuture();
                });
        expression.then(LiteralArgumentBuilder.<SFMClientActionSource>literal("--expected-revision")
                .then(RequiredArgumentBuilder
                        .<SFMClientActionSource, Long>argument("revision", LongArgumentType.longArg(0))
                        .executes(this::invokeLocation)));
        selector.then(expression);
        node.then(selector);
    }

    @Override
    public int execute(
            SFMClientActionContext target,
            CommandContext<SFMClientActionSource> context
    ) throws CommandSyntaxException {
        throw new SimpleCommandExceptionType(Component.literal(
                "Provide an explorer selector, canonical path expression, and expected revision"
        )).create();
    }

    private int invokeLocation(CommandContext<SFMClientActionSource> context) throws CommandSyntaxException {
        try {
            SFMEntitySelector selector = SFMEntitySelector.parseCanonical(
                    SFMEntitySelector.Domain.EXPLORER,
                    SFMCanonicalTokenArgument.get(context, "explorer_selector")
            );
            String text = SFMCanonicalTokenArgument.get(context, "path_expression");
            SFMPathExpression expression = SFMPathExpression.parse(text);
            if (!expression.canonical().equals(text)) {
                throw new IllegalArgumentException(
                        "Explorer location must use its canonical spelling: " + expression.canonical()
                );
            }
            SFMExplorerActionResult result = SFMExplorerRuntime.get().execute(new SFMExplorerActionRequest(
                    selector,
                    new SFMExplorerActionRequest.LocationSet(
                            expression,
                            LongArgumentType.getLong(context, "revision")
                    ),
                    SFMExplorerActionRequest.IfNoMatch.FAIL
            ));
            if (result.status() != SFMExplorerActionResult.Status.SUCCEEDED) {
                throw failure(result);
            }
            for (SFMExplorerActionResult.TargetResult target : result.targets()) {
                context.getSource().sendFeedback(Component.literal(
                        target.explorerId().value() + ": "
                                + target.outcome().name().toLowerCase(Locale.ROOT).replace('_', '-')
                ));
            }
            return Math.max(1, result.targets().size());
        } catch (CommandSyntaxException failure) {
            throw failure;
        } catch (RuntimeException failure) {
            String detail = failure.getMessage() == null
                    ? failure.getClass().getSimpleName()
                    : failure.getMessage();
            throw new SimpleCommandExceptionType(Component.literal(detail)).create();
        }
    }

    private static CommandSyntaxException failure(SFMExplorerActionResult result) {
        String detail = result.diagnostics().isEmpty()
                ? result.status().name().toLowerCase(Locale.ROOT)
                : result.diagnostics().get(0);
        return new SimpleCommandExceptionType(Component.literal(
                "Explorer location was not changed: " + detail
        )).create();
    }

    private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions>
    suggestExplorers(
            CommandContext<SFMClientActionSource> context,
            com.mojang.brigadier.suggestion.SuggestionsBuilder builder
    ) {
        builder.suggest("focused");
        builder.suggest("all");
        SFMExplorerRuntime.get().repository().stateSnapshot().explorers().keySet().forEach(id ->
                builder.suggest(SFMEntitySelector.exact(
                        SFMEntitySelector.Domain.EXPLORER,
                        id.value()
                ).canonical())
        );
        return builder.buildFuture();
    }
}
