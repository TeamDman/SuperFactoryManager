package ca.teamdman.sfm.client.history.workspace;

import ca.teamdman.sfm.client.history.SFMHistoryGraphContract;
import ca.teamdman.sfm.client.history.SFMHistoryGraphRuntime;
import ca.teamdman.sfm.client.history.presentation.SFMHistoryGraphPresentationModel;
import ca.teamdman.sfm.client.history.presentation.SFMHistoryGraphPresentationProjection;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SFMWorkspaceCounterfactualControllerTests {
    private static final String AMBIENT = SFMWorkspaceCounterfactualContract.sha256("unchanged checkout");

    @Test
    void naturalAJourneyForksAndComparesCheckoutFrozenWitnessAndReevaluatedIntent() {
        AtomicInteger ambientReads = new AtomicInteger();
        SFMWorkspaceCounterfactualController controller = controller(ambientReads);

        assertEquals(SFMWorkspaceCounterfactualController.BEFORE_SELECTION, controller.currentFrame().id());
        applied(controller.selectDocument(SFMWorkspaceCounterfactualController.A_PATH));
        applied(controller.openSelectedDocument(SFMWorkspaceCounterfactualController.A_PATH));
        applied(controller.acceptEditorText(
                SFMWorkspaceCounterfactualController.A_OPEN,
                SFMWorkspaceCounterfactualController.A_PATH,
                SFMWorkspaceCounterfactualController.INITIAL_A
                        + SFMWorkspaceCounterfactualController.EDIT_SUFFIX
        ));
        int recordedOperationCount = controller.artifact().operations().size();

        applied(controller.forkBeforeSelection());
        assertEquals(SFMWorkspaceCounterfactualController.BEFORE_SELECTION, controller.currentFrame().id());
        applied(controller.selectDocument(SFMWorkspaceCounterfactualController.B_PATH));
        int beforeCheckoutOperations = controller.artifact().operations().size();
        applied(controller.checkoutRecordedA());
        assertAll(
                () -> assertEquals(SFMWorkspaceCounterfactualController.A_EDITED,
                        controller.currentFrame().id()),
                () -> assertEquals(beforeCheckoutOperations, controller.artifact().operations().size()),
                () -> assertTrue(controller.artifact().headMovements().stream().anyMatch(movement ->
                        movement.narrativeKind()
                                == SFMWorkspaceCounterfactualContract.NarrativeKind.RECORDED_CHECKOUT))
        );

        applied(controller.replayFrozenAWitness());
        assertAll(
                () -> assertEquals(SFMWorkspaceCounterfactualController.FROZEN_A_EDITED,
                        controller.currentFrame().id()),
                () -> assertEquals(
                        SFMWorkspaceCounterfactualController.INITIAL_A
                                + SFMWorkspaceCounterfactualController.EDIT_SUFFIX,
                        controller.documentText(SFMWorkspaceCounterfactualController.A_PATH)),
                () -> assertEquals(SFMWorkspaceCounterfactualController.INITIAL_B,
                        controller.documentText(SFMWorkspaceCounterfactualController.B_PATH))
        );

        applied(controller.reevaluateIntentOnB());
        var artifact = controller.artifact();
        var snapshot = controller.snapshot();
        var graph = snapshot.history();
        var presentation = SFMHistoryGraphPresentationProjection.project(
                graph,
                snapshot.planBook(),
                snapshot.machine()
        );
        assertAll(
                () -> assertEquals(SFMWorkspaceCounterfactualController.REEVALUATED_B_EDITED,
                        artifact.currentStateId()),
                () -> assertEquals(
                        SFMWorkspaceCounterfactualController.INITIAL_B
                                + SFMWorkspaceCounterfactualController.EDIT_SUFFIX,
                        controller.documentText(SFMWorkspaceCounterfactualController.B_PATH)),
                () -> assertTrue(artifact.workspaceFrames().stream().anyMatch(frame ->
                        frame.id().equals(SFMWorkspaceCounterfactualController.A_EDITED))),
                () -> assertTrue(artifact.workspaceFrames().stream().anyMatch(frame ->
                        frame.id().equals(SFMWorkspaceCounterfactualController.FROZEN_A_EDITED))),
                () -> assertEquals(1, artifact.barriers().size()),
                () -> assertTrue(artifact.operations().stream()
                        .filter(operation -> operation.actionId().equals(
                                SFMWorkspaceCounterfactualController.EXTERNAL_ACTION_ID))
                        .allMatch(operation -> operation.resultStateId().isEmpty())),
                () -> assertEquals(1, graph.actionAttempts().size()),
                () -> assertEquals(SFMHistoryGraphContract.ProjectionStatus.EXTERNAL_BARRIER,
                        graph.actionAttempts().get(0).status()),
                () -> assertTrue(presentation.nodes().stream().anyMatch(node ->
                        node.origins().contains(SFMHistoryGraphPresentationModel.NodeOrigin.HEAD_MOVEMENT))),
                () -> assertTrue(presentation.nodes().stream().anyMatch(node ->
                        node.roles().contains(SFMHistoryGraphPresentationModel.LegendRole.BARRIER))),
                () -> assertTrue(controller.artifact().operations().size() > recordedOperationCount),
                () -> assertTrue(ambientReads.get() > 0)
        );
    }

    @Test
    void staleEditorParentFailsClosedWithoutPublishingAFrame() {
        SFMWorkspaceCounterfactualController controller = controller(new AtomicInteger());
        applied(controller.selectDocument(SFMWorkspaceCounterfactualController.A_PATH));
        applied(controller.openSelectedDocument(SFMWorkspaceCounterfactualController.A_PATH));
        long before = controller.revision();

        SFMHistoryGraphRuntime.OperationResult result = controller.acceptEditorText(
                SFMWorkspaceCounterfactualController.BEFORE_SELECTION,
                SFMWorkspaceCounterfactualController.A_PATH,
                "malicious stale value"
        );

        assertAll(
                () -> assertEquals(SFMHistoryGraphRuntime.OperationStatus.REJECTED, result.status()),
                () -> assertEquals(before, controller.revision()),
                () -> assertEquals(SFMWorkspaceCounterfactualController.A_OPEN, controller.currentFrame().id()),
                () -> assertEquals(SFMWorkspaceCounterfactualController.INITIAL_A,
                        controller.documentText(SFMWorkspaceCounterfactualController.A_PATH)),
                () -> assertFalse(controller.artifact().workspaceFrames().stream().anyMatch(frame ->
                        frame.id().equals(SFMWorkspaceCounterfactualController.A_EDITED)))
        );
    }

    @Test
    void canonicalArtifactRestoresFreshAndReencodesByteIdentically() {
        SFMWorkspaceCounterfactualController controller = completedController();
        String encoded = SFMWorkspaceCounterfactualJsonCodec.write(controller.artifact());
        var decoded = SFMWorkspaceCounterfactualJsonCodec.read(encoded);
        SFMWorkspaceCounterfactualController restored = SFMWorkspaceCounterfactualController.restore(
                decoded,
                () -> AMBIENT
        );

        assertAll(
                () -> assertEquals(encoded, SFMWorkspaceCounterfactualJsonCodec.write(restored.artifact())),
                () -> assertEquals(controller.snapshot().history(), restored.snapshot().history()),
                () -> assertEquals(controller.snapshot().planBook(), restored.snapshot().planBook()),
                () -> assertEquals(controller.snapshot().machine(), restored.snapshot().machine()),
                () -> assertEquals(controller.snapshot().summary(), restored.snapshot().summary()),
                () -> assertEquals(controller.documentText(SFMWorkspaceCounterfactualController.A_PATH),
                        restored.documentText(SFMWorkspaceCounterfactualController.A_PATH)),
                () -> assertEquals(controller.documentText(SFMWorkspaceCounterfactualController.B_PATH),
                        restored.documentText(SFMWorkspaceCounterfactualController.B_PATH))
        );
    }

    private static SFMWorkspaceCounterfactualController completedController() {
        SFMWorkspaceCounterfactualController controller = controller(new AtomicInteger());
        applied(controller.selectDocument(SFMWorkspaceCounterfactualController.A_PATH));
        applied(controller.openSelectedDocument(SFMWorkspaceCounterfactualController.A_PATH));
        applied(controller.acceptEditorText(
                SFMWorkspaceCounterfactualController.A_OPEN,
                SFMWorkspaceCounterfactualController.A_PATH,
                SFMWorkspaceCounterfactualController.INITIAL_A
                        + SFMWorkspaceCounterfactualController.EDIT_SUFFIX
        ));
        applied(controller.forkBeforeSelection());
        applied(controller.selectDocument(SFMWorkspaceCounterfactualController.B_PATH));
        applied(controller.checkoutRecordedA());
        applied(controller.replayFrozenAWitness());
        applied(controller.reevaluateIntentOnB());
        return controller;
    }

    private static SFMWorkspaceCounterfactualController controller(AtomicInteger reads) {
        Supplier<String> probe = () -> {
            reads.incrementAndGet();
            return AMBIENT;
        };
        return new SFMWorkspaceCounterfactualController("sfm:x5/test", probe);
    }

    private static void applied(SFMHistoryGraphRuntime.OperationResult result) {
        assertEquals(SFMHistoryGraphRuntime.OperationStatus.APPLIED, result.status(), result::message);
    }
}
