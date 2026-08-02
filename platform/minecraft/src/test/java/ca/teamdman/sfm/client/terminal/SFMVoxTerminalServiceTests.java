package ca.teamdman.sfm.client.terminal;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.List;

import org.facet.vox.ConnectionOptions;
import org.junit.jupiter.api.Test;

class SFMVoxTerminalServiceTests {
    @Test
    void acceptsOnlyPayloadsWithThePngSignature() {
        assertTrue(SFMVoxTerminalService.isPng(new byte[]{
                (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
        }));
        assertFalse(SFMVoxTerminalService.isPng(new byte[]{0x50, 0x4E, 0x47}));
        assertFalse(SFMVoxTerminalService.isPng(null));
    }

    @Test
    void unavailableEndpointStaysRustDisconnectedInsteadOfFallingBack() {
        try (SFMVoxTerminalService vox = unavailableService(new SFMJavaLocalTerminalService())) {
            SFMTerminalService.SFMTerminalSession session = vox.openSession();

            SFMTerminalResponse response = session.execute("pwd");

            assertFalse(response.success());
            assertTrue(response.lines().get(0).startsWith("Rust terminal unavailable:"));
            assertTrue(vox.latestSnapshot().isEmpty());
        }
    }

    @Test
    void blankCommandsRemainNoOpsWhenVoxIsUnavailable() {
        try (SFMVoxTerminalService vox = unavailableService(new SFMJavaLocalTerminalService())) {
            SFMTerminalResponse response = vox.openSession().execute("  ");

            assertTrue(response.success());
            assertTrue(response.lines().isEmpty());
        }
    }

    private static SFMVoxTerminalService unavailableService(SFMTerminalService fallback) {
        ConnectionOptions options = ConnectionOptions.builder()
                .handshakeTimeout(Duration.ofMillis(100))
                .build();
        return new SFMVoxTerminalService(
                new InetSocketAddress("127.0.0.1", 1), fallback, options, Duration.ofMillis(250));
    }
}
