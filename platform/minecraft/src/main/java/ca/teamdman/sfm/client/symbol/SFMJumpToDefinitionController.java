package ca.teamdman.sfm.client.symbol;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.client.action.SFMClientActionContext;
import ca.teamdman.sfm.client.context.SFMContextContribution;
import ca.teamdman.sfm.client.context.SFMContextDocumentProjection;
import ca.teamdman.sfm.client.context.SFMContextOriginId;
import ca.teamdman.sfm.client.context.SFMContextSnapshot;
import ca.teamdman.sfm.client.screen.SFMActionChoice;
import ca.teamdman.sfm.client.screen.SFMCommandPaletteScreen;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenMultiplexer;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspacePanelId;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

/** One-capture UI orchestration for the registered jump-to-definition action. */
public final class SFMJumpToDefinitionController {
    private static final String TOAST_REPLACEMENT_KEY = "sfm:symbol-definition";
    @FunctionalInterface
    interface ChoicePresenter {
        void open(SFMClientActionContext context, Component title, List<SFMActionChoice> choices);
    }

    @FunctionalInterface
    interface Navigator {
        SFMDefinitionNavigation.Result open(
                SFMScreenMultiplexer workspace,
                SFMWorkspacePanelId sourcePanelId,
                SFMSymbolServerProtocol.ServerHello hello,
                SFMDefinitionResult.Definition definition
        );
    }

    interface WorkspaceState {
        SFMContextSnapshot snapshot(SFMScreenMultiplexer workspace);

        boolean containsPanel(SFMScreenMultiplexer workspace, SFMWorkspacePanelId panelId);
    }

    private final ResourceLocation actionId;
    private final Supplier<SFMDefinitionLookupService> lookupService;
    private final Consumer<Runnable> clientExecutor;
    private final Predicate<SFMScreenMultiplexer> currentWorkspace;
    private final ChoicePresenter choicePresenter;
    private final Navigator navigator;
    private final SFMDefinitionChoiceSessionService choiceSessions;
    private final WorkspaceState workspaceState;

    public static SFMJumpToDefinitionController production(ResourceLocation actionId) {
        return new SFMJumpToDefinitionController(
                actionId,
                SFMSymbolNavigationRuntime::get,
                runnable -> Minecraft.getInstance().execute(runnable),
                workspace -> Minecraft.getInstance().screen == workspace,
                SFMCommandPaletteScreen::openChoices,
                SFMDefinitionNavigation::open,
                new SFMDefinitionChoiceSessionService(),
                new WorkspaceState() {
                    @Override public SFMContextSnapshot snapshot(SFMScreenMultiplexer workspace) {
                        return workspace.contextSnapshot();
                    }

                    @Override public boolean containsPanel(
                            SFMScreenMultiplexer workspace,
                            SFMWorkspacePanelId panelId
                    ) {
                        return workspace.containsPanel(panelId);
                    }
                }
        );
    }

    SFMJumpToDefinitionController(
            ResourceLocation actionId,
            Supplier<SFMDefinitionLookupService> lookupService,
            Consumer<Runnable> clientExecutor,
            Predicate<SFMScreenMultiplexer> currentWorkspace,
            ChoicePresenter choicePresenter,
            Navigator navigator,
            SFMDefinitionChoiceSessionService choiceSessions,
            WorkspaceState workspaceState
    ) {
        this.actionId = Objects.requireNonNull(actionId, "actionId");
        this.lookupService = Objects.requireNonNull(lookupService, "lookupService");
        this.clientExecutor = Objects.requireNonNull(clientExecutor, "clientExecutor");
        this.currentWorkspace = Objects.requireNonNull(currentWorkspace, "currentWorkspace");
        this.choicePresenter = Objects.requireNonNull(choicePresenter, "choicePresenter");
        this.navigator = Objects.requireNonNull(navigator, "navigator");
        this.choiceSessions = Objects.requireNonNull(choiceSessions, "choiceSessions");
        this.workspaceState = Objects.requireNonNull(workspaceState, "workspaceState");
    }

    public boolean begin(SFMClientActionContext context, Consumer<Component> feedback) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(feedback, "feedback");
        if (!(context.originatingHost() instanceof SFMScreenMultiplexer workspace)
                || context.originatingPanelId() == null) {
            return reject(null, feedback, "Jump to definition requires a focused SFM text editor");
        }
        SFMWorkspacePanelId sourcePanelId = context.originatingPanelId();
        SFMContextSnapshot snapshot = workspaceState.snapshot(workspace);
        Optional<SFMContextContribution> focused = focusedDocument(snapshot);
        if (focused.isEmpty()) {
            return reject(workspace, feedback,
                    "The focused editor has no active path-addressed text position");
        }
        SFMContextContribution capturedContribution = focused.orElseThrow();

        SFMClientActionContext durableContext = new SFMClientActionContext(
                workspace,
                () -> workspaceState.containsPanel(workspace, sourcePanelId)
                        && (currentWorkspace.test(workspace)
                        || context.originatingHostIsCurrent().getAsBoolean()),
                sourcePanelId
        );
        show(workspace, feedback, Component.literal("Looking up definition...").withStyle(ChatFormatting.GRAY));
        SFMDefinitionLookupService.Submission submission;
        try {
            submission = lookupService.get().query(capturedContribution);
        } catch (RuntimeException failure) {
            return reject(workspace, feedback, failureMessage(failure));
        }
        submission.result().whenComplete((lookup, failure) -> clientExecutor.accept(() -> {
            boolean hostIsCurrent = durableContext.originatingHostIsCurrent().getAsBoolean();
            SFM.LOGGER.info("SFM_DEFINITION_ACTION_COMPLETED host_current={} success={} failure_type={}",
                    hostIsCurrent,
                    failure == null,
                    failure == null ? "none" : failure.getClass().getSimpleName());
            if (!hostIsCurrent) return;
            if (!sameFocusedContribution(workspaceState.snapshot(workspace), capturedContribution)) {
                reject(workspace, feedback,
                        "Jump to definition ignored because the editor document or cursor changed");
                return;
            }
            if (failure != null) {
                reject(workspace, feedback, failureMessage(failure));
                return;
            }
            present(durableContext, workspace, sourcePanelId, lookup, feedback);
        }));
        return true;
    }

    public boolean select(
            SFMClientActionContext context,
            long sessionId,
            int candidateIndex,
            String label,
            Consumer<Component> feedback
    ) {
        Optional<SFMDefinitionChoiceSessionService.Selection> selected = choiceSessions.consume(
                sessionId, candidateIndex, label, context);
        if (selected.isEmpty()) {
            SFMScreenMultiplexer workspace = context.originatingHost() instanceof SFMScreenMultiplexer value
                    ? value : null;
            return reject(workspace, feedback, "That definition choice is stale or no longer available");
        }
        SFMDefinitionChoiceSessionService.Selection selection = selected.orElseThrow();
        SFMDefinitionNavigation.Result navigation = navigator.open(
                selection.workspace(),
                selection.sourcePanelId(),
                selection.hello(),
                selection.definition()
        );
        showNavigation(selection.workspace(), feedback, navigation);
        return navigation.applied();
    }

    private void present(
            SFMClientActionContext durableContext,
            SFMScreenMultiplexer workspace,
            SFMWorkspacePanelId sourcePanelId,
            SFMDefinitionLookupService.Lookup lookup,
            Consumer<Component> feedback
    ) {
        SFMDefinitionResult result = lookup.result();
        SFMDefinitionOutcomeRouter.Decision decision = SFMDefinitionOutcomeRouter.route(result);
        SFM.LOGGER.info("SFM_DEFINITION_ACTION_PRESENT outcome={} definitions={} decision={}",
                result.outcome(), result.definitions().size(), decision.getClass().getSimpleName());
        if (decision instanceof SFMDefinitionOutcomeRouter.Open open) {
            SFMDefinitionNavigation.Result navigation = navigator.open(
                    workspace, sourcePanelId, lookup.hello(), open.definition());
            showNavigation(workspace, feedback, navigation);
            return;
        }
        if (decision instanceof SFMDefinitionOutcomeRouter.Choose choose) {
            SFMDefinitionChoiceSessionService.ChoiceSet choices = choiceSessions.create(
                    actionId, workspace, sourcePanelId, lookup.hello(), choose.definitions());
            choicePresenter.open(
                    durableContext,
                    Component.literal("Choose definition (" + choose.definitions().size() + ")"),
                    choices.choices()
            );
            return;
        }
        SFMDefinitionOutcomeRouter.Report report = (SFMDefinitionOutcomeRouter.Report) decision;
        Component summary = Component.literal(report.message()).withStyle(
                report.incomplete()
                        ? ChatFormatting.YELLOW : ChatFormatting.RED);
        show(workspace, feedback, summary);
        result.diagnostics().stream().limit(4).forEach(diagnostic -> feedback.accept(
                Component.literal(diagnostic.code() + ": " + diagnostic.message())
                        .withStyle(ChatFormatting.GRAY)));
        result.recoveryActions().stream().limit(4).forEach(recovery -> feedback.accept(
                Component.literal("Recovery: " + recovery.label()
                        + recovery.command().map(command -> " — " + command).orElse(""))
                        .withStyle(ChatFormatting.AQUA)));
        feedback.accept(Component.literal(
                "Retry: sfm action invoke " + actionId).withStyle(ChatFormatting.AQUA));
    }

    private static Optional<SFMContextContribution> focusedDocument(SFMContextSnapshot snapshot) {
        Optional<SFMContextOriginId> focused = snapshot.focusedOriginId();
        if (focused.isEmpty()) return Optional.empty();
        return snapshot.contributions().stream()
                .filter(contribution -> contribution.originId().equals(focused.orElseThrow()))
                .filter(contribution -> contribution.projection() instanceof SFMContextDocumentProjection)
                .findFirst();
    }

    /**
     * Results are valid only for the exact focused contribution that produced
     * the request. Record equality covers the stable origin, contributor
     * generations, current document identity/hash, cursors, and selections.
     */
    private static boolean sameFocusedContribution(
            SFMContextSnapshot currentSnapshot,
            SFMContextContribution capturedContribution
    ) {
        return focusedDocument(currentSnapshot)
                .filter(capturedContribution::equals)
                .isPresent();
    }

    private static void showNavigation(
            SFMScreenMultiplexer workspace,
            Consumer<Component> feedback,
            SFMDefinitionNavigation.Result result
    ) {
        SFM.LOGGER.info("SFM_DEFINITION_NAVIGATION status={} applied={} panel={}",
                result.status(), result.applied(), result.panelId());
        Component message = Component.literal(result.message()).withStyle(
                result.applied() ? ChatFormatting.AQUA : ChatFormatting.RED);
        show(workspace, feedback, message);
    }

    private static boolean reject(
            SFMScreenMultiplexer workspace,
            Consumer<Component> feedback,
            String message
    ) {
        show(workspace, feedback, Component.literal(message).withStyle(ChatFormatting.RED));
        return false;
    }

    private static void show(
            SFMScreenMultiplexer workspace,
            Consumer<Component> feedback,
            Component message
    ) {
        feedback.accept(message);
        if (workspace != null) workspace.showWorkspaceToast(TOAST_REPLACEMENT_KEY, message, false);
    }

    static String failureMessage(Throwable failure) {
        Throwable current = failure;
        while ((current instanceof java.util.concurrent.CompletionException
                || current instanceof java.util.concurrent.ExecutionException)
                && current.getCause() != null) current = current.getCause();
        if (current instanceof CancellationException
                || current instanceof SFMDefinitionQueryCoordinator.StaleResponseException) {
            return "A newer jump-to-definition request superseded this one";
        }
        String message = current.getMessage();
        return "Jump to definition unavailable: "
                + (message == null || message.isBlank() ? current.getClass().getSimpleName() : message);
    }
}
