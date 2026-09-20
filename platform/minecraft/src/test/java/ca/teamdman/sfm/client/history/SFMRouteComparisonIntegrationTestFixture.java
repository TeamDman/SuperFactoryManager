package ca.teamdman.sfm.client.history;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Two-retained-route fixture shared by the X3 runtime and palette integration tests. */
public final class SFMRouteComparisonIntegrationTestFixture {
    private SFMRouteComparisonIntegrationTestFixture() {
    }

    public static SFMHistoryGraphRuntime.MachineSnapshot snapshot(String machineId, long revision) {
        SFMHistoryGraphRuntime.MachineSnapshot base = SFMHistoryGraphTestFixture.snapshot(machineId, revision);
        SFMTrajectoryContract.TrajectoryPlanRevision plan = base.planBook().plans().get(0);
        SFMTrajectoryContract.TrajectoryRoute first = plan.routes().get(0);
        SFMTrajectoryContract.TrajectoryRoute second = new SFMTrajectoryContract.TrajectoryRoute(
                machineId + "/route-z",
                first.startStateId(),
                first.steps(),
                first.totalCost(),
                first.status()
        );
        SFMTrajectoryContract.TrajectoryPlanRevision comparedPlan = new SFMTrajectoryContract.TrajectoryPlanRevision(
                plan.id(),
                plan.parentPlanRevisionId(),
                plan.startStateId(),
                plan.supervisionContractRevision(),
                plan.actionGenerator(),
                plan.costPolicy(),
                plan.heuristic(),
                plan.algorithm(),
                plan.budget(),
                plan.result(),
                plan.optimalityClaim(),
                List.of(first, second),
                plan.selectedRouteId(),
                plan.exploredCandidates(),
                plan.resultExplanation()
        );
        return new SFMHistoryGraphRuntime.MachineSnapshot(
                base.machineId(),
                base.revision(),
                base.history(),
                new SFMTrajectoryContract.PlanBook(
                        SFMTrajectoryContract.SCHEMA,
                        List.of(comparedPlan),
                        base.planBook().selectedPlanRevisionId()
                ),
                base.machine(),
                base.supervision(),
                base.summary()
        );
    }

    public static final class RecordingController implements SFMHistoryGraphRuntime.Controller {
        private final String machineId;
        private final List<SFMHistoryGraphRuntime.Operation> operations = new ArrayList<>();
        private long revision;

        public RecordingController(String machineId) {
            this.machineId = machineId;
        }

        @Override
        public String machineId() {
            return machineId;
        }

        @Override
        public SFMHistoryGraphRuntime.MachineSnapshot snapshot() {
            return SFMRouteComparisonIntegrationTestFixture.snapshot(machineId, revision);
        }

        @Override
        public SFMHistoryGraphRuntime.OperationResult apply(SFMHistoryGraphRuntime.Operation operation) {
            operations.add(operation);
            revision++;
            return SFMHistoryGraphRuntime.OperationResult.applied(
                    operation.getClass().getSimpleName() + " at revision " + revision
            );
        }

        public List<SFMHistoryGraphRuntime.Operation> operations() {
            return List.copyOf(operations);
        }

        public Optional<SFMHistoryGraphRuntime.Operation> lastOperation() {
            return operations.isEmpty()
                    ? Optional.empty()
                    : Optional.of(operations.get(operations.size() - 1));
        }
    }
}
