package ca.teamdman.sfm.client.terminal;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;

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
            assertEquals(0, vox.telemetry().pollsStarted());
            assertEquals(0, vox.telemetry().snapshotCalls());
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

    @Test
    void interactiveInputAndResizeNeverWaitForTheUnavailableEndpoint() {
        try (SFMVoxTerminalService vox = unavailableService(new SFMJavaLocalTerminalService())) {
            long started = System.nanoTime();

            assertTrue(vox.resize(100, 30, 1000, 600));
            assertTrue(vox.sendKey(65, 0, true, false));
            assertTrue(vox.sendText("a"));
            assertTrue(vox.sendMouse(1, 1, 0, 0, false, true, 0, 0));

            long elapsedMillis = Duration.ofNanos(System.nanoTime() - started).toMillis();
            assertTrue(elapsedMillis < 250,
                    "Minecraft-thread terminal callbacks must only enqueue work; elapsed="
                            + elapsedMillis + "ms");
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
