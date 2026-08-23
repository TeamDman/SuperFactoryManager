package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.client.review.release_review.SFMReleaseReviewExplorerRuntime;
import ca.teamdman.sfm.client.screen.SFMActionChoice;
import ca.teamdman.sfm.client.screen.SFMCommandPaletteScreen;
import ca.teamdman.sfm.client.screen.explorer.SFMExplorerPanel;
import ca.teamdman.sfm.client.screen.workspace.SFMReleaseReviewExplorerScreenType;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenMultiplexer;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspacePanelId;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

/** Changes the typed projection hosted by one exact release-review Explorer. */
public final class SFMReviewLensSetAction implements SFMClientAction<SFMReviewLensSetAction.Target> {
    public static final ResourceLocation ID = new ResourceLocation("sfm", "review/lens/set");

    record Target(
            SFMClientActionContext actionContext,
            SFMScreenMultiplexer workspace,
            SFMWorkspacePanelId panelId,
            SFMExplorerPanel explorer,
            SFMReleaseReviewExplorerRuntime.LensDescriptor lens
    ) {
        Target {
            Objects.requireNonNull(actionContext, "actionContext");
            Objects.requireNonNull(workspace, "workspace");
            Objects.requireNonNull(panelId, "panelId");
            Objects.requireNonNull(explorer, "explorer");
            Objects.requireNonNull(lens, "lens");
        }

        boolean stillCurrent() {
            return actionContext.originatingHostIsCurrent().getAsBoolean()
                    && workspace.panelInstance(panelId) == explorer
                    && SFMReleaseReviewExplorerRuntime.get()
                    .lensDescriptor(explorer.sessionSnapshot().roots())
                    .map(current -> current.reviewOpenEpoch() == lens.reviewOpenEpoch()
                            && current.reviewPath().equals(lens.reviewPath()))
                    .orElse(false);
        }
    }

    @Override
    public Component title() {
        return Component.literal("Set release-review lens");
    }

    @Override
    public Component description() {
        return Component.literal("Replace the projection in this exact review Explorer without replacing its panel");
    }

    @Override
    public SFMClientActionRequirement<Target> requirement() {
        return SFMReviewLensSetAction::capture;
    }

    static SFMClientActionAvailability<Target> capture(SFMClientActionContext context) {
        if (!context.originatingHostIsCurrent().getAsBoolean()) {
            return SFMClientActionAvailability.unavailable(
                    SFMClientActionContext.ORIGINATING_HOST_CHANGED.getComponent()
            );
        }
        if (!(context.originatingHost() instanceof SFMScreenMultiplexer workspace)
                || context.originatingPanelId() == null
                || !(workspace.panelInstance(context.originatingPanelId()) instanceof SFMExplorerPanel explorer)) {
            return SFMClientActionAvailability.unavailable(Component.literal(
                    "Invoke this action from one release-review Explorer"
            ));
        }
        Optional<SFMReleaseReviewExplorerRuntime.LensDescriptor> lens =
                SFMReleaseReviewExplorerRuntime.get().lensDescriptor(explorer.sessionSnapshot().roots());
        if (lens.isEmpty()) {
            return SFMClientActionAvailability.unavailable(Component.literal(
                    "This Explorer does not host one exact release-review lens"
            ));
        }
        return SFMClientActionAvailability.available(new Target(
                context,
                workspace,
                context.originatingPanelId(),
                explorer,
                lens.orElseThrow()
        ));
    }

    public static boolean isControlVisible(SFMClientActionContext context) {
        return capture(Objects.requireNonNull(context, "context")).isAvailable();
    }

    /** Opens the ordinary constrained palette; no second choice-widget implementation is introduced. */
    public static boolean openChoicesFromControl(
            SFMClientActionContext context,
            Consumer<Component> feedback
    ) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(feedback, "feedback");
        SFMClientActionAvailability<Target> availability = capture(context);
        if (!availability.isAvailable()) {
            feedback.accept(availability.unavailableReason().copy().withStyle(ChatFormatting.RED));
            return false;
        }
        Target target = availability.target();
        SFMCommandPaletteScreen.openChoices(
                target.actionContext(),
                Component.literal("Review lens · " + target.lens().title()),
                choices(target.lens().projection())
        );
        return true;
    }

    static List<SFMActionChoice> choices(SFMReleaseReviewExplorerScreenType.Projection active) {
        return Arrays.stream(SFMReleaseReviewExplorerScreenType.Projection.values())
                .map(projection -> SFMActionChoice.invoke(
                        ID,
                        token(projection),
                        (projection == active ? "Current · " : "Switch to ") + title(projection)
                ))
                .toList();
    }

    @Override
    public void configureCommandNode(LiteralArgumentBuilder<SFMClientActionSource> node) {
        node.then(RequiredArgumentBuilder.<SFMClientActionSource, String>argument(
                        "lens", StringArgumentType.word())
                .suggests((context, builder) -> {
                    Arrays.stream(SFMReleaseReviewExplorerScreenType.Projection.values())
                            .map(SFMReviewLensSetAction::token)
                            .forEach(builder::suggest);
                    return builder.buildFuture();
                })
                .executes(this::invoke));
    }

    @Override
    public int execute(Target target, CommandContext<SFMClientActionSource> context) throws CommandSyntaxException {
        if (!target.stillCurrent()) {
            throw new SimpleCommandExceptionType(Component.literal(
                    "The release-review Explorer changed before its lens could be switched"
            )).create();
        }
        SFMReleaseReviewExplorerScreenType.Projection projection;
        try {
            projection = parse(StringArgumentType.getString(context, "lens"));
        } catch (IllegalArgumentException failure) {
            throw new SimpleCommandExceptionType(Component.literal(failure.getMessage())).create();
        }
        context.getSource().sendFeedback(Component.literal("Switching review lens to " + title(projection)));
        SFMReleaseReviewExplorerRuntime.get().switchLens(target.explorer(), projection, Optional.empty())
                .whenComplete((lens, failure) -> {
                    if (failure != null) {
                        context.getSource().sendFeedback(Component.literal(
                                "Review lens switch failed: " + message(failure)
                        ).withStyle(ChatFormatting.RED));
                        return;
                    }
                    context.getSource().sendFeedback(Component.literal(
                            "Review lens is now " + lens.title()
                    ));
                });
        return 1;
    }

    private static SFMReleaseReviewExplorerScreenType.Projection parse(String value) {
        try {
            return SFMReleaseReviewExplorerScreenType.Projection.valueOf(
                    Objects.requireNonNull(value, "value").replace('-', '_').toUpperCase(Locale.ROOT)
            );
        } catch (IllegalArgumentException failure) {
            throw new IllegalArgumentException("Unknown release-review lens: " + value);
        }
    }

    private static String token(SFMReleaseReviewExplorerScreenType.Projection projection) {
        return projection.name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    private static String title(SFMReleaseReviewExplorerScreenType.Projection projection) {
        String token = token(projection);
        return Character.toUpperCase(token.charAt(0)) + token.substring(1);
    }

    private static String message(Throwable failure) {
        Throwable cursor = failure;
        while ((cursor instanceof java.util.concurrent.CompletionException
                || cursor instanceof java.util.concurrent.ExecutionException)
                && cursor.getCause() != null) cursor = cursor.getCause();
        return Optional.ofNullable(cursor.getMessage()).orElse(cursor.getClass().getSimpleName());
    }
}
