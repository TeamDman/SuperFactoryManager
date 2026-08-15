package ca.teamdman.sfm.client.symbol;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SFMSymbolServerNavigationProviderTests {
    private final List<SFMSymbolServerSupervisor> supervisors = new ArrayList<>();

    @AfterEach
    void closeSupervisors() throws Exception {
        for (SFMSymbolServerSupervisor supervisor : supervisors) {
            supervisor.close();
            supervisor.termination().get(2, TimeUnit.SECONDS);
        }
    }

    @Test
    void missingExecutableFailsWithoutBlockingCallerAndLaterStartRetries() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        FakeSession recovered = FakeSession.responding(7, 1);
        SFMSymbolServerSupervisor supervisor = supervisor(configuration(Duration.ofMillis(500)), config -> {
            if (attempts.getAndIncrement() == 0) throw new IOException("missing executable with private path");
            return recovered;
        });

        CompletableFuture<SFMSymbolServerProtocol.ServerHello> first = supervisor.start(Duration.ofSeconds(1));
        assertInstanceOf(SFMSymbolServerSupervisor.WorkerUnavailableException.class, failure(first));
        assertEquals(1, supervisor.telemetry().launchFailures());

        assertEquals(7, supervisor.start(Duration.ofSeconds(1)).get().workspaceGeneration());
        assertEquals(2, attempts.get());
        assertEquals(List.of("fake-sfm.exe", "symbol", "serve", "--branch", "1.19.2"),
                supervisor.configuration().command());
    }

    @Test
    void handshakeMismatchFailsPendingAndAReplacementSessionCanRestart() throws Exception {
        AtomicReference<FakeSession> mismatchedRef = new AtomicReference<>();
        FakeSession mismatched = new FakeSession(0, frame -> {
            if (kind(frame).equals("hello")) {
                JsonObject response = JsonParser.parseString(
                        SFMSymbolServerProtocolTests.helloEnvelope(7, "D:/workspace/source", null, null)
                ).getAsJsonObject();
                response.getAsJsonObject("hello").addProperty("protocol_schema", "wrong");
                mismatchedRef.get().send(response.toString());
            }
        });
        mismatchedRef.set(mismatched);
        FakeSession recovered = definitionResponder(7, 0);
        FakeFactory factory = new FakeFactory(mismatched, recovered);
        SFMSymbolServerSupervisor supervisor = supervisor(configuration(Duration.ofMillis(500)), factory);

        assertInstanceOf(SFMSymbolServerSupervisor.WorkerUnavailableException.class,
                failure(supervisor.submit(request(1, 1, 7)).result()));
        assertTrue(mismatched.terminated.get());

        SFMDefinitionResult result = supervisor.submit(request(2, 1, 7)).result().get(2, TimeUnit.SECONDS);
        assertEquals(2, result.requestId());
        assertEquals(1, supervisor.telemetry().restarts());
    }

    @Test
    void fragmentedHelloAndCoalescedOutOfOrderResultsSupportConcurrentRequests() throws Exception {
        AtomicReference<FakeSession> sessionRef = new AtomicReference<>();
        List<SFMDefinitionRequest> requests = new ArrayList<>();
        FakeSession session = new FakeSession(1, frame -> {
            String kind = kind(frame);
            if (kind.equals("hello")) {
                sessionRef.get().send(SFMSymbolServerProtocolTests.helloEnvelope(
                        7, "D:/workspace/source", null, null));
            } else if (kind.equals("definition")) {
                requests.add(SFMDefinitionJsonCodec.decodeRequest(frame.get("request").toString()));
                if (requests.size() == 2) {
                    sessionRef.get().sendCoalesced(List.of(
                            definitionResultEnvelope(SFMSymbolServerProtocolTests.result(requests.get(1))),
                            definitionResultEnvelope(SFMSymbolServerProtocolTests.result(requests.get(0)))
                    ));
                }
            }
        });
        sessionRef.set(session);
        SFMSymbolServerSupervisor supervisor = supervisor(configuration(Duration.ofSeconds(1)),
                new FakeFactory(session));

        var first = supervisor.submit(request(1, 4, 7));
        var second = supervisor.submit(request(2, 4, 7));

        assertEquals(1, first.result().get(2, TimeUnit.SECONDS).requestId());
        assertEquals(2, second.result().get(2, TimeUnit.SECONDS).requestId());
        assertEquals(2, supervisor.telemetry().completed());
        assertTrue(session.clientFrames.stream().allMatch(value -> value.getBytes(StandardCharsets.UTF_8).length
                < supervisor.configuration().maximumFrameBytes()));
    }

    @Test
    void cancellationSendsExactIdentityAndLateResultIsDiscarded() throws Exception {
        FakeSession session = nonRespondingDefinitionSession(7, 0);
        SFMSymbolServerSupervisor supervisor = supervisor(configuration(Duration.ofSeconds(1)),
                new FakeFactory(session));
        SFMSymbolServerNavigationProvider provider = new SFMSymbolServerNavigationProvider(supervisor);
        SFMDefinitionRequest request = request(5, 3, 7);
        SFMSymbolNavigationProvider.Query query = provider.query(request);
        await(() -> session.framesOfKind("definition").size() == 1);

        assertTrue(query.cancel());
        await(() -> session.framesOfKind("cancel").size() == 1);
        JsonObject cancel = session.framesOfKind("cancel").get(0);
        assertEquals(5, cancel.get("request_id").getAsLong());
        assertEquals(3, cancel.get("request_generation").getAsLong());
        assertEquals(7, cancel.get("workspace_generation").getAsLong());

        session.send(definitionResultEnvelope(SFMSymbolServerProtocolTests.result(request)));
        await(() -> supervisor.telemetry().lateResponses() == 1);
        assertTrue(query.result().isCancelled());
    }

    @Test
    void crashFailsCurrentRequestAndNextQueryStartsOneReplacementProcess() throws Exception {
        AtomicReference<FakeSession> firstRef = new AtomicReference<>();
        FakeSession first = new FakeSession(0, frame -> {
            if (kind(frame).equals("hello")) {
                firstRef.get().send(SFMSymbolServerProtocolTests.helloEnvelope(
                        7, "D:/workspace/source", null, null));
            } else if (kind(frame).equals("definition")) {
                firstRef.get().crash();
            }
        });
        firstRef.set(first);
        FakeSession second = definitionResponder(7, 0);
        FakeFactory factory = new FakeFactory(first, second);
        SFMSymbolServerSupervisor supervisor = supervisor(configuration(Duration.ofSeconds(1)), factory);

        assertInstanceOf(SFMSymbolServerSupervisor.WorkerUnavailableException.class,
                failure(supervisor.submit(request(6, 1, 7)).result()));
        SFMDefinitionResult recovered = supervisor.submit(request(7, 1, 7)).result()
                .get(2, TimeUnit.SECONDS);

        assertEquals(7, recovered.requestId());
        assertEquals(2, factory.starts.get());
        assertTrue(first.terminated.get());
    }

    @Test
    void requestTimeoutCancelsRemoteWorkAndLateResponseDoesNotReviveIt() throws Exception {
        FakeSession session = nonRespondingDefinitionSession(7, 0);
        SFMSymbolServerSupervisor supervisor = supervisor(configuration(Duration.ofMillis(40)),
                new FakeFactory(session));
        SFMDefinitionRequest request = request(8, 1, 7);
        CompletableFuture<SFMDefinitionResult> result = supervisor.submit(request).result();

        assertInstanceOf(TimeoutException.class, failure(result));
        await(() -> session.framesOfKind("cancel").size() == 1);
        session.send(definitionResultEnvelope(SFMSymbolServerProtocolTests.result(request)));
        await(() -> supervisor.telemetry().lateResponses() == 1);
        assertEquals(1, supervisor.telemetry().timedOut());
    }

    @Test
    void pingWorkspaceReplacementAndShutdownAreAcknowledgedAndReaped() throws Exception {
        FakeSession session = FakeSession.responding(7, 0);
        SFMSymbolServerSupervisor supervisor = supervisor(configuration(Duration.ofSeconds(1)),
                new FakeFactory(session));

        SFMSymbolServerProtocol.ServerHello hello = supervisor.start(Duration.ofSeconds(1))
                .get(2, TimeUnit.SECONDS);
        assertEquals(7, hello.workspaceGeneration());
        long nonce = supervisor.ping(Duration.ofSeconds(1)).get(2, TimeUnit.SECONDS);
        assertTrue(nonce > 0);
        assertEquals(8, supervisor.updateWorkspaceGeneration(8, Duration.ofSeconds(1))
                .get(2, TimeUnit.SECONDS));
        assertEquals(8, supervisor.hello().orElseThrow().workspaceGeneration());

        supervisor.close();
        supervisor.termination().get(2, TimeUnit.SECONDS);
        assertTrue(session.terminated.get());
        assertTrue(session.descendantsReaped.get());
        assertFalse(session.alive());
    }

    @Test
    void distinctWorkspaceGenerationControlsAreSerializedAndAllAcknowledged() throws Exception {
        FakeSession session = FakeSession.responding(7, 0);
        SFMSymbolServerSupervisor supervisor = supervisor(configuration(Duration.ofSeconds(1)),
                new FakeFactory(session));
        supervisor.start(Duration.ofSeconds(1)).get(2, TimeUnit.SECONDS);

        CompletableFuture<Long> first = supervisor.updateWorkspaceGeneration(8, Duration.ofSeconds(1));
        CompletableFuture<Long> second = supervisor.updateWorkspaceGeneration(9, Duration.ofSeconds(1));

        assertEquals(8, first.get(2, TimeUnit.SECONDS));
        assertEquals(9, second.get(2, TimeUnit.SECONDS));
        assertEquals(
                List.of(8L, 9L),
                session.framesOfKind("workspace-generation").stream()
                        .map(frame -> frame.get("workspace_generation").getAsLong())
                        .toList()
        );
        assertEquals(9, supervisor.hello().orElseThrow().workspaceGeneration());
    }

    @Test
    void launchAndCompletionsStayOnDedicatedThreads() throws Exception {
        AtomicReference<String> launchThread = new AtomicReference<>();
        AtomicReference<String> completionThread = new AtomicReference<>();
        AtomicReference<FakeSession> sessionRef = new AtomicReference<>();
        AtomicReference<SFMDefinitionRequest> captured = new AtomicReference<>();
        FakeSession session = new FakeSession(0, frame -> {
            if (kind(frame).equals("hello")) {
                sessionRef.get().send(SFMSymbolServerProtocolTests.helloEnvelope(
                        7, "D:/workspace/source", null, null));
            } else if (kind(frame).equals("definition")) {
                captured.set(SFMDefinitionJsonCodec.decodeRequest(frame.get("request").toString()));
            }
        });
        sessionRef.set(session);
        SFMSymbolServerSupervisor supervisor = supervisor(configuration(Duration.ofSeconds(1)), config -> {
            launchThread.set(Thread.currentThread().getName());
            return session;
        });
        CompletableFuture<SFMDefinitionResult> result = supervisor.submit(request(9, 1, 7)).result();
        result.whenComplete((value, failure) -> completionThread.set(Thread.currentThread().getName()));
        await(() -> captured.get() != null);
        session.send(definitionResultEnvelope(SFMSymbolServerProtocolTests.result(captured.get())));
        result.get(2, TimeUnit.SECONDS);
        await(() -> completionThread.get() != null);

        assertTrue(launchThread.get().startsWith("test-symbol-state"));
        assertTrue(completionThread.get().startsWith("test-symbol-state"));
        assertFalse(Thread.currentThread().getName().equals(completionThread.get()));
    }

    private SFMSymbolServerSupervisor supervisor(
            SFMSymbolServerSupervisor.Configuration configuration,
            SFMSymbolServerSupervisor.SessionFactory factory
    ) {
        ExecutorService state = Executors.newSingleThreadExecutor(daemon("test-symbol-state"));
        ExecutorService reader = Executors.newCachedThreadPool(daemon("test-symbol-reader"));
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(
                daemon("test-symbol-timer"));
        SFMSymbolServerSupervisor supervisor = new SFMSymbolServerSupervisor(
                configuration, factory, state, reader, scheduler, true
        );
        supervisors.add(supervisor);
        return supervisor;
    }

    private static SFMSymbolServerSupervisor.Configuration configuration(Duration requestTimeout) {
        return new SFMSymbolServerSupervisor.Configuration(
                "fake-sfm.exe",
                "1.19.2",
                Duration.ofMillis(500),
                requestTimeout,
                Duration.ofMillis(100),
                1024 * 1024,
                8,
                16
        );
    }

    private static FakeSession definitionResponder(long generation, int fragmentSize) throws IOException {
        return scriptedSession(generation, fragmentSize, true);
    }

    private static FakeSession nonRespondingDefinitionSession(long generation, int fragmentSize) throws IOException {
        return scriptedSession(generation, fragmentSize, false);
    }

    private static FakeSession scriptedSession(
            long generation,
            int fragmentSize,
            boolean answerDefinitions
    ) throws IOException {
        AtomicReference<FakeSession> reference = new AtomicReference<>();
        FakeSession session = new FakeSession(fragmentSize, frame -> {
            switch (kind(frame)) {
                case "hello" -> reference.get().send(SFMSymbolServerProtocolTests.helloEnvelope(
                        generation, "D:/workspace/source", null, null));
                case "definition" -> {
                    if (answerDefinitions) {
                        SFMDefinitionRequest request = SFMDefinitionJsonCodec.decodeRequest(
                                frame.get("request").toString());
                        reference.get().send(definitionResultEnvelope(
                                SFMSymbolServerProtocolTests.result(request)));
                    }
                }
                case "ping" -> reference.get().send(pong(frame.get("nonce").getAsLong()));
                case "workspace-generation" -> reference.get().send(workspaceAck(
                        frame.get("workspace_generation").getAsLong(), 0));
                case "shutdown" -> reference.get().send(shutdownAck());
            }
        });
        reference.set(session);
        return session;
    }

    private static SFMDefinitionRequest request(long id, long generation, long workspaceGeneration) {
        return SFMSymbolServerProtocolTests.request(id, generation, workspaceGeneration);
    }

    private static String definitionResultEnvelope(SFMDefinitionResult result) {
        JsonObject envelope = base("definition-result", SFMSymbolServerProtocol.DEFINITION_SCHEMA);
        envelope.add("result", JsonParser.parseString(SFMDefinitionJsonCodec.encodeResult(result)));
        return envelope.toString();
    }

    private static String pong(long nonce) {
        JsonObject envelope = base("pong", SFMSymbolServerProtocol.PING_SCHEMA);
        envelope.addProperty("nonce", nonce);
        return envelope.toString();
    }

    private static String workspaceAck(long generation, long cancelled) {
        JsonObject hello = JsonParser.parseString(SFMSymbolServerProtocolTests.helloEnvelope(
                generation, "D:/workspace/source", null, null)).getAsJsonObject()
                .getAsJsonObject("hello");
        JsonObject update = new JsonObject();
        update.add("workspace", hello.getAsJsonObject("workspace"));
        update.addProperty("cancelled_requests", cancelled);
        JsonObject envelope = base("workspace-generation", SFMSymbolServerProtocol.WORKSPACE_GENERATION_SCHEMA);
        envelope.add("update", update);
        return envelope.toString();
    }

    private static String shutdownAck() {
        return base("shutdown", SFMSymbolServerProtocol.SHUTDOWN_SCHEMA).toString();
    }

    private static JsonObject base(String kind, String schema) {
        JsonObject result = new JsonObject();
        result.addProperty("kind", kind);
        result.addProperty("schema", schema);
        return result;
    }

    private static String kind(JsonObject frame) {
        return frame.get("kind").getAsString();
    }

    private static Throwable failure(CompletableFuture<?> future) throws Exception {
        try {
            future.get(2, TimeUnit.SECONDS);
            throw new AssertionError("Expected future failure");
        } catch (ExecutionException failure) {
            return failure.getCause();
        }
    }

    private static void await(Check check) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (!check.get() && System.nanoTime() < deadline) Thread.sleep(2);
        assertTrue(check.get(), "condition did not become true");
    }

    private static java.util.concurrent.ThreadFactory daemon(String name) {
        AtomicInteger ids = new AtomicInteger();
        return action -> {
            Thread thread = new Thread(action, name + "-" + ids.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }

    @FunctionalInterface
    private interface Check { boolean get() throws Exception; }

    private static final class FakeFactory implements SFMSymbolServerSupervisor.SessionFactory {
        private final Deque<FakeSession> sessions = new ArrayDeque<>();
        private final AtomicInteger starts = new AtomicInteger();

        private FakeFactory(FakeSession... sessions) {
            this.sessions.addAll(List.of(sessions));
        }

        @Override
        public synchronized SFMSymbolServerSupervisor.WorkerSession start(
                SFMSymbolServerSupervisor.Configuration configuration
        ) throws IOException {
            starts.incrementAndGet();
            FakeSession session = sessions.pollFirst();
            if (session == null) throw new IOException("no scripted process");
            return session;
        }
    }

    private static final class FakeSession implements SFMSymbolServerSupervisor.WorkerSession {
        private static final AtomicInteger PIDS = new AtomicInteger(10_000);
        private final long pid = PIDS.incrementAndGet();
        private final PipedInputStream stdout = new PipedInputStream(64 * 1024);
        private final PipedOutputStream serverOutput;
        private final CapturingInput stdin;
        private final int fragmentSize;
        private final List<String> clientFrames = java.util.Collections.synchronizedList(new ArrayList<>());
        private final AtomicBoolean terminated = new AtomicBoolean();
        private final AtomicBoolean descendantsReaped = new AtomicBoolean();
        private volatile boolean alive = true;

        private FakeSession(int fragmentSize, Consumer<JsonObject> handler) throws IOException {
            this.fragmentSize = fragmentSize;
            this.serverOutput = new PipedOutputStream(stdout);
            this.stdin = new CapturingInput(json -> {
                clientFrames.add(json);
                handler.accept(JsonParser.parseString(json).getAsJsonObject());
            });
        }

        static FakeSession responding(long generation, int fragmentSize) throws IOException {
            return definitionResponder(generation, fragmentSize);
        }

        @Override public long pid() { return pid; }
        @Override public InputStream stdout() { return stdout; }
        @Override public OutputStream stdin() { return stdin; }
        @Override public boolean alive() { return alive; }

        synchronized void send(String json) {
            try {
                byte[] frame = framed(json);
                if (fragmentSize <= 0) {
                    serverOutput.write(frame);
                } else {
                    for (int offset = 0; offset < frame.length; offset += fragmentSize) {
                        serverOutput.write(frame, offset, Math.min(fragmentSize, frame.length - offset));
                        serverOutput.flush();
                    }
                }
                serverOutput.flush();
            } catch (IOException failure) {
                if (alive) throw new AssertionError(failure);
            }
        }

        synchronized void sendCoalesced(List<String> jsonFrames) {
            try {
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                for (String json : jsonFrames) bytes.writeBytes(framed(json));
                serverOutput.write(bytes.toByteArray());
                serverOutput.flush();
            } catch (IOException failure) {
                if (alive) throw new AssertionError(failure);
            }
        }

        synchronized void crash() {
            alive = false;
            try {
                serverOutput.close();
            } catch (IOException ignored) {
            }
        }

        List<JsonObject> framesOfKind(String kind) {
            synchronized (clientFrames) {
                return clientFrames.stream()
                        .map(JsonParser::parseString)
                        .map(JsonElement -> JsonElement.getAsJsonObject())
                        .filter(frame -> kind(frame).equals(kind))
                        .toList();
            }
        }

        @Override
        public synchronized void terminateTree(Duration grace) {
            terminated.set(true);
            descendantsReaped.set(true);
            alive = false;
            try { serverOutput.close(); } catch (IOException ignored) { }
            try { stdout.close(); } catch (IOException ignored) { }
            stdin.close();
        }

        private static byte[] framed(String json) throws IOException {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            SFMDefinitionWorkerFrameCodec.write(bytes, json, 1024 * 1024);
            return bytes.toByteArray();
        }
    }

    private static final class CapturingInput extends OutputStream {
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private final Consumer<String> frameConsumer;
        private boolean closed;

        private CapturingInput(Consumer<String> frameConsumer) {
            this.frameConsumer = frameConsumer;
        }

        @Override public synchronized void write(int value) throws IOException {
            requireOpen();
            bytes.write(value);
        }

        @Override public synchronized void write(byte[] value, int offset, int length) throws IOException {
            requireOpen();
            bytes.write(value, offset, length);
        }

        @Override
        public synchronized void flush() throws IOException {
            requireOpen();
            byte[] current = bytes.toByteArray();
            int offset = 0;
            while (current.length - offset >= 4) {
                int length = ByteBuffer.wrap(current, offset, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
                if (length <= 0) throw new IOException("invalid client frame length");
                if (current.length - offset - 4 < length) break;
                String json = new String(current, offset + 4, length, StandardCharsets.UTF_8);
                frameConsumer.accept(json);
                offset += 4 + length;
            }
            if (offset > 0) {
                bytes.reset();
                bytes.write(current, offset, current.length - offset);
            }
        }

        @Override public synchronized void close() {
            closed = true;
        }

        private void requireOpen() throws IOException {
            if (closed) throw new IOException("fake stdin closed");
        }
    }
}
