package ca.teamdman.sfm.gametest.tests.general;

import ca.teamdman.sfm.client.terminal.SFMTerminalServiceFactory;
import ca.teamdman.sfm.client.terminal.TouchDisplayTerminalRuntime;
import ca.teamdman.sfm.client.terminal.TouchDisplayTerminalServiceTransport;
import ca.teamdman.sfm.common.config.SFMConfig;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** One isolated loopback helper. Never adopts or stops the user's shared terminal server. */
final class OwnedTouchDisplayTerminalFixture implements AutoCloseable {
    private final ThreadPoolExecutor io = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(1), task -> {
                Thread thread = new Thread(task, "sfm-terminal-integration-fixture");
                thread.setDaemon(true);
                return thread;
            }, new ThreadPoolExecutor.AbortPolicy());
    private final AtomicBoolean closing = new AtomicBoolean();
    private volatile TouchDisplayTerminalServiceTransport transport;
    private volatile String failure;
    private volatile boolean ready;
    private volatile boolean cleaned;
    private Process process; // Only the single fixture I/O thread mutates process ownership.
    private Thread shutdown;

    OwnedTouchDisplayTerminalFixture() { io.execute(this::start); }

    Optional<TouchDisplayTerminalServiceTransport> prepared() {
        return ready && !closing.get() ? Optional.of(transport) : Optional.empty();
    }
    Optional<String> failure() { return Optional.ofNullable(failure); }
    boolean cleaned() { return cleaned; }

    private void start() {
        String phase = "worker_configuration";
        try {
            String worker = System.getProperty(TouchDisplayTerminalRuntime.WORKER_EXECUTABLE_PROPERTY,
                    System.getProperty("sfm.controlCliExecutable", "")).trim();
            if (worker.startsWith("\\\\?\\UNC\\")) worker = "\\\\" + worker.substring(8);
            else if (worker.startsWith("\\\\?\\")) worker = worker.substring(4);
            if (worker.isEmpty()) throw new IllegalStateException("Explicit worker override required");
            Path executable = Path.of(worker);
            // Validate the fixed executable before starting any process.
            TouchDisplayTerminalServiceTransport.bootstrapCommand(executable);
            if (closing.get()) return;

            phase = "helper_configuration";
            String configured = System.getProperty(SFMTerminalServiceFactory.VOX_SERVER_EXECUTABLE_PROPERTY, "").trim();
            if (configured.isEmpty()) configured = SFMConfig.getOrFallback(
                    SFMConfig.CLIENT_CONFIG.terminalRustServerExecutable, "teamy-terminal.exe");
            if (configured == null || configured.isBlank()) throw new IllegalStateException("Terminal helper not configured");
            InetSocketAddress endpoint;
            try (ServerSocket reservation = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
                endpoint = new InetSocketAddress("127.0.0.1", reservation.getLocalPort());
            }
            phase = "helper_start";
            process = new ProcessBuilder(configured.trim(), "serve", "127.0.0.1:" + endpoint.getPort())
                    .redirectOutput(ProcessBuilder.Redirect.INHERIT)
                    .redirectError(ProcessBuilder.Redirect.INHERIT).start();
            process.getOutputStream().close();
            Process owned = process;
            shutdown = new Thread(() -> {
                // The test started this exact process tree. No image-name or shared-server termination.
                owned.descendants().forEach(ProcessHandle::destroyForcibly);
                owned.destroyForcibly();
            }, "sfm-terminal-integration-shutdown");
            Runtime.getRuntime().addShutdownHook(shutdown);
            phase = "helper_ready";
            if (!SFMTerminalServiceFactory.awaitEndpoint(endpoint, Duration.ofSeconds(10)) || !process.isAlive()) {
                throw new IllegalStateException("Test-owned terminal helper did not become ready");
            }
            phase = "transport_prepare";
            transport = TouchDisplayTerminalServiceTransport.local(endpoint, executable);
            ready = true;
        } catch (Exception error) {
            failure = "terminal_fixture_" + phase + "_failed";
            // The queued close still runs if the test times out while startup is in flight.
            close();
        }
    }

    @Override public void close() {
        if (!closing.compareAndSet(false, true)) return;
        io.execute(() -> {
            boolean ownedProcessesStopped = false;
            try {
                List<ProcessHandle> owned = new ArrayList<>();
                if (process != null) {
                    owned.addAll(process.descendants().toList());
                    owned.add(process.toHandle());
                }
                try { if (transport != null) transport.close(); }
                finally {
                    if (process != null) process.descendants().forEach(handle -> {
                        if (!owned.contains(handle)) owned.add(handle);
                    });
                    owned.stream().filter(ProcessHandle::isAlive).forEach(ProcessHandle::destroy);
                    awaitExit(owned, Duration.ofSeconds(2));
                    owned.stream().filter(ProcessHandle::isAlive).forEach(ProcessHandle::destroyForcibly);
                    awaitExit(owned, Duration.ofSeconds(2));
                    if (owned.stream().anyMatch(ProcessHandle::isAlive)) {
                        failure = "terminal_fixture_owned_process_cleanup_failed";
                    } else ownedProcessesStopped = true;
                }
            } catch (Exception error) {
                failure = "terminal_fixture_cleanup_failed";
            } finally {
                if (shutdown != null && ownedProcessesStopped) {
                    try { Runtime.getRuntime().removeShutdownHook(shutdown); }
                    catch (IllegalStateException shuttingDown) { /* The owned-process hook is already running. */ }
                }
                cleaned = true;
                io.shutdown();
            }
        });
    }

    private static void awaitExit(List<ProcessHandle> owned, Duration timeout) throws InterruptedException {
        long until = System.nanoTime() + timeout.toNanos();
        while (owned.stream().anyMatch(ProcessHandle::isAlive) && System.nanoTime() < until) Thread.sleep(25);
    }
}
