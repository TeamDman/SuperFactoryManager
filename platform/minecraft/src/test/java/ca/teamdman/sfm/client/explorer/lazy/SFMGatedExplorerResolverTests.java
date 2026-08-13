package ca.teamdman.sfm.client.explorer.lazy;

import ca.teamdman.sfm.client.explorer.SFMPath;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SFMGatedExplorerResolverTests {
    private static final SFMPath ITEM_ROOT = SFMPath.parse("registry://minecraft/item/");
    private static final SFMPath STONE = SFMPath.parse("registry://minecraft/item/minecraft/stone");
    private static final SFMPath BLOCK_ROOT = SFMPath.parse("registry://minecraft/block/");
    private static final SFMPath DIRT = SFMPath.parse("registry://minecraft/block/minecraft/dirt");

    @Test
    public void unarmedResolverDelegatesDescribeAndChildrenWithoutAddingAWait() {
        ManualExecutor executor = new ManualExecutor();
        SFMInMemoryRegistryExplorerResolver delegate = resolver(executor);
        SFMGatedExplorerResolver gated = new SFMGatedExplorerResolver(delegate);

        assertEquals(delegate.scheme(), gated.scheme());
        assertEquals(delegate.generation(), gated.generation());

        CompletableFuture<SFMExplorerEntry> description = gated.describe(
                ITEM_ROOT,
                new SFMExplorerCancellationToken()
        );
        assertFalse(description.isDone());
        executor.runNext();
        assertEquals(ITEM_ROOT, description.join().path());

        CompletableFuture<SFMExplorerResolver.ChildPage> children = gated.resolveChildren(request(
                ITEM_ROOT,
                gated
        ));
        assertFalse(children.isDone());
        executor.runNext();

        assertTrue(children.isDone());
        assertEquals(List.of(STONE), children.join().entries().stream().map(SFMExplorerEntry::path).toList());
        assertEquals(0, executor.size());
    }

    @Test
    public void matchingGateLetsDelegateFinishButHoldsThePageUntilRelease() {
        ManualExecutor executor = new ManualExecutor();
        SFMGatedExplorerResolver gated = new SFMGatedExplorerResolver(resolver(executor));
        SFMGatedExplorerResolver.Gate gate = gated.armNext(ITEM_ROOT);

        CompletableFuture<SFMExplorerResolver.ChildPage> children = gated.resolveChildren(request(
                ITEM_ROOT,
                gated
        ));
        executor.runNext();

        assertTrue(gate.captured().isDone(), "delegate completion must be observable before publication");
        assertFalse(children.isDone(), "the decorated result must remain held until explicit release");
        assertEquals(0, executor.size(), "no delegate work should remain queued while publication is held");

        assertTrue(gate.release());
        assertEquals(List.of(STONE), children.join().entries().stream().map(SFMExplorerEntry::path).toList());
        assertFalse(gate.release(), "a publication gate is released at most once");

        CompletableFuture<SFMExplorerResolver.ChildPage> next = gated.resolveChildren(request(ITEM_ROOT, gated));
        executor.runNext();
        assertTrue(next.isDone(), "the matching gate is one-shot");
    }

    @Test
    public void nonmatchingRequestDoesNotConsumeAnArmedGate() {
        ManualExecutor executor = new ManualExecutor();
        SFMGatedExplorerResolver gated = new SFMGatedExplorerResolver(resolver(executor));
        SFMGatedExplorerResolver.Gate gate = gated.armNext(ITEM_ROOT);

        CompletableFuture<SFMExplorerResolver.ChildPage> blocks = gated.resolveChildren(request(
                BLOCK_ROOT,
                gated
        ));
        executor.runNext();
        assertTrue(blocks.isDone());
        assertEquals(List.of(DIRT), blocks.join().entries().stream().map(SFMExplorerEntry::path).toList());
        assertFalse(gate.captured().isDone());

        CompletableFuture<SFMExplorerResolver.ChildPage> items = gated.resolveChildren(request(
                ITEM_ROOT,
                gated
        ));
        executor.runNext();
        assertTrue(gate.captured().isDone());
        assertFalse(items.isDone());

        gate.release();
        assertEquals(List.of(STONE), items.join().entries().stream().map(SFMExplorerEntry::path).toList());
    }

    private static SFMExplorerResolver.ChildRequest request(
            SFMPath parent,
            SFMExplorerResolver resolver
    ) {
        return new SFMExplorerResolver.ChildRequest(
                parent,
                Optional.empty(),
                8,
                resolver.generation(),
                new SFMExplorerCancellationToken()
        );
    }

    private static SFMInMemoryRegistryExplorerResolver resolver(Executor executor) {
        SFMExplorerEntry itemRoot = SFMExplorerEntry.simple(
                ITEM_ROOT,
                "Items",
                true,
                Optional.of("registry")
        );
        SFMExplorerEntry stone = SFMExplorerEntry.simple(
                STONE,
                "Stone",
                false,
                Optional.of("minecraft:stone")
        );
        SFMExplorerEntry blockRoot = SFMExplorerEntry.simple(
                BLOCK_ROOT,
                "Blocks",
                true,
                Optional.of("registry")
        );
        SFMExplorerEntry dirt = SFMExplorerEntry.simple(
                DIRT,
                "Dirt",
                false,
                Optional.of("minecraft:dirt")
        );
        return new SFMInMemoryRegistryExplorerResolver(
                List.of(
                        new SFMInMemoryRegistryExplorerResolver.Node(itemRoot, List.of(STONE)),
                        new SFMInMemoryRegistryExplorerResolver.Node(stone, List.of()),
                        new SFMInMemoryRegistryExplorerResolver.Node(blockRoot, List.of(DIRT)),
                        new SFMInMemoryRegistryExplorerResolver.Node(dirt, List.of())
                ),
                executor,
                8
        );
    }

    private static final class ManualExecutor implements Executor {
        private final ArrayDeque<Runnable> work = new ArrayDeque<>();

        @Override
        public void execute(Runnable command) {
            work.addLast(Objects.requireNonNull(command, "command"));
        }

        private int size() {
            return work.size();
        }

        private void runNext() {
            work.removeFirst().run();
        }
    }
}
