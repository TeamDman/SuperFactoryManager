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
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(loader, "loader");
        Objects.requireNonNull(root, "root");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(afterMaterialized, "afterMaterialized");
        if (pageSize <= 0) throw new IllegalArgumentException("Page size must be positive");

        List<SFMPath> chain = SFMPathHierarchy.chain(root, target);
        List<SFMPath> parents = chain.size() < 2
                ? List.of()
                : chain.subList(0, chain.size() - 1);
        session.setFilterQuery("");
        AtomicInteger loadedPages = new AtomicInteger();
        CompletableFuture<Void> materialized = CompletableFuture.completedFuture(null);
        for (int index = 0; index + 1 < chain.size(); index++) {
            SFMPath parent = chain.get(index);
            SFMPath child = chain.get(index + 1);
            session.expand(parent);
            materialized = materialized.thenCompose(ignored -> ensureChild(
                    session,
                    loader,
                    parent,
                    child,
                    pageSize,
                    false,
                    loadedPages
            ).toCompletableFuture());
        }
        return materialized.thenApply(ignored -> {
            session.navigateTo(target);
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
            AtomicInteger loadedPages
    ) {
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

        SFMChildRelationRepository.PageState state = relation.pageStates().get(parent);
        if (state != null
                && state.materialization() == SFMChildRelationRepository.PageState.Materialization.MATERIALIZED
                && state.completeness() == SFMChildPage.Completeness.PARTIAL
                && state.continuation().isPresent()) {
            return session.requestNextPage(parent, loader, pageSize)
                    .map(handle -> awaitPublished(handle, loadedPages).thenCompose(ignored -> ensureChild(
                            session, loader, parent, child, pageSize, refreshed, loadedPages
                    )))
                    .orElseGet(() -> CompletableFuture.failedFuture(new IllegalStateException(
                            "Explorer continuation could not be acquired for " + parent.canonical()
                    )));
        }

        if (!refreshed) {
            SFMLazyExplorerLoader.LoadHandle handle = session.requestChildren(parent, loader, pageSize);
            return awaitPublished(handle, loadedPages).thenCompose(ignored -> ensureChild(
                    session, loader, parent, child, pageSize, true, loadedPages
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
}
