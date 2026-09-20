package ca.teamdman.sfm.client.symbol;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.client.action.OpenPanelAction;
import ca.teamdman.sfm.client.action.SFMClientActionContext;
import ca.teamdman.sfm.client.context.SFMContextContribution;
import ca.teamdman.sfm.client.context.SFMContextSnapshot;
import ca.teamdman.sfm.client.explorer.SFMExplorerRuntime;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenMultiplexer;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspacePanelId;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
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

/** One-capture orchestration for persistent symbol-reference explorers. */
public final class SFMFindReferencesController {
    private static final String TOAST_REPLACEMENT_KEY = "sfm:symbol-references";
    private static final SFMFindReferencesController PRODUCTION = createProduction();

    @FunctionalInterface
    interface Presenter {
        boolean open(
                SFMClientActionContext context,
                SFMScreenMultiplexer workspace,
                SFMWorkspacePanelId sourcePanelId,
                SFMReferenceLookupService.Lookup lookup
        );
    }

    private final Supplier<SFMReferenceLookupService> lookupService;
    private final Consumer<Runnable> clientExecutor;
    private final Predicate<SFMScreenMultiplexer> currentWorkspace;
    private final Presenter presenter;
    private final SFMNavigationWorkspaceState workspaceState;
    private final Map<SFMScreenMultiplexer, Map<SFMWorkspacePanelId, Pending>> active =
            new IdentityHashMap<>();
    private long requestGeneration;

    public static SFMFindReferencesController production() {
        return PRODUCTION;
    }

    private static SFMFindReferencesController createProduction() {
        return new SFMFindReferencesController(
                SFMSymbolNavigationRuntime::get,
                runnable -> Minecraft.getInstance().execute(runnable),
                workspace -> Minecraft.getInstance().screen == workspace,
                SFMFindReferencesController::openPersistentExplorer,
                SFMNavigationWorkspaceState.production()
        );
    }

    /**
     * Cancels reference work as soon as its captured editor identity ceases to
     * be current. The workspace calls this once per client tick so an obsolete
     * query does not keep consuming worker capacity until its eventual result.
     */
    public static void tickProduction(SFMScreenMultiplexer workspace) {
        PRODUCTION.cancelStale(workspace);
    }

    /** Cancels owned worker work when its workspace leaves the screen lifecycle. */
    public static void workspaceRemovedProduction(SFMScreenMultiplexer workspace) {
        PRODUCTION.cancelWorkspace(workspace);
    }

    SFMFindReferencesController(
            Supplier<SFMReferenceLookupService> lookupService,
            Consumer<Runnable> clientExecutor,
            Predicate<SFMScreenMultiplexer> currentWorkspace,
            Presenter presenter,
            SFMNavigationWorkspaceState workspaceState
    ) {
        this.lookupService = Objects.requireNonNull(lookupService, "lookupService");
        this.clientExecutor = Objects.requireNonNull(clientExecutor, "clientExecutor");
        this.currentWorkspace = Objects.requireNonNull(currentWorkspace, "currentWorkspace");
        this.presenter = Objects.requireNonNull(presenter, "presenter");
        this.workspaceState = Objects.requireNonNull(workspaceState, "workspaceState");
    }

    public boolean begin(SFMClientActionContext context, Consumer<Component> feedback) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(feedback, "feedback");
        if (!(context.originatingHost() instanceof SFMScreenMultiplexer workspace)
                || context.originatingPanelId() == null) {
            return reject(null, feedback, "Find references requires a focused SFM text editor");
        }
        SFMWorkspacePanelId sourcePanelId = context.originatingPanelId();
        SFMContextSnapshot snapshot = workspaceState.snapshot(workspace);
        Optional<SFMContextContribution> focused = SFMNavigationWorkspaceState.focusedDocument(snapshot);
        if (focused.isEmpty()) {
            return reject(workspace, feedback, "The focused editor has no active path-addressed text position");
        }
        SFMContextContribution captured = focused.orElseThrow();
        Optional<Object> panelEntryIdentity = workspaceState.panelEntryIdentity(workspace, sourcePanelId);
        if (panelEntryIdentity.isEmpty()) {
            return reject(workspace, feedback, "Find references requires a live source panel");
        }
        SFMClientActionContext durableContext = new SFMClientActionContext(
                workspace,
                () -> workspaceState.containsPanel(workspace, sourcePanelId)
                        && (currentWorkspace.test(workspace)
                        || context.originatingHostIsCurrent().getAsBoolean()),
                sourcePanelId
        );
        SFMReferenceLookupService.Submission submission;
        try {
            submission = lookupService.get().queryReferences(captured);
        } catch (RuntimeException failure) {
            return reject(workspace, feedback, failureMessage(failure));
        }
        SFMNavigationRequestWitness witness = SFMNavigationRequestWitness.capture(
                workspace,
                sourcePanelId,
                panelEntryIdentity.orElseThrow(),
                captured,
                nextRequestGeneration()
        );
        Pending pending = new Pending(witness, submission, feedback);
        replaceActive(workspace, pending);
        show(workspace, feedback, Component.literal("Finding references...").withStyle(ChatFormatting.GRAY));
        submission.result().whenComplete((lookup, failure) -> clientExecutor.accept(() -> {
            if (!isActive(workspace, pending)) return;
            SFMNavigationRequestWitness.Validation validity = validate(workspace, pending);
            removeActive(workspace, pending);
            if (!validity.isValid()) {
                reject(workspace, feedback, invalidationMessage(
                        "Find references ignored",
                        validity.rejection().orElseThrow()
                ));
                return;
            }
            if (!durableContext.originatingHostIsCurrent().getAsBoolean()) return;
            if (failure != null) {
                reject(workspace, feedback, failureMessage(failure));
                return;
            }
            boolean opened = presenter.open(durableContext, workspace, sourcePanelId, lookup);
            SFM.LOGGER.info("SFM_REFERENCES_ACTION_PRESENTED outcome={} usages={} completeness={} opened={}",
                    lookup.result().outcome(), lookup.result().usages().size(),
                    lookup.result().completeness(), opened);
            show(workspace, feedback, Component.literal(opened
                    ? "Opened persistent references (" + lookup.result().usages().size() + ")"
                    : "The references explorer could not be opened")
                    .withStyle(opened ? ChatFormatting.AQUA : ChatFormatting.RED));
        }));
        return true;
    }

    void cancelStale(SFMScreenMultiplexer workspace) {
        Objects.requireNonNull(workspace, "workspace");
        List<StalePending> stale = new ArrayList<>();
        synchronized (active) {
            Map<SFMWorkspacePanelId, Pending> byPanel = active.get(workspace);
            if (byPanel == null || byPanel.isEmpty()) return;
            byPanel.values().removeIf(pending -> {
                SFMNavigationRequestWitness.Validation validity = currentWorkspace.test(workspace)
                        ? validate(workspace, pending)
                        : SFMNavigationRequestWitness.Validation.rejected(
                                SFMNavigationRequestWitness.RejectionReason.WORKSPACE_NOT_CURRENT);
                if (validity.isValid()) return false;
                stale.add(new StalePending(pending, validity.rejection().orElseThrow()));
                return true;
            });
            if (byPanel.isEmpty()) active.remove(workspace);
        }
        for (StalePending stalePending : stale) {
            Pending pending = stalePending.pending();
            pending.submission().cancel();
            show(workspace, pending.feedback(), Component.literal(invalidationMessage(
                    "Find references cancelled",
                    stalePending.reason()
            )).withStyle(ChatFormatting.GRAY));
        }
    }

    void cancelWorkspace(SFMScreenMultiplexer workspace) {
        Objects.requireNonNull(workspace, "workspace");
        List<Pending> removed;
        synchronized (active) {
            Map<SFMWorkspacePanelId, Pending> byPanel = active.remove(workspace);
            if (byPanel == null || byPanel.isEmpty()) return;
            removed = List.copyOf(byPanel.values());
        }
        removed.forEach(pending -> pending.submission().cancel());
    }

    private void replaceActive(SFMScreenMultiplexer workspace, Pending pending) {
        Pending previous;
        synchronized (active) {
            previous = active.computeIfAbsent(workspace, ignored -> new LinkedHashMap<>())
                    .put(pending.witness().sourcePanelId(), pending);
        }
        if (previous != null) {
            previous.submission().cancel();
            show(workspace, previous.feedback(), Component.literal(invalidationMessage(
                    "Find references cancelled",
                    SFMNavigationRequestWitness.RejectionReason.SUPERSEDED_REQUEST
            )).withStyle(ChatFormatting.GRAY));
        }
    }

    private boolean isActive(SFMScreenMultiplexer workspace, Pending pending) {
        synchronized (active) {
            Map<SFMWorkspacePanelId, Pending> byPanel = active.get(workspace);
            return byPanel != null && byPanel.get(pending.witness().sourcePanelId()) == pending;
        }
    }

    private boolean removeActive(SFMScreenMultiplexer workspace, Pending pending) {
        synchronized (active) {
            Map<SFMWorkspacePanelId, Pending> byPanel = active.get(workspace);
            if (byPanel == null || byPanel.get(pending.witness().sourcePanelId()) != pending) return false;
            byPanel.remove(pending.witness().sourcePanelId());
            if (byPanel.isEmpty()) active.remove(workspace);
            return true;
        }
    }

    private static boolean openPersistentExplorer(
            SFMClientActionContext context,
            SFMScreenMultiplexer workspace,
            SFMWorkspacePanelId sourcePanelId,
            SFMReferenceLookupService.Lookup lookup
    ) {
        SFMExplorerRuntime runtime = SFMExplorerRuntime.get();
        SFMExplorerRuntime.ReferenceExplorer reference = runtime.publishReferenceResult(
                lookup.result(), lookup.hello(), workspace, sourcePanelId);
        int opened = OpenPanelAction.openPanel(
                context,
                reference.panel(),
                OpenPanelAction.Direction.RIGHT
        );
        if (opened == 0) runtime.discardExplorer(reference.panel().explorerId());
        return opened != 0;
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
                throw new IllegalStateException("Find-references request generation exhausted");
            }
            return ++requestGeneration;
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

    private static String invalidationMessage(
            String prefix,
            SFMNavigationRequestWitness.RejectionReason reason
    ) {
        return prefix + ": " + reason.description();
    }

    static String failureMessage(Throwable failure) {
        Throwable current = failure;
        while ((current instanceof java.util.concurrent.CompletionException
                || current instanceof java.util.concurrent.ExecutionException)
                && current.getCause() != null) current = current.getCause();
        if (current instanceof CancellationException
                || current instanceof SFMUsageQueryCoordinator.StaleResponseException) {
            return "A newer find-references request superseded this one";
        }
        String message = current.getMessage();
        return "Find references unavailable: "
                + (message == null || message.isBlank() ? current.getClass().getSimpleName() : message);
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

    private record Pending(
            SFMNavigationRequestWitness witness,
            SFMReferenceLookupService.Submission submission,
            Consumer<Component> feedback
    ) {
        private Pending {
            Objects.requireNonNull(witness, "witness");
            Objects.requireNonNull(submission, "submission");
            Objects.requireNonNull(feedback, "feedback");
        }
    }

    private record StalePending(
            Pending pending,
            SFMNavigationRequestWitness.RejectionReason reason
    ) {
        private StalePending {
            Objects.requireNonNull(pending, "pending");
            Objects.requireNonNull(reason, "reason");
        }
    }
}
