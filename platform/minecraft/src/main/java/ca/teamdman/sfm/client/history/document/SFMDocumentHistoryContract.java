package ca.teamdman.sfm.client.history.document;

import ca.teamdman.sfm.client.history.SFMHistoryGraphContract;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Immutable, UI-independent values used by one document-history session.
 *
 * <p>All text coordinates are Unicode code-point coordinates. Framebuffer,
 * camera, panel, and hover state deliberately do not appear in this contract.</p>
 */
public final class SFMDocumentHistoryContract {
    public static final String ARCHIVE_SCHEMA = "sfm.document-history-archive/1";
    public static final String PROJECTION_SCHEMA = "sfm.document-history-projection/1";
    public static final String GROUPING_POLICY_ID = "sfm:document/semantic-grouping";
    public static final String GROUPING_POLICY_V1 = "typing-v1";

    private SFMDocumentHistoryContract() {
    }

    /** Exact runtime history identity. A path is metadata, never this identity. */
    public record SessionIdentity(
            String sessionId,
            String documentId,
            Optional<String> sourceAddress
    ) {
        public SessionIdentity {
            sessionId = requireText(sessionId, "sessionId");
            documentId = requireText(documentId, "documentId");
            Objects.requireNonNull(sourceAddress, "sourceAddress");
            sourceAddress = sourceAddress.map(value -> requireText(value, "sourceAddress"));
        }

        public String qualify(String kind, String token) {
            return sessionId + "/" + requireText(kind, "kind") + "/" + requireText(token, "token");
        }
    }

    /** A redundant logical point whose line/column are validated against its code-point offset. */
    public record LogicalPoint(
            int codePointOffset,
            int lineOneBased,
            int columnCodePointOneBased
    ) {
        public LogicalPoint {
            if (codePointOffset < 0) throw new IllegalArgumentException("codePointOffset must not be negative");
            if (lineOneBased < 1) throw new IllegalArgumentException("lineOneBased must be positive");
            if (columnCodePointOneBased < 1) {
                throw new IllegalArgumentException("columnCodePointOneBased must be positive");
            }
        }

        public static LogicalPoint at(String text, int codePointOffset) {
            text = requireWellFormedUtf16(text, "text");
            int length = codePointLength(text);
            if (codePointOffset < 0 || codePointOffset > length) {
                throw new IllegalArgumentException(
                        "Code-point offset " + codePointOffset + " is outside [0," + length + "]"
                );
            }
            int line = 1;
            int column = 1;
            int offset = 0;
            int charIndex = 0;
            boolean previousCarriageReturn = false;
            while (offset < codePointOffset) {
                int codePoint = text.codePointAt(charIndex);
                if (codePoint == '\r') {
                    line++;
                    column = 1;
                    previousCarriageReturn = true;
                } else if (codePoint == '\n') {
                    if (!previousCarriageReturn) line++;
                    column = 1;
                    previousCarriageReturn = false;
                } else {
                    column++;
                    previousCarriageReturn = false;
                }
                charIndex += Character.charCount(codePoint);
                offset++;
            }
            return new LogicalPoint(codePointOffset, line, column);
        }

        public void validateAgainst(String text) {
            if (!equals(at(text, codePointOffset))) {
                throw new IllegalArgumentException("Logical point line/column disagrees with its code-point offset");
            }
        }

        String canonicalForm() {
            return codePointOffset + ":" + lineOneBased + ":" + columnCodePointOneBased;
        }
    }

    /** One directional caret/selection range in logical document coordinates. */
    public record LogicalSelection(
            String id,
            LogicalPoint anchor,
            LogicalPoint active
    ) {
        public LogicalSelection {
            id = requireText(id, "selection.id");
            Objects.requireNonNull(anchor, "anchor");
            Objects.requireNonNull(active, "active");
        }

        public boolean collapsed() {
            return anchor.codePointOffset() == active.codePointOffset();
        }

        public int startCodePointOffset() {
            return Math.min(anchor.codePointOffset(), active.codePointOffset());
        }

        public int endCodePointOffset() {
            return Math.max(anchor.codePointOffset(), active.codePointOffset());
        }

        public String topologyToken() {
            return id + ":" + (collapsed() ? "caret" : anchor.codePointOffset() <= active.codePointOffset()
                    ? "forward"
                    : "backward");
        }

        String canonicalForm() {
            return id + ":" + anchor.canonicalForm() + ":" + active.canonicalForm();
        }
    }

    /** Exact text plus edit-relevant logical selections/carets. */
    public record DocumentState(
            String text,
            List<LogicalSelection> selections,
            Optional<String> primarySelectionId
    ) {
        public DocumentState {
            String validatedText = requireWellFormedUtf16(text, "text");
            text = validatedText;
            Objects.requireNonNull(selections, "selections");
            ArrayList<LogicalSelection> canonicalSelections = new ArrayList<>(selections);
            canonicalSelections.forEach(selection -> {
                Objects.requireNonNull(selection, "selection");
                selection.anchor().validateAgainst(validatedText);
                selection.active().validateAgainst(validatedText);
            });
            canonicalSelections.sort(Comparator.comparing(LogicalSelection::id));
            for (int index = 1; index < canonicalSelections.size(); index++) {
                if (canonicalSelections.get(index - 1).id().equals(canonicalSelections.get(index).id())) {
                    throw new IllegalArgumentException(
                            "Duplicate logical selection id: " + canonicalSelections.get(index).id()
                    );
                }
            }
            selections = List.copyOf(canonicalSelections);
            Objects.requireNonNull(primarySelectionId, "primarySelectionId");
            primarySelectionId = primarySelectionId.map(value -> requireText(value, "primarySelectionId"));
            if (primarySelectionId.isPresent()) {
                String primary = primarySelectionId.orElseThrow();
                boolean found = false;
                for (LogicalSelection selection : selections) {
                    if (selection.id().equals(primary)) {
                        found = true;
                        break;
                    }
                }
                if (!found) throw new IllegalArgumentException("Primary selection is not present in selections");
            }
        }

        public static DocumentState withoutSelection(String text) {
            return new DocumentState(text, List.of(), Optional.empty());
        }

        public static DocumentState withCaret(String text, int codePointOffset) {
            LogicalPoint caret = LogicalPoint.at(text, codePointOffset);
            return new DocumentState(
                    text,
                    List.of(new LogicalSelection("primary", caret, caret)),
                    Optional.of("primary")
            );
        }

        public String contentHash() {
            return fingerprint("sfm.document-content/1", text);
        }

        public String stateHash() {
            ArrayList<String> parts = new ArrayList<>();
            parts.add("sfm.document-state/1");
            parts.add(text);
            parts.add(primarySelectionId.orElse("primary:none"));
            selections.forEach(selection -> parts.add(selection.canonicalForm()));
            return fingerprint(parts);
        }

        public String selectionTopologyHash() {
            ArrayList<String> parts = new ArrayList<>();
            parts.add("sfm.document-selection-topology/1");
            parts.add(primarySelectionId.orElse("primary:none"));
            selections.forEach(selection -> parts.add(selection.topologyToken()));
            return fingerprint(parts);
        }
    }

    public record DocumentRevision(
            String id,
            Optional<String> parentRevisionId,
            DocumentState state,
            String stateHash,
            long sequence,
            Optional<String> mutationId
    ) {
        public DocumentRevision {
            id = requireText(id, "revision.id");
            Objects.requireNonNull(parentRevisionId, "parentRevisionId");
            parentRevisionId = parentRevisionId.map(value -> requireText(value, "parentRevisionId"));
            Objects.requireNonNull(state, "state");
            stateHash = requireText(stateHash, "stateHash");
            if (sequence < 0) throw new IllegalArgumentException("revision sequence must not be negative");
            Objects.requireNonNull(mutationId, "mutationId");
            mutationId = mutationId.map(value -> requireText(value, "mutationId"));
            if (parentRevisionId.isEmpty() != mutationId.isEmpty()) {
                throw new IllegalArgumentException("Only the root revision may omit both parent and mutation");
            }
        }
    }

    public enum RawEventKind {
        KEY_DOWN,
        KEY_REPEAT,
        KEY_UP,
        KEY_RESET,
        CHARACTER,
        PASTE,
        COMPLETION,
        POINTER,
        FOCUS,
        ACTION
    }

    /** Caller-supplied exact ingress identity and final dispatch disposition. */
    public record RawInput(
            String id,
            long clientTick,
            RawEventKind kind,
            String source,
            String code,
            Optional<String> text,
            int modifiers,
            boolean consumed,
            boolean delivered
    ) {
        public RawInput {
            id = requireText(id, "rawInput.id");
            if (clientTick < 0) throw new IllegalArgumentException("clientTick must not be negative");
            Objects.requireNonNull(kind, "kind");
            source = requireText(source, "source");
            code = requireText(code, "code");
            Objects.requireNonNull(text, "text");
            text = text.map(value -> requireWellFormedUtf16(value, "rawInput.text"));
        }
    }

    public record RawInputEvent(long sequence, RawInput input) {
        public RawInputEvent {
            if (sequence <= 0) throw new IllegalArgumentException("raw event sequence must be positive");
            Objects.requireNonNull(input, "input");
        }

        public String id() {
            return input.id();
        }
    }

    public enum MutationKind {
        TYPE,
        DELETE_BACKWARD,
        DELETE_FORWARD,
        PASTE,
        COMPLETION,
        CARET_CHANGE,
        SELECTION_CHANGE,
        FOCUS_CHANGE,
        ACTION,
        OTHER,
        NO_OP
    }

    public enum EditDirection {
        FORWARD,
        BACKWARD,
        NONE
    }

    public enum MutationStatus {
        APPLIED,
        NO_CHANGE
    }

    public record MutationProvenance(
            String actor,
            String requestId,
            long clientTick,
            String focusId,
            List<String> rawEventIds
    ) {
        public MutationProvenance {
            actor = requireText(actor, "actor");
            requestId = requireText(requestId, "requestId");
            if (clientTick < 0) throw new IllegalArgumentException("clientTick must not be negative");
            focusId = requireText(focusId, "focusId");
            rawEventIds = immutableUniqueText(rawEventIds, "rawEventIds");
        }
    }

    /** Exact graph identities/evidence supplied by a semantic domain adapter. */
    public record GraphIdentity(
            SFMHistoryGraphContract.ActionIntent intent,
            String evaluatorRevision,
            SFMHistoryGraphContract.EvaluationPolicy evaluationPolicy,
            List<SFMHistoryGraphContract.DependencyWitness> dependencyWitnesses,
            String evaluationId,
            String outcomeId,
            String edgeId,
            SFMHistoryGraphContract.EffectClass effectClass,
            List<String> evidence
    ) {
        public GraphIdentity {
            Objects.requireNonNull(intent, "intent");
            evaluatorRevision = requireText(evaluatorRevision, "evaluatorRevision");
            Objects.requireNonNull(evaluationPolicy, "evaluationPolicy");
            Objects.requireNonNull(dependencyWitnesses, "dependencyWitnesses");
            dependencyWitnesses = dependencyWitnesses.stream()
                    .map(value -> Objects.requireNonNull(value, "dependencyWitness"))
                    .sorted(Comparator.comparing(SFMHistoryGraphContract.DependencyWitness::kind)
                            .thenComparing(SFMHistoryGraphContract.DependencyWitness::identity)
                            .thenComparing(SFMHistoryGraphContract.DependencyWitness::revision))
                    .toList();
            evaluationId = requireText(evaluationId, "evaluationId");
            outcomeId = requireText(outcomeId, "outcomeId");
            edgeId = requireText(edgeId, "edgeId");
            Objects.requireNonNull(effectClass, "effectClass");
            evidence = immutableSortedText(evidence, "evidence");
        }
    }

    public record MutationRequest(
            String mutationId,
            MutationKind kind,
            EditDirection direction,
            DocumentState resultingState,
            Optional<String> revisionId,
            Optional<String> stateHash,
            Optional<String> changedText,
            MutationProvenance provenance,
            Optional<GraphIdentity> graphIdentity
    ) {
        public MutationRequest {
            mutationId = requireText(mutationId, "mutationId");
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(direction, "direction");
            Objects.requireNonNull(resultingState, "resultingState");
            Objects.requireNonNull(revisionId, "revisionId");
            revisionId = revisionId.map(value -> requireText(value, "revisionId"));
            Objects.requireNonNull(stateHash, "stateHash");
            stateHash = stateHash.map(value -> requireText(value, "stateHash"));
            Objects.requireNonNull(changedText, "changedText");
            changedText = changedText.map(value -> requireWellFormedUtf16(value, "changedText"));
            Objects.requireNonNull(provenance, "provenance");
            Objects.requireNonNull(graphIdentity, "graphIdentity");
        }
    }

    public record DocumentMutation(
            String id,
            long sequence,
            MutationKind kind,
            EditDirection direction,
            String beforeRevisionId,
            String afterRevisionId,
            Optional<String> changedText,
            MutationProvenance provenance,
            GraphIdentity graphIdentity,
            MutationStatus status
    ) {
        public DocumentMutation {
            id = requireText(id, "mutation.id");
            if (sequence <= 0) throw new IllegalArgumentException("mutation sequence must be positive");
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(direction, "direction");
            beforeRevisionId = requireText(beforeRevisionId, "beforeRevisionId");
            afterRevisionId = requireText(afterRevisionId, "afterRevisionId");
            Objects.requireNonNull(changedText, "changedText");
            changedText = changedText.map(value -> requireWellFormedUtf16(value, "changedText"));
            Objects.requireNonNull(provenance, "provenance");
            Objects.requireNonNull(graphIdentity, "graphIdentity");
            Objects.requireNonNull(status, "status");
            if ((status == MutationStatus.NO_CHANGE) != beforeRevisionId.equals(afterRevisionId)) {
                throw new IllegalArgumentException("Only no-change mutations may retain the same revision");
            }
        }
    }

    public record GroupingPolicy(
            String id,
            String revision,
            long idleBoundTicks,
            boolean groupWordTyping,
            boolean groupDeletionRuns
    ) {
        public GroupingPolicy {
            id = requireText(id, "groupingPolicy.id");
            revision = requireText(revision, "groupingPolicy.revision");
            if (idleBoundTicks < 0) throw new IllegalArgumentException("idleBoundTicks must not be negative");
        }

        public static GroupingPolicy typingV1() {
            return new GroupingPolicy(GROUPING_POLICY_ID, GROUPING_POLICY_V1, 20, true, true);
        }
    }

    public enum SemanticTransactionKind {
        TYPE_WORD,
        TYPE_DELIMITER,
        DELETE_BACKWARD,
        DELETE_FORWARD,
        PASTE,
        COMPLETION,
        CARET_CHANGE,
        SELECTION_CHANGE,
        FOCUS_CHANGE,
        ACTION,
        OTHER,
        NO_OP
    }

    public record SemanticTransaction(
            String id,
            long sequence,
            String policyId,
            String policyRevision,
            SemanticTransactionKind kind,
            String beforeRevisionId,
            String afterRevisionId,
            List<String> mutationIds,
            List<String> rawEventIds,
            List<String> stateRevisionIds,
            String label
    ) {
        public SemanticTransaction {
            id = requireText(id, "transaction.id");
            if (sequence <= 0) throw new IllegalArgumentException("transaction sequence must be positive");
            policyId = requireText(policyId, "policyId");
            policyRevision = requireText(policyRevision, "policyRevision");
            Objects.requireNonNull(kind, "kind");
            beforeRevisionId = requireText(beforeRevisionId, "beforeRevisionId");
            afterRevisionId = requireText(afterRevisionId, "afterRevisionId");
            mutationIds = immutableUniqueText(mutationIds, "mutationIds");
            rawEventIds = immutableUniqueText(rawEventIds, "rawEventIds");
            stateRevisionIds = immutableText(stateRevisionIds, "stateRevisionIds");
            if (mutationIds.isEmpty()) throw new IllegalArgumentException("A semantic transaction needs a mutation");
            if (stateRevisionIds.isEmpty()
                    || !stateRevisionIds.get(0).equals(beforeRevisionId)
                    || !stateRevisionIds.get(stateRevisionIds.size() - 1).equals(afterRevisionId)) {
                throw new IllegalArgumentException("Transaction state chain must span before through after");
            }
            label = requireText(label, "label");
        }
    }

    public record DocumentHeadMovement(
            long sequence,
            SFMHistoryGraphContract.HeadMovement movement,
            List<String> rawEventIds
    ) {
        public DocumentHeadMovement {
            if (sequence <= 0) throw new IllegalArgumentException("head movement sequence must be positive");
            Objects.requireNonNull(movement, "movement");
            rawEventIds = immutableUniqueText(rawEventIds, "rawEventIds");
        }
    }

    public record PreferredRedo(String parentRevisionId, String childRevisionId) {
        public PreferredRedo {
            parentRevisionId = requireText(parentRevisionId, "parentRevisionId");
            childRevisionId = requireText(childRevisionId, "childRevisionId");
        }
    }

    public record Archive(
            String schema,
            SessionIdentity identity,
            String headId,
            String rootRevisionId,
            String currentRevisionId,
            long generation,
            long nextSequence,
            long nextMovementOrdinal,
            List<DocumentRevision> revisions,
            List<RawInputEvent> rawEvents,
            List<DocumentMutation> mutations,
            List<DocumentHeadMovement> headMovements,
            List<SFMHistoryGraphContract.RetentionPin> retentionPins,
            List<PreferredRedo> preferredRedo
    ) {
        public Archive {
            if (!ARCHIVE_SCHEMA.equals(schema)) throw new IllegalArgumentException("Unsupported archive schema " + schema);
            Objects.requireNonNull(identity, "identity");
            headId = requireText(headId, "headId");
            rootRevisionId = requireText(rootRevisionId, "rootRevisionId");
            currentRevisionId = requireText(currentRevisionId, "currentRevisionId");
            if (generation < 0 || nextSequence <= 0 || nextMovementOrdinal <= 0) {
                throw new IllegalArgumentException("Archive counters are invalid");
            }
            revisions = immutableTextValues(revisions, "revisions");
            rawEvents = immutableTextValues(rawEvents, "rawEvents");
            mutations = immutableTextValues(mutations, "mutations");
            headMovements = immutableTextValues(headMovements, "headMovements");
            retentionPins = immutableTextValues(retentionPins, "retentionPins");
            preferredRedo = immutableTextValues(preferredRedo, "preferredRedo").stream()
                    .sorted(Comparator.comparing(PreferredRedo::parentRevisionId)
                            .thenComparing(PreferredRedo::childRevisionId))
                    .toList();
        }

        public String digest() {
            return archiveDigest(this);
        }
    }

    public record Projection(
            String schema,
            SessionIdentity identity,
            long generation,
            String currentRevisionId,
            GroupingPolicy groupingPolicy,
            List<DocumentRevision> revisions,
            List<RawInputEvent> rawEvents,
            List<DocumentMutation> mutations,
            List<SemanticTransaction> semanticTransactions,
            List<DocumentHeadMovement> headMovements,
            List<PreferredRedo> preferredRedo,
            SFMHistoryGraphContract.Graph graph
    ) {
        public Projection {
            if (!PROJECTION_SCHEMA.equals(schema)) {
                throw new IllegalArgumentException("Unsupported projection schema " + schema);
            }
            Objects.requireNonNull(identity, "identity");
            if (generation < 0) throw new IllegalArgumentException("generation must not be negative");
            currentRevisionId = requireText(currentRevisionId, "currentRevisionId");
            Objects.requireNonNull(groupingPolicy, "groupingPolicy");
            revisions = immutableTextValues(revisions, "revisions");
            rawEvents = immutableTextValues(rawEvents, "rawEvents");
            mutations = immutableTextValues(mutations, "mutations");
            semanticTransactions = immutableTextValues(semanticTransactions, "semanticTransactions");
            headMovements = immutableTextValues(headMovements, "headMovements");
            preferredRedo = immutableTextValues(preferredRedo, "preferredRedo").stream()
                    .sorted(Comparator.comparing(PreferredRedo::parentRevisionId)
                            .thenComparing(PreferredRedo::childRevisionId))
                    .toList();
            Objects.requireNonNull(graph, "graph");
        }

        public String digest() {
            return projectionDigest(this);
        }
    }

    public static String requireWellFormedUtf16(String value, String label) {
        Objects.requireNonNull(value, label);
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (Character.isHighSurrogate(current)) {
                if (index + 1 >= value.length() || !Character.isLowSurrogate(value.charAt(index + 1))) {
                    throw new IllegalArgumentException(label + " contains an unpaired high surrogate at UTF-16 index " + index);
                }
                index++;
            } else if (Character.isLowSurrogate(current)) {
                throw new IllegalArgumentException(label + " contains an unpaired low surrogate at UTF-16 index " + index);
            }
        }
        return value;
    }

    public static int charIndexAtCodePoint(String text, int codePointOffset) {
        text = requireWellFormedUtf16(text, "text");
        int length = codePointLength(text);
        if (codePointOffset < 0 || codePointOffset > length) {
            throw new IllegalArgumentException(
                    "Code-point offset " + codePointOffset + " is outside [0," + length + "]"
            );
        }
        return text.offsetByCodePoints(0, codePointOffset);
    }

    public static int codePointLength(String text) {
        text = requireWellFormedUtf16(text, "text");
        return text.codePointCount(0, text.length());
    }

    public static String fingerprint(String... parts) {
        return fingerprint(List.of(parts));
    }

    public static String fingerprint(List<String> parts) {
        Objects.requireNonNull(parts, "parts");
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by the Java runtime", e);
        }
        for (String part : parts) {
            part = requireWellFormedUtf16(part, "fingerprint part");
            byte[] bytes = part.getBytes(StandardCharsets.UTF_8);
            digest.update((byte) (bytes.length >>> 24));
            digest.update((byte) (bytes.length >>> 16));
            digest.update((byte) (bytes.length >>> 8));
            digest.update((byte) bytes.length);
            digest.update(bytes);
        }
        return "sha256:" + HexFormat.of().formatHex(digest.digest());
    }

    private static String archiveDigest(Archive archive) {
        ArrayList<String> parts = commonDigestParts(
                archive.schema(), archive.identity(), archive.generation(), archive.currentRevisionId());
        parts.add(archive.headId());
        parts.add(archive.rootRevisionId());
        parts.add(Long.toString(archive.nextSequence()));
        parts.add(Long.toString(archive.nextMovementOrdinal()));
        appendValues(parts, archive.revisions());
        appendValues(parts, archive.rawEvents());
        appendValues(parts, archive.mutations());
        appendValues(parts, archive.headMovements());
        appendValues(parts, archive.retentionPins());
        appendValues(parts, archive.preferredRedo());
        return fingerprint(parts);
    }

    private static String projectionDigest(Projection projection) {
        ArrayList<String> parts = commonDigestParts(
                projection.schema(), projection.identity(), projection.generation(), projection.currentRevisionId());
        parts.add(projection.groupingPolicy().toString());
        appendValues(parts, projection.revisions());
        appendValues(parts, projection.rawEvents());
        appendValues(parts, projection.mutations());
        appendValues(parts, projection.semanticTransactions());
        appendValues(parts, projection.headMovements());
        appendValues(parts, projection.preferredRedo());
        parts.add(projection.graph().toString());
        return fingerprint(parts);
    }

    private static ArrayList<String> commonDigestParts(
            String schema,
            SessionIdentity identity,
            long generation,
            String currentRevisionId
    ) {
        ArrayList<String> parts = new ArrayList<>();
        parts.add(schema);
        parts.add(identity.sessionId());
        parts.add(identity.documentId());
        parts.add(identity.sourceAddress().orElse("source:none"));
        parts.add(Long.toString(generation));
        parts.add(currentRevisionId);
        return parts;
    }

    private static void appendValues(List<String> parts, List<?> values) {
        parts.add(Integer.toString(values.size()));
        values.forEach(value -> parts.add(value.toString()));
    }

    private static String requireText(String value, String label) {
        value = requireWellFormedUtf16(value, label);
        if (value.isEmpty()) throw new IllegalArgumentException(label + " must not be empty");
        return value;
    }

    private static List<String> immutableText(List<String> values, String label) {
        Objects.requireNonNull(values, label);
        return values.stream().map(value -> requireText(value, label)).toList();
    }

    private static List<String> immutableUniqueText(List<String> values, String label) {
        List<String> result = immutableText(values, label);
        if (new LinkedHashSet<>(result).size() != result.size()) {
            throw new IllegalArgumentException(label + " must not contain duplicates");
        }
        return result;
    }

    private static List<String> immutableSortedText(List<String> values, String label) {
        return immutableText(values, label).stream().sorted().toList();
    }

    private static <T> List<T> immutableTextValues(List<T> values, String label) {
        Objects.requireNonNull(values, label);
        return values.stream().map(value -> Objects.requireNonNull(value, label + " item")).toList();
    }

    static Map<String, String> canonicalMap(Map<String, String> values, String label) {
        Objects.requireNonNull(values, label);
        TreeMap<String, String> answer = new TreeMap<>();
        values.forEach((key, value) -> answer.put(requireText(key, label + " key"), requireText(value, label + " value")));
        return Map.copyOf(answer);
    }
}
