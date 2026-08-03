package ca.teamdman.sfm.client.terminal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.time.Duration;

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

    @Test
    void observesEndpointListenerTeardown() throws Exception {
        InetSocketAddress endpoint;
        try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            endpoint = new InetSocketAddress(InetAddress.getLoopbackAddress(), server.getLocalPort());
            assertFalse(SFMTerminalServiceFactory.awaitEndpointUnavailable(endpoint, Duration.ZERO));
        }
        assertTrue(SFMTerminalServiceFactory.awaitEndpointUnavailable(endpoint, Duration.ofSeconds(1)));
    }
}
