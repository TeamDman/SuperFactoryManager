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
        Map<String, SFMHistoryGraphContract.ActionIntent> historyIntents = index(
                history.intents(),
                SFMHistoryGraphContract.ActionIntent::id,
                "history intent"
        );
        Map<String, SFMHistoryGraphContract.ActionEvaluation> historyEvaluations = index(
                history.evaluations(),
                SFMHistoryGraphContract.ActionEvaluation::id,
                "history evaluation"
        );
        Map<String, SFMHistoryGraphContract.ActionOutcome> historyOutcomes = index(
                history.outcomes(),
                SFMHistoryGraphContract.ActionOutcome::id,
                "history outcome"
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

        for (SFMHistoryGraphContract.ActionIntent intent : history.intents()) {
            addIntentNode(nodes, intent);
        }
        for (SFMHistoryGraphContract.ActionEvaluation evaluation : history.evaluations()) {
            addEvaluationNode(nodes, evaluation);
        }
        for (SFMHistoryGraphContract.ActionOutcome outcome : history.outcomes()) {
            addOutcomeNode(nodes, outcome);
        }

        for (SFMHistoryGraphContract.StateRevision state : history.states()) {
            NodeDraft node = node(nodes, state.id(), SFMHistoryGraphPresentationModel.NodeOrigin.HISTORY);
            node.detail("history.committed", Boolean.toString(state.committed()));
            node.detail("history.parents", state.parentRevisionIds().isEmpty()
                    ? "(root)"
                    : String.join(", ", state.parentRevisionIds()));
            node.detail("history.state-hash", state.stateHash());
            node.detail("history.status", state.status().name());
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
                    narration,
                    Map.ofEntries(
                            Map.entry("history.committed", Boolean.toString(edge.committed())),
                            Map.entry("history.effect-class", edge.effectClass().name()),
                            Map.entry("history.evaluation", edge.evaluationId().orElse("(none)")),
                            Map.entry("history.intent", edge.intentId().orElse("(none)")),
                            Map.entry("history.outcome", edge.outcomeId().orElse("(none)")),
                            Map.entry("history.status", edge.status().name())
                    )
            ));
            addSemanticHistoryChain(
                    nodes,
                    edges,
                    edge,
                    roles,
                    historyIntents,
                    historyEvaluations,
                    historyOutcomes
            );
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
                NodeDraft frontierNode = namedNode(
                        nodes,
                        searchStateNodeId(frontier.planRevisionId(), stateId),
                        SFMHistoryGraphPresentationModel.NodeOrigin.SEARCH,
                        "Search state " + stateId,
                        "Search state " + stateId + " in plan " + frontier.planRevisionId() + "."
                );
                frontierNode.roles.add(SFMHistoryGraphPresentationModel.LegendRole.OPEN_FRONTIER);
                frontierNode.detail("search.frontier-expanded", Long.toString(frontier.expanded()));
                frontierNode.detail("search.frontier-generated", Long.toString(frontier.generated()));
                frontierNode.detail("search.frontier-status", frontier.status().name());
                frontierNode.detail("search.plan-revision", frontier.planRevisionId());
                frontierNode.detail("search.state-id", stateId);
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
            boolean executedPrefix = selectedRoute && index < suffixStart;

            EnumSet<SFMHistoryGraphPresentationModel.LegendRole> roles = EnumSet.noneOf(
                    SFMHistoryGraphPresentationModel.LegendRole.class
            );
            String label;
            String narration;
            if (executedPrefix) {
                roles.add(SFMHistoryGraphPresentationModel.LegendRole.SELECTED_EXECUTED_PREFIX);
                parent.roles.add(SFMHistoryGraphPresentationModel.LegendRole.SELECTED_EXECUTED_PREFIX);
                child.roles.add(SFMHistoryGraphPresentationModel.LegendRole.SELECTED_EXECUTED_PREFIX);
                label = "Selected executed step " + step.id();
                narration = "Selected trajectory step " + step.id() + " from state " + parent.id
                        + " to state " + child.id + " is before the machine instruction pointer."
                        + " It is machine progress, not an assertion that this projected edge is materialized history.";
            } else if (selectedRoute) {
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
                    narration,
                    Map.ofEntries(
                            Map.entry("trajectory.action", step.actionIntent().actionId()),
                            Map.entry("trajectory.arguments", step.actionIntent().arguments().isEmpty()
                                    ? "(none)"
                                    : String.join(" ", step.actionIntent().arguments())),
                            Map.entry("trajectory.cost-policy", plan.costPolicy().id() + "@" + plan.costPolicy().revision()),
                            Map.entry("trajectory.effect-class", step.effectClass().name()),
                            Map.entry("trajectory.evaluation-policy", step.evaluationPolicy().name()),
                            Map.entry("trajectory.f", Long.toString(step.estimatedTotalCost())),
                            Map.entry("trajectory.g", Long.toString(step.accumulatedCost())),
                            Map.entry("trajectory.generator", plan.actionGenerator().id() + "@" + plan.actionGenerator().revision()),
                            Map.entry("trajectory.h", Long.toString(step.estimatedRemainingCost())),
                            Map.entry("trajectory.heuristic", plan.heuristic().id() + "@" + plan.heuristic().revision()),
                            Map.entry("trajectory.history-commitment", "NOT_ASSERTED"),
                            Map.entry("trajectory.machine-progress", executedPrefix
                                    ? "EXECUTED_PREFIX"
                                    : selectedRoute ? "PROJECTED_SUFFIX" : "RETAINED_ALTERNATIVE"),
                            Map.entry("trajectory.outcome", step.predictedOutcomeId()),
                            Map.entry("trajectory.plan-revision", plan.id()),
                            Map.entry("trajectory.post-state", step.predictedStateId()),
                            Map.entry("trajectory.pre-state", step.expectedParentStateId()),
                            Map.entry("trajectory.provenance", "trajectory-plan/" + plan.id()),
                            Map.entry("trajectory.route", route.id()),
                            Map.entry("trajectory.route-status", route.status().name()),
                            Map.entry("trajectory.search-algorithm", plan.algorithm().name()),
                            Map.entry("trajectory.step-cost", Long.toString(step.stepCost()))
                    )
            ));
        }
    }

    private static void addSearchCandidate(
            Map<String, NodeDraft> nodes,
            Map<String, EdgeDraft> edges,
            SFMTrajectoryContract.TrajectoryPlanRevision plan,
            SFMTrajectoryContract.SearchCandidate candidate
    ) {
        String candidateNodeId = searchStateNodeId(plan.id(), candidate.stateId());
        NodeDraft candidateNode = namedNode(
                nodes,
                candidateNodeId,
                SFMHistoryGraphPresentationModel.NodeOrigin.SEARCH,
                "Search state " + candidate.stateId(),
                "Search state " + candidate.stateId() + " in plan " + plan.id() + "."
        );
        SFMHistoryGraphPresentationModel.LegendRole role = switch (candidate.status()) {
            case OPEN -> SFMHistoryGraphPresentationModel.LegendRole.OPEN_FRONTIER;
            case CLOSED -> SFMHistoryGraphPresentationModel.LegendRole.CLOSED;
            case BARRIER -> SFMHistoryGraphPresentationModel.LegendRole.BARRIER;
            case PRUNED -> SFMHistoryGraphPresentationModel.LegendRole.RETAINED_ALTERNATIVE;
        };
        candidateNode.roles.add(role);
        candidateNode.detail("search.f", Long.toString(Math.addExact(
                candidate.accumulatedCost(), candidate.estimatedRemainingCost())));
        candidateNode.detail("search.algorithm", plan.algorithm().name());
        candidateNode.detail("search.cost-policy", plan.costPolicy().id() + "@" + plan.costPolicy().revision());
        candidateNode.detail("search.g", Long.toString(candidate.accumulatedCost()));
        candidateNode.detail("search.generator", plan.actionGenerator().id() + "@" + plan.actionGenerator().revision());
        candidateNode.detail("search.h", Long.toString(candidate.estimatedRemainingCost()));
        candidateNode.detail("search.heuristic", plan.heuristic().id() + "@" + plan.heuristic().revision());
        candidateNode.detail("search.plan-revision", plan.id());
        candidateNode.detail("search.predecessor", candidate.predecessorStateId().orElse("(none)"));
        candidateNode.detail("search.reason", candidate.reason());
        candidateNode.detail("search.state-id", candidate.stateId());
        candidateNode.detail("search.status", candidate.status().name());
        candidate.predecessorStateId().ifPresent(parentId -> {
            String parentNodeId = searchStateNodeId(plan.id(), parentId);
            NodeDraft parent = namedNode(
                    nodes,
                    parentNodeId,
                    SFMHistoryGraphPresentationModel.NodeOrigin.SEARCH,
                    "Search state " + parentId,
                    "Search state " + parentId + " in plan " + plan.id() + "."
            );
            parent.detail("search.plan-revision", plan.id());
            parent.detail("search.state-id", parentId);
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
                            + candidateNode.id + ".",
                    Map.ofEntries(
                            Map.entry("search.action-intent", candidate.actionIntentId().orElse("(none)")),
                            Map.entry("search.f", Long.toString(Math.addExact(
                                    candidate.accumulatedCost(), candidate.estimatedRemainingCost()))),
                            Map.entry("search.g", Long.toString(candidate.accumulatedCost())),
                            Map.entry("search.h", Long.toString(candidate.estimatedRemainingCost())),
                            Map.entry("search.plan-revision", plan.id()),
                            Map.entry("search.predecessor-state", parentId),
                            Map.entry("search.reason", candidate.reason()),
                            Map.entry("search.state-id", candidate.stateId()),
                            Map.entry("search.status", candidate.status().name())
                    )
            ));
        });
    }

    private static NodeDraft addIntentNode(
            Map<String, NodeDraft> nodes,
            SFMHistoryGraphContract.ActionIntent intent
    ) {
        NodeDraft node = namedNode(
                nodes,
                semanticNodeId("intent", intent.id()),
                SFMHistoryGraphPresentationModel.NodeOrigin.ACTION_INTENT,
                "Intent " + intent.actionId(),
                "Action intent " + intent.id() + " invokes " + intent.actionId() + "."
        );
        node.detail("intent.action-id", intent.actionId());
        node.detail("intent.argument-count", Integer.toString(intent.arguments().size()));
        for (int index = 0; index < intent.arguments().size(); index++) {
            node.detail(indexedDetailKey("intent.argument", index), intent.arguments().get(index));
        }
        node.detail("intent.arguments", intent.arguments().isEmpty()
                ? "(none)"
                : String.join(" | ", intent.arguments()));
        node.detail("intent.hash", intent.intentHash());
        node.detail("intent.id", intent.id());
        node.detail("intent.query", renderActionQuery(intent));
        node.detail("semantic.kind", "ACTION_INTENT");
        node.detail("semantic.provenance", "history.intents/" + intent.id());
        return node;
    }

    private static NodeDraft addEvaluationNode(
            Map<String, NodeDraft> nodes,
            SFMHistoryGraphContract.ActionEvaluation evaluation
    ) {
        NodeDraft node = namedNode(
                nodes,
                semanticNodeId("evaluation", evaluation.id()),
                SFMHistoryGraphPresentationModel.NodeOrigin.ACTION_EVALUATION,
                "Evaluation " + evaluation.id(),
                "Action evaluation " + evaluation.id() + " uses evaluator "
                        + evaluation.evaluatorRevision() + "."
        );
        node.detail("evaluation.evaluator-revision", evaluation.evaluatorRevision());
        node.detail("evaluation.expected-parent-state", evaluation.expectedParentStateId());
        node.detail("evaluation.id", evaluation.id());
        node.detail("evaluation.intent-id", evaluation.intentId());
        node.detail("evaluation.policy", evaluation.policy().name());
        node.detail("evaluation.predicted-outcome-id", evaluation.predictedOutcomeId());
        node.detail("evaluation.status", evaluation.status().name());
        node.detail("evaluation.witness-count", Integer.toString(evaluation.dependencyWitnesses().size()));
        for (int index = 0; index < evaluation.dependencyWitnesses().size(); index++) {
            SFMHistoryGraphContract.DependencyWitness witness = evaluation.dependencyWitnesses().get(index);
            String prefix = indexedDetailKey("evaluation.witness", index);
            node.detail(prefix + ".identity", witness.identity());
            node.detail(prefix + ".kind", witness.kind());
            node.detail(prefix + ".revision", witness.revision());
        }
        node.detail("semantic.kind", "ACTION_EVALUATION");
        node.detail("semantic.provenance", "history.evaluations/" + evaluation.id());
        return node;
    }

    private static NodeDraft addOutcomeNode(
            Map<String, NodeDraft> nodes,
            SFMHistoryGraphContract.ActionOutcome outcome
    ) {
        NodeDraft node = namedNode(
                nodes,
                semanticNodeId("outcome", outcome.id()),
                SFMHistoryGraphPresentationModel.NodeOrigin.ACTION_OUTCOME,
                "Outcome " + outcome.id(),
                "Action outcome " + outcome.id() + " has status " + outcome.status() + "."
        );
        node.detail("outcome.evaluation-id", outcome.evaluationId());
        node.detail("outcome.evidence-count", Integer.toString(outcome.evidence().size()));
        for (int index = 0; index < outcome.evidence().size(); index++) {
            node.detail(indexedDetailKey("outcome.evidence", index), outcome.evidence().get(index));
        }
        node.detail("outcome.id", outcome.id());
        node.detail("outcome.resulting-state", outcome.resultingStateId().orElse("(none)"));
        node.detail("outcome.status", outcome.status().name());
        node.detail("semantic.kind", "ACTION_OUTCOME");
        node.detail("semantic.provenance", "history.outcomes/" + outcome.id());
        return node;
    }

    private static void addSemanticHistoryChain(
            Map<String, NodeDraft> nodes,
            Map<String, EdgeDraft> edges,
            SFMHistoryGraphContract.BranchEdge branch,
            EnumSet<SFMHistoryGraphPresentationModel.LegendRole> branchRoles,
            Map<String, SFMHistoryGraphContract.ActionIntent> intents,
            Map<String, SFMHistoryGraphContract.ActionEvaluation> evaluations,
            Map<String, SFMHistoryGraphContract.ActionOutcome> outcomes
    ) {
        ArrayList<String> chain = new ArrayList<>();
        chain.add(branch.parentStateRevisionId());
        branch.intentId().ifPresent(id -> {
            addIntentNode(nodes, require(intents, id, "branch-edge intent")).roles.addAll(branchRoles);
            chain.add(semanticNodeId("intent", id));
        });
        branch.evaluationId().ifPresent(id -> {
            addEvaluationNode(nodes, require(evaluations, id, "branch-edge evaluation")).roles.addAll(branchRoles);
            chain.add(semanticNodeId("evaluation", id));
        });
        branch.outcomeId().ifPresent(id -> {
            addOutcomeNode(nodes, require(outcomes, id, "branch-edge outcome")).roles.addAll(branchRoles);
            chain.add(semanticNodeId("outcome", id));
        });
        chain.add(branch.childStateRevisionId());
        if (chain.size() == 2) return;

        for (int index = 0; index + 1 < chain.size(); index++) {
            String from = chain.get(index);
            String to = chain.get(index + 1);
            addEdge(edges, new EdgeDraft(
                    compositeId("semantic", branch.id(), Integer.toString(index)),
                    branch.id(),
                    from,
                    to,
                    SFMHistoryGraphPresentationModel.EdgeOrigin.SEMANTIC,
                    SFMHistoryGraphPresentationModel.EdgeCommitment.SEMANTIC_RELATION,
                    branchRoles,
                    "Semantic history relation " + branch.id(),
                    "Semantic relation " + (index + 1) + " of " + (chain.size() - 1)
                            + " for history edge " + branch.id() + " from " + from + " to " + to + ".",
                    Map.ofEntries(
                            Map.entry("history.committed", Boolean.toString(branch.committed())),
                            Map.entry("history.effect-class", branch.effectClass().name()),
                            Map.entry("history.post-state", branch.childStateRevisionId()),
                            Map.entry("history.pre-state", branch.parentStateRevisionId()),
                            Map.entry("history.status", branch.status().name()),
                            Map.entry("semantic.position", Integer.toString(index)),
                            Map.entry("semantic.provenance", "history.edges/" + branch.id())
                    )
            ));
        }
    }

    private static String semanticNodeId(String kind, String contractId) {
        return compositeId("semantic-" + kind, contractId);
    }

    private static String indexedDetailKey(String prefix, int index) {
        return prefix + "." + String.format(java.util.Locale.ROOT, "%06d", index);
    }

    private static String renderActionQuery(SFMHistoryGraphContract.ActionIntent intent) {
        StringBuilder answer = new StringBuilder(intent.actionId());
        for (String argument : intent.arguments()) {
            answer.append(' ').append('"');
            for (int index = 0; index < argument.length(); index++) {
                char value = argument.charAt(index);
                switch (value) {
                    case '\\' -> answer.append("\\\\");
                    case '"' -> answer.append("\\\"");
                    case '\n' -> answer.append("\\n");
                    case '\r' -> answer.append("\\r");
                    case '\t' -> answer.append("\\t");
                    default -> answer.append(value);
                }
            }
            answer.append('"');
        }
        return answer.toString();
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
                case SELECTED_EXECUTED_PREFIX -> 3;
                case SELECTED_PROJECTED_SUFFIX -> 4;
                case BARRIER -> 5;
                case COMMITTED_EXECUTED -> 6;
                case RETAINED_ALTERNATIVE -> 7;
                case CLOSED -> 8;
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

    private static NodeDraft namedNode(
            Map<String, NodeDraft> nodes,
            String id,
            SFMHistoryGraphPresentationModel.NodeOrigin origin,
            String label,
            String narration
    ) {
        NodeDraft answer = node(nodes, id, origin);
        answer.describe(label, narration);
        return answer;
    }

    private static void addEdge(Map<String, EdgeDraft> edges, EdgeDraft edge) {
        EdgeDraft previous = edges.putIfAbsent(edge.id, edge);
        if (previous != null) throw new IllegalArgumentException("Duplicate presentation edge: " + edge.id);
    }

    private static String trajectoryEdgeId(String planId, String routeId, String stepId) {
        return compositeId("trajectory", planId, routeId, stepId);
    }

    private static String searchStateNodeId(String planId, String stateId) {
        return compositeId("search-state", planId, stateId);
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
        private final java.util.TreeMap<String, String> details = new java.util.TreeMap<>();
        private String label;
        private String baseNarration;

        private NodeDraft(String id) {
            this.id = Objects.requireNonNull(id, "node id");
        }

        private void detail(String key, String value) {
            String previous = details.putIfAbsent(key, value);
            if (previous != null && !previous.equals(value)) {
                throw new IllegalArgumentException("Conflicting node detail " + key + " for " + id);
            }
        }

        private void describe(String label, String narration) {
            Objects.requireNonNull(label, "node label");
            Objects.requireNonNull(narration, "node narration");
            if (this.label != null && (!this.label.equals(label) || !this.baseNarration.equals(narration))) {
                throw new IllegalArgumentException("Conflicting node description for " + id);
            }
            this.label = label;
            this.baseNarration = narration;
        }

        private SFMHistoryGraphPresentationModel.Node finish() {
            List<SFMHistoryGraphPresentationModel.LegendRole> orderedRoles = List.copyOf(roles);
            String roleNarration = SFMHistoryGraphPresentationModel.describeRoles(orderedRoles);
            String finishedLabel = label == null ? "State " + id : label;
            String finishedNarration = baseNarration == null ? "State " + id + "." : baseNarration;
            return new SFMHistoryGraphPresentationModel.Node(
                    id,
                    List.copyOf(origins),
                    orderedRoles,
                    finishedLabel,
                    finishedNarration + (roleNarration.isEmpty() ? "" : " " + roleNarration),
                    details.entrySet().stream()
                            .map(entry -> new SFMHistoryGraphPresentationModel.Detail(entry.getKey(), entry.getValue()))
                            .toList()
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
        private final Map<String, String> details;

        private EdgeDraft(
                String id,
                String sourceContractId,
                String fromNodeId,
                String toNodeId,
                SFMHistoryGraphPresentationModel.EdgeOrigin origin,
                SFMHistoryGraphPresentationModel.EdgeCommitment commitment,
                EnumSet<SFMHistoryGraphPresentationModel.LegendRole> roles,
                String label,
                String baseNarration,
                Map<String, String> details
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
            this.details = Map.copyOf(details);
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
                    baseNarration + (roleNarration.isEmpty() ? "" : " " + roleNarration),
                    details.entrySet().stream()
                            .map(entry -> new SFMHistoryGraphPresentationModel.Detail(entry.getKey(), entry.getValue()))
                            .toList()
            );
        }
    }
}
