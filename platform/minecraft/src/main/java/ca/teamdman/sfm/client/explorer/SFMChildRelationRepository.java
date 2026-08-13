package ca.teamdman.sfm.client.explorer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Versioned immediate-child relation store with fetch-before-publish semantics.
 *
 * <p>Resolver work happens elsewhere. This synchronized repository only starts
 * request generations and atomically publishes already staged pages.</p>
 */
public final class SFMChildRelationRepository {
    public enum PublishDisposition {
        PUBLISHED,
        STALE,
        FAILED,
        CANCELLED
    }

    public enum RequestMode {
        REPLACE,
        APPEND
    }

    public record RefreshTicket(
            long requestId,
            RequestMode mode,
            Set<SFMPath> parents,
            long resolverGeneration,
            Optional<String> expectedContinuation,
            long baseRevision
    ) {
        public RefreshTicket {
            if (requestId <= 0) throw new IllegalArgumentException("Request id must be positive");
            Objects.requireNonNull(mode, "mode");
            parents = Collections.unmodifiableSet(new TreeSet<>(parents));
            if (parents.isEmpty()) throw new IllegalArgumentException("Refresh requires at least one parent");
            if (resolverGeneration < 0) throw new IllegalArgumentException("Resolver generation must not be negative");
            Objects.requireNonNull(expectedContinuation, "expectedContinuation");
            if (mode == RequestMode.APPEND && parents.size() != 1) {
                throw new IllegalArgumentException("Append requests target exactly one parent");
            }
            if (mode == RequestMode.APPEND && expectedContinuation.isEmpty()) {
                throw new IllegalArgumentException("Append requests require an expected continuation");
            }
            if (mode == RequestMode.REPLACE && expectedContinuation.isPresent()) {
                throw new IllegalArgumentException("Replacement requests do not carry a continuation");
            }
        }
    }

    public record PageState(
            long resolverGeneration,
            Optional<String> continuation,
            SFMChildPage.Completeness completeness,
            Materialization materialization,
            List<String> diagnostics,
            long lastRequestId
    ) {
        public enum Materialization {
            MATERIALIZED,
            REFRESH_FAILED
        }

        public PageState {
            Objects.requireNonNull(continuation, "continuation");
            Objects.requireNonNull(completeness, "completeness");
            Objects.requireNonNull(materialization, "materialization");
            diagnostics = List.copyOf(diagnostics);
        }
    }

    public record Snapshot(
            SFMChildRelationRevision relation,
            long statusGeneration,
            Map<SFMPath, PageState> pageStates
    ) {
        public Snapshot {
            Objects.requireNonNull(relation, "relation");
            pageStates = Collections.unmodifiableMap(new TreeMap<>(pageStates));
        }
    }

    public record PublishResult(PublishDisposition disposition, Snapshot snapshot) {
        public PublishResult {
            Objects.requireNonNull(disposition, "disposition");
            Objects.requireNonNull(snapshot, "snapshot");
        }
    }

    private record ActiveRequest(long requestId, long resolverGeneration, RequestMode mode) {
    }

    private long nextRequestId = 1;
    private long nextRevisionId = 1;
    private long statusGeneration;
    private SFMChildRelationRevision relation = new SFMChildRelationRevision(0, Set.of());
    private final Map<SFMPath, PageState> pageStates = new HashMap<>();
    private final Map<SFMPath, ActiveRequest> activeRequests = new HashMap<>();
    private final Set<Long> cancelledRequestIds = new HashSet<>();

    public synchronized Snapshot snapshot() {
        return snapshotLocked();
    }

    public synchronized RefreshTicket beginRefresh(
            Collection<SFMPath> parents,
            long resolverGeneration
    ) {
        TreeSet<SFMPath> capturedParents = requireParents(parents);
        validateRefreshLocked(capturedParents, resolverGeneration);
        retireRequestsOverlappingLocked(capturedParents);
        long requestId = nextRequestId++;
        ActiveRequest active = new ActiveRequest(requestId, resolverGeneration, RequestMode.REPLACE);
        capturedParents.forEach(parent -> activeRequests.put(parent, active));
        return new RefreshTicket(
                requestId,
                RequestMode.REPLACE,
                capturedParents,
                resolverGeneration,
                Optional.empty(),
                relation.id()
        );
    }

    /** Pure validation used before a multi-target action starts any resolver. */
    public synchronized void validateRefresh(
            Collection<SFMPath> parents,
            long resolverGeneration
    ) {
        validateRefreshLocked(requireParents(parents), resolverGeneration);
    }

    private void validateRefreshLocked(TreeSet<SFMPath> capturedParents, long resolverGeneration) {
        for (SFMPath parent : capturedParents) {
            PageState published = pageStates.get(parent);
            ActiveRequest active = activeRequests.get(parent);
            long newestGeneration = Math.max(
                    published == null ? -1 : published.resolverGeneration(),
                    active == null ? -1 : active.resolverGeneration()
            );
            if (resolverGeneration < newestGeneration) {
                throw new IllegalArgumentException(
                        "Resolver generation " + resolverGeneration
                                + " is older than generation " + newestGeneration
                                + " for " + parent.canonical()
                );
            }
        }
    }

    public synchronized Optional<RefreshTicket> beginNextPage(
            SFMPath parent,
            String expectedContinuation
    ) {
        Objects.requireNonNull(parent, "parent");
        Objects.requireNonNull(expectedContinuation, "expectedContinuation");
        PageState state = pageStates.get(parent);
        if (state == null || !state.continuation().equals(Optional.of(expectedContinuation))) {
            return Optional.empty();
        }
        retireRequestsOverlappingLocked(Set.of(parent));
        long requestId = nextRequestId++;
        ActiveRequest active = new ActiveRequest(
                requestId,
                state.resolverGeneration(),
                RequestMode.APPEND
        );
        activeRequests.put(parent, active);
        return Optional.of(new RefreshTicket(
                requestId,
                RequestMode.APPEND,
                Set.of(parent),
                state.resolverGeneration(),
                Optional.of(expectedContinuation),
                relation.id()
        ));
    }

    public synchronized PublishResult publish(
            RefreshTicket ticket,
            Collection<SFMChildPage> pages
    ) {
        Objects.requireNonNull(ticket, "ticket");
        Objects.requireNonNull(pages, "pages");
        if (!isCurrentLocked(ticket)) {
            return new PublishResult(nonCurrentDisposition(ticket), snapshotLocked());
        }
        Map<SFMPath, SFMChildPage> byParent = indexPages(ticket, pages);
        if (ticket.mode() == RequestMode.APPEND) {
            return appendLocked(ticket, byParent.values().iterator().next());
        }
        TreeSet<SFMChildEdge> nextEdges = new TreeSet<>();
        for (SFMChildEdge edge : relation.edges()) {
            if (!ticket.parents().contains(edge.parent())) nextEdges.add(edge);
        }
        for (SFMChildPage page : byParent.values()) nextEdges.addAll(page.edges());

        relation = new SFMChildRelationRevision(nextRevisionId++, nextEdges);
        for (SFMChildPage page : byParent.values()) {
            pageStates.put(page.parent(), stateFor(ticket, page));
            activeRequests.remove(page.parent());
        }
        statusGeneration++;
        return new PublishResult(PublishDisposition.PUBLISHED, snapshotLocked());
    }

    public synchronized PublishResult fail(RefreshTicket ticket, String diagnostic) {
        Objects.requireNonNull(ticket, "ticket");
        Objects.requireNonNull(diagnostic, "diagnostic");
        if (!isCurrentLocked(ticket)) {
            return new PublishResult(nonCurrentDisposition(ticket), snapshotLocked());
        }
        for (SFMPath parent : ticket.parents()) {
            PageState previous = pageStates.get(parent);
            pageStates.put(parent, new PageState(
                    previous == null ? ticket.resolverGeneration() : previous.resolverGeneration(),
                    previous == null ? Optional.empty() : previous.continuation(),
                    previous == null ? SFMChildPage.Completeness.PARTIAL : previous.completeness(),
                    PageState.Materialization.REFRESH_FAILED,
                    List.of(diagnostic),
                    ticket.requestId()
            ));
            activeRequests.remove(parent);
        }
        statusGeneration++;
        return new PublishResult(PublishDisposition.FAILED, snapshotLocked());
    }

    public synchronized boolean cancel(RefreshTicket ticket) {
        Objects.requireNonNull(ticket, "ticket");
        boolean cancelled = false;
        for (SFMPath parent : ticket.parents()) {
            ActiveRequest active = activeRequests.get(parent);
            if (active != null && active.requestId() == ticket.requestId()) {
                activeRequests.remove(parent);
                cancelled = true;
            }
        }
        if (cancelled) {
            cancelledRequestIds.add(ticket.requestId());
            statusGeneration++;
        }
        return cancelled;
    }

    private PublishResult appendLocked(RefreshTicket ticket, SFMChildPage page) {
        PageState previous = pageStates.get(page.parent());
        if (previous == null
                || previous.resolverGeneration() != ticket.resolverGeneration()
                || !previous.continuation().equals(ticket.expectedContinuation())) {
            return new PublishResult(PublishDisposition.STALE, snapshotLocked());
        }
        TreeSet<SFMChildEdge> nextEdges = new TreeSet<>(relation.edges());
        nextEdges.addAll(page.edges());
        relation = new SFMChildRelationRevision(nextRevisionId++, nextEdges);
        ArrayList<String> diagnostics = new ArrayList<>(previous.diagnostics());
        diagnostics.addAll(page.diagnostics());
        pageStates.put(page.parent(), new PageState(
                page.resolverGeneration(),
                page.continuation(),
                page.completeness(),
                PageState.Materialization.MATERIALIZED,
                diagnostics,
                ticket.requestId()
        ));
        activeRequests.remove(page.parent());
        statusGeneration++;
        return new PublishResult(PublishDisposition.PUBLISHED, snapshotLocked());
    }

    private static Map<SFMPath, SFMChildPage> indexPages(
            RefreshTicket ticket,
            Collection<SFMChildPage> pages
    ) {
        TreeMap<SFMPath, SFMChildPage> answer = new TreeMap<>();
        for (SFMChildPage page : pages) {
            Objects.requireNonNull(page, "page");
            if (!ticket.parents().contains(page.parent())) {
                throw new IllegalArgumentException("Page parent was not captured by this request");
            }
            if (page.resolverGeneration() != ticket.resolverGeneration()) {
                throw new IllegalArgumentException("Page resolver generation does not match its request");
            }
            if (answer.put(page.parent(), page) != null) {
                throw new IllegalArgumentException("Request contains two first pages for one parent");
            }
        }
        if (!answer.keySet().equals(ticket.parents())) {
            throw new IllegalArgumentException("A replacement publication requires one page per captured parent");
        }
        return answer;
    }

    private boolean isCurrentLocked(RefreshTicket ticket) {
        for (SFMPath parent : ticket.parents()) {
            ActiveRequest active = activeRequests.get(parent);
            if (active == null
                    || active.requestId() != ticket.requestId()
                    || active.resolverGeneration() != ticket.resolverGeneration()
                    || active.mode() != ticket.mode()) {
                return false;
            }
        }
        return true;
    }

    /**
     * A replacement ticket is one atomic parent set. Superseding any member
     * invalidates the whole ticket, so its reservations on non-overlapping
     * parents must not survive as generation-blocking ghosts.
     */
    private void retireRequestsOverlappingLocked(Collection<SFMPath> parents) {
        HashSet<Long> superseded = new HashSet<>();
        for (SFMPath parent : parents) {
            ActiveRequest active = activeRequests.get(parent);
            if (active != null) superseded.add(active.requestId());
        }
        if (!superseded.isEmpty()) {
            activeRequests.entrySet().removeIf(entry ->
                    superseded.contains(entry.getValue().requestId())
            );
        }
    }

    private PublishDisposition nonCurrentDisposition(RefreshTicket ticket) {
        return cancelledRequestIds.remove(ticket.requestId())
                ? PublishDisposition.CANCELLED
                : PublishDisposition.STALE;
    }

    private Snapshot snapshotLocked() {
        return new Snapshot(relation, statusGeneration, pageStates);
    }

    private static PageState stateFor(RefreshTicket ticket, SFMChildPage page) {
        return new PageState(
                page.resolverGeneration(),
                page.continuation(),
                page.completeness(),
                PageState.Materialization.MATERIALIZED,
                page.diagnostics(),
                ticket.requestId()
        );
    }

    private static TreeSet<SFMPath> requireParents(Collection<SFMPath> parents) {
        Objects.requireNonNull(parents, "parents");
        TreeSet<SFMPath> answer = new TreeSet<>();
        for (SFMPath parent : parents) answer.add(Objects.requireNonNull(parent, "parent"));
        if (answer.isEmpty()) throw new IllegalArgumentException("Refresh requires at least one parent");
        return answer;
    }
}
