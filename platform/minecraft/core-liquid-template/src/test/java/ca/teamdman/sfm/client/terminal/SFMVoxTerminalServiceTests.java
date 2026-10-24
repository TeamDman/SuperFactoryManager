package ca.teamdman.sfm.client.terminal;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4' %}

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.fail;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.facet.vox.ConnectionState;
import org.facet.vox.LaneOptions;
import org.facet.vox.ServiceLane;
import org.facet.vox.ServiceRegistry;
import org.facet.vox.VoxConnection;
import org.facet.vox.VoxResult;
import org.facet.vox.generated.TerminalClient;
import org.facet.vox.generated.TerminalDispatcher;
import org.facet.vox.generated.TerminalError;
import org.facet.vox.generated.TerminalErrorCode;
import org.facet.vox.generated.TerminalHandler;
import org.facet.vox.generated.TerminalInputResult;
import org.facet.vox.generated.TerminalOperationResult;
import org.facet.vox.generated.TerminalPasteDisposition;
import org.facet.vox.generated.TerminalPastePolicy;
import org.facet.vox.generated.TerminalPasteRequest;
import org.facet.vox.generated.TerminalPasteResult;
import org.facet.vox.generated.TerminalServiceDescriptor;
import org.facet.vox.generated.TerminalState;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
{% when '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1', '26.1.2' %}
{% endcase %}

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.List;

import org.facet.vox.ConnectionOptions;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4' %}
import org.facet.vox.generated.TerminalAlphaMode;
import org.facet.vox.generated.TerminalColorSpace;
import org.facet.vox.generated.TerminalFrameEncoding;
import org.facet.vox.generated.TerminalFrameOrigin;
import org.facet.vox.generated.TerminalPresentationMode;
import org.facet.vox.generated.TerminalRasterFrameKind;
import org.facet.vox.generated.TerminalRasterizationOwner;
import org.facet.vox.generated.TerminalTuningMode;
import org.facet.vox.generated.TerminalTuningRequest;
{% when '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1', '26.1.2' %}
{% endcase %}
import org.junit.jupiter.api.Test;

class SFMVoxTerminalServiceTests {
{% case minecraft_version %}
{% when '1.19.2', '1.19.4' %}
    @Test
    void startupInputRejectionKeepsTheSameSessionAndStreamUntilUserRetry() throws Exception {
        try (ReadinessFixture fixture = new ReadinessFixture()) {
            fixture.subscribe();
            Object stream = field(fixture.service, "frameReceiver");
            Object subscription = field(fixture.service, "subscriptionCall");
            long epoch = fixture.service.interactionEpoch();
            for (String message : List.of("arbitrary wording", "", "雪: not a readiness keyword")) {
                fixture.rejection = new TerminalError(TerminalErrorCode.INVALID_REQUEST, message, true, 73);
                int before = fixture.requests.size();
                assertTrue(fixture.service.sendText("one user attempt"));
                fixture.drain();
                assertEquals(before + 1, fixture.requests.size());
                assertEquals(0, fixture.forwarded.get());
                assertEquals(new SFMTerminalError(SFMTerminalErrorCode.INVALID_REQUEST, message, true, 73),
                        fixture.service.inputFailure().orElseThrow());
                assertTrue(fixture.service.failureMessage().orElseThrow().contains("retry manually"));
                assertTrue(fixture.service.connectionSnapshot().failure().isEmpty());
                assertTrue(fixture.service.connectionSnapshot().inputFailure().isPresent());
                assertTrue(fixture.service.connectionSnapshot().displayFailure().isPresent());
                assertTrue(fixture.service.isConnected());
                assertEquals(epoch, fixture.service.interactionEpoch());
                assertSame(stream, field(fixture.service, "frameReceiver"));
                assertSame(subscription, field(fixture.service, "subscriptionCall"));
                assertFalse(((CompletableFuture<?>) subscription).isDone());
            }
            fixture.rejection = null;
            assertTrue(fixture.service.sendText("explicit user retry"));
            fixture.drain();
            assertEquals(1, fixture.forwarded.get());
            assertTrue(fixture.service.inputFailure().isEmpty());
            assertTrue(fixture.service.failureMessage().isEmpty());
            assertTrue(fixture.service.connectionSnapshot().displayFailure().isEmpty());
            assertEquals(epoch, fixture.service.interactionEpoch());
            assertSame(stream, field(fixture.service, "frameReceiver"));
        }
    }

    @Test
    void keyMouseCoalescedMotionCancelAndExecuteUseTheTypedInputPolicy() throws Exception {
        try (ReadinessFixture fixture = new ReadinessFixture()) {
            fixture.rejection = new TerminalError(TerminalErrorCode.INVALID_REQUEST, "opaque", true, 4);
            assertTrue(fixture.service.sendKey(65, 0, true, false));
            fixture.drain();
            assertTrue(fixture.service.inputFailure().isPresent());
            assertTrue(fixture.service.sendMouse(2, 2, 0, 0, true, false, 0, 0));
            fixture.drain();
            assertTrue(fixture.service.isConnected());
            assertTrue(fixture.service.sendMouse(3, 3, 0, 0, false, true, 0, 0));
            fixture.drain();
            assertTrue(fixture.service.cancel());
            fixture.drain();
            assertFalse(fixture.service.openSession().execute("dangerous command").success());
            assertEquals(List.of("sendKey", "sendMouse", "sendMouse", "cancel", "sendText"), fixture.requests);
            assertEquals(0, fixture.forwarded.get());
            assertTrue(fixture.service.isConnected());
            fixture.rejection = null;
            SFMTerminalResponse result = fixture.service.openSession().execute("user retry");
            assertTrue(result.success());
            assertTrue(result.lines().get(0).contains("execution not acknowledged"));
            assertTrue(fixture.service.inputFailure().isEmpty());
            assertEquals(1, fixture.forwarded.get());
        }
    }

    @Test
    void ordinaryInvalidDisconnectedAndOtherRetryableErrorsStillRetireTheTransport() throws Exception {
        for (TerminalError error : List.of(
                new TerminalError(TerminalErrorCode.INVALID_REQUEST, "same words", false, 9),
                new TerminalError(TerminalErrorCode.DISCONNECTED, "same words", false, 10),
                new TerminalError(TerminalErrorCode.INTERNAL, "same words", true, 11))) {
            try (ReadinessFixture fixture = new ReadinessFixture()) {
                fixture.rejection = error;
                long epoch = fixture.service.interactionEpoch();
                assertTrue(fixture.service.sendText("do not replay"));
                fixture.drain();
                assertFalse(fixture.service.isConnected());
                assertTrue(fixture.service.interactionEpoch() > epoch);
                assertTrue(fixture.service.inputFailure().isEmpty());
                assertTrue(fixture.service.failureMessage().isPresent());
                assertEquals(List.of("sendText"), fixture.requests);
            }
        }
    }

    @Test
    void retryableInvalidOnANonInputOperationDoesNotBecomeStartupReadiness() throws Exception {
        try (ReadinessFixture fixture = new ReadinessFixture()) {
            fixture.rejection = new TerminalError(TerminalErrorCode.INVALID_REQUEST, "arbitrary", true, 19);
            assertTrue(fixture.service.copySelection(ignored -> fail("copy failed")));
            fixture.drain();
            assertFalse(fixture.service.isConnected());
            assertTrue(fixture.service.inputFailure().isEmpty());
        }
    }

    @Test
    void staleSuccessCannotClearANewerSessionsInputFailure() throws Exception {
        try (ReadinessFixture fixture = new ReadinessFixture()) {
            CompletableFuture<VoxResult<TerminalInputResult, TerminalError>> reply = new CompletableFuture<>();
            fixture.nextInput.set(reply);
            fixture.service.sendText("old session");
            fixture.awaitRequests(1);
            TerminalError newer = new TerminalError(TerminalErrorCode.INVALID_REQUEST, "new session", true, 88);
            synchronized (field(fixture.service, "lock")) {
                setField(fixture.service, "transportGeneration", fixture.service.interactionEpoch() + 1);
                setField(fixture.service, "sessionId", "new-session");
                setField(fixture.service, "inputFailure", newer);
            }
            reply.complete(VoxResult.success(new TerminalInputResult("fixture-session", 1, 1)));
            fixture.drain();
            assertEquals("new session", fixture.service.inputFailure().orElseThrow().message());
            assertTrue(fixture.service.isConnected());
        }
    }

    @Test
    void staleFailureCannotCloseOrPoisonANewerSession() throws Exception {
        try (ReadinessFixture fixture = new ReadinessFixture()) {
            CompletableFuture<VoxResult<TerminalInputResult, TerminalError>> reply = new CompletableFuture<>();
            fixture.nextInput.set(reply);
            fixture.service.sendText("old session");
            fixture.awaitRequests(1);
            synchronized (field(fixture.service, "lock")) {
                setField(fixture.service, "transportGeneration", fixture.service.interactionEpoch() + 1);
                setField(fixture.service, "sessionId", "new-session");
            }
            reply.complete(VoxResult.applicationError(new TerminalError(
                    TerminalErrorCode.DISCONNECTED, "old failure", false, 99)));
            fixture.drain();
            assertTrue(fixture.service.isConnected());
            assertTrue(fixture.service.failureMessage().isEmpty());
        }
    }

    @Test
    void pasteRejectionIsTypedCompletesOnceAndAllowsAnExplicitRetry() throws Exception {
        try (ReadinessFixture fixture = new ReadinessFixture()) {
            fixture.rejection = new TerminalError(TerminalErrorCode.INVALID_REQUEST, "not a magic string", true, 67);
            BlockingQueue<SFMTerminalPasteResult> results = new LinkedBlockingQueue<>();
            long epoch = fixture.service.interactionEpoch();
            assertTrue(fixture.service.pasteWithGuard("single", results::add));
            SFMTerminalPasteResult failure = results.poll(3, TimeUnit.SECONDS);
            assertNotNull(failure);
            assertEquals(SFMTerminalPasteResult.Disposition.FAILED, failure.disposition());
            assertEquals(new SFMTerminalError(SFMTerminalErrorCode.INVALID_REQUEST, "not a magic string", true, 67),
                    failure.error().orElseThrow());
            assertEquals("", failure.contentId());
            fixture.drain();
            assertTrue(fixture.service.isConnected());
            assertEquals(epoch, fixture.service.interactionEpoch());
            assertEquals(0, fixture.forwarded.get());
            fixture.rejection = null;
            assertTrue(fixture.service.pasteWithGuard("single", results::add));
            assertEquals(SFMTerminalPasteResult.Disposition.PASTED,
                    results.poll(3, TimeUnit.SECONDS).disposition());
            fixture.drain();
            assertTrue(fixture.service.inputFailure().isEmpty());
            assertEquals(1, fixture.forwarded.get());
            assertNull(results.poll(100, TimeUnit.MILLISECONDS));
        }
    }

    @Test
    void pasteTimeoutHasAmbiguousAcceptanceAndIsNeverReplayed() throws Exception {
        try (ReadinessFixture fixture = new ReadinessFixture(Duration.ofMillis(150))) {
            CompletableFuture<VoxResult<TerminalPasteResult, TerminalError>> late = new CompletableFuture<>();
            fixture.nextPaste.set(late);
            BlockingQueue<SFMTerminalPasteResult> results = new LinkedBlockingQueue<>();
            assertTrue(fixture.service.pasteWithGuard("uncertain", results::add));
            SFMTerminalPasteResult result = results.poll(3, TimeUnit.SECONDS);
            assertNotNull(result);
            assertEquals(SFMTerminalErrorCode.DISCONNECTED, result.error().orElseThrow().code());
            assertFalse(result.error().orElseThrow().retryable());
            fixture.drain();
            assertFalse(fixture.service.isConnected());
            late.complete(VoxResult.success(pasted("fixture-session")));
            assertEquals(List.of("paste"), fixture.requests);
            assertNull(results.poll(100, TimeUnit.MILLISECONDS));
        }
    }

    @Test
    void closeCompletesInFlightAndQueuedPastesExactlyOnceWithoutForwardingQueuedWork() throws Exception {
        try (ReadinessFixture fixture = new ReadinessFixture()) {
            CompletableFuture<VoxResult<TerminalPasteResult, TerminalError>> late = new CompletableFuture<>();
            fixture.nextPaste.set(late);
            BlockingQueue<SFMTerminalPasteResult> results = new LinkedBlockingQueue<>();
            assertTrue(fixture.service.pasteWithGuard("in flight", results::add));
            fixture.awaitRequests(1);
            assertTrue(fixture.service.pasteWithGuard("still queued", results::add));
            fixture.service.close();
            for (int i = 0; i < 2; i++) {
                SFMTerminalPasteResult result = results.poll(3, TimeUnit.SECONDS);
                assertNotNull(result);
                assertEquals(SFMTerminalErrorCode.CANCELLED, result.error().orElseThrow().code());
                assertFalse(result.error().orElseThrow().retryable());
            }
            late.complete(VoxResult.success(pasted("fixture-session")));
            assertFalse(fixture.service.pasteWithGuard("closed", results::add));
            assertEquals(List.of("paste"), fixture.requests);
            assertNull(results.poll(100, TimeUnit.MILLISECONDS));
        }
    }

    @Test
    void reconnectCancelsAcceptedPastesAndStaleSuccessCannotOpenConfirmation() throws Exception {
        try (ReadinessFixture fixture = new ReadinessFixture()) {
            CompletableFuture<VoxResult<TerminalPasteResult, TerminalError>> late = new CompletableFuture<>();
            fixture.nextPaste.set(late);
            BlockingQueue<SFMTerminalPasteResult> results = new LinkedBlockingQueue<>();
            assertTrue(fixture.service.pasteWithGuard("in flight", results::add));
            fixture.awaitRequests(1);
            fixture.service.reconnect();
            assertEquals(SFMTerminalErrorCode.CANCELLED,
                    results.poll(3, TimeUnit.SECONDS).error().orElseThrow().code());
            late.complete(VoxResult.success(new TerminalPasteResult("fixture-session",
                    TerminalPasteDisposition.CONFIRMATION_REQUIRED, 1, 1, "preview", "stale-id")));
            fixture.drain();
            assertFalse(fixture.service.isConnected());
            assertNull(results.poll(100, TimeUnit.MILLISECONDS));
        }
    }

    @Test
    void guardedAndApprovedPasteRetainTheirNormalDistinctResults() throws Exception {
        try (ReadinessFixture fixture = new ReadinessFixture()) {
            BlockingQueue<SFMTerminalPasteResult> results = new LinkedBlockingQueue<>();
            assertTrue(fixture.service.pasteWithGuard("one\ntwo", results::add));
            SFMTerminalPasteResult guard = results.poll(3, TimeUnit.SECONDS);
            assertEquals(SFMTerminalPasteResult.Disposition.CONFIRMATION_REQUIRED, guard.disposition());
            assertEquals(0, fixture.forwarded.get());
            assertFalse(fixture.service.pasteWithoutGuard("one\ntwo", "", results::add));
            assertTrue(fixture.service.pasteWithoutGuard("one\ntwo", guard.contentId(), results::add));
            assertEquals(SFMTerminalPasteResult.Disposition.PASTED,
                    results.poll(3, TimeUnit.SECONDS).disposition());
            assertEquals(1, fixture.forwarded.get());
            assertNull(results.poll(100, TimeUnit.MILLISECONDS));
        }
    }

    @Test
    void pasteFailureCannotContainConfirmationDataOrLoseItsError() {
        SFMTerminalError error = new SFMTerminalError(SFMTerminalErrorCode.CANCELLED, "closed", false, 0);
        assertThrows(IllegalArgumentException.class, () -> new SFMTerminalPasteResult(
                SFMTerminalPasteResult.Disposition.FAILED, "", ""));
        assertThrows(IllegalArgumentException.class, () -> new SFMTerminalPasteResult(
                SFMTerminalPasteResult.Disposition.FAILED, "preview", "id", Optional.of(error)));
        assertThrows(IllegalArgumentException.class, () -> new SFMTerminalPasteResult(
                SFMTerminalPasteResult.Disposition.PASTED, "", "", Optional.of(error)));
    }

    private static TerminalPasteResult pasted(String session) {
        return new TerminalPasteResult(session, TerminalPasteDisposition.PASTED, 1, 1, "", "");
    }

    private static Object field(Object instance, String name) throws Exception {
        java.lang.reflect.Field field = instance.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(instance);
    }

    private static void setField(Object instance, String name, Object value) throws Exception {
        java.lang.reflect.Field field = instance.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(instance, value);
    }

    /** Real pinned Vox client/dispatcher over loopback; no Rust Terminal or Minecraft launch. */
    private static final class ReadinessFixture implements AutoCloseable {
        private final SFMVoxTerminalService service;
        private final VoxConnection peer;
        private final VoxConnection connection;
        private final List<String> requests = new CopyOnWriteArrayList<>();
        private final AtomicInteger forwarded = new AtomicInteger();
        private final AtomicReference<CompletableFuture<VoxResult<TerminalInputResult, TerminalError>>> nextInput = new AtomicReference<>();
        private final AtomicReference<CompletableFuture<VoxResult<TerminalPasteResult, TerminalError>>> nextPaste = new AtomicReference<>();
        private volatile TerminalError rejection;

        private ReadinessFixture() throws Exception {
            this(Duration.ofSeconds(2));
        }

        private ReadinessFixture(Duration timeout) throws Exception {
            TerminalHandler handler = (TerminalHandler) java.lang.reflect.Proxy.newProxyInstance(
                    TerminalHandler.class.getClassLoader(), new Class<?>[]{TerminalHandler.class},
                    (proxy, method, args) -> respond(method.getName(), args));
            try (ServerSocket listener = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
                InetSocketAddress endpoint = new InetSocketAddress(InetAddress.getLoopbackAddress(), listener.getLocalPort());
                connection = VoxConnection.connect(endpoint, options());
                peer = VoxConnection.accept(listener.accept(),
                        new ServiceRegistry().register(new TerminalDispatcher(handler)), options());
                connection.start(ReadinessFixture::startDaemon);
                peer.start(ReadinessFixture::startDaemon);
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
                while (connection.state() != ConnectionState.OPEN && System.nanoTime() < deadline) Thread.sleep(2);
                assertEquals(ConnectionState.OPEN, connection.state());
                ServiceLane lane = connection.openLane(TerminalServiceDescriptor.INSTANCE, LaneOptions.defaults());
                lane.opened().get(3, TimeUnit.SECONDS);
                service = new SFMVoxTerminalService(endpoint, options(), timeout);
                // Attach an established lane to isolate input policy from presentation negotiation.
                setField(service, "connection", connection);
                setField(service, "lane", lane);
                setField(service, "client", new TerminalClient(lane));
                setField(service, "sessionId", "fixture-session");
            }
        }

        private Object respond(String method, Object[] args) {
            if (method.equals("subscribeFrames")) return new CompletableFuture<>();
            if (method.equals("disconnect")) return CompletableFuture.completedFuture(VoxResult.success(
                    new TerminalOperationResult("fixture-session", TerminalState.CLOSED, 1)));
            requests.add(method);
            if (method.equals("paste")) {
                CompletableFuture<VoxResult<TerminalPasteResult, TerminalError>> deferred = nextPaste.getAndSet(null);
                if (deferred != null) return deferred;
            } else {
                CompletableFuture<VoxResult<TerminalInputResult, TerminalError>> deferred = nextInput.getAndSet(null);
                if (deferred != null) return deferred;
            }
            if (rejection != null) return CompletableFuture.completedFuture(VoxResult.applicationError(rejection));
            if (method.equals("paste")) {
                TerminalPasteRequest request = (TerminalPasteRequest) args[1];
                if (request.policy() == TerminalPastePolicy.GUARD_MULTILINE && request.suppliedText().contains("\n")) {
                    return CompletableFuture.completedFuture(VoxResult.success(new TerminalPasteResult(
                            request.sessionId(), TerminalPasteDisposition.CONFIRMATION_REQUIRED,
                            1, 1, request.suppliedText(), "guard-id")));
                }
                forwarded.incrementAndGet();
                return CompletableFuture.completedFuture(VoxResult.success(pasted(request.sessionId())));
            }
            forwarded.incrementAndGet();
            if (method.equals("cancel")) return CompletableFuture.completedFuture(VoxResult.success(
                    new TerminalOperationResult("fixture-session", TerminalState.READY, 1)));
            return CompletableFuture.completedFuture(VoxResult.success(new TerminalInputResult("fixture-session", 1, 1)));
        }

        private void subscribe() throws Exception {
            java.lang.reflect.Method method = SFMVoxTerminalService.class.getDeclaredMethod(
                    "startFrameSubscription", TerminalClient.class, String.class);
            method.setAccessible(true);
            method.invoke(service, field(service, "client"), "fixture-session");
        }

        private void drain() throws Exception {
            ((ExecutorService) field(service, "driver")).submit(() -> {}).get(4, TimeUnit.SECONDS);
        }

        private void awaitRequests(int count) throws Exception {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
            while (requests.size() < count && System.nanoTime() < deadline) Thread.sleep(2);
            assertEquals(count, requests.size());
        }

        private static ConnectionOptions options() {
            return ConnectionOptions.builder().handshakeTimeout(Duration.ofSeconds(2))
                    .idleTimeout(Duration.ofSeconds(10)).closeTimeout(Duration.ofMillis(200)).build();
        }

        private static void startDaemon(Runnable action) {
            Thread thread = new Thread(action, "sfm-terminal-readiness-test");
            thread.setDaemon(true);
            thread.start();
        }

        @Override
        public void close() {
            service.close();
            connection.close();
            peer.close();
        }
    }

    @Test
    void rasterEvidenceAllowsOnlySupersededReceiverFailuresDuringExpectedReconnect() {
        assertTrue(SFMVoxTerminalService.rasterDeliveryCountsValid(false, 1, 0, 0));
        assertFalse(SFMVoxTerminalService.rasterDeliveryCountsValid(false, 1, 0, 1));
        assertFalse(SFMVoxTerminalService.rasterDeliveryCountsValid(true, 1, 0, 0));
        assertTrue(SFMVoxTerminalService.rasterDeliveryCountsValid(true, 2, 0, 0));
        assertTrue(SFMVoxTerminalService.rasterDeliveryCountsValid(true, 2, 0, 1));
        assertFalse(SFMVoxTerminalService.rasterDeliveryCountsValid(true, 2, 0, 2));
        assertFalse(SFMVoxTerminalService.rasterDeliveryCountsValid(true, 2, 1, 0));
    }

    @Test
    void typedTuningPreservesAutomaticAxesWithEffectiveCompatibilityValues() {
        TerminalTuningRequest request = SFMVoxTerminalService.requestedTuningRequest(
                "rust-gpu-slug",
                SFMTerminalTuningSettings.automatic(),
                120,
                40,
                1600,
                900,
                0
        );

        assertEquals("rust-gpu-slug", request.rendererId());
        assertEquals(TerminalTuningMode.AUTO, request.surfaceMode());
        assertEquals(1600, request.surfaceWidth());
        assertEquals(900, request.surfaceHeight());
        assertEquals(TerminalTuningMode.AUTO, request.fontMode());
        assertEquals(0, request.fontPixelSize());
        assertEquals(TerminalTuningMode.AUTO, request.cellsMode());
        assertEquals(120, request.columns());
        assertEquals(40, request.rows());
    }

    @Test
    void typedTuningMarksPartiallyOverriddenAxesManual() {
        TerminalTuningRequest request = SFMVoxTerminalService.requestedTuningRequest(
                "rust-cpu-fontdue",
                new SFMTerminalTuningSettings(1664, 0, 24, 124, 0),
                124,
                40,
                1664,
                900,
                24
        );

        assertEquals(TerminalTuningMode.MANUAL, request.surfaceMode());
        assertEquals(TerminalTuningMode.MANUAL, request.fontMode());
        assertEquals(TerminalTuningMode.MANUAL, request.cellsMode());
        assertEquals(1664, request.surfaceWidth());
        assertEquals(900, request.surfaceHeight());
        assertEquals(124, request.columns());
        assertEquals(40, request.rows());
    }

{% when '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1', '26.1.2' %}
{% endcase %}
    @Test
    void acceptsOnlyPayloadsWithThePngSignature() {
        assertTrue(SFMVoxTerminalService.isPng(new byte[]{
                (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
        }));
        assertFalse(SFMVoxTerminalService.isPng(new byte[]{0x50, 0x4E, 0x47}));
        assertFalse(SFMVoxTerminalService.isPng(null));
    }

    @Test
{% case minecraft_version %}
{% when '1.19.2', '1.19.4' %}
    void unavailableEndpointStaysRustDisconnectedInsteadOfFallingBack() {
{% when '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1', '26.1.2' %}
    void unavailableEndpointFallsBackToJavaLocalBackend() {
{% endcase %}
        try (SFMVoxTerminalService vox = unavailableService(new SFMJavaLocalTerminalService())) {
            SFMTerminalService.SFMTerminalSession session = vox.openSession();

            SFMTerminalResponse response = session.execute("pwd");

{% case minecraft_version %}
{% when '1.19.2', '1.19.4' %}
            assertFalse(response.success());
            assertTrue(response.lines().get(0).startsWith("Rust terminal unavailable:"));
{% when '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1', '26.1.2' %}
            assertTrue(response.success());
            assertTrue(response.lines().get(0).equals("Vox unavailable; Java-local fallback active"));
            assertTrue(response.lines().stream().anyMatch(line -> line.equals("/")));
{% endcase %}
            assertTrue(vox.latestSnapshot().isEmpty());
{% case minecraft_version %}
{% when '1.19.2', '1.19.4' %}
            assertEquals(0, vox.telemetry().pollsStarted());
            assertEquals(0, vox.telemetry().snapshotCalls());
{% when '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1', '26.1.2' %}
{% endcase %}
        }
    }

    @Test
    void blankCommandsRemainNoOpsWhenVoxIsUnavailable() {
        try (SFMVoxTerminalService vox = unavailableService(new SFMJavaLocalTerminalService())) {
            SFMTerminalResponse response = vox.openSession().execute("  ");

            assertTrue(response.success());
            assertTrue(response.lines().isEmpty());
        }
    }

{% case minecraft_version %}
{% when '1.19.2', '1.19.4' %}
    @Test
    void interactiveInputAndResizeNeverWaitForTheUnavailableEndpoint() {
        try (SFMVoxTerminalService vox = unavailableService(new SFMJavaLocalTerminalService())) {
            long started = System.nanoTime();

            assertTrue(vox.resize(100, 30, 1000, 600));
            long afterResize = System.nanoTime();
            assertTrue(vox.sendKey(65, 0, true, false));
            long afterKey = System.nanoTime();
            assertTrue(vox.sendText("a"));
            long afterText = System.nanoTime();
            assertTrue(vox.sendMouse(1, 1, 0, 0, false, true, 0, 0));
            long afterMouse = System.nanoTime();

            long elapsedMillis = Duration.ofNanos(afterMouse - started).toMillis();
            assertTrue(elapsedMillis < 250,
                    "Minecraft-thread terminal callbacks must only enqueue work; elapsed="
                            + elapsedMillis + "ms resize=" + Duration.ofNanos(afterResize - started).toMillis()
                            + "ms key=" + Duration.ofNanos(afterKey - afterResize).toMillis()
                            + "ms text=" + Duration.ofNanos(afterText - afterKey).toMillis()
                            + "ms mouse=" + Duration.ofNanos(afterMouse - afterText).toMillis() + "ms");
        }
    }

    @Test
    void capabilityIntersectionRequiresTheExactStableContractTuples() {
        SFMTerminalPresentationCatalog catalog = SFMVoxTerminalService.intersectPresentationModes(List.of(
                mode("full-png", "full", TerminalFrameEncoding.PNG, TerminalRasterFrameKind.FULL),
                mode("full-raw-rgba", "full", TerminalFrameEncoding.RGBA8, TerminalRasterFrameKind.FULL),
                mode("dirty-raw-rgba", "dirty", TerminalFrameEncoding.RGBA8,
                        TerminalRasterFrameKind.DIRTY_REGIONS)));
        List<SFMTerminalTransportOption> options =
                catalog.transportOptions(SFMTerminalRendererId.RUST_CPU_FONTDUE);

        assertEquals(List.of("full-png", "full-raw-rgba", "dirty-raw-rgba"),
                options.stream().map(option -> option.id().wireId()).toList());
        assertTrue(options.stream().allMatch(SFMTerminalTransportOption::supported));

        SFMTerminalPresentationCatalog malformedCatalog = SFMVoxTerminalService.intersectPresentationModes(List.of(
                mode("dirty-raw-rgba", "full", TerminalFrameEncoding.RGBA8,
                        TerminalRasterFrameKind.DIRTY_REGIONS)));
        List<SFMTerminalTransportOption> malformed =
                malformedCatalog.transportOptions(SFMTerminalRendererId.RUST_CPU_FONTDUE);
        assertTrue(malformed.stream().noneMatch(SFMTerminalTransportOption::supported));
        assertTrue(malformed.stream().allMatch(option -> !option.unavailableReason().isBlank()));
    }

    @Test
    void gpuPixelTelemetryDistinguishesFullTargetsFromDirtyRegions() {
        assertDoesNotThrow(() -> SFMVoxTerminalService.validateGpuPixelTelemetry(
                97_020, 388_080, 420, 231, SFMTerminalTransportId.FULL_RAW_RGBA));
        assertDoesNotThrow(() -> SFMVoxTerminalService.validateGpuPixelTelemetry(
                29_700, 118_800, 420, 231, SFMTerminalTransportId.DIRTY_RAW_RGBA));

        assertThrows(IllegalStateException.class, () -> SFMVoxTerminalService.validateGpuPixelTelemetry(
                29_700, 118_800, 420, 231, SFMTerminalTransportId.FULL_PNG));
        assertThrows(IllegalStateException.class, () -> SFMVoxTerminalService.validateGpuPixelTelemetry(
                0, 0, 420, 231, SFMTerminalTransportId.DIRTY_RAW_RGBA));
        assertThrows(IllegalStateException.class, () -> SFMVoxTerminalService.validateGpuPixelTelemetry(
                97_021, 388_084, 420, 231, SFMTerminalTransportId.DIRTY_RAW_RGBA));
        assertThrows(IllegalStateException.class, () -> SFMVoxTerminalService.validateGpuPixelTelemetry(
                29_700, 118_799, 420, 231, SFMTerminalTransportId.DIRTY_RAW_RGBA));
    }

    private static TerminalPresentationMode mode(
            String transport,
            String damage,
            TerminalFrameEncoding encoding,
            TerminalRasterFrameKind kind) {
        return new TerminalPresentationMode(
                "rust-cpu-fontdue", TerminalRasterizationOwner.SERVER,
                damage, transport, 1, encoding, kind, 1,
                TerminalFrameOrigin.TOP_LEFT, TerminalAlphaMode.STRAIGHT, TerminalColorSpace.SRGB,
                1280, 720, 4 * 1024 * 1024L, 64);
    }

{% when '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1', '26.1.2' %}
{% endcase %}
    private static SFMVoxTerminalService unavailableService(SFMTerminalService fallback) {
        ConnectionOptions options = ConnectionOptions.builder()
                .handshakeTimeout(Duration.ofMillis(100))
                .build();
        return new SFMVoxTerminalService(
                new InetSocketAddress("127.0.0.1", 1), fallback, options, Duration.ofMillis(250));
    }
}
