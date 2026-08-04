package ca.teamdman.sfm.client.terminal;

import org.facet.vox.generated.TerminalAlphaMode;
import org.facet.vox.generated.TerminalColorSpace;
import org.facet.vox.generated.TerminalFrameEncoding;
import org.facet.vox.generated.TerminalFrameOrigin;
import org.facet.vox.generated.TerminalPresentationCapabilitiesResult;
import org.facet.vox.generated.TerminalPresentationMode;
import org.facet.vox.generated.TerminalRasterFrameEvent;
import org.facet.vox.generated.TerminalRasterFrameKind;
import org.facet.vox.generated.TerminalRasterSubscribeRequest;

import java.util.List;

/**
 * The only compatibility seam for the pre-migration generated Vox raster
 * bindings. Replace the two legacy defaults/generation accessors here when
 * Facet supplies default_renderer_id, rasterization_owner, and
 * presentation_generation; the rest of the Java model remains unchanged.
 */
final class SFMVoxTerminalPresentationAdapter {
    private SFMVoxTerminalPresentationAdapter() {
    }

    static SFMTerminalPresentationCatalog catalog(
            TerminalPresentationCapabilitiesResult result
    ) {
        List<SFMTerminalPresentationAdvertisedMode> modes = result.modes().stream()
                .map(SFMVoxTerminalPresentationAdapter::advertisedMode)
                .toList();
        // The legacy method is specifically subscribe_raster_frames, so its
        // producer is explicitly server-owned. This is adapter context, not an
        // inference from renderer-id spelling.
        return SFMTerminalPresentationCatalog.intersect(
                SFMTerminalRendererId.RUST_CPU_FONTDUE.wireId(),
                result.defaultTransportId(),
                modes);
    }

    static SFMTerminalPresentationAdvertisedMode advertisedMode(TerminalPresentationMode mode) {
        return new SFMTerminalPresentationAdvertisedMode(
                mode.rendererId(),
                SFMTerminalRasterizationOwner.SERVER.wireId(),
                mode.damageModeId(),
                mode.transportId(),
                mode.transportVersion(),
                encoding(mode.encoding()),
                frameKind(mode.steadyFrameKind()),
                mode.frameContractVersion(),
                origin(mode.origin()),
                alphaMode(mode.alphaMode()),
                colorSpace(mode.colorSpace()),
                mode.maxPixelWidth(),
                mode.maxPixelHeight(),
                mode.maxFrameBytes(),
                mode.maxRegions());
    }

    static String presentationGeneration(TerminalRasterFrameEvent event) {
        return event.transportGeneration();
    }

    static TerminalRasterSubscribeRequest subscribeRequest(
            String sessionId,
            TerminalPresentationMode mode,
            String presentationGeneration,
            long maxFrameBytes,
            int maxRegions,
            long requestSequence,
            String correlationId
    ) {
        return new TerminalRasterSubscribeRequest(
                sessionId,
                mode.rendererId(),
                mode.damageModeId(),
                mode.transportId(),
                mode.transportVersion(),
                presentationGeneration,
                0,
                0,
                maxFrameBytes,
                maxRegions,
                requestSequence,
                correlationId);
    }

    private static SFMTerminalRasterEncoding encoding(TerminalFrameEncoding encoding) {
        return switch (encoding) {
            case PNG -> SFMTerminalRasterEncoding.PNG;
            case RGBA8 -> SFMTerminalRasterEncoding.RGBA8;
            default -> throw new IllegalArgumentException(
                    "Unsupported terminal frame encoding: " + encoding);
        };
    }

    private static SFMTerminalRasterFrameKind frameKind(TerminalRasterFrameKind kind) {
        return switch (kind) {
            case FULL -> SFMTerminalRasterFrameKind.FULL;
            case DIRTY_REGIONS -> SFMTerminalRasterFrameKind.DIRTY_REGIONS;
            default -> throw new IllegalArgumentException(
                    "Unsupported terminal raster frame kind: " + kind);
        };
    }

    private static SFMTerminalRasterOrigin origin(TerminalFrameOrigin origin) {
        return switch (origin) {
            case TOP_LEFT -> SFMTerminalRasterOrigin.TOP_LEFT;
            default -> throw new IllegalArgumentException(
                    "Unsupported terminal frame origin: " + origin);
        };
    }

    private static SFMTerminalRasterAlphaMode alphaMode(TerminalAlphaMode alphaMode) {
        return switch (alphaMode) {
            case STRAIGHT -> SFMTerminalRasterAlphaMode.STRAIGHT;
            default -> throw new IllegalArgumentException(
                    "Unsupported terminal alpha mode: " + alphaMode);
        };
    }

    private static SFMTerminalRasterColorSpace colorSpace(TerminalColorSpace colorSpace) {
        return switch (colorSpace) {
            case SRGB -> SFMTerminalRasterColorSpace.SRGB;
            default -> throw new IllegalArgumentException(
                    "Unsupported terminal color space: " + colorSpace);
        };
    }
}
