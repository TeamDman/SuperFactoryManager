package ca.teamdman.sfm.client.history;

import ca.teamdman.sfm.client.history.presentation.SFMHistoryGraphPresentationModel;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

/** Shared exact fixture for History Graph runtime, action, scene, and panel tests. */
public final class SFMHistoryGraphTestFixture {
    private SFMHistoryGraphTestFixture() {
    }

    public static SFMHistoryGraphRuntime.MachineSnapshot snapshot(String machineId, long revision) {
        SFMHistoryGraphContract.StateRevision root = new SFMHistoryGraphContract.StateRevision(
                machineId + "/root",
                List.of(),
                "sha256:" + machineId + "/root",
                true,
                SFMHistoryGraphContract.ProjectionStatus.MATERIALIZED
        );
        String headId = machineId + "/head";
        SFMHistoryGraphContract.Graph history = new SFMHistoryGraphContract.Graph(
                SFMHistoryGraphContract.SCHEMA,
                List.of(),
                List.of(),
                List.of(),
                List.of(root),
                List.of(new SFMHistoryGraphContract.HistoryHead(
                        headId,
                        new SFMHistoryGraphContract.UndoDomain(
                                SFMHistoryGraphContract.UndoDomainKind.DOCUMENT,
                                "document://" + machineId
                        ),
                        root.id(),
                        Optional.of("current")
                )),
                List.of(),
                List.of(),
                List.of()
        );
        SFMHistoryGraphContract.ActionIntent intent = new SFMHistoryGraphContract.ActionIntent(
                machineId + "/intent",
                "sfm:fixture/advance",
                List.of("--fixture"),
                "sha256:" + machineId + "/intent"
        );
        SFMTrajectoryContract.TrajectoryStep step = new SFMTrajectoryContract.TrajectoryStep(
                machineId + "/step",
                root.id(),
                intent,
                SFMHistoryGraphContract.EvaluationPolicy.INTENT_REEVALUATION,
                machineId + "/outcome",
                machineId + "/target",
                SFMHistoryGraphContract.EffectClass.PURE,
                2,
                2,
                0
        );
        SFMTrajectoryContract.TrajectoryRoute route = new SFMTrajectoryContract.TrajectoryRoute(
                machineId + "/route",
                root.id(),
                List.of(step),
                2,
                SFMHistoryGraphContract.ProjectionStatus.MATERIALIZED
        );
        String planId = machineId + "/plan";
        SFMTrajectoryContract.TrajectoryPlanRevision plan = new SFMTrajectoryContract.TrajectoryPlanRevision(
                planId,
                Optional.empty(),
                root.id(),
                "supervision-v1",
                new SFMTrajectoryContract.ActionGeneratorIdentity("fixture-generator", "v1", true),
                new SFMTrajectoryContract.CostPolicyIdentity("fixture-cost", "v1"),
                new SFMTrajectoryContract.HeuristicIdentity("fixture-heuristic", "v1", true),
                SFMTrajectoryContract.SearchAlgorithm.A_STAR,
                new SFMTrajectoryContract.SearchBudget(32, 64, 1_000),
                SFMTrajectoryContract.PlanResult.FOUND,
                SFMTrajectoryContract.OptimalityClaim.MINIMUM_COST_UNDER_DECLARED_GRAPH_AND_POLICY,
                List.of(route),
                Optional.of(route.id()),
                List.of(new SFMTrajectoryContract.SearchCandidate(
                        machineId + "/frontier",
                        1,
                        1,
                        SFMTrajectoryContract.SearchCandidateStatus.OPEN,
                        Optional.of(root.id()),
                        Optional.of(intent.id()),
                        "fixture frontier"
                )),
                "fixture plan"
        );
        SFMTrajectoryContract.PlanBook planBook = new SFMTrajectoryContract.PlanBook(
                SFMTrajectoryContract.SCHEMA,
                List.of(plan),
                Optional.of(plan.id())
        );
        SFMTrajectoryContract.TrajectoryMachineState machine = new SFMTrajectoryContract.TrajectoryMachineState(
                headId,
                Optional.of(plan.id()),
                Optional.of(new SFMTrajectoryContract.InstructionPointer(plan.id(), route.id(), 0)),
                Optional.of(new SFMTrajectoryContract.ProjectionFrontier(
                        plan.id(), 1, 2, List.of(machineId + "/frontier"),
                        SFMHistoryGraphContract.ProjectionStatus.RUNNING
                )),
                "supervision-v1",
                SFMTrajectoryContract.MachineStatus.READY,
                new SFMTrajectoryContract.RemainingBudget(31, 62, 999)
        );
        SFMTrajectoryContract.SupervisionContract supervision = new SFMTrajectoryContract.SupervisionContract(
                machineId + "/supervision",
                "supervision-v1",
                "document://" + machineId,
                root.id(),
                List.of(new SFMTrajectoryContract.Predicate("target", "exact-state", step.predictedStateId())),
                List.of(),
                EnumSet.of(SFMHistoryGraphContract.EffectClass.EXTERNAL_IRREVERSIBLE),
                List.of(),
                plan.budget(),
                8,
                SFMTrajectoryContract.ApprovalRequirement.HUMAN,
                SFMTrajectoryContract.SupervisionStatus.PLANNED,
                Optional.empty()
        );
        return new SFMHistoryGraphRuntime.MachineSnapshot(
                machineId,
                revision,
                history,
                planBook,
                machine,
                Optional.of(supervision),
                "fixture revision " + revision
        );
    }

    public static SFMHistoryGraphRuntime.MachineSnapshot snapshotWithProjection(
            SFMHistoryGraphRuntime.MachineSnapshot contracts,
            Executor executor,
            Supplier<SFMHistoryGraphPresentationModel.Presentation> projection
    ) {
        return new SFMHistoryGraphRuntime.MachineSnapshot(
                contracts.machineId(),
                contracts.revision(),
                contracts.history(),
                contracts.planBook(),
                contracts.machine(),
                contracts.supervision(),
                contracts.summary(),
                executor,
                projection
        );
    }

    public static final class MutableController implements SFMHistoryGraphRuntime.Controller {
        private final String machineId;
        private final List<SFMHistoryGraphRuntime.Operation> operations = new ArrayList<>();
        private long revision;

        public MutableController(String machineId) {
            this.machineId = machineId;
        }

        @Override
        public String machineId() {
            return machineId;
        }

        @Override
        public SFMHistoryGraphRuntime.MachineSnapshot snapshot() {
            return SFMHistoryGraphTestFixture.snapshot(machineId, revision);
        }

        @Override
        public SFMHistoryGraphRuntime.OperationResult apply(SFMHistoryGraphRuntime.Operation operation) {
            operations.add(operation);
            revision++;
            return SFMHistoryGraphRuntime.OperationResult.applied(
                    operation.getClass().getSimpleName() + " at revision " + revision);
        }

        public List<SFMHistoryGraphRuntime.Operation> operations() {
            return List.copyOf(operations);
        }

        public long revision() {
            return revision;
        }

        public void advanceOutsideAction() {
            revision++;
        }
    }
}
