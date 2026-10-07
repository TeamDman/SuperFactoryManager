package ca.teamdman.sfm.client.terminal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Proxy;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static ca.teamdman.sfm.client.terminal.TouchDisplayTerminalWorkerProtocolTests.*;
import static org.junit.jupiter.api.Assertions.*;

class TouchDisplayTerminalServiceTransportTests {
    @Test void interactiveModeKeepsConfiguredShellAndForwardsPairedCellClicks() {
        FakeRemote remote = new FakeRemote();
        ManualExecutor io = new ManualExecutor();
        var transport = new TouchDisplayTerminalServiceTransport(() -> remote.service, "",
                TouchDisplayTerminalServiceTransport.Mode.INTERACTIVE, 512, 384, io, () -> 0);
        transport.start(); io.runNext(); transport.pump();
        assertTrue(transport.ready());
        assertNull(remote.executed, "Interactive mode must not replace the normal configured shell");
        assertEquals(List.of("requestTransport", "resize", "requestConnect"), remote.calls);
        assertEquals(0, remote.contentReads, "Ordinary terminal output is not interpreted as worker JSON");
        assertTrue(transport.touch(1, 1, 0));
        assertEquals(List.of("79,0,1,true", "79,0,0,false"), remote.mouse);
        assertTrue(remote.sent.isEmpty());
        assertEquals(0, transport.acknowledgedTouches(), "A queued mouse event is not a structured worker acknowledgement");
        transport.close();
    }

    @Test void rejectedInteractiveReleaseClosesSessionInsteadOfLeavingInputHeld() {
        FakeRemote remote = new FakeRemote();
        remote.acceptMouseRelease = false;
        ManualExecutor io = new ManualExecutor();
        var transport = new TouchDisplayTerminalServiceTransport(() -> remote.service, "",
                TouchDisplayTerminalServiceTransport.Mode.INTERACTIVE, 512, 384, io, () -> 0);
        transport.start(); io.runNext(); transport.pump();
        assertFalse(transport.touch(1, .5, .5));
        assertEquals(Optional.of("terminal_input_rejected"), transport.failure());
        assertFalse(transport.active());
        assertEquals(1, remote.closes.get());
    }

    @Test void serviceCreationBootstrapAndContentReadsAreQueuedNotRunByClientPump() {
        Fixture f = new Fixture();
        f.transport.start();
        assertTrue(f.transport.active());
        assertFalse(f.transport.ready());
        assertEquals(0, f.factories);
        f.io.runNext();
        assertEquals(1, f.factories);
        assertEquals(List.of("requestTransport", "resize", "execute"), f.remote.calls);
        assertEquals("fixed worker command", f.remote.executed);
        assertFalse(f.transport.ready(), "Accepted shell input alone is not worker readiness");
        f.transport.pump();
        assertEquals(0, f.remote.contentReads);
        f.io.runNext();
        assertEquals(1, f.remote.contentReads);
        assertFalse(f.transport.ready());
        f.transport.pump();
        assertTrue(f.transport.ready());
        assertEquals(0, f.io.tasks.size(), "Polling cadence must not busy loop");
        f.transport.close();
        assertEquals(1, f.remote.closes.get());
        assertTrue(f.io.isShutdown());
    }

    @Test void onlyMatchingStructuredAckReleasesTheSinglePendingInput() {
        Fixture f = new Fixture(); f.startReady();
        assertTrue(f.transport.touch(7, .25, .75));
        assertEquals(List.of(TouchDisplayTerminalWorkerProtocol.touch(7, .25, .75) + "\r"), f.remote.sent);
        assertFalse(f.transport.touch(8, .5, .5));
        f.poll(READY + "\n" + TouchDisplayTerminalWorkerProtocol.touch(7, .25, .75));
        assertEquals(0, f.transport.acknowledgedTouches(), "PTY echo does not acknowledge execution");
        assertFalse(f.transport.touch(8, .5, .5));
        f.poll(READY + "\n" + ack(7, 1, .25, .75) + "\n" + render(1));
        assertEquals(1, f.transport.acknowledgedTouches());
        assertTrue(f.transport.touch(8, .5, .5));
        f.poll(ack(7, 1, .25, .75));
        assertEquals(1, f.transport.acknowledgedTouches(), "Retained old acknowledgements are not replayed");
        f.poll(ack(8, 2, .5, .5));
        assertEquals(2, f.transport.acknowledgedTouches());
        f.transport.close();
    }

    @Test void wrongIdCounterOrCoordinatesFailClosedRatherThanAcceptingAnotherReply() {
        String[] mismatches = {ack(8, 1, .25, .75), ack(7, 2, .25, .75), ack(7, 1, .75, .25)};
        for (String mismatch : mismatches) {
            Fixture f = new Fixture(); f.startReady();
            assertTrue(f.transport.touch(7, .25, .75));
            f.poll(mismatch);
            assertEquals(Optional.of("worker_ack_mismatch"), f.transport.failure());
            assertFalse(f.transport.active());
            assertEquals(1, f.remote.closes.get());
            assertFalse(f.transport.touch(9, .5, .5));
        }
    }

    @Test void unsolicitedAckOrProtocolErrorEndsOwnedSession() {
        String[] invalid = {ack(7, 1, .25, .75), READY.replace("1024", "2048"),
                "{\"type\":\"error\",\"version\":1,\"count\":0,\"code\":\"invalid_input\"}",
                "{\"type\":\"bye\",\"version\":1,\"count\":0,\"reason\":\"eof\"}"};
        for (String content : invalid) {
            Fixture f = new Fixture(); f.startReady(); f.poll(content);
            assertFalse(f.transport.active());
            assertEquals(1, f.remote.closes.get());
            assertTrue(f.transport.failure().isPresent());
        }
    }

    @Test void startupAndAcknowledgementWaitsHaveMonotonicBounds() {
        Fixture unstarted = new Fixture();
        unstarted.transport.start();
        unstarted.clock.set(Duration.ofSeconds(30).toNanos());
        unstarted.transport.pump();
        assertEquals(Optional.of("worker_start_timeout"), unstarted.transport.failure());
        assertEquals(0, unstarted.factories);
        assertTrue(unstarted.io.tasks.isEmpty());

        Fixture f = new Fixture(); f.startReady();
        assertTrue(f.transport.touch(1, .5, .5));
        f.clock.addAndGet(Duration.ofSeconds(20).toNanos());
        f.transport.pump();
        assertEquals(Optional.of("worker_ack_timeout"), f.transport.failure());
        assertEquals(1, f.remote.closes.get());
    }

    @Test void boundedRawFramesAreDrainedOnlyAfterReadinessAndIgnoreOldSequences() {
        Fixture f = new Fixture();
        f.transport.start(); f.io.runNext();
        f.remote.frame = frame(1, 2, 2, "stream");
        assertTrue(f.transport.latestFrame().isEmpty());
        assertEquals(0, f.remote.frameReads);
        f.transport.pump(); f.io.runNext(); f.transport.pump();
        var first = f.transport.latestFrame().orElseThrow();
        assertEquals(1, first.sequence()); assertEquals(2, first.width()); assertEquals(2, first.height());
        assertTrue(f.transport.latestFrame().isEmpty());
        f.remote.frame = frame(0, 2, 2, "stream");
        assertTrue(f.transport.latestFrame().isEmpty());
        assertTrue(f.transport.active());
        f.remote.frame = frame(3, 2, 2, "stream");
        assertEquals(3, f.transport.latestFrame().orElseThrow().sequence());
        f.transport.close();
    }

    @Test void reconnectStreamReplacementOrDimensionChangeRequiresFreshSessionAndLease() {
        for (int scenario = 0; scenario < 4; scenario++) {
            Fixture f = new Fixture(); f.startReady();
            f.remote.frame = frame(1, 2, 2, "stream");
            assertTrue(f.transport.latestFrame().isPresent());
            switch (scenario) {
                case 0 -> f.remote.epoch++;
                case 1 -> f.remote.connected = false;
                case 2 -> f.remote.frame = frame(2, 2, 2, "new-stream");
                case 3 -> f.remote.frame = frame(2, 3, 2, "stream");
            }
            if (scenario < 2) f.transport.pump();
            else assertTrue(f.transport.latestFrame().isEmpty());
            assertFalse(f.transport.active());
            assertEquals(1, f.remote.closes.get());
            assertThrows(IllegalStateException.class, f.transport::start);
        }
    }

    @Test void pngDirtyOversizedAndInvalidPayloadsAreRejected() {
        SFMTerminalFrame[] invalid = {
                new SFMTerminalFrame(1, true, true, new byte[4], metadata(1, 1), "stream"),
                new SFMTerminalFrame(1, false, false, new byte[4], metadata(1, 1), "stream"),
                new SFMTerminalFrame(1, true, false, new byte[4], metadata(513, 1), "stream"),
                new SFMTerminalFrame(1, true, false, new byte[3], metadata(1, 1), "stream")
        };
        for (var frame : invalid) {
            Fixture f = new Fixture(); f.startReady(); f.remote.frame = frame;
            assertTrue(f.transport.latestFrame().isEmpty());
            assertFalse(f.transport.active());
            assertEquals(1, f.remote.closes.get());
        }
    }

    @Test void factoryOrTuningFailureReleasesWhatWasActuallyCreated() {
        Fixture f = new Fixture();
        f.remote.acceptResize = false;
        f.transport.start(); f.io.runNext();
        assertFalse(f.transport.active());
        assertEquals(Optional.of("terminal_worker_io_failed"), f.transport.failure());
        assertEquals(1, f.remote.closes.get());
        assertNull(f.remote.executed);

        ManualExecutor io = new ManualExecutor();
        var throwing = new TouchDisplayTerminalServiceTransport(() -> { throw new IllegalStateException("fixture"); },
                "fixed", 512, 384, io, () -> 0);
        throwing.start(); io.runNext();
        assertFalse(throwing.active());
        assertTrue(io.isShutdown());
    }

    @Test void workerQueueRejectionIsBoundedAndCloseCancelsQueuedStartup() {
        Fixture rejected = new Fixture();
        rejected.io.shutdown();
        rejected.transport.start();
        assertEquals(Optional.of("terminal_worker_queue_full"), rejected.transport.failure());
        assertFalse(rejected.transport.active());

        Fixture cancelled = new Fixture();
        cancelled.transport.start(); cancelled.transport.close(); cancelled.transport.close();
        assertEquals(0, cancelled.factories);
        assertTrue(cancelled.io.tasks.isEmpty());
        assertFalse(cancelled.transport.active());
    }

    @Test void closeDuringBackgroundCreationReleasesLateServiceWithoutClientBlocking() throws Exception {
        FakeRemote remote = new FakeRemote();
        CountDownLatch creating = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService io = TouchDisplayTerminalServiceTransport.boundedExecutor();
        Thread client = Thread.currentThread();
        var transport = new TouchDisplayTerminalServiceTransport(() -> {
            assertNotSame(client, Thread.currentThread());
            creating.countDown();
            boolean interrupted = false;
            while (true) {
                try { if (release.await(5, TimeUnit.SECONDS)) break; else throw new IllegalStateException("fixture timed out"); }
                catch (InterruptedException ignored) { interrupted = true; }
            }
            if (interrupted) Thread.currentThread().interrupt();
            return remote.service;
        }, "fixed", 512, 384, io, System::nanoTime);
        try {
            transport.start();
            assertTrue(creating.await(5, TimeUnit.SECONDS));
            transport.close();
            assertFalse(transport.active());
            release.countDown();
            assertTrue(io.awaitTermination(5, TimeUnit.SECONDS));
            assertEquals(1, remote.closes.get());
            assertTrue(remote.calls.isEmpty(), "A late service cannot bootstrap after closure");
        } finally { release.countDown(); transport.close(); }
    }

    @Test void localSelectionRejectsRemoteOrUnresolvedEndpointsAndQuotesFixedExecutable(@TempDir Path directory) throws Exception {
        Path worker = Files.createFile(directory.resolve("test worker's executable.exe"));
        String command = TouchDisplayTerminalServiceTransport.bootstrapCommand(worker);
        assertEquals("& '" + worker.toRealPath().toString().replace("'", "''") + "' terminal worker", command);
        assertThrows(IllegalArgumentException.class, () -> TouchDisplayTerminalServiceTransport.bootstrapCommand(Path.of("relative.exe")));
        assertThrows(IllegalArgumentException.class, () -> TouchDisplayTerminalServiceTransport.bootstrapCommand(directory));
        assertThrows(IllegalArgumentException.class, () -> TouchDisplayTerminalServiceTransport.local(
                InetSocketAddress.createUnresolved("localhost", 12345), worker));
        assertThrows(IllegalArgumentException.class, () -> TouchDisplayTerminalServiceTransport.local(
                new InetSocketAddress(InetAddress.getByAddress(new byte[]{(byte) 192, 0, 2, 1}), 12345), worker));
        var local = TouchDisplayTerminalServiceTransport.local(new InetSocketAddress(InetAddress.getLoopbackAddress(), 12345), worker);
        local.close(); // No connection or process creation occurred.
    }

    private static SFMTerminalFrame frame(long sequence, int width, int height, String stream) {
        return new SFMTerminalFrame(sequence, true, false, new byte[width * height * 4], metadata(width, height), stream);
    }

    private static SFMTerminalFrameMetadata metadata(int width, int height) {
        return new SFMTerminalFrameMetadata(1, 80, 32, width, height, 512, 384, 6, 8, 8,
                "fixture", "full-raw-rgba", 0, 0, 0, 0, 0, 0, 0, 0, "fixture");
    }

    private static final class Fixture {
        final ManualExecutor io = new ManualExecutor();
        final AtomicLong clock = new AtomicLong();
        final FakeRemote remote = new FakeRemote();
        int factories;
        final TouchDisplayTerminalServiceTransport transport = new TouchDisplayTerminalServiceTransport(() -> {
            factories++; return remote.service;
        }, "fixed worker command", 512, 384, io, clock::get);

        void startReady() { transport.start(); io.runNext(); transport.pump(); io.runNext(); transport.pump(); assertTrue(transport.ready()); }
        void poll(String content) {
            remote.content = content;
            clock.addAndGet(Duration.ofMillis(250).toNanos());
            transport.pump(); io.runNext(); transport.pump();
        }
    }

    private static final class ManualExecutor extends AbstractExecutorService {
        final Deque<Runnable> tasks = new ArrayDeque<>();
        boolean shutdown;
        public void execute(Runnable command) { if (shutdown || tasks.size() >= 1) throw new RejectedExecutionException(); tasks.add(command); }
        void runNext() { tasks.remove().run(); }
        public void shutdown() { shutdown = true; }
        public List<Runnable> shutdownNow() { shutdown = true; var pending = List.copyOf(tasks); tasks.clear(); return pending; }
        public boolean isShutdown() { return shutdown; }
        public boolean isTerminated() { return shutdown && tasks.isEmpty(); }
        public boolean awaitTermination(long timeout, TimeUnit unit) { return isTerminated(); }
    }

    private static final class FakeRemote {
        final List<String> calls = new ArrayList<>();
        final List<String> sent = new ArrayList<>();
        final List<String> mouse = new ArrayList<>();
        final AtomicInteger closes = new AtomicInteger();
        boolean connected = true, acceptResize = true, acceptMouseRelease = true;
        long epoch = 1;
        int contentReads, frameReads;
        String content = READY, executed;
        SFMTerminalFrame frame;
        final SFMTerminalRemoteService service = (SFMTerminalRemoteService) Proxy.newProxyInstance(
                SFMTerminalRemoteService.class.getClassLoader(), new Class<?>[]{SFMTerminalRemoteService.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "requestTransport" -> { calls.add("requestTransport"); assertEquals("full-raw-rgba", args[0]); yield new SFMTerminalPresentationChangeResult(true, "fixture"); }
                    case "resize" -> { calls.add("resize"); yield acceptResize; }
                    case "requestConnect" -> { calls.add("requestConnect"); yield null; }
                    case "openSession" -> new SFMTerminalService.SFMTerminalSession() {
                        public SFMTerminalResponse execute(String command) { calls.add("execute"); executed = command; return SFMTerminalResponse.ok(List.of(), "fixture"); }
                        public String workingDirectory() { return "fixture"; }
                    };
                    case "connectionSnapshot" -> new SFMTerminalConnectionSnapshot(connected, false, connected, epoch, Optional.empty());
                    case "contentForAutomation" -> { contentReads++; yield content; }
                    case "latestFrame" -> { frameReads++; var result = Optional.ofNullable(frame); frame = null; yield result; }
                    case "sendText" -> { sent.add((String) args[0]); yield true; }
                    case "logicalWidth" -> 80;
                    case "logicalHeight" -> 32;
                    case "sendMouse" -> {
                        mouse.add(args[0] + "," + args[1] + "," + args[2] + "," + args[4]);
                        yield (boolean) args[4] || acceptMouseRelease;
                    }
                    case "close" -> { closes.incrementAndGet(); yield null; }
                    default -> throw new AssertionError("Unexpected remote call: " + method.getName());
                });
    }
}
