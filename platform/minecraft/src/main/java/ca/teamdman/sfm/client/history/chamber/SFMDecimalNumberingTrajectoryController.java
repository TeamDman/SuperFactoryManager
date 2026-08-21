package ca.teamdman.sfm.client.history.chamber;

import ca.teamdman.sfm.client.history.SFMBoundedTrajectoryPlanner;
import ca.teamdman.sfm.client.history.SFMHistoryGraphContract;
import ca.teamdman.sfm.client.history.SFMHistoryGraphRuntime;
import ca.teamdman.sfm.client.history.SFMTrajectoryContract;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Locked authoritative aggregate for one temporal decimal-numbering episode.
 *
 * <p>Planning is projection-only. Step execution re-reads the current state,
 * validates the plan-scoped typed manifest, computes the complete transition,
 * and only then commits state, edge, head, instruction pointer, and supervision
 * in one monitor transaction.</p>
 */
public final class SFMDecimalNumberingTrajectoryController implements SFMHistoryGraphRuntime.Controller {
    public static final String INITIAL_TEXT = "- apples\n- bananas\n";
    public static final String THIRD_ITEM_TEXT = "- apricots\n";

    private final SFMChamberDocumentState.IdentityScope scope;
    private final SFMDecimalNumberingChamber chamber;
    private final Supplier<String> ambientCheckoutProbe;
    private final String ambientCheckoutBaseline;
    private final String historyHeadId;
    private final LinkedHashMap<String, SFMChamberDocumentState> states = new LinkedHashMap<>();
    private final LinkedHashMap<String, String> firstObservedStateHashes = new LinkedHashMap<>();
    private final ArrayList<CommittedTransition> committedTransitions = new ArrayList<>();
    private final ArrayList<SFMHistoryGraphContract.HeadMovement> headMovements = new ArrayList<>();
    private final ArrayList<SFMHistoryGraphContract.RetentionPin> retentionPins = new ArrayList<>();
    private final LinkedHashMap<String, SFMDecimalNumberingPlannerAdapter.ExecutionManifest> manifests =
            new LinkedHashMap<>();
    private final LinkedHashMap<String, SFMDecimalNumberingPlannerAdapter.PlanningBundle> planningBundles =
            new LinkedHashMap<>();
    private final LinkedHashMap<String, SFMDecimalNumberingChamber.SupervisionDefinition>
            supervisionDefinitionsByPlan = new LinkedHashMap<>();
    private final LinkedHashMap<String, SFMTrajectoryContract.SupervisionContract>
            supervisionContractsByPlan = new LinkedHashMap<>();
    private final ArrayList<ReplanLineage> replanLineage = new ArrayList<>();

    private String currentStateId;
    private SFMTrajectoryContract.PlanBook planBook = new SFMTrajectoryContract.PlanBook(
            SFMTrajectoryContract.SCHEMA,
            List.of(),
            Optional.empty()
    );
    private SFMDecimalNumberingChamber.SupervisionDefinition supervisionDefinition;
    private SFMTrajectoryContract.SupervisionContract activeSupervision;
    private SFMTrajectoryContract.TrajectoryMachineState machine;
    private long revision;
    private long planSequence;
    private long movementSequence;

    public SFMDecimalNumberingTrajectoryController(
            String episodeId,
            String documentId,
            String initialText,
            Supplier<String> ambientCheckoutProbe
    ) {
        scope = new SFMChamberDocumentState.IdentityScope(episodeId, documentId);
        chamber = new SFMDecimalNumberingChamber(scope);
        this.ambientCheckoutProbe = Objects.requireNonNull(ambientCheckoutProbe, "ambientCheckoutProbe");
        ambientCheckoutBaseline = measuredAmbientHash();
        historyHeadId = scope.qualify("head", "current");
        SFMChamberDocumentState initial = SFMChamberDocumentState.root(scope, initialText);
        retainState(initial);
        currentStateId = initial.revisionId();
        resetSupervisionFor(initial);
        machine = new SFMTrajectoryContract.TrajectoryMachineState(
                historyHeadId,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                activeSupervision.revision(),
                SFMTrajectoryContract.MachineStatus.IDLE,
                fullRemainingBudget(activeSupervision)
        );
    }

    @Override
    public synchronized String machineId() {
        return scope.episodeId();
    }

    public synchronized SFMChamberDocumentState currentState() {
        return requireState(currentStateId);
    }

    public synchronized String currentText() {
        return currentState().text();
    }

    public synchronized long revision() {
        return revision;
    }

    public synchronized List<ReplanLineage> replanLineage() {
        return List.copyOf(replanLineage);
    }

    /** Immutable retained document revisions in deterministic first-observed order. */
    public synchronized List<SFMChamberDocumentState> retainedStates() {
        return List.copyOf(states.values());
    }

    /** Fresh byte-level evidence that the chamber has not changed its protected ambient checkout. */
    public synchronized CheckoutEvidence measureCheckoutEvidence() {
        String current = measuredAmbientHash();
        return new CheckoutEvidence(
                "sfm.chamber-checkout-evidence/1",
                ambientCheckoutBaseline,
                current,
                ambientCheckoutBaseline.equals(current)
        );
    }

    public synchronized Optional<SFMDecimalNumberingPlannerAdapter.PlanningBundle> planningBundle(
            String planRevisionId
    ) {
        return Optional.ofNullable(planningBundles.get(planRevisionId));
    }

    public synchronized List<String> childRevisionIds(String parentRevisionId) {
        return states.values().stream()
                .filter(state -> state.parentRevisionId().filter(parentRevisionId::equals).isPresent())
                .map(SFMChamberDocumentState::revisionId)
                .sorted()
                .toList();
    }

    @Override
    public synchronized SFMHistoryGraphRuntime.MachineSnapshot snapshot() {
        return new SFMHistoryGraphRuntime.MachineSnapshot(
                machineId(),
                revision,
                historyGraph(),
                planBook,
                machine,
                Optional.of(activeSupervision),
                summary()
        );
    }

    @Override
    public synchronized SFMHistoryGraphRuntime.OperationResult apply(SFMHistoryGraphRuntime.Operation operation) {
        Objects.requireNonNull(operation, "operation");
        if (operation instanceof SFMHistoryGraphRuntime.Plan) return plan(false);
        if (operation instanceof SFMHistoryGraphRuntime.Replan) return plan(true);
        if (operation instanceof SFMHistoryGraphRuntime.Step) return step();
        if (operation instanceof SFMHistoryGraphRuntime.Run run) return run(run.maxSteps());
        if (operation instanceof SFMHistoryGraphRuntime.Pause) return pause();
        if (operation instanceof SFMHistoryGraphRuntime.SelectRoute select) {
            return selectRoute(select.planRevisionId(), select.routeId());
        }
        if (operation instanceof SFMHistoryGraphRuntime.InspectCost) return inspectCost();
        return SFMHistoryGraphRuntime.OperationResult.rejected("Unsupported chamber operation");
    }

    public synchronized SFMHistoryGraphRuntime.OperationResult undo(String actor, String requestId) {
        SFMChamberDocumentState current = currentState();
        if (current.parentRevisionId().isEmpty()) {
            return SFMHistoryGraphRuntime.OperationResult.noChange("Document head is already at its root");
        }
        String parentId = current.parentRevisionId().orElseThrow();
        requireState(parentId);
        String movementId = scope.qualify("head-movement", Long.toString(++movementSequence));
        headMovements.add(new SFMHistoryGraphContract.HeadMovement(
                movementId,
                SFMHistoryGraphContract.HeadMovementKind.UNDO,
                historyHeadId,
                current.revisionId(),
                parentId,
                List.of(parentId),
                requireText(actor, "actor"),
                requireText(requestId, "requestId")
        ));
        retentionPins.add(new SFMHistoryGraphContract.RetentionPin(
                scope.qualify("retention-pin", "undo-" + movementSequence),
                SFMHistoryGraphContract.RetentionKind.NAMED_BRANCH,
                current.revisionId(),
                movementId
        ));
        currentStateId = parentId;
        Optional<SFMTrajectoryContract.InstructionPointer> pointer = pointerForSelectedPlan(parentId);
        machine = new SFMTrajectoryContract.TrajectoryMachineState(
                historyHeadId,
                machine.selectedTrajectoryRevisionId(),
                pointer,
                machine.projectionFrontier(),
                activeSupervision.revision(),
                SFMTrajectoryContract.MachineStatus.PAUSED,
                machine.remainingBudget()
        );
        activeSupervision = withStatus(activeSupervision, SFMTrajectoryContract.SupervisionStatus.RUNNING);
        machine = withSupervision(machine, activeSupervision, SFMTrajectoryContract.MachineStatus.PAUSED);
        revision++;
        return SFMHistoryGraphRuntime.OperationResult.applied(
                "Moved document head to retained parent " + parentId
        );
    }

    public synchronized SFMHistoryGraphRuntime.OperationResult insertText(
            int codePointOffset,
            String insertedText,
            String actor
    ) {
        SFMDecimalNumberingChamber.Transition transition = chamber.literalInsert(
                currentState(),
                codePointOffset,
                insertedText
        );
        commitTransition(transition, "manual-insert:" + requireText(actor, "actor"));
        refreshSupervisionAfterManualEdit();
        machine = new SFMTrajectoryContract.TrajectoryMachineState(
                historyHeadId,
                machine.selectedTrajectoryRevisionId(),
                machine.instructionPointer(),
                machine.projectionFrontier(),
                activeSupervision.revision(),
                SFMTrajectoryContract.MachineStatus.STALE_PRECONDITION,
                machine.remainingBudget()
        );
        revision++;
        return SFMHistoryGraphRuntime.OperationResult.applied(
                "Inserted " + insertedText.codePointCount(0, insertedText.length()) + " code points"
        );
    }

    /** Accepts natural editor input only when it is a pure insertion into the bounded overlay. */
    public synchronized SFMHistoryGraphRuntime.OperationResult acceptEditorText(String editedText) {
        editedText = SFMChamberDocumentState.requireWellFormedUtf16(editedText, "editedText");
        String current = currentText();
        if (current.equals(editedText)) {
            return SFMHistoryGraphRuntime.OperationResult.noChange("Editor text already matches the document head");
        }
        int[] before = current.codePoints().toArray();
        int[] after = editedText.codePoints().toArray();
        int prefix = 0;
        while (prefix < before.length && prefix < after.length && before[prefix] == after[prefix]) prefix++;
        int suffix = 0;
        while (suffix < before.length - prefix
                && suffix < after.length - prefix
                && before[before.length - 1 - suffix] == after[after.length - 1 - suffix]) {
            suffix++;
        }
        if (prefix + suffix != before.length || after.length <= before.length) {
            return SFMHistoryGraphRuntime.OperationResult.rejected(
                    "The bounded numbering chamber currently accepts insertion edits only"
            );
        }
        String inserted = new String(after, prefix, after.length - prefix - suffix);
        return insertText(prefix, inserted, "text-editor-v3");
    }

    private SFMHistoryGraphRuntime.OperationResult plan(boolean replan) {
        SFMChamberDocumentState start = currentState();
        SFMDecimalNumberingChamber.TargetDerivation targetDerivation = chamber.deriveTarget(start);
        if (targetDerivation.target().isEmpty()) {
            return SFMHistoryGraphRuntime.OperationResult.rejected(
                    "Cannot plan this chamber state: " + targetDerivation.diagnosticsSummary()
            );
        }
        SFMDecimalNumberingChamber.Target target = targetDerivation.target().orElseThrow();
        SFMDecimalNumberingChamber.SupervisionDefinition plannedSupervision =
                chamber.supervision(start, target);

        String previousSelectedPlan = planBook.selectedPlanRevisionId().orElse(null);
        Optional<String> parentPlanRevisionId = Optional.ofNullable(previousSelectedPlan)
                .flatMap(this::planById)
                .filter(previous -> previous.startStateId().equals(start.revisionId()))
                .map(SFMTrajectoryContract.TrajectoryPlanRevision::id);
        long sequence = ++planSequence;
        String planId = scope.qualify("plan", Long.toString(sequence));
        String routeId = scope.qualify("route", Long.toString(sequence));
        SFMDecimalNumberingPlannerAdapter.PlanningBundle bundle =
                SFMDecimalNumberingPlannerAdapter.plan(
                        chamber,
                        start,
                        target,
                        plannedSupervision,
                        measuredInvariantEvidence(),
                        planId,
                        replan ? parentPlanRevisionId : Optional.empty(),
                        routeId,
                        SFMTrajectoryContract.SearchAlgorithm.A_STAR,
                        SFMBoundedTrajectoryPlanner.CancellationToken.never(),
                        SFMBoundedTrajectoryPlanner.NanoClock.system()
                );
        SFMTrajectoryContract.TrajectoryPlanRevision planned = bundle.result().plan();
        planBook = SFMBoundedTrajectoryPlanner.appendPlanRevision(planBook, planned, true);
        manifests.put(planned.id(), bundle.manifest());
        planningBundles.put(planned.id(), bundle);
        supervisionDefinitionsByPlan.put(planned.id(), plannedSupervision);
        supervisionContractsByPlan.put(planned.id(), plannedSupervision.contract());
        supervisionDefinition = plannedSupervision;
        activeSupervision = plannedSupervision.contract();
        if (replan && previousSelectedPlan != null) {
            replanLineage.add(new ReplanLineage(
                    scope.qualify("replan-lineage", Long.toString(sequence)),
                    previousSelectedPlan,
                    planned.id(),
                    start.revisionId(),
                    parentPlanRevisionId.isPresent()
                            ? "same-start child plan revision"
                            : "new authoritative start; retained source plan"
            ));
        }
        Optional<SFMTrajectoryContract.InstructionPointer> pointer = planned.selectedRouteId().map(selected ->
                new SFMTrajectoryContract.InstructionPointer(planned.id(), selected, 0));
        SFMTrajectoryContract.MachineStatus status = planned.result() == SFMTrajectoryContract.PlanResult.FOUND
                ? SFMTrajectoryContract.MachineStatus.READY
                : planned.result() == SFMTrajectoryContract.PlanResult.CANCELLED
                        ? SFMTrajectoryContract.MachineStatus.PAUSED
                        : SFMTrajectoryContract.MachineStatus.BLOCKED;
        machine = new SFMTrajectoryContract.TrajectoryMachineState(
                historyHeadId,
                Optional.of(planned.id()),
                pointer,
                Optional.of(bundle.result().projectionFrontier()),
                activeSupervision.revision(),
                status,
                remainingBudget(activeSupervision, bundle.result())
        );
        revision++;
        return planned.result() == SFMTrajectoryContract.PlanResult.FOUND
                ? SFMHistoryGraphRuntime.OperationResult.applied(
                        (replan ? "Replanned" : "Planned") + " minimum-cost route "
                                + planned.selectedRouteId().orElseThrow()
                                + " at cost " + selectedRoute(planned).totalCost())
                : SFMHistoryGraphRuntime.OperationResult.rejected(planned.resultExplanation());
    }

    private SFMHistoryGraphRuntime.OperationResult step() {
        Optional<String> selectedPlanId = machine.selectedTrajectoryRevisionId();
        Optional<SFMTrajectoryContract.InstructionPointer> pointerValue = machine.instructionPointer();
        if (selectedPlanId.isEmpty() || pointerValue.isEmpty()) {
            return SFMHistoryGraphRuntime.OperationResult.noChange("No selected trajectory step is ready");
        }
        SFMTrajectoryContract.TrajectoryPlanRevision plan = planById(selectedPlanId.orElseThrow())
                .orElseThrow(() -> new IllegalStateException("Selected plan is absent from the retained plan book"));
        SFMTrajectoryContract.InstructionPointer pointer = pointerValue.orElseThrow();
        SFMChamberDocumentState authoritative = requireState(currentStateId); // fresh read inside lock
        SFMBoundedTrajectoryPlanner.StepValidation validation = SFMBoundedTrajectoryPlanner.validateNextStep(
                plan,
                pointer.routeId(),
                pointer.nextStepIndex(),
                authoritative.revisionId()
        );
        if (validation.readiness() == SFMBoundedTrajectoryPlanner.StepReadiness.STALE_PARENT) {
            machine = SFMBoundedTrajectoryPlanner.pauseOnStaleParent(
                    machine,
                    validation,
                    authoritative.revisionId()
            );
            revision++;
            return SFMHistoryGraphRuntime.OperationResult.applied(
                    validation.explanation()
                            + "; choose frozen witness re-execution, semantic re-evaluation, or Replan"
            );
        }
        if (validation.readiness() == SFMBoundedTrajectoryPlanner.StepReadiness.ROUTE_COMPLETE) {
            return SFMHistoryGraphRuntime.OperationResult.noChange("Selected route is already complete");
        }
        SFMTrajectoryContract.TrajectoryStep plannedStep = validation.step().orElseThrow();
        SFMDecimalNumberingPlannerAdapter.ExecutionManifest manifest = Objects.requireNonNull(
                manifests.get(plan.id()),
                "selected plan execution manifest"
        );
        SFMDecimalNumberingPlannerAdapter.ManifestStep executable = manifest.requireStep(plannedStep);
        if (!executable.routeId().equals(pointer.routeId())) {
            throw new IllegalStateException("Execution manifest step belongs to another route");
        }
        SFMDecimalNumberingChamber.Transition expectedTransition = executable.executableTransition();
        SFMChamberDocumentState computed = expectedTransition.action().apply(authoritative);
        if (!computed.equals(expectedTransition.result())
                || !computed.revisionId().equals(plannedStep.predictedStateId())
                || !expectedTransition.outcomeId().equals(plannedStep.predictedOutcomeId())) {
            throw new IllegalStateException("Exact manifest execution disagrees with the selected planned step");
        }

        int nextIndex = pointer.nextStepIndex() + 1;
        SFMTrajectoryContract.TrajectoryRoute route = routeById(plan, pointer.routeId());
        boolean complete = nextIndex == route.steps().size();
        SFMDecimalNumberingChamber.SupervisionDefinition selectedSupervision = Objects.requireNonNull(
                supervisionDefinitionsByPlan.get(plan.id()),
                "selected plan supervision definition"
        );
        SFMTrajectoryContract.SupervisionContract nextSupervision;
        SFMTrajectoryContract.TrajectoryMachineState nextMachine;
        if (complete) {
            List<SFMDecimalNumberingChamber.Transition> exactRoute = route.steps().stream()
                    .map(step -> manifest.requireStep(step).executableTransition())
                    .toList();
            SFMDecimalNumberingChamber.SupervisionEvidence evidence = chamber.evaluateSupervision(
                    selectedSupervision,
                    requireState(plan.startStateId()),
                    computed,
                    exactRoute,
                    measuredInvariantEvidenceIncluding(exactRoute)
            );
            nextSupervision = selectedSupervision.resolvedContract(evidence);
            nextMachine = new SFMTrajectoryContract.TrajectoryMachineState(
                    historyHeadId,
                    Optional.of(plan.id()),
                    Optional.of(new SFMTrajectoryContract.InstructionPointer(plan.id(), route.id(), nextIndex)),
                    machine.projectionFrontier(),
                    nextSupervision.revision(),
                    evidence.status() == SFMTrajectoryContract.SupervisionStatus.SUPERVISION_READY
                            ? SFMTrajectoryContract.MachineStatus.COMPLETE
                            : SFMTrajectoryContract.MachineStatus.BLOCKED,
                    machine.remainingBudget()
            );
        } else {
            SFMTrajectoryContract.SupervisionContract selectedContract = Objects.requireNonNull(
                    supervisionContractsByPlan.get(plan.id()),
                    "selected plan supervision contract"
            );
            nextSupervision = withStatus(selectedContract, SFMTrajectoryContract.SupervisionStatus.RUNNING);
            nextMachine = new SFMTrajectoryContract.TrajectoryMachineState(
                    historyHeadId,
                    Optional.of(plan.id()),
                    Optional.of(new SFMTrajectoryContract.InstructionPointer(plan.id(), route.id(), nextIndex)),
                    machine.projectionFrontier(),
                    nextSupervision.revision(),
                    SFMTrajectoryContract.MachineStatus.RUNNING,
                    machine.remainingBudget()
            );
        }

        // Stage every failure-prone computation before mutating the aggregate.
        // Applying the staged commit and prevalidated machine values below is
        // the complete monitor-atomic state transition.
        StagedTransitionCommit staged = stageTransitionCommit(
                expectedTransition,
                "trajectory-step:" + plannedStep.id()
        );
        applyTransitionCommit(staged);
        supervisionDefinition = selectedSupervision;
        activeSupervision = nextSupervision;
        supervisionContractsByPlan.put(plan.id(), nextSupervision);
        machine = nextMachine;
        revision++;
        return SFMHistoryGraphRuntime.OperationResult.applied(
                "Executed step " + plannedStep.id() + (complete ? "; supervision is " + activeSupervision.status() : "")
        );
    }

    private SFMHistoryGraphRuntime.OperationResult run(int maxSteps) {
        int applied = 0;
        String last = "No step executed";
        while (applied < maxSteps) {
            SFMHistoryGraphRuntime.OperationResult result = step();
            last = result.message();
            if (result.status() != SFMHistoryGraphRuntime.OperationStatus.APPLIED) break;
            applied++;
            if (machine.status() == SFMTrajectoryContract.MachineStatus.COMPLETE
                    || machine.status() == SFMTrajectoryContract.MachineStatus.STALE_PRECONDITION
                    || machine.status() == SFMTrajectoryContract.MachineStatus.BLOCKED) {
                break;
            }
        }
        return applied == 0
                ? SFMHistoryGraphRuntime.OperationResult.noChange(last)
                : SFMHistoryGraphRuntime.OperationResult.applied(
                        "Executed " + applied + " step(s); " + last
                );
    }

    private SFMHistoryGraphRuntime.OperationResult pause() {
        if (machine.status() == SFMTrajectoryContract.MachineStatus.PAUSED) {
            return SFMHistoryGraphRuntime.OperationResult.noChange("Trajectory machine is already paused");
        }
        machine = new SFMTrajectoryContract.TrajectoryMachineState(
                historyHeadId,
                machine.selectedTrajectoryRevisionId(),
                machine.instructionPointer(),
                machine.projectionFrontier(),
                activeSupervision.revision(),
                SFMTrajectoryContract.MachineStatus.PAUSED,
                machine.remainingBudget()
        );
        revision++;
        return SFMHistoryGraphRuntime.OperationResult.applied("Paused at the current instruction pointer");
    }

    private SFMHistoryGraphRuntime.OperationResult selectRoute(String planRevisionId, String routeId) {
        SFMTrajectoryContract.TrajectoryPlanRevision plan = planById(planRevisionId)
                .orElse(null);
        if (plan == null) return SFMHistoryGraphRuntime.OperationResult.rejected("Unknown retained plan " + planRevisionId);
        SFMTrajectoryContract.TrajectoryRoute route;
        try {
            route = routeById(plan, routeId);
        } catch (IllegalArgumentException missing) {
            return SFMHistoryGraphRuntime.OperationResult.rejected(missing.getMessage());
        }
        Optional<Integer> nextValue = pointerIndexForState(route, currentStateId);
        if (nextValue.isEmpty()) {
            return SFMHistoryGraphRuntime.OperationResult.rejected(
                    "Current document head is not on retained route " + route.id()
                            + "; switch the document branch or Replan"
            );
        }
        int next = nextValue.orElseThrow();
        SFMTrajectoryContract.SupervisionContract selectedSupervision = Objects.requireNonNull(
                supervisionContractsByPlan.get(plan.id()),
                "retained plan supervision contract"
        );
        planBook = new SFMTrajectoryContract.PlanBook(
                SFMTrajectoryContract.SCHEMA,
                planBook.plans(),
                Optional.of(plan.id())
        );
        machine = new SFMTrajectoryContract.TrajectoryMachineState(
                historyHeadId,
                Optional.of(plan.id()),
                Optional.of(new SFMTrajectoryContract.InstructionPointer(plan.id(), route.id(), next)),
                planningBundle(plan.id()).map(bundle -> bundle.result().projectionFrontier()),
                selectedSupervision.revision(),
                next == route.steps().size()
                        ? selectedSupervision.status() == SFMTrajectoryContract.SupervisionStatus.SUPERVISION_READY
                                ? SFMTrajectoryContract.MachineStatus.COMPLETE
                                : SFMTrajectoryContract.MachineStatus.BLOCKED
                        : SFMTrajectoryContract.MachineStatus.READY,
                machine.remainingBudget()
        );
        supervisionDefinition = Objects.requireNonNull(
                supervisionDefinitionsByPlan.get(plan.id()),
                "retained plan supervision definition"
        );
        activeSupervision = selectedSupervision;
        revision++;
        return SFMHistoryGraphRuntime.OperationResult.applied("Selected route " + route.id());
    }

    private SFMHistoryGraphRuntime.OperationResult inspectCost() {
        Optional<SFMTrajectoryContract.TrajectoryPlanRevision> selected = planBook.selectedPlanRevisionId()
                .flatMap(this::planById);
        if (selected.isEmpty() || selected.orElseThrow().selectedRouteId().isEmpty()) {
            return SFMHistoryGraphRuntime.OperationResult.noChange("No selected route cost is available");
        }
        SFMTrajectoryContract.TrajectoryPlanRevision plan = selected.orElseThrow();
        SFMTrajectoryContract.TrajectoryRoute route = selectedRoute(plan);
        return SFMHistoryGraphRuntime.OperationResult.noChange(
                "cost=" + route.totalCost()
                        + " policy=" + plan.costPolicy().id() + "@" + plan.costPolicy().revision()
                        + " algorithm=" + plan.algorithm()
                        + " optimality=" + plan.optimalityClaim()
        );
    }

    private void commitTransition(SFMDecimalNumberingChamber.Transition transition, String evidence) {
        applyTransitionCommit(stageTransitionCommit(transition, evidence));
    }

    private StagedTransitionCommit stageTransitionCommit(
            SFMDecimalNumberingChamber.Transition transition,
            String evidence
    ) {
        Objects.requireNonNull(transition, "transition");
        if (!transition.parent().revisionId().equals(currentStateId)) {
            throw new SFMDecimalNumberingChamber.StaleParentException(
                    "Authoritative head changed before transition commit"
            );
        }
        SFMChamberDocumentState existing = states.get(transition.result().revisionId());
        if (existing != null && !existing.equals(transition.result())) {
            throw new IllegalStateException("Revision identity collision while committing chamber transition");
        }
        String evaluationId = scope.qualify("evaluation", token(
                transition.parent().revisionId(),
                transition.action().intent().id(),
                transition.result().revisionId()
        ));
        String edgeId = scope.qualify("edge", token(
                transition.parent().revisionId(),
                transition.result().revisionId(),
                transition.action().intent().id()
        ));
        boolean alreadyCommitted = committedTransitions.stream().anyMatch(value -> value.edgeId().equals(edgeId));
        String firstHash = firstObservedStateHashes.get(transition.result().revisionId());
        if (firstHash != null && !firstHash.equals(transition.result().stateHash())) {
            throw new IllegalStateException("Retained state was mutated after first observation");
        }
        return new StagedTransitionCommit(
                transition,
                new CommittedTransition(
                        transition,
                        evaluationId,
                        edgeId,
                        requireText(evidence, "commit evidence")
                ),
                alreadyCommitted
        );
    }

    private void applyTransitionCommit(StagedTransitionCommit staged) {
        SFMDecimalNumberingChamber.Transition transition = staged.transition();
        states.putIfAbsent(transition.result().revisionId(), transition.result());
        firstObservedStateHashes.putIfAbsent(transition.result().revisionId(), transition.result().stateHash());
        if (!staged.alreadyCommitted()) committedTransitions.add(staged.committed());
        currentStateId = transition.result().revisionId();
    }

    private SFMHistoryGraphContract.Graph historyGraph() {
        LinkedHashMap<String, SFMHistoryGraphContract.ActionIntent> intents = new LinkedHashMap<>();
        ArrayList<SFMHistoryGraphContract.ActionEvaluation> evaluations = new ArrayList<>();
        ArrayList<SFMHistoryGraphContract.ActionOutcome> outcomes = new ArrayList<>();
        ArrayList<SFMHistoryGraphContract.BranchEdge> edges = new ArrayList<>();
        for (CommittedTransition committed : committedTransitions) {
            SFMDecimalNumberingChamber.Transition transition = committed.transition();
            intents.putIfAbsent(transition.action().intent().id(), transition.action().intent());
            List<SFMHistoryGraphContract.DependencyWitness> witnesses = dependencyWitnesses(transition);
            evaluations.add(new SFMHistoryGraphContract.ActionEvaluation(
                    committed.evaluationId(),
                    transition.action().intent().id(),
                    transition.parent().revisionId(),
                    SFMDecimalNumberingChamber.ACTION_GENERATOR_REVISION,
                    transition.action().evaluationPolicy(),
                    witnesses,
                    transition.outcomeId(),
                    SFMHistoryGraphContract.ProjectionStatus.MATERIALIZED
            ));
            outcomes.add(new SFMHistoryGraphContract.ActionOutcome(
                    transition.outcomeId(),
                    committed.evaluationId(),
                    SFMHistoryGraphContract.OutcomeStatus.SUCCEEDED,
                    Optional.of(transition.result().revisionId()),
                    List.of(committed.evidence(), "result-state-hash=" + transition.result().stateHash())
            ));
            edges.add(new SFMHistoryGraphContract.BranchEdge(
                    committed.edgeId(),
                    transition.parent().revisionId(),
                    transition.result().revisionId(),
                    Optional.of(transition.action().intent().id()),
                    Optional.of(committed.evaluationId()),
                    Optional.of(transition.outcomeId()),
                    transition.action().effectClass(),
                    SFMHistoryGraphContract.ProjectionStatus.MATERIALIZED,
                    true
            ));
        }
        List<SFMHistoryGraphContract.StateRevision> projectedStates = states.values().stream()
                .map(state -> new SFMHistoryGraphContract.StateRevision(
                        state.revisionId(),
                        state.parentRevisionId().stream().toList(),
                        state.stateHash(),
                        true,
                        SFMHistoryGraphContract.ProjectionStatus.MATERIALIZED
                ))
                .toList();
        return new SFMHistoryGraphContract.Graph(
                SFMHistoryGraphContract.SCHEMA,
                List.copyOf(intents.values()),
                evaluations,
                outcomes,
                projectedStates,
                List.of(new SFMHistoryGraphContract.HistoryHead(
                        historyHeadId,
                        new SFMHistoryGraphContract.UndoDomain(
                                SFMHistoryGraphContract.UndoDomainKind.DOCUMENT,
                                scope.episodeId() + "/document/" + scope.documentId()
                        ),
                        currentStateId,
                        Optional.of("current")
                )),
                headMovements,
                edges,
                retentionPins
        );
    }

    private static List<SFMHistoryGraphContract.DependencyWitness> dependencyWitnesses(
            SFMDecimalNumberingChamber.Transition transition
    ) {
        ArrayList<SFMHistoryGraphContract.DependencyWitness> witnesses = new ArrayList<>();
        witnesses.add(new SFMHistoryGraphContract.DependencyWitness(
                "parent-state",
                transition.parent().revisionId(),
                transition.parent().stateHash()
        ));
        if (transition.action() instanceof SFMDecimalNumberingChamber.SelectAllHyphenMarkersAction select) {
            witnesses.add(new SFMHistoryGraphContract.DependencyWitness(
                    "selection-witness",
                    select.witness().evaluatorId(),
                    select.witness().witnessHash()
            ));
        } else if (transition.action() instanceof SFMDecimalNumberingChamber.ReplaceOrderedWitnessAction replace) {
            witnesses.add(new SFMHistoryGraphContract.DependencyWitness(
                    "selection-witness",
                    replace.witness().evaluatorId(),
                    replace.witness().witnessHash()
            ));
        }
        return List.copyOf(witnesses);
    }

    private SFMDecimalNumberingChamber.ExternalInvariantEvidence measuredInvariantEvidence() {
        LinkedHashMap<String, String> currentHashes = new LinkedHashMap<>();
        states.forEach((id, state) -> currentHashes.put(id, state.stateHash()));
        return new SFMDecimalNumberingChamber.ExternalInvariantEvidence(
                ambientCheckoutBaseline,
                measuredAmbientHash(),
                firstObservedStateHashes,
                currentHashes
        );
    }

    /** Post-commit invariant view staged without publishing candidate states. */
    private SFMDecimalNumberingChamber.ExternalInvariantEvidence measuredInvariantEvidenceIncluding(
            List<SFMDecimalNumberingChamber.Transition> exactRoute
    ) {
        Objects.requireNonNull(exactRoute, "exactRoute");
        LinkedHashMap<String, String> firstHashes = new LinkedHashMap<>(firstObservedStateHashes);
        LinkedHashMap<String, String> currentHashes = new LinkedHashMap<>();
        states.forEach((id, state) -> currentHashes.put(id, state.stateHash()));
        for (SFMDecimalNumberingChamber.Transition transition : exactRoute) {
            for (SFMChamberDocumentState state : List.of(transition.parent(), transition.result())) {
                String previousFirst = firstHashes.putIfAbsent(state.revisionId(), state.stateHash());
                if (previousFirst != null && !previousFirst.equals(state.stateHash())) {
                    throw new IllegalStateException("Exact route changed a retained state's first-observed hash");
                }
                String previousCurrent = currentHashes.putIfAbsent(state.revisionId(), state.stateHash());
                if (previousCurrent != null && !previousCurrent.equals(state.stateHash())) {
                    throw new IllegalStateException("Exact route changed a retained state's current hash");
                }
            }
        }
        return new SFMDecimalNumberingChamber.ExternalInvariantEvidence(
                ambientCheckoutBaseline,
                measuredAmbientHash(),
                firstHashes,
                currentHashes
        );
    }

    private String measuredAmbientHash() {
        return requireText(ambientCheckoutProbe.get(), "ambient checkout measurement");
    }

    private void retainState(SFMChamberDocumentState state) {
        Objects.requireNonNull(state, "state");
        if (!scope.equals(state.scope())) throw new IllegalArgumentException("State belongs to another chamber scope");
        SFMChamberDocumentState previous = states.putIfAbsent(state.revisionId(), state);
        if (previous != null && !previous.equals(state)) {
            throw new IllegalStateException("State revision identity collision");
        }
        String previousHash = firstObservedStateHashes.putIfAbsent(state.revisionId(), state.stateHash());
        if (previousHash != null && !previousHash.equals(state.stateHash())) {
            throw new IllegalStateException("Retained state was mutated after first observation");
        }
    }

    private void resetSupervisionFor(SFMChamberDocumentState state) {
        SFMDecimalNumberingChamber.Target target = chamber.requireTarget(state);
        supervisionDefinition = chamber.supervision(state, target);
        activeSupervision = supervisionDefinition.contract();
    }

    private void refreshSupervisionAfterManualEdit() {
        SFMDecimalNumberingChamber.TargetDerivation derivation = chamber.deriveTarget(currentState());
        if (derivation.target().isPresent()) {
            resetSupervisionFor(currentState());
        } else {
            activeSupervision = withStatus(activeSupervision, SFMTrajectoryContract.SupervisionStatus.BLOCKED);
        }
    }

    private Optional<SFMTrajectoryContract.InstructionPointer> pointerForSelectedPlan(String stateId) {
        return machine.selectedTrajectoryRevisionId().flatMap(this::planById).flatMap(plan ->
                plan.selectedRouteId().flatMap(routeId -> {
                    SFMTrajectoryContract.TrajectoryRoute route = routeById(plan, routeId);
                    return pointerIndexForState(route, stateId).map(index ->
                            new SFMTrajectoryContract.InstructionPointer(plan.id(), route.id(), index));
                }));
    }

    private static Optional<Integer> pointerIndexForState(
            SFMTrajectoryContract.TrajectoryRoute route,
            String stateId
    ) {
        if (route.startStateId().equals(stateId)) return Optional.of(0);
        for (int index = 0; index < route.steps().size(); index++) {
            if (route.steps().get(index).predictedStateId().equals(stateId)) return Optional.of(index + 1);
        }
        return Optional.empty();
    }

    private Optional<SFMTrajectoryContract.TrajectoryPlanRevision> planById(String id) {
        return planBook.plans().stream().filter(plan -> plan.id().equals(id)).findFirst();
    }

    private static SFMTrajectoryContract.TrajectoryRoute routeById(
            SFMTrajectoryContract.TrajectoryPlanRevision plan,
            String routeId
    ) {
        return plan.routes().stream().filter(route -> route.id().equals(routeId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown route " + routeId + " in " + plan.id()));
    }

    private static SFMTrajectoryContract.TrajectoryRoute selectedRoute(
            SFMTrajectoryContract.TrajectoryPlanRevision plan
    ) {
        return routeById(plan, plan.selectedRouteId().orElseThrow());
    }

    private SFMChamberDocumentState requireState(String id) {
        SFMChamberDocumentState state = states.get(id);
        if (state == null) throw new IllegalStateException("Unknown retained chamber state " + id);
        return state;
    }

    private String summary() {
        String preview = currentText().replace("\n", " / ").strip();
        return "Temporal numbering chamber " + scope.documentId()
                + "; status=" + machine.status()
                + "; plans=" + planBook.plans().size()
                + "; head=" + currentStateId
                + "; document=" + preview;
    }

    private static SFMTrajectoryContract.RemainingBudget fullRemainingBudget(
            SFMTrajectoryContract.SupervisionContract supervision
    ) {
        return new SFMTrajectoryContract.RemainingBudget(
                supervision.searchBudget().maxExpanded(),
                supervision.searchBudget().maxGenerated(),
                supervision.searchBudget().maxElapsedMillis()
        );
    }

    private static SFMTrajectoryContract.RemainingBudget remainingBudget(
            SFMTrajectoryContract.SupervisionContract supervision,
            SFMBoundedTrajectoryPlanner.PlanningResult<?> result
    ) {
        return new SFMTrajectoryContract.RemainingBudget(
                Math.max(0, supervision.searchBudget().maxExpanded() - result.expanded()),
                Math.max(0, supervision.searchBudget().maxGenerated() - result.generated()),
                Math.max(0, supervision.searchBudget().maxElapsedMillis() - result.elapsedMillis())
        );
    }

    private static SFMTrajectoryContract.SupervisionContract withStatus(
            SFMTrajectoryContract.SupervisionContract contract,
            SFMTrajectoryContract.SupervisionStatus status
    ) {
        return new SFMTrajectoryContract.SupervisionContract(
                contract.id(),
                contract.revision(),
                contract.startDomainId(),
                contract.startStateRevisionId(),
                contract.goalPredicates(),
                contract.hardInvariants(),
                contract.forbiddenEffectClasses(),
                contract.requiredEvidence(),
                contract.searchBudget(),
                contract.maxExecutionSteps(),
                contract.approvalRequirement(),
                status,
                Optional.empty()
        );
    }

    private static SFMTrajectoryContract.TrajectoryMachineState withSupervision(
            SFMTrajectoryContract.TrajectoryMachineState machine,
            SFMTrajectoryContract.SupervisionContract supervision,
            SFMTrajectoryContract.MachineStatus status
    ) {
        return new SFMTrajectoryContract.TrajectoryMachineState(
                machine.actualHistoryHeadId(),
                machine.selectedTrajectoryRevisionId(),
                machine.instructionPointer(),
                machine.projectionFrontier(),
                supervision.revision(),
                status,
                machine.remainingBudget()
        );
    }

    private static String token(String... values) {
        return SFMChamberDocumentState.fingerprint(values).substring("sha256:".length());
    }

    private static String requireText(String value, String label) {
        value = SFMChamberDocumentState.requireWellFormedUtf16(value, label);
        if (value.isEmpty()) throw new IllegalArgumentException(label + " must not be empty");
        return value;
    }

    private record CommittedTransition(
            SFMDecimalNumberingChamber.Transition transition,
            String evaluationId,
            String edgeId,
            String evidence
    ) {
        private CommittedTransition {
            Objects.requireNonNull(transition, "transition");
            evaluationId = requireText(evaluationId, "evaluationId");
            edgeId = requireText(edgeId, "edgeId");
            evidence = requireText(evidence, "evidence");
        }
    }

    private record StagedTransitionCommit(
            SFMDecimalNumberingChamber.Transition transition,
            CommittedTransition committed,
            boolean alreadyCommitted
    ) {
        private StagedTransitionCommit {
            Objects.requireNonNull(transition, "transition");
            Objects.requireNonNull(committed, "committed");
            if (!transition.equals(committed.transition())) {
                throw new IllegalArgumentException("Staged transition and committed evidence disagree");
            }
        }
    }

    public record ReplanLineage(
            String id,
            String sourcePlanRevisionId,
            String resultingPlanRevisionId,
            String authoritativeStartStateId,
            String reason
    ) {
        public ReplanLineage {
            id = requireText(id, "replanLineage.id");
            sourcePlanRevisionId = requireText(sourcePlanRevisionId, "replanLineage.sourcePlanRevisionId");
            resultingPlanRevisionId = requireText(resultingPlanRevisionId, "replanLineage.resultingPlanRevisionId");
            authoritativeStartStateId = requireText(authoritativeStartStateId, "replanLineage.authoritativeStartStateId");
            reason = requireText(reason, "replanLineage.reason");
        }
    }

    public record CheckoutEvidence(
            String schema,
            String beforeHash,
            String afterHash,
            boolean unchanged
    ) {
        public CheckoutEvidence {
            schema = requireText(schema, "checkoutEvidence.schema");
            beforeHash = requireText(beforeHash, "checkoutEvidence.beforeHash");
            afterHash = requireText(afterHash, "checkoutEvidence.afterHash");
            if (unchanged != beforeHash.equals(afterHash)) {
                throw new IllegalArgumentException("Checkout evidence status does not match its measured hashes");
            }
        }
    }
}
