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
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;

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
            SFMTextDocumentSnapshot documentSnapshot,
            Optional<SFMPath> directContainingRoot
    ) {
        Target {
            Objects.requireNonNull(actionContext, "actionContext");
            Objects.requireNonNull(workspace, "workspace");
            Objects.requireNonNull(explorerPanelId, "explorerPanelId");
            Objects.requireNonNull(explorer, "explorer");
            Objects.requireNonNull(documentPanelId, "documentPanelId");
            Objects.requireNonNull(documentPanel, "documentPanel");
            Objects.requireNonNull(documentSnapshot, "documentSnapshot");
            directContainingRoot = Objects.requireNonNull(directContainingRoot, "directContainingRoot");
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
        if (containingRoot == null && !SFMReleaseReviewExplorerRuntime.get().hasLensRoot(
                explorer.sessionSnapshot().roots()
        )) {
            return SFMClientActionAvailability.unavailable(Component.literal(
                    "This Explorer is not authorized to reveal " + path.canonical()
            ));
        }
        return SFMClientActionAvailability.available(new Target(
                context,
                workspace,
                context.originatingPanelId(),
                explorer,
                recent.id(),
                recent.panel(),
                snapshot,
                Optional.ofNullable(containingRoot)
        ));
    }

    /** Cheap visibility query; it never builds a release-review projection. */
    public static boolean isControlVisible(SFMClientActionContext context) {
        return capture(Objects.requireNonNull(context, "context")).isAvailable();
    }

    /**
     * Executes the registered reveal behavior from its semantic panel control
     * without round-tripping through Brigadier's availability-filtered tree.
     */
    public static boolean invokeFromControl(
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
        beginReveal(availability.target(), feedback);
        return true;
    }

    @Override
    public int execute(Target target, CommandContext<SFMClientActionSource> context) throws CommandSyntaxException {
        if (!target.stillCurrent()) {
            throw new SimpleCommandExceptionType(Component.literal(
                    "Reveal target became stale before execution"
            )).create();
        }

        beginReveal(target, context.getSource()::sendFeedback);
        return 1;
    }

    private static void beginReveal(Target target, Consumer<Component> feedback) {
        SFMPath sourcePath = target.documentSnapshot().path().orElseThrow();
        CompletionStage<java.util.List<SFMReleaseReviewExplorerRuntime.RevealTarget>> resolution;
        if (target.directContainingRoot().isPresent()) {
            resolution = CompletableFuture.completedFuture(java.util.List.of(
                    new SFMReleaseReviewExplorerRuntime.RevealTarget(
                            target.directContainingRoot().orElseThrow(),
                            sourcePath
                    )
            ));
        } else {
            feedback.accept(Component.literal("Finding the exact review row for " + sourcePath.canonical()));
            resolution = SFMReleaseReviewExplorerRuntime.get().revealTargetsAsync(
                    target.explorer().sessionSnapshot().roots(),
                    target.documentSnapshot()
            );
        }
        resolution.whenComplete((matches, resolutionFailure) -> {
            if (resolutionFailure != null) {
                feedback.accept(failure("Reveal target lookup failed", resolutionFailure));
                return;
            }
            if (!target.stillCurrent()) {
                feedback.accept(Component.literal(
                        "Reveal target became stale because the Explorer or document changed"
                ).withStyle(ChatFormatting.RED));
                return;
            }
            if (matches.isEmpty()) {
                feedback.accept(Component.literal(
                        "This Explorer has no exact row for " + sourcePath.canonical()
                ).withStyle(ChatFormatting.RED));
                return;
            }
            if (matches.size() > 1) {
                feedback.accept(Component.literal(
                        "More than one exact review row represents " + sourcePath.canonical()
                ).withStyle(ChatFormatting.RED));
                return;
            }
            SFMReleaseReviewExplorerRuntime.RevealTarget resolved = matches.get(0);
            CompletionStage<?> reveal;
            try {
                reveal = Objects.requireNonNull(
                        target.explorer().revealPath(resolved.containingRoot(), resolved.explorerPath()),
                        "reveal completion"
                );
            } catch (RuntimeException failure) {
                feedback.accept(failure("Reveal here failed", failure));
                return;
            }
            reveal.whenComplete((ignored, revealFailure) -> {
                if (revealFailure != null) {
                    feedback.accept(failure("Reveal here failed", revealFailure));
                    return;
                }
                feedback.accept(ca.teamdman.sfm.client.screen.workspace.toast.SFMWorkspaceToastContent.pathMessage(
                        "Revealed ", resolved.explorerPath(), " in this Explorer",
                        sourcePath.segments().isEmpty() ? sourcePath.authority()
                                : sourcePath.segments().get(sourcePath.segments().size() - 1)
                ));
            });
        });
    }

    private static Component failure(String prefix, Throwable failure) {
        return Component.literal(prefix + ": " + message(unwrap(failure))).withStyle(ChatFormatting.RED);
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
