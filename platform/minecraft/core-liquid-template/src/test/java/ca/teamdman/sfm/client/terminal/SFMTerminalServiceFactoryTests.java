package ca.teamdman.sfm.client.terminal;

import static org.junit.jupiter.api.Assertions.assertEquals;
{% if features.runtime_resource_cleanup %}
import static org.junit.jupiter.api.Assertions.assertFalse;
{% endif %}
import static org.junit.jupiter.api.Assertions.assertThrows;
{% if features.runtime_resource_cleanup %}

import static org.junit.jupiter.api.Assertions.assertTrue;
{% endif %}

{% if features.runtime_resource_cleanup %}
import java.net.InetAddress;
{% endif %}
import java.net.InetSocketAddress;
{% if features.runtime_resource_cleanup %}

import java.net.ServerSocket;
import java.time.Duration;
{% endif %}

import org.junit.jupiter.api.Test;

class SFMTerminalServiceFactoryTests {
    @Test
    void parsesLoopbackShorthandAndBarePorts() {
        assertEquals(new InetSocketAddress("127.0.0.1", 63946),
                SFMTerminalServiceFactory.parseEndpoint(":63946", "test"));
        assertEquals(new InetSocketAddress("127.0.0.1", 63946),
                SFMTerminalServiceFactory.parseEndpoint("63946", "test"));
        assertEquals(new InetSocketAddress("localhost", 63946),
                SFMTerminalServiceFactory.parseEndpoint("localhost:63946", "test"));
    }

    @Test
    void rejectsMalformedEndpoint() {
        assertThrows(IllegalArgumentException.class,
                () -> SFMTerminalServiceFactory.parseEndpoint("localhost", "test"));
        assertThrows(IllegalArgumentException.class,
                () -> SFMTerminalServiceFactory.parseEndpoint("localhost:0", "test"));
    }
{% if features.runtime_resource_cleanup %}

    @Test
    void observesEndpointListenerTeardown() throws Exception {
        InetSocketAddress endpoint;
        try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            endpoint = new InetSocketAddress(InetAddress.getLoopbackAddress(), server.getLocalPort());
            assertFalse(SFMTerminalServiceFactory.awaitEndpointUnavailable(endpoint, Duration.ZERO));
        }
        assertTrue(SFMTerminalServiceFactory.awaitEndpointUnavailable(endpoint, Duration.ofSeconds(1)));
    }
{% endif %}
}
