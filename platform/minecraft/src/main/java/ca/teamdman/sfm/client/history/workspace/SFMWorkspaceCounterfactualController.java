package ca.teamdman.sfm.client.history.workspace;

import ca.teamdman.sfm.client.history.SFMHistoryGraphContract;
import ca.teamdman.sfm.client.history.SFMHistoryGraphRuntime;
import ca.teamdman.sfm.client.history.SFMTrajectoryContract;
import ca.teamdman.sfm.client.history.workspace.SFMWorkspaceCounterfactualContract.Artifact;
import ca.teamdman.sfm.client.history.workspace.SFMWorkspaceCounterfactualContract.Barrier;
import ca.teamdman.sfm.client.history.workspace.SFMWorkspaceCounterfactualContract.HeadMovement;
import ca.teamdman.sfm.client.history.workspace.SFMWorkspaceCounterfactualContract.NarrativeKind;
import ca.teamdman.sfm.client.history.workspace.SFMWorkspaceCounterfactualContract.Operation;
import ca.teamdman.sfm.client.history.workspace.SFMWorkspaceCounterfactualContract.WorkspaceDocument;
import ca.teamdman.sfm.client.history.workspace.SFMWorkspaceCounterfactualContract.WorkspaceFrame;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Authoritative bounded X5 workspace episode.
 *
 * <p>The fixture deliberately models complete workspace values rather than
 * mutating the checkout. Every operation is staged as immutable values and is
 * published only after the versioned artifact validates.</p>
 */
public final class SFMWorkspaceCounterfactualController implements SFMHistoryGraphRuntime.Controller {
    public static final String A_PATH = "A.java";
    public static final String B_PATH = "B.java";
    public static final String INITIAL_A = "package fixture;\n\npublic final class A {\n    public static final String VALUE = \"A\";\n}\n";
    public static final String INITIAL_B = "package fixture;\n\npublic final class B {\n    public static final String VALUE = \"B\";\n}\n";
    public static final String EDIT_SUFFIX = "// reviewed through a compatible restorable suffix\n";
    public static final String EXPLORER_LOCATION = "workspace-counterfactual://documents/";

    public static final String TITLE = "state:title-screen";
    public static final String PALETTE = "state:command-palette";
    public static final String BEFORE_SELECTION = "state:explorer-before-selection";
    public static final String A_SELECTED = "state:recorded-a-selected";
    public static final String A_OPEN = "state:recorded-a-open";
    public static final String A_EDITED = "state:recorded-a-edited";
    public static final String B_SELECTED = "state:counterfactual-b-selected";
    public static final String FROZEN_A_OPEN = "state:frozen-witness-a-open";
    public static final String FROZEN_A_EDITED = "state:frozen-witness-a-edited";
    public static final String REEVALUATED_B_OPEN = "state:reevaluated-intent-b-open";
    public static final String REEVALUATED_B_EDITED = "state:reevaluated-intent-b-edited";

    public static final String HEAD_ID = "head:workspace";
    public static final String EXTERNAL_ACTION_ID = "sfm:fixture/external/process-launch";

    private final String episodeId;
    private final Supplier<String> ambientCheckoutProbe;
    private final String ambientBeforeHash;
    private final LinkedHashMap<String, WorkspaceFrame> frames = new LinkedHashMap<>();
    private final ArrayList<Operation> operations = new ArrayList<>();
    private final ArrayList<HeadMovement> headMovements = new ArrayList<>();
    private final ArrayList<Barrier> barriers = new ArrayList<>();
    private String currentStateId;
    private long generation;
    private long movementSequence;

    public SFMWorkspaceCounterfactualController(
            String episodeId,
            Supplier<String> ambientCheckoutProbe
    ) {
        this.episodeId = requireText(episodeId, "episodeId");
        this.ambientCheckoutProbe = Objects.requireNonNull(ambientCheckoutProbe, "ambientCheckoutProbe");
        ambientBeforeHash = requireHash(ambientCheckoutProbe.get(), "ambient checkout baseline");
        initializeNaturalEntry();
    }

    private SFMWorkspaceCounterfactualController(
            Artifact artifact,
            Supplier<String> ambientCheckoutProbe
    ) {
        Objects.requireNonNull(artifact, "artifact");
        episodeId = artifact.episodeId();
        this.ambientCheckoutProbe = Objects.requireNonNull(ambientCheckoutProbe, "ambientCheckoutProbe");
        ambientBeforeHash = artifact.ambientBeforeHash();
        String currentAmbient = requireHash(ambientCheckoutProbe.get(), "restored ambient checkout");
        if (!artifact.ambientAfterHash().equals(currentAmbient)) {
            throw new IllegalArgumentException("Ambient checkout changed before workspace artifact restore");
        }
        artifact.workspaceFrames().forEach(frame -> frames.put(frame.id(), frame));
        operations.addAll(artifact.operations());
        headMovements.addAll(artifact.headMovements());
        barriers.addAll(artifact.barriers());
        currentStateId = artifact.currentStateId();
        generation = artifact.generation();
        movementSequence = headMovements.size();
        // Reconstructing the public artifact proves that all references and
        // hashes still close before this controller becomes observable.
        artifact();
    }

    public static SFMWorkspaceCounterfactualController restore(
            Artifact artifact,
            Supplier<String> ambientCheckoutProbe
    ) {
        return new SFMWorkspaceCounterfactualController(artifact, ambientCheckoutProbe);
    }

    private void initializeNaturalEntry() {
        List<WorkspaceDocument> documents = initialDocuments();
        retain(frame(TITLE, Optional.empty(), "layout:title", "panel:title",
                Optional.empty(), Optional.empty(), documents));
        commitOperation(
                "operation:open-command-palette",
                "sfm:palette/open",
                TITLE,
                frame(PALETTE, Optional.of(TITLE), "layout:palette-over-title", "panel:palette",
                        Optional.empty(), Optional.empty(), documents),
                SFMHistoryGraphContract.EvaluationPolicy.INTENT_REEVALUATION,
                SFMHistoryGraphContract.EffectClass.SNAPSHOT_RESTORABLE,
                "Open the normal command palette from the title screen",
                List.of(),
                List.of(witness("workspace-frame", TITLE, requireFrame(TITLE).stateHash())),
                List.of(), List.of(), NarrativeKind.RECORDED,
                List.of("ordinary title-screen command palette"));
        commitOperation(
                "operation:open-workspace-explorer",
                "sfm:panel/open",
                PALETTE,
                frame(BEFORE_SELECTION, Optional.of(PALETTE), "layout:explorer-left-history-right",
                        "panel:explorer", Optional.empty(), Optional.empty(), documents),
                SFMHistoryGraphContract.EvaluationPolicy.INTENT_REEVALUATION,
                SFMHistoryGraphContract.EffectClass.SNAPSHOT_RESTORABLE,
                "sfm action invoke sfm:panel/open sfm:chamber/workspace-counterfactual",
                List.of("scene=sfm:chamber/workspace-counterfactual"),
                List.of(witness("workspace-frame", PALETTE, requireFrame(PALETTE).stateHash())),
                List.of(), List.of(), NarrativeKind.RECORDED,
                List.of("ordinary explorer and History Graph opened"));
    }

    @Override
    public synchronized String machineId() {
        return episodeId;
    }

    public synchronized long revision() {
        return generation;
    }

    public synchronized WorkspaceFrame currentFrame() {
        return requireFrame(currentStateId);
    }

    public synchronized WorkspaceFrame requireFrame(String id) {
        WorkspaceFrame frame = frames.get(Objects.requireNonNull(id, "id"));
        if (frame == null) throw new IllegalArgumentException("Unknown workspace frame " + id);
        return frame;
    }

    public synchronized String documentText(String logicalPath) {
        return document(requireFrame(currentStateId), logicalPath).text();
    }

    public synchronized Optional<String> selectedPath() {
        return currentFrame().selectedPath();
    }

    public synchronized Artifact artifact() {
        String ambientAfter = requireHash(ambientCheckoutProbe.get(), "ambient checkout after");
        return Artifact.create(
                episodeId,
                generation,
                currentStateId,
                ambientBeforeHash,
                ambientAfter,
                List.copyOf(frames.values()),
                operations,
                headMovements,
                barriers
        );
    }

    /** Records an ordinary explorer selection through the registered action seam. */
    public synchronized SFMHistoryGraphRuntime.OperationResult selectDocument(String logicalPath) {
        requireDocumentPath(logicalPath);
        WorkspaceFrame parent = currentFrame();
        if (parent.selectedPath().filter(logicalPath::equals).isPresent()) {
            return SFMHistoryGraphRuntime.OperationResult.noChange(logicalPath + " is already selected");
        }
        String stateId;
        NarrativeKind narrative;
        if (logicalPath.equals(A_PATH) && parent.id().equals(BEFORE_SELECTION)) {
            stateId = A_SELECTED;
            narrative = NarrativeKind.RECORDED;
        } else if (logicalPath.equals(B_PATH) && parent.id().equals(BEFORE_SELECTION)) {
            stateId = B_SELECTED;
            narrative = NarrativeKind.REEVALUATED_INTENT;
        } else {
            return SFMHistoryGraphRuntime.OperationResult.rejected(
                    "Selection requires the retained before-selection workspace frame");
        }
        WorkspaceFrame result = frame(
                stateId,
                Optional.of(parent.id()),
                "layout:explorer-left-history-right",
                "panel:explorer",
                Optional.of(logicalPath),
                Optional.empty(),
                parent.documents()
        );
        commitOperation(
                "operation:select-" + logicalPath.substring(0, 1).toLowerCase(java.util.Locale.ROOT),
                "sfm:episode/workspace-counterfactual/selection/set",
                parent.id(), result,
                SFMHistoryGraphContract.EvaluationPolicy.INTENT_REEVALUATION,
                SFMHistoryGraphContract.EffectClass.SNAPSHOT_RESTORABLE,
                "Select " + logicalPath + " in the ordinary explorer",
                List.of("path=" + logicalPath),
                List.of(witness("explorer-location", EXPLORER_LOCATION, parent.stateHash())),
                List.of(), List.of(), narrative,
                List.of("selection witness captured before document opening"));
        return SFMHistoryGraphRuntime.OperationResult.applied("Selected " + logicalPath);
    }

    /** Opens the selected in-memory document and returns its complete retained frame. */
    public synchronized SFMHistoryGraphRuntime.OperationResult openSelectedDocument(String logicalPath) {
        requireDocumentPath(logicalPath);
        if (currentFrame().selectedPath().filter(logicalPath::equals).isEmpty()) {
            SFMHistoryGraphRuntime.OperationResult selection = selectDocument(logicalPath);
            if (selection.status() == SFMHistoryGraphRuntime.OperationStatus.REJECTED) return selection;
        }
        WorkspaceFrame parent = currentFrame();
        if (parent.openDocumentPath().filter(logicalPath::equals).isPresent()) {
            return SFMHistoryGraphRuntime.OperationResult.noChange(logicalPath + " is already open");
        }
        String stateId = logicalPath.equals(A_PATH) && parent.id().equals(A_SELECTED)
                ? A_OPEN
                : logicalPath.equals(B_PATH) && parent.id().equals(B_SELECTED)
                        ? REEVALUATED_B_OPEN
                        : null;
        if (stateId == null) {
            return SFMHistoryGraphRuntime.OperationResult.rejected(
                    "Open-selected requires a retained A or B selection frame");
        }
        NarrativeKind narrative = logicalPath.equals(A_PATH)
                ? NarrativeKind.RECORDED
                : NarrativeKind.REEVALUATED_INTENT;
        WorkspaceFrame result = frame(
                stateId,
                Optional.of(parent.id()),
                "layout:editor-left-history-right",
                "panel:text-editor-v3",
                Optional.of(logicalPath),
                Optional.of(logicalPath),
                parent.documents()
        );
        commitOperation(
                "operation:open-selected-" + logicalPath.substring(0, 1).toLowerCase(java.util.Locale.ROOT),
                "sfm:path/open",
                parent.id(), result,
                SFMHistoryGraphContract.EvaluationPolicy.INTENT_REEVALUATION,
                SFMHistoryGraphContract.EffectClass.SNAPSHOT_RESTORABLE,
                "Open the document named by the current explorer selection",
                List.of("selected=" + logicalPath, "editor=sfm:text_editor_v3"),
                List.of(
                        witness("explorer-selection", logicalPath, parent.stateHash()),
                        witness("document-bytes", logicalPath, document(parent, logicalPath).textHash())
                ),
                List.of("sfm:fixture/document/edit"),
                List.of(EXTERNAL_ACTION_ID),
                narrative,
                List.of("dynamic open-selected intent evaluated against current selection"));
        return SFMHistoryGraphRuntime.OperationResult.applied("Opened " + logicalPath);
    }

    /** Commits one complete editor value against an exact retained parent. */
    public synchronized SFMHistoryGraphRuntime.OperationResult acceptEditorText(
            String expectedParentStateId,
            String logicalPath,
            String text
    ) {
        Objects.requireNonNull(text, "text");
        WorkspaceFrame parent = currentFrame();
        if (!parent.id().equals(expectedParentStateId)) {
            return SFMHistoryGraphRuntime.OperationResult.rejected(
                    "Stale editor parent " + expectedParentStateId + "; current frame is " + parent.id());
        }
        if (parent.openDocumentPath().filter(logicalPath::equals).isEmpty()) {
            return SFMHistoryGraphRuntime.OperationResult.rejected("The addressed document is not open");
        }
        if (document(parent, logicalPath).text().equals(text)) {
            return SFMHistoryGraphRuntime.OperationResult.noChange("Document bytes are unchanged");
        }
        String stateId;
        NarrativeKind narrative;
        SFMHistoryGraphContract.EvaluationPolicy policy;
        if (parent.id().equals(A_OPEN) && logicalPath.equals(A_PATH)) {
            stateId = A_EDITED;
            narrative = NarrativeKind.RECORDED;
            policy = SFMHistoryGraphContract.EvaluationPolicy.INTENT_REEVALUATION;
        } else if (parent.id().equals(FROZEN_A_OPEN) && logicalPath.equals(A_PATH)) {
            stateId = FROZEN_A_EDITED;
            narrative = NarrativeKind.FROZEN_WITNESS;
            policy = SFMHistoryGraphContract.EvaluationPolicy.FROZEN_WITNESS_REEXECUTION;
        } else if (parent.id().equals(REEVALUATED_B_OPEN) && logicalPath.equals(B_PATH)) {
            stateId = REEVALUATED_B_EDITED;
            narrative = NarrativeKind.REEVALUATED_INTENT;
            policy = SFMHistoryGraphContract.EvaluationPolicy.INTENT_REEVALUATION;
        } else {
            return SFMHistoryGraphRuntime.OperationResult.rejected(
                    "This bounded fixture does not accept an edit from " + parent.id());
        }
        WorkspaceFrame result = frame(
                stateId,
                Optional.of(parent.id()),
                parent.panelLayoutId(),
                parent.focusedPanelId(),
                parent.selectedPath(),
                parent.openDocumentPath(),
                replaceDocument(parent.documents(), logicalPath, text)
        );
        commitOperation(
                "operation:edit-" + stateId.substring("state:".length()),
                "sfm:fixture/document/edit",
                parent.id(), result,
                policy,
                SFMHistoryGraphContract.EffectClass.SNAPSHOT_RESTORABLE,
                "Commit complete Text Editor v3 bytes for " + logicalPath,
                List.of("path=" + logicalPath, "text-hash=" + document(result, logicalPath).textHash()),
                List.of(
                        witness("parent-workspace", parent.id(), parent.stateHash()),
                        witness("document-before", logicalPath, document(parent, logicalPath).textHash())
                ),
                List.of("sfm:fixture/document/edit"),
                List.of(EXTERNAL_ACTION_ID),
                narrative,
                List.of("complete editable in-memory document value retained"));
        return SFMHistoryGraphRuntime.OperationResult.applied("Saved in-memory " + logicalPath);
    }

    public synchronized SFMHistoryGraphRuntime.OperationResult forkBeforeSelection() {
        if (!frames.containsKey(A_EDITED)) {
            return SFMHistoryGraphRuntime.OperationResult.rejected("Record and edit the A.java journey first");
        }
        moveHead(
                SFMHistoryGraphContract.HeadMovementKind.UNDO,
                "Fork before explorer selection; retain recorded A branch",
                currentStateId,
                BEFORE_SELECTION,
                NarrativeKind.REEVALUATED_INTENT
        );
        return SFMHistoryGraphRuntime.OperationResult.applied(
                "Forked before selection; the recorded A branch remains retained");
    }

    /** Moves to the already-recorded A result without creating an evaluation or operation. */
    public synchronized SFMHistoryGraphRuntime.OperationResult checkoutRecordedA() {
        if (!frames.containsKey(B_SELECTED) || !frames.containsKey(A_EDITED)) {
            return SFMHistoryGraphRuntime.OperationResult.rejected(
                    "A recorded result and the counterfactual B selection are required");
        }
        moveHead(
                SFMHistoryGraphContract.HeadMovementKind.CHECKOUT,
                "Recorded checkout: reuse A result without execution",
                currentStateId,
                A_EDITED,
                NarrativeKind.RECORDED_CHECKOUT
        );
        return SFMHistoryGraphRuntime.OperationResult.applied(
                "Checked out recorded A result without re-executing any action");
    }

    /** Re-executes the suffix against B's parent while retaining the exact recorded A witness. */
    public synchronized SFMHistoryGraphRuntime.OperationResult replayFrozenAWitness() {
        if (!frames.containsKey(B_SELECTED) || !frames.containsKey(A_EDITED)) {
            return SFMHistoryGraphRuntime.OperationResult.rejected(
                    "A recorded result and the counterfactual B selection are required");
        }
        returnToBParent("Return to B parent before frozen-witness replay", NarrativeKind.FROZEN_WITNESS);
        WorkspaceFrame parent = requireFrame(B_SELECTED);
        WorkspaceFrame opened = frame(
                FROZEN_A_OPEN,
                Optional.of(parent.id()),
                "layout:editor-left-history-right",
                "panel:text-editor-v3",
                parent.selectedPath(),
                Optional.of(A_PATH),
                parent.documents()
        );
        commitOperation(
                "operation:frozen-open-a",
                "sfm:path/open",
                parent.id(), opened,
                SFMHistoryGraphContract.EvaluationPolicy.FROZEN_WITNESS_REEXECUTION,
                SFMHistoryGraphContract.EffectClass.SNAPSHOT_RESTORABLE,
                "Re-execute open-selected using the frozen A.java selection witness",
                List.of("recorded-selection=A.java", "current-selection=B.java"),
                List.of(
                        witness("frozen-explorer-selection", A_PATH, requireFrame(A_SELECTED).stateHash()),
                        witness("current-parent", B_PATH, parent.stateHash())
                ),
                List.of("sfm:fixture/document/edit"),
                List.of(EXTERNAL_ACTION_ID),
                NarrativeKind.FROZEN_WITNESS,
                List.of("frozen witness targets A.java even though B.java is selected"));
        String recordedA = document(requireFrame(A_EDITED), A_PATH).text();
        return acceptEditorText(FROZEN_A_OPEN, A_PATH, recordedA);
    }

    /** Re-evaluates the dynamic intent against B and stops at the declared external barrier. */
    public synchronized SFMHistoryGraphRuntime.OperationResult reevaluateIntentOnB() {
        if (!frames.containsKey(B_SELECTED) || !frames.containsKey(A_EDITED)) {
            return SFMHistoryGraphRuntime.OperationResult.rejected(
                    "A recorded result and the counterfactual B selection are required");
        }
        returnToBParent("Return to B parent before intent re-evaluation", NarrativeKind.REEVALUATED_INTENT);
        WorkspaceFrame parent = requireFrame(B_SELECTED);
        WorkspaceFrame opened = frame(
                REEVALUATED_B_OPEN,
                Optional.of(parent.id()),
                "layout:editor-left-history-right",
                "panel:text-editor-v3",
                Optional.of(B_PATH),
                Optional.of(B_PATH),
                parent.documents()
        );
        commitOperation(
                "operation:reevaluated-open-b",
                "sfm:path/open",
                parent.id(), opened,
                SFMHistoryGraphContract.EvaluationPolicy.INTENT_REEVALUATION,
                SFMHistoryGraphContract.EffectClass.SNAPSHOT_RESTORABLE,
                "Re-evaluate open-selected against the changed B.java selection",
                List.of("selected=B.java", "editor=sfm:text_editor_v3"),
                List.of(
                        witness("reevaluated-explorer-selection", B_PATH, parent.stateHash()),
                        witness("document-bytes", B_PATH, document(parent, B_PATH).textHash())
                ),
                List.of("sfm:fixture/document/edit"),
                List.of(EXTERNAL_ACTION_ID),
                NarrativeKind.REEVALUATED_INTENT,
                List.of("dynamic intent obtained a new B.java witness"));

        String recordedA = document(requireFrame(A_EDITED), A_PATH).text();
        if (!recordedA.startsWith(INITIAL_A)) {
            return SFMHistoryGraphRuntime.OperationResult.rejected(
                    "The recorded edit is not a compatible append-only suffix");
        }
        String compatibleSuffix = recordedA.substring(INITIAL_A.length());
        SFMHistoryGraphRuntime.OperationResult edit = acceptEditorText(
                REEVALUATED_B_OPEN,
                B_PATH,
                INITIAL_B + compatibleSuffix
        );
        if (edit.status() == SFMHistoryGraphRuntime.OperationStatus.REJECTED) return edit;
        commitExternalBarrier();
        return SFMHistoryGraphRuntime.OperationResult.applied(
                "Re-evaluated B, applied the compatible edit, and stopped before the external process action");
    }

    private void commitExternalBarrier() {
        WorkspaceFrame parent = requireFrame(REEVALUATED_B_EDITED);
        String operationId = "operation:external-process-barrier";
        String barrierId = "barrier:external-process-launch";
        if (operations.stream().anyMatch(operation -> operation.id().equals(operationId))) {
            currentStateId = parent.id();
            return;
        }
        Operation operation = new Operation(
                operationId,
                EXTERNAL_ACTION_ID,
                parent.id(),
                Optional.empty(),
                SFMHistoryGraphContract.EvaluationPolicy.INTENT_REEVALUATION,
                SFMHistoryGraphContract.EffectClass.EXTERNAL_IRREVERSIBLE,
                SFMHistoryGraphContract.OutcomeStatus.EXTERNAL_BARRIER,
                "Launch an external process after the restorable edit suffix",
                List.of("command=fixture-external-process"),
                List.of(witness("parent-workspace", parent.id(), parent.stateHash())),
                List.of(),
                List.of(EXTERNAL_ACTION_ID),
                NarrativeKind.REEVALUATED_INTENT,
                List.of("unapplied: external process effects are outside the restorable fixture")
        );
        Barrier barrier = new Barrier(
                barrierId,
                parent.id(),
                EXTERNAL_ACTION_ID,
                SFMHistoryGraphContract.EffectClass.EXTERNAL_IRREVERSIBLE,
                SFMHistoryGraphContract.OutcomeStatus.EXTERNAL_BARRIER,
                "Stopped before external process launch; no result state or partial effect was published"
        );
        ArrayList<Operation> candidateOperations = new ArrayList<>(operations);
        candidateOperations.add(operation);
        ArrayList<Barrier> candidateBarriers = new ArrayList<>(barriers);
        candidateBarriers.add(barrier);
        validateCandidate(frames.values(), candidateOperations, headMovements, candidateBarriers, parent.id(), generation + 1);
        operations.add(operation);
        barriers.add(barrier);
        generation++;
        currentStateId = parent.id();
    }

    private void returnToBParent(String label, NarrativeKind narrativeKind) {
        if (currentStateId.equals(B_SELECTED)) return;
        moveHead(
                SFMHistoryGraphContract.HeadMovementKind.CHECKOUT,
                label,
                currentStateId,
                B_SELECTED,
                narrativeKind
        );
    }

    private void moveHead(
            SFMHistoryGraphContract.HeadMovementKind kind,
            String label,
            String from,
            String to,
            NarrativeKind narrative
    ) {
        requireFrame(from);
        requireFrame(to);
        HeadMovement movement = new HeadMovement(
                "movement:" + (++movementSequence),
                kind,
                label,
                from,
                to,
                narrative
        );
        ArrayList<HeadMovement> candidate = new ArrayList<>(headMovements);
        candidate.add(movement);
        validateCandidate(frames.values(), operations, candidate, barriers, to, generation + 1);
        headMovements.add(movement);
        currentStateId = to;
        generation++;
    }

    private void commitOperation(
            String operationId,
            String actionId,
            String parentId,
            WorkspaceFrame result,
            SFMHistoryGraphContract.EvaluationPolicy policy,
            SFMHistoryGraphContract.EffectClass effect,
            String query,
            List<String> arguments,
            List<SFMHistoryGraphContract.DependencyWitness> witnesses,
            List<String> compatible,
            List<String> rejected,
            NarrativeKind narrative,
            List<String> diagnostics
    ) {
        if (!currentStateId.equals(parentId)) {
            throw new IllegalStateException(
                    "Workspace transition expected " + parentId + " but current state is " + currentStateId);
        }
        if (frames.containsKey(result.id())) {
            throw new IllegalStateException("Workspace frame already exists: " + result.id());
        }
        Operation operation = new Operation(
                operationId,
                actionId,
                parentId,
                Optional.of(result.id()),
                policy,
                effect,
                SFMHistoryGraphContract.OutcomeStatus.SUCCEEDED,
                query,
                arguments,
                witnesses,
                compatible,
                rejected,
                narrative,
                diagnostics
        );
        LinkedHashMap<String, WorkspaceFrame> candidateFrames = new LinkedHashMap<>(frames);
        candidateFrames.put(result.id(), result);
        ArrayList<Operation> candidateOperations = new ArrayList<>(operations);
        candidateOperations.add(operation);
        validateCandidate(
                candidateFrames.values(),
                candidateOperations,
                headMovements,
                barriers,
                result.id(),
                generation + 1
        );
        frames.put(result.id(), result);
        operations.add(operation);
        currentStateId = result.id();
        generation++;
    }

    private void retain(WorkspaceFrame frame) {
        if (frames.putIfAbsent(frame.id(), frame) != null) {
            throw new IllegalStateException("Duplicate workspace frame " + frame.id());
        }
        currentStateId = frame.id();
    }

    private void validateCandidate(
            java.util.Collection<WorkspaceFrame> candidateFrames,
            List<Operation> candidateOperations,
            List<HeadMovement> candidateMovements,
            List<Barrier> candidateBarriers,
            String candidateHead,
            long candidateGeneration
    ) {
        Artifact.create(
                episodeId,
                candidateGeneration,
                candidateHead,
                ambientBeforeHash,
                ambientBeforeHash,
                List.copyOf(candidateFrames),
                candidateOperations,
                candidateMovements,
                candidateBarriers
        );
    }

    @Override
    public synchronized SFMHistoryGraphRuntime.MachineSnapshot snapshot() {
        SFMTrajectoryContract.PlanBook planBook = new SFMTrajectoryContract.PlanBook(
                SFMTrajectoryContract.SCHEMA,
                List.of(),
                Optional.empty()
        );
        SFMTrajectoryContract.MachineStatus status = barriers.isEmpty()
                ? SFMTrajectoryContract.MachineStatus.PAUSED
                : SFMTrajectoryContract.MachineStatus.COMPLETE;
        SFMTrajectoryContract.TrajectoryMachineState machine = new SFMTrajectoryContract.TrajectoryMachineState(
                HEAD_ID,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                "supervision:workspace-counterfactual/1",
                status,
                new SFMTrajectoryContract.RemainingBudget(0, 0, 0)
        );
        WorkspaceFrame current = currentFrame();
        String summary = "head=" + current.id()
                + " · selection=" + current.selectedPath().orElse("none")
                + " · open=" + current.openDocumentPath().orElse("none")
                + (barriers.isEmpty() ? "" : " · external barrier retained");
        return new SFMHistoryGraphRuntime.MachineSnapshot(
                episodeId,
                generation,
                historyGraph(),
                planBook,
                machine,
                Optional.empty(),
                summary
        );
    }

    @Override
    public SFMHistoryGraphRuntime.OperationResult apply(SFMHistoryGraphRuntime.Operation operation) {
        return SFMHistoryGraphRuntime.OperationResult.rejected(
                "Use the selector-explicit workspace-counterfactual actions for this episode");
    }

    private SFMHistoryGraphContract.Graph historyGraph() {
        ArrayList<SFMHistoryGraphContract.ActionIntent> intents = new ArrayList<>();
        ArrayList<SFMHistoryGraphContract.ActionEvaluation> evaluations = new ArrayList<>();
        ArrayList<SFMHistoryGraphContract.ActionOutcome> outcomes = new ArrayList<>();
        ArrayList<SFMHistoryGraphContract.BranchEdge> edges = new ArrayList<>();
        ArrayList<SFMHistoryGraphContract.ActionAttempt> attempts = new ArrayList<>();
        ArrayList<SFMHistoryGraphContract.StateRevision> states = new ArrayList<>();

        for (WorkspaceFrame frame : frames.values()) {
            states.add(new SFMHistoryGraphContract.StateRevision(
                    frame.id(),
                    frame.parentId().stream().toList(),
                    frame.stateHash(),
                    true,
                    SFMHistoryGraphContract.ProjectionStatus.MATERIALIZED
            ));
        }
        for (Operation operation : operations) {
            String intentId = "intent:" + operation.id();
            String evaluationId = "evaluation:" + operation.id();
            String outcomeId = "outcome:" + operation.id();
            intents.add(new SFMHistoryGraphContract.ActionIntent(
                    intentId,
                    operation.actionId(),
                    operation.arguments(),
                    SFMWorkspaceCounterfactualContract.sha256(
                            operation.actionId() + "\n" + operation.query() + "\n"
                                    + String.join("\n", operation.arguments()))
            ));
            SFMHistoryGraphContract.ProjectionStatus projection = projectionStatus(operation.outcomeStatus());
            evaluations.add(new SFMHistoryGraphContract.ActionEvaluation(
                    evaluationId,
                    intentId,
                    operation.parentStateId(),
                    "evaluator:workspace-counterfactual/1",
                    operation.evaluationPolicy(),
                    operation.dependencyWitnesses(),
                    outcomeId,
                    projection
            ));
            outcomes.add(new SFMHistoryGraphContract.ActionOutcome(
                    outcomeId,
                    evaluationId,
                    operation.outcomeStatus(),
                    operation.resultStateId(),
                    operation.diagnostics()
            ));
            if (operation.resultStateId().isPresent()) {
                edges.add(new SFMHistoryGraphContract.BranchEdge(
                        "edge:" + operation.id(),
                        operation.parentStateId(),
                        operation.resultStateId().orElseThrow(),
                        Optional.of(intentId),
                        Optional.of(evaluationId),
                        Optional.of(outcomeId),
                        operation.effectClass(),
                        projection,
                        true
                ));
            } else {
                attempts.add(new SFMHistoryGraphContract.ActionAttempt(
                        "attempt:" + operation.id(),
                        operation.parentStateId(),
                        intentId,
                        evaluationId,
                        outcomeId,
                        operation.effectClass(),
                        projection
                ));
            }
        }

        ArrayList<SFMHistoryGraphContract.HeadMovement> graphMovements = new ArrayList<>();
        for (HeadMovement movement : headMovements) {
            graphMovements.add(new SFMHistoryGraphContract.HeadMovement(
                    movement.id(),
                    movement.kind(),
                    HEAD_ID,
                    movement.fromStateId(),
                    movement.toStateId(),
                    List.of(movement.toStateId()),
                    "sfm:user",
                    movement.label()
            ));
        }
        ArrayList<SFMHistoryGraphContract.RetentionPin> pins = new ArrayList<>();
        if (frames.containsKey(A_EDITED)) {
            pins.add(new SFMHistoryGraphContract.RetentionPin(
                    "pin:recorded-a-branch",
                    SFMHistoryGraphContract.RetentionKind.NAMED_BRANCH,
                    A_EDITED,
                    "recorded-a-result"
            ));
        }
        if (frames.containsKey(FROZEN_A_EDITED)) {
            pins.add(new SFMHistoryGraphContract.RetentionPin(
                    "pin:frozen-a-branch",
                    SFMHistoryGraphContract.RetentionKind.NAMED_BRANCH,
                    FROZEN_A_EDITED,
                    "frozen-a-replay"
            ));
        }
        return new SFMHistoryGraphContract.Graph(
                SFMHistoryGraphContract.SCHEMA,
                intents,
                evaluations,
                outcomes,
                states,
                List.of(new SFMHistoryGraphContract.HistoryHead(
                        HEAD_ID,
                        new SFMHistoryGraphContract.UndoDomain(
                                SFMHistoryGraphContract.UndoDomainKind.WORKSPACE,
                                episodeId
                        ),
                        currentStateId,
                        Optional.of("workspace-current")
                )),
                graphMovements,
                edges,
                attempts,
                pins
        );
    }

    private static SFMHistoryGraphContract.ProjectionStatus projectionStatus(
            SFMHistoryGraphContract.OutcomeStatus status
    ) {
        return switch (status) {
            case SUCCEEDED -> SFMHistoryGraphContract.ProjectionStatus.MATERIALIZED;
            case REJECTED, CONFLICT -> SFMHistoryGraphContract.ProjectionStatus.CONFLICT;
            case CANCELLED -> SFMHistoryGraphContract.ProjectionStatus.CANCELLED;
            case BUDGET_EXHAUSTED -> SFMHistoryGraphContract.ProjectionStatus.BUDGET_EXHAUSTED;
            case EXTERNAL_BARRIER -> SFMHistoryGraphContract.ProjectionStatus.EXTERNAL_BARRIER;
        };
    }

    private static WorkspaceFrame frame(
            String id,
            Optional<String> parent,
            String layout,
            String focus,
            Optional<String> selection,
            Optional<String> open,
            List<WorkspaceDocument> documents
    ) {
        return WorkspaceFrame.create(
                id,
                parent,
                layout,
                focus,
                EXPLORER_LOCATION,
                selection,
                open,
                documents
        );
    }

    private static List<WorkspaceDocument> initialDocuments() {
        return List.of(
                WorkspaceDocument.create(A_PATH, INITIAL_A),
                WorkspaceDocument.create(B_PATH, INITIAL_B)
        );
    }

    private static List<WorkspaceDocument> replaceDocument(
            List<WorkspaceDocument> documents,
            String logicalPath,
            String text
    ) {
        ArrayList<WorkspaceDocument> result = new ArrayList<>();
        boolean replaced = false;
        for (WorkspaceDocument document : documents) {
            if (document.logicalPath().equals(logicalPath)) {
                result.add(WorkspaceDocument.create(logicalPath, text));
                replaced = true;
            } else {
                result.add(document);
            }
        }
        if (!replaced) throw new IllegalArgumentException("Unknown workspace document " + logicalPath);
        return List.copyOf(result);
    }

    private static WorkspaceDocument document(WorkspaceFrame frame, String logicalPath) {
        return frame.documents().stream()
                .filter(document -> document.logicalPath().equals(logicalPath))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown workspace document " + logicalPath));
    }

    private static SFMHistoryGraphContract.DependencyWitness witness(
            String kind,
            String identity,
            String revision
    ) {
        return new SFMHistoryGraphContract.DependencyWitness(kind, identity, revision);
    }

    private static void requireDocumentPath(String logicalPath) {
        if (!A_PATH.equals(logicalPath) && !B_PATH.equals(logicalPath)) {
            throw new IllegalArgumentException("Unknown fixture document " + logicalPath);
        }
    }

    private static String requireText(String value, String label) {
        value = Objects.requireNonNull(value, label).strip();
        if (value.isEmpty()) throw new IllegalArgumentException(label + " must not be blank");
        return value;
    }

    private static String requireHash(String value, String label) {
        value = requireText(value, label);
        if (!value.matches("sha256:[0-9a-f]{64}")) {
            throw new IllegalArgumentException(label + " must be a canonical SHA-256 hash");
        }
        return value;
    }
}
