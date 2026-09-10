package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.client.review.release_review.SFMReleaseReviewExplorerRuntime;
import ca.teamdman.sfm.client.screen.SFMActionChoice;
import ca.teamdman.sfm.client.screen.explorer.SFMExplorerPanel;
import ca.teamdman.sfm.client.screen.review.explorer.SFMReviewExplorerModel;
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

/** Switches the Changes lens between repository hierarchy and full-path rows. */
public final class SFMReviewChangesLayoutSetAction
        implements SFMClientAction<SFMReviewChangesLayoutSetAction.Target> {
    public static final ResourceLocation ID = new ResourceLocation("sfm", "review/changes/layout/set");

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
                            && current.reviewPath().equals(lens.reviewPath())
                            && current.projection() == SFMReleaseReviewExplorerScreenType.Projection.CHANGES)
                    .orElse(false);
        }
    }

    @Override
    public Component title() {
        return Component.literal("Set release-review changed-path layout");
    }

    @Override
    public Component description() {
        return Component.literal("Show changed repository paths as a hierarchy or as complete flat paths");
    }

    @Override
    public SFMClientActionRequirement<Target> requirement() {
        return SFMReviewChangesLayoutSetAction::capture;
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
                    "Invoke this action from one release-review Changes Explorer"
            ));
        }
        Optional<SFMReleaseReviewExplorerRuntime.LensDescriptor> lens =
                SFMReleaseReviewExplorerRuntime.get().lensDescriptor(explorer.sessionSnapshot().roots());
        if (lens.isEmpty()
                || lens.orElseThrow().projection() != SFMReleaseReviewExplorerScreenType.Projection.CHANGES) {
            return SFMClientActionAvailability.unavailable(Component.literal(
                    "This Explorer does not host one exact release-review Changes lens"
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

    /** The alternative presented in review chrome and every review-row context surface. */
    public static List<SFMActionChoice> alternativeChoice(SFMReviewExplorerModel.PathLayout active) {
        SFMReviewExplorerModel.PathLayout alternative = active == SFMReviewExplorerModel.PathLayout.HIERARCHY
                ? SFMReviewExplorerModel.PathLayout.FLAT_PATHS
                : SFMReviewExplorerModel.PathLayout.HIERARCHY;
        return List.of(SFMActionChoice.invoke(
                ID,
                token(alternative),
                alternative == SFMReviewExplorerModel.PathLayout.HIERARCHY
                        ? "View changed paths as hierarchy"
                        : "View changed paths as flat paths"
        ));
    }

    @Override
    public void configureCommandNode(LiteralArgumentBuilder<SFMClientActionSource> node) {
        node.then(RequiredArgumentBuilder.<SFMClientActionSource, String>argument(
                        "layout", StringArgumentType.word())
                .suggests((context, builder) -> {
                    Arrays.stream(SFMReviewExplorerModel.PathLayout.values())
                            .map(SFMReviewChangesLayoutSetAction::token)
                            .forEach(builder::suggest);
                    return builder.buildFuture();
                })
                .executes(this::invoke));
    }

    @Override
    public int execute(Target target, CommandContext<SFMClientActionSource> context) throws CommandSyntaxException {
        if (!target.stillCurrent()) {
            throw new SimpleCommandExceptionType(Component.literal(
                    "The release-review Explorer changed before its path layout could be switched"
            )).create();
        }
        SFMReviewExplorerModel.PathLayout layout;
        try {
            layout = parse(StringArgumentType.getString(context, "layout"));
        } catch (IllegalArgumentException failure) {
            throw new SimpleCommandExceptionType(Component.literal(failure.getMessage())).create();
        }
        context.getSource().sendFeedback(Component.literal("Switching changed paths to " + title(layout)));
        SFMReleaseReviewExplorerRuntime.get().switchChangesPathLayout(target.explorer(), layout)
                .whenComplete((lens, failure) -> {
                    if (failure != null) {
                        context.getSource().sendFeedback(Component.literal(
                                "Changed-path layout switch failed: " + message(failure)
                        ).withStyle(ChatFormatting.RED));
                        return;
                    }
                    context.getSource().sendFeedback(Component.literal(
                            "Changed paths now use " + title(lens.changesPathLayout())
                    ));
                });
        return 1;
    }

    static SFMReviewExplorerModel.PathLayout parse(String value) {
        return switch (Objects.requireNonNull(value, "value").toLowerCase(Locale.ROOT)) {
            case "hierarchy" -> SFMReviewExplorerModel.PathLayout.HIERARCHY;
            case "flat", "flat-paths" -> SFMReviewExplorerModel.PathLayout.FLAT_PATHS;
            default -> throw new IllegalArgumentException("Unknown changed-path layout: " + value);
        };
    }

    static String token(SFMReviewExplorerModel.PathLayout layout) {
        return switch (layout) {
            case HIERARCHY -> "hierarchy";
            case FLAT_PATHS -> "flat-paths";
        };
    }

    private static String title(SFMReviewExplorerModel.PathLayout layout) {
        return switch (layout) {
            case HIERARCHY -> "repository hierarchy";
            case FLAT_PATHS -> "flat paths";
        };
    }

    private static String message(Throwable failure) {
        Throwable cursor = failure;
        while ((cursor instanceof java.util.concurrent.CompletionException
                || cursor instanceof java.util.concurrent.ExecutionException)
                && cursor.getCause() != null) cursor = cursor.getCause();
        return Optional.ofNullable(cursor.getMessage()).orElse(cursor.getClass().getSimpleName());
    }
}
