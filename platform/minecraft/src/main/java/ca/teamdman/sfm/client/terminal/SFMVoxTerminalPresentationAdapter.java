package ca.teamdman.sfm.client.terminal;

import org.facet.vox.generated.TerminalAlphaMode;
import org.facet.vox.generated.TerminalColorSpace;
import org.facet.vox.generated.TerminalError;
import org.facet.vox.generated.TerminalErrorCode;
import org.facet.vox.generated.TerminalFrameEncoding;
import org.facet.vox.generated.TerminalFrameOrigin;
import org.facet.vox.generated.TerminalPresentationCapabilitiesResult;
import org.facet.vox.generated.TerminalPresentationMode;
import org.facet.vox.generated.TerminalRasterFrameEvent;
import org.facet.vox.generated.TerminalRasterFrameKind;
import org.facet.vox.generated.TerminalRasterizationOwner;
import org.facet.vox.generated.TerminalRasterSubscribeRequest;

import java.util.List;

/**
 * Converts the generated Vox presentation contract into SFM's panel-local
 * renderer/transport model without inferring ownership from renderer names.
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
        List<SFMTerminalPresentationUnavailable> unavailablePresentations =
                result.unavailablePresentations().stream()
                        .map(SFMVoxTerminalPresentationAdapter::unavailablePresentation)
                        .toList();
        return SFMTerminalPresentationCatalog.intersect(
                result.defaultRendererId(),
                result.defaultTransportId(),
                modes,
                unavailablePresentations);
    }

    static SFMTerminalPresentationAdvertisedMode advertisedMode(TerminalPresentationMode mode) {
        return new SFMTerminalPresentationAdvertisedMode(
                mode.rendererId(),
                rasterizationOwner(mode.rasterizationOwner()).wireId(),
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

    static SFMTerminalPresentationUnavailable unavailablePresentation(
            org.facet.vox.generated.TerminalPresentationUnavailable unavailable
    ) {
        return new SFMTerminalPresentationUnavailable(
                unavailable.rendererId(),
                rasterizationOwner(unavailable.rasterizationOwner()),
                unavailable.damageModeId(),
                unavailable.transportId(),
                unavailable.transportVersion(),
                terminalError(unavailable.error()));
    }

    static SFMTerminalError terminalError(TerminalError error) {
        return new SFMTerminalError(
                terminalErrorCode(error.code()),
                error.message(),
                error.retryable(),
                error.serverSequence());
    }

    private static SFMTerminalErrorCode terminalErrorCode(TerminalErrorCode code) {
        return switch (code) {
            case INVALID_REQUEST -> SFMTerminalErrorCode.INVALID_REQUEST;
            case UNSUPPORTED_CAPABILITY -> SFMTerminalErrorCode.UNSUPPORTED_CAPABILITY;
            case SESSION_NOT_FOUND -> SFMTerminalErrorCode.SESSION_NOT_FOUND;
            case CAPACITY_EXCEEDED -> SFMTerminalErrorCode.CAPACITY_EXCEEDED;
            case CANCELLED -> SFMTerminalErrorCode.CANCELLED;
            case DISCONNECTED -> SFMTerminalErrorCode.DISCONNECTED;
            case INTERNAL -> SFMTerminalErrorCode.INTERNAL;
            default -> throw new IllegalArgumentException(
                    "Unsupported terminal error code: " + code);
        };
    }

    static String presentationGeneration(TerminalRasterFrameEvent event) {
        return event.presentationGeneration();
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

    private static SFMTerminalRasterizationOwner rasterizationOwner(
            TerminalRasterizationOwner owner
    ) {
        return switch (owner) {
            case SERVER -> SFMTerminalRasterizationOwner.SERVER;
            case CLIENT -> SFMTerminalRasterizationOwner.CLIENT;
            default -> throw new IllegalArgumentException(
                    "Unsupported terminal rasterization owner: " + owner);
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
