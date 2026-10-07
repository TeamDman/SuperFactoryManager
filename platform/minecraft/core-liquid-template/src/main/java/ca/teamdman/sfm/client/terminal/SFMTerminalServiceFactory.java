package ca.teamdman.sfm.client.terminal;

{% if features.runtime_resource_cleanup %}
import ca.teamdman.sfm.SFM;
{% endif %}
import ca.teamdman.sfm.common.config.SFMConfig;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/** Builds explicit Java-local or Rust-backed terminal services. */
public final class SFMTerminalServiceFactory {
{% if features.terminal_remote or features.terminal_vox_runtime or features.terminal_properties %}
    public static final String VOX_ENDPOINT_PROPERTY = "sfm.terminal.voxEndpoint";
    public static final String VOX_SERVER_EXECUTABLE_PROPERTY = "sfm.terminal.rustServerExecutable";
    public static final String DEFAULT_ENDPOINT = "127.0.0.1:63946";
    private static final Duration SERVER_READY_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration SERVER_READY_RETRY = Duration.ofMillis(50);
{% if features.runtime_resource_cleanup %}
    private static final Duration SERVER_STOP_TIMEOUT = Duration.ofSeconds(2);
{% endif %}
    private static Process ownedRustServer;
{% if features.runtime_resource_cleanup %}
    private static InetSocketAddress ownedRustServerEndpoint;
{% endif %}

{% endif %}
    private SFMTerminalServiceFactory() {
    }

{% if features.terminal_local %}
    /** Creates the Java-only terminal; it never probes or depends on Rust. */
    public static SFMTerminalService createRepl() {
        return new SFMJavaLocalTerminalService();
    }
{% endif %}

{% if features.terminal_remote or features.terminal_vox_runtime or features.terminal_properties %}
{% if features.terminal_properties %}
    /** Creates the Rust scene; unavailable Vox is represented as a disconnected placeholder. */
{% else %}
    /** Creates a Rust-preferred terminal with Java-local fallback. */
{% endif %}
    public static SFMTerminalService createRust() {
        InetSocketAddress endpoint = configuredEndpoint().orElseThrow();
{% if features.terminal_properties %}
        return createRustOrUnavailable(endpoint);
    }

    /** Recreates the Rust scene against one already-resolved endpoint, including slim fallback. */
    public static SFMTerminalService createRustOrUnavailable(InetSocketAddress endpoint) {
        return instantiateRust(endpoint).orElseGet(() -> new SFMUnavailableTerminalService(endpoint));
{% else %}
        return createRustOrFallback(endpoint);
{% endif %}
    }

    /** Creates the optional Rust implementation without making Vox a Java compile-time dependency. */
    public static SFMTerminalService createRust(InetSocketAddress endpoint) {
        return instantiateRust(endpoint).orElseThrow(() -> new IllegalStateException(
                "Rust/Vox terminal support is not present in this Java-only artifact"));
    }

{% if features.terminal_properties %}
{% else %}
    private static SFMTerminalService createRustOrFallback(InetSocketAddress endpoint) {
        return instantiateRust(endpoint).orElseGet(SFMJavaLocalTerminalService::new);
    }

{% endif %}
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
{% if features.runtime_resource_cleanup %}
                // Keep the owned helper in the launcher's existing console/log
                // stream.  Discarding these handles hid bind/runtime failures
                // from both users and headless puppet evidence.
                processBuilder.redirectOutput(ProcessBuilder.Redirect.INHERIT);
                processBuilder.redirectError(ProcessBuilder.Redirect.INHERIT);
{% else %}
                processBuilder.redirectOutput(ProcessBuilder.Redirect.DISCARD);
                processBuilder.redirectError(ProcessBuilder.Redirect.DISCARD);
{% endif %}
                ownedRustServer = processBuilder.start();
{% if features.runtime_resource_cleanup %}
                ownedRustServerEndpoint = endpoint;
{% endif %}
                ownedRustServer.getOutputStream().close();
                Process server = ownedRustServer;
{% if features.runtime_resource_cleanup %}
                SFM.LOGGER.info("Started owned Teamy Terminal server pid={} endpoint={} executable={}",
                        server.pid(), endpoint, executable.trim());
{% endif %}
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
{% if features.runtime_resource_cleanup %}
            SFM.LOGGER.info("Owned Teamy Terminal server is ready pid={} endpoint={}",
                    ownedRustServer == null ? -1L : ownedRustServer.pid(), endpoint);
{% endif %}
        }
        return endpoint;
    }

    /** Stops only a Rust server process started by this factory. */
    public static synchronized void stopOwnedRustServer() {
        Process server = ownedRustServer;
{% if features.runtime_resource_cleanup %}
        InetSocketAddress endpoint = ownedRustServerEndpoint;
{% endif %}
        ownedRustServer = null;
{% if features.runtime_resource_cleanup %}
        ownedRustServerEndpoint = null;
{% endif %}
        if (server == null || !server.isAlive()) return;
{% if features.runtime_resource_cleanup %}
        SFM.LOGGER.info("Stopping owned Teamy Terminal server pid={}", server.pid());
{% endif %}
        server.destroy();
        try {
{% if features.runtime_resource_cleanup %}
            if (!server.waitFor(SERVER_STOP_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
                server.destroyForcibly();
                if (!server.waitFor(SERVER_STOP_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
                    SFM.LOGGER.warn("Owned Teamy Terminal server pid={} did not exit after forced termination",
                            server.pid());
                }
            }
            // Process exit and listener teardown are not observed atomically on
            // Windows.  A restart that probes during this gap can adopt the
            // dying listener and then inherit a refused connection.  Do not
            // return until the old endpoint has actually stopped accepting.
            if (endpoint != null && !awaitEndpointUnavailable(endpoint, SERVER_STOP_TIMEOUT)) {
                SFM.LOGGER.warn("Owned Teamy Terminal endpoint {} still accepts connections after pid={} exited",
                        endpoint, server.pid());
            }
{% else %}
            if (!server.waitFor(2, TimeUnit.SECONDS)) server.destroyForcibly();
{% endif %}
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

{% if features.runtime_resource_cleanup %}
    /** Waits until a TCP endpoint refuses connections, including the zero-time probe form. */
    static boolean awaitEndpointUnavailable(InetSocketAddress endpoint, Duration timeout)
            throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        do {
            try (Socket socket = new Socket()) {
                socket.connect(endpoint, 200);
                if (timeout.isZero()) return false;
            } catch (IOException ignored) {
                return true;
            }
            Thread.sleep(SERVER_READY_RETRY.toMillis());
        } while (System.nanoTime() < deadline);
        return false;
    }

{% endif %}
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
{% endif %}
}
