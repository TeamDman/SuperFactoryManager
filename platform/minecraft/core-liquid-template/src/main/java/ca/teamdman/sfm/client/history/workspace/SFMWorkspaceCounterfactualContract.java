package ca.teamdman.sfm.client.history.workspace;

import ca.teamdman.sfm.client.history.SFMHistoryGraphContract;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * Versioned, bounded, Java-local interchange contract for the X5 whole-workspace counterfactual fixture.
 *
 * <p>Every workspace frame is a complete in-memory value. The contract deliberately stores full document text so a
 * fresh controller can restore and validate the artifact without consulting the ambient checkout.</p>
 */
public final class SFMWorkspaceCounterfactualContract {
    public static final String SCHEMA = "sfm.workspace-counterfactual/1";
    public static final int MAX_RECORDS_PER_KIND = 65_536;
    public static final int MAX_DOCUMENTS_PER_FRAME = 4_096;
    public static final int MAX_DOCUMENT_UTF8_BYTES = 4 * 1024 * 1024;
    public static final int MAX_TOTAL_DOCUMENT_UTF8_BYTES = 32 * 1024 * 1024;
    public static final int MAX_IDENTIFIER_UTF8_BYTES = 16 * 1024;
    public static final int MAX_METADATA_UTF8_BYTES = 256 * 1024;
    public static final int MAX_JSON_UTF8_BYTES = 64 * 1024 * 1024;

    private static final String SHA256_PREFIX = "sha256:";
    private static final int SHA256_HEX_LENGTH = 64;

    private SFMWorkspaceCounterfactualContract() {
    }

    public enum NarrativeKind {
        RECORDED,
        RECORDED_CHECKOUT,
        FROZEN_WITNESS,
        REEVALUATED_INTENT
    }

    /** One complete logical document contained in a workspace frame. */
    public record WorkspaceDocument(String logicalPath, String text, String textHash) {
        public WorkspaceDocument {
            logicalPath = identifier(logicalPath, "document.logicalPath");
            text = boundedText(text, "document.text", MAX_DOCUMENT_UTF8_BYTES, true);
            textHash = hash(textHash, "document.textHash");
            String computed = sha256(text);
            if (!computed.equals(textHash)) {
                throw new IllegalArgumentException("Document text hash does not match its full UTF-8 content");
            }
        }

        public static WorkspaceDocument create(String logicalPath, String text) {
            return new WorkspaceDocument(logicalPath, text, sha256(text));
        }
    }

    /** One complete restorable workspace state; identity and ancestry are separate from the validated state hash. */
    public record WorkspaceFrame(
            String id,
            Optional<String> parentId,
            String panelLayoutId,
            String focusedPanelId,
            String explorerLocation,
            Optional<String> selectedPath,
            Optional<String> openDocumentPath,
            List<WorkspaceDocument> documents,
            String stateHash
    ) {
        public WorkspaceFrame {
            id = identifier(id, "frame.id");
            parentId = optionalIdentifier(parentId, "frame.parentId");
            if (parentId.filter(id::equals).isPresent()) {
                throw new IllegalArgumentException("Workspace frame cannot be its own parent");
            }
            panelLayoutId = identifier(panelLayoutId, "frame.panelLayoutId");
            focusedPanelId = identifier(focusedPanelId, "frame.focusedPanelId");
            explorerLocation = metadata(explorerLocation, "frame.explorerLocation", false);
            selectedPath = optionalMetadata(selectedPath, "frame.selectedPath");
            openDocumentPath = optionalMetadata(openDocumentPath, "frame.openDocumentPath");
            documents = canonicalDocuments(documents);
            stateHash = hash(stateHash, "frame.stateHash");

            Set<String> documentPaths = new HashSet<>();
            long totalBytes = 0;
            for (WorkspaceDocument document : documents) {
                documentPaths.add(document.logicalPath());
                totalBytes = Math.addExact(totalBytes, utf8Length(document.text()));
            }
            if (totalBytes > MAX_TOTAL_DOCUMENT_UTF8_BYTES) {
                throw new IllegalArgumentException("Workspace frame document content exceeds the bounded total");
            }
            openDocumentPath.ifPresent(path -> {
                if (!documentPaths.contains(path)) {
                    throw new IllegalArgumentException("Open document is absent from the complete workspace frame: " + path);
                }
            });

            String computed = workspaceStateHash(
                    panelLayoutId,
                    focusedPanelId,
                    explorerLocation,
                    selectedPath,
                    openDocumentPath,
                    documents
            );
            if (!computed.equals(stateHash)) {
                throw new IllegalArgumentException("Workspace state hash does not match the complete frame content");
            }
        }

        public static WorkspaceFrame create(
                String id,
                Optional<String> parentId,
                String panelLayoutId,
                String focusedPanelId,
                String explorerLocation,
                Optional<String> selectedPath,
                Optional<String> openDocumentPath,
                List<WorkspaceDocument> documents
        ) {
            List<WorkspaceDocument> canonical = canonicalDocuments(documents);
            return new WorkspaceFrame(
                    id,
                    parentId,
                    panelLayoutId,
                    focusedPanelId,
                    explorerLocation,
                    selectedPath,
                    openDocumentPath,
                    canonical,
                    workspaceStateHash(
                            panelLayoutId,
                            focusedPanelId,
                            explorerLocation,
                            selectedPath,
                            openDocumentPath,
                            canonical
                    )
            );
        }
    }

    /** A semantic workspace operation, including the policy and concrete witnesses used for its evaluation. */
    public record Operation(
            String id,
            String actionId,
            String parentStateId,
            Optional<String> resultStateId,
            SFMHistoryGraphContract.EvaluationPolicy evaluationPolicy,
            SFMHistoryGraphContract.EffectClass effectClass,
            SFMHistoryGraphContract.OutcomeStatus outcomeStatus,
            String query,
            List<String> arguments,
            List<SFMHistoryGraphContract.DependencyWitness> dependencyWitnesses,
            List<String> compatibleSuffixActionIds,
            List<String> rejectedSuffixActionIds,
            NarrativeKind narrativeKind,
            List<String> diagnostics
    ) {
        public Operation {
            id = identifier(id, "operation.id");
            actionId = identifier(actionId, "operation.actionId");
            parentStateId = identifier(parentStateId, "operation.parentStateId");
            resultStateId = optionalIdentifier(resultStateId, "operation.resultStateId");
            Objects.requireNonNull(evaluationPolicy, "operation.evaluationPolicy");
            Objects.requireNonNull(effectClass, "operation.effectClass");
            Objects.requireNonNull(outcomeStatus, "operation.outcomeStatus");
            query = metadata(query, "operation.query", true);
            arguments = orderedText(arguments, "operation.arguments", true);
            dependencyWitnesses = canonicalWitnesses(dependencyWitnesses);
            compatibleSuffixActionIds = orderedUniqueIds(
                    compatibleSuffixActionIds,
                    "operation.compatibleSuffixActionIds"
            );
            rejectedSuffixActionIds = orderedUniqueIds(
                    rejectedSuffixActionIds,
                    "operation.rejectedSuffixActionIds"
            );
            HashSet<String> overlap = new HashSet<>(compatibleSuffixActionIds);
            overlap.retainAll(rejectedSuffixActionIds);
            if (!overlap.isEmpty()) {
                throw new IllegalArgumentException("Suffix actions cannot be both compatible and rejected: " + overlap);
            }
            Objects.requireNonNull(narrativeKind, "operation.narrativeKind");
            diagnostics = sortedText(diagnostics, "operation.diagnostics", true);

            if (outcomeStatus == SFMHistoryGraphContract.OutcomeStatus.SUCCEEDED && resultStateId.isEmpty()) {
                throw new IllegalArgumentException("Successful workspace operations require a result state");
            }
            boolean externalEffect = effectClass == SFMHistoryGraphContract.EffectClass.EXTERNAL_IRREVERSIBLE;
            boolean externalStatus = outcomeStatus == SFMHistoryGraphContract.OutcomeStatus.EXTERNAL_BARRIER;
            if (externalEffect != externalStatus || externalStatus && resultStateId.isPresent()) {
                throw new IllegalArgumentException(
                        "External barrier operations require EXTERNAL_IRREVERSIBLE/EXTERNAL_BARRIER and no result"
                );
            }
        }
    }

    /** An explicit movement of the workspace head; it never implies that an operation was re-executed. */
    public record HeadMovement(
            String id,
            SFMHistoryGraphContract.HeadMovementKind kind,
            String label,
            String fromStateId,
            String toStateId,
            NarrativeKind narrativeKind
    ) {
        public HeadMovement {
            id = identifier(id, "headMovement.id");
            Objects.requireNonNull(kind, "headMovement.kind");
            label = metadata(label, "headMovement.label", false);
            fromStateId = identifier(fromStateId, "headMovement.fromStateId");
            toStateId = identifier(toStateId, "headMovement.toStateId");
            Objects.requireNonNull(narrativeKind, "headMovement.narrativeKind");
        }
    }

    /** A typed non-restorable boundary. A barrier is evidence and never masquerades as a workspace frame. */
    public record Barrier(
            String id,
            String parentStateId,
            String actionId,
            SFMHistoryGraphContract.EffectClass effectClass,
            SFMHistoryGraphContract.OutcomeStatus outcomeStatus,
            String reason
    ) {
        public Barrier {
            id = identifier(id, "barrier.id");
            parentStateId = identifier(parentStateId, "barrier.parentStateId");
            actionId = identifier(actionId, "barrier.actionId");
            Objects.requireNonNull(effectClass, "barrier.effectClass");
            Objects.requireNonNull(outcomeStatus, "barrier.outcomeStatus");
            reason = metadata(reason, "barrier.reason", false);
            if (effectClass != SFMHistoryGraphContract.EffectClass.EXTERNAL_IRREVERSIBLE
                    || outcomeStatus != SFMHistoryGraphContract.OutcomeStatus.EXTERNAL_BARRIER) {
                throw new IllegalArgumentException(
                        "Workspace barriers require EXTERNAL_IRREVERSIBLE/EXTERNAL_BARRIER"
                );
            }
        }
    }

    /** The complete canonical artifact consumed and produced by the forthcoming X5 controller. */
    public record Artifact(
            String schema,
            String episodeId,
            long generation,
            String currentStateId,
            String ambientBeforeHash,
            String ambientAfterHash,
            List<WorkspaceFrame> workspaceFrames,
            List<Operation> operations,
            List<HeadMovement> headMovements,
            List<Barrier> barriers
    ) {
        public Artifact {
            if (!SCHEMA.equals(schema)) {
                throw new IllegalArgumentException("Unsupported workspace counterfactual schema: " + schema);
            }
            episodeId = identifier(episodeId, "artifact.episodeId");
            if (generation < 0) throw new IllegalArgumentException("Artifact generation must not be negative");
            currentStateId = identifier(currentStateId, "artifact.currentStateId");
            ambientBeforeHash = hash(ambientBeforeHash, "artifact.ambientBeforeHash");
            ambientAfterHash = hash(ambientAfterHash, "artifact.ambientAfterHash");
            if (!ambientBeforeHash.equals(ambientAfterHash)) {
                throw new IllegalArgumentException("The X5 fixture must not mutate the ambient checkout");
            }
            workspaceFrames = canonical(workspaceFrames, WorkspaceFrame::id, "artifact.workspaceFrames");
            operations = canonical(operations, Operation::id, "artifact.operations");
            headMovements = canonical(headMovements, HeadMovement::id, "artifact.headMovements");
            barriers = canonical(barriers, Barrier::id, "artifact.barriers");
            validateReferences(workspaceFrames, operations, headMovements, barriers, currentStateId);
            validateTotalDocumentBytes(workspaceFrames);
        }

        public static Artifact create(
                String episodeId,
                long generation,
                String currentStateId,
                String ambientBeforeHash,
                String ambientAfterHash,
                List<WorkspaceFrame> workspaceFrames,
                List<Operation> operations,
                List<HeadMovement> headMovements,
                List<Barrier> barriers
        ) {
            return new Artifact(
                    SCHEMA,
                    episodeId,
                    generation,
                    currentStateId,
                    ambientBeforeHash,
                    ambientAfterHash,
                    workspaceFrames,
                    operations,
                    headMovements,
                    barriers
            );
        }

        public WorkspaceFrame requireFrame(String id) {
            return workspaceFrames.stream().filter(frame -> frame.id().equals(id)).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Unknown workspace frame " + id));
        }

        public Operation requireOperation(String id) {
            return operations.stream().filter(operation -> operation.id().equals(id)).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Unknown workspace operation " + id));
        }
    }

    /** Hashes a Java string as strict UTF-8 and returns the canonical lowercase {@code sha256:...} form. */
    public static String sha256(String value) {
        Objects.requireNonNull(value, "value");
        return sha256(utf8(value, "value"));
    }

    /** Returns the strict UTF-8 byte length, rejecting malformed surrogate input. */
    public static int utf8Length(String value) {
        return utf8(Objects.requireNonNull(value, "value"), "value").length;
    }

    /** Computes the content hash validated by {@link WorkspaceFrame}. */
    public static String workspaceStateHash(
            String panelLayoutId,
            String focusedPanelId,
            String explorerLocation,
            Optional<String> selectedPath,
            Optional<String> openDocumentPath,
            List<WorkspaceDocument> documents
    ) {
        String canonicalPanelLayoutId = identifier(panelLayoutId, "frame.panelLayoutId");
        String canonicalFocusedPanelId = identifier(focusedPanelId, "frame.focusedPanelId");
        String canonicalExplorerLocation = metadata(explorerLocation, "frame.explorerLocation", false);
        Optional<String> canonicalSelectedPath = optionalMetadata(selectedPath, "frame.selectedPath");
        Optional<String> canonicalOpenDocumentPath = optionalMetadata(openDocumentPath, "frame.openDocumentPath");
        List<WorkspaceDocument> canonicalDocuments = canonicalDocuments(documents);

        CanonicalHasher hasher = new CanonicalHasher();
        hasher.value("schema", SCHEMA);
        hasher.value("panel-layout-id", canonicalPanelLayoutId);
        hasher.value("focused-panel-id", canonicalFocusedPanelId);
        hasher.value("explorer-location", canonicalExplorerLocation);
        hasher.optional("selected-path", canonicalSelectedPath);
        hasher.optional("open-document-path", canonicalOpenDocumentPath);
        hasher.number("document-count", canonicalDocuments.size());
        for (WorkspaceDocument document : canonicalDocuments) {
            hasher.value("document-logical-path", document.logicalPath());
            hasher.value("document-text", document.text());
            hasher.value("document-text-hash", document.textHash());
        }
        return hasher.finish();
    }

    private static void validateReferences(
            List<WorkspaceFrame> frames,
            List<Operation> operations,
            List<HeadMovement> movements,
            List<Barrier> barriers,
            String currentStateId
    ) {
        Map<String, WorkspaceFrame> frameIndex = index(frames, WorkspaceFrame::id);
        requireReference(frameIndex, currentStateId, "current state");
        for (WorkspaceFrame frame : frames) {
            frame.parentId().ifPresent(parent -> requireReference(frameIndex, parent, "frame parent"));
        }
        validateAcyclicFrames(frameIndex);
        for (Operation operation : operations) {
            requireReference(frameIndex, operation.parentStateId(), "operation parent state");
            operation.resultStateId().ifPresent(result -> requireReference(frameIndex, result, "operation result state"));
        }
        for (HeadMovement movement : movements) {
            requireReference(frameIndex, movement.fromStateId(), "head movement source");
            requireReference(frameIndex, movement.toStateId(), "head movement target");
        }
        for (Barrier barrier : barriers) {
            requireReference(frameIndex, barrier.parentStateId(), "barrier parent state");
            if (frameIndex.containsKey(barrier.id())) {
                throw new IllegalArgumentException("A barrier cannot masquerade as a workspace frame: " + barrier.id());
            }
        }
    }

    private static void validateAcyclicFrames(Map<String, WorkspaceFrame> frames) {
        Map<String, Integer> marks = new HashMap<>();
        for (String start : frames.keySet()) {
            if (marks.getOrDefault(start, 0) == 2) continue;
            ArrayList<String> path = new ArrayList<>();
            String cursor = start;
            while (true) {
                int mark = marks.getOrDefault(cursor, 0);
                if (mark == 1) throw new IllegalArgumentException("Workspace frame ancestry contains a cycle at " + cursor);
                if (mark == 2) break;
                marks.put(cursor, 1);
                path.add(cursor);
                Optional<String> parent = frames.get(cursor).parentId();
                if (parent.isEmpty()) break;
                cursor = parent.orElseThrow();
            }
            path.forEach(id -> marks.put(id, 2));
        }
    }

    private static void validateTotalDocumentBytes(List<WorkspaceFrame> frames) {
        long total = 0;
        for (WorkspaceFrame frame : frames) {
            for (WorkspaceDocument document : frame.documents()) {
                total = Math.addExact(total, utf8Length(document.text()));
                if (total > MAX_TOTAL_DOCUMENT_UTF8_BYTES) {
                    throw new IllegalArgumentException("Artifact document content exceeds the bounded total");
                }
            }
        }
    }

    private static List<WorkspaceDocument> canonicalDocuments(List<WorkspaceDocument> documents) {
        Objects.requireNonNull(documents, "frame.documents");
        if (documents.size() > MAX_DOCUMENTS_PER_FRAME) {
            throw new IllegalArgumentException("Workspace frame exceeds the bounded document count");
        }
        ArrayList<WorkspaceDocument> answer = new ArrayList<>(documents.size());
        HashSet<String> paths = new HashSet<>();
        for (WorkspaceDocument document : documents) {
            document = Objects.requireNonNull(document, "frame.documents item");
            if (!paths.add(document.logicalPath())) {
                throw new IllegalArgumentException("Duplicate workspace document path " + document.logicalPath());
            }
            answer.add(document);
        }
        answer.sort(Comparator.comparing(WorkspaceDocument::logicalPath));
        return List.copyOf(answer);
    }

    private static List<SFMHistoryGraphContract.DependencyWitness> canonicalWitnesses(
            List<SFMHistoryGraphContract.DependencyWitness> witnesses
    ) {
        Objects.requireNonNull(witnesses, "operation.dependencyWitnesses");
        if (witnesses.size() > MAX_RECORDS_PER_KIND) {
            throw new IllegalArgumentException("Operation dependency witnesses exceed the bounded count");
        }
        ArrayList<SFMHistoryGraphContract.DependencyWitness> answer = new ArrayList<>(witnesses.size());
        HashSet<String> identities = new HashSet<>();
        for (SFMHistoryGraphContract.DependencyWitness witness : witnesses) {
            witness = Objects.requireNonNull(witness, "operation.dependencyWitnesses item");
            identifier(witness.kind(), "witness.kind");
            identifier(witness.identity(), "witness.identity");
            identifier(witness.revision(), "witness.revision");
            String identity = witness.kind() + "\u0000" + witness.identity() + "\u0000" + witness.revision();
            if (!identities.add(identity)) throw new IllegalArgumentException("Duplicate dependency witness");
            answer.add(witness);
        }
        answer.sort(Comparator.comparing(SFMHistoryGraphContract.DependencyWitness::kind)
                .thenComparing(SFMHistoryGraphContract.DependencyWitness::identity)
                .thenComparing(SFMHistoryGraphContract.DependencyWitness::revision));
        return List.copyOf(answer);
    }

    private static <T> List<T> canonical(List<T> values, Function<T, String> id, String label) {
        Objects.requireNonNull(values, label);
        if (values.size() > MAX_RECORDS_PER_KIND) {
            throw new IllegalArgumentException(label + " exceeds the bounded record count");
        }
        ArrayList<T> answer = new ArrayList<>(values.size());
        HashSet<String> identities = new HashSet<>();
        for (T value : values) {
            value = Objects.requireNonNull(value, label + " item");
            String identity = identifier(id.apply(value), label + ".id");
            if (!identities.add(identity)) throw new IllegalArgumentException("Duplicate " + label + " id " + identity);
            answer.add(value);
        }
        answer.sort(Comparator.comparing(id));
        return List.copyOf(answer);
    }

    private static List<String> orderedUniqueIds(List<String> values, String label) {
        List<String> answer = orderedText(values, label, false);
        if (new HashSet<>(answer).size() != answer.size()) {
            throw new IllegalArgumentException(label + " must be unique");
        }
        return answer;
    }

    private static List<String> orderedText(List<String> values, String label, boolean allowEmpty) {
        Objects.requireNonNull(values, label);
        if (values.size() > MAX_RECORDS_PER_KIND) {
            throw new IllegalArgumentException(label + " exceeds the bounded count");
        }
        ArrayList<String> answer = new ArrayList<>(values.size());
        for (String value : values) answer.add(metadata(value, label + " item", allowEmpty));
        return List.copyOf(answer);
    }

    private static List<String> sortedText(List<String> values, String label, boolean allowEmpty) {
        ArrayList<String> answer = new ArrayList<>(orderedText(values, label, allowEmpty));
        answer.sort(String::compareTo);
        return List.copyOf(answer);
    }

    private static <T> Map<String, T> index(List<T> values, Function<T, String> id) {
        HashMap<String, T> answer = new HashMap<>();
        values.forEach(value -> answer.put(id.apply(value), value));
        return Map.copyOf(answer);
    }

    private static <T> T requireReference(Map<String, T> index, String id, String label) {
        T value = index.get(id);
        if (value == null) throw new IllegalArgumentException("Unknown " + label + ": " + id);
        return value;
    }

    private static String identifier(String value, String label) {
        return boundedText(value, label, MAX_IDENTIFIER_UTF8_BYTES, false);
    }

    private static String metadata(String value, String label, boolean allowEmpty) {
        return boundedText(value, label, MAX_METADATA_UTF8_BYTES, allowEmpty);
    }

    private static String boundedText(String value, String label, int maximumBytes, boolean allowEmpty) {
        value = Objects.requireNonNull(value, label);
        int bytes = utf8(value, label).length;
        if (!allowEmpty && value.isEmpty()) throw new IllegalArgumentException(label + " must not be empty");
        if (bytes > maximumBytes) throw new IllegalArgumentException(label + " exceeds the bounded UTF-8 size");
        return value;
    }

    private static Optional<String> optionalIdentifier(Optional<String> value, String label) {
        Objects.requireNonNull(value, label);
        return value.map(item -> identifier(item, label));
    }

    private static Optional<String> optionalMetadata(Optional<String> value, String label) {
        Objects.requireNonNull(value, label);
        return value.map(item -> metadata(item, label, false));
    }

    private static String hash(String value, String label) {
        value = identifier(value, label);
        if (value.length() != SHA256_PREFIX.length() + SHA256_HEX_LENGTH || !value.startsWith(SHA256_PREFIX)) {
            throw new IllegalArgumentException(label + " must use canonical sha256:<lowercase-hex> form");
        }
        for (int index = SHA256_PREFIX.length(); index < value.length(); index++) {
            char character = value.charAt(index);
            if (!(character >= '0' && character <= '9') && !(character >= 'a' && character <= 'f')) {
                throw new IllegalArgumentException(label + " must use canonical sha256:<lowercase-hex> form");
            }
        }
        return value;
    }

    private static byte[] utf8(String value, String label) {
        try {
            ByteBuffer encoded = StandardCharsets.UTF_8.newEncoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .encode(CharBuffer.wrap(value));
            byte[] answer = new byte[encoded.remaining()];
            encoded.get(answer);
            return answer;
        } catch (CharacterCodingException invalid) {
            throw new IllegalArgumentException(label + " must be valid Unicode encodable as UTF-8", invalid);
        }
    }

    private static String sha256(byte[] bytes) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder answer = new StringBuilder(SHA256_PREFIX);
            for (byte item : digest) answer.append(String.format("%02x", item & 0xff));
            return answer.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static final class CanonicalHasher {
        private final MessageDigest digest;

        private CanonicalHasher() {
            try {
                digest = MessageDigest.getInstance("SHA-256");
            } catch (NoSuchAlgorithmException impossible) {
                throw new IllegalStateException("SHA-256 is unavailable", impossible);
            }
        }

        private void value(String label, String value) {
            bytes(utf8(label, "hash label"));
            bytes(utf8(value, label));
        }

        private void optional(String label, Optional<String> value) {
            Objects.requireNonNull(value, label);
            value(label + "-presence", value.isPresent() ? "present" : "absent");
            value.ifPresent(item -> value(label, item));
        }

        private void number(String label, long value) {
            value(label, Long.toString(value));
        }

        private void bytes(byte[] value) {
            long length = value.length;
            for (int shift = 56; shift >= 0; shift -= 8) digest.update((byte) (length >>> shift));
            digest.update(value);
        }

        private String finish() {
            return sha256Digest(digest.digest());
        }

        private static String sha256Digest(byte[] digest) {
            StringBuilder answer = new StringBuilder(SHA256_PREFIX);
            for (byte item : digest) answer.append(String.format("%02x", item & 0xff));
            return answer.toString();
        }
    }
}
