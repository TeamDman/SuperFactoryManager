package ca.teamdman.sfm.client.terminal;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Test-only, single-sample probe for the live Rust terminal presentation.
 *
 * <p>The production panel intentionally does not expose its renderer objects.
 * Keeping this reflective bridge in the gametest source set lets the puppet
 * prove the uploaded native image without widening the gameplay API. The
 * presentation-handoff implementation must include {@code logical_columns},
 * {@code logical_rows}, {@code panel_width}, {@code panel_height},
 * {@code native_width}, {@code native_height}, {@code cell_width},
 * {@code cell_height}, and {@code font_pixel_size} in its typed push evidence.</p>
 */
public final class SFMTerminalPresentationPuppetProbe {
    private static final Field REMOTE_SERVICE = field(SFMTerminalPanel.class, "remoteService");
    private static final Field PNG_RENDERER = field(SFMTerminalPanel.class, "pngRenderer");
    private static final Field RGBA_RENDERER = field(SFMTerminalPanel.class, "rgbaRenderer");
    private static final Field PNG_IMAGE_WIDTH = field(SFMTerminalPngRenderer.class, "imageWidth");
    private static final Field PNG_IMAGE_HEIGHT = field(SFMTerminalPngRenderer.class, "imageHeight");
    private static final Field PNG_SEQUENCE = field(SFMTerminalPngRenderer.class, "sequence");
    private static final Field PNG_TEXTURE = field(SFMTerminalPngRenderer.class, "texture");
    private static final Field RGBA_IMAGE_WIDTH = field(SFMTerminalRgbaRenderer.class, "imageWidth");
    private static final Field RGBA_IMAGE_HEIGHT = field(SFMTerminalRgbaRenderer.class, "imageHeight");
    private static final Field RGBA_SEQUENCE = field(SFMTerminalRgbaRenderer.class, "sequence");
    private static final Field RGBA_TEXTURE = field(SFMTerminalRgbaRenderer.class, "texture");

    private SFMTerminalPresentationPuppetProbe() {
    }

    public record RendererObservation(
            String deviceIdentity,
            String shaderIdentity,
            boolean gpuStagesPresent,
            boolean fullRgbaPackPresent,
            boolean dirtyRegionPackPresent,
            boolean rawPacketPackPresent,
            boolean pngPacketPackPresent,
            long requestedPixels,
            long readbackBytes,
            long targetAllocationId,
            long geometryHits,
            long geometryMisses,
            long targetCapacity,
            long retainedTargets,
            long targetAllocations,
            long targetReuses,
            long fontRendererCacheHits,
            long fontRendererCacheMisses,
            long glyphCacheHits,
            long glyphCacheMisses,
            long frameBufferGrows,
            long frameBufferReuses,
            long pngBufferGrows,
            long pngBufferReuses,
            long transportPayloadCopies
    ) {
        private String artifact() {
            return String.join("\n",
                    "renderer_device_identity=" + deviceIdentity,
                    "renderer_shader_identity=" + shaderIdentity,
                    "renderer_gpu_stages_present=" + gpuStagesPresent,
                    "renderer_full_rgba_pack_present=" + fullRgbaPackPresent,
                    "renderer_dirty_region_pack_present=" + dirtyRegionPackPresent,
                    "renderer_raw_packet_pack_present=" + rawPacketPackPresent,
                    "renderer_png_packet_pack_present=" + pngPacketPackPresent,
                    "renderer_requested_pixels=" + requestedPixels,
                    "renderer_readback_bytes=" + readbackBytes,
                    "renderer_target_allocation_id=" + targetAllocationId,
                    "renderer_geometry_hits=" + geometryHits,
                    "renderer_geometry_misses=" + geometryMisses,
                    "renderer_target_capacity=" + targetCapacity,
                    "renderer_retained_targets=" + retainedTargets,
                    "renderer_target_allocations=" + targetAllocations,
                    "renderer_target_reuses=" + targetReuses,
                    "renderer_font_renderer_cache_hits=" + fontRendererCacheHits,
                    "renderer_font_renderer_cache_misses=" + fontRendererCacheMisses,
                    "renderer_glyph_cache_hits=" + glyphCacheHits,
                    "renderer_glyph_cache_misses=" + glyphCacheMisses,
                    "renderer_frame_buffer_grows=" + frameBufferGrows,
                    "renderer_frame_buffer_reuses=" + frameBufferReuses,
                    "renderer_png_buffer_grows=" + pngBufferGrows,
                    "renderer_png_buffer_reuses=" + pngBufferReuses,
                    "renderer_transport_payload_copies=" + transportPayloadCopies);
        }
    }

    public record Observation(
            String rasterizationOwner,
            String requestedRenderer,
            String activeRenderer,
            String requestedTransport,
            String activeTransport,
            String sessionId,
            String connectionEpoch,
            String sessionEpoch,
            String damageModeId,
            int transportVersion,
            int frameContractVersion,
            String presentationGeneration,
            long fullResyncFrames,
            long terminalSequence,
            long latestFrameSequence,
            long latestBaseFrameSequence,
            boolean latestFullResync,
            String latestFrameKind,
            int latestPayloadBytes,
            int latestDirtyRegions,
            long presentedFrameSequence,
            int targetPanelWidth,
            int targetPanelHeight,
            int nativeWidth,
            int nativeHeight,
            int logicalColumns,
            int logicalRows,
            int cellWidth,
            int cellHeight,
            int fontPixelSize,
            RendererObservation rendererTelemetry,
            String content,
            String servicePushEvidence
    ) {
        public String artifact() {
            return String.join("\n",
                    "schema=sfm-terminal-presentation-puppet-v1",
                    "sample_model=single-pushed-state-no-polling",
                    "rasterization_owner=" + rasterizationOwner,
                    "requested_renderer=" + requestedRenderer,
                    "active_renderer=" + activeRenderer,
                    "requested_transport=" + requestedTransport,
                    "active_transport=" + activeTransport,
                    "session_id=" + sessionId,
                    "connection_epoch=" + connectionEpoch,
                    "session_epoch=" + sessionEpoch,
                    "damage_mode_id=" + damageModeId,
                    "transport_version=" + transportVersion,
                    "frame_contract_version=" + frameContractVersion,
                    "presentation_generation=" + presentationGeneration,
                    "full_resync_frames=" + fullResyncFrames,
                    "terminal_sequence=" + terminalSequence,
                    "latest_frame_sequence=" + latestFrameSequence,
                    "latest_base_frame_sequence=" + latestBaseFrameSequence,
                    "latest_full_resync=" + latestFullResync,
                    "latest_frame_kind=" + latestFrameKind,
                    "latest_payload_bytes=" + latestPayloadBytes,
                    "latest_dirty_regions=" + latestDirtyRegions,
                    "presented_frame_sequence=" + presentedFrameSequence,
                    "target_panel_width=" + targetPanelWidth,
                    "target_panel_height=" + targetPanelHeight,
                    "native_width=" + nativeWidth,
                    "native_height=" + nativeHeight,
                    "logical_columns=" + logicalColumns,
                    "logical_rows=" + logicalRows,
                    "cell_width=" + cellWidth,
                    "cell_height=" + cellHeight,
                    "font_pixel_size=" + fontPixelSize,
                    rendererTelemetry.artifact(),
                    "grid_native_width=" + (long) logicalColumns * cellWidth,
                    "grid_native_height=" + (long) logicalRows * cellHeight,
                    "native_within_target=true",
                    "java_presenter_native_match=true",
                    "java_upscale=false",
                    "content_utf8_bytes=" + content.getBytes(StandardCharsets.UTF_8).length,
                    "service_push_evidence_begin=true",
                    servicePushEvidence.stripTrailing(),
                    "service_push_evidence_end=true",
                    "");
        }
    }

    public static Observation observe(
            SFMTerminalPanel panel,
            String expectedRenderer,
            String expectedTransport,
            String requiredContentLine,
            int expectedPanelWidth,
            int expectedPanelHeight
    ) {
        SFMTerminalRemoteService service = remoteService(panel);
        SFMTerminalPresentationTransitionState state = service.presentationState();
        SFMTerminalPresentationSelection active = state.active().orElseThrow(() ->
                new IllegalStateException("Terminal presentation has no accepted active tuple"));
        if (state.pending()) {
            throw new IllegalStateException("Terminal presentation is still pending: requested="
                    + state.requested().label() + " active=" + active.label());
        }
        if (state.failure().isPresent()) {
            throw new IllegalStateException("Terminal presentation failed: " + state.failure().get());
        }
        requireEqual(expectedRenderer, state.requested().rendererId().wireId(), "requested renderer");
        requireEqual(expectedTransport, state.requested().transportId().wireId(), "requested transport");
        requireEqual(expectedRenderer, active.rendererId().wireId(), "active renderer");
        requireEqual(expectedTransport, active.transportId().wireId(), "active transport");

        SFMTerminalRendererOption renderer = service.rendererOptions().stream()
                .filter(option -> option.id().wireId().equals(expectedRenderer))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Active renderer is absent from the panel capability cache: " + expectedRenderer));
        if (!renderer.supported()) {
            throw new IllegalStateException("Active renderer is marked unavailable: "
                    + renderer.unavailableReason());
        }
        if (renderer.rasterizationOwner() != SFMTerminalRasterizationOwner.SERVER) {
            throw new IllegalStateException("Rust pixel renderer must be server-owned, got "
                    + renderer.rasterizationOwner().wireId());
        }

        String pushEvidence = panel.assertPushEvidenceForAutomation(false);
        Map<String, String> fields = parseEvidence(pushEvidence);
        requireEqual("vox.txrx.raster", required(fields, "transport"), "push transport");
        requireEqual(expectedRenderer, required(fields, "requested_renderer"),
                "push requested renderer");
        requireEqual(expectedRenderer, required(fields, "active_renderer"),
                "push active renderer");
        requireEqual(expectedTransport, required(fields, "requested_transport"),
                "push requested transport");
        requireEqual(expectedTransport, required(fields, "active_transport"),
                "push active transport");
        requireEqual(renderer.rasterizationOwner().wireId(), required(fields, "rasterization_owner"),
                "push rasterization owner");
        String sessionId = requiredNonBlank(fields, "session_id");
        String connectionEpoch = requiredNonBlank(fields, "connection_epoch");
        String sessionEpoch = requiredNonBlank(fields, "session_epoch");
        String damageModeId = requiredNonBlank(fields, "damage_mode_id");
        int transportVersion = positiveInt(fields, "transport_version");
        int frameContractVersion = positiveInt(fields, "frame_contract_version");
        String generation = required(fields, "presentation_generation");
        if (generation.isBlank()) {
            throw new IllegalStateException("Push evidence omitted the presentation generation");
        }
        long fullResyncFrames = positiveLong(fields, "raster_full_resync_frames");
        long terminalSequence = positiveLong(fields, "latest_terminal_sequence");
        long latestFrameSequence = positiveLong(fields, "latest_frame_sequence");
        long latestBaseFrameSequence = nonNegativeLong(fields, "latest_base_frame_sequence");
        boolean latestFullResync = booleanValue(fields, "latest_full_resync");
        String latestFrameKind = requiredNonBlank(fields, "latest_frame_kind");
        int latestPayloadBytes = positiveInt(fields, "latest_payload_bytes");
        int latestDirtyRegions = nonNegativeInt(fields, "latest_dirty_regions");
        int logicalColumns = positiveInt(fields, "logical_columns");
        int logicalRows = positiveInt(fields, "logical_rows");
        int targetPanelWidth = positiveInt(fields, "panel_width");
        int targetPanelHeight = positiveInt(fields, "panel_height");
        int nativeWidth = positiveInt(fields, "native_width");
        int nativeHeight = positiveInt(fields, "native_height");
        int cellWidth = positiveInt(fields, "cell_width");
        int cellHeight = positiveInt(fields, "cell_height");
        int fontPixelSize = positiveInt(fields, "font_pixel_size");
        RendererObservation rendererTelemetry = rendererObservation(
                fields,
                expectedRenderer,
                expectedTransport,
                nativeWidth,
                nativeHeight);
        if ((long) logicalColumns * cellWidth != nativeWidth
                || (long) logicalRows * cellHeight != nativeHeight) {
            throw new IllegalStateException("Native raster " + nativeWidth + "x" + nativeHeight
                    + " does not equal grid " + logicalColumns + "x" + logicalRows
                    + " times cell " + cellWidth + "x" + cellHeight);
        }
        if (nativeWidth > targetPanelWidth || nativeHeight > targetPanelHeight) {
            throw new IllegalStateException("Grid-native raster " + nativeWidth + "x" + nativeHeight
                    + " exceeds accepted panel target " + targetPanelWidth + "x" + targetPanelHeight);
        }
        if (targetPanelWidth != expectedPanelWidth || targetPanelHeight != expectedPanelHeight) {
            throw new IllegalStateException("Accepted panel target was " + targetPanelWidth + "x"
                    + targetPanelHeight + " instead of focused panel "
                    + expectedPanelWidth + "x" + expectedPanelHeight);
        }
        if (service.logicalWidth() != logicalColumns || service.logicalHeight() != logicalRows) {
            throw new IllegalStateException("Accepted grid does not match the panel's requested logical size");
        }

        PresenterState presenter = presenterState(panel, expectedTransport);
        if (presenter.texture() == null || presenter.width() <= 0 || presenter.height() <= 0
                || presenter.sequence() < 1) {
            throw new IllegalStateException("Selected Java pixel presenter has no uploaded native frame");
        }
        if (presenter.width() != nativeWidth || presenter.height() != nativeHeight) {
            throw new IllegalStateException("Java presenter uploaded " + presenter.width() + "x"
                    + presenter.height() + " instead of accepted native raster "
                    + nativeWidth + "x" + nativeHeight);
        }
        if (presenter.sequence() > latestFrameSequence) {
            throw new IllegalStateException("Presented frame sequence " + presenter.sequence()
                    + " exceeded the latest accepted sequence " + latestFrameSequence);
        }

        String content = panel.contentForAutomation();
        if (requiredContentLine != null && content.lines().map(String::strip)
                .noneMatch(requiredContentLine.strip()::equals)) {
            throw new IllegalStateException("Terminal pushed content omitted witness line '"
                    + requiredContentLine + "':\n" + content);
        }
        return new Observation(
                renderer.rasterizationOwner().wireId(),
                state.requested().rendererId().wireId(),
                active.rendererId().wireId(),
                state.requested().transportId().wireId(),
                active.transportId().wireId(),
                sessionId,
                connectionEpoch,
                sessionEpoch,
                damageModeId,
                transportVersion,
                frameContractVersion,
                generation,
                fullResyncFrames,
                terminalSequence,
                latestFrameSequence,
                latestBaseFrameSequence,
                latestFullResync,
                latestFrameKind,
                latestPayloadBytes,
                latestDirtyRegions,
                presenter.sequence(),
                targetPanelWidth,
                targetPanelHeight,
                nativeWidth,
                nativeHeight,
                logicalColumns,
                logicalRows,
                cellWidth,
                cellHeight,
                fontPixelSize,
                rendererTelemetry,
                content,
                pushEvidence
        );
    }

    private static RendererObservation rendererObservation(
            Map<String, String> fields,
            String expectedRenderer,
            String expectedTransport,
            int nativeWidth,
            int nativeHeight
    ) {
        String deviceIdentity = required(fields, "renderer_device_identity");
        String shaderIdentity = required(fields, "renderer_shader_identity");
        boolean gpuStagesPresent = booleanValue(fields, "renderer_gpu_stages_present");
        boolean fullRgbaPackPresent = booleanValue(fields, "renderer_full_rgba_pack_present");
        boolean dirtyRegionPackPresent = booleanValue(fields, "renderer_dirty_region_pack_present");
        boolean rawPacketPackPresent = booleanValue(fields, "renderer_raw_packet_pack_present");
        boolean pngPacketPackPresent = booleanValue(fields, "renderer_png_packet_pack_present");
        long requestedPixels = nonNegativeLong(fields, "renderer_requested_pixels");
        long readbackBytes = nonNegativeLong(fields, "renderer_readback_bytes");
        long targetAllocationId = nonNegativeLong(fields, "renderer_target_allocation_id");
        long geometryHits = nonNegativeLong(fields, "renderer_geometry_hits");
        long geometryMisses = nonNegativeLong(fields, "renderer_geometry_misses");
        long targetCapacity = nonNegativeLong(fields, "renderer_target_capacity");
        long retainedTargets = nonNegativeLong(fields, "renderer_retained_targets");
        long targetAllocations = nonNegativeLong(fields, "renderer_target_allocations");
        long targetReuses = nonNegativeLong(fields, "renderer_target_reuses");
        long fontRendererCacheHits = nonNegativeLong(fields, "renderer_font_renderer_cache_hits");
        long fontRendererCacheMisses = nonNegativeLong(fields, "renderer_font_renderer_cache_misses");
        long glyphCacheHits = nonNegativeLong(fields, "renderer_glyph_cache_hits");
        long glyphCacheMisses = nonNegativeLong(fields, "renderer_glyph_cache_misses");
        long frameBufferGrows = nonNegativeLong(fields, "renderer_frame_buffer_grows");
        long frameBufferReuses = nonNegativeLong(fields, "renderer_frame_buffer_reuses");
        long pngBufferGrows = nonNegativeLong(fields, "renderer_png_buffer_grows");
        long pngBufferReuses = nonNegativeLong(fields, "renderer_png_buffer_reuses");
        long transportPayloadCopies = nonNegativeLong(fields, "renderer_transport_payload_copies");

        boolean gpuExpected = SFMTerminalRendererId.RUST_GPU_SLUG.wireId().equals(expectedRenderer);
        if (gpuStagesPresent != gpuExpected) {
            throw new IllegalStateException("GPU-stage telemetry did not match renderer " + expectedRenderer);
        }
        if (gpuExpected) {
            if (deviceIdentity.isBlank() || shaderIdentity.isBlank()) {
                throw new IllegalStateException("GPU telemetry omitted device or shader identity");
            }
            long nativePixels = (long) nativeWidth * nativeHeight;
            boolean dirtyTransport = SFMTerminalTransportId.DIRTY_RAW_RGBA.wireId()
                    .equals(expectedTransport);
            if (requestedPixels <= 0 || requestedPixels > nativePixels
                    || !dirtyTransport && requestedPixels != nativePixels
                    || readbackBytes < requestedPixels * 4L) {
                throw new IllegalStateException("GPU requested/readback pixel telemetry did not match the native raster");
            }
            if (targetAllocationId <= 0 || targetCapacity <= 0 || retainedTargets <= 0
                    || retainedTargets > targetCapacity || targetAllocations + targetReuses <= 0) {
                throw new IllegalStateException("GPU target allocation/reuse telemetry was incoherent");
            }
        } else if (!deviceIdentity.isBlank() || !shaderIdentity.isBlank()) {
            throw new IllegalStateException("CPU telemetry unexpectedly included GPU identities");
        }
        boolean pngExpected = SFMTerminalTransportId.FULL_PNG.wireId().equals(expectedTransport);
        if (pngPacketPackPresent != pngExpected || rawPacketPackPresent == pngExpected) {
            throw new IllegalStateException("Packet-stage telemetry did not match transport " + expectedTransport);
        }

        return new RendererObservation(
                deviceIdentity,
                shaderIdentity,
                gpuStagesPresent,
                fullRgbaPackPresent,
                dirtyRegionPackPresent,
                rawPacketPackPresent,
                pngPacketPackPresent,
                requestedPixels,
                readbackBytes,
                targetAllocationId,
                geometryHits,
                geometryMisses,
                targetCapacity,
                retainedTargets,
                targetAllocations,
                targetReuses,
                fontRendererCacheHits,
                fontRendererCacheMisses,
                glyphCacheHits,
                glyphCacheMisses,
                frameBufferGrows,
                frameBufferReuses,
                pngBufferGrows,
                pngBufferReuses,
                transportPayloadCopies);
    }

    private static SFMTerminalRemoteService remoteService(SFMTerminalPanel panel) {
        Object value = read(REMOTE_SERVICE, panel);
        if (!(value instanceof SFMTerminalRemoteService service)) {
            throw new IllegalStateException("Focused panel is not backed by a Rust remote service");
        }
        return service;
    }

    private static PresenterState presenterState(SFMTerminalPanel panel, String transportId) {
        boolean png = SFMTerminalTransportId.FULL_PNG.wireId().equals(transportId);
        Object presenter = read(png ? PNG_RENDERER : RGBA_RENDERER, panel);
        return new PresenterState(
                readInt(png ? PNG_IMAGE_WIDTH : RGBA_IMAGE_WIDTH, presenter),
                readInt(png ? PNG_IMAGE_HEIGHT : RGBA_IMAGE_HEIGHT, presenter),
                readLong(png ? PNG_SEQUENCE : RGBA_SEQUENCE, presenter),
                read(png ? PNG_TEXTURE : RGBA_TEXTURE, presenter));
    }

    private static Map<String, String> parseEvidence(String evidence) {
        Map<String, String> result = new LinkedHashMap<>();
        for (String line : evidence.lines().toList()) {
            if (line.isBlank()) continue;
            int separator = line.indexOf('=');
            if (separator <= 0) {
                throw new IllegalStateException("Malformed terminal push evidence line: " + line);
            }
            String key = line.substring(0, separator);
            String value = line.substring(separator + 1);
            if (result.putIfAbsent(key, value) != null) {
                throw new IllegalStateException("Duplicate terminal push evidence field: " + key);
            }
        }
        return result;
    }

    private static String required(Map<String, String> fields, String name) {
        String value = fields.get(name);
        if (value == null) throw new IllegalStateException("Terminal push evidence omitted " + name);
        return value;
    }

    private static String requiredNonBlank(Map<String, String> fields, String name) {
        String value = required(fields, name);
        if (value.isBlank()) throw new IllegalStateException(name + " must not be blank");
        return value;
    }

    private static long positiveLong(Map<String, String> fields, String name) {
        try {
            long value = Long.parseLong(required(fields, name));
            if (value < 1) throw new IllegalStateException(name + " must be positive, got " + value);
            return value;
        } catch (NumberFormatException error) {
            throw new IllegalStateException(name + " was not an integer", error);
        }
    }

    private static int positiveInt(Map<String, String> fields, String name) {
        long value = positiveLong(fields, name);
        if (value > Integer.MAX_VALUE) {
            throw new IllegalStateException(name + " exceeds the Java integer range: " + value);
        }
        return (int) value;
    }

    private static long nonNegativeLong(Map<String, String> fields, String name) {
        try {
            long value = Long.parseLong(required(fields, name));
            if (value < 0) throw new IllegalStateException(name + " must not be negative, got " + value);
            return value;
        } catch (NumberFormatException error) {
            throw new IllegalStateException(name + " was not an integer", error);
        }
    }

    private static int nonNegativeInt(Map<String, String> fields, String name) {
        long value = nonNegativeLong(fields, name);
        if (value > Integer.MAX_VALUE) {
            throw new IllegalStateException(name + " exceeds the Java integer range: " + value);
        }
        return (int) value;
    }

    private static boolean booleanValue(Map<String, String> fields, String name) {
        return switch (required(fields, name)) {
            case "true" -> true;
            case "false" -> false;
            default -> throw new IllegalStateException(name + " was not a boolean");
        };
    }

    private static void requireEqual(String expected, String actual, String name) {
        if (!expected.equals(actual)) {
            throw new IllegalStateException(name + " was '" + actual + "' instead of '" + expected + "'");
        }
    }

    private static Field field(Class<?> owner, String name) {
        try {
            Field field = owner.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException error) {
            throw new ExceptionInInitializerError(error);
        }
    }

    private static Object read(Field field, Object owner) {
        try {
            return field.get(owner);
        } catch (IllegalAccessException error) {
            throw new IllegalStateException("Could not inspect terminal puppet state", error);
        }
    }

    private static int readInt(Field field, Object owner) {
        try {
            return field.getInt(owner);
        } catch (IllegalAccessException error) {
            throw new IllegalStateException("Could not inspect terminal puppet dimensions", error);
        }
    }

    private static long readLong(Field field, Object owner) {
        try {
            return field.getLong(owner);
        } catch (IllegalAccessException error) {
            throw new IllegalStateException("Could not inspect terminal puppet sequence", error);
        }
    }

    private record PresenterState(int width, int height, long sequence, Object texture) {
    }
}
