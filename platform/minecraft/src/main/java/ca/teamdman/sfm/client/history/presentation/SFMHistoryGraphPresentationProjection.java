package ca.teamdman.sfm.client.history.presentation;

import ca.teamdman.sfm.client.history.SFMHistoryGraphContract;
import ca.teamdman.sfm.client.history.SFMTrajectoryContract;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Deterministically projects immutable history and trajectory contracts into bounded presentation data. */
public final class SFMHistoryGraphPresentationProjection {
    private SFMHistoryGraphPresentationProjection() {
    }

    public static SFMHistoryGraphPresentationModel.Presentation project(
            SFMHistoryGraphContract.Graph history,
            SFMTrajectoryContract.PlanBook planBook,
            SFMTrajectoryContract.TrajectoryMachineState machine
    ) {
        return project(history, planBook, machine, SFMHistoryGraphPresentationModel.Bounds.defaults());
    }

    public static SFMHistoryGraphPresentationModel.Presentation project(
            SFMHistoryGraphContract.Graph history,
            SFMTrajectoryContract.PlanBook planBook,
            SFMTrajectoryContract.TrajectoryMachineState machine,
            SFMHistoryGraphPresentationModel.Bounds bounds
    ) {
        Objects.requireNonNull(history, "history");
        Objects.requireNonNull(planBook, "planBook");
        Objects.requireNonNull(machine, "machine");
        Objects.requireNonNull(bounds, "bounds");

        Map<String, NodeDraft> nodes = new HashMap<>();
        Map<String, EdgeDraft> edges = new HashMap<>();
        ArrayList<SFMHistoryGraphPresentationModel.Marker> markers = new ArrayList<>();
        HashSet<String> requiredNodeIds = new HashSet<>();
        HashSet<String> requiredEdgeIds = new HashSet<>();

        Map<String, SFMHistoryGraphContract.StateRevision> historyStates = index(
                history.states(),
                SFMHistoryGraphContract.StateRevision::id,
                "history state"
        );
        Map<String, SFMHistoryGraphContract.HistoryHead> historyHeads = index(
                history.heads(),
                SFMHistoryGraphContract.HistoryHead::id,
                "history head"
        );
        SFMHistoryGraphContract.HistoryHead actualHead = require(
                historyHeads,
                machine.actualHistoryHeadId(),
                "actual history head"
        );
        Set<String> actualAncestry = collectAncestry(actualHead.stateRevisionId(), historyStates);

        for (SFMHistoryGraphContract.StateRevision state : history.states()) {
            NodeDraft node = node(nodes, state.id(), SFMHistoryGraphPresentationModel.NodeOrigin.HISTORY);
            if (state.committed()) {
                node.roles.add(SFMHistoryGraphPresentationModel.LegendRole.COMMITTED_EXECUTED);
                if (!actualAncestry.contains(state.id())) {
                    node.roles.add(SFMHistoryGraphPresentationModel.LegendRole.RETAINED_ALTERNATIVE);
                }
            } else {
                node.roles.add(SFMHistoryGraphPresentationModel.LegendRole.RETAINED_ALTERNATIVE);
            }
            addProjectionStatus(node.roles, state.status());
        }

        for (SFMHistoryGraphContract.BranchEdge edge : history.edges()) {
            NodeDraft parent = node(nodes, edge.parentStateRevisionId(),
                    SFMHistoryGraphPresentationModel.NodeOrigin.HISTORY);
            NodeDraft child = node(nodes, edge.childStateRevisionId(),
                    SFMHistoryGraphPresentationModel.NodeOrigin.HISTORY);
            EnumSet<SFMHistoryGraphPresentationModel.LegendRole> roles = EnumSet.noneOf(
                    SFMHistoryGraphPresentationModel.LegendRole.class
            );
            SFMHistoryGraphPresentationModel.EdgeCommitment commitment;
            String label;
            String narration;
            if (edge.committed()) {
                commitment = SFMHistoryGraphPresentationModel.EdgeCommitment.COMMITTED_HISTORY;
                roles.add(SFMHistoryGraphPresentationModel.LegendRole.COMMITTED_EXECUTED);
                label = "Committed history transition " + edge.id();
                narration = "Committed history transition " + edge.id()
                        + " from state " + parent.id + " to state " + child.id + ".";
                if (!actualAncestry.contains(parent.id) || !actualAncestry.contains(child.id)) {
                    roles.add(SFMHistoryGraphPresentationModel.LegendRole.RETAINED_ALTERNATIVE);
                }
            } else {
                commitment = SFMHistoryGraphPresentationModel.EdgeCommitment.PROJECTED_CANDIDATE;
                roles.add(SFMHistoryGraphPresentationModel.LegendRole.RETAINED_ALTERNATIVE);
                label = "Projected history transition " + edge.id();
                narration = "Projected history transition " + edge.id()
                        + " from state " + parent.id + " to state " + child.id + ".";
            }
            addProjectionStatus(roles, edge.status());
            String edgeId = compositeId("history", edge.id());
            addEdge(edges, new EdgeDraft(
                    edgeId,
                    edge.id(),
                    parent.id,
                    child.id,
                    SFMHistoryGraphPresentationModel.EdgeOrigin.HISTORY,
                    commitment,
                    roles,
                    label,
                    narration
            ));
        }

        for (SFMHistoryGraphContract.RetentionPin pin : history.retentionPins()) {
            NodeDraft retainedNode = nodes.get(pin.targetId());
            if (retainedNode != null) {
                retainedNode.roles.add(SFMHistoryGraphPresentationModel.LegendRole.RETAINED_ALTERNATIVE);
            }
            edges.values().stream()
                    .filter(edge -> edge.origin == SFMHistoryGraphPresentationModel.EdgeOrigin.HISTORY)
                    .filter(edge -> edge.sourceContractId.equals(pin.targetId()))
                    .forEach(edge -> edge.roles.add(
                            SFMHistoryGraphPresentationModel.LegendRole.RETAINED_ALTERNATIVE
                    ));
        }

        NodeDraft actualHeadNode = node(
                nodes,
                actualHead.stateRevisionId(),
                SFMHistoryGraphPresentationModel.NodeOrigin.HISTORY
        );
        actualHeadNode.roles.add(SFMHistoryGraphPresentationModel.LegendRole.ACTUAL_HEAD);
        requiredNodeIds.add(actualHeadNode.id);
        markers.add(new SFMHistoryGraphPresentationModel.Marker(
                "marker:actual-head",
                SFMHistoryGraphPresentationModel.MarkerKind.ACTUAL_HEAD,
                SFMHistoryGraphPresentationModel.MarkerSubjectKind.NODE,
                actualHeadNode.id,
                SFMHistoryGraphPresentationModel.LegendRole.ACTUAL_HEAD,
                "Actual head " + actualHead.id(),
                "Actual history head " + actualHead.id() + " points to state " + actualHeadNode.id + "."
        ));

        Map<String, SFMTrajectoryContract.TrajectoryPlanRevision> plans = index(
                planBook.plans(),
                SFMTrajectoryContract.TrajectoryPlanRevision::id,
                "trajectory plan"
        );
        machine.selectedTrajectoryRevisionId().ifPresent(id -> require(plans, id, "machine trajectory plan"));
        Optional<String> presentationPlanId = machine.selectedTrajectoryRevisionId()
                .or(planBook::selectedPlanRevisionId);
        presentationPlanId.ifPresent(id -> require(plans, id, "presentation trajectory plan"));

        for (SFMTrajectoryContract.TrajectoryPlanRevision plan : planBook.plans()) {
            for (SFMTrajectoryContract.TrajectoryRoute route : plan.routes()) {
                addTrajectoryRoute(
                        nodes,
                        edges,
                        plan,
                        route,
                        presentationPlanId,
                        machine.instructionPointer()
                );
            }
            for (SFMTrajectoryContract.SearchCandidate candidate : plan.exploredCandidates()) {
                addSearchCandidate(nodes, edges, plan, candidate);
            }
        }

        if (presentationPlanId.isPresent()) {
            SFMTrajectoryContract.TrajectoryPlanRevision selectedPlan = require(
                    plans,
                    presentationPlanId.orElseThrow(),
                    "presentation trajectory plan"
            );
            selectedPlan.selectedRouteId().ifPresent(routeId -> {
                SFMTrajectoryContract.TrajectoryRoute selectedRoute = requireRoute(selectedPlan, routeId);
                String targetStateId = terminalState(selectedRoute);
                NodeDraft target = node(
                        nodes,
                        targetStateId,
                        SFMHistoryGraphPresentationModel.NodeOrigin.TRAJECTORY
                );
                target.roles.add(SFMHistoryGraphPresentationModel.LegendRole.TARGET);
            });
        }

        machine.projectionFrontier().ifPresent(frontier -> {
            require(plans, frontier.planRevisionId(), "frontier trajectory plan");
            for (String stateId : frontier.frontierStateIds()) {
                NodeDraft frontierNode = node(
                        nodes,
                        stateId,
                        SFMHistoryGraphPresentationModel.NodeOrigin.SEARCH
                );
                frontierNode.roles.add(SFMHistoryGraphPresentationModel.LegendRole.OPEN_FRONTIER);
            }
        });

        machine.instructionPointer().ifPresent(pointer -> {
            SFMTrajectoryContract.TrajectoryPlanRevision plan = require(
                    plans,
                    pointer.planRevisionId(),
                    "instruction-pointer trajectory plan"
            );
            SFMTrajectoryContract.TrajectoryRoute route = requireRoute(plan, pointer.routeId());
            if (pointer.nextStepIndex() > route.steps().size()) {
                throw new IllegalArgumentException("Instruction pointer lies beyond its route");
            }
            if (pointer.nextStepIndex() == route.steps().size()) {
                String subject = terminalState(route);
                NodeDraft node = node(
                        nodes,
                        subject,
                        SFMHistoryGraphPresentationModel.NodeOrigin.TRAJECTORY
                );
                node.roles.add(SFMHistoryGraphPresentationModel.LegendRole.INSTRUCTION_POINTER);
                requiredNodeIds.add(subject);
                markers.add(new SFMHistoryGraphPresentationModel.Marker(
                        "marker:instruction-pointer",
                        SFMHistoryGraphPresentationModel.MarkerKind.INSTRUCTION_POINTER,
                        SFMHistoryGraphPresentationModel.MarkerSubjectKind.NODE,
                        subject,
                        SFMHistoryGraphPresentationModel.LegendRole.INSTRUCTION_POINTER,
                        "Instruction pointer after final step",
                        "Instruction pointer for plan " + plan.id() + " and route " + route.id()
                                + " is after the final step at state " + subject + "."
                ));
                return;
            }

            SFMTrajectoryContract.TrajectoryStep step = route.steps().get(pointer.nextStepIndex());
            String subject = trajectoryEdgeId(plan.id(), route.id(), step.id());
            EdgeDraft edge = require(edges, subject, "instruction-pointer trajectory edge");
            edge.roles.add(SFMHistoryGraphPresentationModel.LegendRole.INSTRUCTION_POINTER);
            requiredEdgeIds.add(subject);
            requiredNodeIds.add(edge.fromNodeId);
            requiredNodeIds.add(edge.toNodeId);
            markers.add(new SFMHistoryGraphPresentationModel.Marker(
                    "marker:instruction-pointer",
                    SFMHistoryGraphPresentationModel.MarkerKind.INSTRUCTION_POINTER,
                    SFMHistoryGraphPresentationModel.MarkerSubjectKind.EDGE,
                    subject,
                    SFMHistoryGraphPresentationModel.LegendRole.INSTRUCTION_POINTER,
                    "Instruction pointer before " + step.id(),
                    "Instruction pointer for plan " + plan.id() + " and route " + route.id()
                            + " is before step " + step.id() + "."
            ));
        });

        List<SFMHistoryGraphPresentationModel.Node> allNodes = nodes.values().stream()
                .map(NodeDraft::finish)
                .toList();
        List<SFMHistoryGraphPresentationModel.Node> visibleNodes = allNodes.stream()
                .sorted(Comparator
                        .comparingInt((SFMHistoryGraphPresentationModel.Node node) ->
                                requiredNodeIds.contains(node.id()) ? 0 : 1)
                        .thenComparingInt(node -> rolePriority(node.roles()))
                        .thenComparing(SFMHistoryGraphPresentationModel.Node::id))
                .limit(bounds.maxNodes())
                .sorted(Comparator.comparing(SFMHistoryGraphPresentationModel.Node::id))
                .toList();
        Set<String> visibleNodeIds = visibleNodes.stream()
                .map(SFMHistoryGraphPresentationModel.Node::id)
                .collect(java.util.stream.Collectors.toSet());

        List<SFMHistoryGraphPresentationModel.Edge> allEdges = edges.values().stream()
                .map(EdgeDraft::finish)
                .toList();
        List<SFMHistoryGraphPresentationModel.Edge> visibleEdges = allEdges.stream()
                .filter(edge -> visibleNodeIds.contains(edge.fromNodeId()))
                .filter(edge -> visibleNodeIds.contains(edge.toNodeId()))
                .sorted(Comparator
                        .comparingInt((SFMHistoryGraphPresentationModel.Edge edge) ->
                                requiredEdgeIds.contains(edge.id()) ? 0 : 1)
                        .thenComparingInt(edge -> rolePriority(edge.roles()))
                        .thenComparing(SFMHistoryGraphPresentationModel.Edge::id))
                .limit(bounds.maxEdges())
                .sorted(Comparator.comparing(SFMHistoryGraphPresentationModel.Edge::id))
                .toList();
        Set<String> visibleEdgeIds = visibleEdges.stream()
                .map(SFMHistoryGraphPresentationModel.Edge::id)
                .collect(java.util.stream.Collectors.toSet());

        List<SFMHistoryGraphPresentationModel.Marker> visibleMarkers = markers.stream()
                .filter(marker -> marker.subjectKind() == SFMHistoryGraphPresentationModel.MarkerSubjectKind.NODE
                        ? visibleNodeIds.contains(marker.subjectId())
                        : visibleEdgeIds.contains(marker.subjectId()))
                .sorted(Comparator.comparing(SFMHistoryGraphPresentationModel.Marker::id))
                .toList();

        return new SFMHistoryGraphPresentationModel.Presentation(
                SFMHistoryGraphPresentationModel.LegendRole.stableLegend(),
                visibleNodes,
                visibleEdges,
                visibleMarkers,
                new SFMHistoryGraphPresentationModel.ProjectionSummary(
                        allNodes.size(),
                        visibleNodes.size(),
                        allEdges.size(),
                        visibleEdges.size(),
                        markers.size(),
                        visibleMarkers.size()
                )
        );
    }

    private static void addTrajectoryRoute(
            Map<String, NodeDraft> nodes,
            Map<String, EdgeDraft> edges,
            SFMTrajectoryContract.TrajectoryPlanRevision plan,
            SFMTrajectoryContract.TrajectoryRoute route,
            Optional<String> presentationPlanId,
            Optional<SFMTrajectoryContract.InstructionPointer> instructionPointer
    ) {
        node(nodes, route.startStateId(), SFMHistoryGraphPresentationModel.NodeOrigin.TRAJECTORY);
        boolean selectedRoute = presentationPlanId.filter(plan.id()::equals).isPresent()
                && plan.selectedRouteId().filter(route.id()::equals).isPresent();
        int suffixStart = 0;
        if (selectedRoute && instructionPointer.isPresent()) {
            SFMTrajectoryContract.InstructionPointer pointer = instructionPointer.orElseThrow();
            if (pointer.planRevisionId().equals(plan.id()) && pointer.routeId().equals(route.id())) {
                if (pointer.nextStepIndex() > route.steps().size()) {
                    throw new IllegalArgumentException("Instruction pointer lies beyond its route");
                }
                suffixStart = pointer.nextStepIndex();
            }
        }

        for (int index = 0; index < route.steps().size(); index++) {
            SFMTrajectoryContract.TrajectoryStep step = route.steps().get(index);
            NodeDraft parent = node(
                    nodes,
                    step.expectedParentStateId(),
                    SFMHistoryGraphPresentationModel.NodeOrigin.TRAJECTORY
            );
            NodeDraft child = node(
                    nodes,
                    step.predictedStateId(),
                    SFMHistoryGraphPresentationModel.NodeOrigin.TRAJECTORY
            );
            if (selectedRoute && index < suffixStart) continue;

            EnumSet<SFMHistoryGraphPresentationModel.LegendRole> roles = EnumSet.noneOf(
                    SFMHistoryGraphPresentationModel.LegendRole.class
            );
            String label;
            String narration;
            if (selectedRoute) {
                roles.add(SFMHistoryGraphPresentationModel.LegendRole.SELECTED_PROJECTED_SUFFIX);
                parent.roles.add(SFMHistoryGraphPresentationModel.LegendRole.SELECTED_PROJECTED_SUFFIX);
                child.roles.add(SFMHistoryGraphPresentationModel.LegendRole.SELECTED_PROJECTED_SUFFIX);
                label = "Selected projected step " + step.id();
                narration = "Selected projected step " + step.id() + " from state " + parent.id
                        + " to state " + child.id + ".";
            } else {
                roles.add(SFMHistoryGraphPresentationModel.LegendRole.RETAINED_ALTERNATIVE);
                parent.roles.add(SFMHistoryGraphPresentationModel.LegendRole.RETAINED_ALTERNATIVE);
                child.roles.add(SFMHistoryGraphPresentationModel.LegendRole.RETAINED_ALTERNATIVE);
                label = "Retained trajectory step " + step.id();
                narration = "Retained trajectory step " + step.id() + " from state " + parent.id
                        + " to state " + child.id + ".";
            }
            if (step.effectClass() == SFMHistoryGraphContract.EffectClass.EXTERNAL_IRREVERSIBLE
                    || route.status() == SFMHistoryGraphContract.ProjectionStatus.EXTERNAL_BARRIER) {
                roles.add(SFMHistoryGraphPresentationModel.LegendRole.BARRIER);
                parent.roles.add(SFMHistoryGraphPresentationModel.LegendRole.BARRIER);
                child.roles.add(SFMHistoryGraphPresentationModel.LegendRole.BARRIER);
            }
            addEdge(edges, new EdgeDraft(
                    trajectoryEdgeId(plan.id(), route.id(), step.id()),
                    step.id(),
                    parent.id,
                    child.id,
                    SFMHistoryGraphPresentationModel.EdgeOrigin.TRAJECTORY,
                    SFMHistoryGraphPresentationModel.EdgeCommitment.PROJECTED_CANDIDATE,
                    roles,
                    label,
                    narration
            ));
        }
    }

    private static void addSearchCandidate(
            Map<String, NodeDraft> nodes,
            Map<String, EdgeDraft> edges,
            SFMTrajectoryContract.TrajectoryPlanRevision plan,
            SFMTrajectoryContract.SearchCandidate candidate
    ) {
        NodeDraft candidateNode = node(
                nodes,
                candidate.stateId(),
                SFMHistoryGraphPresentationModel.NodeOrigin.SEARCH
        );
        SFMHistoryGraphPresentationModel.LegendRole role = switch (candidate.status()) {
            case OPEN -> SFMHistoryGraphPresentationModel.LegendRole.OPEN_FRONTIER;
            case CLOSED -> SFMHistoryGraphPresentationModel.LegendRole.CLOSED;
            case BARRIER -> SFMHistoryGraphPresentationModel.LegendRole.BARRIER;
            case PRUNED -> SFMHistoryGraphPresentationModel.LegendRole.RETAINED_ALTERNATIVE;
        };
        candidateNode.roles.add(role);
        candidate.predecessorStateId().ifPresent(parentId -> {
            NodeDraft parent = node(nodes, parentId, SFMHistoryGraphPresentationModel.NodeOrigin.SEARCH);
            EnumSet<SFMHistoryGraphPresentationModel.LegendRole> roles = EnumSet.of(role);
            String sourceId = plan.id() + "/" + candidate.stateId();
            addEdge(edges, new EdgeDraft(
                    compositeId("search", plan.id(), candidate.stateId()),
                    sourceId,
                    parent.id,
                    candidateNode.id,
                    SFMHistoryGraphPresentationModel.EdgeOrigin.SEARCH,
                    SFMHistoryGraphPresentationModel.EdgeCommitment.PROJECTED_CANDIDATE,
                    roles,
                    "Search candidate " + candidate.stateId(),
                    "Search candidate transition from state " + parent.id + " to state "
                            + candidateNode.id + "."
            ));
        });
    }

    private static Set<String> collectAncestry(
            String start,
            Map<String, SFMHistoryGraphContract.StateRevision> states
    ) {
        HashSet<String> answer = new HashSet<>();
        ArrayDeque<String> work = new ArrayDeque<>();
        work.add(start);
        while (!work.isEmpty()) {
            String id = work.removeFirst();
            if (!answer.add(id)) continue;
            SFMHistoryGraphContract.StateRevision state = require(states, id, "history ancestry state");
            work.addAll(state.parentRevisionIds());
        }
        return Set.copyOf(answer);
    }

    private static void addProjectionStatus(
            EnumSet<SFMHistoryGraphPresentationModel.LegendRole> roles,
            SFMHistoryGraphContract.ProjectionStatus status
    ) {
        switch (status) {
            case QUEUED, RUNNING -> roles.add(SFMHistoryGraphPresentationModel.LegendRole.OPEN_FRONTIER);
            case EXTERNAL_BARRIER -> roles.add(SFMHistoryGraphPresentationModel.LegendRole.BARRIER);
            default -> {
            }
        }
    }

    private static int rolePriority(List<SFMHistoryGraphPresentationModel.LegendRole> roles) {
        int priority = Integer.MAX_VALUE;
        for (SFMHistoryGraphPresentationModel.LegendRole role : roles) {
            priority = Math.min(priority, switch (role) {
                case ACTUAL_HEAD, INSTRUCTION_POINTER -> 0;
                case OPEN_FRONTIER -> 1;
                case TARGET -> 2;
                case SELECTED_PROJECTED_SUFFIX -> 3;
                case BARRIER -> 4;
                case COMMITTED_EXECUTED -> 5;
                case RETAINED_ALTERNATIVE -> 6;
                case CLOSED -> 7;
            });
        }
        return priority;
    }

    private static String terminalState(SFMTrajectoryContract.TrajectoryRoute route) {
        if (route.steps().isEmpty()) return route.startStateId();
        return route.steps().get(route.steps().size() - 1).predictedStateId();
    }

    private static SFMTrajectoryContract.TrajectoryRoute requireRoute(
            SFMTrajectoryContract.TrajectoryPlanRevision plan,
            String routeId
    ) {
        return plan.routes().stream()
                .filter(route -> route.id().equals(routeId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown trajectory route " + routeId + " in plan " + plan.id()
                ));
    }

    private static NodeDraft node(
            Map<String, NodeDraft> nodes,
            String id,
            SFMHistoryGraphPresentationModel.NodeOrigin origin
    ) {
        NodeDraft answer = nodes.computeIfAbsent(id, NodeDraft::new);
        answer.origins.add(origin);
        return answer;
    }

    private static void addEdge(Map<String, EdgeDraft> edges, EdgeDraft edge) {
        EdgeDraft previous = edges.putIfAbsent(edge.id, edge);
        if (previous != null) throw new IllegalArgumentException("Duplicate presentation edge: " + edge.id);
    }

    private static String trajectoryEdgeId(String planId, String routeId, String stepId) {
        return compositeId("trajectory", planId, routeId, stepId);
    }

    private static String compositeId(String prefix, String... parts) {
        StringBuilder answer = new StringBuilder(prefix);
        for (String part : parts) {
            Objects.requireNonNull(part, "identity part");
            answer.append(':').append(part.length()).append(':').append(part);
        }
        return answer.toString();
    }

    private static <T> Map<String, T> index(
            List<T> values,
            java.util.function.Function<T, String> id,
            String label
    ) {
        HashMap<String, T> answer = new HashMap<>();
        for (T value : values) {
            T previous = answer.put(id.apply(value), value);
            if (previous != null) throw new IllegalArgumentException("Duplicate " + label + " identity");
        }
        return Map.copyOf(answer);
    }

    private static <T> T require(Map<String, T> values, String id, String label) {
        T answer = values.get(id);
        if (answer == null) throw new IllegalArgumentException("Unknown " + label + ": " + id);
        return answer;
    }

    private static final class NodeDraft {
        private final String id;
        private final EnumSet<SFMHistoryGraphPresentationModel.NodeOrigin> origins = EnumSet.noneOf(
                SFMHistoryGraphPresentationModel.NodeOrigin.class
        );
        private final EnumSet<SFMHistoryGraphPresentationModel.LegendRole> roles = EnumSet.noneOf(
                SFMHistoryGraphPresentationModel.LegendRole.class
        );

        private NodeDraft(String id) {
            this.id = Objects.requireNonNull(id, "node id");
        }

        private SFMHistoryGraphPresentationModel.Node finish() {
            List<SFMHistoryGraphPresentationModel.LegendRole> orderedRoles = List.copyOf(roles);
            String roleNarration = SFMHistoryGraphPresentationModel.describeRoles(orderedRoles);
            return new SFMHistoryGraphPresentationModel.Node(
                    id,
                    List.copyOf(origins),
                    orderedRoles,
                    "State " + id,
                    "State " + id + "." + (roleNarration.isEmpty() ? "" : " " + roleNarration)
            );
        }
    }

    private static final class EdgeDraft {
        private final String id;
        private final String sourceContractId;
        private final String fromNodeId;
        private final String toNodeId;
        private final SFMHistoryGraphPresentationModel.EdgeOrigin origin;
        private final SFMHistoryGraphPresentationModel.EdgeCommitment commitment;
        private final EnumSet<SFMHistoryGraphPresentationModel.LegendRole> roles;
        private final String label;
        private final String baseNarration;

        private EdgeDraft(
                String id,
                String sourceContractId,
                String fromNodeId,
                String toNodeId,
                SFMHistoryGraphPresentationModel.EdgeOrigin origin,
                SFMHistoryGraphPresentationModel.EdgeCommitment commitment,
                EnumSet<SFMHistoryGraphPresentationModel.LegendRole> roles,
                String label,
                String baseNarration
        ) {
            this.id = id;
            this.sourceContractId = sourceContractId;
            this.fromNodeId = fromNodeId;
            this.toNodeId = toNodeId;
            this.origin = origin;
            this.commitment = commitment;
            this.roles = EnumSet.copyOf(roles);
            this.label = label;
            this.baseNarration = baseNarration;
        }

        private SFMHistoryGraphPresentationModel.Edge finish() {
            List<SFMHistoryGraphPresentationModel.LegendRole> orderedRoles = List.copyOf(roles);
            String roleNarration = SFMHistoryGraphPresentationModel.describeRoles(orderedRoles);
            return new SFMHistoryGraphPresentationModel.Edge(
                    id,
                    sourceContractId,
                    fromNodeId,
                    toNodeId,
                    origin,
                    commitment,
                    orderedRoles,
                    label,
                    baseNarration + (roleNarration.isEmpty() ? "" : " " + roleNarration)
            );
        }
    }
}
