package ca.teamdman.sfm.client.terminal;

import org.facet.vox.generated.TerminalAlphaMode;
import org.facet.vox.generated.TerminalColorSpace;
import org.facet.vox.generated.TerminalFrameEncoding;
import org.facet.vox.generated.TerminalFrameOrigin;
import org.facet.vox.generated.TerminalPresentationCapabilitiesResult;
import org.facet.vox.generated.TerminalPresentationMode;
import org.facet.vox.generated.TerminalRasterFrameKind;
import org.facet.vox.generated.TerminalRasterSubscribeRequest;
import org.facet.vox.generated.TerminalRasterizationOwner;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SFMTerminalPresentationContractTests {
    @Test
    void generatedContractMapsAllSixServerOwnedTuplesWithoutCollapsingTransportIds() {
        List<TerminalPresentationMode> modes = List.of(
                mode("rust-cpu-fontdue", "dirty-raw-rgba"),
                mode("rust-gpu-slug", "full-png"),
                mode("rust-cpu-fontdue", "full-png"),
                mode("rust-gpu-slug", "dirty-raw-rgba"),
                mode("rust-cpu-fontdue", "full-raw-rgba"),
                mode("rust-gpu-slug", "full-raw-rgba"));
        TerminalPresentationCapabilitiesResult generated =
                new TerminalPresentationCapabilitiesResult(
                        "session-a", "rust-gpu-slug", "full-raw-rgba", modes, 17);

        SFMTerminalPresentationCatalog catalog =
                SFMVoxTerminalPresentationAdapter.catalog(generated);

        assertEquals(
                new SFMTerminalPresentationSelection(
                        SFMTerminalRendererId.RUST_GPU_SLUG,
                        SFMTerminalTransportId.FULL_RAW_RGBA),
                catalog.defaultSelection(),
                "renderer and transport defaults must be read from their independent fields");
        assertEquals(6, catalog.modes().size());
        assertEquals(6, catalog.modes().stream()
                .map(option -> option.tuple().selection())
                .distinct()
                .count());
        assertTrue(catalog.modes().stream().allMatch(SFMTerminalPresentationModeOption::supported));
        assertTrue(catalog.modes().stream()
                .allMatch(option -> option.tuple().rasterizationOwner()
                        == SFMTerminalRasterizationOwner.SERVER));

        Map<SFMTerminalTransportId, Long> transportOccurrences = catalog.modes().stream()
                .collect(Collectors.groupingBy(
                        option -> option.tuple().transportId(),
                        Collectors.counting()));
        assertEquals(Map.of(
                SFMTerminalTransportId.FULL_PNG, 2L,
                SFMTerminalTransportId.FULL_RAW_RGBA, 2L,
                SFMTerminalTransportId.DIRTY_RAW_RGBA, 2L), transportOccurrences);

        for (SFMTerminalRendererId renderer : SFMTerminalRendererId.values()) {
            assertEquals(3, catalog.transportOptions(renderer).stream()
                    .filter(SFMTerminalTransportOption::supported)
                    .count());
        }
        for (SFMTerminalTransportId transport : SFMTerminalTransportId.values()) {
            assertEquals(2, catalog.rendererOptions(transport).stream()
                    .filter(SFMTerminalRendererOption::supported)
                    .count());
        }
    }

    @Test
    void unknownOwnerRendererAndTransportAreRejectedWithActionableReasons() {
        SFMTerminalPresentationCatalog catalog = SFMTerminalPresentationCatalog.intersect(
                "rust-cpu-fontdue",
                "full-png",
                List.of(
                        advertised("rust-cpu-fontdue", "future-owner", "full-png"),
                        advertised("rust-future", "server", "full-png"),
                        advertised("rust-cpu-fontdue", "server", "future-transport")));

        assertEquals(3, catalog.rejections().size());
        assertTrue(catalog.rejections().stream()
                .map(SFMTerminalPresentationCatalog.Rejection::reason)
                .anyMatch(reason -> reason.contains("Unknown terminal rasterization owner")));
        assertTrue(catalog.rejections().stream()
                .map(SFMTerminalPresentationCatalog.Rejection::reason)
                .anyMatch(reason -> reason.contains("Unknown terminal renderer id")));
        assertTrue(catalog.rejections().stream()
                .map(SFMTerminalPresentationCatalog.Rejection::reason)
                .anyMatch(reason -> reason.contains("Unknown terminal transport id")));
        assertTrue(catalog.modes().isEmpty());
    }

    @Test
    void invalidOwnerAndRendererTransportShapesRemainUnavailableWithoutFallback() {
        SFMTerminalPresentationAdvertisedMode clientOwnedPixels =
                advertised("rust-cpu-fontdue", "client", "full-png");
        SFMTerminalPresentationAdvertisedMode malformedDirty = new SFMTerminalPresentationAdvertisedMode(
                "rust-gpu-slug",
                "server",
                "full",
                "dirty-raw-rgba",
                1,
                SFMTerminalRasterEncoding.RGBA8,
                SFMTerminalRasterFrameKind.DIRTY_REGIONS,
                1,
                SFMTerminalRasterOrigin.TOP_LEFT,
                SFMTerminalRasterAlphaMode.STRAIGHT,
                SFMTerminalRasterColorSpace.SRGB,
                1280,
                720,
                4 * 1024 * 1024L,
                64);
        SFMTerminalPresentationCatalog catalog = SFMTerminalPresentationCatalog.intersect(
                "rust-cpu-fontdue", "full-png", List.of(clientOwnedPixels, malformedDirty));

        SFMTerminalPresentationSelection cpuPng = new SFMTerminalPresentationSelection(
                SFMTerminalRendererId.RUST_CPU_FONTDUE, SFMTerminalTransportId.FULL_PNG);
        SFMTerminalPresentationSelection gpuDirty = new SFMTerminalPresentationSelection(
                SFMTerminalRendererId.RUST_GPU_SLUG, SFMTerminalTransportId.DIRTY_RAW_RGBA);

        assertTrue(catalog.supportedMode(cpuPng).isEmpty());
        assertTrue(catalog.unavailableReason(cpuPng).contains("does not match declared owner"));
        assertTrue(catalog.supportedMode(gpuDirty).isEmpty());
        assertTrue(catalog.unavailableReason(gpuDirty).contains("dirty-raw-rgba contract"));
        assertFalse(catalog.defaultSelection().equals(gpuDirty),
                "an invalid tuple must not become an implicit fallback");
    }

    @Test
    void subscribeRequestCarriesTheAtomicTupleAndACompletePresentationGeneration() {
        TerminalPresentationMode gpuDirty = mode("rust-gpu-slug", "dirty-raw-rgba");

        TerminalRasterSubscribeRequest first = SFMVoxTerminalPresentationAdapter.subscribeRequest(
                "session-a", gpuDirty, "presentation-41", 1024, 8, 7, "correlation-a");
        TerminalRasterSubscribeRequest replacement = SFMVoxTerminalPresentationAdapter.subscribeRequest(
                "session-a", gpuDirty, "presentation-42", 1024, 8, 8, "correlation-b");

        assertEquals("rust-gpu-slug", first.requestedRendererId());
        assertEquals("dirty", first.requestedDamageModeId());
        assertEquals("dirty-raw-rgba", first.requestedTransportId());
        assertEquals(1, first.requestedTransportVersion());
        assertEquals("presentation-41", first.presentationGeneration());
        assertEquals("presentation-42", replacement.presentationGeneration());
        assertFalse(first.presentationGeneration().equals(replacement.presentationGeneration()));
    }

    private static TerminalPresentationMode mode(String renderer, String transport) {
        boolean png = transport.equals("full-png");
        boolean dirty = transport.equals("dirty-raw-rgba");
        return new TerminalPresentationMode(
                renderer,
                TerminalRasterizationOwner.SERVER,
                dirty ? "dirty" : "full",
                transport,
                1,
                png ? TerminalFrameEncoding.PNG : TerminalFrameEncoding.RGBA8,
                dirty ? TerminalRasterFrameKind.DIRTY_REGIONS : TerminalRasterFrameKind.FULL,
                1,
                TerminalFrameOrigin.TOP_LEFT,
                TerminalAlphaMode.STRAIGHT,
                TerminalColorSpace.SRGB,
                1280,
                720,
                4 * 1024 * 1024L,
                64);
    }

    private static SFMTerminalPresentationAdvertisedMode advertised(
            String renderer,
            String owner,
            String transport) {
        boolean png = transport.equals("full-png");
        boolean dirty = transport.equals("dirty-raw-rgba");
        return new SFMTerminalPresentationAdvertisedMode(
                renderer,
                owner,
                dirty ? "dirty" : "full",
                transport,
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
}
