package ca.teamdman.sfm.client.terminal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.List;

import org.facet.vox.ConnectionOptions;
import org.junit.jupiter.api.Test;

class SFMVoxTerminalServiceTests {
    @Test
    void unavailableEndpointFailsClosedWithoutReplacingJavaLocalBackend() {
        try (SFMVoxTerminalService vox = unavailableService()) {
            SFMTerminalService.SFMTerminalSession session = vox.openSession();

            SFMTerminalResponse response = session.execute("pwd");

            assertFalse(response.success());
            assertTrue(response.lines().get(0).startsWith("Vox terminal unavailable:"));
            assertTrue(response.workingDirectory().startsWith("vox://127.0.0.1:"));
            assertTrue(vox.latestSnapshot().isEmpty());
        }
    }

    @Test
    void blankCommandsRemainNoOpsWhenVoxIsUnavailable() {
        try (SFMVoxTerminalService vox = unavailableService()) {
            SFMTerminalResponse response = vox.openSession().execute("  ");

            assertTrue(response.success());
            assertTrue(response.lines().isEmpty());
        }
    }

    private static SFMVoxTerminalService unavailableService() {
        ConnectionOptions options = ConnectionOptions.builder()
                .handshakeTimeout(Duration.ofMillis(100))
                .build();
        return new SFMVoxTerminalService(
                new InetSocketAddress("127.0.0.1", 1), options, Duration.ofMillis(250));
    }
}
