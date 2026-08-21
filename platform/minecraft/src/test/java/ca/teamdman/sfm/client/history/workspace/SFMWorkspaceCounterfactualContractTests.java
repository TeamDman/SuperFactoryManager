package ca.teamdman.sfm.client.history.workspace;

import ca.teamdman.sfm.client.history.SFMHistoryGraphContract;
import ca.teamdman.sfm.client.history.workspace.SFMWorkspaceCounterfactualContract.Artifact;
import ca.teamdman.sfm.client.history.workspace.SFMWorkspaceCounterfactualContract.Barrier;
import ca.teamdman.sfm.client.history.workspace.SFMWorkspaceCounterfactualContract.HeadMovement;
import ca.teamdman.sfm.client.history.workspace.SFMWorkspaceCounterfactualContract.NarrativeKind;
import ca.teamdman.sfm.client.history.workspace.SFMWorkspaceCounterfactualContract.Operation;
import ca.teamdman.sfm.client.history.workspace.SFMWorkspaceCounterfactualContract.WorkspaceDocument;
import ca.teamdman.sfm.client.history.workspace.SFMWorkspaceCounterfactualContract.WorkspaceFrame;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SFMWorkspaceCounterfactualContractTests {
    @Test
    void canonicalizesSetLikeInputWithoutChangingOrderedActionSemantics() {
        Artifact forward = fixture(false);
        Artifact reversed = fixture(true);

        assertAll(
                () -> assertEquals(forward, reversed),
                () -> assertEquals(List.of("state:a-open", "state:b-selected", "state:root"),
                        forward.workspaceFrames().stream().map(WorkspaceFrame::id).toList()),
                () -> assertEquals(List.of("A.java", "B.java"),
                        forward.requireFrame("state:root").documents().stream()
                                .map(WorkspaceDocument::logicalPath).toList()),
                () -> assertEquals(List.of("fixture-selection", "workspace-frame"),
                        forward.requireOperation("operation:open-a").dependencyWitnesses().stream()
                                .map(SFMHistoryGraphContract.DependencyWitness::kind).toList()),
                () -> assertEquals(List.of("selected=A.java", "editor=default"),
                        forward.requireOperation("operation:open-a").arguments()),
                () -> assertEquals(List.of("sfm:document/edit", "sfm:workspace/save"),
                        forward.requireOperation("operation:open-a").compatibleSuffixActionIds())
        );
    }

    @Test
    void fullUnicodeDocumentsAndWorkspaceStateHashesAreDeterministic() {
        String text = "package demo;\n// café 東京 😀\nclass A { String value = \"λ\"; }\n";
        WorkspaceDocument document = WorkspaceDocument.create("src/日本/A.java", text);
        WorkspaceFrame first = WorkspaceFrame.create(
                "state:unicode-1",
                Optional.empty(),
                "layout:λ",
                "panel:編集",
                "selection://workspace/日本",
                Optional.of("src/日本/A.java"),
                Optional.of("src/日本/A.java"),
                List.of(document)
        );
        WorkspaceFrame second = WorkspaceFrame.create(
                "state:unicode-2",
                Optional.empty(),
                "layout:λ",
                "panel:編集",
                "selection://workspace/日本",
                Optional.of("src/日本/A.java"),
                Optional.of("src/日本/A.java"),
                List.of(document)
        );

        assertAll(
                () -> assertEquals(SFMWorkspaceCounterfactualContract.sha256(text), document.textHash()),
                () -> assertEquals(first.stateHash(), second.stateHash()),
                () -> assertEquals(text, first.documents().get(0).text()),
                () -> assertTrue(SFMWorkspaceCounterfactualContract.utf8Length(text) > text.length())
        );
    }

    @Test
    void immutableCopiesDoNotExposeCallerOrAccessorMutation() {
        ArrayList<WorkspaceDocument> documents = new ArrayList<>();
        documents.add(WorkspaceDocument.create("A.java", "class A {}\n"));
        WorkspaceFrame frame = WorkspaceFrame.create(
                "state:root",
                Optional.empty(),
                "layout:one",
                "panel:explorer",
                "selection://fixture",
                Optional.empty(),
                Optional.empty(),
                documents
        );
        ArrayList<WorkspaceFrame> frames = new ArrayList<>(List.of(frame));
        Artifact artifact = Artifact.create(
                "episode:immutable",
                0,
                frame.id(),
                hash("ambient"),
                hash("ambient"),
                frames,
                List.of(),
                List.of(),
                List.of()
        );

        documents.clear();
        frames.clear();

        assertAll(
                () -> assertEquals(1, artifact.workspaceFrames().size()),
                () -> assertEquals(1, artifact.workspaceFrames().get(0).documents().size()),
                () -> assertThrows(UnsupportedOperationException.class,
                        () -> artifact.workspaceFrames().add(frame)),
                () -> assertThrows(UnsupportedOperationException.class,
                        () -> frame.documents().add(WorkspaceDocument.create("B.java", "class B {}\n")))
        );
    }

    @Test
    void constructorsRejectInvalidHashesReferencesCyclesAndAmbientMutation() {
        WorkspaceDocument document = WorkspaceDocument.create("A.java", "class A {}\n");
        WorkspaceFrame root = frame("state:root", Optional.empty(), Optional.empty(), List.of(document));
        WorkspaceFrame missingParent = frame(
                "state:missing-parent",
                Optional.of("state:absent"),
                Optional.empty(),
                List.of(document)
        );
        WorkspaceFrame cycleA = frame("state:cycle-a", Optional.of("state:cycle-b"), Optional.empty(), List.of(document));
        WorkspaceFrame cycleB = frame("state:cycle-b", Optional.of("state:cycle-a"), Optional.empty(), List.of(document));

        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new WorkspaceDocument("A.java", document.text(), hash("wrong"))),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new WorkspaceFrame(
                                root.id(), root.parentId(), root.panelLayoutId(), root.focusedPanelId(),
                                root.explorerLocation(), root.selectedPath(), root.openDocumentPath(),
                                root.documents(), hash("wrong-state")
                        )),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> Artifact.create(
                                "episode:ambient", 0, root.id(), hash("before"), hash("after"),
                                List.of(root), List.of(), List.of(), List.of()
                        )),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> artifact(List.of(missingParent), missingParent.id(), List.of(), List.of(), List.of())),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> artifact(List.of(root, root), root.id(), List.of(), List.of(), List.of())),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> artifact(List.of(cycleA, cycleB), cycleA.id(), List.of(), List.of(), List.of())),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> WorkspaceFrame.create(
                                "state:bad-open", Optional.empty(), "layout:one", "panel:editor",
                                "selection://fixture", Optional.empty(), Optional.of("B.java"), List.of(document)
                        )),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> SFMWorkspaceCounterfactualContract.sha256("bad\ud800unicode"))
        );
    }

    @Test
    void operationAndBarrierInvariantsFailClosed() {
        WorkspaceFrame root = frame(
                "state:root",
                Optional.empty(),
                Optional.empty(),
                List.of(WorkspaceDocument.create("A.java", "class A {}\n"))
        );
        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> operation(
                                "operation:no-result",
                                root.id(),
                                Optional.empty(),
                                SFMHistoryGraphContract.EffectClass.SNAPSHOT_RESTORABLE,
                                SFMHistoryGraphContract.OutcomeStatus.SUCCEEDED
                        )),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> operation(
                                "operation:external-wrong-status",
                                root.id(),
                                Optional.empty(),
                                SFMHistoryGraphContract.EffectClass.EXTERNAL_IRREVERSIBLE,
                                SFMHistoryGraphContract.OutcomeStatus.REJECTED
                        )),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new Barrier(
                                "barrier:bad", root.id(), "sfm:external",
                                SFMHistoryGraphContract.EffectClass.SNAPSHOT_RESTORABLE,
                                SFMHistoryGraphContract.OutcomeStatus.EXTERNAL_BARRIER,
                                "not actually external"
                        )),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new Operation(
                                "operation:overlap", "sfm:test", root.id(), Optional.of(root.id()),
                                SFMHistoryGraphContract.EvaluationPolicy.INTENT_REEVALUATION,
                                SFMHistoryGraphContract.EffectClass.SNAPSHOT_RESTORABLE,
                                SFMHistoryGraphContract.OutcomeStatus.SUCCEEDED,
                                "test", List.of(), List.of(), List.of("sfm:edit"), List.of("sfm:edit"),
                                NarrativeKind.REEVALUATED_INTENT, List.of()
                        ))
        );
    }

    @Test
    void documentCountAndPayloadBoundsAreEnforced() {
        ArrayList<WorkspaceDocument> tooMany = new ArrayList<>();
        for (int index = 0; index <= SFMWorkspaceCounterfactualContract.MAX_DOCUMENTS_PER_FRAME; index++) {
            tooMany.add(WorkspaceDocument.create("file-" + index, ""));
        }

        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> WorkspaceDocument.create(
                                "large.java",
                                "x".repeat(SFMWorkspaceCounterfactualContract.MAX_DOCUMENT_UTF8_BYTES + 1)
                        )),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> WorkspaceFrame.create(
                                "state:large", Optional.empty(), "layout:one", "panel:explorer",
                                "selection://fixture", Optional.empty(), Optional.empty(), tooMany
                        ))
        );
    }

    private static Artifact fixture(boolean reverse) {
        WorkspaceDocument a = WorkspaceDocument.create("A.java", "class A { int value = 1; }\n");
        WorkspaceDocument b = WorkspaceDocument.create("B.java", "class B { int value = 2; }\n");
        List<WorkspaceDocument> documents = reverse ? List.of(b, a) : List.of(a, b);
        WorkspaceFrame root = frame("state:root", Optional.empty(), Optional.empty(), documents);
        WorkspaceFrame selectedB = frame("state:b-selected", Optional.of(root.id()), Optional.of("B.java"), documents);
        WorkspaceFrame openA = WorkspaceFrame.create(
                "state:a-open",
                Optional.of(root.id()),
                "layout:explorer-editor",
                "panel:editor",
                "selection://fixture",
                Optional.of("A.java"),
                Optional.of("A.java"),
                documents
        );
        Operation openOperation = new Operation(
                "operation:open-a",
                "sfm:document/open-selected",
                root.id(),
                Optional.of(openA.id()),
                SFMHistoryGraphContract.EvaluationPolicy.FROZEN_WITNESS_REEXECUTION,
                SFMHistoryGraphContract.EffectClass.SNAPSHOT_RESTORABLE,
                SFMHistoryGraphContract.OutcomeStatus.SUCCEEDED,
                "sfm action invoke sfm:document/open-selected",
                List.of("selected=A.java", "editor=default"),
                reverse
                        ? List.of(witness("workspace-frame"), witness("fixture-selection"))
                        : List.of(witness("fixture-selection"), witness("workspace-frame")),
                List.of("sfm:document/edit", "sfm:workspace/save"),
                List.of("sfm:process/launch"),
                NarrativeKind.FROZEN_WITNESS,
                reverse ? List.of("witness retained", "A remains selected")
                        : List.of("A remains selected", "witness retained")
        );
        Operation barrierOperation = operation(
                "operation:external",
                selectedB.id(),
                Optional.empty(),
                SFMHistoryGraphContract.EffectClass.EXTERNAL_IRREVERSIBLE,
                SFMHistoryGraphContract.OutcomeStatus.EXTERNAL_BARRIER
        );
        HeadMovement movement = new HeadMovement(
                "movement:checkout",
                SFMHistoryGraphContract.HeadMovementKind.CHECKOUT,
                "Recorded A checkout",
                selectedB.id(),
                openA.id(),
                NarrativeKind.RECORDED_CHECKOUT
        );
        Barrier barrier = new Barrier(
                "barrier:process",
                selectedB.id(),
                "sfm:process/launch",
                SFMHistoryGraphContract.EffectClass.EXTERNAL_IRREVERSIBLE,
                SFMHistoryGraphContract.OutcomeStatus.EXTERNAL_BARRIER,
                "External process effects are outside the restorable fixture"
        );
        ArrayList<WorkspaceFrame> frames = new ArrayList<>(List.of(root, selectedB, openA));
        ArrayList<Operation> operations = new ArrayList<>(List.of(openOperation, barrierOperation));
        if (reverse) {
            Collections.reverse(frames);
            Collections.reverse(operations);
        }
        return artifact(frames, openA.id(), operations, List.of(movement), List.of(barrier));
    }

    private static WorkspaceFrame frame(
            String id,
            Optional<String> parent,
            Optional<String> selectedPath,
            List<WorkspaceDocument> documents
    ) {
        return WorkspaceFrame.create(
                id,
                parent,
                "layout:explorer",
                "panel:explorer",
                "selection://fixture",
                selectedPath,
                Optional.empty(),
                documents
        );
    }

    private static Artifact artifact(
            List<WorkspaceFrame> frames,
            String current,
            List<Operation> operations,
            List<HeadMovement> movements,
            List<Barrier> barriers
    ) {
        return Artifact.create(
                "episode:x5",
                12,
                current,
                hash("ambient-checkout"),
                hash("ambient-checkout"),
                frames,
                operations,
                movements,
                barriers
        );
    }

    private static Operation operation(
            String id,
            String parent,
            Optional<String> result,
            SFMHistoryGraphContract.EffectClass effectClass,
            SFMHistoryGraphContract.OutcomeStatus status
    ) {
        return new Operation(
                id,
                "sfm:fixture/action",
                parent,
                result,
                SFMHistoryGraphContract.EvaluationPolicy.INTENT_REEVALUATION,
                effectClass,
                status,
                "fixture action",
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                NarrativeKind.REEVALUATED_INTENT,
                List.of()
        );
    }

    private static SFMHistoryGraphContract.DependencyWitness witness(String kind) {
        return new SFMHistoryGraphContract.DependencyWitness(kind, "fixture", "revision:1");
    }

    private static String hash(String value) {
        return SFMWorkspaceCounterfactualContract.sha256(value);
    }
}
