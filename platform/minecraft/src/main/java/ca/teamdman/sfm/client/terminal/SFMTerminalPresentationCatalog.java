package ca.teamdman.sfm.client.terminal;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable, bounded intersection of advertised terminal presentation modes
 * with the Java pixel presenters available in this Minecraft client.
 */
public final class SFMTerminalPresentationCatalog {
    public record Rejection(
            String rendererId,
            String rasterizationOwner,
            String damageModeId,
            String transportId,
            int transportVersion,
            String reason
    ) {
        public Rejection {
            rendererId = rendererId == null ? "" : rendererId;
            rasterizationOwner = rasterizationOwner == null ? "" : rasterizationOwner;
            damageModeId = damageModeId == null ? "" : damageModeId;
            transportId = transportId == null ? "" : transportId;
            reason = Objects.requireNonNull(reason, "reason");
        }
    }

    private final boolean discovered;
    private final SFMTerminalPresentationSelection defaultSelection;
    private final List<SFMTerminalPresentationModeOption> modes;
    private final List<Rejection> rejections;
    private final List<SFMTerminalPresentationUnavailable> unavailablePresentations;

    private SFMTerminalPresentationCatalog(
            boolean discovered,
            SFMTerminalPresentationSelection defaultSelection,
            List<SFMTerminalPresentationModeOption> modes,
            List<Rejection> rejections,
            List<SFMTerminalPresentationUnavailable> unavailablePresentations
    ) {
        this.discovered = discovered;
        this.defaultSelection = Objects.requireNonNull(defaultSelection, "defaultSelection");
        this.modes = List.copyOf(modes);
        this.rejections = List.copyOf(rejections);
        this.unavailablePresentations = List.copyOf(unavailablePresentations);
    }

    public static SFMTerminalPresentationCatalog undiscovered() {
        return new SFMTerminalPresentationCatalog(
                false, SFMTerminalPresentationSelection.DEFAULT, List.of(), List.of(), List.of());
    }

    public static SFMTerminalPresentationCatalog intersect(
            String defaultRendererId,
            String defaultTransportId,
            List<SFMTerminalPresentationAdvertisedMode> advertisedModes
    ) {
        return intersect(defaultRendererId, defaultTransportId, advertisedModes, List.of());
    }

    public static SFMTerminalPresentationCatalog intersect(
            String defaultRendererId,
            String defaultTransportId,
            List<SFMTerminalPresentationAdvertisedMode> advertisedModes,
            List<SFMTerminalPresentationUnavailable> unavailablePresentations
    ) {
        Objects.requireNonNull(advertisedModes, "advertisedModes");
        Objects.requireNonNull(unavailablePresentations, "unavailablePresentations");
        SFMTerminalPresentationSelection defaultSelection = new SFMTerminalPresentationSelection(
                SFMTerminalRendererId.fromWireId(defaultRendererId),
                SFMTerminalTransportId.fromWireId(defaultTransportId));
        List<SFMTerminalPresentationModeOption> modes = new ArrayList<>();
        List<Rejection> rejections = new ArrayList<>();
        for (SFMTerminalPresentationAdvertisedMode advertised : advertisedModes) {
            SFMTerminalRendererId renderer;
            SFMTerminalRasterizationOwner owner;
            SFMTerminalTransportId transport;
            try {
                renderer = SFMTerminalRendererId.fromWireId(advertised.rendererId());
                owner = SFMTerminalRasterizationOwner.fromWireId(advertised.rasterizationOwner());
                transport = SFMTerminalTransportId.fromWireId(advertised.transportId());
            } catch (IllegalArgumentException error) {
                rejections.add(new Rejection(
                        advertised.rendererId(), advertised.rasterizationOwner(),
                        advertised.damageModeId(), advertised.transportId(),
                        advertised.transportVersion(), error.getMessage()));
                continue;
            }
            SFMTerminalPresentationTuple tuple;
            try {
                tuple = new SFMTerminalPresentationTuple(
                        renderer, owner, transport, advertised.damageModeId(),
                        advertised.transportVersion());
            } catch (IllegalArgumentException error) {
                rejections.add(new Rejection(
                        advertised.rendererId(), advertised.rasterizationOwner(),
                        advertised.damageModeId(), advertised.transportId(),
                        advertised.transportVersion(), error.getMessage()));
                continue;
            }
            String reason = unsupportedReason(advertised, tuple);
            if (reason != null) {
                rejections.add(new Rejection(
                        advertised.rendererId(),
                        advertised.rasterizationOwner(),
                        advertised.damageModeId(),
                        advertised.transportId(),
                        advertised.transportVersion(),
                        reason));
                continue;
            }
            modes.add(new SFMTerminalPresentationModeOption(
                    tuple,
                    advertised.encoding(),
                    advertised.steadyFrameKind(),
                    advertised.frameContractVersion(),
                    advertised.maxPixelWidth(),
                    advertised.maxPixelHeight(),
                    advertised.maxFrameBytes(),
                    advertised.maxRegions(),
                    true,
                    ""));
        }
        return new SFMTerminalPresentationCatalog(
                true, defaultSelection, modes, rejections, unavailablePresentations);
    }

    private static String unsupportedReason(
            SFMTerminalPresentationAdvertisedMode advertised,
            SFMTerminalPresentationTuple tuple
    ) {
        if (tuple.rasterizationOwner() != tuple.rendererId().rasterizationOwner()) {
            return "renderer owner " + tuple.rasterizationOwner().wireId()
                    + " does not match declared owner "
                    + tuple.rendererId().rasterizationOwner().wireId();
        }
        if (tuple.rasterizationOwner() != SFMTerminalRasterizationOwner.SERVER) {
            return "pixel transports require server-owned rasterization";
        }
        if (tuple.transportVersion() != 1) return "unsupported transport version";
        if (advertised.frameContractVersion() != 1) return "unsupported frame contract version";
        if (advertised.origin() != SFMTerminalRasterOrigin.TOP_LEFT) return "unsupported frame origin";
        if (advertised.alphaMode() != SFMTerminalRasterAlphaMode.STRAIGHT) {
            return "unsupported alpha mode";
        }
        if (advertised.colorSpace() != SFMTerminalRasterColorSpace.SRGB) {
            return "unsupported color space";
        }
        if (advertised.maxPixelWidth() <= 0 || advertised.maxPixelWidth() > 4096
                || advertised.maxPixelHeight() <= 0 || advertised.maxPixelHeight() > 4096
                || advertised.maxFrameBytes() <= 0 || advertised.maxRegions() <= 0) {
            return "advertised raster bounds are outside Java presenter limits";
        }
        return switch (tuple.transportId()) {
            case FULL_PNG -> "full".equals(tuple.damageModeId())
                    && advertised.encoding() == SFMTerminalRasterEncoding.PNG
                    && advertised.steadyFrameKind() == SFMTerminalRasterFrameKind.FULL
                    ? null : "full-png contract does not match the Java PNG presenter";
            case FULL_RAW_RGBA -> "full".equals(tuple.damageModeId())
                    && advertised.encoding() == SFMTerminalRasterEncoding.RGBA8
                    && advertised.steadyFrameKind() == SFMTerminalRasterFrameKind.FULL
                    ? null : "full-raw-rgba contract does not match the Java RGBA presenter";
            case DIRTY_RAW_RGBA -> "dirty".equals(tuple.damageModeId())
                    && advertised.encoding() == SFMTerminalRasterEncoding.RGBA8
                    && advertised.steadyFrameKind() == SFMTerminalRasterFrameKind.DIRTY_REGIONS
                    ? null : "dirty-raw-rgba contract does not match the Java RGBA presenter";
        };
    }

    public boolean discovered() {
        return discovered;
    }

    public SFMTerminalPresentationSelection defaultSelection() {
        return defaultSelection;
    }

    public List<SFMTerminalPresentationModeOption> modes() {
        return modes;
    }

    public List<Rejection> rejections() {
        return rejections;
    }

    public List<SFMTerminalPresentationUnavailable> unavailablePresentations() {
        return unavailablePresentations;
    }

    public Optional<SFMTerminalPresentationModeOption> supportedMode(
            SFMTerminalPresentationSelection selection
    ) {
        return modes.stream()
                .filter(SFMTerminalPresentationModeOption::supported)
                .filter(mode -> mode.tuple().selection().equals(selection))
                .findFirst();
    }

    public List<SFMTerminalRendererOption> rendererOptions(SFMTerminalTransportId transport) {
        return Arrays.stream(SFMTerminalRendererId.values())
                .map(renderer -> {
                    SFMTerminalPresentationSelection selection =
                            new SFMTerminalPresentationSelection(renderer, transport);
                    boolean supported = supportedMode(selection).isPresent();
                    return new SFMTerminalRendererOption(
                            renderer,
                            renderer.rasterizationOwner(),
                            supported,
                            supported ? "" : unavailableReason(selection));
                })
                .toList();
    }

    public List<SFMTerminalTransportOption> transportOptions(SFMTerminalRendererId renderer) {
        return Arrays.stream(SFMTerminalTransportId.values())
                .map(transport -> {
                    SFMTerminalPresentationSelection selection =
                            new SFMTerminalPresentationSelection(renderer, transport);
                    boolean supported = supportedMode(selection).isPresent();
                    return new SFMTerminalTransportOption(
                            transport,
                            supported,
                            supported ? "" : unavailableReason(selection));
                })
                .toList();
    }

    public String unavailableReason(SFMTerminalPresentationSelection selection) {
        if (!discovered) return "awaiting server capabilities";
        if (supportedMode(selection).isPresent()) return "";
        Optional<SFMTerminalPresentationUnavailable> unavailable = unavailablePresentations.stream()
                .filter(candidate -> exactIdentityMatches(candidate, selection))
                .findFirst();
        if (unavailable.isPresent()) return unavailable.get().error().message();
        Optional<Rejection> rejected = rejections.stream()
                .filter(rejection -> rejection.rendererId().equals(selection.rendererId().wireId()))
                .filter(rejection -> rejection.transportId().equals(selection.transportId().wireId()))
                .findFirst();
        if (rejected.isPresent()) return rejected.get().reason();
        return "combination was not advertised by the server";
    }

    private static boolean exactIdentityMatches(
            SFMTerminalPresentationUnavailable unavailable,
            SFMTerminalPresentationSelection selection
    ) {
        return exactIdentityMatches(
                unavailable.rendererId(),
                unavailable.rasterizationOwner().wireId(),
                unavailable.damageModeId(),
                unavailable.transportId(),
                unavailable.transportVersion(),
                selection);
    }

    private static boolean exactIdentityMatches(
            String rendererId,
            String rasterizationOwner,
            String damageModeId,
            String transportId,
            int transportVersion,
            SFMTerminalPresentationSelection selection
    ) {
        return rendererId.equals(selection.rendererId().wireId())
                && rasterizationOwner.equals(selection.rendererId().rasterizationOwner().wireId())
                && damageModeId.equals(selection.transportId().damageModeId())
                && transportId.equals(selection.transportId().wireId())
                && transportVersion == SFMTerminalTransportId.SUPPORTED_VERSION;
    }
}
