package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.client.explorer.SFMExplorerDocumentRevealCoordinator;
import ca.teamdman.sfm.client.explorer.SFMExplorerDocumentRevealCoordinator.Document;
import ca.teamdman.sfm.client.explorer.SFMExplorerDocumentRevealCoordinator.ExplorerTarget;
import ca.teamdman.sfm.client.explorer.SFMExplorerDocumentRevealCoordinator.Outcome;
import ca.teamdman.sfm.client.explorer.SFMExplorerRuntime;
import ca.teamdman.sfm.client.screen.SFMCommandPaletteScreen;
import ca.teamdman.sfm.client.screen.explorer.SFMExplorerPanel;
import ca.teamdman.sfm.client.screen.text_editor.SFMTextDocumentPanelState;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenMultiplexer;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspacePanelId;
import ca.teamdman.sfm.client.text_editor.SFMTextDocumentSnapshot;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletionStage;

/** Registered focused-document route into the generic explorer reveal policy. */
public final class SFMRevealInExplorerAction implements SFMClientAction<SFMRevealInExplorerAction.Target> {
    public static final ResourceLocation ID = new ResourceLocation("sfm", "explorer/reveal");

    record Target(
            SFMClientActionContext actionContext,
            SFMScreenMultiplexer workspace,
            SFMWorkspacePanelId sourcePanelId,
            Document document
    ) {
        Target {
            Objects.requireNonNull(actionContext, "actionContext");
            Objects.requireNonNull(workspace, "workspace");
            Objects.requireNonNull(sourcePanelId, "sourcePanelId");
            Objects.requireNonNull(document, "document");
        }
    }

    @FunctionalInterface
    interface RevealExecutor {
        CompletionStage<Outcome> reveal(Target target);
    }

    private final RevealExecutor revealExecutor;

    public SFMRevealInExplorerAction() {
        this(new ProductionRevealExecutor());
    }

    SFMRevealInExplorerAction(RevealExecutor revealExecutor) {
        this.revealExecutor = Objects.requireNonNull(revealExecutor, "revealExecutor");
    }

    @Override
    public Component title() {
        return Component.literal("Reveal document in Explorer");
    }

    @Override
    public Component description() {
        return Component.literal("Select the focused document's exact resolver path in a compatible explorer");
    }

    @Override
    public SFMClientActionRequirement<Target> requirement() {
        return context -> {
            if (!context.originatingHostIsCurrent().getAsBoolean()) {
                return SFMClientActionAvailability.unavailable(
                        SFMClientActionContext.ORIGINATING_HOST_CHANGED.getComponent()
                );
            }
            if (!(context.originatingHost() instanceof SFMScreenMultiplexer workspace)
                    || context.originatingPanelId() == null
                    || !(workspace.panelInstance(context.originatingPanelId())
                    instanceof SFMTextDocumentPanelState documentPanel)) {
                return SFMClientActionAvailability.unavailable(Component.literal(
                        "Focus an addressed SFM text document before revealing it in Explorer"
                ));
            }
            SFMTextDocumentSnapshot snapshot = documentPanel.documentSnapshot().orElse(null);
            if (snapshot == null || !snapshot.ready()) {
                return SFMClientActionAvailability.unavailable(Component.literal(
                        "The focused document has not finished loading"
                ));
            }
            if (snapshot.path().isEmpty() || snapshot.authorizedRoot().isEmpty()) {
                return SFMClientActionAvailability.unavailable(Component.literal(
                        "The focused document has no resolver-issued path to reveal"
                ));
            }
            try {
                return SFMClientActionAvailability.available(new Target(
                        context,
                        workspace,
                        context.originatingPanelId(),
                        new Document(snapshot.path().orElseThrow(), snapshot.authorizedRoot().orElseThrow())
                ));
            } catch (IllegalArgumentException invalidAuthority) {
                return SFMClientActionAvailability.unavailable(Component.literal(
                        invalidAuthority.getMessage() == null
                                ? "The focused document's resolver authority is invalid"
                                : invalidAuthority.getMessage()
                ));
            }
        };
    }

    @Override
    public int execute(Target target, CommandContext<SFMClientActionSource> context) throws CommandSyntaxException {
        CompletionStage<Outcome> completion;
        try {
            completion = Objects.requireNonNull(revealExecutor.reveal(target), "reveal completion");
        } catch (RuntimeException failure) {
            throw syntaxFailure(failure);
        }
        completion.whenComplete((outcome, failure) -> {
            if (failure != null) {
                Throwable cause = unwrap(failure);
                context.getSource().sendFeedback(Component.literal(
                        "Reveal in Explorer failed: " + message(cause)
                ).withStyle(ChatFormatting.RED));
                return;
            }
            context.getSource().sendFeedback(Component.literal(
                    (outcome.openedNew() ? "Opened Explorer and revealed " : "Revealed ")
                            + outcome.path().canonical()
            ));
        });
        return 1;
    }

    private static CommandSyntaxException syntaxFailure(Throwable failure) {
        return new SimpleCommandExceptionType(Component.literal(
                "Reveal in Explorer failed: " + message(failure)
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

    private static final class ProductionRevealExecutor implements RevealExecutor {
        private final SFMExplorerDocumentRevealCoordinator coordinator =
                new SFMExplorerDocumentRevealCoordinator();

        @Override
        public CompletionStage<Outcome> reveal(Target target) {
            SFMExplorerRuntime runtime = SFMExplorerRuntime.get();
            return coordinator.reveal(target.document(), new SFMExplorerDocumentRevealCoordinator.Host() {
                @Override
                public List<ExplorerTarget> existingTargets() {
                    var repository = runtime.repository().stateSnapshot();
                    Set<SFMWorkspacePanelId> visible = new HashSet<>();
                    target.workspace().visiblePanelEntries().forEach(entry -> visible.add(entry.id()));
                    return target.workspace().panelIds().stream()
                            .map(panelId -> candidate(panelId, repository, visible))
                            .filter(Objects::nonNull)
                            .toList();
                }

                @Override
                public ExplorerTarget openNew(ca.teamdman.sfm.client.explorer.SFMPath authorizedRoot) {
                    SFMExplorerPanel panel = (SFMExplorerPanel) runtime.openScene(authorizedRoot);
                    int opened = OpenPanelAction.openPanel(
                            target.actionContext(),
                            panel,
                            OpenPanelAction.Direction.RIGHT
                    );
                    if (opened == 0) {
                        runtime.discardExplorer(panel.explorerId());
                        throw new IllegalStateException("A new Explorer could not be appended to the workspace");
                    }
                    SFMWorkspacePanelId panelId = target.workspace().focusedPanelId();
                    return new ExplorerTarget(
                            panel.explorerId(),
                            panelId,
                            panel.sessionSnapshot().roots(),
                            true,
                            Long.MAX_VALUE,
                            panel::revealPath
                    );
                }

                @Override
                public boolean focus(SFMWorkspacePanelId panelId) {
                    boolean focused = target.workspace().focusPanel(panelId);
                    if (focused && Minecraft.getInstance().screen instanceof SFMCommandPaletteScreen palette) {
                        palette.onClose();
                    }
                    return focused;
                }

                private ExplorerTarget candidate(
                        SFMWorkspacePanelId panelId,
                        ca.teamdman.sfm.client.explorer.action.SFMExplorerRepository.StateSnapshot repository,
                        Set<SFMWorkspacePanelId> visible
                ) {
                    if (!(target.workspace().panelInstance(panelId) instanceof SFMExplorerPanel panel)) return null;
                    if (!repository.explorers().containsKey(panel.explorerId())) return null;
                    long recency = repository.focusRecency().getOrDefault(panel.explorerId(), 0L);
                    return new ExplorerTarget(
                            panel.explorerId(),
                            panelId,
                            panel.sessionSnapshot().roots(),
                            visible.contains(panelId),
                            recency,
                            panel::revealPath
                    );
                }
            });
        }
    }
}
