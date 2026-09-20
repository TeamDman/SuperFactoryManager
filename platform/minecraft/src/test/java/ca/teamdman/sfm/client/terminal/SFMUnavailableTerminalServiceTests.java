package ca.teamdman.sfm.client.terminal;

import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SFMUnavailableTerminalServiceTests {
    @Test
    void javaOnlyArtifactReportsAnExplicitDisconnectedRustScene() {
        try (SFMUnavailableTerminalService service = new SFMUnavailableTerminalService(
                new InetSocketAddress("127.0.0.1", 63946))) {
            assertFalse(service.isConnected());
            assertFalse(service.isConnecting());
            assertTrue(service.failureMessage().orElseThrow()
                    .contains("Rust/Vox terminal support is not present in this artifact"));
            assertFalse(service.sendKey(65, 0, true, false));
            assertFalse(service.resize(80, 20));
            assertFalse(service.openSession().execute("pwd").success());
        }
    }
}
