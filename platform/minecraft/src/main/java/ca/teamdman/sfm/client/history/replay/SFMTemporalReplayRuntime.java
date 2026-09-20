package ca.teamdman.sfm.client.history.replay;

import ca.teamdman.sfm.client.history.replay.SFMTemporalReplayArchive.ActionInvocation;
import ca.teamdman.sfm.client.history.replay.SFMTemporalReplayArchive.Archive;
import ca.teamdman.sfm.client.history.replay.SFMTemporalReplayArchive.BindingDecision;
import ca.teamdman.sfm.client.history.replay.SFMTemporalReplayArchive.BindingSnapshot;
import ca.teamdman.sfm.client.history.replay.SFMTemporalReplayArchive.Frame;
import ca.teamdman.sfm.client.history.replay.SFMTemporalReplayArchive.HeadMovement;
import ca.teamdman.sfm.client.history.replay.SFMTemporalReplayArchive.Observation;
import ca.teamdman.sfm.client.history.replay.SFMTemporalReplayArchive.ReplayReport;
import ca.teamdman.sfm.client.history.replay.SFMTemporalReplayArchive.SelectionExpression;
import ca.teamdman.sfm.client.history.replay.SFMTemporalReplayArchive.SelectionWitness;
import ca.teamdman.sfm.client.history.replay.SFMTemporalReplayArchive.SemanticTransition;
import ca.teamdman.sfm.client.history.replay.SFMTemporalReplayArchive.SourceEvent;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.TreeMap;
import java.util.function.Function;

/**
 * Immutable, Java-local random-access view of one temporal replay archive.
 *
 * <p>Restoration constructs a fresh {@link Archive}, so the archive's strict
 * schema, bounds, hashes, and references are revalidated at the import
 * boundary. The runtime then indexes every top-level record without changing
 * the canonical archive representation.</p>
 */
public final class SFMTemporalReplayRuntime {
    private final Archive archive;
    private final String canonicalJson;
    private final Map<String, BindingSnapshot> bindingSnapshots;
    private final Map<String, SourceEvent> sourceEvents;
    private final Map<String, BindingDecision> bindingDecisions;
    private final Map<String, ActionInvocation> invocations;
    private final Map<String, SelectionExpression> selectionExpressions;
    private final Map<String, SelectionWitness> selectionWitnesses;
    private final Map<String, Frame> frames;
    private final Map<String, SemanticTransition> transitions;
    private final Map<String, Observation> observations;
    private final Map<String, HeadMovement> headMovements;
    private final Map<String, ReplayReport> replayReports;
    private final Map<RecordAddress, AddressedRecord> addressedRecords;
    private final NavigableMap<Long, AddressedRecord> causalRecordsBySequence;
    private final List<AddressedRecord> causalTimeline;
    private final FrameGraph frameGraph;

    private SFMTemporalReplayRuntime(Archive source) {
        String sourceCanonicalJson = SFMTemporalReplayArchiveJsonCodec.write(source);
        archive = freshArchive(source);
        canonicalJson = SFMTemporalReplayArchiveJsonCodec.write(archive);
        if (!sourceCanonicalJson.equals(canonicalJson)) {
            throw new IllegalArgumentException("Restoration changed the canonical replay archive");
        }

        bindingSnapshots = index(archive.bindingSnapshots(), BindingSnapshot::id);
        sourceEvents = index(archive.sourceEvents(), SourceEvent::id);
        bindingDecisions = index(archive.bindingDecisions(), BindingDecision::id);
        invocations = index(archive.invocations(), ActionInvocation::id);
        selectionExpressions = index(archive.selectionExpressions(), SelectionExpression::id);
        selectionWitnesses = index(archive.selectionWitnesses(), SelectionWitness::id);
        frames = index(archive.frames(), Frame::stateId);
        transitions = index(archive.transitions(), SemanticTransition::id);
        observations = index(archive.observations(), Observation::id);
        headMovements = index(archive.headMovements(), HeadMovement::id);
        replayReports = index(archive.replayReports(), ReplayReport::id);

        LinkedHashMap<RecordAddress, AddressedRecord> records = new LinkedHashMap<>();
        TreeMap<Long, AddressedRecord> sequenced = new TreeMap<>();
        registerAll(records, sequenced, RecordKind.BINDING_SNAPSHOT,
                archive.bindingSnapshots(), BindingSnapshot::id, ignored -> OptionalLong.empty());
        registerAll(records, sequenced, RecordKind.SOURCE_EVENT,
                archive.sourceEvents(), SourceEvent::id, event -> OptionalLong.of(event.sequence()));
        registerAll(records, sequenced, RecordKind.BINDING_DECISION,
                archive.bindingDecisions(), BindingDecision::id, decision -> OptionalLong.of(decision.sequence()));
        registerAll(records, sequenced, RecordKind.ACTION_INVOCATION,
                archive.invocations(), ActionInvocation::id, invocation -> OptionalLong.of(invocation.sequence()));
        registerAll(records, sequenced, RecordKind.SELECTION_EXPRESSION,
                archive.selectionExpressions(), SelectionExpression::id, ignored -> OptionalLong.empty());
        registerAll(records, sequenced, RecordKind.SELECTION_WITNESS,
                archive.selectionWitnesses(), SelectionWitness::id, ignored -> OptionalLong.empty());
        registerAll(records, sequenced, RecordKind.FRAME,
                archive.frames(), Frame::stateId, ignored -> OptionalLong.empty());
        registerAll(records, sequenced, RecordKind.SEMANTIC_TRANSITION,
                archive.transitions(), SemanticTransition::id, transition -> OptionalLong.of(transition.sequence()));
        registerAll(records, sequenced, RecordKind.OBSERVATION,
                archive.observations(), Observation::id, observation -> OptionalLong.of(observation.sequence()));
        registerAll(records, sequenced, RecordKind.HEAD_MOVEMENT,
                archive.headMovements(), HeadMovement::id, movement -> OptionalLong.of(movement.sequence()));
        registerAll(records, sequenced, RecordKind.REPLAY_REPORT,
                archive.replayReports(), ReplayReport::id, report -> OptionalLong.of(report.sequence()));
        addressedRecords = Collections.unmodifiableMap(records);
        causalRecordsBySequence = Collections.unmodifiableNavigableMap(sequenced);
        causalTimeline = List.copyOf(sequenced.values());
        frameGraph = FrameGraph.restore(frames, archive.currentStateId());
    }

    /** Restores and revalidates a previously constructed archive. */
    public static SFMTemporalReplayRuntime restore(Archive archive) {
        return new SFMTemporalReplayRuntime(Objects.requireNonNull(archive, "archive"));
    }

    /** Strictly decodes canonical JSON before restoring it into a fresh runtime. */
    public static SFMTemporalReplayRuntime restoreCanonical(String json) {
        return restore(SFMTemporalReplayArchiveJsonCodec.read(Objects.requireNonNull(json, "json")));
    }

    public Archive archive() {
        return archive;
    }

    public String encodeCanonical() {
        return canonicalJson;
    }

    public byte[] encodeCanonicalUtf8() {
        return canonicalJson.getBytes(StandardCharsets.UTF_8);
    }

    public FrameGraph frameGraph() {
        return frameGraph;
    }

    public Frame head() {
        return frameGraph.head();
    }

    public Optional<Frame> frame(String stateId) {
        return Optional.ofNullable(frames.get(requireId(stateId, "stateId")));
    }

    public Frame requireFrame(String stateId) {
        return frame(stateId).orElseThrow(() -> new IllegalArgumentException("Unknown replay frame " + stateId));
    }

    public Optional<AddressedRecord> record(RecordAddress address) {
        return Optional.ofNullable(addressedRecords.get(Objects.requireNonNull(address, "address")));
    }

    public Optional<AddressedRecord> record(RecordKind kind, String id) {
        return record(new RecordAddress(kind, id));
    }

    public AddressedRecord requireRecord(RecordAddress address) {
        return record(address).orElseThrow(() -> new IllegalArgumentException("Unknown replay record " + address));
    }

    public AddressedRecord requireRecord(RecordKind kind, String id) {
        return requireRecord(new RecordAddress(kind, id));
    }

    public Optional<AddressedRecord> causalRecord(long logicalSequence) {
        if (logicalSequence <= 0) throw new IllegalArgumentException("Logical sequence must be positive");
        return Optional.ofNullable(causalRecordsBySequence.get(logicalSequence));
    }

    public AddressedRecord requireCausalRecord(long logicalSequence) {
        return causalRecord(logicalSequence).orElseThrow(() ->
                new IllegalArgumentException("Unknown replay logical sequence " + logicalSequence));
    }

    public Map<String, BindingSnapshot> bindingSnapshots() {
        return bindingSnapshots;
    }

    public Map<String, SourceEvent> sourceEvents() {
        return sourceEvents;
    }

    public Map<String, BindingDecision> bindingDecisions() {
        return bindingDecisions;
    }

    public Map<String, ActionInvocation> invocations() {
        return invocations;
    }

    public Map<String, SelectionExpression> selectionExpressions() {
        return selectionExpressions;
    }

    public Map<String, SelectionWitness> selectionWitnesses() {
        return selectionWitnesses;
    }

    public Map<String, Frame> frames() {
        return frames;
    }

    public Map<String, SemanticTransition> transitions() {
        return transitions;
    }

    public Map<String, Observation> observations() {
        return observations;
    }

    public Map<String, HeadMovement> headMovements() {
        return headMovements;
    }

    public Map<String, ReplayReport> replayReports() {
        return replayReports;
    }

    public Map<RecordAddress, AddressedRecord> addressedRecords() {
        return addressedRecords;
    }

    public NavigableMap<Long, AddressedRecord> causalRecordsBySequence() {
        return causalRecordsBySequence;
    }

    public List<AddressedRecord> causalTimeline() {
        return causalTimeline;
    }

    private static Archive freshArchive(Archive source) {
        return new Archive(
                source.schema(),
                source.episodeId(),
                source.documentId(),
                source.generation(),
                source.bindingSnapshots(),
                source.sourceEvents(),
                source.bindingDecisions(),
                source.invocations(),
                source.selectionExpressions(),
                source.selectionWitnesses(),
                source.frames(),
                source.transitions(),
                source.observations(),
                source.headMovements(),
                source.replayReports(),
                source.currentStateId()
        );
    }

    private static <T> Map<String, T> index(List<T> values, Function<T, String> id) {
        LinkedHashMap<String, T> answer = new LinkedHashMap<>();
        for (T value : values) {
            String identity = requireId(id.apply(value), "record id");
            if (answer.put(identity, value) != null) {
                throw new IllegalArgumentException("Duplicate replay record id " + identity);
            }
        }
        return Collections.unmodifiableMap(answer);
    }

    private static <T> void registerAll(
            Map<RecordAddress, AddressedRecord> records,
            Map<Long, AddressedRecord> sequenced,
            RecordKind kind,
            List<T> values,
            Function<T, String> id,
            Function<T, OptionalLong> sequence
    ) {
        for (T value : values) {
            RecordAddress address = new RecordAddress(kind, id.apply(value));
            AddressedRecord record = new AddressedRecord(address, value, sequence.apply(value));
            if (records.put(address, record) != null) {
                throw new IllegalArgumentException("Duplicate replay record address " + address);
            }
            if (record.logicalSequence().isPresent()
                    && sequenced.put(record.logicalSequence().getAsLong(), record) != null) {
                throw new IllegalArgumentException(
                        "Duplicate replay logical sequence " + record.logicalSequence().getAsLong()
                );
            }
        }
    }

    private static String requireId(String value, String label) {
        Objects.requireNonNull(value, label);
        if (value.isEmpty()) throw new IllegalArgumentException(label + " must not be empty");
        return value;
    }

    public enum RecordKind {
        BINDING_SNAPSHOT(BindingSnapshot.class, false),
        SOURCE_EVENT(SourceEvent.class, true),
        BINDING_DECISION(BindingDecision.class, true),
        ACTION_INVOCATION(ActionInvocation.class, true),
        SELECTION_EXPRESSION(SelectionExpression.class, false),
        SELECTION_WITNESS(SelectionWitness.class, false),
        FRAME(Frame.class, false),
        SEMANTIC_TRANSITION(SemanticTransition.class, true),
        OBSERVATION(Observation.class, true),
        HEAD_MOVEMENT(HeadMovement.class, true),
        REPLAY_REPORT(ReplayReport.class, true);

        private final Class<?> valueType;
        private final boolean sequenced;

        RecordKind(Class<?> valueType, boolean sequenced) {
            this.valueType = valueType;
            this.sequenced = sequenced;
        }

        public Class<?> valueType() {
            return valueType;
        }

        public boolean sequenced() {
            return sequenced;
        }
    }

    public record RecordAddress(RecordKind kind, String id) {
        public RecordAddress {
            Objects.requireNonNull(kind, "kind");
            id = requireId(id, "record address id");
        }
    }

    public record AddressedRecord(
            RecordAddress address,
            Object value,
            OptionalLong logicalSequence
    ) {
        public AddressedRecord {
            Objects.requireNonNull(address, "address");
            Objects.requireNonNull(value, "value");
            Objects.requireNonNull(logicalSequence, "logicalSequence");
            if (!address.kind().valueType().isInstance(value)) {
                throw new IllegalArgumentException(
                        address.kind() + " cannot address " + value.getClass().getName()
                );
            }
            if (logicalSequence.isPresent() != address.kind().sequenced()) {
                throw new IllegalArgumentException(
                        address.kind() + " logical-sequence presence does not match its record contract"
                );
            }
            if (logicalSequence.isPresent() && logicalSequence.getAsLong() <= 0) {
                throw new IllegalArgumentException("Logical sequence must be positive");
            }
        }

        public <T> T requireValue(Class<T> type) {
            Objects.requireNonNull(type, "type");
            if (!type.isInstance(value)) {
                throw new IllegalArgumentException(
                        "Replay record " + address + " is not a " + type.getName()
                );
            }
            return type.cast(value);
        }
    }

    /** Immutable parent/child frame forest and its currently selected head. */
    public static final class FrameGraph {
        private final Map<String, Frame> frames;
        private final Map<String, Optional<String>> parentStateIds;
        private final Map<String, List<String>> childStateIds;
        private final List<String> rootStateIds;
        private final String headStateId;

        private FrameGraph(
                Map<String, Frame> frames,
                Map<String, Optional<String>> parentStateIds,
                Map<String, List<String>> childStateIds,
                List<String> rootStateIds,
                String headStateId
        ) {
            this.frames = frames;
            this.parentStateIds = parentStateIds;
            this.childStateIds = childStateIds;
            this.rootStateIds = rootStateIds;
            this.headStateId = headStateId;
        }

        private static FrameGraph restore(Map<String, Frame> frames, String headStateId) {
            requireId(headStateId, "headStateId");
            if (!frames.containsKey(headStateId)) {
                throw new IllegalArgumentException("Unknown replay head frame " + headStateId);
            }

            LinkedHashMap<String, Optional<String>> parents = new LinkedHashMap<>();
            LinkedHashMap<String, ArrayList<String>> mutableChildren = new LinkedHashMap<>();
            ArrayList<String> roots = new ArrayList<>();
            for (String stateId : frames.keySet()) mutableChildren.put(stateId, new ArrayList<>());
            for (Frame frame : frames.values()) {
                parents.put(frame.stateId(), frame.parentStateId());
                if (frame.parentStateId().isPresent()) {
                    String parentStateId = frame.parentStateId().orElseThrow();
                    ArrayList<String> children = mutableChildren.get(parentStateId);
                    if (children == null) {
                        throw new IllegalArgumentException("Unknown replay frame parent " + parentStateId);
                    }
                    children.add(frame.stateId());
                } else {
                    roots.add(frame.stateId());
                }
            }
            validateAcyclic(parents);

            LinkedHashMap<String, List<String>> children = new LinkedHashMap<>();
            for (Map.Entry<String, ArrayList<String>> entry : mutableChildren.entrySet()) {
                entry.getValue().sort(String::compareTo);
                children.put(entry.getKey(), List.copyOf(entry.getValue()));
            }
            roots.sort(String::compareTo);
            return new FrameGraph(
                    frames,
                    Collections.unmodifiableMap(parents),
                    Collections.unmodifiableMap(children),
                    List.copyOf(roots),
                    headStateId
            );
        }

        public Map<String, Frame> frames() {
            return frames;
        }

        public Map<String, Optional<String>> parentStateIds() {
            return parentStateIds;
        }

        public Map<String, List<String>> childStateIds() {
            return childStateIds;
        }

        public List<String> rootStateIds() {
            return rootStateIds;
        }

        public String headStateId() {
            return headStateId;
        }

        public Frame head() {
            return requireFrame(headStateId);
        }

        public Frame requireFrame(String stateId) {
            Frame frame = frames.get(requireId(stateId, "stateId"));
            if (frame == null) throw new IllegalArgumentException("Unknown replay frame " + stateId);
            return frame;
        }

        public Optional<Frame> parent(String stateId) {
            requireFrame(stateId);
            return parentStateIds.get(stateId).map(this::requireFrame);
        }

        public List<Frame> children(String stateId) {
            requireFrame(stateId);
            return childStateIds.get(stateId).stream().map(this::requireFrame).toList();
        }

        private static void validateAcyclic(Map<String, Optional<String>> parents) {
            HashMap<String, VisitState> visits = new HashMap<>();
            for (String initial : parents.keySet()) {
                if (visits.get(initial) == VisitState.COMPLETE) continue;
                ArrayList<String> path = new ArrayList<>();
                String cursor = initial;
                while (true) {
                    VisitState state = visits.get(cursor);
                    if (state == VisitState.COMPLETE) break;
                    if (state == VisitState.VISITING) {
                        throw new IllegalArgumentException("Replay frame parent graph contains a cycle at " + cursor);
                    }
                    visits.put(cursor, VisitState.VISITING);
                    path.add(cursor);
                    Optional<String> parent = parents.get(cursor);
                    if (parent == null) {
                        throw new IllegalArgumentException("Unknown replay frame in parent graph " + cursor);
                    }
                    if (parent.isEmpty()) break;
                    cursor = parent.orElseThrow();
                }
                for (String stateId : path) visits.put(stateId, VisitState.COMPLETE);
            }
        }

        private enum VisitState {
            VISITING,
            COMPLETE
        }
    }
}
