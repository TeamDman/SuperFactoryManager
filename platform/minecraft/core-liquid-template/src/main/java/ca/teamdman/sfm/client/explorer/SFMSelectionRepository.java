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
    public static final String ARCHIVE_SCHEMA = "sfm.selection-history/1";
    /**
     * Opaque, complete repository state used by a caller that already holds this
     * repository's monitor while publishing a larger all-or-none transaction.
     */
    public static final class TransactionSnapshot {
        private final Map<SFMSelectionId, SFMSelection> selections;
        private final Map<String, SFMSelectionId> selectionIdsByName;
        private final Map<Long, SFMSelectionRevision> revisions;
        private final Map<Long, Set<Long>> childRevisionIds;
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
            childRevisionIds = immutableChildren(repository.childRevisionIds);
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
            Map<Long, Set<Long>> childRevisionIds,
            List<SFMSelectionHeadEvent> headEvents
    ) {
        public StateSnapshot {
            if (generation < 0) throw new IllegalArgumentException("Generation must not be negative");
            Comparator<SFMSelectionId> ids = Comparator.comparing(SFMSelectionId::value);
            TreeMap<SFMSelectionId, SFMSelection> immutableSelections = new TreeMap<>(ids);
            immutableSelections.putAll(selections);
            selections = Collections.unmodifiableMap(immutableSelections);
            revisions = Collections.unmodifiableMap(new TreeMap<>(revisions));
            childRevisionIds = immutableChildren(childRevisionIds);
            headEvents = List.copyOf(headEvents);
        }
    }

    /** Canonically ordered, request-cache-free session interchange. */
    public record Archive(
            String schema,
            long generation,
            List<SFMSelection> selections,
            List<SFMSelectionRevision> revisions,
            List<SFMSelectionHeadEvent> headEvents
    ) {
        public Archive {
            if (!ARCHIVE_SCHEMA.equals(schema)) {
                throw new IllegalArgumentException("Unsupported selection archive schema: " + schema);
            }
            if (generation < 0) throw new IllegalArgumentException("Generation must not be negative");
            Objects.requireNonNull(selections, "selections");
            Objects.requireNonNull(revisions, "revisions");
            Objects.requireNonNull(headEvents, "headEvents");
            selections = selections.stream()
                    .sorted(Comparator.comparing(selection -> selection.id().value()))
                    .toList();
            revisions = revisions.stream()
                    .sorted(Comparator.comparingLong(SFMSelectionRevision::id))
                    .toList();
            headEvents = headEvents.stream()
                    .sorted(Comparator.comparingLong(SFMSelectionHeadEvent::id))
                    .toList();
        }
    }

    public enum HeadNavigationStatus {
        NOT_HEAD_NAVIGATION,
        MOVED,
        NO_CANDIDATE,
        AMBIGUOUS,
        ALREADY_CURRENT
    }

    public record MutationResult(
            boolean changed,
            boolean replayed,
            long repositoryGeneration,
            SFMSelection selection,
            SFMSelectionRevision revision,
            Optional<SFMSelectionHeadEvent> headEvent,
            HeadNavigationStatus headNavigationStatus,
            List<Long> candidateRevisionIds
    ) {
        public MutationResult {
            if (repositoryGeneration < 0) {
                throw new IllegalArgumentException("Repository generation must not be negative");
            }
            Objects.requireNonNull(selection, "selection");
            Objects.requireNonNull(revision, "revision");
            Objects.requireNonNull(headEvent, "headEvent");
            Objects.requireNonNull(headNavigationStatus, "headNavigationStatus");
            Objects.requireNonNull(candidateRevisionIds, "candidateRevisionIds");
            candidateRevisionIds = List.copyOf(candidateRevisionIds);
            if (candidateRevisionIds.stream().anyMatch(value -> value == null || value <= 0)) {
                throw new IllegalArgumentException("Candidate revision ids must be positive");
            }
            candidateRevisionIds = candidateRevisionIds.stream().sorted().distinct().toList();
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
                    headEvent,
                    headNavigationStatus,
                    candidateRevisionIds
            );
        }
    }

    private enum RequestKind {
        CREATE,
        ADD,
        REMOVE,
        REPLACE,
        UNION,
        INTERSECTION,
        DIFFERENCE,
        UNDO,
        REDO,
        CHECKOUT,
        NAME_HEAD
    }

    private record RequestSignature(
            RequestKind kind,
            Optional<SFMSelectionId> target,
            Optional<String> name,
            Optional<Long> revisionId,
            List<SFMSelectionId> sources,
            Set<SFMPath> paths
    ) {
        private RequestSignature {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(target, "target");
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(revisionId, "revisionId");
            revisionId.ifPresent(value -> {
                if (value <= 0) throw new IllegalArgumentException("Revision id must be positive");
            });
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
    private final Map<Long, Set<Long>> childRevisionIds = new HashMap<>();
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
        return new StateSnapshot(generation, selections, revisions, childRevisionIds, headEvents);
    }

    public synchronized Archive exportArchive() {
        return new Archive(
                ARCHIVE_SCHEMA,
                generation,
                List.copyOf(selections.values()),
                List.copyOf(revisions.values()),
                headEvents
        );
    }

    public synchronized void restoreArchive(Archive archive) {
        Objects.requireNonNull(archive, "archive");
        HashMap<Long, SFMSelectionRevision> importedRevisions = new HashMap<>();
        for (SFMSelectionRevision revision : archive.revisions()) {
            if (importedRevisions.put(revision.id(), revision) != null) {
                throw new IllegalArgumentException("Duplicate selection revision: " + revision.id());
            }
        }
        for (SFMSelectionRevision revision : archive.revisions()) {
            for (long parentId : revision.parentRevisionIds()) {
                SFMSelectionRevision parent = importedRevisions.get(parentId);
                if (parent == null) {
                    throw new IllegalArgumentException("Selection revision has missing parent: " + parentId);
                }
                if (parent.id() >= revision.id()) {
                    throw new IllegalArgumentException("Selection archive history must be acyclic and parent-first");
                }
            }
        }

        HashMap<SFMSelectionId, SFMSelection> importedSelections = new HashMap<>();
        HashMap<String, SFMSelectionId> importedNames = new HashMap<>();
        for (SFMSelection selection : archive.selections()) {
            if (importedSelections.put(selection.id(), selection) != null) {
                throw new IllegalArgumentException("Duplicate selection id: " + selection.id().value());
            }
            selection.name().ifPresent(name -> {
                if (importedNames.put(name, selection.id()) != null) {
                    throw new IllegalArgumentException("Duplicate selection name: " + name);
                }
            });
            validateSelectionHeads(selection, importedRevisions);
        }

        TreeMap<Long, Set<Long>> importedChildren = new TreeMap<>();
        importedRevisions.keySet().forEach(id -> importedChildren.put(id, new TreeSet<>()));
        for (SFMSelectionRevision revision : importedRevisions.values()) {
            for (long parentId : revision.parentRevisionIds()) {
                SFMSelectionRevision parent = importedRevisions.get(parentId);
                if (parent.selectionId().equals(revision.selectionId())) {
                    importedChildren.get(parentId).add(revision.id());
                }
            }
        }
        for (SFMSelection selection : importedSelections.values()) {
            selection.preferredChildRevisionIds().forEach((parent, child) -> {
                if (!importedChildren.getOrDefault(parent, Set.of()).contains(child)) {
                    throw new IllegalArgumentException("Preferred child is not a history edge: " + parent + " -> " + child);
                }
            });
        }

        TreeSet<Long> eventIds = new TreeSet<>();
        for (SFMSelectionHeadEvent event : archive.headEvents()) {
            if (!eventIds.add(event.id())) {
                throw new IllegalArgumentException("Duplicate selection head event: " + event.id());
            }
            SFMSelection selection = importedSelections.get(event.selectionId());
            if (selection == null) throw new IllegalArgumentException("Head event has unknown selection");
            for (long revisionId : List.of(event.fromRevisionId(), event.toRevisionId())) {
                SFMSelectionRevision revision = importedRevisions.get(revisionId);
                if (revision == null || !revision.selectionId().equals(selection.id())) {
                    throw new IllegalArgumentException("Head event revision does not belong to its selection");
                }
            }
        }

        selections.clear();
        selections.putAll(importedSelections);
        selectionIdsByName.clear();
        selectionIdsByName.putAll(importedNames);
        revisions.clear();
        revisions.putAll(importedRevisions);
        childRevisionIds.clear();
        importedChildren.forEach((parent, children) ->
                childRevisionIds.put(parent, new TreeSet<>(children)));
        headEvents.clear();
        headEvents.addAll(archive.headEvents());
        requests.clear();
        generation = archive.generation();
        nextRevisionId = importedRevisions.keySet().stream().mapToLong(Long::longValue).max().orElse(0L) + 1;
        nextHeadEventId = eventIds.stream().mapToLong(Long::longValue).max().orElse(0L) + 1;
        nextSelectionOrdinal = importedSelections.keySet().stream()
                .map(SFMSelectionId::value)
                .filter(value -> value.startsWith("selection-"))
                .map(value -> value.substring("selection-".length()))
                .mapToLong(value -> {
                    try {
                        return Long.parseLong(value);
                    } catch (NumberFormatException ignored) {
                        return 0L;
                    }
                })
                .max()
                .orElse(0L) + 1;
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
        childRevisionIds.clear();
        snapshot.childRevisionIds.forEach((parent, children) ->
                childRevisionIds.put(parent, new TreeSet<>(children)));
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
                Optional.empty(),
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
                Optional.empty(),
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

    /** One immutable revision for replacement; never publishes an intermediate empty selection. */
    public synchronized MutationResult replace(SFMSelectionId id, Collection<SFMPath> paths,
                                               String actor, String requestId) {
        return mutateMembers(RequestKind.REPLACE, id, paths, actor, requestId);
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
        return moveHead(SFMSelectionHeadEvent.Kind.UNDO, id, Optional.empty(), actor, requestId);
    }

    public synchronized MutationResult undo(
            SFMSelectionId id,
            long parentRevisionId,
            String actor,
            String requestId
    ) {
        return moveHead(
                SFMSelectionHeadEvent.Kind.UNDO,
                id,
                Optional.of(parentRevisionId),
                actor,
                requestId
        );
    }

    public synchronized MutationResult redo(
            SFMSelectionId id,
            String actor,
            String requestId
    ) {
        return moveHead(SFMSelectionHeadEvent.Kind.REDO, id, Optional.empty(), actor, requestId);
    }

    public synchronized MutationResult redo(
            SFMSelectionId id,
            long childRevisionId,
            String actor,
            String requestId
    ) {
        return moveHead(
                SFMSelectionHeadEvent.Kind.REDO,
                id,
                Optional.of(childRevisionId),
                actor,
                requestId
        );
    }

    public synchronized MutationResult checkout(
            SFMSelectionId id,
            long revisionId,
            String actor,
            String requestId
    ) {
        return moveHead(
                SFMSelectionHeadEvent.Kind.CHECKOUT,
                id,
                Optional.of(revisionId),
                actor,
                requestId
        );
    }

    public synchronized MutationResult nameHead(
            SFMSelectionId id,
            String headName,
            long revisionId,
            String actor,
            String requestId
    ) {
        return nameHeadLocked(id, headName, revisionId, actor, requestId);
    }

    public synchronized List<Long> undoCandidates(SFMSelectionId id) {
        SFMSelection selection = requireSelection(id);
        return historyParents(selection.id(), selection.headRevisionId());
    }

    public synchronized List<Long> redoCandidates(SFMSelectionId id) {
        SFMSelection selection = requireSelection(id);
        return historyChildren(selection.id(), selection.headRevisionId());
    }

    public synchronized List<SFMSelectionRevision> history(SFMSelectionId id) {
        requireSelection(id);
        return revisions.values().stream()
                .filter(revision -> revision.selectionId().equals(id))
                .sorted(Comparator.comparingLong(SFMSelectionRevision::id))
                .toList();
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
                Map.of(),
                Map.of()
        );
        selections.put(id, selection);
        name.ifPresent(value -> selectionIdsByName.put(value, id));
        publishRevision(revision);
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
        } else if (kind == RequestKind.REPLACE) {
            nextMembers.clear();
            nextMembers.addAll(operands);
        } else {
            throw new IllegalArgumentException("Member mutation requires add, remove or replace");
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
                                : kind == RequestKind.REMOVE ? SFMSelectionRevision.OperationKind.REMOVE
                                : SFMSelectionRevision.OperationKind.REPLACE,
                        List.of(id),
                        operands
                ),
                actor,
                requestId
        );
        TreeMap<Long, Long> preferredChildren = new TreeMap<>(selection.preferredChildRevisionIds());
        preferredChildren.put(previous.id(), revision.id());
        SFMSelection updated = new SFMSelection(
                id,
                selection.name(),
                selection.lifetime(),
                revision.id(),
                selection.namedHeadRevisionIds(),
                preferredChildren
        );
        selections.put(id, updated);
        publishRevision(revision);
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
                Optional.empty(),
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
                Map.of(),
                Map.of()
        );
        selections.put(resultId, selection);
        resultName.ifPresent(value -> selectionIdsByName.put(value, resultId));
        publishRevision(revision);
        generation++;
        return remember(actor, requestId, signature, result(true, selection, revision, Optional.empty()));
    }

    private MutationResult moveHead(
            SFMSelectionHeadEvent.Kind kind,
            SFMSelectionId id,
            Optional<Long> requestedRevisionId,
            String actor,
            String requestId
    ) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(requestedRevisionId, "requestedRevisionId");
        RequestKind requestKind = switch (kind) {
            case UNDO -> RequestKind.UNDO;
            case REDO -> RequestKind.REDO;
            case CHECKOUT -> RequestKind.CHECKOUT;
            case NAME_HEAD -> throw new IllegalArgumentException("Named heads use nameHead");
        };
        RequestSignature signature = signature(
                requestKind,
                Optional.of(id),
                Optional.empty(),
                requestedRevisionId,
                List.of(id),
                Set.of()
        );
        MutationResult replay = replay(actor, requestId, signature);
        if (replay != null) return replay;
        requireRequest(actor, requestId);
        SFMSelection selection = requireSelection(id);
        SFMSelectionRevision current = requireRevision(selection.headRevisionId());
        List<Long> candidates = switch (kind) {
            case UNDO -> historyParents(id, current.id());
            case REDO -> historyChildren(id, current.id());
            case CHECKOUT -> requestedRevisionId.map(List::of).orElseGet(List::of);
            case NAME_HEAD -> throw new IllegalArgumentException("Named heads use nameHead");
        };
        if (requestedRevisionId.isEmpty()) {
            if (candidates.isEmpty()) {
                return remember(
                        actor,
                        requestId,
                        signature,
                        headResult(false, selection, current, Optional.empty(),
                                HeadNavigationStatus.NO_CANDIDATE, candidates)
                );
            }
            if (candidates.size() > 1) {
                return remember(
                        actor,
                        requestId,
                        signature,
                        headResult(false, selection, current, Optional.empty(),
                                HeadNavigationStatus.AMBIGUOUS, candidates)
                );
            }
        }

        long targetRevisionId = requestedRevisionId.orElseGet(() -> candidates.get(0));
        SFMSelectionRevision target = requireRevision(targetRevisionId);
        if (!target.selectionId().equals(id)) {
            throw new IllegalArgumentException("Target revision does not belong to selection " + id.value());
        }
        if (kind != SFMSelectionHeadEvent.Kind.CHECKOUT && !candidates.contains(targetRevisionId)) {
            throw new IllegalArgumentException(
                    "Revision " + targetRevisionId + " is not a "
                            + kind.name().toLowerCase() + " candidate from " + current.id()
            );
        }
        if (targetRevisionId == current.id()) {
            return remember(
                    actor,
                    requestId,
                    signature,
                    headResult(false, selection, current, Optional.empty(),
                            HeadNavigationStatus.ALREADY_CURRENT, List.of(current.id()))
            );
        }

        TreeMap<Long, Long> preferredChildren = new TreeMap<>(selection.preferredChildRevisionIds());
        if (kind == SFMSelectionHeadEvent.Kind.UNDO) preferredChildren.put(targetRevisionId, current.id());
        if (kind == SFMSelectionHeadEvent.Kind.REDO) preferredChildren.put(current.id(), targetRevisionId);
        SFMSelection updated = new SFMSelection(
                id,
                selection.name(),
                selection.lifetime(),
                targetRevisionId,
                selection.namedHeadRevisionIds(),
                preferredChildren
        );
        SFMSelectionHeadEvent event = new SFMSelectionHeadEvent(
                nextHeadEventId++,
                id,
                current.id(),
                targetRevisionId,
                kind,
                Optional.empty(),
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
                headResult(true, updated, target, Optional.of(event),
                        HeadNavigationStatus.MOVED, candidates)
        );
    }

    private MutationResult nameHeadLocked(
            SFMSelectionId id,
            String headName,
            long revisionId,
            String actor,
            String requestId
    ) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(headName, "headName");
        SFMCanonicalText.requireValidUnicode(headName, "selection.invalid-head-name");
        if (headName.isEmpty()) throw new IllegalArgumentException("Head name must not be empty");
        RequestSignature signature = signature(
                RequestKind.NAME_HEAD,
                Optional.of(id),
                Optional.of(headName),
                Optional.of(revisionId),
                List.of(id),
                Set.of()
        );
        MutationResult replay = replay(actor, requestId, signature);
        if (replay != null) return replay;
        requireRequest(actor, requestId);
        SFMSelection selection = requireSelection(id);
        SFMSelectionRevision revision = requireRevision(revisionId);
        if (!revision.selectionId().equals(id)) {
            throw new IllegalArgumentException("Named-head revision does not belong to selection " + id.value());
        }
        if (selection.namedHead(headName).filter(value -> value == revisionId).isPresent()) {
            return remember(actor, requestId, signature,
                    result(false, selection, revision, Optional.empty()));
        }
        TreeMap<String, Long> namedHeads = new TreeMap<>(selection.namedHeadRevisionIds());
        namedHeads.put(headName, revisionId);
        SFMSelection updated = new SFMSelection(
                id,
                selection.name(),
                selection.lifetime(),
                selection.headRevisionId(),
                namedHeads,
                selection.preferredChildRevisionIds()
        );
        SFMSelectionHeadEvent event = new SFMSelectionHeadEvent(
                nextHeadEventId++,
                id,
                selection.headRevisionId(),
                revisionId,
                SFMSelectionHeadEvent.Kind.NAME_HEAD,
                Optional.of(headName),
                actor,
                requestId,
                clock.instant()
        );
        selections.put(id, updated);
        headEvents.add(event);
        generation++;
        return remember(actor, requestId, signature,
                result(true, updated, revision, Optional.of(event)));
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

    private void publishRevision(SFMSelectionRevision revision) {
        if (revisions.containsKey(revision.id())) {
            throw new IllegalStateException("Selection revision already exists: " + revision.id());
        }
        for (long parentId : revision.parentRevisionIds()) {
            SFMSelectionRevision parent = revisions.get(parentId);
            if (parent == null) {
                throw new IllegalStateException("Unknown parent selection revision: " + parentId);
            }
            if (parent.selectionId().equals(revision.selectionId())) {
                childRevisionIds
                        .computeIfAbsent(parentId, ignored -> new TreeSet<>())
                        .add(revision.id());
            }
        }
        revisions.put(revision.id(), revision);
        childRevisionIds.computeIfAbsent(revision.id(), ignored -> new TreeSet<>());
    }

    private List<Long> historyParents(SFMSelectionId selectionId, long revisionId) {
        return requireRevision(revisionId).parentRevisionIds().stream()
                .filter(parentId -> requireRevision(parentId).selectionId().equals(selectionId))
                .sorted()
                .toList();
    }

    private List<Long> historyChildren(SFMSelectionId selectionId, long revisionId) {
        return childRevisionIds.getOrDefault(revisionId, Set.of()).stream()
                .filter(childId -> requireRevision(childId).selectionId().equals(selectionId))
                .sorted()
                .toList();
    }

    private MutationResult result(
            boolean changed,
            SFMSelection selection,
            SFMSelectionRevision revision,
            Optional<SFMSelectionHeadEvent> headEvent
    ) {
        return new MutationResult(
                changed,
                false,
                generation,
                selection,
                revision,
                headEvent,
                HeadNavigationStatus.NOT_HEAD_NAVIGATION,
                List.of()
        );
    }

    private MutationResult headResult(
            boolean changed,
            SFMSelection selection,
            SFMSelectionRevision revision,
            Optional<SFMSelectionHeadEvent> headEvent,
            HeadNavigationStatus status,
            List<Long> candidates
    ) {
        return new MutationResult(
                changed,
                false,
                generation,
                selection,
                revision,
                headEvent,
                status,
                candidates
        );
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

    private static void validateSelectionHeads(
            SFMSelection selection,
            Map<Long, SFMSelectionRevision> availableRevisions
    ) {
        ArrayList<Long> heads = new ArrayList<>();
        heads.add(selection.headRevisionId());
        heads.addAll(selection.namedHeadRevisionIds().values());
        for (long revisionId : heads) {
            SFMSelectionRevision revision = availableRevisions.get(revisionId);
            if (revision == null || !revision.selectionId().equals(selection.id())) {
                throw new IllegalArgumentException(
                        "Selection head revision does not belong to " + selection.id().value()
                );
            }
        }
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

    private static Map<Long, Set<Long>> immutableChildren(Map<Long, ? extends Collection<Long>> values) {
        Objects.requireNonNull(values, "childRevisionIds");
        TreeMap<Long, Set<Long>> answer = new TreeMap<>();
        values.forEach((parent, children) -> {
            if (parent == null || parent <= 0) {
                throw new IllegalArgumentException("Child adjacency parent ids must be positive");
            }
            Objects.requireNonNull(children, "child revision ids");
            TreeSet<Long> ordered = new TreeSet<>();
            for (Long child : children) {
                if (child == null || child <= 0) {
                    throw new IllegalArgumentException("Child adjacency revision ids must be positive");
                }
                ordered.add(child);
            }
            answer.put(parent, Collections.unmodifiableSet(ordered));
        });
        return Collections.unmodifiableMap(answer);
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
            Optional<Long> revisionId,
            List<SFMSelectionId> sources,
            Collection<SFMPath> paths
    ) {
        Objects.requireNonNull(name, "name");
        name.ifPresent(value -> {
            SFMCanonicalText.requireValidUnicode(value, "selection.invalid-name");
            if (value.isEmpty()) throw new IllegalArgumentException("Selection name must not be empty");
        });
        return new RequestSignature(kind, target, name, revisionId, sources, immutablePaths(paths));
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
