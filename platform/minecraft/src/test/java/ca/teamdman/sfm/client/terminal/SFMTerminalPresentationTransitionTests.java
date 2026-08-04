package ca.teamdman.sfm.client.terminal;

import org.facet.vox.ConnectionOptions;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SFMTerminalPresentationTransitionTests {
    private static final SFMTerminalPresentationSelection CPU_PNG =
            SFMTerminalPresentationSelection.DEFAULT;
    private static final SFMTerminalPresentationSelection GPU_PNG =
            new SFMTerminalPresentationSelection(
                    SFMTerminalRendererId.RUST_GPU_SLUG,
                    SFMTerminalTransportId.FULL_PNG);
    private static final SFMTerminalPresentationSelection GPU_DIRTY =
            new SFMTerminalPresentationSelection(
                    SFMTerminalRendererId.RUST_GPU_SLUG,
                    SFMTerminalTransportId.DIRTY_RAW_RGBA);

    @Test
    void rendererAndTransportRequestsUseTheSameAtomicRequestedPendingPath() throws Exception {
        try (SFMVoxTerminalService service = unavailableService()) {
            setField(service, "presentationCatalog", allSixCatalog());
            setField(service, "activePresentation", CPU_PNG);
            setField(service, "presentationRequestGeneration", 40L);

            SFMTerminalPresentationChangeResult rendererResult =
                    service.requestRenderer("rust-gpu-slug");
            SFMTerminalPresentationTransitionState rendererPending = service.presentationState();

            assertTrue(rendererResult.accepted());
            assertEquals(GPU_PNG, rendererPending.requested());
            assertEquals(Optional.of(CPU_PNG), rendererPending.active());
            assertTrue(rendererPending.pending());
            assertEquals(41L, getLong(service, "presentationRequestGeneration"));
            assertEquals(null, getField(service, "sessionId"));

            SFMTerminalPresentationChangeResult transportResult =
                    service.requestTransport("dirty-raw-rgba");
            SFMTerminalPresentationTransitionState transportPending = service.presentationState();

            assertTrue(transportResult.accepted());
            assertEquals(GPU_DIRTY, transportPending.requested());
            assertEquals(Optional.of(CPU_PNG), transportPending.active());
            assertTrue(transportPending.pending());
            assertEquals(42L, getLong(service, "presentationRequestGeneration"));
            assertEquals(null, getField(service, "sessionId"),
                    "presentation changes must not replace or synthesize a terminal session");
        }
    }

    @Test
    void rejectedTuplePreservesRequestedActiveGenerationAndRetainedFrame() throws Exception {
        try (SFMVoxTerminalService service = unavailableService()) {
            setField(service, "presentationCatalog", onlyCpuPngCatalog());
            setField(service, "activePresentation", CPU_PNG);
            setField(service, "presentationRequestGeneration", 9L);
            SFMTerminalFrame retained = new SFMTerminalFrame(
                    7, true, true, png(), null, "retained-stream");
            setField(service, "pendingRasterFrame", retained);
            SFMTerminalPresentationTransitionState before = service.presentationState();

            SFMTerminalPresentationChangeResult unsupported =
                    service.requestRenderer("rust-gpu-slug");
            SFMTerminalPresentationChangeResult unknown =
                    service.requestTransport("future-transport");

            assertFalse(unsupported.accepted());
            assertTrue(unsupported.message().contains("unavailable"));
            assertFalse(unknown.accepted());
            assertTrue(unknown.message().contains("Unknown terminal transport id"));
            assertEquals(before, service.presentationState());
            assertEquals(9L, getLong(service, "presentationRequestGeneration"));
            assertSame(retained, getField(service, "pendingRasterFrame"));
        }
    }

    @Test
    void aNewPresentationGenerationRequiresFullResyncBeforeReplacingPixels() {
        SFMTerminalRgbaCompositor compositor = new SFMTerminalRgbaCompositor(
                new SFMTerminalRasterLimits(8, 8, 1024, 8));
        byte[] cpuPixels = rgba(1, 2, 3, 4);
        byte[] gpuPixels = rgba(5, 6, 7, 8);
        compositor.apply(fullFrame("presentation-1", 10, true, cpuPixels));
        compositor.expectGeneration("presentation-2");

        assertThrows(IllegalArgumentException.class,
                () -> compositor.apply(fullFrame("presentation-2", 1, false, gpuPixels)));
        assertArrayEquals(cpuPixels, compositor.pixels(),
                "the active presentation must remain visible while replacement is pending");
        assertEquals("presentation-1", compositor.generation());
        assertEquals("presentation-2", compositor.expectedGeneration());

        assertEquals(SFMTerminalRgbaCompositor.ApplyResult.APPLIED,
                compositor.apply(fullFrame("presentation-2", 1, true, gpuPixels)));
        assertArrayEquals(gpuPixels, compositor.pixels());
        assertEquals("presentation-2", compositor.generation());
    }

    private static SFMTerminalPresentationCatalog allSixCatalog() {
        return SFMTerminalPresentationCatalog.intersect(
                "rust-cpu-fontdue",
                "full-png",
                java.util.Arrays.stream(SFMTerminalRendererId.values())
                        .flatMap(renderer -> java.util.Arrays.stream(SFMTerminalTransportId.values())
                                .map(transport -> advertised(renderer, transport)))
                        .toList());
    }

    private static SFMTerminalPresentationCatalog onlyCpuPngCatalog() {
        return SFMTerminalPresentationCatalog.intersect(
                "rust-cpu-fontdue",
                "full-png",
                List.of(advertised(
                        SFMTerminalRendererId.RUST_CPU_FONTDUE,
                        SFMTerminalTransportId.FULL_PNG)));
    }

    private static SFMTerminalPresentationAdvertisedMode advertised(
            SFMTerminalRendererId renderer,
            SFMTerminalTransportId transport) {
        boolean png = transport == SFMTerminalTransportId.FULL_PNG;
        boolean dirty = transport == SFMTerminalTransportId.DIRTY_RAW_RGBA;
        return new SFMTerminalPresentationAdvertisedMode(
                renderer.wireId(),
                "server",
                dirty ? "dirty" : "full",
                transport.wireId(),
                1,
                png ? SFMTerminalRasterEncoding.PNG : SFMTerminalRasterEncoding.RGBA8,
                dirty ? SFMTerminalRasterFrameKind.DIRTY_REGIONS : SFMTerminalRasterFrameKind.FULL,
                1,
                SFMTerminalRasterOrigin.TOP_LEFT,
                SFMTerminalRasterAlphaMode.STRAIGHT,
                SFMTerminalRasterColorSpace.SRGB,
                1280,
                720,
                4 * 1024 * 1024L,
                64);
    }

    private static SFMTerminalRasterFrame fullFrame(
            String generation,
            long sequence,
            boolean fullResync,
            byte[] pixels) {
        return new SFMTerminalRasterFrame(
                SFMTerminalTransportId.FULL_RAW_RGBA,
                1,
                1,
                generation,
                sequence,
                0,
                fullResync,
                SFMTerminalRasterEncoding.RGBA8,
                SFMTerminalRasterFrameKind.FULL,
                SFMTerminalRasterOrigin.TOP_LEFT,
                SFMTerminalRasterAlphaMode.STRAIGHT,
                SFMTerminalRasterColorSpace.SRGB,
                1,
                1,
                4,
                pixels,
                List.of());
    }

    private static SFMVoxTerminalService unavailableService() {
        ConnectionOptions options = ConnectionOptions.builder()
                .handshakeTimeout(Duration.ofMillis(100))
                .build();
        return new SFMVoxTerminalService(
                new InetSocketAddress("127.0.0.1", 1),
                new SFMJavaLocalTerminalService(),
                options,
                Duration.ofMillis(250));
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static Object getField(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static long getLong(Object target, String name) throws Exception {
        return (long) getField(target, name);
    }

    private static byte[] png() {
        return new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
    }

    private static byte[] rgba(int... bytes) {
        byte[] result = new byte[bytes.length];
        for (int index = 0; index < bytes.length; index++) result[index] = (byte) bytes[index];
        return result;
    }
}
