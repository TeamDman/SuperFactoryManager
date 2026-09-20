package ca.teamdman.sfm.client.explorer.lazy;

import ca.teamdman.sfm.client.explorer.SFMPath;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SFMItemRegistryExplorerResolverTests {
    @Test
    public void rootDescriptionIsLazyAndContributesPanelSortKeys() {
        AtomicInteger captures = new AtomicInteger();
        SFMItemRegistryExplorerResolver resolver = resolver(captures, Runnable::run, 2);

        SFMExplorerEntry root = resolver.describe(
                SFMItemRegistryExplorerResolver.ROOT,
                new SFMExplorerCancellationToken()
        ).join();

        assertEquals(0, captures.get(), "describing the synthetic root must not enumerate the registry");
        assertEquals("Items", root.label());
        assertTrue(root.expandable());
        assertEquals("Items", root.sortKey(SFMExplorerEntry.SORT_NAME).value().orElseThrow());
        assertEquals("minecraft:chest", root.sortKey(SFMExplorerEntry.SORT_ICON).value().orElseThrow());
        assertFalse(root.sortKey(SFMExplorerEntry.SORT_EXTENSION).available());
    }

    @Test
    public void childrenAreCanonicalDeterministicCachedAndBoundedByContinuation() {
        AtomicInteger captures = new AtomicInteger();
        SFMItemRegistryExplorerResolver resolver = resolver(captures, Runnable::run, 2);
        long generation = resolver.generation();

        SFMExplorerResolver.ChildPage first = resolver.resolveChildren(request(
                resolver,
                Optional.empty(),
                100,
                generation,
                new SFMExplorerCancellationToken()
        )).join();

        assertEquals(1, captures.get());
        assertEquals(List.of(
                "registry://minecraft/item/alpha/nested/tool",
                "registry://minecraft/item/minecraft/apple"
        ), first.entries().stream().map(entry -> entry.path().canonical()).toList());
        assertEquals(2, first.entries().size());
        assertEquals(3, first.observedEntries(), "one bounded look-ahead proves continuation");
        assertTrue(first.continuation().isPresent());
        assertEquals("Alpha Tool", first.entries().get(0).sortKey(SFMExplorerEntry.SORT_NAME)
                .value().orElseThrow());
        assertEquals("alpha:nested/tool", first.entries().get(0).sortKey(SFMExplorerEntry.SORT_ICON)
                .value().orElseThrow());
        assertFalse(first.entries().get(0).sortKey(SFMExplorerEntry.SORT_EXTENSION).available());

        SFMExplorerResolver.ChildPage second = resolver.resolveChildren(request(
                resolver,
                first.continuation(),
                100,
                generation,
                new SFMExplorerCancellationToken()
        )).join();

        assertEquals(1, captures.get(), "pages in one generation share one immutable source snapshot");
        assertEquals(List.of("registry://minecraft/item/other/zeta"), second.entries().stream()
                .map(entry -> entry.path().canonical())
                .toList());
        assertTrue(second.complete());
        assertEquals(1, second.observedEntries());
    }

    @Test
    public void describeAndLeafResolutionUseExactCanonicalItemPaths() {
        AtomicInteger captures = new AtomicInteger();
        SFMItemRegistryExplorerResolver resolver = resolver(captures, Runnable::run, 8);
        SFMPath item = SFMPath.parse("registry://minecraft/item/alpha/nested/tool");

        SFMExplorerEntry described = resolver.describe(item, new SFMExplorerCancellationToken()).join();
        assertEquals("Alpha Tool", described.label());
        assertFalse(described.expandable());
        assertEquals(1, captures.get());

        SFMExplorerResolver.ChildPage leaf = resolver.resolveChildren(new SFMExplorerResolver.ChildRequest(
                item,
                Optional.empty(),
                8,
                resolver.generation(),
                new SFMExplorerCancellationToken()
        )).join();
        assertTrue(leaf.entries().isEmpty());
        assertTrue(leaf.complete());

        CompletionException unknown = assertThrows(
                CompletionException.class,
                () -> resolver.describe(
                        SFMPath.parse("registry://minecraft/item/minecraft/missing"),
                        new SFMExplorerCancellationToken()
                ).join()
        );
        assertInstanceOf(IllegalArgumentException.class, unknown.getCause());
        assertThrows(
                IllegalArgumentException.class,
                () -> resolver.describe(
                        SFMPath.parse("registry://minecraft/block/minecraft/stone"),
                        new SFMExplorerCancellationToken()
                )
        );
    }

    @Test
    public void cancellationAndGenerationChangesRejectQueuedWork() {
        AtomicInteger captures = new AtomicInteger();
        ManualExecutor executor = new ManualExecutor();
        SFMItemRegistryExplorerResolver resolver = resolver(captures, executor, 2);

        SFMExplorerCancellationToken cancelledToken = new SFMExplorerCancellationToken();
        var cancelled = resolver.resolveChildren(request(
                resolver,
                Optional.empty(),
                2,
                resolver.generation(),
                cancelledToken
        ));
        cancelledToken.cancel();
        executor.runNext();
        CompletionException cancelledFailure = assertThrows(CompletionException.class, cancelled::join);
        assertInstanceOf(CancellationException.class, cancelledFailure.getCause());
        assertEquals(0, captures.get());

        long oldGeneration = resolver.generation();
        var stale = resolver.resolveChildren(request(
                resolver,
                Optional.empty(),
                2,
                oldGeneration,
                new SFMExplorerCancellationToken()
        ));
        resolver.invalidate();
        executor.runNext();
        CompletionException staleFailure = assertThrows(CompletionException.class, stale::join);
        assertInstanceOf(SFMExplorerResolver.StaleGenerationException.class, staleFailure.getCause());
        assertEquals(0, captures.get());
    }

    @Test
    public void continuationTokensCannotCrossGenerations() {
        AtomicInteger captures = new AtomicInteger();
        SFMItemRegistryExplorerResolver resolver = resolver(captures, Runnable::run, 1);
        long firstGeneration = resolver.generation();
        SFMExplorerResolver.ChildPage first = resolver.resolveChildren(request(
                resolver,
                Optional.empty(),
                1,
                firstGeneration,
                new SFMExplorerCancellationToken()
        )).join();
        resolver.invalidate();

        CompletionException stale = assertThrows(
                CompletionException.class,
                () -> resolver.resolveChildren(request(
                        resolver,
                        first.continuation(),
                        1,
                        resolver.generation(),
                        new SFMExplorerCancellationToken()
                )).join()
        );
        assertInstanceOf(SFMExplorerResolver.StaleGenerationException.class, stale.getCause());
    }

    private static SFMItemRegistryExplorerResolver resolver(
            AtomicInteger captures,
            Executor executor,
            int maximumPageSize
    ) {
        return new SFMItemRegistryExplorerResolver(
                () -> {
                    captures.incrementAndGet();
                    // Deliberately not sorted: the resolver owns deterministic canonical order.
                    return new SFMItemRegistryExplorerResolver.ItemSnapshot(List.of(
                            new SFMItemRegistryExplorerResolver.ItemKeyLabel(
                                    "other", "zeta", "Zeta"
                            ),
                            new SFMItemRegistryExplorerResolver.ItemKeyLabel(
                                    "minecraft", "apple", "Apple"
                            ),
                            new SFMItemRegistryExplorerResolver.ItemKeyLabel(
                                    "alpha", "nested/tool", "Alpha Tool"
                            )
                    ));
                },
                executor,
                maximumPageSize
        );
    }

    private static SFMExplorerResolver.ChildRequest request(
            SFMItemRegistryExplorerResolver resolver,
            Optional<String> continuation,
            int pageSize,
            long expectedGeneration,
            SFMExplorerCancellationToken cancellation
    ) {
        return new SFMExplorerResolver.ChildRequest(
                SFMItemRegistryExplorerResolver.ROOT,
                continuation,
                pageSize,
                expectedGeneration,
                cancellation
        );
    }

    private static final class ManualExecutor implements Executor {
        private final ArrayDeque<Runnable> work = new ArrayDeque<>();

        @Override
        public void execute(Runnable command) {
            work.add(Objects.requireNonNull(command, "command"));
        }

        private void runNext() {
            work.removeFirst().run();
        }
    }
}
