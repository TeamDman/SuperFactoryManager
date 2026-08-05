package ca.teamdman.sfm.client.terminal;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.List;

import org.facet.vox.ConnectionOptions;
import org.facet.vox.generated.TerminalAlphaMode;
import org.facet.vox.generated.TerminalColorSpace;
import org.facet.vox.generated.TerminalFrameEncoding;
import org.facet.vox.generated.TerminalFrameOrigin;
import org.facet.vox.generated.TerminalPresentationMode;
import org.facet.vox.generated.TerminalRasterFrameKind;
import org.facet.vox.generated.TerminalRasterizationOwner;
import org.facet.vox.generated.TerminalTuningMode;
import org.facet.vox.generated.TerminalTuningRequest;
import org.junit.jupiter.api.Test;

class SFMVoxTerminalServiceTests {
    @Test
    void typedTuningPreservesAutomaticAxesWithEffectiveCompatibilityValues() {
        TerminalTuningRequest request = SFMVoxTerminalService.requestedTuningRequest(
                "rust-gpu-slug",
                SFMTerminalTuningSettings.automatic(),
                120,
                40,
                1600,
                900,
                0
        );

        assertEquals("rust-gpu-slug", request.rendererId());
        assertEquals(TerminalTuningMode.AUTO, request.surfaceMode());
        assertEquals(1600, request.surfaceWidth());
        assertEquals(900, request.surfaceHeight());
        assertEquals(TerminalTuningMode.AUTO, request.fontMode());
        assertEquals(0, request.fontPixelSize());
        assertEquals(TerminalTuningMode.AUTO, request.cellsMode());
        assertEquals(120, request.columns());
        assertEquals(40, request.rows());
    }

    @Test
    void typedTuningMarksPartiallyOverriddenAxesManual() {
        TerminalTuningRequest request = SFMVoxTerminalService.requestedTuningRequest(
                "rust-cpu-fontdue",
                new SFMTerminalTuningSettings(1664, 0, 24, 124, 0),
                124,
                40,
                1664,
                900,
                24
        );

        assertEquals(TerminalTuningMode.MANUAL, request.surfaceMode());
        assertEquals(TerminalTuningMode.MANUAL, request.fontMode());
        assertEquals(TerminalTuningMode.MANUAL, request.cellsMode());
        assertEquals(1664, request.surfaceWidth());
        assertEquals(900, request.surfaceHeight());
        assertEquals(124, request.columns());
        assertEquals(40, request.rows());
    }

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

    @Test
    void capabilityIntersectionRequiresTheExactStableContractTuples() {
        SFMTerminalPresentationCatalog catalog = SFMVoxTerminalService.intersectPresentationModes(List.of(
                mode("full-png", "full", TerminalFrameEncoding.PNG, TerminalRasterFrameKind.FULL),
                mode("full-raw-rgba", "full", TerminalFrameEncoding.RGBA8, TerminalRasterFrameKind.FULL),
                mode("dirty-raw-rgba", "dirty", TerminalFrameEncoding.RGBA8,
                        TerminalRasterFrameKind.DIRTY_REGIONS)));
        List<SFMTerminalTransportOption> options =
                catalog.transportOptions(SFMTerminalRendererId.RUST_CPU_FONTDUE);

        assertEquals(List.of("full-png", "full-raw-rgba", "dirty-raw-rgba"),
                options.stream().map(option -> option.id().wireId()).toList());
        assertTrue(options.stream().allMatch(SFMTerminalTransportOption::supported));

        SFMTerminalPresentationCatalog malformedCatalog = SFMVoxTerminalService.intersectPresentationModes(List.of(
                mode("dirty-raw-rgba", "full", TerminalFrameEncoding.RGBA8,
                        TerminalRasterFrameKind.DIRTY_REGIONS)));
        List<SFMTerminalTransportOption> malformed =
                malformedCatalog.transportOptions(SFMTerminalRendererId.RUST_CPU_FONTDUE);
        assertTrue(malformed.stream().noneMatch(SFMTerminalTransportOption::supported));
        assertTrue(malformed.stream().allMatch(option -> !option.unavailableReason().isBlank()));
    }

    @Test
    void gpuPixelTelemetryDistinguishesFullTargetsFromDirtyRegions() {
        assertDoesNotThrow(() -> SFMVoxTerminalService.validateGpuPixelTelemetry(
                97_020, 388_080, 420, 231, SFMTerminalTransportId.FULL_RAW_RGBA));
        assertDoesNotThrow(() -> SFMVoxTerminalService.validateGpuPixelTelemetry(
                29_700, 118_800, 420, 231, SFMTerminalTransportId.DIRTY_RAW_RGBA));

        assertThrows(IllegalStateException.class, () -> SFMVoxTerminalService.validateGpuPixelTelemetry(
                29_700, 118_800, 420, 231, SFMTerminalTransportId.FULL_PNG));
        assertThrows(IllegalStateException.class, () -> SFMVoxTerminalService.validateGpuPixelTelemetry(
                0, 0, 420, 231, SFMTerminalTransportId.DIRTY_RAW_RGBA));
        assertThrows(IllegalStateException.class, () -> SFMVoxTerminalService.validateGpuPixelTelemetry(
                97_021, 388_084, 420, 231, SFMTerminalTransportId.DIRTY_RAW_RGBA));
        assertThrows(IllegalStateException.class, () -> SFMVoxTerminalService.validateGpuPixelTelemetry(
                29_700, 118_799, 420, 231, SFMTerminalTransportId.DIRTY_RAW_RGBA));
    }

    private static TerminalPresentationMode mode(
            String transport,
            String damage,
            TerminalFrameEncoding encoding,
            TerminalRasterFrameKind kind) {
        return new TerminalPresentationMode(
                "rust-cpu-fontdue", TerminalRasterizationOwner.SERVER,
                damage, transport, 1, encoding, kind, 1,
                TerminalFrameOrigin.TOP_LEFT, TerminalAlphaMode.STRAIGHT, TerminalColorSpace.SRGB,
                1280, 720, 4 * 1024 * 1024L, 64);
    }

    private static SFMVoxTerminalService unavailableService(SFMTerminalService fallback) {
        ConnectionOptions options = ConnectionOptions.builder()
                .handshakeTimeout(Duration.ofMillis(100))
                .build();
        return new SFMVoxTerminalService(
                new InetSocketAddress("127.0.0.1", 1), fallback, options, Duration.ofMillis(250));
    }
}
