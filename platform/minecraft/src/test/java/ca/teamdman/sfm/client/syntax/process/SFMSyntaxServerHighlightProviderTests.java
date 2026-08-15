package ca.teamdman.sfm.client.syntax.process;

import ca.teamdman.sfm.client.syntax.SFMSyntaxHighlightJsonCodec;
import ca.teamdman.sfm.client.syntax.SFMSyntaxHighlightLimits;
import ca.teamdman.sfm.client.syntax.SFMSyntaxHighlightProvider;
import ca.teamdman.sfm.client.syntax.SFMSyntaxHighlightRequest;
import ca.teamdman.sfm.client.syntax.SFMSyntaxHighlightResult;
import com.google.gson.JsonArray;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SFMSyntaxServerHighlightProviderTests {
    private final List<SFMSyntaxServerSupervisor> supervisors = new ArrayList<>();

    @AfterEach
    void closeSupervisors() throws Exception {
        for (SFMSyntaxServerSupervisor supervisor : supervisors) {
            supervisor.close();
            supervisor.termination().get(2, TimeUnit.SECONDS);
        }
    }

    @Test
    void handshakeNegotiatesCapabilitiesLimitsAndCanonicalCommand() throws Exception {
        FakeSession session = respondingSession(3, true);
        SFMSyntaxServerSupervisor supervisor = supervisor(configuration(
                Duration.ofMillis(500),
                Duration.ofSeconds(1),
                5
        ), new FakeFactory(session));

        SFMSyntaxServerProtocol.ServerHello hello = supervisor.start(Duration.ofSeconds(1))
                .get(2, TimeUnit.SECONDS);

        assertEquals(SFMSyntaxServerProtocol.PROTOCOL_SCHEMA, hello.protocolSchema());
        assertEquals(3, hello.maximumPendingRequests());
        assertEquals(List.of("fake-sfm.exe", "syntax", "serve"), supervisor.configuration().command());
        JsonObject clientHello = session.framesOfKind("hello").get(0).getAsJsonObject("hello");
        assertEquals(SFMSyntaxServerSupervisor.CLIENT_NAME, clientHello.get("client_name").getAsString());
        assertEquals(4, clientHello.getAsJsonArray("capabilities").size());
        assertEquals(SFMSyntaxServerSupervisor.Lifecycle.READY, supervisor.lifecycle());
    }

    @Test
    void providerSubmitsAsynchronouslyAndCompletesMatchingResult() throws Exception {
        FakeSession session = respondingSession(8, false);
        SFMSyntaxServerSupervisor supervisor = supervisor(defaultConfiguration(), new FakeFactory(session));
        SFMSyntaxServerHighlightProvider provider = new SFMSyntaxServerHighlightProvider(supervisor);
        SFMSyntaxHighlightRequest request = request(1, 2, "class Café {}\n");
        AtomicReference<String> completionThread = new AtomicReference<>();

        SFMSyntaxHighlightProvider.Query query = provider.query(request);
        query.result().whenComplete((ignored, failure) -> completionThread.set(Thread.currentThread().getName()));
        await(() -> session.framesOfKind("highlight").size() == 1);
        SFMSyntaxHighlightRequest received = SFMSyntaxHighlightJsonCodec.decodeRequest(
                session.framesOfKind("highlight").get(0).getAsJsonObject("request").toString()
        );
        session.send(resultEnvelope(result(received)));
        SFMSyntaxHighlightResult result = query.result().get(2, TimeUnit.SECONDS);
        await(() -> completionThread.get() != null);

        assertEquals(request.requestId(), result.requestId());
        assertTrue(result.matchesIdentity(request));
        assertEquals(SFMSyntaxServerHighlightProvider.ID, provider.id());
        assertEquals(1, supervisor.telemetry().completed());
        assertTrue(completionThread.get().startsWith("test-syntax-state"));
        assertFalse(Thread.currentThread().getName().equals(completionThread.get()));
        assertTrue(session.framesOfKind("highlight").stream()
                .map(frame -> frame.getAsJsonObject("request"))
                .anyMatch(frame -> frame.get("source_sha256").getAsString().equals(request.sourceSha256())));
    }

    @Test
    void cancellationSendsTheCompleteImmutableIdentity() throws Exception {
        FakeSession session = respondingSession(8, false);
        SFMSyntaxServerSupervisor supervisor = supervisor(defaultConfiguration(), new FakeFactory(session));
        SFMSyntaxServerHighlightProvider provider = new SFMSyntaxServerHighlightProvider(supervisor);
        SFMSyntaxHighlightRequest request = request(7, 4, "class Cancelled {}\n");
        SFMSyntaxHighlightProvider.Query query = provider.query(request);
        await(() -> session.framesOfKind("highlight").size() == 1);

        assertTrue(query.cancel());
        await(() -> session.framesOfKind("cancel").size() == 1);
        JsonObject cancellation = session.framesOfKind("cancel").get(0);

        assertEquals(request.requestId(), cancellation.get("request_id").getAsLong());
        assertEquals(request.requestGeneration(), cancellation.get("request_generation").getAsLong());
        assertEquals(request.originId(), cancellation.get("origin_id").getAsString());
        assertEquals(request.originGeneration(), cancellation.get("origin_generation").getAsLong());
        assertTrue(query.result().isCancelled());
    }

    @Test
    void localQueueCapacityIsSeparateFromRetainedRemoteOccupancy() throws Exception {
        FakeSession session = respondingSession(1, false);
        SFMSyntaxServerSupervisor supervisor = supervisor(configuration(
                Duration.ofMillis(500),
                Duration.ofSeconds(2),
                1
        ), new FakeFactory(session));
        SFMSyntaxServerSupervisor.Submission first = supervisor.submit(request(1, 1, "class First {}"));
        await(() -> session.framesOfKind("highlight").size() == 1);

        SFMSyntaxServerSupervisor.Submission second = supervisor.submit(request(2, 1, "class Second {}"));
        SFMSyntaxServerSupervisor.Submission third = supervisor.submit(request(3, 1, "class Third {}"));

        assertFalse(second.result().isDone());
        assertInstanceOf(SFMSyntaxServerSupervisor.CapacityException.class, failure(third.result()));
        assertEquals(2, supervisor.telemetry().pendingRequests());
        assertEquals(1, session.framesOfKind("highlight").size());
        first.cancellation().run();
        second.cancellation().run();
    }

    @Test
    void rapidSupersessionRetainsRemoteSlotUntilTerminalResultAndQueuesLatest() throws Exception {
        AtomicReference<FakeSession> reference = new AtomicReference<>();
        List<SFMSyntaxHighlightRequest> received = new CopyOnWriteArrayList<>();
        FakeSession session = new FakeSession(frame -> {
            switch (kind(frame)) {
                case "hello" -> reference.get().send(helloEnvelope(1));
                case "highlight" -> received.add(SFMSyntaxHighlightJsonCodec.decodeRequest(
                        frame.getAsJsonObject("request").toString()
                ));
                case "cancel" -> reference.get().send(cancelledEnvelope(frame));
                case "shutdown" -> reference.get().send(shutdownEnvelope());
                default -> { }
            }
        });
        reference.set(session);
        SFMSyntaxServerSupervisor supervisor = supervisor(configuration(
                Duration.ofMillis(500),
                Duration.ofSeconds(2),
                1
        ), new FakeFactory(session));

        SFMSyntaxServerSupervisor.Submission active = supervisor.submit(request(1, 1, "class Generation1 {}"));
        await(() -> received.size() == 1);
        active.cancellation().run();
        assertInstanceOf(java.util.concurrent.CancellationException.class, failure(active.result()));
        await(() -> session.framesOfKind("cancel").size() == 1);

        SFMSyntaxServerSupervisor.Submission latest = null;
        for (int generation = 2; generation <= 9; generation++) {
            if (latest != null) latest.cancellation().run();
            latest = supervisor.submit(request(generation, generation, "class Generation" + generation + " {}"));
        }
        SFMSyntaxServerSupervisor.Submission expectedLatest = latest;
        assertFalse(expectedLatest.result().isDone());
        assertEquals(1, received.size(), "a cancellation-requested acknowledgement is not terminal");

        session.send(resultEnvelope(result(received.get(0), SFMSyntaxHighlightResult.Outcome.CANCELLED)));
        await(() -> received.size() == 2);
        assertEquals(9, received.get(1).requestId());
        session.send(resultEnvelope(result(received.get(1))));

        assertEquals(9, expectedLatest.result().get(2, TimeUnit.SECONDS).requestId());
        assertEquals(1, supervisor.telemetry().completed());
        assertEquals(0, supervisor.telemetry().remoteFailures());
    }

    @Test
    void launchBackoffCoalescesAutomaticRetriesAndManualRetryBypassesIt() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        AtomicReference<String> launchThread = new AtomicReference<>();
        FakeSession recovered = respondingSession(8, true);
        SFMSyntaxServerSupervisor.Configuration configuration = new SFMSyntaxServerSupervisor.Configuration(
                "fake-sfm.exe",
                Duration.ofMillis(500),
                Duration.ofSeconds(2),
                Duration.ofMillis(100),
                1024 * 1024,
                8,
                Duration.ofSeconds(1),
                Duration.ofSeconds(4)
        );
        SFMSyntaxServerSupervisor supervisor = supervisor(configuration, ignored -> {
            launchThread.set(Thread.currentThread().getName());
            if (attempts.getAndIncrement() == 0) {
                throw new IOException("missing executable at a private path");
            }
            return recovered;
        });

        assertInstanceOf(
                SFMSyntaxServerSupervisor.WorkerUnavailableException.class,
                failure(supervisor.start(Duration.ofSeconds(1)))
        );
        assertEquals(1, supervisor.telemetry().launchFailures());

        CompletableFuture<SFMSyntaxServerProtocol.ServerHello> automaticOne =
                supervisor.start(Duration.ofSeconds(2));
        CompletableFuture<SFMSyntaxServerProtocol.ServerHello> automaticTwo =
                supervisor.start(Duration.ofSeconds(2));
        Thread.sleep(25);
        assertEquals(1, attempts.get(), "editor mutations during backoff must not relaunch the worker");

        supervisor.retry(Duration.ofSeconds(1)).get(2, TimeUnit.SECONDS);
        automaticOne.get(2, TimeUnit.SECONDS);
        automaticTwo.get(2, TimeUnit.SECONDS);
        assertEquals(2, attempts.get());
        assertEquals(1, supervisor.telemetry().restarts());
        assertTrue(launchThread.get().startsWith("test-syntax-state"));
        assertFalse(Thread.currentThread().getName().equals(launchThread.get()));
    }

    @Test
    void crashFailsCurrentRequestAndTheNextRequestStartsOneReplacement() throws Exception {
        AtomicReference<FakeSession> firstReference = new AtomicReference<>();
        FakeSession first = new FakeSession(frame -> {
            switch (kind(frame)) {
                case "hello" -> firstReference.get().send(helloEnvelope(8));
                case "highlight" -> firstReference.get().crash();
                default -> { }
            }
        });
        firstReference.set(first);
        FakeSession recovered = respondingSession(8, true);
        FakeFactory factory = new FakeFactory(first, recovered);
        SFMSyntaxServerSupervisor supervisor = supervisor(defaultConfiguration(), factory);

        assertInstanceOf(
                SFMSyntaxServerSupervisor.WorkerUnavailableException.class,
                failure(supervisor.submit(request(11, 1, "class Crashes {}" )).result())
        );
        SFMSyntaxHighlightResult result = supervisor.submit(request(12, 1, "class Recovers {}"))
                .result()
                .get(2, TimeUnit.SECONDS);

        assertEquals(12, result.requestId());
        assertEquals(2, factory.starts.get());
        assertEquals(1, supervisor.telemetry().restarts());
        assertTrue(first.terminated.get());
    }

    @Test
    void malformedSchemaFailsTheSessionAndDoesNotPublishAResult() throws Exception {
        AtomicReference<FakeSession> reference = new AtomicReference<>();
        FakeSession malformed = new FakeSession(frame -> {
            if (kind(frame).equals("hello")) {
                reference.get().send(helloEnvelope(8));
            } else if (kind(frame).equals("highlight")) {
                SFMSyntaxHighlightRequest request = SFMSyntaxHighlightJsonCodec.decodeRequest(
                        frame.getAsJsonObject("request").toString()
                );
                JsonObject envelope = JsonParser.parseString(resultEnvelope(result(request))).getAsJsonObject();
                envelope.addProperty("schema", "sfm.syntax-server.highlight/999");
                reference.get().send(envelope.toString());
            }
        });
        reference.set(malformed);
        SFMSyntaxServerSupervisor supervisor = supervisor(defaultConfiguration(), new FakeFactory(malformed));

        assertInstanceOf(
                SFMSyntaxServerSupervisor.WorkerUnavailableException.class,
                failure(supervisor.submit(request(21, 1, "class InvalidSchema {}" )).result())
        );
        assertEquals(1, supervisor.telemetry().protocolFailures());
        assertTrue(malformed.terminated.get());
        assertEquals(0, supervisor.telemetry().completed());
    }

    @Test
    void jsonExpansionBeyondNegotiatedFrameFailsOnlyThatRequest() throws Exception {
        int sixteenMiB = SFMSyntaxServerFrameCodec.DEFAULT_MAXIMUM_FRAME_BYTES;
        FakeSession session = respondingSession(8, true, sixteenMiB);
        SFMSyntaxServerSupervisor.Configuration configuration = new SFMSyntaxServerSupervisor.Configuration(
                "fake-sfm.exe",
                Duration.ofMillis(500),
                Duration.ofSeconds(2),
                Duration.ofMillis(100),
                sixteenMiB,
                8
        );
        SFMSyntaxServerSupervisor supervisor = supervisor(configuration, new FakeFactory(session));
        String sourceUnderFourMiB = "\u0001".repeat(3_000_000);
        SFMSyntaxHighlightRequest expandedRequest = request(41, 1, sourceUnderFourMiB);
        assertTrue(expandedRequest.sourceBytes() < SFMSyntaxHighlightLimits.DEFAULT_MAXIMUM_SOURCE_BYTES);
        assertTrue(SFMSyntaxServerFrameCodec.encodedPayloadBytes(
                SFMSyntaxServerProtocol.highlight(expandedRequest)
        ) > sixteenMiB);

        SFMSyntaxServerSupervisor.Submission oversized =
                supervisor.submit(expandedRequest);

        assertInstanceOf(
                SFMSyntaxServerSupervisor.CapacityException.class,
                failureWithin(oversized.result())
        );
        assertEquals(0, session.framesOfKind("highlight").size());
        assertEquals(SFMSyntaxServerSupervisor.Lifecycle.READY, supervisor.lifecycle());

        SFMSyntaxHighlightResult following = supervisor.submit(request(42, 1, "class Small {}"))
                .result()
                .get(2, TimeUnit.SECONDS);
        assertEquals(42, following.requestId());
        assertEquals(1, session.framesOfKind("highlight").size());
        assertTrue(supervisor.telemetry().processAlive());
    }

    @Test
    void decodedFramesApplyBackpressureInsteadOfFillingTheStateExecutorQueue() throws Exception {
        FakeSession session = respondingSession(8, false);
        ThreadPoolExecutor state = new ThreadPoolExecutor(
                1,
                1,
                0,
                TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(),
                runnable -> daemon(runnable, "test-bounded-state")
        );
        ExecutorService reader = Executors.newSingleThreadExecutor(
                runnable -> daemon(runnable, "test-bounded-reader")
        );
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(
                runnable -> daemon(runnable, "test-bounded-timer")
        );
        SFMSyntaxServerSupervisor supervisor = new SFMSyntaxServerSupervisor(
                defaultConfiguration(),
                new FakeFactory(session),
                state,
                reader,
                scheduler,
                true
        );
        supervisors.add(supervisor);
        supervisor.start(Duration.ofSeconds(1)).get(2, TimeUnit.SECONDS);

        CountDownLatch blockerEntered = new CountDownLatch(1);
        CountDownLatch releaseBlocker = new CountDownLatch(1);
        state.execute(() -> {
            blockerEntered.countDown();
            try {
                releaseBlocker.await();
            } catch (InterruptedException failure) {
                Thread.currentThread().interrupt();
            }
        });
        assertTrue(blockerEntered.await(1, TimeUnit.SECONDS));
        try {
            for (int nonce = 0; nonce < 100; nonce++) session.send(pongEnvelope(nonce));
            await(() -> state.getQueue().size() == 1);
            Thread.sleep(25);
            assertEquals(1, state.getQueue().size());
        } finally {
            releaseBlocker.countDown();
        }
    }

    @Test
    void handshakeTimeoutReapsTheSilentProcess() throws Exception {
        FakeSession silent = new FakeSession(frame -> { });
        SFMSyntaxServerSupervisor supervisor = supervisor(configuration(
                Duration.ofMillis(25),
                Duration.ofSeconds(1),
                8
        ), new FakeFactory(silent));

        assertInstanceOf(
                SFMSyntaxServerSupervisor.WorkerUnavailableException.class,
                failureWithin(supervisor.start(Duration.ofSeconds(1)))
        );
        assertEquals(1, supervisor.telemetry().transportFailures());
        assertTrue(silent.terminated.get());
    }

    @Test
    void acknowledgedShutdownReapsTheProcessTreeAndClosesTheProvider() throws Exception {
        FakeSession session = respondingSession(8, true);
        SFMSyntaxServerSupervisor supervisor = supervisor(defaultConfiguration(), new FakeFactory(session));
        SFMSyntaxServerHighlightProvider provider = new SFMSyntaxServerHighlightProvider(supervisor);
        provider.start(Duration.ofSeconds(1)).get(2, TimeUnit.SECONDS);

        provider.close();
        provider.termination().get(2, TimeUnit.SECONDS);

        assertEquals(1, session.framesOfKind("shutdown").size());
        assertTrue(session.terminated.get());
        assertTrue(session.descendantsReaped.get());
        assertFalse(session.alive());
        assertFalse(provider.available());
        assertEquals(SFMSyntaxServerSupervisor.Lifecycle.CLOSED, supervisor.lifecycle());
    }

    @Test
    void telemetryNeverRetainsSourceOriginLanguageOrHash() throws Exception {
        String source = "class DO_NOT_RETAIN_THIS_SOURCE_7f56e9 {}";
        SFMSyntaxHighlightRequest request = new SFMSyntaxHighlightRequest(
                SFMSyntaxHighlightRequest.SCHEMA,
                31,
                2,
                "private-origin-marker-29d2",
                5,
                "java",
                source,
                SFMSyntaxHighlightRequest.sha256(source),
                100
        );
        FakeSession session = respondingSession(8, true);
        SFMSyntaxServerSupervisor supervisor = supervisor(defaultConfiguration(), new FakeFactory(session));

        supervisor.submit(request).result().get(2, TimeUnit.SECONDS);
        String evidence = supervisor.telemetry().toString();

        assertFalse(evidence.contains(source));
        assertFalse(evidence.contains(request.originId()));
        assertFalse(evidence.contains(request.language()));
        assertFalse(evidence.contains(request.sourceSha256()));
    }

    private SFMSyntaxServerSupervisor supervisor(
            SFMSyntaxServerSupervisor.Configuration configuration,
            SFMSyntaxServerSupervisor.SessionFactory factory
    ) {
        ExecutorService state = Executors.newSingleThreadExecutor(runnable -> daemon(
                runnable,
                "test-syntax-state"
        ));
        ExecutorService reader = Executors.newCachedThreadPool(runnable -> daemon(
                runnable,
                "test-syntax-reader"
        ));
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> daemon(
                runnable,
                "test-syntax-timer"
        ));
        SFMSyntaxServerSupervisor supervisor = new SFMSyntaxServerSupervisor(
                configuration,
                factory,
                state,
                reader,
                scheduler,
                true
        );
        supervisors.add(supervisor);
        return supervisor;
    }

    private static SFMSyntaxServerSupervisor.Configuration defaultConfiguration() {
        return configuration(Duration.ofMillis(500), Duration.ofSeconds(1), 8);
    }

    private static SFMSyntaxServerSupervisor.Configuration configuration(
            Duration handshakeTimeout,
            Duration requestTimeout,
            int maximumPending
    ) {
        return new SFMSyntaxServerSupervisor.Configuration(
                "fake-sfm.exe",
                handshakeTimeout,
                requestTimeout,
                Duration.ofMillis(100),
                1024 * 1024,
                maximumPending
        );
    }

    private static FakeSession respondingSession(int maximumPending, boolean answerHighlights) throws IOException {
        return respondingSession(maximumPending, answerHighlights, 1024 * 1024);
    }

    private static FakeSession respondingSession(
            int maximumPending,
            boolean answerHighlights,
            int maximumFrameBytes
    ) throws IOException {
        AtomicReference<FakeSession> reference = new AtomicReference<>();
        FakeSession session = new FakeSession(frame -> {
            switch (kind(frame)) {
                case "hello" -> reference.get().send(helloEnvelope(maximumPending, maximumFrameBytes));
                case "highlight" -> {
                    if (answerHighlights) {
                        SFMSyntaxHighlightRequest request = SFMSyntaxHighlightJsonCodec.decodeRequest(
                                frame.getAsJsonObject("request").toString()
                        );
                        reference.get().send(resultEnvelope(result(request)));
                    }
                }
                case "cancel" -> reference.get().send(cancelledEnvelope(frame));
                case "shutdown" -> reference.get().send(shutdownEnvelope());
                default -> { }
            }
        });
        reference.set(session);
        return session;
    }

    private static SFMSyntaxHighlightRequest request(long id, long generation, String source) {
        return SFMSyntaxHighlightRequest.create(
                id,
                generation,
                "editor:test",
                generation,
                "java",
                source,
                1000
        );
    }

    private static SFMSyntaxHighlightResult result(SFMSyntaxHighlightRequest request) {
        return result(request, SFMSyntaxHighlightResult.Outcome.HIGHLIGHTED);
    }

    private static SFMSyntaxHighlightResult result(
            SFMSyntaxHighlightRequest request,
            SFMSyntaxHighlightResult.Outcome outcome
    ) {
        return new SFMSyntaxHighlightResult(
                SFMSyntaxHighlightResult.SCHEMA,
                request.requestId(),
                request.requestGeneration(),
                request.originId(),
                request.originGeneration(),
                request.language(),
                request.sourceSha256(),
                request.sourceBytes(),
                outcome,
                outcome.isComplete(),
                SFMSyntaxHighlightResult.PARSER_FINGERPRINT,
                SFMSyntaxHighlightResult.FORMATTING_SCHEMA,
                10,
                SFMSyntaxHighlightResult.CacheEvidence.bypassed(),
                List.of(),
                List.of()
        );
    }

    private static String helloEnvelope(int maximumPending) {
        return helloEnvelope(maximumPending, 1024 * 1024);
    }

    private static String helloEnvelope(int maximumPending, int maximumFrameBytes) {
        JsonObject envelope = base("hello", SFMSyntaxServerProtocol.HELLO_SCHEMA);
        JsonObject hello = new JsonObject();
        hello.addProperty("protocol_schema", SFMSyntaxServerProtocol.PROTOCOL_SCHEMA);
        hello.addProperty("server_name", "sfm-propagate-changes");
        hello.addProperty("server_version", "test");
        JsonArray capabilities = new JsonArray();
        capabilities.add("highlight");
        capabilities.add("cancellation");
        capabilities.add("ping");
        capabilities.add("shutdown");
        hello.add("capabilities", capabilities);
        hello.addProperty("max_frame_bytes", maximumFrameBytes);
        hello.addProperty("max_pending_requests", maximumPending);
        JsonArray languages = new JsonArray();
        languages.add("java");
        hello.add("supported_languages", languages);
        hello.addProperty("request_schema", SFMSyntaxHighlightRequest.SCHEMA);
        hello.addProperty("result_schema", SFMSyntaxHighlightResult.SCHEMA);
        envelope.add("hello", hello);
        return envelope.toString();
    }

    private static String resultEnvelope(SFMSyntaxHighlightResult result) {
        JsonObject envelope = base("highlight-result", SFMSyntaxServerProtocol.HIGHLIGHT_SCHEMA);
        envelope.add("result", JsonParser.parseString(SFMSyntaxHighlightJsonCodec.encodeResult(result)));
        return envelope.toString();
    }

    private static String cancelledEnvelope(JsonObject cancellation) {
        JsonObject envelope = base("cancelled", SFMSyntaxServerProtocol.CANCEL_SCHEMA);
        JsonObject value = new JsonObject();
        value.addProperty("request_id", cancellation.get("request_id").getAsLong());
        value.addProperty("request_generation", cancellation.get("request_generation").getAsLong());
        value.addProperty("origin_id", cancellation.get("origin_id").getAsString());
        value.addProperty("origin_generation", cancellation.get("origin_generation").getAsLong());
        value.addProperty("status", "cancellation-requested");
        envelope.add("cancellation", value);
        return envelope.toString();
    }

    private static String shutdownEnvelope() {
        return base("shutdown", SFMSyntaxServerProtocol.SHUTDOWN_SCHEMA).toString();
    }

    private static String pongEnvelope(long nonce) {
        JsonObject value = base("pong", SFMSyntaxServerProtocol.PING_SCHEMA);
        value.addProperty("nonce", nonce);
        return value.toString();
    }

    private static JsonObject base(String kind, String schema) {
        JsonObject value = new JsonObject();
        value.addProperty("kind", kind);
        value.addProperty("schema", schema);
        return value;
    }

    private static String kind(JsonObject frame) {
        return frame.get("kind").getAsString();
    }

    private static Throwable failure(CompletableFuture<?> future) {
        try {
            future.join();
            throw new AssertionError("Expected future to fail");
        } catch (CompletionException failure) {
            return failure.getCause();
        } catch (java.util.concurrent.CancellationException failure) {
            return failure;
        }
    }

    private static Throwable failureWithin(CompletableFuture<?> future) throws Exception {
        try {
            future.get(2, TimeUnit.SECONDS);
            throw new AssertionError("Expected future to fail");
        } catch (java.util.concurrent.ExecutionException failure) {
            return failure.getCause();
        } catch (java.util.concurrent.CancellationException failure) {
            return failure;
        }
    }

    private static Thread daemon(Runnable runnable, String name) {
        Thread thread = new Thread(runnable, name);
        thread.setDaemon(true);
        return thread;
    }

    private static void await(Check check) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (!check.ready()) {
            if (System.nanoTime() >= deadline) throw new TimeoutException("Condition did not become true");
            Thread.sleep(2);
        }
    }

    @FunctionalInterface
    private interface Check {
        boolean ready() throws Exception;
    }

    private static final class FakeFactory implements SFMSyntaxServerSupervisor.SessionFactory {
        private final Deque<FakeSession> sessions = new ArrayDeque<>();
        private final AtomicInteger starts = new AtomicInteger();

        private FakeFactory(FakeSession... sessions) {
            this.sessions.addAll(List.of(sessions));
        }

        @Override
        public synchronized SFMSyntaxServerSupervisor.WorkerSession start(
                SFMSyntaxServerSupervisor.Configuration configuration
        ) throws IOException {
            starts.incrementAndGet();
            FakeSession session = sessions.pollFirst();
            if (session == null) throw new IOException("No scripted syntax session remains");
            return session;
        }
    }

    private static final class FakeSession implements SFMSyntaxServerSupervisor.WorkerSession {
        private static final AtomicInteger PIDS = new AtomicInteger(1000);
        private final long pid = PIDS.incrementAndGet();
        private final PipedInputStream stdout = new PipedInputStream(1024 * 1024);
        private final PipedOutputStream serverOutput;
        private final ClientFrameOutput stdin;
        private final List<JsonObject> clientFrames = new ArrayList<>();
        private final AtomicBoolean alive = new AtomicBoolean(true);
        private final AtomicBoolean terminated = new AtomicBoolean();
        private final AtomicBoolean descendantsReaped = new AtomicBoolean();

        private FakeSession(Consumer<JsonObject> handler) throws IOException {
            serverOutput = new PipedOutputStream(stdout);
            stdin = new ClientFrameOutput(frame -> {
                JsonObject json = JsonParser.parseString(frame).getAsJsonObject();
                synchronized (clientFrames) {
                    clientFrames.add(json);
                }
                handler.accept(json);
            });
        }

        synchronized void send(String json) {
            if (!alive.get()) return;
            try {
                SFMSyntaxServerFrameCodec.write(
                        serverOutput,
                        json,
                        SFMSyntaxServerFrameCodec.DEFAULT_MAXIMUM_FRAME_BYTES
                );
            } catch (IOException failure) {
                throw new IllegalStateException(failure);
            }
        }

        synchronized void crash() {
            if (!alive.compareAndSet(true, false)) return;
            try {
                serverOutput.close();
            } catch (IOException ignored) {
            }
        }

        List<JsonObject> framesOfKind(String kind) {
            synchronized (clientFrames) {
                return clientFrames.stream()
                        .filter(frame -> kind(frame).equals(kind))
                        .map(JsonObject::deepCopy)
                        .toList();
            }
        }

        @Override
        public long pid() {
            return pid;
        }

        @Override
        public InputStream stdout() {
            return stdout;
        }

        @Override
        public OutputStream stdin() {
            return stdin;
        }

        @Override
        public boolean alive() {
            return alive.get();
        }

        @Override
        public synchronized void terminateTree(Duration grace) {
            terminated.set(true);
            descendantsReaped.set(true);
            alive.set(false);
            stdin.close();
            try {
                serverOutput.close();
            } catch (IOException ignored) {
            }
            try {
                stdout.close();
            } catch (IOException ignored) {
            }
        }
    }

    private static final class ClientFrameOutput extends OutputStream {
        private final ByteArrayOutputStream buffered = new ByteArrayOutputStream();
        private final Consumer<String> receiver;
        private boolean closed;

        private ClientFrameOutput(Consumer<String> receiver) {
            this.receiver = receiver;
        }

        @Override
        public synchronized void write(int value) throws IOException {
            requireOpen();
            buffered.write(value);
            drain();
        }

        @Override
        public synchronized void write(byte[] values, int offset, int length) throws IOException {
            requireOpen();
            buffered.write(values, offset, length);
            drain();
        }

        @Override
        public synchronized void close() {
            closed = true;
        }

        private void requireOpen() throws IOException {
            if (closed) throw new IOException("fake syntax stdin is closed");
        }

        private void drain() throws IOException {
            while (true) {
                byte[] values = buffered.toByteArray();
                if (values.length < Integer.BYTES) return;
                int length = ByteBuffer.wrap(values, 0, Integer.BYTES)
                        .order(ByteOrder.LITTLE_ENDIAN)
                        .getInt();
                if (length <= 0) throw new IOException("invalid fake client frame length");
                int total = Integer.BYTES + length;
                if (values.length < total) return;
                String json = new String(values, Integer.BYTES, length, StandardCharsets.UTF_8);
                buffered.reset();
                buffered.write(values, total, values.length - total);
                receiver.accept(json);
            }
        }
    }
}
