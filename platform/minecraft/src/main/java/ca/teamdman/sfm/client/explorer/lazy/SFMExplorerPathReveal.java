package ca.teamdman.sfm.client.explorer.lazy;

import ca.teamdman.sfm.client.explorer.SFMChildPage;
import ca.teamdman.sfm.client.explorer.SFMChildRelationRepository;
import ca.teamdman.sfm.client.explorer.SFMPath;
import ca.teamdman.sfm.client.explorer.SFMPathHierarchy;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;

/** Lazily materializes a root-to-target chain before publishing selection. */
public final class SFMExplorerPathReveal {
    private static final int MAXIMUM_PAGES_PER_REVEAL = 4096;

    public enum FilterPolicy { CLEAR, RETAIN }

    public record Result(List<SFMPath> expanded, int loadedPages) {
        public Result {
            expanded = List.copyOf(expanded);
            if (loadedPages < 0) throw new IllegalArgumentException("Loaded page count must not be negative");
        }
    }

    private SFMExplorerPathReveal() {
    }

    public static CompletionStage<Result> reveal(
            SFMExplorerSession session,
            SFMLazyExplorerLoader loader,
            SFMPath root,
            SFMPath target,
            int pageSize,
            Runnable afterMaterialized
    ) {
        return reveal(session, loader, root, target, pageSize, afterMaterialized, FilterPolicy.CLEAR);
    }

    public static CompletionStage<Result> reveal(
            SFMExplorerSession session, SFMLazyExplorerLoader loader, SFMPath root, SFMPath target,
            int pageSize, Runnable afterMaterialized, FilterPolicy filterPolicy
    ) {
        return materialize(session, loader, root, target, pageSize, afterMaterialized, filterPolicy, true, () -> true);
    }

    /** Query previews retain both selection and filter; stale generations stop before another page. */
    public static CompletionStage<Result> preview(
            SFMExplorerSession session, SFMLazyExplorerLoader loader, SFMPath root, SFMPath target,
            int pageSize, Runnable afterMaterialized, java.util.function.BooleanSupplier current
    ) {
        return materialize(session, loader, root, target, pageSize, afterMaterialized, FilterPolicy.RETAIN, false, current);
    }

    private static CompletionStage<Result> materialize(
            SFMExplorerSession session, SFMLazyExplorerLoader loader, SFMPath root, SFMPath target,
            int pageSize, Runnable afterMaterialized, FilterPolicy filterPolicy, boolean select,
            java.util.function.BooleanSupplier current
    ) {
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(loader, "loader");
        Objects.requireNonNull(root, "root");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(afterMaterialized, "afterMaterialized");
        Objects.requireNonNull(current, "current");
        requireCurrent(current);
        if (pageSize <= 0) throw new IllegalArgumentException("Page size must be positive");

        List<SFMPath> chain = SFMPathHierarchy.chain(root, target);
        List<SFMPath> parents = chain.size() < 2
                ? List.of()
                : chain.subList(0, chain.size() - 1);
        if (Objects.requireNonNull(filterPolicy, "filterPolicy") == FilterPolicy.CLEAR) session.setFilterQuery("");
        AtomicInteger loadedPages = new AtomicInteger();
        CompletableFuture<Void> materialized = CompletableFuture.completedFuture(null);
        for (int index = 0; index + 1 < chain.size(); index++) {
            SFMPath parent = chain.get(index);
            SFMPath child = chain.get(index + 1);
            materialized = materialized.thenCompose(ignored -> {
                requireCurrent(current);
                session.expand(parent);
                return ensureChild(
                    session,
                    loader,
                    parent,
                    child,
                    pageSize,
                    false,
                    loadedPages, current
                ).toCompletableFuture();
            });
        }
        return materialized.thenApply(ignored -> {
            requireCurrent(current);
            if (select) session.navigateTo(target);
            afterMaterialized.run();
            return new Result(parents, loadedPages.get());
        });
    }

    private static CompletionStage<Void> ensureChild(
            SFMExplorerSession session,
            SFMLazyExplorerLoader loader,
            SFMPath parent,
            SFMPath child,
            int pageSize,
            boolean refreshed,
            AtomicInteger loadedPages,
            java.util.function.BooleanSupplier current
    ) {
        requireCurrent(current);
        SFMChildRelationRepository.Snapshot relation = loader.relationSnapshot();
        if (relation.relation().childrenOf(parent).contains(child)) {
            return CompletableFuture.completedFuture(null);
        }
        if (loadedPages.get() >= MAXIMUM_PAGES_PER_REVEAL) {
            return CompletableFuture.failedFuture(new IllegalStateException(
                    "Explorer reveal exceeded " + MAXIMUM_PAGES_PER_REVEAL + " pages beneath "
                            + parent.canonical()
            ));
        }

        // A render/prefetch pass may already own this parent's replacement or
        // append request. Joining it is the only race-safe response: starting a
        // competing refresh would retire useful work, while reporting that the
        // continuation is unavailable turns ordinary deduplication into a
        // spurious reveal failure.
        var active = session.activeRequestHandle(parent);
        if (active.isPresent()) {
            return awaitPublished(active.orElseThrow(), loadedPages).thenCompose(ignored -> ensureChild(
                    session, loader, parent, child, pageSize, refreshed, loadedPages, current
            ));
        }

        SFMChildRelationRepository.PageState state = relation.pageStates().get(parent);
        if (state != null
                && state.materialization() == SFMChildRelationRepository.PageState.Materialization.MATERIALIZED
                && state.completeness() == SFMChildPage.Completeness.PARTIAL
                && state.continuation().isPresent()) {
            return session.requestNextPage(parent, loader, pageSize)
                    .map(handle -> awaitPublished(handle, loadedPages).thenCompose(ignored -> ensureChild(
                            session, loader, parent, child, pageSize, refreshed, loadedPages, current
                    )))
                    .orElseGet(() -> session.activeRequestHandle(parent)
                            .<CompletionStage<Void>>map(handle -> awaitPublished(handle, loadedPages)
                                    .thenCompose(ignored -> ensureChild(
                                            session, loader, parent, child, pageSize, refreshed, loadedPages, current
                                    )))
                            .orElseGet(() -> CompletableFuture.failedFuture(new IllegalStateException(
                                    "Explorer continuation disappeared before reveal could join it for "
                                            + parent.canonical()
                            ))));
        }

        if (!refreshed) {
            SFMLazyExplorerLoader.LoadHandle handle = session.requestChildren(parent, loader, pageSize);
            return awaitPublished(handle, loadedPages).thenCompose(ignored -> ensureChild(
                    session, loader, parent, child, pageSize, true, loadedPages, current
            ));
        }
        return CompletableFuture.failedFuture(new IllegalStateException(
                "Resolver did not publish target child " + child.canonical() + " beneath " + parent.canonical()
        ));
    }

    private static CompletionStage<Void> awaitPublished(
            SFMLazyExplorerLoader.LoadHandle handle,
            AtomicInteger loadedPages
    ) {
        loadedPages.incrementAndGet();
        return handle.completion().thenCompose(result -> result.disposition() == SFMLazyExplorerLoader.LoadDisposition.PUBLISHED
                ? CompletableFuture.completedFuture(null)
                : CompletableFuture.failedFuture(new IllegalStateException(
                        "Explorer reveal load was " + result.disposition().name().toLowerCase(java.util.Locale.ROOT)
                                + result.diagnostic().map(value -> ": " + value).orElse("")
                )));
    }

    private static void requireCurrent(java.util.function.BooleanSupplier current) {
        if (!current.getAsBoolean()) throw new java.util.concurrent.CancellationException("Explorer reveal generation changed");
    }
}
