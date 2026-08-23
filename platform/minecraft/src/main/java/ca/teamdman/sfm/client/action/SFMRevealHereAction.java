package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.client.explorer.SFMPath;
import ca.teamdman.sfm.client.explorer.SFMPathHierarchy;
import ca.teamdman.sfm.client.review.release_review.SFMReleaseReviewExplorerRuntime;
import ca.teamdman.sfm.client.screen.explorer.SFMExplorerPanel;
import ca.teamdman.sfm.client.screen.text_editor.SFMTextDocumentPanelState;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenMultiplexer;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenPanel;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspacePanelId;
import ca.teamdman.sfm.client.text_editor.SFMTextDocumentSnapshot;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;
import java.util.concurrent.CompletionStage;

/** Reveals the most recently focused addressed document in one exact explorer. */
public final class SFMRevealHereAction implements SFMClientAction<SFMRevealHereAction.Target> {
    public static final ResourceLocation ID = new ResourceLocation("sfm", "explorer/reveal/here");

    record Target(
            SFMClientActionContext actionContext,
            SFMScreenMultiplexer workspace,
            SFMWorkspacePanelId explorerPanelId,
            SFMExplorerPanel explorer,
            SFMWorkspacePanelId documentPanelId,
            SFMScreenPanel documentPanel,
            SFMPath containingRoot,
            SFMPath documentPath
    ) {
        Target {
            Objects.requireNonNull(actionContext, "actionContext");
            Objects.requireNonNull(workspace, "workspace");
            Objects.requireNonNull(explorerPanelId, "explorerPanelId");
            Objects.requireNonNull(explorer, "explorer");
            Objects.requireNonNull(documentPanelId, "documentPanelId");
            Objects.requireNonNull(documentPanel, "documentPanel");
            Objects.requireNonNull(containingRoot, "containingRoot");
            Objects.requireNonNull(documentPath, "documentPath");
        }

        boolean stillCurrent() {
            return actionContext.originatingHostIsCurrent().getAsBoolean()
                    && workspace.panelInstance(explorerPanelId) == explorer
                    && workspace.panelInstance(documentPanelId) == documentPanel;
        }
    }

    @Override
    public Component title() {
        return Component.literal("Reveal recent document here");
    }

    @Override
    public Component description() {
        return Component.literal(
                "Reveal the most recently focused addressed document in this exact Explorer"
        );
    }

    @Override
    public SFMClientActionRequirement<Target> requirement() {
        return context -> capture(context);
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
                    "Invoke this action from the target Explorer"
            ));
        }

        var recent = workspace.mostRecentlyFocusedPanel(
                panel -> panel instanceof SFMTextDocumentPanelState,
                context.originatingPanelId()
        ).orElse(null);
        if (recent == null || !(recent.panel() instanceof SFMTextDocumentPanelState documentState)) {
            return SFMClientActionAvailability.unavailable(Component.literal(
                    "No recently focused addressed document is available"
            ));
        }
        SFMTextDocumentSnapshot snapshot = documentState.documentSnapshot().orElse(null);
        if (snapshot == null || !snapshot.ready()) {
            return SFMClientActionAvailability.unavailable(Component.literal(
                    "The most recently focused document has not finished loading"
            ));
        }
        SFMPath path = snapshot.path().orElse(null);
        if (path == null || snapshot.authorizedRoot().isEmpty()) {
            return SFMClientActionAvailability.unavailable(Component.literal(
                    "The most recently focused document has no resolver-issued path"
            ));
        }
        SFMPath containingRoot = SFMPathHierarchy.deepestContainingRoot(
                explorer.sessionSnapshot().roots(),
                path
        ).orElse(null);
        if (containingRoot == null) {
            java.util.List<SFMReleaseReviewExplorerRuntime.RevealTarget> reviewTargets;
            try {
                reviewTargets = SFMReleaseReviewExplorerRuntime.get().revealTargets(
                        explorer.sessionSnapshot().roots(),
                        snapshot
                );
            } catch (IllegalStateException unavailableReview) {
                reviewTargets = java.util.List.of();
            }
            if (reviewTargets.size() > 1) {
                return SFMClientActionAvailability.unavailable(Component.literal(
                        "More than one exact review row represents " + path.canonical()
                ));
            }
            if (reviewTargets.size() == 1) {
                SFMReleaseReviewExplorerRuntime.RevealTarget target = reviewTargets.get(0);
                containingRoot = target.containingRoot();
                path = target.explorerPath();
            } else {
                return SFMClientActionAvailability.unavailable(Component.literal(
                        "This Explorer is not authorized to reveal " + path.canonical()
                                + "; it has no exact row for that document"
                ));
            }
        }
        return SFMClientActionAvailability.available(new Target(
                context,
                workspace,
                context.originatingPanelId(),
                explorer,
                recent.id(),
                recent.panel(),
                containingRoot,
                path
        ));
    }

    @Override
    public int execute(Target target, CommandContext<SFMClientActionSource> context) throws CommandSyntaxException {
        if (!target.stillCurrent()) {
            throw new SimpleCommandExceptionType(Component.literal(
                    "Reveal target became stale before execution"
            )).create();
        }

        CompletionStage<?> completion;
        try {
            completion = Objects.requireNonNull(
                    target.explorer().revealPath(target.containingRoot(), target.documentPath()),
                    "reveal completion"
            );
        } catch (RuntimeException failure) {
            throw syntaxFailure(failure);
        }
        completion.whenComplete((ignored, failure) -> {
            if (failure != null) {
                context.getSource().sendFeedback(Component.literal(
                        "Reveal here failed: " + message(unwrap(failure))
                ).withStyle(ChatFormatting.RED));
                return;
            }
            context.getSource().sendFeedback(Component.literal(
                    "Revealed " + target.documentPath().canonical() + " in this Explorer"
            ));
        });
        return 1;
    }

    private static CommandSyntaxException syntaxFailure(Throwable failure) {
        return new SimpleCommandExceptionType(Component.literal(
                "Reveal here failed: " + message(failure)
        )).create();
    }

    private static Throwable unwrap(Throwable failure) {
        Throwable answer = failure;
        while ((answer instanceof java.util.concurrent.CompletionException
                || answer instanceof java.util.concurrent.ExecutionException)
                && answer.getCause() != null) answer = answer.getCause();
        return answer;
    }

    private static String message(Throwable failure) {
        return failure.getMessage() == null || failure.getMessage().isBlank()
                ? failure.getClass().getSimpleName()
                : failure.getMessage();
    }
}
