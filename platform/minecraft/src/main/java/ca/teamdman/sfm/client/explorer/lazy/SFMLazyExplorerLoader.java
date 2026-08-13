package ca.teamdman.sfm.client.explorer.lazy;

import ca.teamdman.sfm.client.explorer.SFMChildEdge;
import ca.teamdman.sfm.client.explorer.SFMChildPage;
import ca.teamdman.sfm.client.explorer.SFMChildRelationRepository;
import ca.teamdman.sfm.client.explorer.SFMPath;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;

/**
 * Coordinates asynchronous resolvers with the atomic child-relation store.
 * Resolver results become visible only after their captured request publishes.
 */
public final class SFMLazyExplorerLoader {
    public enum LoadDisposition {
        PUBLISHED,
        STALE,
        FAILED,
        CANCELLED
    }

    public record RequestEvidence(
            long relationRequestId,
            SFMChildRelationRepository.RequestMode mode,
            SFMPath parent,
            String resolverScheme,
            long resolverGeneration,
            Optional<String> continuation,
            int pageSize
    ) {
        public RequestEvidence {
            if (relationRequestId <= 0) throw new IllegalArgumentException("Request id must be positive");
            Objects.requireNonNull(mode, "mode");
            Objects.requireNonNull(parent, "parent");
            Objects.requireNonNull(resolverScheme, "resolverScheme");
            if (resolverGeneration < 0) {
                throw new IllegalArgumentException("Resolver generation must not be negative");
            }
            Objects.requireNonNull(continuation, "continuation");
            if (pageSize <= 0) throw new IllegalArgumentException("Page size must be positive");
        }
    }

    public record LoadResult(
            RequestEvidence evidence,
            LoadDisposition disposition,
            SFMChildRelationRepository.Snapshot relationSnapshot,
            Optional<SFMExplorerResolver.ChildPage> resolverPage,
            Optional<String> diagnostic
    ) {
        public LoadResult {
            Objects.requireNonNull(evidence, "evidence");
            Objects.requireNonNull(disposition, "disposition");
            Objects.requireNonNull(relationSnapshot, "relationSnapshot");
            Objects.requireNonNull(resolverPage, "resolverPage");
            Objects.requireNonNull(diagnostic, "diagnostic");
        }
    }

    public final class LoadHandle {
        private final SFMChildRelationRepository.RefreshTicket ticket;
        private final RequestEvidence evidence;
        private final SFMExplorerCancellationToken cancellation;
        private final CompletableFuture<LoadResult> completion;

        private LoadHandle(
                SFMChildRelationRepository.RefreshTicket ticket,
                RequestEvidence evidence,
                SFMExplorerCancellationToken cancellation,
                CompletableFuture<LoadResult> completion
        ) {
            this.ticket = ticket;
            this.evidence = evidence;
            this.cancellation = cancellation;
            this.completion = completion;
        }

        public RequestEvidence evidence() {
            return evidence;
        }

        public CompletableFuture<LoadResult> completion() {
            return completion;
        }

        public synchronized boolean cancel() {
            if (completion.isDone()) return false;
            boolean tokenChanged = cancellation.cancel();
            boolean relationChanged = relations.cancel(ticket);
            completion.complete(new LoadResult(
                    evidence,
                    LoadDisposition.CANCELLED,
                    relations.snapshot(),
                    Optional.empty(),
                    Optional.of("request cancelled")
            ));
            return tokenChanged || relationChanged;
        }
    }

    private final SFMExplorerResolverRegistry resolvers;
    private final SFMChildRelationRepository relations;
    private final Executor publicationExecutor;
    private final Map<SFMPath, SFMExplorerEntry> entries = new TreeMap<>();

    public SFMLazyExplorerLoader(
            SFMExplorerResolverRegistry resolvers,
            SFMChildRelationRepository relations
    ) {
        this(resolvers, relations, Runnable::run);
    }

    /**
     * @param publicationExecutor owns relation/cache publication; production
     *                            supplies Minecraft's client executor.
     */
    public SFMLazyExplorerLoader(
            SFMExplorerResolverRegistry resolvers,
            SFMChildRelationRepository relations,
            Executor publicationExecutor
    ) {
        this.resolvers = Objects.requireNonNull(resolvers, "resolvers");
        this.relations = Objects.requireNonNull(relations, "relations");
        this.publicationExecutor = Objects.requireNonNull(publicationExecutor, "publicationExecutor");
    }

    /** Describes one root only; this method never asks for its children. */
    public CompletableFuture<SFMExplorerEntry> openRoot(SFMPath root) {
        Objects.requireNonNull(root, "root");
        SFMExplorerResolver resolver = resolvers.require(root);
        SFMExplorerCancellationToken cancellation = new SFMExplorerCancellationToken();
        return resolver.describe(root, cancellation).thenApplyAsync(entry -> {
            if (!entry.path().equals(root)) {
                throw new IllegalStateException("Resolver described a different root path");
            }
            synchronized (entries) {
                entries.put(root, entry);
            }
            return entry;
        }, publicationExecutor);
    }

    public LoadHandle refresh(SFMPath parent, int pageSize) {
        Objects.requireNonNull(parent, "parent");
        SFMExplorerResolver resolver = resolvers.require(parent);
        long resolverGeneration = resolver.generation();
        SFMChildRelationRepository.RefreshTicket ticket = relations.beginRefresh(
                java.util.Set.of(parent),
                resolverGeneration
        );
        return start(ticket, resolver, Optional.empty(), pageSize);
    }

    public Optional<LoadHandle> nextPage(SFMPath parent, int pageSize) {
        Objects.requireNonNull(parent, "parent");
        SFMChildRelationRepository.PageState state = relations.snapshot().pageStates().get(parent);
        if (state == null || state.continuation().isEmpty()) return Optional.empty();
        String continuation = state.continuation().orElseThrow();
        Optional<SFMChildRelationRepository.RefreshTicket> ticket = relations.beginNextPage(
                parent,
                continuation
        );
        if (ticket.isEmpty()) return Optional.empty();
        SFMExplorerResolver resolver = resolvers.require(parent);
        return Optional.of(start(ticket.orElseThrow(), resolver, Optional.of(continuation), pageSize));
    }

    public Optional<SFMExplorerEntry> entry(SFMPath path) {
        synchronized (entries) {
            return Optional.ofNullable(entries.get(Objects.requireNonNull(path, "path")));
        }
    }

    public Map<SFMPath, SFMExplorerEntry> entrySnapshot() {
        synchronized (entries) {
            return Collections.unmodifiableMap(new TreeMap<>(entries));
        }
    }

    public SFMChildRelationRepository.Snapshot relationSnapshot() {
        return relations.snapshot();
    }

    /** Repository authority used for one atomic path-expression resolution. */
    public SFMChildRelationRepository relationRepository() {
        return relations;
    }

    /** Pure request validation; no relation ticket or resolver work is started. */
    public void preflightRefresh(SFMPath parent, int pageSize) {
        Objects.requireNonNull(parent, "parent");
        if (pageSize <= 0) throw new IllegalArgumentException("Page size must be positive");
        SFMExplorerResolver resolver = resolvers.require(parent);
        relations.validateRefresh(java.util.Set.of(parent), resolver.generation());
    }

    private LoadHandle start(
            SFMChildRelationRepository.RefreshTicket ticket,
            SFMExplorerResolver resolver,
            Optional<String> continuation,
            int pageSize
    ) {
        if (pageSize <= 0) throw new IllegalArgumentException("Page size must be positive");
        SFMPath parent = ticket.parents().iterator().next();
        RequestEvidence evidence = new RequestEvidence(
                ticket.requestId(),
                ticket.mode(),
                parent,
                resolver.scheme(),
                ticket.resolverGeneration(),
                continuation,
                pageSize
        );
        SFMExplorerCancellationToken cancellation = new SFMExplorerCancellationToken();
        CompletableFuture<LoadResult> completion = new CompletableFuture<>();
        LoadHandle handle = new LoadHandle(ticket, evidence, cancellation, completion);
        SFMExplorerResolver.ChildRequest request = new SFMExplorerResolver.ChildRequest(
                parent,
                continuation,
                pageSize,
                ticket.resolverGeneration(),
                cancellation
        );
        CompletableFuture<SFMExplorerResolver.ChildPage> resolverCompletion;
        try {
            resolverCompletion = Objects.requireNonNull(
                    resolver.resolveChildren(request),
                    "resolver child completion"
            );
        } catch (RuntimeException failure) {
            resolverCompletion = new CompletableFuture<>();
            resolverCompletion.completeExceptionally(failure);
        }
        resolverCompletion.whenComplete((page, failure) -> schedulePublication(handle, () -> {
            synchronized (handle) {
                if (completion.isDone()) return;
                Throwable cause = unwrap(failure);
                if (cause != null) {
                    completeFailure(handle, cause);
                    return;
                }
                try {
                    handle.cancellation.throwIfCancelled();
                    validatePage(request, page);
                    ArrayList<SFMChildEdge> edges = new ArrayList<>();
                    page.entries().forEach(entry -> edges.add(new SFMChildEdge(parent, entry.path())));
                    SFMChildPage relationPage = new SFMChildPage(
                            parent,
                            edges,
                            page.continuation(),
                            page.complete() ? SFMChildPage.Completeness.COMPLETE : SFMChildPage.Completeness.PARTIAL,
                            page.resolverGeneration(),
                            page.diagnostics()
                    );
                    SFMChildRelationRepository.PublishResult published = relations.publish(
                            ticket,
                            java.util.List.of(relationPage)
                    );
                    LoadDisposition disposition = switch (published.disposition()) {
                        case PUBLISHED -> LoadDisposition.PUBLISHED;
                        case STALE -> LoadDisposition.STALE;
                        case FAILED -> LoadDisposition.FAILED;
                        case CANCELLED -> LoadDisposition.CANCELLED;
                    };
                    if (disposition == LoadDisposition.PUBLISHED) {
                        synchronized (entries) {
                            page.entries().forEach(entry -> entries.put(entry.path(), entry));
                        }
                    }
                    completion.complete(new LoadResult(
                            evidence,
                            disposition,
                            published.snapshot(),
                            Optional.of(page),
                            Optional.empty()
                    ));
                } catch (Throwable validationFailure) {
                    completeFailure(handle, validationFailure);
                }
            }
        }));
        return handle;
    }

    private void schedulePublication(LoadHandle handle, Runnable publication) {
        try {
            publicationExecutor.execute(publication);
        } catch (RuntimeException failure) {
            completeFailure(handle, new IllegalStateException(
                    "publication executor rejected work: " + failure.getMessage(),
                    failure
            ));
        }
    }

    private void completeFailure(LoadHandle handle, Throwable failure) {
        synchronized (handle) {
            if (handle.completion.isDone()) return;
            Throwable cause = unwrap(failure);
            if (cause instanceof CancellationException || handle.cancellation.isCancelled()) {
                relations.cancel(handle.ticket);
                handle.completion.complete(new LoadResult(
                        handle.evidence,
                        LoadDisposition.CANCELLED,
                        relations.snapshot(),
                        Optional.empty(),
                        Optional.of("request cancelled")
                ));
                return;
            }
            if (cause instanceof SFMExplorerResolver.StaleGenerationException) {
                relations.cancel(handle.ticket);
                handle.completion.complete(new LoadResult(
                        handle.evidence,
                        LoadDisposition.STALE,
                        relations.snapshot(),
                        Optional.empty(),
                        Optional.of(cause.getMessage())
                ));
                return;
            }
            String diagnostic = cause == null
                    ? "resolver failed without an exception"
                    : cause.getClass().getSimpleName() + ": " + String.valueOf(cause.getMessage());
            SFMChildRelationRepository.PublishResult failed = relations.fail(handle.ticket, diagnostic);
            LoadDisposition disposition = failed.disposition() == SFMChildRelationRepository.PublishDisposition.STALE
                    ? LoadDisposition.STALE
                    : LoadDisposition.FAILED;
            handle.completion.complete(new LoadResult(
                    handle.evidence,
                    disposition,
                    failed.snapshot(),
                    Optional.empty(),
                    Optional.of(diagnostic)
            ));
        }
    }

    private static void validatePage(
            SFMExplorerResolver.ChildRequest request,
            SFMExplorerResolver.ChildPage page
    ) {
        Objects.requireNonNull(page, "page");
        if (!page.parent().equals(request.parent())) {
            throw new IllegalStateException("Resolver page parent does not match its request");
        }
        if (page.resolverGeneration() != request.expectedResolverGeneration()) {
            throw new SFMExplorerResolver.StaleGenerationException(
                    request.expectedResolverGeneration(),
                    page.resolverGeneration()
            );
        }
        if (page.entries().size() > request.pageSize()) {
            throw new IllegalStateException("Resolver returned more entries than the requested page bound");
        }
        for (SFMExplorerEntry entry : page.entries()) {
            if (!entry.path().scheme().equals(request.parent().scheme())) {
                throw new IllegalStateException("A resolver returned a child owned by another scheme");
            }
        }
    }

    private static Throwable unwrap(Throwable failure) {
        if (failure == null) return null;
        Throwable answer = failure;
        while ((answer instanceof CompletionException || answer instanceof java.util.concurrent.ExecutionException)
                && answer.getCause() != null) {
            answer = answer.getCause();
        }
        return answer;
    }
}
