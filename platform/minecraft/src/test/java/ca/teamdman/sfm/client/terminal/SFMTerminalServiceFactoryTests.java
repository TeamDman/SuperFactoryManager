package ca.teamdman.sfm.client.terminal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.InetSocketAddress;

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
}
