package ca.teamdman.sfm.client.history;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Immutable, read-only addresses and frames for scrubbing a projected route. */
public final class SFMCandidateHistoryContract {
    public static final String SCHEMA = "sfm.candidate-history/1";

    private SFMCandidateHistoryContract() {
    }

    /**
     * A pinned coordinate in one immutable trajectory plan and route.
     * Position zero is the route start; position N is the state after step N.
     */
    public record CandidateFrameAddress(
            String trajectoryPlanRevisionId,
            String routeId,
            int routeStepPosition,
            Optional<String> trajectoryStepId,
            String predictedStateId,
            Optional<String> predictedStateHash,
            SFMHistoryGraphContract.ProjectionStatus projectionStatus,
            Optional<String> evaluatorRevision,
            List<EvaluatorEvidence> evaluatorEvidence
    ) {
        public CandidateFrameAddress {
            trajectoryPlanRevisionId = requireText(
                    trajectoryPlanRevisionId,
                    "candidateFrameAddress.trajectoryPlanRevisionId"
            );
            routeId = requireText(routeId, "candidateFrameAddress.routeId");
            if (routeStepPosition < 0) {
                throw new IllegalArgumentException("Candidate route-step position must not be negative");
            }
            trajectoryStepId = optionalText(trajectoryStepId, "candidateFrameAddress.trajectoryStepId");
            predictedStateId = requireText(predictedStateId, "candidateFrameAddress.predictedStateId");
            predictedStateHash = optionalText(
                    predictedStateHash,
                    "candidateFrameAddress.predictedStateHash"
            );
            Objects.requireNonNull(projectionStatus, "projectionStatus");
            evaluatorRevision = optionalText(evaluatorRevision, "candidateFrameAddress.evaluatorRevision");
            Objects.requireNonNull(evaluatorEvidence, "evaluatorEvidence");
            evaluatorEvidence = evaluatorEvidence.stream()
                    .map(value -> Objects.requireNonNull(value, "evaluator evidence"))
                    .sorted(Comparator.comparing(EvaluatorEvidence::key))
                    .toList();
            HashSet<String> keys = new HashSet<>();
            evaluatorEvidence.forEach(value -> {
                if (!keys.add(value.key())) {
                    throw new IllegalArgumentException("Duplicate evaluator-evidence key " + value.key());
                }
            });
            if (routeStepPosition == 0 && trajectoryStepId.isPresent()) {
                throw new IllegalArgumentException("The route-start frame must not name a trajectory step");
            }
            if (routeStepPosition > 0 && trajectoryStepId.isEmpty()) {
                throw new IllegalArgumentException("A post-step candidate frame must name its trajectory step");
            }
            if (projectionStatus == SFMHistoryGraphContract.ProjectionStatus.MATERIALIZED
                    && predictedStateHash.isEmpty()) {
                throw new IllegalArgumentException("A materialized candidate frame requires a predicted state hash");
            }
        }

        public String canonical() {
            return trajectoryPlanRevisionId + "#" + routeId + "@" + routeStepPosition
                    + "?state=" + predictedStateId
                    + "&status=" + projectionStatus.name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    public record EvaluatorEvidence(String key, String value) {
        public EvaluatorEvidence {
            key = requireText(key, "evaluatorEvidence.key");
            value = requireText(value, "evaluatorEvidence.value");
        }
    }

    /** Domain payload for the first candidate-history integration: a text document frame. */
    public record CandidateDocument(
            String documentId,
            String text,
            String stateHash,
            int selectionRegionCount
    ) {
        public CandidateDocument {
            documentId = requireText(documentId, "candidateDocument.documentId");
            Objects.requireNonNull(text, "text");
            stateHash = requireText(stateHash, "candidateDocument.stateHash");
            if (selectionRegionCount < 0) {
                throw new IllegalArgumentException("selectionRegionCount must not be negative");
            }
        }
    }

    public record CandidateFrame(
            CandidateFrameAddress address,
            Optional<CandidateDocument> document,
            Optional<String> actionIntentId,
            Optional<String> lastTrustworthyPredecessorStateId,
            String statusNarration
    ) {
        public CandidateFrame {
            Objects.requireNonNull(address, "address");
            Objects.requireNonNull(document, "document");
            actionIntentId = optionalText(actionIntentId, "candidateFrame.actionIntentId");
            lastTrustworthyPredecessorStateId = optionalText(
                    lastTrustworthyPredecessorStateId,
                    "candidateFrame.lastTrustworthyPredecessorStateId"
            );
            statusNarration = requireText(statusNarration, "candidateFrame.statusNarration");
            if (address.projectionStatus() == SFMHistoryGraphContract.ProjectionStatus.MATERIALIZED) {
                CandidateDocument materialized = document.orElseThrow(() -> new IllegalArgumentException(
                        "A materialized candidate frame requires document bytes"));
                if (!address.predictedStateHash().orElseThrow().equals(materialized.stateHash())) {
                    throw new IllegalArgumentException("Candidate address and materialized document hash disagree");
                }
            } else if (document.isPresent()) {
                throw new IllegalArgumentException(
                        "Unavailable candidate frames must render a predecessor, not fabricated document bytes"
                );
            }
        }
    }

    public record CandidateRouteProjection(
            String schema,
            String machineId,
            long machineRevision,
            String actualHistoryHeadId,
            Optional<String> selectedPlanRevisionId,
            Optional<SFMTrajectoryContract.InstructionPointer> instructionPointer,
            String trajectoryPlanRevisionId,
            String routeId,
            List<CandidateFrame> frames
    ) {
        public CandidateRouteProjection {
            if (!SCHEMA.equals(schema)) {
                throw new IllegalArgumentException("Unsupported candidate-history schema " + schema);
            }
            machineId = requireText(machineId, "candidateRoute.machineId");
            if (machineRevision < 0) throw new IllegalArgumentException("machineRevision must not be negative");
            actualHistoryHeadId = requireText(actualHistoryHeadId, "candidateRoute.actualHistoryHeadId");
            selectedPlanRevisionId = optionalText(
                    selectedPlanRevisionId,
                    "candidateRoute.selectedPlanRevisionId"
            );
            Objects.requireNonNull(instructionPointer, "instructionPointer");
            trajectoryPlanRevisionId = requireText(
                    trajectoryPlanRevisionId,
                    "candidateRoute.trajectoryPlanRevisionId"
            );
            routeId = requireText(routeId, "candidateRoute.routeId");
            Objects.requireNonNull(frames, "frames");
            frames = List.copyOf(frames);
            if (frames.isEmpty()) throw new IllegalArgumentException("Candidate route requires its start frame");
            ArrayList<String> addresses = new ArrayList<>();
            for (int index = 0; index < frames.size(); index++) {
                CandidateFrame frame = Objects.requireNonNull(frames.get(index), "candidate frame");
                CandidateFrameAddress address = frame.address();
                if (!trajectoryPlanRevisionId.equals(address.trajectoryPlanRevisionId())
                        || !routeId.equals(address.routeId())) {
                    throw new IllegalArgumentException("Candidate frame belongs to another plan or route");
                }
                if (address.routeStepPosition() != index) {
                    throw new IllegalArgumentException("Candidate frame positions must be contiguous from zero");
                }
                addresses.add(address.canonical());
            }
            if (new HashSet<>(addresses).size() != addresses.size()) {
                throw new IllegalArgumentException("Candidate frame addresses must be unique");
            }
        }

        public CandidateFrame frame(int position) {
            if (position < 0 || position >= frames.size()) {
                throw new IllegalArgumentException("Candidate frame position is outside this route");
            }
            return frames.get(position);
        }

        public int lastPosition() {
            return frames.size() - 1;
        }
    }

    private static Optional<String> optionalText(Optional<String> value, String label) {
        Objects.requireNonNull(value, label);
        return value.map(item -> requireText(item, label));
    }

    private static String requireText(String value, String label) {
        Objects.requireNonNull(value, label);
        if (value.isBlank()) throw new IllegalArgumentException(label + " must not be blank");
        return value;
    }
}
