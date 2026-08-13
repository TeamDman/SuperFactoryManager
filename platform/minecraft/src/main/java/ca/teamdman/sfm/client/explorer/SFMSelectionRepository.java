package ca.teamdman.sfm.client.explorer;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Session-scoped immutable selection ledger with idempotent request replay.
 *
 * <p>Membership revisions are never edited or deleted. Undo and redo move an
 * explicit selection head and append a separate provenance event.</p>
 */
public final class SFMSelectionRepository implements SFMSelectorRepository<SFMSelectionId> {
    /**
     * Opaque, complete repository state used by a caller that already holds this
     * repository's monitor while publishing a larger all-or-none transaction.
     */
    public static final class TransactionSnapshot {
        private final Map<SFMSelectionId, SFMSelection> selections;
        private final Map<String, SFMSelectionId> selectionIdsByName;
        private final Map<Long, SFMSelectionRevision> revisions;
        private final List<SFMSelectionHeadEvent> headEvents;
        private final Map<RequestKey, AppliedRequest> requests;
        private final long generation;
        private final long nextSelectionOrdinal;
        private final long nextRevisionId;
        private final long nextHeadEventId;

        private TransactionSnapshot(SFMSelectionRepository repository) {
            selections = Map.copyOf(repository.selections);
            selectionIdsByName = Map.copyOf(repository.selectionIdsByName);
            revisions = Map.copyOf(repository.revisions);
            headEvents = List.copyOf(repository.headEvents);
            requests = Map.copyOf(repository.requests);
            generation = repository.generation;
            nextSelectionOrdinal = repository.nextSelectionOrdinal;
            nextRevisionId = repository.nextRevisionId;
            nextHeadEventId = repository.nextHeadEventId;
        }
    }

    public record StateSnapshot(
            long generation,
            Map<SFMSelectionId, SFMSelection> selections,
            Map<Long, SFMSelectionRevision> revisions,
            List<SFMSelectionHeadEvent> headEvents
    ) {
        public StateSnapshot {
            if (generation < 0) throw new IllegalArgumentException("Generation must not be negative");
            Comparator<SFMSelectionId> ids = Comparator.comparing(SFMSelectionId::value);
            TreeMap<SFMSelectionId, SFMSelection> immutableSelections = new TreeMap<>(ids);
            immutableSelections.putAll(selections);
            selections = Collections.unmodifiableMap(immutableSelections);
            revisions = Collections.unmodifiableMap(new TreeMap<>(revisions));
            headEvents = List.copyOf(headEvents);
        }
    }

    public record MutationResult(
            boolean changed,
            boolean replayed,
            long repositoryGeneration,
            SFMSelection selection,
            SFMSelectionRevision revision,
            Optional<SFMSelectionHeadEvent> headEvent
    ) {
        public MutationResult {
            if (repositoryGeneration < 0) {
                throw new IllegalArgumentException("Repository generation must not be negative");
            }
            Objects.requireNonNull(selection, "selection");
            Objects.requireNonNull(revision, "revision");
            Objects.requireNonNull(headEvent, "headEvent");
            if (!selection.id().equals(revision.selectionId())) {
                throw new IllegalArgumentException("Mutation result selection and revision must agree");
            }
        }

        private MutationResult asReplay() {
            return new MutationResult(
                    false,
                    true,
                    repositoryGeneration,
                    selection,
                    revision,
                    headEvent
            );
        }
    }

    private enum RequestKind {
        CREATE,
        ADD,
        REMOVE,
        UNION,
        INTERSECTION,
        DIFFERENCE,
        UNDO,
        REDO
    }

    private record RequestSignature(
            RequestKind kind,
            Optional<SFMSelectionId> target,
            Optional<String> name,
            List<SFMSelectionId> sources,
            Set<SFMPath> paths
    ) {
        private RequestSignature {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(target, "target");
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(sources, "sources");
            Objects.requireNonNull(paths, "paths");
            sources = List.copyOf(sources);
            paths = Collections.unmodifiableSet(new TreeSet<>(paths));
        }
    }

    private record AppliedRequest(RequestSignature signature, MutationResult result) {
    }

    private record RequestKey(String actor, String requestId) {
        private RequestKey {
            requireRequest(actor, requestId);
        }
    }

    private final Clock clock;
    private final Map<SFMSelectionId, SFMSelection> selections = new HashMap<>();
    private final Map<String, SFMSelectionId> selectionIdsByName = new HashMap<>();
    private final Map<Long, SFMSelectionRevision> revisions = new HashMap<>();
    private final List<SFMSelectionHeadEvent> headEvents = new ArrayList<>();
    private final Map<RequestKey, AppliedRequest> requests = new HashMap<>();
    private long generation;
    private long nextSelectionOrdinal = 1;
    private long nextRevisionId = 1;
    private long nextHeadEventId = 1;

    public SFMSelectionRepository() {
        this(Clock.systemUTC());
    }

    public SFMSelectionRepository(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public synchronized SFMSelectorRepositorySnapshot<SFMSelectionId> snapshot() {
        return selectorSnapshotLocked();
    }

    public synchronized StateSnapshot stateSnapshot() {
        return new StateSnapshot(generation, selections, revisions, headEvents);
    }

    public synchronized TransactionSnapshot transactionSnapshot() {
        return new TransactionSnapshot(this);
    }

    public synchronized void restoreTransactionSnapshot(TransactionSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        selections.clear();
        selections.putAll(snapshot.selections);
        selectionIdsByName.clear();
        selectionIdsByName.putAll(snapshot.selectionIdsByName);
        revisions.clear();
        revisions.putAll(snapshot.revisions);
        headEvents.clear();
        headEvents.addAll(snapshot.headEvents);
        requests.clear();
        requests.putAll(snapshot.requests);
        generation = snapshot.generation;
        nextSelectionOrdinal = snapshot.nextSelectionOrdinal;
        nextRevisionId = snapshot.nextRevisionId;
        nextHeadEventId = snapshot.nextHeadEventId;
    }

    public synchronized Optional<SFMSelection> selection(SFMSelectionId id) {
        return Optional.ofNullable(selections.get(Objects.requireNonNull(id, "id")));
    }

    public synchronized Optional<SFMSelectionRevision> revision(long id) {
        return Optional.ofNullable(revisions.get(id));
    }

    public synchronized MutationResult create(
            Optional<String> name,
            Collection<SFMPath> initialMembers,
            String actor,
            String requestId
    ) {
        RequestSignature signature = signature(
                RequestKind.CREATE,
                Optional.empty(),
                name,
                List.of(),
                initialMembers
        );
        MutationResult replay = replay(actor, requestId, signature);
        if (replay != null) return replay;
        SFMSelectionId id;
        do {
            id = new SFMSelectionId("selection-" + nextSelectionOrdinal++);
        } while (selections.containsKey(id) || selectionIdsByName.containsKey(id.value()));
        return createLocked(id, name, initialMembers, actor, requestId, signature);
    }

    public synchronized MutationResult create(
            SFMSelectionId id,
            Optional<String> name,
            Collection<SFMPath> initialMembers,
            String actor,
            String requestId
    ) {
        Objects.requireNonNull(id, "id");
        RequestSignature signature = signature(
                RequestKind.CREATE,
                Optional.of(id),
                name,
                List.of(),
                initialMembers
        );
        MutationResult replay = replay(actor, requestId, signature);
        if (replay != null) return replay;
        return createLocked(id, name, initialMembers, actor, requestId, signature);
    }

    public synchronized MutationResult add(
            SFMSelectionId id,
            Collection<SFMPath> paths,
            String actor,
            String requestId
    ) {
        return mutateMembers(RequestKind.ADD, id, paths, actor, requestId);
    }

    public synchronized MutationResult remove(
            SFMSelectionId id,
            Collection<SFMPath> paths,
            String actor,
            String requestId
    ) {
        return mutateMembers(RequestKind.REMOVE, id, paths, actor, requestId);
    }

    public synchronized MutationResult union(
            SFMSelectionId resultId,
            Optional<String> resultName,
            List<SFMSelectionId> sources,
            String actor,
            String requestId
    ) {
        return derive(
                RequestKind.UNION,
                resultId,
                resultName,
                requireSources(sources, 1, "Union"),
                actor,
                requestId
        );
    }

    public synchronized MutationResult intersection(
            SFMSelectionId resultId,
            Optional<String> resultName,
            List<SFMSelectionId> sources,
            String actor,
            String requestId
    ) {
        return derive(
                RequestKind.INTERSECTION,
                resultId,
                resultName,
                requireSources(sources, 1, "Intersection"),
                actor,
                requestId
        );
    }

    public synchronized MutationResult difference(
            SFMSelectionId resultId,
            Optional<String> resultName,
            SFMSelectionId include,
            List<SFMSelectionId> exclude,
            String actor,
            String requestId
    ) {
        Objects.requireNonNull(include, "include");
        Objects.requireNonNull(exclude, "exclude");
        if (exclude.isEmpty()) throw new IllegalArgumentException("Difference requires an exclusion");
        ArrayList<SFMSelectionId> sources = new ArrayList<>();
        sources.add(include);
        sources.addAll(exclude);
        return derive(
                RequestKind.DIFFERENCE,
                resultId,
                resultName,
                List.copyOf(sources),
                actor,
                requestId
        );
    }

    public synchronized MutationResult undo(
            SFMSelectionId id,
            String actor,
            String requestId
    ) {
        return moveHead(SFMSelectionHeadEvent.Kind.UNDO, id, actor, requestId);
    }

    public synchronized MutationResult redo(
            SFMSelectionId id,
            String actor,
            String requestId
    ) {
        return moveHead(SFMSelectionHeadEvent.Kind.REDO, id, actor, requestId);
    }

    public synchronized SFMSelectionMembersResolution resolveMembers(SFMEntitySelector selector) {
        Objects.requireNonNull(selector, "selector");
        SFMSelectorResolution<SFMSelectionId> selectorResolution = SFMEntitySelectorResolver.resolve(
                selector,
                SFMSelectorDomains.selections(SFMSelectorRepository.immutable(selectorSnapshotLocked()))
        );
        TreeMap<SFMSelectionId, Long> capturedHeads = new TreeMap<>(
                Comparator.comparing(SFMSelectionId::value)
        );
        TreeSet<SFMPath> members = new TreeSet<>();
        if (selectorResolution.complete()) {
            for (SFMSelectionId id : selectorResolution.identities()) {
                SFMSelection selection = requireSelection(id);
                SFMSelectionRevision revision = requireRevision(selection.headRevisionId());
                capturedHeads.put(id, revision.id());
                members.addAll(revision.members());
            }
        }
        return new SFMSelectionMembersResolution(selectorResolution, capturedHeads, members);
    }

    public synchronized SFMSelectionPathResolution resolve(SFMPath path) {
        Objects.requireNonNull(path, "path");
        if (path.kind() != SFMPath.Kind.SELECTION) {
            return pathFailure(
                    path,
                    SFMSelectionPathResolution.Completeness.INVALID,
                    "selection.path-kind-required",
                    "Only selection paths can be resolved by the selection repository"
            );
        }
        SFMSelectionId id = new SFMSelectionId(path.authority());
        SFMSelection selection = selections.get(id);
        if (selection == null) {
            SFMSelectionId namedId = selectionIdsByName.get(path.authority());
            if (namedId != null) {
                id = namedId;
                selection = selections.get(namedId);
            }
        }
        if (selection == null) {
            return pathFailure(
                    path,
                    SFMSelectionPathResolution.Completeness.MISSING,
                    "selection.missing",
                    "No selection has the stable id " + id.value()
            );
        }

        boolean pinned = path.revision().isPresent();
        long revisionId;
        if (pinned) {
            Optional<Long> parsedRevision = parseRevisionToken(path.revision().orElseThrow());
            if (parsedRevision.isEmpty()) {
                return pathFailure(
                        path,
                        SFMSelectionPathResolution.Completeness.INVALID,
                        "selection.invalid-revision-token",
                        "Selection revision must use revision-<positive integer>"
                );
            }
            revisionId = parsedRevision.orElseThrow();
        } else {
            revisionId = selection.headRevisionId();
        }
        SFMSelectionRevision revision = revisions.get(revisionId);
        if (revision == null || !revision.selectionId().equals(id)) {
            return pathFailure(
                    path,
                    SFMSelectionPathResolution.Completeness.MISSING,
                    "selection.revision-missing",
                    "The requested revision does not belong to selection " + id.value()
            );
        }
        return new SFMSelectionPathResolution(
                path,
                generation,
                SFMSelectionPathResolution.Completeness.COMPLETE,
                Optional.of(id),
                Optional.of(revision.id()),
                pinned,
                revision.members(),
                List.of()
        );
    }

    public synchronized SFMPath livePath(SFMSelectionId id) {
        requireSelection(id);
        return new SFMPath(
                SFMPath.Kind.SELECTION,
                "selection",
                id.value(),
                List.of(),
                Optional.empty(),
                false
        );
    }

    public synchronized SFMPath pinnedPath(SFMSelectionId id, long revisionId) {
        SFMSelectionRevision revision = requireRevision(revisionId);
        if (!revision.selectionId().equals(id)) {
            throw new IllegalArgumentException("Revision does not belong to the requested selection");
        }
        return new SFMPath(
                SFMPath.Kind.SELECTION,
                "selection",
                id.value(),
                List.of(),
                Optional.of(revision.revisionToken()),
                false
        );
    }

    private MutationResult createLocked(
            SFMSelectionId id,
            Optional<String> name,
            Collection<SFMPath> initialMembers,
            String actor,
            String requestId,
            RequestSignature signature
    ) {
        requireRequest(actor, requestId);
        Objects.requireNonNull(name, "name");
        assertAvailableIdentity(id, name);
        TreeSet<SFMPath> members = immutablePaths(initialMembers);
        SFMSelectionRevision revision = newRevision(
                id,
                List.of(),
                members,
                new SFMSelectionRevision.Operation(
                        SFMSelectionRevision.OperationKind.CREATE,
                        List.of(),
                        members
                ),
                actor,
                requestId
        );
        SFMSelection selection = new SFMSelection(
                id,
                name,
                SFMSelection.Lifetime.SESSION,
                revision.id(),
                List.of(),
                List.of()
        );
        selections.put(id, selection);
        name.ifPresent(value -> selectionIdsByName.put(value, id));
        revisions.put(revision.id(), revision);
        generation++;
        return remember(actor, requestId, signature, result(true, selection, revision, Optional.empty()));
    }

    private MutationResult mutateMembers(
            RequestKind kind,
            SFMSelectionId id,
            Collection<SFMPath> paths,
            String actor,
            String requestId
    ) {
        Objects.requireNonNull(id, "id");
        TreeSet<SFMPath> operands = immutablePaths(paths);
        RequestSignature signature = signature(
                kind,
                Optional.of(id),
                Optional.empty(),
                List.of(id),
                operands
        );
        MutationResult replay = replay(actor, requestId, signature);
        if (replay != null) return replay;
        requireRequest(actor, requestId);
        SFMSelection selection = requireSelection(id);
        SFMSelectionRevision previous = requireRevision(selection.headRevisionId());
        TreeSet<SFMPath> nextMembers = new TreeSet<>(previous.members());
        if (kind == RequestKind.ADD) {
            nextMembers.addAll(operands);
        } else if (kind == RequestKind.REMOVE) {
            nextMembers.removeAll(operands);
        } else {
            throw new IllegalArgumentException("Member mutation requires add or remove");
        }
        if (nextMembers.equals(previous.members())) {
            return remember(actor, requestId, signature, result(false, selection, previous, Optional.empty()));
        }
        SFMSelectionRevision revision = newRevision(
                id,
                List.of(previous.id()),
                nextMembers,
                new SFMSelectionRevision.Operation(
                        kind == RequestKind.ADD
                                ? SFMSelectionRevision.OperationKind.ADD
                                : SFMSelectionRevision.OperationKind.REMOVE,
                        List.of(id),
                        operands
                ),
                actor,
                requestId
        );
        ArrayList<Long> undo = new ArrayList<>(selection.undoRevisionIds());
        undo.add(previous.id());
        SFMSelection updated = new SFMSelection(
                id,
                selection.name(),
                selection.lifetime(),
                revision.id(),
                undo,
                List.of()
        );
        selections.put(id, updated);
        revisions.put(revision.id(), revision);
        generation++;
        return remember(actor, requestId, signature, result(true, updated, revision, Optional.empty()));
    }

    private MutationResult derive(
            RequestKind kind,
            SFMSelectionId resultId,
            Optional<String> resultName,
            List<SFMSelectionId> sources,
            String actor,
            String requestId
    ) {
        Objects.requireNonNull(resultId, "resultId");
        RequestSignature signature = signature(
                kind,
                Optional.of(resultId),
                resultName,
                sources,
                Set.of()
        );
        MutationResult replay = replay(actor, requestId, signature);
        if (replay != null) return replay;
        requireRequest(actor, requestId);
        assertAvailableIdentity(resultId, resultName);
        ArrayList<SFMSelectionRevision> sourceRevisions = new ArrayList<>();
        for (SFMSelectionId source : sources) {
            SFMSelection selection = requireSelection(source);
            sourceRevisions.add(requireRevision(selection.headRevisionId()));
        }
        TreeSet<SFMPath> members = new TreeSet<>(sourceRevisions.get(0).members());
        if (kind == RequestKind.UNION) {
            sourceRevisions.subList(1, sourceRevisions.size())
                    .forEach(revision -> members.addAll(revision.members()));
        } else if (kind == RequestKind.INTERSECTION) {
            sourceRevisions.subList(1, sourceRevisions.size())
                    .forEach(revision -> members.retainAll(revision.members()));
        } else if (kind == RequestKind.DIFFERENCE) {
            sourceRevisions.subList(1, sourceRevisions.size())
                    .forEach(revision -> members.removeAll(revision.members()));
        } else {
            throw new IllegalArgumentException("Derived selection requires a set operation");
        }
        SFMSelectionRevision revision = newRevision(
                resultId,
                sourceRevisions.stream().map(SFMSelectionRevision::id).toList(),
                members,
                new SFMSelectionRevision.Operation(operationKind(kind), sources, Set.of()),
                actor,
                requestId
        );
        SFMSelection selection = new SFMSelection(
                resultId,
                resultName,
                SFMSelection.Lifetime.SESSION,
                revision.id(),
                List.of(),
                List.of()
        );
        selections.put(resultId, selection);
        resultName.ifPresent(value -> selectionIdsByName.put(value, resultId));
        revisions.put(revision.id(), revision);
        generation++;
        return remember(actor, requestId, signature, result(true, selection, revision, Optional.empty()));
    }

    private MutationResult moveHead(
            SFMSelectionHeadEvent.Kind kind,
            SFMSelectionId id,
            String actor,
            String requestId
    ) {
        Objects.requireNonNull(id, "id");
        RequestKind requestKind = kind == SFMSelectionHeadEvent.Kind.UNDO
                ? RequestKind.UNDO
                : RequestKind.REDO;
        RequestSignature signature = signature(
                requestKind,
                Optional.of(id),
                Optional.empty(),
                List.of(id),
                Set.of()
        );
        MutationResult replay = replay(actor, requestId, signature);
        if (replay != null) return replay;
        requireRequest(actor, requestId);
        SFMSelection selection = requireSelection(id);
        List<Long> source = kind == SFMSelectionHeadEvent.Kind.UNDO
                ? selection.undoRevisionIds()
                : selection.redoRevisionIds();
        SFMSelectionRevision current = requireRevision(selection.headRevisionId());
        if (source.isEmpty()) {
            return remember(actor, requestId, signature, result(false, selection, current, Optional.empty()));
        }

        long targetRevisionId = source.get(source.size() - 1);
        ArrayList<Long> undo = new ArrayList<>(selection.undoRevisionIds());
        ArrayList<Long> redo = new ArrayList<>(selection.redoRevisionIds());
        if (kind == SFMSelectionHeadEvent.Kind.UNDO) {
            undo.remove(undo.size() - 1);
            redo.add(current.id());
        } else {
            redo.remove(redo.size() - 1);
            undo.add(current.id());
        }
        SFMSelection updated = new SFMSelection(
                id,
                selection.name(),
                selection.lifetime(),
                targetRevisionId,
                undo,
                redo
        );
        SFMSelectionHeadEvent event = new SFMSelectionHeadEvent(
                nextHeadEventId++,
                id,
                current.id(),
                targetRevisionId,
                kind,
                actor,
                requestId,
                clock.instant()
        );
        selections.put(id, updated);
        headEvents.add(event);
        generation++;
        return remember(
                actor,
                requestId,
                signature,
                result(true, updated, requireRevision(targetRevisionId), Optional.of(event))
        );
    }

    private SFMSelectionRevision newRevision(
            SFMSelectionId selectionId,
            List<Long> parentRevisionIds,
            Set<SFMPath> members,
            SFMSelectionRevision.Operation operation,
            String actor,
            String requestId
    ) {
        return new SFMSelectionRevision(
                nextRevisionId++,
                selectionId,
                parentRevisionIds,
                members,
                operation,
                actor,
                requestId,
                clock.instant()
        );
    }

    private MutationResult result(
            boolean changed,
            SFMSelection selection,
            SFMSelectionRevision revision,
            Optional<SFMSelectionHeadEvent> headEvent
    ) {
        return new MutationResult(changed, false, generation, selection, revision, headEvent);
    }

    private MutationResult replay(String actor, String requestId, RequestSignature signature) {
        RequestKey key = new RequestKey(actor, requestId);
        AppliedRequest previous = requests.get(key);
        if (previous == null) return null;
        if (!previous.signature().equals(signature)) {
            throw new IllegalArgumentException("Request id was already used for a different selection operation");
        }
        return previous.result().asReplay();
    }

    private MutationResult remember(
            String actor,
            String requestId,
            RequestSignature signature,
            MutationResult result
    ) {
        requests.put(new RequestKey(actor, requestId), new AppliedRequest(signature, result));
        return result;
    }

    private SFMSelectorRepositorySnapshot<SFMSelectionId> selectorSnapshotLocked() {
        ArrayList<SFMSelectorRepositoryEntry<SFMSelectionId>> entries = new ArrayList<>();
        selections.values().stream()
                .sorted(Comparator.comparing(selection -> selection.id().value()))
                .forEach(selection -> entries.add(new SFMSelectorRepositoryEntry<>(
                        selection.id(),
                        selection.name(),
                        false
                )));
        return new SFMSelectorRepositorySnapshot<>(generation, entries);
    }

    private SFMSelectionPathResolution pathFailure(
            SFMPath path,
            SFMSelectionPathResolution.Completeness completeness,
            String code,
            String message
    ) {
        return new SFMSelectionPathResolution(
                path,
                generation,
                completeness,
                Optional.empty(),
                Optional.empty(),
                path.revision().isPresent(),
                Set.of(),
                List.of(new SFMSelectorResolution.Diagnostic(
                        code,
                        SFMSelectorResolution.Severity.ERROR,
                        message
                ))
        );
    }

    private static Optional<Long> parseRevisionToken(String token) {
        if (!token.startsWith("revision-") || token.length() == "revision-".length()) {
            return Optional.empty();
        }
        try {
            long value = Long.parseLong(token.substring("revision-".length()));
            return value > 0 ? Optional.of(value) : Optional.empty();
        } catch (NumberFormatException ignored) {
            return Optional.empty();
        }
    }

    private SFMSelection requireSelection(SFMSelectionId id) {
        Objects.requireNonNull(id, "id");
        SFMSelection selection = selections.get(id);
        if (selection == null) throw new IllegalArgumentException("Unknown selection id: " + id);
        return selection;
    }

    private void assertAvailableIdentity(SFMSelectionId id, Optional<String> name) {
        if (selections.containsKey(id) || selectionIdsByName.containsKey(id.value())) {
            throw new IllegalArgumentException("Selection id already exists or is used as a name: " + id);
        }
        if (name.isEmpty()) return;
        String value = name.orElseThrow();
        if (selectionIdsByName.containsKey(value)
                || selections.containsKey(new SFMSelectionId(value))) {
            throw new IllegalArgumentException("Selection name already exists or is used as an id: " + value);
        }
    }

    private SFMSelectionRevision requireRevision(long id) {
        SFMSelectionRevision revision = revisions.get(id);
        if (revision == null) throw new IllegalStateException("Unknown selection revision: " + id);
        return revision;
    }

    private static TreeSet<SFMPath> immutablePaths(Collection<SFMPath> paths) {
        Objects.requireNonNull(paths, "paths");
        TreeSet<SFMPath> answer = new TreeSet<>();
        for (SFMPath path : paths) answer.add(Objects.requireNonNull(path, "path"));
        return answer;
    }

    private static List<SFMSelectionId> requireSources(
            List<SFMSelectionId> sources,
            int minimum,
            String label
    ) {
        Objects.requireNonNull(sources, "sources");
        sources = List.copyOf(sources);
        sources.forEach(source -> Objects.requireNonNull(source, "source"));
        if (sources.size() < minimum) {
            throw new IllegalArgumentException(label + " requires at least " + minimum + " source selection(s)");
        }
        return sources;
    }

    private static RequestSignature signature(
            RequestKind kind,
            Optional<SFMSelectionId> target,
            Optional<String> name,
            List<SFMSelectionId> sources,
            Collection<SFMPath> paths
    ) {
        Objects.requireNonNull(name, "name");
        name.ifPresent(value -> {
            SFMCanonicalText.requireValidUnicode(value, "selection.invalid-name");
            if (value.isEmpty()) throw new IllegalArgumentException("Selection name must not be empty");
        });
        return new RequestSignature(kind, target, name, sources, immutablePaths(paths));
    }

    private static SFMSelectionRevision.OperationKind operationKind(RequestKind kind) {
        return switch (kind) {
            case UNION -> SFMSelectionRevision.OperationKind.UNION;
            case INTERSECTION -> SFMSelectionRevision.OperationKind.INTERSECTION;
            case DIFFERENCE -> SFMSelectionRevision.OperationKind.DIFFERENCE;
            default -> throw new IllegalArgumentException("Request kind is not a set operation: " + kind);
        };
    }

    private static void requireRequest(String actor, String requestId) {
        requireActor(actor);
        requireRequestId(requestId);
    }

    private static void requireActor(String actor) {
        Objects.requireNonNull(actor, "actor");
        SFMCanonicalText.requireValidUnicode(actor, "selection.invalid-actor");
        if (actor.isEmpty()) throw new IllegalArgumentException("Actor must not be empty");
    }

    private static void requireRequestId(String requestId) {
        Objects.requireNonNull(requestId, "requestId");
        SFMCanonicalText.requireValidUnicode(requestId, "selection.invalid-request-id");
        if (requestId.isEmpty()) throw new IllegalArgumentException("Request id must not be empty");
    }
}
