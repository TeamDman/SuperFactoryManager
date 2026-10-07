package ca.teamdman.sfm.client.terminal;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Privacy-safe live evidence for renderer-neutral terminal interactions. */
public final class SFMTerminalInteractionPuppetProbe {
    private static final Field REMOTE_SERVICE = field(SFMTerminalPanel.class, "remoteService");

    private SFMTerminalInteractionPuppetProbe() {
    }

    public record TextRange(int startColumn, int endColumn, int row, int utf8Bytes) {
        public TextRange {
            if (startColumn < 0 || endColumn < startColumn || row < 0 || utf8Bytes < 1) {
                throw new IllegalArgumentException("Invalid terminal text range");
            }
        }
    }

    public record Point(double x, double y) {
    }

    public record Observation(
            String rendererId,
            String transportId,
            String sessionId,
            String connectionEpoch,
            String sessionEpoch,
            String presentationGeneration,
            long interactionSequence,
            Optional<SFMTerminalSelection> selection,
            List<SFMTerminalSelectionLayout.LogicalHighlight> highlights,
            long rasterFramesReceived,
            long rasterFramesAccepted,
            long rasterFullFrames,
            long rasterDirtyFrames,
            long latestTerminalSequence,
            long latestFrameSequence,
            long producerRendersStarted,
            long producerRendersCompleted,
            long producerFramesPushed,
            long rendererRequestedPixels,
            long rendererReadbackBytes,
            long rendererFrameBufferGrows,
            long rendererFrameBufferReuses,
            long rendererPngBufferGrows,
            long rendererPngBufferReuses,
            long rendererTransportPayloadCopies,
            long pngDecodeAttempts,
            long pngTextureUploads,
            long pngFramesPresented,
            long rgbaUploads,
            long rgbaBytesUploaded
    ) {
    }

    public static TextRange locateExactVisibleLine(SFMTerminalPanel panel, String exactText) {
        if (exactText == null || exactText.isEmpty() || exactText.indexOf('\n') >= 0
                || exactText.indexOf('\r') >= 0) {
            throw new IllegalArgumentException("Selection witness must be one non-empty line");
        }
        List<String> lines = panel.contentForAutomation().lines().toList();
        for (int row = lines.size() - 1; row >= 0; row--) {
            String line = lines.get(row);
            if (!line.strip().equals(exactText)) continue;
            int start = line.indexOf(exactText);
            return new TextRange(
                    start,
                    start + exactText.length() - 1,
                    row,
                    exactText.getBytes(java.nio.charset.StandardCharsets.UTF_8).length);
        }
        throw new IllegalStateException("Terminal does not contain the exact selection witness line");
    }

    public static Point point(SFMTerminalPanel panel, int column, int row) {
        SFMTerminalPanel.TerminalCellPoint point = panel.terminalCellCenterForAutomation(column, row);
        return new Point(point.x(), point.y());
    }

    public static Observation observe(
            SFMTerminalPanel panel,
            String expectedRenderer,
            String expectedTransport
    ) {
        SFMTerminalRemoteService service = remoteService(panel);
        Map<String, String> fields = parseEvidence(service.assertPushEvidenceForAutomation(false));
        requireEqual(expectedRenderer, required(fields, "requested_renderer"), "requested renderer");
        requireEqual(expectedRenderer, required(fields, "active_renderer"), "active renderer");
        requireEqual(expectedTransport, required(fields, "requested_transport"), "requested transport");
        requireEqual(expectedTransport, required(fields, "active_transport"), "active transport");

        Optional<SFMTerminalSelection> selection = service.selection();
        boolean evidenceSelectionPresent = Boolean.parseBoolean(required(fields, "selection_present"));
        if (evidenceSelectionPresent != selection.isPresent()) {
            throw new IllegalStateException("Selection evidence disagrees with the transport-neutral service state");
        }
        if (selection.isPresent()) {
            SFMTerminalSelection value = selection.get();
            requireEqual(value.anchorX(), integer(fields, "selection_anchor_x"), "selection anchor x");
            requireEqual(value.anchorY(), integer(fields, "selection_anchor_y"), "selection anchor y");
            requireEqual(value.focusX(), integer(fields, "selection_focus_x"), "selection focus x");
            requireEqual(value.focusY(), integer(fields, "selection_focus_y"), "selection focus y");
        }
        SFMTerminalPngTelemetry.Snapshot png = panel.pngTelemetryForAutomation();
        SFMTerminalRgbaRenderer.Snapshot rgba = panel.rgbaTelemetryForAutomation();
        return new Observation(
                expectedRenderer,
                expectedTransport,
                nonBlank(fields, "session_id"),
                nonBlank(fields, "connection_epoch"),
                nonBlank(fields, "session_epoch"),
                nonBlank(fields, "presentation_generation"),
                nonNegativeLong(fields, "latest_interaction_sequence"),
                selection,
                panel.selectionHighlightsForAutomation(),
                nonNegativeLong(fields, "raster_frames_received"),
                nonNegativeLong(fields, "raster_frames_accepted"),
                nonNegativeLong(fields, "raster_full_frames"),
                nonNegativeLong(fields, "raster_dirty_frames"),
                nonNegativeLong(fields, "latest_terminal_sequence"),
                nonNegativeLong(fields, "latest_frame_sequence"),
                nonNegativeLong(fields, "producer_renders_started"),
                nonNegativeLong(fields, "producer_renders_completed"),
                nonNegativeLong(fields, "producer_frames_pushed"),
                nonNegativeLong(fields, "renderer_requested_pixels"),
                nonNegativeLong(fields, "renderer_readback_bytes"),
                nonNegativeLong(fields, "renderer_frame_buffer_grows"),
                nonNegativeLong(fields, "renderer_frame_buffer_reuses"),
                nonNegativeLong(fields, "renderer_png_buffer_grows"),
                nonNegativeLong(fields, "renderer_png_buffer_reuses"),
                nonNegativeLong(fields, "renderer_transport_payload_copies"),
                png.pngDecodeAttempts(),
                png.dynamicTextureUploads(),
                png.framesPresented(),
                rgba.uploads(),
                rgba.bytesUploaded());
    }

    public static boolean matchesSelection(Observation observation, TextRange expected, boolean reverse) {
        int anchor = reverse ? expected.endColumn() : expected.startColumn();
        int focus = reverse ? expected.startColumn() : expected.endColumn();
        return observation.selection().filter(selection ->
                selection.anchorX() == anchor
                        && selection.anchorY() == expected.row()
                        && selection.focusX() == focus
                        && selection.focusY() == expected.row()).isPresent();
    }

    public static String selectionArtifact(
            Observation before,
            Observation after,
            TextRange expected,
            boolean reverse
    ) {
        requireStablePresentation(before, after);
        if (after.interactionSequence() <= before.interactionSequence()) {
            throw new IllegalStateException("Selection did not advance the interaction sequence");
        }
        if (!matchesSelection(after, expected, reverse)) {
            throw new IllegalStateException("Rust-authoritative selection did not match the requested cells");
        }
        if (after.highlights().isEmpty()) {
            throw new IllegalStateException("Java selection overlay produced no highlight rectangles");
        }
        requireNoRasterWork(before, after);
        return commonArtifact("selection", before, after)
                + "drag_reverse=" + reverse + "\n"
                + "selection_anchor_x=" + (reverse ? expected.endColumn() : expected.startColumn()) + "\n"
                + "selection_anchor_y=" + expected.row() + "\n"
                + "selection_focus_x=" + (reverse ? expected.startColumn() : expected.endColumn()) + "\n"
                + "selection_focus_y=" + expected.row() + "\n"
                + "selection_utf8_bytes=" + expected.utf8Bytes() + "\n"
                + "highlight_count=" + after.highlights().size() + "\n"
                + highlightArtifact(after.highlights())
                + "selection_matches=true\n"
                + "selection_only_zero_raster_work=true\n";
    }

    public static String copyArtifact(
            Observation before,
            Observation after,
            boolean clipboardMatches,
            boolean rightClick
    ) {
        requireStablePresentation(before, after);
        if (before.selection().isEmpty() || after.selection().isPresent()) {
            throw new IllegalStateException("Copy did not atomically clear the authoritative selection");
        }
        if (after.interactionSequence() <= before.interactionSequence()) {
            throw new IllegalStateException("Copy did not advance the interaction sequence");
        }
        if (!after.highlights().isEmpty()) {
            throw new IllegalStateException("Java retained selection highlights after copy-clear");
        }
        if (!clipboardMatches) {
            throw new IllegalStateException("Minecraft clipboard did not match the exact selected text");
        }
        requireNoRasterWork(before, after);
        return commonArtifact(rightClick ? "right-click-copy" : "ctrl-c-copy", before, after)
                + "clipboard_exact_match=true\n"
                + "selection_cleared=true\n"
                + "highlight_cleared=true\n"
                + "copy_clear_zero_raster_work=true\n";
    }

    public static String absentSelectionArtifact(
            Observation observation,
            boolean childMouseForwarded
    ) {
        if (observation.selection().isPresent() || !observation.highlights().isEmpty()) {
            throw new IllegalStateException("Child mouse-reporting input became a shell selection");
        }
        return "schema=sfm-terminal-interaction-puppet-v1\n"
                + "operation=child-mouse-forwarding\n"
                + "renderer=" + observation.rendererId() + "\n"
                + "transport=" + observation.transportId() + "\n"
                + "selection_present=false\n"
                + "highlight_count=0\n"
                + "child_mouse_forwarded=" + childMouseForwarded + "\n";
    }

    private static String commonArtifact(String operation, Observation before, Observation after) {
        return "schema=sfm-terminal-interaction-puppet-v1\n"
                + "operation=" + operation + "\n"
                + "renderer=" + after.rendererId() + "\n"
                + "transport=" + after.transportId() + "\n"
                + "session_unchanged=true\n"
                + "presentation_generation_unchanged=true\n"
                + "interaction_sequence_before=" + before.interactionSequence() + "\n"
                + "interaction_sequence_after=" + after.interactionSequence() + "\n"
                + counterArtifact("raster_frames_received", before.rasterFramesReceived(), after.rasterFramesReceived())
                + counterArtifact("raster_frames_accepted", before.rasterFramesAccepted(), after.rasterFramesAccepted())
                + counterArtifact("raster_full_frames", before.rasterFullFrames(), after.rasterFullFrames())
                + counterArtifact("raster_dirty_frames", before.rasterDirtyFrames(), after.rasterDirtyFrames())
                + counterArtifact("latest_terminal_sequence", before.latestTerminalSequence(), after.latestTerminalSequence())
                + counterArtifact("latest_frame_sequence", before.latestFrameSequence(), after.latestFrameSequence())
                + counterArtifact("producer_renders_started", before.producerRendersStarted(), after.producerRendersStarted())
                + counterArtifact("producer_renders_completed", before.producerRendersCompleted(), after.producerRendersCompleted())
                + counterArtifact("producer_frames_pushed", before.producerFramesPushed(), after.producerFramesPushed())
                + counterArtifact("renderer_requested_pixels", before.rendererRequestedPixels(), after.rendererRequestedPixels())
                + counterArtifact("renderer_readback_bytes", before.rendererReadbackBytes(), after.rendererReadbackBytes())
                + counterArtifact("renderer_frame_buffer_grows", before.rendererFrameBufferGrows(), after.rendererFrameBufferGrows())
                + counterArtifact("renderer_frame_buffer_reuses", before.rendererFrameBufferReuses(), after.rendererFrameBufferReuses())
                + counterArtifact("renderer_png_buffer_grows", before.rendererPngBufferGrows(), after.rendererPngBufferGrows())
                + counterArtifact("renderer_png_buffer_reuses", before.rendererPngBufferReuses(), after.rendererPngBufferReuses())
                + counterArtifact("renderer_transport_payload_copies", before.rendererTransportPayloadCopies(), after.rendererTransportPayloadCopies())
                + counterArtifact("java_png_decode_attempts", before.pngDecodeAttempts(), after.pngDecodeAttempts())
                + counterArtifact("java_png_texture_uploads", before.pngTextureUploads(), after.pngTextureUploads())
                + counterArtifact("java_png_frames_presented", before.pngFramesPresented(), after.pngFramesPresented())
                + counterArtifact("java_rgba_uploads", before.rgbaUploads(), after.rgbaUploads())
                + counterArtifact("java_rgba_bytes_uploaded", before.rgbaBytesUploaded(), after.rgbaBytesUploaded());
    }

    private static String highlightArtifact(List<SFMTerminalSelectionLayout.LogicalHighlight> highlights) {
        StringBuilder result = new StringBuilder();
        for (int index = 0; index < highlights.size(); index++) {
            SFMTerminalSelectionLayout.LogicalHighlight highlight = highlights.get(index);
            result.append("highlight_").append(index).append('=')
                    .append(highlight.startX()).append(',')
                    .append(highlight.startY()).append(',')
                    .append(highlight.endX()).append(',')
                    .append(highlight.endY()).append('\n');
        }
        return result.toString();
    }

    private static String counterArtifact(String name, long before, long after) {
        return name + "_before=" + before + "\n"
                + name + "_after=" + after + "\n"
                + name + "_delta=" + (after - before) + "\n";
    }

    private static void requireStablePresentation(Observation before, Observation after) {
        requireEqual(before.rendererId(), after.rendererId(), "renderer");
        requireEqual(before.transportId(), after.transportId(), "transport");
        requireEqual(before.sessionId(), after.sessionId(), "session");
        requireEqual(before.connectionEpoch(), after.connectionEpoch(), "connection epoch");
        requireEqual(before.sessionEpoch(), after.sessionEpoch(), "session epoch");
        requireEqual(before.presentationGeneration(), after.presentationGeneration(), "presentation generation");
    }

    private static void requireNoRasterWork(Observation before, Observation after) {
        requireEqual(before.rasterFramesReceived(), after.rasterFramesReceived(), "raster frames received");
        requireEqual(before.rasterFramesAccepted(), after.rasterFramesAccepted(), "raster frames accepted");
        requireEqual(before.rasterFullFrames(), after.rasterFullFrames(), "raster full frames");
        requireEqual(before.rasterDirtyFrames(), after.rasterDirtyFrames(), "raster dirty frames");
        requireEqual(before.latestTerminalSequence(), after.latestTerminalSequence(), "raster terminal sequence");
        requireEqual(before.latestFrameSequence(), after.latestFrameSequence(), "raster frame sequence");
        requireEqual(before.producerRendersStarted(), after.producerRendersStarted(), "producer renders started");
        requireEqual(before.producerRendersCompleted(), after.producerRendersCompleted(), "producer renders completed");
        requireEqual(before.producerFramesPushed(), after.producerFramesPushed(), "producer frames pushed");
        requireEqual(before.rendererRequestedPixels(), after.rendererRequestedPixels(), "renderer requested pixels");
        requireEqual(before.rendererReadbackBytes(), after.rendererReadbackBytes(), "renderer readback bytes");
        requireEqual(before.rendererFrameBufferGrows(), after.rendererFrameBufferGrows(), "renderer frame-buffer grows");
        requireEqual(before.rendererFrameBufferReuses(), after.rendererFrameBufferReuses(), "renderer frame-buffer reuses");
        requireEqual(before.rendererPngBufferGrows(), after.rendererPngBufferGrows(), "renderer PNG-buffer grows");
        requireEqual(before.rendererPngBufferReuses(), after.rendererPngBufferReuses(), "renderer PNG-buffer reuses");
        requireEqual(before.rendererTransportPayloadCopies(), after.rendererTransportPayloadCopies(), "renderer transport payload copies");
        requireEqual(before.pngDecodeAttempts(), after.pngDecodeAttempts(), "Java PNG decodes");
        requireEqual(before.pngTextureUploads(), after.pngTextureUploads(), "Java PNG uploads");
        requireEqual(before.rgbaUploads(), after.rgbaUploads(), "Java RGBA uploads");
        requireEqual(before.rgbaBytesUploaded(), after.rgbaBytesUploaded(), "Java RGBA uploaded bytes");
    }

    private static SFMTerminalRemoteService remoteService(SFMTerminalPanel panel) {
        try {
            return (SFMTerminalRemoteService) REMOTE_SERVICE.get(panel);
        } catch (IllegalAccessException error) {
            throw new IllegalStateException("Could not inspect terminal remote service", error);
        }
    }

    private static Map<String, String> parseEvidence(String evidence) {
        Map<String, String> result = new LinkedHashMap<>();
        for (String line : evidence.lines().toList()) {
            int separator = line.indexOf('=');
            if (separator <= 0) continue;
            result.put(line.substring(0, separator), line.substring(separator + 1));
        }
        return result;
    }

    private static String required(Map<String, String> fields, String key) {
        String value = fields.get(key);
        if (value == null) throw new IllegalStateException("Terminal evidence omitted " + key);
        return value;
    }

    private static String nonBlank(Map<String, String> fields, String key) {
        String value = required(fields, key);
        if (value.isBlank()) throw new IllegalStateException("Terminal evidence left " + key + " blank");
        return value;
    }

    private static int integer(Map<String, String> fields, String key) {
        return Math.toIntExact(number(fields, key));
    }

    private static long nonNegativeLong(Map<String, String> fields, String key) {
        long value = number(fields, key);
        if (value < 0) throw new IllegalStateException("Terminal evidence made " + key + " negative");
        return value;
    }

    private static long number(Map<String, String> fields, String key) {
        try {
            return Long.parseLong(required(fields, key));
        } catch (NumberFormatException error) {
            throw new IllegalStateException("Terminal evidence made " + key + " non-numeric", error);
        }
    }

    private static void requireEqual(Object expected, Object actual, String label) {
        if (!java.util.Objects.equals(expected, actual)) {
            throw new IllegalStateException("Terminal " + label + " was " + actual + " instead of " + expected);
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
}
