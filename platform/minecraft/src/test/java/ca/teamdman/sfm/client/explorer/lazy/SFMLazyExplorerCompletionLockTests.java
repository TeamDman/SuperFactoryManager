package ca.teamdman.sfm.client.explorer.lazy;

import ca.teamdman.sfm.client.explorer.SFMChildRelationRepository;
import ca.teamdman.sfm.client.explorer.SFMExplorerId;
import ca.teamdman.sfm.client.explorer.SFMPath;
import ca.teamdman.sfm.client.explorer.SFMSelectionRepository;
import org.junit.jupiter.api.Test;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class SFMLazyExplorerCompletionLockTests {
    private static final SFMPath ROOT = SFMPath.parse("registry://test/completion");
    private static final SFMPath CHILD = SFMPath.parse("registry://test/completion/child");

    @Test
    public void publicationCompletesOutsideHandleAndWinsOverLaterCancellation() throws Exception {
        ManualExecutor publication = new ManualExecutor();
        Fixture fixture = fixture(publication);
        SFMLazyExplorerLoader.LoadHandle handle = fixture.loader().refresh(ROOT, 8);
        CompletionProbe probe = new CompletionProbe(handle);

        fixture.resolver().pending.complete(page(ROOT));
        assertFalse(handle.completion().isDone());
        publication.runNext();

        assertTerminal(fixture, handle, probe, SFMLazyExplorerLoader.LoadDisposition.PUBLISHED);
        assertEquals(Set.of(CHILD), fixture.relations().snapshot().relation().childrenOf(ROOT));
    }

    @Test
    public void cancellationCompletesOutsideHandleAndWinsOverQueuedPublication() throws Exception {
        ManualExecutor publication = new ManualExecutor();
        Fixture fixture = fixture(publication);
        SFMLazyExplorerLoader.LoadHandle handle = fixture.loader().refresh(ROOT, 8);
        CompletionProbe probe = new CompletionProbe(handle);
        fixture.resolver().pending.complete(page(ROOT));

        assertTrue(handle.cancel());
        publication.runNext();

        assertTerminal(fixture, handle, probe, SFMLazyExplorerLoader.LoadDisposition.CANCELLED);
        assertTrue(fixture.resolver().request.cancellation().isCancelled());
        assertTrue(fixture.relations().snapshot().relation().edges().isEmpty());
        assertTrue(fixture.loader().entry(CHILD).isEmpty());
    }

    @Test
    public void resolverFailureCompletesOutsideHandle() throws Exception {
        Fixture fixture = fixture(Runnable::run);
        SFMLazyExplorerLoader.LoadHandle handle = fixture.loader().refresh(ROOT, 8);
        CompletionProbe probe = new CompletionProbe(handle);

        fixture.resolver().pending.completeExceptionally(new IllegalStateException("resolver fixture failed"));

        SFMLazyExplorerLoader.LoadResult result = assertTerminal(
                fixture, handle, probe, SFMLazyExplorerLoader.LoadDisposition.FAILED);
        assertTrue(result.diagnostic().orElseThrow().contains("resolver fixture failed"));
    }

    @Test
    public void pageValidationFailureCompletesOutsideHandle() throws Exception {
        Fixture fixture = fixture(Runnable::run);
        SFMLazyExplorerLoader.LoadHandle handle = fixture.loader().refresh(ROOT, 8);
        CompletionProbe probe = new CompletionProbe(handle);

        fixture.resolver().pending.complete(page(SFMPath.parse("registry://test/wrong-parent")));

        SFMLazyExplorerLoader.LoadResult result = assertTerminal(
                fixture, handle, probe, SFMLazyExplorerLoader.LoadDisposition.FAILED);
        assertTrue(result.diagnostic().orElseThrow().contains("parent does not match"));
        assertTrue(fixture.relations().snapshot().relation().edges().isEmpty());
    }

    @Test
    public void publicationExecutorRejectionCompletesOutsideHandle() throws Exception {
        Fixture fixture = fixture(command -> {
            throw new RejectedExecutionException("owner fixture stopped");
        });
        SFMLazyExplorerLoader.LoadHandle handle = fixture.loader().refresh(ROOT, 8);
        CompletionProbe probe = new CompletionProbe(handle);

        fixture.resolver().pending.complete(page(ROOT));

        SFMLazyExplorerLoader.LoadResult result = assertTerminal(
                fixture, handle, probe, SFMLazyExplorerLoader.LoadDisposition.FAILED);
        assertTrue(result.diagnostic().orElseThrow().contains("publication executor rejected work"));
        assertTrue(fixture.relations().snapshot().relation().edges().isEmpty());
    }

    @Test
    public void sessionCanCloseWhilePublicationCallbackWaitsForItsMonitor() throws Exception {
        ManualExecutor publication = new ManualExecutor();
        Fixture fixture = fixture(publication);
        SFMExplorerSession session = new SFMExplorerSession(
                new SFMExplorerId("completion-lock"), ROOT, new SFMSelectionRepository());
        SFMLazyExplorerLoader.LoadHandle handle = session.requestChildren(ROOT, fixture.loader(), 8);
        fixture.resolver().pending.complete(page(ROOT));
        FutureTask<Void> publish = new FutureTask<>(() -> {
            publication.runNext();
            return null;
        });
        FutureTask<Boolean> cancel = new FutureTask<>(handle::cancel);
        Thread publisher = daemonThread("explorer-publication-lock-test", publish);
        Thread canceller = daemonThread("explorer-cancellation-lock-test", cancel);
        boolean cancellationFinished = false;
        try {
            synchronized (session) {
                publisher.start();
                awaitBlockedOn(publisher, session);
                canceller.start();
                try {
                    assertFalse(cancel.get(2, TimeUnit.SECONDS), "publication already won");
                    cancellationFinished = true;
                    session.close();
                } catch (TimeoutException lockRegression) {
                    // Release the session before asserting: the old lock order can
                    // then unwind, rather than stranding a real deadlocked pair.
                }
            }
        } finally {
            publisher.join(2000);
            canceller.join(2000);
        }
        assertFalse(publisher.isAlive(), "publication thread must finish after releasing the session");
        assertFalse(canceller.isAlive(), "cancellation thread must finish after releasing the session");
        publish.get(2, TimeUnit.SECONDS);
        assertFalse(cancel.get(2, TimeUnit.SECONDS));
        session.close();
        assertTrue(cancellationFinished, "a callback waiting for the session must not retain the handle monitor");
        assertTrue(session.snapshot().closed());
        assertEquals(0, session.activeRequestCount());
        assertEquals(1, session.recentRequestEvidence().size(), "record the request exactly once");
        assertEquals(handle.evidence(), session.recentRequestEvidence().get(0).evidence());
        assertEquals(Optional.of(SFMLazyExplorerLoader.LoadDisposition.PUBLISHED),
                session.recentRequestEvidence().get(0).disposition());
        assertEquals(Set.of(CHILD), fixture.relations().snapshot().relation().childrenOf(ROOT));
    }

    private static void awaitBlockedOn(Thread thread, Object monitor) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (System.nanoTime() < deadline) {
            ThreadInfo info = ManagementFactory.getThreadMXBean().getThreadInfo(thread.getId());
            if (info != null && info.getThreadState() == Thread.State.BLOCKED
                    && info.getLockInfo() != null
                    && info.getLockInfo().getIdentityHashCode() == System.identityHashCode(monitor)) {
                return;
            }
            Thread.sleep(5);
        }
        fail("publication callback did not contend for the held session monitor");
    }

    private static Thread daemonThread(String name, Runnable work) {
        Thread thread = new Thread(work, name);
        thread.setDaemon(true);
        return thread;
    }

    private static SFMLazyExplorerLoader.LoadResult assertTerminal(
            Fixture fixture,
            SFMLazyExplorerLoader.LoadHandle handle,
            CompletionProbe probe,
            SFMLazyExplorerLoader.LoadDisposition disposition
    ) throws Exception {
        SFMLazyExplorerLoader.LoadResult result = probe.observed.get(2, TimeUnit.SECONDS);
        assertFalse(probe.heldHandle.get(), "completion callbacks must run outside the handle monitor");
        assertEquals(disposition, result.disposition());
        assertEquals(handle.evidence(), result.evidence());
        assertFalse(handle.cancel(), "a terminal request cannot change its winner");
        assertSame(result, handle.completion().get(2, TimeUnit.SECONDS));
        assertEquals(1, probe.calls.get());
        assertTrue(fixture.loader().activeParents().isEmpty());
        return result;
    }

    private static Fixture fixture(Executor publication) {
        DeferredResolver resolver = new DeferredResolver();
        SFMExplorerResolverRegistry registry = new SFMExplorerResolverRegistry();
        registry.register(resolver);
        SFMChildRelationRepository relations = new SFMChildRelationRepository();
        return new Fixture(resolver, relations, new SFMLazyExplorerLoader(registry, relations, publication));
    }

    private static SFMExplorerResolver.ChildPage page(SFMPath parent) {
        return new SFMExplorerResolver.ChildPage(parent,
                List.of(SFMExplorerEntry.simple(CHILD, "child", false, Optional.of("fixture"))),
                Optional.empty(), 7, List.of(), 1);
    }

    private record Fixture(DeferredResolver resolver, SFMChildRelationRepository relations,
                           SFMLazyExplorerLoader loader) {
    }

    private static final class CompletionProbe {
        private final AtomicBoolean heldHandle = new AtomicBoolean();
        private final AtomicInteger calls = new AtomicInteger();
        private final CompletableFuture<SFMLazyExplorerLoader.LoadResult> observed;

        private CompletionProbe(SFMLazyExplorerLoader.LoadHandle handle) {
            observed = handle.completion().thenApply(result -> {
                heldHandle.set(Thread.holdsLock(handle));
                calls.incrementAndGet();
                return result;
            });
        }
    }

    private static final class DeferredResolver implements SFMExplorerResolver {
        private final CompletableFuture<ChildPage> pending = new CompletableFuture<>();
        private ChildRequest request;

        @Override
        public String scheme() { return "registry"; }

        @Override
        public long generation() { return 7; }

        @Override
        public CompletableFuture<SFMExplorerEntry> describe(SFMPath path, SFMExplorerCancellationToken cancellation) {
            return CompletableFuture.completedFuture(SFMExplorerEntry.simple(path, "root", true, Optional.empty()));
        }

        @Override
        public CompletableFuture<ChildPage> resolveChildren(ChildRequest request) {
            this.request = request;
            return pending;
        }
    }

    private static final class ManualExecutor implements Executor {
        private final ArrayDeque<Runnable> work = new ArrayDeque<>();

        @Override
        public void execute(Runnable command) { work.add(command); }

        private void runNext() { work.removeFirst().run(); }
    }
}
