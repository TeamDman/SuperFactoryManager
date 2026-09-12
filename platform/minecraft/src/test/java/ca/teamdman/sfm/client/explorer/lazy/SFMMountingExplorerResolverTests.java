package ca.teamdman.sfm.client.explorer.lazy;

import ca.teamdman.sfm.client.explorer.SFMChildRelationRepository;
import ca.teamdman.sfm.client.explorer.SFMPath;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SFMMountingExplorerResolverTests {
    private static final SFMPath ROOT = SFMPath.parse("file:///C:/fixture/");
    private static final SFMPath REVIEW = SFMPath.parse("file:///C:/fixture/review.sfm-review.json");
    private static final SFMPath ORDINARY = SFMPath.parse("file:///C:/fixture/notes.json");
    private static final SFMPath CHILD = SFMPath.parse("review-tree://fixture/changes/");

    @Test
    void mountedLeafKeepsFileSemanticsAndGainsIndependentExpansionCapability() {
        var resolver = new SFMMountingExplorerResolver(new FakeResolver(), List.of(new ReviewMount()));

        SFMExplorerEntry mounted = resolver.describe(REVIEW, new SFMExplorerCancellationToken()).join();
        SFMExplorerEntry ordinary = resolver.describe(ORDINARY, new SFMExplorerCancellationToken()).join();

        assertTrue(mounted.expandable());
        assertTrue(mounted.opensOnActivate(), "primary activation must still open raw JSON text");
        assertEquals(Optional.of("file"), mounted.sortKey(SFMExplorerEntry.SUBJECT_KIND).value());
        assertEquals(Optional.of("sfm:test-review"), mounted.sortKey(SFMExplorerEntry.MOUNT_PROVIDER).value());
        assertFalse(ordinary.expandable());
    }

    @Test
    void mountedChildrenMayBelongToAnotherRegisteredResolverScheme() {
        var resolver = new SFMMountingExplorerResolver(new FakeResolver(), List.of(new ReviewMount()));

        SFMExplorerResolver.ChildPage page = resolver.resolveChildren(new SFMExplorerResolver.ChildRequest(
                REVIEW,
                Optional.empty(),
                16,
                resolver.generation(),
                new SFMExplorerCancellationToken()
        )).join();

        assertEquals(REVIEW, page.parent());
        assertEquals(List.of(CHILD), page.entries().stream().map(SFMExplorerEntry::path).toList());
        assertEquals(List.of("mounted read-only"), page.diagnostics());
    }

    @Test
    void ordinaryDirectoryPageAdvertisesDiscoveredMountedFileChild() {
        var resolver = new SFMMountingExplorerResolver(new FakeResolver(), List.of(new ReviewMount()));

        SFMExplorerResolver.ChildPage page = resolver.resolveChildren(new SFMExplorerResolver.ChildRequest(
                ROOT,
                Optional.empty(),
                16,
                resolver.generation(),
                new SFMExplorerCancellationToken()
        )).join();

        SFMExplorerEntry review = page.entries().stream()
                .filter(entry -> entry.path().equals(REVIEW))
                .findFirst().orElseThrow();
        SFMExplorerEntry ordinary = page.entries().stream()
                .filter(entry -> entry.path().equals(ORDINARY))
                .findFirst().orElseThrow();
        assertTrue(review.expandable());
        assertTrue(review.opensOnActivate());
        assertEquals(Optional.of("sfm:test-review"), review.sortKey(SFMExplorerEntry.MOUNT_PROVIDER).value());
        assertFalse(ordinary.expandable());
    }

    @Test
    void explicitMountCapabilityPublishesCrossSchemeChildrenThroughTheLazyLoader() {
        var resolver = new SFMMountingExplorerResolver(new FakeResolver(), List.of(new ReviewMount()));
        var registry = new SFMExplorerResolverRegistry();
        registry.register(new SFMGatedExplorerResolver(resolver));
        var relations = new SFMChildRelationRepository();
        var loader = new SFMLazyExplorerLoader(registry, relations, Runnable::run);

        SFMLazyExplorerLoader.LoadResult result = loader.refresh(REVIEW, 16).completion().join();

        assertEquals(SFMLazyExplorerLoader.LoadDisposition.PUBLISHED, result.disposition());
        assertEquals(Set.of(CHILD), relations.snapshot().relation().childrenOf(REVIEW));
        assertTrue(resolver.permitsCrossSchemeChildren(REVIEW));
        assertFalse(resolver.permitsCrossSchemeChildren(ROOT));
    }

    @Test
    void ordinaryResolverCannotPublishCrossSchemeChildrenWithoutAnExplicitMountCapability() {
        var registry = new SFMExplorerResolverRegistry();
        registry.register(new SFMGatedExplorerResolver(new RogueResolver()));
        var relations = new SFMChildRelationRepository();
        var loader = new SFMLazyExplorerLoader(registry, relations, Runnable::run);

        SFMLazyExplorerLoader.LoadResult result = loader.refresh(ROOT, 16).completion().join();

        assertEquals(SFMLazyExplorerLoader.LoadDisposition.FAILED, result.disposition());
        assertTrue(result.diagnostic().orElseThrow().contains("another scheme"));
        assertTrue(relations.snapshot().relation().childrenOf(ROOT).isEmpty());
    }

    private static final class ReviewMount implements SFMExplorerMountProvider {
        @Override public String id() { return "sfm:test-review"; }
        @Override public boolean supports(SFMPath path) { return path.equals(REVIEW); }
        @Override public CompletableFuture<Page> resolveChildren(
                SFMExplorerResolver.ChildRequest request,
                SFMExplorerEntry backingEntry
        ) {
            return CompletableFuture.completedFuture(new Page(
                    List.of(SFMExplorerEntry.simple(CHILD, "Changes", true, Optional.empty())),
                    Optional.empty(),
                    List.of("mounted read-only"),
                    1
            ));
        }
    }

    private static final class FakeResolver implements SFMExplorerResolver {
        @Override public String scheme() { return "file"; }
        @Override public long generation() { return 7; }
        @Override public CompletableFuture<SFMExplorerEntry> describe(
                SFMPath path,
                SFMExplorerCancellationToken cancellation
        ) {
            boolean directory = path.equals(ROOT);
            return CompletableFuture.completedFuture(new SFMExplorerEntry(
                    path,
                    directory ? "fixture" : path.equals(REVIEW) ? "review.sfm-review.json" : "notes.json",
                    directory,
                    Map.of(
                            SFMExplorerEntry.SORT_NAME, SFMExplorerEntry.SortKey.available("name"),
                            SFMExplorerEntry.SUBJECT_KIND, SFMExplorerEntry.SortKey.available(
                                    directory ? "directory" : "file")
                    ),
                    List.of(path.canonical()),
                    List.of()
            ));
        }
        @Override public CompletableFuture<ChildPage> resolveChildren(ChildRequest request) {
            if (!request.parent().equals(ROOT)) {
                return CompletableFuture.failedFuture(new IllegalArgumentException("ordinary file has no children"));
            }
            return CompletableFuture.completedFuture(new ChildPage(
                    ROOT,
                    List.of(
                            describe(REVIEW, request.cancellation()).join(),
                            describe(ORDINARY, request.cancellation()).join()
                    ),
                    Optional.empty(),
                    generation(),
                    List.of(),
                    2
            ));
        }
    }

    private static final class RogueResolver implements SFMExplorerResolver {
        @Override public String scheme() { return "file"; }
        @Override public long generation() { return 11; }
        @Override public CompletableFuture<SFMExplorerEntry> describe(
                SFMPath path,
                SFMExplorerCancellationToken cancellation
        ) {
            return CompletableFuture.completedFuture(SFMExplorerEntry.simple(path, "fixture", true, Optional.empty()));
        }
        @Override public CompletableFuture<ChildPage> resolveChildren(ChildRequest request) {
            return CompletableFuture.completedFuture(new ChildPage(
                    request.parent(),
                    List.of(SFMExplorerEntry.simple(CHILD, "foreign child", false, Optional.empty())),
                    Optional.empty(),
                    generation(),
                    List.of(),
                    1
            ));
        }
    }
}
