package ca.teamdman.sfm.client.action;

import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/** Frozen v1 grammar shell for Java-owned canvas coverage. */
public final class SFMSpatialCoverageRunAction
        implements SFMClientAction<PanelActionSupport.CapturedPanel> {
    static final List<String> SCOPES = List.of("document", "workspace");
    static final List<String> PROFILES = List.of(
            "sfm:classification",
            "sfm:strict_java_navigation",
            "sfm:real_gesture",
            "sfm:branch_boundary",
            "sfm:reciprocity"
    );
    static final List<String> LAYOUT_MATRICES = List.of("sfm:auto_1_through_8");

    private static final String SELECTOR = "focused";
    private static final String AUTO_DESTINATION = "auto";
    private final Handler handler;

    public SFMSpatialCoverageRunAction() {
        this(SFMSpatialCoverageRuntime::run);
    }

    SFMSpatialCoverageRunAction(Handler handler) {
        this.handler = Objects.requireNonNull(handler);
    }

    @Override
    public Component title() {
        return Component.literal("Run spatial coverage");
    }

    @Override
    public Component description() {
        return Component.literal(
                "Capture document or workspace canvas coverage using the frozen spatial-semantic contract"
        );
    }

    @Override
    public SFMClientActionRequirement<PanelActionSupport.CapturedPanel> requirement() {
        return PanelActionSupport::resolveCapturedPanel;
    }

    @Override
    public void configureCommandNode(LiteralArgumentBuilder<SFMClientActionSource> node) {
        RequiredArgumentBuilder<SFMClientActionSource, String> scope = word("scope", SCOPES);
        RequiredArgumentBuilder<SFMClientActionSource, String> selector =
                word("selector", List.of(SELECTOR));
        RequiredArgumentBuilder<SFMClientActionSource, String> profile =
                token("profile", PROFILES);
        RequiredArgumentBuilder<SFMClientActionSource, String> layoutMatrix =
                token("layout-matrix", LAYOUT_MATRICES);
        RequiredArgumentBuilder<SFMClientActionSource, Long> seed = RequiredArgumentBuilder
                .<SFMClientActionSource, Long>argument("seed", LongArgumentType.longArg(0))
                .suggests((context, builder) -> builder.suggest(0).buildFuture());
        RequiredArgumentBuilder<SFMClientActionSource, Long> budget = RequiredArgumentBuilder
                .<SFMClientActionSource, Long>argument("budget", LongArgumentType.longArg(1))
                .suggests((context, builder) -> builder.suggest(100000).buildFuture());
        RequiredArgumentBuilder<SFMClientActionSource, String> artifactDestination =
                RequiredArgumentBuilder
                        .<SFMClientActionSource, String>argument(
                                "artifact-destination",
                                StringArgumentType.string()
                        )
                        .suggests((context, builder) ->
                                builder.suggest(AUTO_DESTINATION).buildFuture())
                        .executes(this::invoke);

        budget.then(artifactDestination);
        seed.then(budget);
        layoutMatrix.then(seed);
        profile.then(layoutMatrix);
        selector.then(profile);
        scope.then(selector);
        node.then(scope);
    }

    @Override
    public int execute(
            PanelActionSupport.CapturedPanel target,
            CommandContext<SFMClientActionSource> context
    ) throws CommandSyntaxException {
        return handler.run(target, request(context), context.getSource()::sendFeedback);
    }

    private static Request request(CommandContext<SFMClientActionSource> context)
            throws CommandSyntaxException {
        String scope = StringArgumentType.getString(context, "scope");
        if (!SCOPES.contains(scope)) {
            throw invalid("scope", scope, "document or workspace");
        }

        String selector = StringArgumentType.getString(context, "selector");
        if (!SELECTOR.equals(selector)) {
            throw invalid("selector", selector, SELECTOR);
        }

        String profile = SFMCanonicalTokenArgument.get(context, "profile");
        requireCanonicalNamespaced("profile", profile);
        String layoutMatrix = SFMCanonicalTokenArgument.get(context, "layout-matrix");
        requireCanonicalNamespaced("layout matrix", layoutMatrix);

        String artifactDestination = StringArgumentType
                .getString(context, "artifact-destination")
                .trim();
        if (artifactDestination.isEmpty()) {
            throw invalid("artifact destination", artifactDestination, "a non-empty string");
        }

        return new Request(
                scope,
                selector,
                profile,
                layoutMatrix,
                LongArgumentType.getLong(context, "seed"),
                LongArgumentType.getLong(context, "budget"),
                artifactDestination
        );
    }

    private static RequiredArgumentBuilder<SFMClientActionSource, String> word(
            String name,
            List<String> suggestions
    ) {
        return RequiredArgumentBuilder
                .<SFMClientActionSource, String>argument(name, StringArgumentType.word())
                .suggests((context, builder) -> {
                    suggestions.forEach(builder::suggest);
                    return builder.buildFuture();
                });
    }

    private static RequiredArgumentBuilder<SFMClientActionSource, String> token(
            String name,
            List<String> suggestions
    ) {
        return RequiredArgumentBuilder
                .<SFMClientActionSource, String>argument(name, SFMCanonicalTokenArgument.token())
                .suggests((context, builder) -> {
                    suggestions.forEach(builder::suggest);
                    return builder.buildFuture();
                });
    }

    private static void requireCanonicalNamespaced(String label, String value)
            throws CommandSyntaxException {
        ResourceLocation parsed = ResourceLocation.tryParse(value);
        if (value.indexOf(':') <= 0 || parsed == null || !parsed.toString().equals(value)) {
            throw invalid(label, value, "a canonical namespaced id");
        }
    }

    private static CommandSyntaxException invalid(String label, String actual, String expected) {
        return new SimpleCommandExceptionType(Component.literal(
                "Invalid " + label + " `" + actual + "`; expected " + expected
        )).create();
    }

    @FunctionalInterface
    interface Handler {
        int run(
                PanelActionSupport.CapturedPanel target,
                Request request,
                Consumer<Component> feedback
        )
                throws CommandSyntaxException;
    }

    record Request(
            String scope,
            String selector,
            String profile,
            String layoutMatrix,
            long seed,
            long budget,
            String artifactDestination
    ) {
    }
}
