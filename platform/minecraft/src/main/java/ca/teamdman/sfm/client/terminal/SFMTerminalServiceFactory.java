package ca.teamdman.sfm.client.terminal;

import ca.teamdman.sfm.common.config.SFMConfig;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/** Builds explicit Java-local or Rust-backed terminal services. */
public final class SFMTerminalServiceFactory {
    public static final String VOX_ENDPOINT_PROPERTY = "sfm.terminal.voxEndpoint";
    public static final String VOX_SERVER_EXECUTABLE_PROPERTY = "sfm.terminal.rustServerExecutable";
    public static final String DEFAULT_ENDPOINT = "127.0.0.1:63946";
    private static final Duration SERVER_READY_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration SERVER_READY_RETRY = Duration.ofMillis(50);
    private static Process ownedRustServer;

    private SFMTerminalServiceFactory() {
    }

    /** Creates the Java-only terminal; it never probes or depends on Rust. */
    public static SFMTerminalService createRepl() {
        return new SFMJavaLocalTerminalService();
    }

    /** Creates the Rust scene; unavailable Vox is represented as a disconnected placeholder. */
    public static SFMTerminalService createRust() {
        InetSocketAddress endpoint = configuredEndpoint().orElseThrow();
        return instantiateRust(endpoint).orElseGet(() -> new SFMUnavailableTerminalService(endpoint));
    }

    /** Creates the optional Rust implementation without making Vox a Java compile-time dependency. */
    public static SFMTerminalService createRust(InetSocketAddress endpoint) {
        return instantiateRust(endpoint).orElseThrow(() -> new IllegalStateException(
                "Rust/Vox terminal support is not present in this Java-only artifact"));
    }

    private static Optional<SFMTerminalService> instantiateRust(InetSocketAddress endpoint) {
        try {
            Class<?> implementation = Class.forName(
                    "ca.teamdman.sfm.client.terminal.SFMVoxTerminalService");
            Object service = implementation
                    .getConstructor(InetSocketAddress.class)
                    .newInstance(endpoint);
            return Optional.of((SFMTerminalService) service);
        } catch (ClassNotFoundException error) {
            return Optional.empty();
        } catch (ReflectiveOperationException | ClassCastException error) {
            throw new IllegalStateException("Could not construct Rust/Vox terminal service", error);
        }
    }

    /** Starts or adopts the configured server and waits for its TCP endpoint to accept connections. */
    public static synchronized InetSocketAddress startRustServer(String rawAddress)
            throws IOException, InterruptedException {
        String source = rawAddress == null ? "terminalRustServerAddress" : "start-rust-server address";
        String configuredAddress = rawAddress == null
                ? SFMConfig.getOrFallback(SFMConfig.CLIENT_CONFIG.terminalRustServerAddress, DEFAULT_ENDPOINT)
                : rawAddress;
        InetSocketAddress endpoint = parseEndpoint(configuredAddress, source);
        if (!awaitEndpoint(endpoint, Duration.ZERO)) {
            if (ownedRustServer == null || !ownedRustServer.isAlive()) {
                String executableProperty = System.getProperty(VOX_SERVER_EXECUTABLE_PROPERTY, "").trim();
                String executable = executableProperty.isEmpty()
                        ? SFMConfig.getOrFallback(
                        SFMConfig.CLIENT_CONFIG.terminalRustServerExecutable, "teamy-terminal.exe")
                        : executableProperty;
                if (executable == null || executable.isBlank()) {
                    throw new IOException("terminalRustServerExecutable is empty");
                }
                ProcessBuilder processBuilder = new ProcessBuilder(
                        executable.trim(), "serve", endpoint.getHostString() + ":" + endpoint.getPort());
                processBuilder.redirectOutput(ProcessBuilder.Redirect.DISCARD);
                processBuilder.redirectError(ProcessBuilder.Redirect.DISCARD);
                ownedRustServer = processBuilder.start();
                ownedRustServer.getOutputStream().close();
                Process server = ownedRustServer;
                Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                    if (server.isAlive()) {
                        server.destroy();
                    }
                }, "sfm-rust-terminal-shutdown"));
            }
            if (!awaitEndpoint(endpoint, SERVER_READY_TIMEOUT)) {
                if (ownedRustServer != null && !ownedRustServer.isAlive()) {
                    throw new IOException("teamy-terminal exited with code " + ownedRustServer.exitValue());
                }
                throw new IOException("Rust terminal server did not become ready at " + endpoint);
            }
        }
        return endpoint;
    }

    /** Stops only a Rust server process started by this factory. */
    public static synchronized void stopOwnedRustServer() {
        Process server = ownedRustServer;
        ownedRustServer = null;
        if (server == null || !server.isAlive()) return;
        server.destroy();
        try {
            if (!server.waitFor(2, TimeUnit.SECONDS)) server.destroyForcibly();
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            server.destroyForcibly();
        }
    }

    /** Waits for a loopback endpoint without sending application data. */
    public static boolean awaitEndpoint(InetSocketAddress endpoint, Duration timeout)
            throws IOException, InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        do {
            try (Socket socket = new Socket()) {
                socket.connect(endpoint, 200);
                return true;
            } catch (IOException ignored) {
                if (timeout.isZero()) return false;
            }
            Thread.sleep(SERVER_READY_RETRY.toMillis());
        } while (System.nanoTime() < deadline);
        return false;
    }

    /** Resolves the configured endpoint, retaining the JVM property as a test override. */
    public static Optional<InetSocketAddress> configuredEndpoint() {
        String property = System.getProperty(VOX_ENDPOINT_PROPERTY, "").trim();
        String value = property.isEmpty()
                ? SFMConfig.getOrFallback(SFMConfig.CLIENT_CONFIG.terminalRustServerAddress, DEFAULT_ENDPOINT)
                : property;
        return Optional.of(parseEndpoint(value, property.isEmpty() ? "terminalRustServerAddress" : VOX_ENDPOINT_PROPERTY));
    }

    /** Parses HOST:PORT, :PORT, or a bare port as a loopback endpoint. */
    public static InetSocketAddress parseEndpoint(String raw, String source) {
        String value = raw == null ? "" : raw.trim();
        if (value.startsWith(":")) value = "127.0.0.1" + value;
        else if (value.chars().allMatch(Character::isDigit)) value = "127.0.0.1:" + value;

        int separator = value.lastIndexOf(':');
        if (separator <= 0 || separator == value.length() - 1) {
            throw new IllegalArgumentException(
                    "The " + source + " value must be HOST:PORT, :PORT, or PORT, got: " + raw);
        }
        String host = value.substring(0, separator).trim();
        try {
            int port = Integer.parseInt(value.substring(separator + 1).trim());
            if (port < 1 || port > 65535) {
                throw new IllegalArgumentException(
                        "The " + source + " port must be within 1..65535, got: " + port);
            }
            return new InetSocketAddress(host, port);
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException(
                    "The " + source + " port must be numeric, got: " + raw, error);
        }
    }
}
