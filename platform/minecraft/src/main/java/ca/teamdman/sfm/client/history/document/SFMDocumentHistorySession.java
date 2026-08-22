package ca.teamdman.sfm.client.history.document;

import ca.teamdman.sfm.client.history.SFMHistoryGraphContract;
import ca.teamdman.sfm.client.history.document.SFMDocumentHistoryContract.Archive;
import ca.teamdman.sfm.client.history.document.SFMDocumentHistoryContract.DocumentHeadMovement;
import ca.teamdman.sfm.client.history.document.SFMDocumentHistoryContract.DocumentMutation;
import ca.teamdman.sfm.client.history.document.SFMDocumentHistoryContract.DocumentRevision;
import ca.teamdman.sfm.client.history.document.SFMDocumentHistoryContract.DocumentState;
import ca.teamdman.sfm.client.history.document.SFMDocumentHistoryContract.GraphIdentity;
import ca.teamdman.sfm.client.history.document.SFMDocumentHistoryContract.GroupingPolicy;
import ca.teamdman.sfm.client.history.document.SFMDocumentHistoryContract.MutationKind;
import ca.teamdman.sfm.client.history.document.SFMDocumentHistoryContract.MutationRequest;
import ca.teamdman.sfm.client.history.document.SFMDocumentHistoryContract.MutationStatus;
import ca.teamdman.sfm.client.history.document.SFMDocumentHistoryContract.PreferredRedo;
import ca.teamdman.sfm.client.history.document.SFMDocumentHistoryContract.Projection;
import ca.teamdman.sfm.client.history.document.SFMDocumentHistoryContract.RawInput;
import ca.teamdman.sfm.client.history.document.SFMDocumentHistoryContract.RawInputEvent;
import ca.teamdman.sfm.client.history.document.SFMDocumentHistoryContract.SemanticTransaction;
import ca.teamdman.sfm.client.history.document.SFMDocumentHistoryContract.SessionIdentity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Thread-safe, editor-independent owner of one immutable document revision graph.
 *
 * <p>Raw input and mutations are append-only. Undo, redo, and checkout append
 * head movements and never remove a state or branch.</p>
 */
public final class SFMDocumentHistorySession {
    public static final String EVALUATOR_REVISION = "sfm.document-history-kernel/1";

    public enum RawInputStatus {
        APPENDED,
        DUPLICATE
    }

    public record RawInputResult(RawInputStatus status, RawInputEvent event, long generation) {
        public RawInputResult {
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(event, "event");
            if (generation < 0) throw new IllegalArgumentException("generation must not be negative");
        }
    }

    public enum AppendStatus {
        APPLIED,
        NO_CHANGE,
        DUPLICATE
    }

    public record AppendResult(
            AppendStatus status,
            DocumentMutation mutation,
            DocumentRevision revision,
            long generation
    ) {
        public AppendResult {
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(mutation, "mutation");
            Objects.requireNonNull(revision, "revision");
            if (generation < 0) throw new IllegalArgumentException("generation must not be negative");
        }
    }

    public enum HeadMoveStatus {
        APPLIED,
        NO_CHANGE,
        AMBIGUOUS,
        REJECTED
    }

    public record HeadMoveResult(
            HeadMoveStatus status,
            String fromRevisionId,
            String toRevisionId,
            List<String> candidateRevisionIds,
            Optional<DocumentHeadMovement> movement,
            String message,
            long generation
    ) {
        public HeadMoveResult {
            Objects.requireNonNull(status, "status");
            fromRevisionId = requireText(fromRevisionId, "fromRevisionId");
            toRevisionId = requireText(toRevisionId, "toRevisionId");
            Objects.requireNonNull(candidateRevisionIds, "candidateRevisionIds");
            candidateRevisionIds = candidateRevisionIds.stream()
                    .map(value -> requireText(value, "candidateRevisionId"))
                    .distinct()
                    .sorted()
                    .toList();
            Objects.requireNonNull(movement, "movement");
            message = requireText(message, "message");
            if (generation < 0) throw new IllegalArgumentException("generation must not be negative");
            if ((status == HeadMoveStatus.APPLIED) != movement.isPresent()) {
                throw new IllegalArgumentException("Only applied head moves contain a movement");
            }
        }
    }

    public enum ChangeKind {
        INITIAL,
        RAW_INPUT,
        MUTATION,
        HEAD_MOVEMENT,
        RESTORED
    }

    public record ChangeNotification(
            SessionIdentity identity,
            long generation,
            String currentRevisionId,
            ChangeKind kind,
            String subjectId
    ) {
        public ChangeNotification {
            Objects.requireNonNull(identity, "identity");
            if (generation < 0) throw new IllegalArgumentException("generation must not be negative");
            currentRevisionId = requireText(currentRevisionId, "currentRevisionId");
            Objects.requireNonNull(kind, "kind");
            subjectId = requireText(subjectId, "subjectId");
        }
    }

    @FunctionalInterface
    public interface Listener {
        void changed(ChangeNotification notification);
    }

    @FunctionalInterface
    public interface Subscription extends AutoCloseable {
        @Override
        void close();
    }

    public record RootSeed(String revisionId, DocumentState state, String stateHash) {
        public RootSeed {
            revisionId = requireText(revisionId, "revisionId");
            Objects.requireNonNull(state, "state");
            stateHash = requireText(stateHash, "stateHash");
        }
    }

    /** Advanced adapter seam for replaying or materializing an already-authorized branch. */
    public record HeadMoveRequest(
            SFMHistoryGraphContract.HeadMovementKind kind,
            String targetRevisionId,
            List<String> candidateRevisionIds,
            String actor,
            String requestId,
            List<String> rawEventIds,
            boolean pinDepartedRevision
    ) {
        public HeadMoveRequest {
            Objects.requireNonNull(kind, "kind");
            targetRevisionId = requireText(targetRevisionId, "targetRevisionId");
            Objects.requireNonNull(candidateRevisionIds, "candidateRevisionIds");
            candidateRevisionIds = candidateRevisionIds.stream()
                    .map(value -> requireText(value, "candidateRevisionId"))
                    .distinct()
                    .sorted()
                    .toList();
            actor = requireText(actor, "actor");
            requestId = requireText(requestId, "requestId");
            Objects.requireNonNull(rawEventIds, "rawEventIds");
            rawEventIds = List.copyOf(rawEventIds);
        }
    }

    private final SessionIdentity identity;
    private final String headId;
    private final String rootRevisionId;
    private final LinkedHashMap<String, DocumentRevision> revisions = new LinkedHashMap<>();
    private final LinkedHashMap<String, TreeSet<String>> childRevisionIds = new LinkedHashMap<>();
    private final LinkedHashMap<String, RawInputEvent> rawEvents = new LinkedHashMap<>();
    private final LinkedHashMap<String, DocumentMutation> mutations = new LinkedHashMap<>();
    private final ArrayList<DocumentHeadMovement> headMovements = new ArrayList<>();
    private final ArrayList<SFMHistoryGraphContract.RetentionPin> retentionPins = new ArrayList<>();
    private final LinkedHashMap<String, String> preferredRedo = new LinkedHashMap<>();
    private final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();

    private String currentRevisionId;
    private long generation;
    private long nextSequence = 1;
    private long nextMovementOrdinal = 1;

    public static SFMDocumentHistorySession create(SessionIdentity identity, DocumentState initialState) {
        Objects.requireNonNull(identity, "identity");
        Objects.requireNonNull(initialState, "initialState");
        String stateHash = initialState.stateHash();
        String revisionId = identity.qualify(
                "revision",
                SFMDocumentHistoryContract.fingerprint("root", stateHash).substring("sha256:".length())
        );
        return new SFMDocumentHistorySession(identity, new RootSeed(revisionId, initialState, stateHash));
    }

    public static SFMDocumentHistorySession create(SessionIdentity identity, RootSeed root) {
        return new SFMDocumentHistorySession(identity, root);
    }

    public static SFMDocumentHistorySession restore(Archive archive) {
        Objects.requireNonNull(archive, "archive");
        DocumentRevision root = archive.revisions().stream()
                .filter(revision -> revision.id().equals(archive.rootRevisionId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Archive root revision is missing"));
        SFMDocumentHistorySession session = new SFMDocumentHistorySession(
                archive.identity(),
                new RootSeed(root.id(), root.state(), root.stateHash())
        );
        session.loadArchive(archive, false);
        return session;
    }

    private SFMDocumentHistorySession(SessionIdentity identity, RootSeed root) {
        this.identity = Objects.requireNonNull(identity, "identity");
        Objects.requireNonNull(root, "root");
        headId = identity.qualify("head", "current");
        rootRevisionId = root.revisionId();
        DocumentRevision initial = new DocumentRevision(
                root.revisionId(),
                Optional.empty(),
                root.state(),
                root.stateHash(),
                0,
                Optional.empty()
        );
        revisions.put(initial.id(), initial);
        childRevisionIds.put(initial.id(), new TreeSet<>());
        currentRevisionId = initial.id();
    }

    public SessionIdentity identity() {
        return identity;
    }

    public String headId() {
        return headId;
    }

    public String rootRevisionId() {
        return rootRevisionId;
    }

    public synchronized long generation() {
        return generation;
    }

    public synchronized String currentRevisionId() {
        return currentRevisionId;
    }

    public synchronized DocumentRevision currentRevision() {
        return requireRevision(currentRevisionId);
    }

    public synchronized DocumentState currentState() {
        return currentRevision().state();
    }

    public synchronized Optional<DocumentRevision> revision(String revisionId) {
        return Optional.ofNullable(revisions.get(Objects.requireNonNull(revisionId, "revisionId")));
    }

    public synchronized List<DocumentRevision> retainedRevisions() {
        return List.copyOf(revisions.values());
    }

    public synchronized List<String> childRevisionIds(String parentRevisionId) {
        requireRevision(parentRevisionId);
        return List.copyOf(childRevisionIds.getOrDefault(parentRevisionId, new TreeSet<>()));
    }

    public Subscription subscribe(Listener listener, boolean emitCurrent) {
        Objects.requireNonNull(listener, "listener");
        listeners.add(listener);
        if (emitCurrent) {
            listener.changed(notification(ChangeKind.INITIAL, headId));
        }
        return () -> listeners.remove(listener);
    }

    public synchronized RawInputResult appendRawInput(RawInput input) {
        Objects.requireNonNull(input, "input");
        RawInputEvent existing = rawEvents.get(input.id());
        if (existing != null) {
            if (!existing.input().equals(input)) {
                throw new IllegalArgumentException("Raw input id collision: " + input.id());
            }
            return new RawInputResult(RawInputStatus.DUPLICATE, existing, generation);
        }
        RawInputEvent event = new RawInputEvent(nextSequence, input);
        rawEvents.put(event.id(), event);
        nextSequence++;
        generation++;
        notifyListeners(ChangeKind.RAW_INPUT, event.id());
        return new RawInputResult(RawInputStatus.APPENDED, event, generation);
    }

    /** Append a mutation whose parent must be the current document head. */
    public synchronized AppendResult append(MutationRequest request) {
        return appendFrom(currentRevisionId, request, true);
    }

    /**
     * Materialize a retained branch without moving the current head.
     * Intended for deterministic planners/replay adapters, not ordinary widget edits.
     */
    public synchronized AppendResult appendDetached(String parentRevisionId, MutationRequest request) {
        return appendFrom(parentRevisionId, request, false);
    }

    private AppendResult appendFrom(String parentRevisionId, MutationRequest request, boolean moveHead) {
        String parentId = requireText(parentRevisionId, "parentRevisionId");
        Objects.requireNonNull(request, "request");
        DocumentRevision parent = requireRevision(parentId);
        if (moveHead && !currentRevisionId.equals(parentId)) {
            throw new IllegalStateException("Ordinary mutation parent must be the current document head");
        }
        validateRawEventReferences(request.provenance().rawEventIds());

        DocumentMutation existing = mutations.get(request.mutationId());
        if (existing != null) return duplicateAppend(existing, request, parent);

        boolean noChange = parent.state().equals(request.resultingState());
        if (request.kind() == MutationKind.NO_OP && !noChange) {
            throw new IllegalArgumentException("NO_OP mutations must preserve their chosen parent state exactly");
        }
        MutationStatus mutationStatus = noChange ? MutationStatus.NO_CHANGE : MutationStatus.APPLIED;
        long sequence = nextSequence;
        String resultingStateHash = request.stateHash().orElseGet(request.resultingState()::stateHash);
        String revisionId = noChange
                ? parentId
                : request.revisionId().orElseGet(() -> identity.qualify(
                        "revision",
                        SFMDocumentHistoryContract.fingerprint(
                                parentId,
                                request.mutationId(),
                                resultingStateHash
                        ).substring("sha256:".length())
                ));
        GraphIdentity graphIdentity = request.graphIdentity().orElseGet(() -> defaultGraphIdentity(
                request,
                parent,
                revisionId,
                resultingStateHash
        ));
        DocumentMutation mutation = new DocumentMutation(
                request.mutationId(),
                sequence,
                request.kind(),
                request.direction(),
                parentId,
                revisionId,
                request.changedText(),
                request.provenance(),
                graphIdentity,
                mutationStatus
        );

        DocumentRevision resultingRevision = parent;
        if (!noChange) {
            resultingRevision = new DocumentRevision(
                    revisionId,
                    Optional.of(parentId),
                    request.resultingState(),
                    resultingStateHash,
                    sequence,
                    Optional.of(mutation.id())
            );
            if (revisions.containsKey(revisionId)) {
                throw new IllegalArgumentException("Document revision identity collision: " + revisionId);
            }
            revisions.put(revisionId, resultingRevision);
            childRevisionIds.computeIfAbsent(parentId, ignored -> new TreeSet<>()).add(revisionId);
            childRevisionIds.putIfAbsent(revisionId, new TreeSet<>());
            if (moveHead) currentRevisionId = revisionId;
        }
        mutations.put(mutation.id(), mutation);
        nextSequence++;
        generation++;
        notifyListeners(ChangeKind.MUTATION, mutation.id());
        return new AppendResult(
                noChange ? AppendStatus.NO_CHANGE : AppendStatus.APPLIED,
                mutation,
                resultingRevision,
                generation
        );
    }

    private AppendResult duplicateAppend(
            DocumentMutation existing,
            MutationRequest request,
            DocumentRevision requestedParent
    ) {
        DocumentRevision result = requireRevision(existing.afterRevisionId());
        if (!existing.beforeRevisionId().equals(requestedParent.id())
                || existing.kind() != request.kind()
                || existing.direction() != request.direction()
                || !result.state().equals(request.resultingState())
                || !existing.changedText().equals(request.changedText())
                || !existing.provenance().equals(request.provenance())
                || request.revisionId().filter(value -> !value.equals(existing.afterRevisionId())).isPresent()
                || request.stateHash().filter(value -> !value.equals(result.stateHash())).isPresent()
                || request.graphIdentity().filter(value -> !value.equals(existing.graphIdentity())).isPresent()) {
            throw new IllegalArgumentException("Mutation id collision: " + request.mutationId());
        }
        return new AppendResult(AppendStatus.DUPLICATE, existing, result, generation);
    }

    public synchronized List<SemanticTransaction> semanticTransactions(GroupingPolicy policy) {
        return SFMDocumentSemanticTransactionProjection.project(
                identity,
                policy,
                List.copyOf(revisions.values()),
                List.copyOf(rawEvents.values()),
                List.copyOf(mutations.values()),
                List.copyOf(headMovements)
        );
    }

    public synchronized List<DocumentRevision> eligibleRedoChildren() {
        return eligibleRedoChildren(GroupingPolicy.typingV1());
    }

    public synchronized List<DocumentRevision> eligibleRedoChildren(GroupingPolicy policy) {
        LinkedHashSet<String> candidates = new LinkedHashSet<>();
        semanticTransactions(policy).stream()
                .filter(transaction -> transaction.beforeRevisionId().equals(currentRevisionId))
                .filter(transaction -> !transaction.beforeRevisionId().equals(transaction.afterRevisionId()))
                .forEach(transaction -> candidates.add(transaction.afterRevisionId()));
        return candidates.stream().sorted().map(this::requireRevision).toList();
    }

    public synchronized HeadMoveResult undo(String actor, String requestId, List<String> rawEventIds) {
        return undo(GroupingPolicy.typingV1(), actor, requestId, rawEventIds);
    }

    public synchronized HeadMoveResult undo(
            GroupingPolicy policy,
            String actor,
            String requestId,
            List<String> rawEventIds
    ) {
        DocumentRevision current = currentRevision();
        SemanticTransaction transaction = semanticTransactionForUndo(policy, current.id()).orElse(null);
        String target;
        String preferredChild;
        if (transaction != null) {
            target = transaction.beforeRevisionId();
            preferredChild = transaction.afterRevisionId();
        } else if (current.parentRevisionId().isPresent()) {
            target = current.parentRevisionId().orElseThrow();
            preferredChild = current.id();
        } else {
            return noChange("Document head is already at its root");
        }
        preferredRedo.put(target, preferredChild);
        return moveHeadLocked(new HeadMoveRequest(
                SFMHistoryGraphContract.HeadMovementKind.UNDO,
                target,
                List.of(target),
                actor,
                requestId,
                rawEventIds,
                true
        ));
    }

    public synchronized HeadMoveResult redo(
            Optional<String> requestedChildRevisionId,
            String actor,
            String requestId,
            List<String> rawEventIds
    ) {
        return redo(GroupingPolicy.typingV1(), requestedChildRevisionId, actor, requestId, rawEventIds);
    }

    public synchronized HeadMoveResult redo(
            GroupingPolicy policy,
            Optional<String> requestedChildRevisionId,
            String actor,
            String requestId,
            List<String> rawEventIds
    ) {
        Objects.requireNonNull(requestedChildRevisionId, "requestedChildRevisionId");
        List<String> candidates = eligibleRedoChildren(policy).stream().map(DocumentRevision::id).toList();
        if (candidates.isEmpty()) return noChange("Document head has no eligible redo child");
        String selected;
        if (requestedChildRevisionId.isPresent()) {
            selected = requestedChildRevisionId.orElseThrow();
            if (!candidates.contains(selected)) {
                return rejected(candidates, "Requested redo revision is not an eligible child: " + selected);
            }
        } else {
            String preferred = preferredRedo.get(currentRevisionId);
            if (preferred != null && candidates.contains(preferred)) {
                selected = preferred;
            } else if (candidates.size() == 1) {
                selected = candidates.get(0);
            } else {
                return ambiguous(candidates, "More than one redo child is eligible");
            }
        }
        return moveHeadLocked(new HeadMoveRequest(
                SFMHistoryGraphContract.HeadMovementKind.REDO,
                selected,
                candidates,
                actor,
                requestId,
                rawEventIds,
                false
        ));
    }

    public synchronized HeadMoveResult checkout(
            String revisionId,
            String actor,
            String requestId,
            List<String> rawEventIds
    ) {
        return moveHeadLocked(new HeadMoveRequest(
                SFMHistoryGraphContract.HeadMovementKind.CHECKOUT,
                revisionId,
                List.of(revisionId),
                actor,
                requestId,
                rawEventIds,
                false
        ));
    }

    public synchronized HeadMoveResult moveHead(HeadMoveRequest request) {
        return moveHeadLocked(request);
    }

    private HeadMoveResult moveHeadLocked(HeadMoveRequest request) {
        Objects.requireNonNull(request, "request");
        validateRawEventReferences(request.rawEventIds());
        requireRevision(request.targetRevisionId());
        request.candidateRevisionIds().forEach(this::requireRevision);
        if (!request.candidateRevisionIds().isEmpty()
                && !request.candidateRevisionIds().contains(request.targetRevisionId())) {
            throw new IllegalArgumentException("Head movement target must be one of its candidates");
        }
        String from = currentRevisionId;
        if (from.equals(request.targetRevisionId())) return noChange("Document head is already at the requested revision");
        long ordinal = nextMovementOrdinal++;
        SFMHistoryGraphContract.HeadMovement graphMovement = new SFMHistoryGraphContract.HeadMovement(
                identity.qualify("head-movement", Long.toString(ordinal)),
                request.kind(),
                headId,
                from,
                request.targetRevisionId(),
                request.candidateRevisionIds().isEmpty()
                        ? List.of(request.targetRevisionId())
                        : request.candidateRevisionIds(),
                request.actor(),
                request.requestId()
        );
        DocumentHeadMovement movement = new DocumentHeadMovement(nextSequence++, graphMovement, request.rawEventIds());
        headMovements.add(movement);
        if (request.pinDepartedRevision()) {
            retentionPins.add(new SFMHistoryGraphContract.RetentionPin(
                    identity.qualify("retention-pin", "head-movement-" + ordinal),
                    SFMHistoryGraphContract.RetentionKind.NAMED_BRANCH,
                    from,
                    graphMovement.id()
            ));
        }
        currentRevisionId = request.targetRevisionId();
        generation++;
        notifyListeners(ChangeKind.HEAD_MOVEMENT, graphMovement.id());
        return new HeadMoveResult(
                HeadMoveStatus.APPLIED,
                from,
                currentRevisionId,
                graphMovement.candidateStateRevisionIds(),
                Optional.of(movement),
                "Moved document head to " + currentRevisionId,
                generation
        );
    }

    public synchronized Projection projection() {
        return projection(GroupingPolicy.typingV1());
    }

    public synchronized Projection projection(GroupingPolicy policy) {
        List<DocumentRevision> revisionList = List.copyOf(revisions.values());
        List<RawInputEvent> rawEventList = List.copyOf(rawEvents.values());
        List<DocumentMutation> mutationList = List.copyOf(mutations.values());
        List<DocumentHeadMovement> movementList = List.copyOf(headMovements);
        List<SemanticTransaction> transactions = SFMDocumentSemanticTransactionProjection.project(
                identity,
                policy,
                revisionList,
                rawEventList,
                mutationList,
                movementList
        );
        return new Projection(
                SFMDocumentHistoryContract.PROJECTION_SCHEMA,
                identity,
                generation,
                currentRevisionId,
                policy,
                revisionList,
                rawEventList,
                mutationList,
                transactions,
                movementList,
                preferredRedoEntries(),
                graphProjection(revisionList, mutationList, movementList)
        );
    }

    public synchronized Archive exportArchive() {
        return new Archive(
                SFMDocumentHistoryContract.ARCHIVE_SCHEMA,
                identity,
                headId,
                rootRevisionId,
                currentRevisionId,
                generation,
                nextSequence,
                nextMovementOrdinal,
                List.copyOf(revisions.values()),
                List.copyOf(rawEvents.values()),
                List.copyOf(mutations.values()),
                List.copyOf(headMovements),
                List.copyOf(retentionPins),
                preferredRedoEntries()
        );
    }

    public synchronized void restoreArchive(Archive archive) {
        loadArchive(archive, true);
    }

    private void loadArchive(Archive archive, boolean notify) {
        Objects.requireNonNull(archive, "archive");
        if (!identity.equals(archive.identity())) throw new IllegalArgumentException("Archive belongs to another session");
        if (!headId.equals(archive.headId())) throw new IllegalArgumentException("Archive head identity differs");
        if (!rootRevisionId.equals(archive.rootRevisionId())) {
            throw new IllegalArgumentException("Archive root identity differs");
        }

        LinkedHashMap<String, DocumentRevision> importedRevisions = new LinkedHashMap<>();
        LinkedHashMap<String, TreeSet<String>> importedChildren = new LinkedHashMap<>();
        for (DocumentRevision revision : archive.revisions()) {
            if (importedRevisions.put(revision.id(), revision) != null) {
                throw new IllegalArgumentException("Duplicate document revision " + revision.id());
            }
            importedChildren.put(revision.id(), new TreeSet<>());
        }
        DocumentRevision root = importedRevisions.get(rootRevisionId);
        if (root == null || root.parentRevisionId().isPresent() || root.sequence() != 0) {
            throw new IllegalArgumentException("Archive root revision is invalid");
        }
        for (DocumentRevision revision : importedRevisions.values()) {
            revision.parentRevisionId().ifPresent(parentId -> {
                DocumentRevision parent = importedRevisions.get(parentId);
                if (parent == null || parent.sequence() >= revision.sequence()) {
                    throw new IllegalArgumentException("Revision parent must exist and precede its child");
                }
                importedChildren.get(parentId).add(revision.id());
            });
        }
        if (!importedRevisions.containsKey(archive.currentRevisionId())) {
            throw new IllegalArgumentException("Archive current revision is missing");
        }

        HashSet<Long> observationSequences = new HashSet<>();
        LinkedHashMap<String, RawInputEvent> importedRaw = new LinkedHashMap<>();
        for (RawInputEvent event : archive.rawEvents()) {
            if (importedRaw.put(event.id(), event) != null || !observationSequences.add(event.sequence())) {
                throw new IllegalArgumentException("Duplicate raw event identity or sequence");
            }
        }
        LinkedHashMap<String, DocumentMutation> importedMutations = new LinkedHashMap<>();
        for (DocumentMutation mutation : archive.mutations()) {
            if (importedMutations.put(mutation.id(), mutation) != null
                    || !observationSequences.add(mutation.sequence())) {
                throw new IllegalArgumentException("Duplicate mutation identity or sequence");
            }
            requireImportedRevision(importedRevisions, mutation.beforeRevisionId());
            DocumentRevision after = requireImportedRevision(importedRevisions, mutation.afterRevisionId());
            mutation.provenance().rawEventIds().forEach(id -> requireImportedRaw(importedRaw, id));
            if (mutation.status() == MutationStatus.APPLIED
                    && !after.mutationId().filter(mutation.id()::equals).isPresent()) {
                throw new IllegalArgumentException("Applied mutation does not own its resulting revision");
            }
        }
        ArrayList<DocumentHeadMovement> importedMovements = new ArrayList<>();
        for (DocumentHeadMovement movement : archive.headMovements()) {
            if (!observationSequences.add(movement.sequence())) {
                throw new IllegalArgumentException("Duplicate head movement sequence");
            }
            requireImportedRevision(importedRevisions, movement.movement().fromStateRevisionId());
            requireImportedRevision(importedRevisions, movement.movement().toStateRevisionId());
            movement.movement().candidateStateRevisionIds().forEach(id -> requireImportedRevision(importedRevisions, id));
            movement.rawEventIds().forEach(id -> requireImportedRaw(importedRaw, id));
            importedMovements.add(movement);
        }
        long maximumSequence = observationSequences.stream().mapToLong(Long::longValue).max().orElse(0);
        if (archive.nextSequence() <= maximumSequence) {
            throw new IllegalArgumentException("Archive next sequence must follow every observation");
        }

        LinkedHashMap<String, String> importedPreferred = new LinkedHashMap<>();
        for (PreferredRedo preferred : archive.preferredRedo()) {
            requireImportedRevision(importedRevisions, preferred.parentRevisionId());
            requireImportedRevision(importedRevisions, preferred.childRevisionId());
            if (!isDescendant(importedRevisions, preferred.parentRevisionId(), preferred.childRevisionId())) {
                throw new IllegalArgumentException("Preferred redo child is not a descendant of its parent");
            }
            if (importedPreferred.put(preferred.parentRevisionId(), preferred.childRevisionId()) != null) {
                throw new IllegalArgumentException("Duplicate preferred redo parent");
            }
        }
        for (SFMHistoryGraphContract.RetentionPin pin : archive.retentionPins()) {
            requireImportedRevision(importedRevisions, pin.targetId());
        }

        revisions.clear();
        revisions.putAll(importedRevisions);
        childRevisionIds.clear();
        childRevisionIds.putAll(importedChildren);
        rawEvents.clear();
        rawEvents.putAll(importedRaw);
        mutations.clear();
        mutations.putAll(importedMutations);
        headMovements.clear();
        headMovements.addAll(importedMovements);
        retentionPins.clear();
        retentionPins.addAll(archive.retentionPins());
        preferredRedo.clear();
        preferredRedo.putAll(importedPreferred);
        currentRevisionId = archive.currentRevisionId();
        generation = archive.generation();
        nextSequence = archive.nextSequence();
        nextMovementOrdinal = archive.nextMovementOrdinal();
        if (notify) notifyListeners(ChangeKind.RESTORED, archive.digest());
    }

    private SFMHistoryGraphContract.Graph graphProjection(
            List<DocumentRevision> revisionList,
            List<DocumentMutation> mutationList,
            List<DocumentHeadMovement> movementList
    ) {
        LinkedHashMap<String, SFMHistoryGraphContract.ActionIntent> intents = new LinkedHashMap<>();
        ArrayList<SFMHistoryGraphContract.ActionEvaluation> evaluations = new ArrayList<>();
        ArrayList<SFMHistoryGraphContract.ActionOutcome> outcomes = new ArrayList<>();
        ArrayList<SFMHistoryGraphContract.BranchEdge> edges = new ArrayList<>();
        for (DocumentMutation mutation : mutationList) {
            if (mutation.status() != MutationStatus.APPLIED) continue;
            GraphIdentity graph = mutation.graphIdentity();
            SFMHistoryGraphContract.ActionIntent prior = intents.putIfAbsent(graph.intent().id(), graph.intent());
            if (prior != null && !prior.equals(graph.intent())) {
                throw new IllegalStateException("Action intent identity collision in document history");
            }
            evaluations.add(new SFMHistoryGraphContract.ActionEvaluation(
                    graph.evaluationId(),
                    graph.intent().id(),
                    mutation.beforeRevisionId(),
                    graph.evaluatorRevision(),
                    graph.evaluationPolicy(),
                    graph.dependencyWitnesses(),
                    graph.outcomeId(),
                    SFMHistoryGraphContract.ProjectionStatus.MATERIALIZED
            ));
            outcomes.add(new SFMHistoryGraphContract.ActionOutcome(
                    graph.outcomeId(),
                    graph.evaluationId(),
                    SFMHistoryGraphContract.OutcomeStatus.SUCCEEDED,
                    Optional.of(mutation.afterRevisionId()),
                    graph.evidence()
            ));
            edges.add(new SFMHistoryGraphContract.BranchEdge(
                    graph.edgeId(),
                    mutation.beforeRevisionId(),
                    mutation.afterRevisionId(),
                    Optional.of(graph.intent().id()),
                    Optional.of(graph.evaluationId()),
                    Optional.of(graph.outcomeId()),
                    graph.effectClass(),
                    SFMHistoryGraphContract.ProjectionStatus.MATERIALIZED,
                    true
            ));
        }
        List<SFMHistoryGraphContract.StateRevision> states = revisionList.stream()
                .map(revision -> new SFMHistoryGraphContract.StateRevision(
                        revision.id(),
                        revision.parentRevisionId().stream().toList(),
                        revision.stateHash(),
                        true,
                        SFMHistoryGraphContract.ProjectionStatus.MATERIALIZED
                ))
                .toList();
        return new SFMHistoryGraphContract.Graph(
                SFMHistoryGraphContract.SCHEMA,
                List.copyOf(intents.values()),
                evaluations,
                outcomes,
                states,
                List.of(new SFMHistoryGraphContract.HistoryHead(
                        headId,
                        new SFMHistoryGraphContract.UndoDomain(
                                SFMHistoryGraphContract.UndoDomainKind.DOCUMENT,
                                identity.sessionId()
                        ),
                        currentRevisionId,
                        Optional.of("current")
                )),
                movementList.stream().map(DocumentHeadMovement::movement).toList(),
                edges,
                List.copyOf(retentionPins)
        );
    }

    private GraphIdentity defaultGraphIdentity(
            MutationRequest request,
            DocumentRevision parent,
            String resultingRevisionId,
            String resultingStateHash
    ) {
        String actionId = "sfm:document/edit/" + request.kind().name().toLowerCase(java.util.Locale.ROOT);
        ArrayList<String> arguments = new ArrayList<>();
        arguments.add("direction=" + request.direction().name());
        request.changedText().ifPresent(text -> arguments.add("changed-text=" + text));
        String intentHash = SFMDocumentHistoryContract.fingerprint(
                "sfm.document-action-intent/1",
                identity.sessionId(),
                actionId,
                String.join("\u0000", arguments)
        );
        SFMHistoryGraphContract.ActionIntent intent = new SFMHistoryGraphContract.ActionIntent(
                identity.qualify("intent", intentHash.substring("sha256:".length())),
                actionId,
                arguments,
                intentHash
        );
        String token = SFMDocumentHistoryContract.fingerprint(
                request.mutationId(), parent.id(), resultingRevisionId, intent.id()
        ).substring("sha256:".length());
        return new GraphIdentity(
                intent,
                EVALUATOR_REVISION,
                SFMHistoryGraphContract.EvaluationPolicy.REUSE_RECORDED_TRANSITION,
                List.of(new SFMHistoryGraphContract.DependencyWitness(
                        "parent-state",
                        parent.id(),
                        parent.stateHash()
                )),
                identity.qualify("evaluation", token),
                identity.qualify("outcome", token),
                identity.qualify("edge", token),
                SFMHistoryGraphContract.EffectClass.PURE,
                List.of("result-state-hash=" + resultingStateHash)
        );
    }

    private Optional<SemanticTransaction> semanticTransactionForUndo(GroupingPolicy policy, String revisionId) {
        List<SemanticTransaction> transactions = semanticTransactions(policy);
        Optional<SemanticTransaction> exact = transactions.stream()
                .filter(value -> value.afterRevisionId().equals(revisionId))
                .max(Comparator.comparingLong(SemanticTransaction::sequence));
        if (exact.isPresent()) return exact;
        return transactions.stream()
                .filter(value -> value.stateRevisionIds().contains(revisionId))
                .max(Comparator.comparingLong(SemanticTransaction::sequence));
    }

    private List<PreferredRedo> preferredRedoEntries() {
        return preferredRedo.entrySet().stream()
                .map(entry -> new PreferredRedo(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparing(PreferredRedo::parentRevisionId))
                .toList();
    }

    private void validateRawEventReferences(List<String> ids) {
        Set<String> unique = new HashSet<>();
        for (String id : ids) {
            id = requireText(id, "rawEventId");
            if (!unique.add(id)) throw new IllegalArgumentException("Duplicate raw event reference " + id);
            if (!rawEvents.containsKey(id)) throw new IllegalArgumentException("Unknown raw event " + id);
        }
    }

    private HeadMoveResult noChange(String message) {
        return new HeadMoveResult(
                HeadMoveStatus.NO_CHANGE,
                currentRevisionId,
                currentRevisionId,
                List.of(),
                Optional.empty(),
                message,
                generation
        );
    }

    private HeadMoveResult ambiguous(List<String> candidates, String message) {
        return new HeadMoveResult(
                HeadMoveStatus.AMBIGUOUS,
                currentRevisionId,
                currentRevisionId,
                candidates,
                Optional.empty(),
                message,
                generation
        );
    }

    private HeadMoveResult rejected(List<String> candidates, String message) {
        return new HeadMoveResult(
                HeadMoveStatus.REJECTED,
                currentRevisionId,
                currentRevisionId,
                candidates,
                Optional.empty(),
                message,
                generation
        );
    }

    private DocumentRevision requireRevision(String id) {
        DocumentRevision revision = revisions.get(requireText(id, "revisionId"));
        if (revision == null) throw new IllegalArgumentException("Unknown document revision " + id);
        return revision;
    }

    private ChangeNotification notification(ChangeKind kind, String subjectId) {
        synchronized (this) {
            return new ChangeNotification(identity, generation, currentRevisionId, kind, subjectId);
        }
    }

    private void notifyListeners(ChangeKind kind, String subjectId) {
        ChangeNotification notification = new ChangeNotification(
                identity,
                generation,
                currentRevisionId,
                kind,
                subjectId
        );
        for (Listener listener : listeners) listener.changed(notification);
    }

    private static DocumentRevision requireImportedRevision(
            Map<String, DocumentRevision> revisions,
            String id
    ) {
        DocumentRevision revision = revisions.get(id);
        if (revision == null) throw new IllegalArgumentException("Archive references unknown revision " + id);
        return revision;
    }

    private static RawInputEvent requireImportedRaw(Map<String, RawInputEvent> rawEvents, String id) {
        RawInputEvent event = rawEvents.get(id);
        if (event == null) throw new IllegalArgumentException("Archive references unknown raw event " + id);
        return event;
    }

    private static boolean isDescendant(
            Map<String, DocumentRevision> revisions,
            String ancestor,
            String candidate
    ) {
        String cursor = candidate;
        while (!cursor.equals(ancestor)) {
            DocumentRevision revision = revisions.get(cursor);
            if (revision == null || revision.parentRevisionId().isEmpty()) return false;
            cursor = revision.parentRevisionId().orElseThrow();
        }
        return true;
    }

    private static String requireText(String value, String label) {
        value = SFMDocumentHistoryContract.requireWellFormedUtf16(value, label);
        if (value.isEmpty()) throw new IllegalArgumentException(label + " must not be empty");
        return value;
    }
}
