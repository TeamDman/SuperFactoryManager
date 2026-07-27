package ca.teamdman.sfm.client.terminal;

import java.net.InetSocketAddress;
import java.util.Optional;

/** Selects the Rust endpoint when explicitly configured, otherwise local fallback. */
public final class SFMTerminalServiceFactory {
    public static final String VOX_ENDPOINT_PROPERTY = "sfm.terminal.voxEndpoint";

    private SFMTerminalServiceFactory() {
    }

    public static SFMTerminalService create() {
        return configuredEndpoint()
                .<SFMTerminalService>map(SFMVoxTerminalService::new)
                .orElseGet(SFMJavaLocalTerminalService::new);
    }

    public static boolean voxConfigured() {
        return configuredEndpoint().isPresent();
    }

    static Optional<InetSocketAddress> configuredEndpoint() {
        String value = System.getProperty(VOX_ENDPOINT_PROPERTY, "").trim();
        if (value.isEmpty()) return Optional.empty();
        int separator = value.lastIndexOf(':');
        if (separator <= 0 || separator == value.length() - 1) {
            throw new IllegalArgumentException(
                    "The " + VOX_ENDPOINT_PROPERTY + " value must be HOST:PORT, got: " + value);
        }
        String host = value.substring(0, separator).trim();
        int port;
        try {
            port = Integer.parseInt(value.substring(separator + 1).trim());
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException(
                    "The " + VOX_ENDPOINT_PROPERTY + " port must be numeric, got: " + value, error);
        }
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException(
                    "The " + VOX_ENDPOINT_PROPERTY + " port must be within 1..65535, got: " + port);
        }
        return Optional.of(new InetSocketAddress(host, port));
    }
}
