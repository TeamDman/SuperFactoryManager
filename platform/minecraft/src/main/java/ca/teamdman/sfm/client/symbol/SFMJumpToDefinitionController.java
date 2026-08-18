package ca.teamdman.sfm.client.symbol;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.client.action.SFMClientActionContext;
import ca.teamdman.sfm.client.context.SFMContextContribution;
import ca.teamdman.sfm.client.context.SFMContextSnapshot;
import ca.teamdman.sfm.client.screen.SFMActionChoice;
import ca.teamdman.sfm.client.screen.SFMCommandPaletteScreen;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenMultiplexer;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspacePanelId;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

    private final ResourceLocation actionId;
    private final Supplier<SFMDefinitionLookupService> lookupService;
    private final Consumer<Runnable> clientExecutor;
    private final Predicate<SFMScreenMultiplexer> currentWorkspace;
    private final ChoicePresenter choicePresenter;
    private final Navigator navigator;
    private final SFMDefinitionChoiceSessionService choiceSessions;
    private final SFMNavigationWorkspaceState workspaceState;
    private final SFMNavigationCompletionGate completionGate;
    private final Map<SFMScreenMultiplexer, Map<SFMWorkspacePanelId, Pending>> active =
            new IdentityHashMap<>();
    private long requestGeneration;

    public static SFMJumpToDefinitionController production(ResourceLocation actionId) {
        return new SFMJumpToDefinitionController(
                actionId,
                SFMSymbolNavigationRuntime::get,
                runnable -> Minecraft.getInstance().execute(runnable),
                workspace -> Minecraft.getInstance().screen == workspace,
                SFMCommandPaletteScreen::openChoices,
                SFMDefinitionNavigation::open,
                new SFMDefinitionChoiceSessionService(),
                SFMNavigationWorkspaceState.production(),
                SFMNavigationPuppetCompletionGate.productionHook()
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
            SFMNavigationWorkspaceState workspaceState
    ) {
        this(
                actionId,
                lookupService,
                clientExecutor,
                currentWorkspace,
                choicePresenter,
                navigator,
                choiceSessions,
                workspaceState,
                SFMNavigationCompletionGate.DIRECT
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
            SFMNavigationWorkspaceState workspaceState,
            SFMNavigationCompletionGate completionGate
    ) {
        this.actionId = Objects.requireNonNull(actionId, "actionId");
        this.lookupService = Objects.requireNonNull(lookupService, "lookupService");
        this.clientExecutor = Objects.requireNonNull(clientExecutor, "clientExecutor");
        this.currentWorkspace = Objects.requireNonNull(currentWorkspace, "currentWorkspace");
        this.choicePresenter = Objects.requireNonNull(choicePresenter, "choicePresenter");
        this.navigator = Objects.requireNonNull(navigator, "navigator");
        this.choiceSessions = Objects.requireNonNull(choiceSessions, "choiceSessions");
        this.workspaceState = Objects.requireNonNull(workspaceState, "workspaceState");
        this.completionGate = Objects.requireNonNull(completionGate, "completionGate");
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
        Optional<SFMContextContribution> focused = SFMNavigationWorkspaceState.focusedDocument(snapshot);
        if (focused.isEmpty()) {
            return reject(workspace, feedback,
                    "The focused editor has no active path-addressed text position");
        }
        SFMContextContribution capturedContribution = focused.orElseThrow();
        Optional<Object> panelEntryIdentity = workspaceState.panelEntryIdentity(workspace, sourcePanelId);
        if (panelEntryIdentity.isEmpty()) {
            return reject(workspace, feedback, "Jump to definition requires a live source panel");
        }

        SFMClientActionContext durableContext = new SFMClientActionContext(
                workspace,
                () -> workspaceState.containsPanel(workspace, sourcePanelId)
                        && (currentWorkspace.test(workspace)
                        || context.originatingHostIsCurrent().getAsBoolean()),
                sourcePanelId
        );
        SFMDefinitionLookupService.Submission submission;
        try {
            submission = lookupService.get().query(capturedContribution);
        } catch (RuntimeException failure) {
            return reject(workspace, feedback, failureMessage(failure));
        }
        SFMNavigationRequestWitness witness = SFMNavigationRequestWitness.capture(
                workspace,
                sourcePanelId,
                panelEntryIdentity.orElseThrow(),
                capturedContribution,
                nextRequestGeneration()
        );
        Pending pending = new Pending(witness, submission, feedback);
        replaceActive(workspace, pending);
        completionGate.accepted(witness);
        show(workspace, feedback, Component.literal("Looking up definition...").withStyle(ChatFormatting.GRAY));
        submission.result().whenComplete((lookup, failure) -> completionGate.dispatchCompletion(
                witness,
                () -> clientExecutor.accept(() -> {
                    if (!isActive(workspace, pending)) return;
                    SFMNavigationRequestWitness.Validation validity = validate(workspace, pending);
                    removeActive(workspace, pending);
                    if (!validity.isValid()) {
                        SFMNavigationRequestWitness.RejectionReason reason = validity.rejection().orElseThrow();
                        rejectPending(workspace, pending, reason);
                        return;
                    }
                    boolean hostIsCurrent = durableContext.originatingHostIsCurrent().getAsBoolean();
                    SFM.LOGGER.info("SFM_DEFINITION_ACTION_COMPLETED host_current={} success={} failure_type={}",
                            hostIsCurrent,
                            failure == null,
                            failure == null ? "none" : failure.getClass().getSimpleName());
                    if (!hostIsCurrent) return;
                    if (failure != null) {
                        reject(workspace, feedback, failureMessage(failure));
                        return;
                    }
                    present(durableContext, workspace, sourcePanelId, lookup, feedback);
                })
        ));
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

    private SFMNavigationRequestWitness.Validation validate(
            SFMScreenMultiplexer workspace,
            Pending pending
    ) {
        SFMContextSnapshot snapshot = workspaceState.snapshot(workspace);
        SFMNavigationRequestWitness witness = pending.witness();
        return witness.validate(
                workspace,
                workspaceState.panelEntryIdentity(workspace, witness.sourcePanelId()),
                workspaceState.contribution(
                        workspace,
                        witness.sourcePanelId(),
                        witness.originId(),
                        snapshot
                ),
                currentRequestGeneration(workspace, witness.sourcePanelId())
        );
    }

    private long nextRequestGeneration() {
        synchronized (active) {
            if (requestGeneration == Long.MAX_VALUE) {
                throw new IllegalStateException("Jump-to-definition request generation exhausted");
            }
            return ++requestGeneration;
        }
    }

    private void replaceActive(SFMScreenMultiplexer workspace, Pending pending) {
        Pending previous;
        synchronized (active) {
            previous = active.computeIfAbsent(workspace, ignored -> new LinkedHashMap<>())
                    .put(pending.witness().sourcePanelId(), pending);
        }
        if (previous != null) {
            previous.submission().cancel();
            rejectPending(
                    workspace,
                    previous,
                    SFMNavigationRequestWitness.RejectionReason.SUPERSEDED_REQUEST
            );
        }
    }

    private void rejectPending(
            SFMScreenMultiplexer workspace,
            Pending pending,
            SFMNavigationRequestWitness.RejectionReason reason
    ) {
        completionGate.rejected(pending.witness(), reason);
        SFM.LOGGER.info(
                "SFM_DEFINITION_ACTION_REJECTED request={} panel={} reason={} description={}",
                pending.witness().requestGeneration(),
                pending.witness().sourcePanelId().value(),
                reason.name(),
                reason.description()
        );
        reject(workspace, pending.feedback(), invalidationMessage(reason));
    }

    private boolean isActive(SFMScreenMultiplexer workspace, Pending pending) {
        synchronized (active) {
            Map<SFMWorkspacePanelId, Pending> byPanel = active.get(workspace);
            return byPanel != null && byPanel.get(pending.witness().sourcePanelId()) == pending;
        }
    }

    private void removeActive(SFMScreenMultiplexer workspace, Pending pending) {
        synchronized (active) {
            Map<SFMWorkspacePanelId, Pending> byPanel = active.get(workspace);
            if (byPanel == null || byPanel.get(pending.witness().sourcePanelId()) != pending) return;
            byPanel.remove(pending.witness().sourcePanelId());
            if (byPanel.isEmpty()) active.remove(workspace);
        }
    }

    private long currentRequestGeneration(
            SFMScreenMultiplexer workspace,
            SFMWorkspacePanelId panelId
    ) {
        synchronized (active) {
            Map<SFMWorkspacePanelId, Pending> byPanel = active.get(workspace);
            if (byPanel == null) return -1;
            Pending pending = byPanel.get(panelId);
            return pending == null ? -1 : pending.witness().requestGeneration();
        }
    }

    private static String invalidationMessage(SFMNavigationRequestWitness.RejectionReason reason) {
        return "Jump to definition ignored: " + reason.description();
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

    private record Pending(
            SFMNavigationRequestWitness witness,
            SFMDefinitionLookupService.Submission submission,
            Consumer<Component> feedback
    ) {
        private Pending {
            Objects.requireNonNull(witness, "witness");
            Objects.requireNonNull(submission, "submission");
            Objects.requireNonNull(feedback, "feedback");
        }
    }
}
