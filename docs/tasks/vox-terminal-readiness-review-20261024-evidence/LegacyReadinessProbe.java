package ca.teamdman.sfm.client.terminal;

import java.net.*;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.facet.vox.*;
import org.facet.vox.generated.*;

/** Runs against native-rendered legacy consumer classes and the unchanged pinned Vox JAR. */
public final class LegacyReadinessProbe {
    static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    static Object field(Object value, String name) throws Exception {
        var f = value.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(value);
    }
    static void set(Object value, String name, Object next) throws Exception {
        var f = value.getClass().getDeclaredField(name); f.setAccessible(true); f.set(value, next);
    }
    static void daemon(Runnable action) { var t = new Thread(action); t.setDaemon(true); t.start(); }
    static ConnectionOptions options() {
        return ConnectionOptions.builder().handshakeTimeout(Duration.ofSeconds(2))
                .idleTimeout(Duration.ofSeconds(10)).closeTimeout(Duration.ofMillis(200)).build();
    }
    static final class Fixture implements AutoCloseable {
        SFMVoxTerminalService service;
        VoxConnection client, server;
        AtomicInteger calls = new AtomicInteger(), forwarded = new AtomicInteger(), localExecutions = new AtomicInteger();
        volatile TerminalError error;
        volatile boolean timeout;
        volatile String resultSession = "session";
        Fixture() throws Exception {
            TerminalHandler handler = (TerminalHandler) java.lang.reflect.Proxy.newProxyInstance(
                    TerminalHandler.class.getClassLoader(), new Class<?>[]{TerminalHandler.class}, (proxy, method, args) -> {
                        if (method.getName().equals("disconnect")) return CompletableFuture.completedFuture(
                                VoxResult.success(new TerminalOperationResult("session", TerminalState.CLOSED, 0)));
                        calls.incrementAndGet();
                        if (timeout) return new CompletableFuture<>();
                        if (error != null) return CompletableFuture.completedFuture(VoxResult.applicationError(error));
                        forwarded.incrementAndGet();
                        if (method.getName().equals("cancel")) return CompletableFuture.completedFuture(
                                VoxResult.success(new TerminalOperationResult("session", TerminalState.READY, 1)));
                        return CompletableFuture.completedFuture(VoxResult.success(new TerminalInputResult(resultSession, 1, 1)));
                    });
            try (ServerSocket listener = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
                var endpoint = new InetSocketAddress(InetAddress.getLoopbackAddress(), listener.getLocalPort());
                client = VoxConnection.connect(endpoint, options());
                server = VoxConnection.accept(listener.accept(), new ServiceRegistry().register(new TerminalDispatcher(handler)), options());
                client.start(LegacyReadinessProbe::daemon); server.start(LegacyReadinessProbe::daemon);
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
                while (client.state() != ConnectionState.OPEN && System.nanoTime() < deadline) Thread.sleep(2);
                check(client.state() == ConnectionState.OPEN, "fixture connection");
                ServiceLane lane = client.openLane(TerminalServiceDescriptor.INSTANCE, LaneOptions.defaults());
                lane.opened().get(3, TimeUnit.SECONDS);
                SFMTerminalService fallback = () -> new SFMTerminalService.SFMTerminalSession() {
                    public SFMTerminalResponse execute(String command) { localExecutions.incrementAndGet(); return SFMTerminalResponse.ok(List.of(), "local"); }
                    public String workingDirectory() { return "local"; }
                };
                service = new SFMVoxTerminalService(endpoint, fallback, options(), Duration.ofMillis(250));
                set(service, "connection", client); set(service, "lane", lane);
                set(service, "client", new TerminalClient(lane)); set(service, "sessionId", "session");
                // This probe isolates input; frame polling has its separate service tests.
                set(service, "polling", true);
            }
        }
        public void close() { service.close(); client.close(); server.close(); }
    }
    public static void main(String[] ignored) throws Exception {
        try (Fixture f = new Fixture()) {
            Object generation = field(f.service, "transportGeneration");
            f.error = new TerminalError(TerminalErrorCode.INVALID_REQUEST, "arbitrary 雪", true, 81);
            check(!f.service.sendText("not forwarded"), "synchronous rejection must be false");
            check(!f.service.sendKey(65, 0, true, false), "key rejection");
            check(!f.service.sendMouse(1, 1, 0, 0, false, true, 0, 0), "mouse rejection");
            check(!f.service.cancel(), "cancel rejection");
            check(!f.service.openSession().execute("not replayed").success(), "execute rejection");
            check(f.localExecutions.get() == 0 && f.forwarded.get() == 0 && f.calls.get() == 5, "no automatic replay: calls="+f.calls+" forwarded="+f.forwarded+" local="+f.localExecutions);
            check(field(f.service, "sessionId").equals("session"), "same session");
            check(field(f.service, "transportGeneration").equals(generation), "same generation");
            check(field(f.service, "inputFailure").equals(f.error), "typed fields preserved");
            check(f.service.failureMessage().orElseThrow().contains("retry manually"), "visible retryable failure");
            f.error = null;
            f.resultSession = "unrelated-session";
            check(f.service.sendText("wrong-session reply"), "request completed");
            check(f.service.failureMessage().isPresent(), "wrong-session reply cannot clear rejection");
            f.resultSession = "session";
            check(f.service.sendText("user retry"), "retry succeeds");
            check(f.forwarded.get() == 2 && f.calls.get() == 7, "two explicit user actions");
            check(f.service.failureMessage().isEmpty(), "transient cleared");
        }
        for (TerminalError error : List.of(
                new TerminalError(TerminalErrorCode.INVALID_REQUEST, "same words", false, 1),
                new TerminalError(TerminalErrorCode.DISCONNECTED, "same words", false, 2),
                new TerminalError(TerminalErrorCode.INTERNAL, "same words", true, 3))) {
            try (Fixture f = new Fixture()) {
                f.error = error;
                check(!f.service.sendText("failed"), "ordinary error rejected");
                check(field(f.service, "sessionId") == null, "ordinary error disconnects");
            }
        }
        try (Fixture f = new Fixture()) {
            f.timeout = true;
            check(!f.service.openSession().execute("ambiguous acceptance").success(), "timeout rejected");
            check(f.localExecutions.get() == 0 && f.calls.get() == 1, "timeout must not replay locally");
            check(field(f.service, "sessionId") == null, "timeout retires transport");
        }
        System.out.println("PASS: legacy synchronous typed rejection, same-session recovery, transient clear, narrow classification, timeout and no local replay");
    }
}
