package ca.teamdman.sfm.client.terminal;

import org.facet.vox.generated.TerminalAlphaMode;
import org.facet.vox.generated.TerminalColorSpace;
import org.facet.vox.generated.TerminalError;
import org.facet.vox.generated.TerminalErrorCode;
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
    void generatedNegativeCapabilitiesRemainTypedDiagnosticOnlyExactTuples() {
        List<TerminalPresentationMode> cpuModes = List.of(
                mode("rust-cpu-fontdue", "full-png"),
                mode("rust-cpu-fontdue", "full-raw-rgba"),
                mode("rust-cpu-fontdue", "dirty-raw-rgba"));
        List<org.facet.vox.generated.TerminalPresentationUnavailable> unavailable = List.of(
                unavailable("full-png", "full", 1, "GPU PNG unavailable", false, 7),
                unavailable("full-raw-rgba", "full", 1, "GPU raw unavailable", true, 8),
                unavailable("dirty-raw-rgba", "dirty", 1, "GPU dirty unavailable", false, 9));
        TerminalPresentationCapabilitiesResult generated =
                new TerminalPresentationCapabilitiesResult(
                        "session-a",
                        "rust-cpu-fontdue",
                        "full-png",
                        cpuModes,
                        unavailable,
                        10);

        SFMTerminalPresentationCatalog catalog =
                SFMVoxTerminalPresentationAdapter.catalog(generated);

        assertEquals(3, catalog.modes().size());
        assertTrue(catalog.modes().stream().allMatch(SFMTerminalPresentationModeOption::supported));
        assertTrue(catalog.modes().stream().allMatch(mode ->
                mode.tuple().rendererId() == SFMTerminalRendererId.RUST_CPU_FONTDUE));
        assertEquals(3, catalog.unavailablePresentations().size());
        assertEquals(
                new SFMTerminalPresentationSelection(
                        SFMTerminalRendererId.RUST_CPU_FONTDUE,
                        SFMTerminalTransportId.FULL_PNG),
                catalog.defaultSelection());
        assertTrue(catalog.supportedMode(catalog.defaultSelection()).isPresent(),
                "the advertised default must remain a valid selectable CPU tuple");

        SFMTerminalPresentationUnavailable pngUnavailable =
                catalog.unavailablePresentations().get(0);
        assertEquals("rust-gpu-slug", pngUnavailable.rendererId());
        assertEquals(SFMTerminalRasterizationOwner.SERVER, pngUnavailable.rasterizationOwner());
        assertEquals("full", pngUnavailable.damageModeId());
        assertEquals("full-png", pngUnavailable.transportId());
        assertEquals(1, pngUnavailable.transportVersion());
        assertEquals(SFMTerminalErrorCode.UNSUPPORTED_CAPABILITY, pngUnavailable.error().code());
        assertEquals("GPU PNG unavailable", pngUnavailable.error().message());
        assertFalse(pngUnavailable.error().retryable());
        assertEquals(7, pngUnavailable.error().serverSequence());

        Map<SFMTerminalTransportId, String> expectedReasons = Map.of(
                SFMTerminalTransportId.FULL_PNG, "GPU PNG unavailable",
                SFMTerminalTransportId.FULL_RAW_RGBA, "GPU raw unavailable",
                SFMTerminalTransportId.DIRTY_RAW_RGBA, "GPU dirty unavailable");
        expectedReasons.forEach((transport, reason) -> {
            SFMTerminalPresentationSelection selection = new SFMTerminalPresentationSelection(
                    SFMTerminalRendererId.RUST_GPU_SLUG, transport);
            assertTrue(catalog.supportedMode(selection).isEmpty(),
                    "negative capabilities must never become selectable modes");
            assertEquals(reason, catalog.unavailableReason(selection));
            SFMTerminalRendererOption rendererOption = catalog.rendererOptions(transport).stream()
                    .filter(option -> option.id() == SFMTerminalRendererId.RUST_GPU_SLUG)
                    .findFirst()
                    .orElseThrow();
            assertFalse(rendererOption.supported());
            assertEquals(reason, rendererOption.unavailableReason());
            assertTrue(rendererOption.label().contains(reason));
        });
        assertTrue(catalog.transportOptions(SFMTerminalRendererId.RUST_GPU_SLUG).stream()
                .noneMatch(SFMTerminalTransportOption::supported));
        assertEquals(
                List.of("GPU PNG unavailable", "GPU raw unavailable", "GPU dirty unavailable"),
                catalog.transportOptions(SFMTerminalRendererId.RUST_GPU_SLUG).stream()
                        .map(SFMTerminalTransportOption::unavailableReason)
                        .toList());
    }

    @Test
    void negativeCapabilityLookupRequiresExactOwnerDamageAndTransportVersion() {
        List<SFMTerminalPresentationUnavailable> malformedNegatives = List.of(
                localUnavailable(
                        SFMTerminalRasterizationOwner.CLIENT,
                        "full",
                        "full-png",
                        1,
                        "wrong owner"),
                localUnavailable(
                        SFMTerminalRasterizationOwner.SERVER,
                        "dirty",
                        "full-raw-rgba",
                        1,
                        "wrong damage"),
                localUnavailable(
                        SFMTerminalRasterizationOwner.SERVER,
                        "dirty",
                        "dirty-raw-rgba",
                        2,
                        "wrong version"));
        SFMTerminalPresentationCatalog catalog = SFMTerminalPresentationCatalog.intersect(
                "rust-cpu-fontdue",
                "full-png",
                List.of(
                        advertised("rust-cpu-fontdue", "server", "full-png"),
                        advertised("rust-cpu-fontdue", "server", "full-raw-rgba"),
                        advertised("rust-cpu-fontdue", "server", "dirty-raw-rgba")),
                malformedNegatives);

        assertEquals(3, catalog.unavailablePresentations().size(),
                "malformed negative identities are retained exactly for diagnostics");
        for (SFMTerminalTransportId transport : SFMTerminalTransportId.values()) {
            SFMTerminalPresentationSelection selection = new SFMTerminalPresentationSelection(
                    SFMTerminalRendererId.RUST_GPU_SLUG, transport);
            assertEquals("combination was not advertised by the server",
                    catalog.unavailableReason(selection));
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

    private static org.facet.vox.generated.TerminalPresentationUnavailable unavailable(
            String transport,
            String damage,
            int version,
            String message,
            boolean retryable,
            long serverSequence
    ) {
        return new org.facet.vox.generated.TerminalPresentationUnavailable(
                "rust-gpu-slug",
                TerminalRasterizationOwner.SERVER,
                damage,
                transport,
                version,
                new TerminalError(
                        TerminalErrorCode.UNSUPPORTED_CAPABILITY,
                        message,
                        retryable,
                        serverSequence));
    }

    private static SFMTerminalPresentationUnavailable localUnavailable(
            SFMTerminalRasterizationOwner owner,
            String damage,
            String transport,
            int version,
            String message
    ) {
        return new SFMTerminalPresentationUnavailable(
                "rust-gpu-slug",
                owner,
                damage,
                transport,
                version,
                new SFMTerminalError(
                        SFMTerminalErrorCode.UNSUPPORTED_CAPABILITY,
                        message,
                        false,
                        7));
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
