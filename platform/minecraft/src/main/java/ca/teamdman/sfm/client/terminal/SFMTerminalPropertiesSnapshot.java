package ca.teamdman.sfm.client.terminal;

import ca.teamdman.sfm.client.screen.workspace.SFMScreenPanelBounds;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspacePanelMetrics;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Immutable production diagnostics shared by the properties panel and puppets. */
public record SFMTerminalPropertiesSnapshot(
        SFMTerminalTuningSettings requested,
        SFMTerminalTuningSettings accepted,
        SFMTerminalTuningSettings.Effective effective,
        SFMTerminalTuningSettings.Effective automatic,
        boolean tuningPending,
        SFMScreenPanelBounds panelLogicalBounds,
        SFMScreenPanelBounds viewportLogicalBounds,
        Optional<SFMWorkspacePanelMetrics> panelMetrics,
        Optional<SFMWorkspacePanelMetrics> workspaceMetrics,
        int configuredGuiScale,
        int effectiveGuiScale,
        Optional<SFMScreenPanelBounds> javaDrawLogicalBounds,
        Optional<SFMScreenPanelBounds> javaDrawPhysicalBounds,
        Optional<AcceptedFrame> acceptedFrame,
        Optional<SFMTerminalPresentationDiagnostics> presentation,
        String requestedRenderer,
        String activeRenderer,
        String requestedTransport,
        String activeTransport,
        Optional<SFMTerminalTuningRejection> lastTypedRejection
) {
    public SFMTerminalPropertiesSnapshot {
        panelMetrics = optional(panelMetrics);
        workspaceMetrics = optional(workspaceMetrics);
        javaDrawLogicalBounds = optional(javaDrawLogicalBounds);
        javaDrawPhysicalBounds = optional(javaDrawPhysicalBounds);
        acceptedFrame = optional(acceptedFrame);
        presentation = optional(presentation);
        lastTypedRejection = optional(lastTypedRejection);
        requestedRenderer = normalize(requestedRenderer);
        activeRenderer = normalize(activeRenderer);
        requestedTransport = normalize(requestedTransport);
        activeTransport = normalize(activeTransport);
    }

    public static AcceptedFrame fromFrame(SFMTerminalFrame frame) {
        SFMTerminalFrameMetadata metadata = frame.metadata();
        return new AcceptedFrame(
                frame.sequence(),
                frame.streamIdentity(),
                metadata.logicalColumns(),
                metadata.logicalRows(),
                metadata.targetPanelWidth(),
                metadata.targetPanelHeight(),
                metadata.cellWidth(),
                metadata.cellHeight(),
                metadata.fontPixelSize(),
                metadata.rendererId(),
                metadata.transportId(),
                frame.payload().length,
                metadata.correlationId());
    }

    public String lastRejection() {
        return lastTypedRejection
                .map(rejection -> rejection.error().code() + ": " + rejection.error().message())
                .orElse("");
    }

    /** Stable key/value diagnostics consumed by live puppet artifacts. */
    public String artifact() {
        ArrayList<String> lines = new ArrayList<>();
        lines.add("properties_schema=sfm-terminal-properties-v2");
        lines.add("properties_configured_gui_scale=" + configuredGuiScale);
        lines.add("properties_effective_gui_scale=" + effectiveGuiScale);
        appendBounds(lines, "properties_panel_logical", panelLogicalBounds);
        appendBounds(lines, "properties_viewport_logical", viewportLogicalBounds);
        panelMetrics.ifPresent(metrics -> appendMetrics(lines, "properties_panel", metrics));
        workspaceMetrics.ifPresent(metrics -> appendMetrics(lines, "properties_viewport", metrics));

        appendSettings(lines, "properties_requested", requested);
        appendSettings(lines, "properties_accepted_override", accepted);
        lines.add("properties_tuning_pending=" + tuningPending);
        lines.add("properties_surface_mode="
                + (requested.surfaceWidth() == 0 && requested.surfaceHeight() == 0 ? "auto" : "manual"));
        lines.add("properties_font_mode=" + (requested.fontPixelSize() == 0 ? "auto" : "manual"));
        lines.add("properties_cells_mode="
                + (requested.columns() == 0 && requested.rows() == 0 ? "auto" : "manual"));
        lines.add("properties_effective_surface_width=" + effective.surfaceWidth());
        lines.add("properties_effective_surface_height=" + effective.surfaceHeight());
        lines.add("properties_effective_font_pixel_size=" + effective.fontPixelSize());
        lines.add("properties_effective_columns=" + effective.columns());
        lines.add("properties_effective_rows=" + effective.rows());
        lines.add("properties_automatic_surface_width=" + automatic.surfaceWidth());
        lines.add("properties_automatic_surface_height=" + automatic.surfaceHeight());
        lines.add("properties_automatic_columns=" + automatic.columns());
        lines.add("properties_automatic_rows=" + automatic.rows());
        lines.add("properties_surface_step=" + SFMTerminalTuningSettings.SURFACE_STEP);
        lines.add("properties_font_step=" + SFMTerminalTuningSettings.FONT_STEP);
        lines.add("properties_column_step=" + SFMTerminalTuningSettings.COLUMN_STEP);
        lines.add("properties_row_step=" + SFMTerminalTuningSettings.ROW_STEP);
        lines.add("properties_surface_max_width=" + SFMTerminalRasterLimits.RGBA8_V1_MAX_WIDTH);
        lines.add("properties_surface_max_height=" + SFMTerminalRasterLimits.RGBA8_V1_MAX_HEIGHT);
        lines.add("properties_font_min=" + SFMTerminalTuningSettings.MIN_FONT_PIXEL_SIZE);
        lines.add("properties_font_max=" + SFMTerminalTuningSettings.MAX_FONT_PIXEL_SIZE);
        lines.add("properties_columns_max=" + SFMTerminalTuningSettings.MAX_COLUMNS);
        lines.add("properties_rows_max=" + SFMTerminalTuningSettings.MAX_ROWS);
        workspaceMetrics.ifPresent(metrics -> {
            SFMScreenPanelBounds physical = metrics.physicalPixelBounds();
            lines.add("properties_surface_width_clamp="
                    + (requested.surfaceWidth() == 0
                    ? Math.max(0, physical.width() - automatic.surfaceWidth()) : 0));
            lines.add("properties_surface_height_clamp="
                    + (requested.surfaceHeight() == 0
                    ? Math.max(0, physical.height() - automatic.surfaceHeight()) : 0));
        });
        lines.add("properties_columns_clamp=" + (requested.columns() == 0
                ? Math.max(0, automatic.columns() - effective.columns()) : 0));
        lines.add("properties_rows_clamp=" + (requested.rows() == 0
                ? Math.max(0, automatic.rows() - effective.rows()) : 0));

        lines.add("properties_requested_renderer=" + requestedRenderer);
        lines.add("properties_active_renderer=" + activeRenderer);
        lines.add("properties_requested_transport=" + requestedTransport);
        lines.add("properties_active_transport=" + activeTransport);
        javaDrawLogicalBounds.ifPresent(value -> appendBounds(lines, "properties_java_draw_logical", value));
        javaDrawPhysicalBounds.ifPresent(value -> appendBounds(lines, "properties_java_draw_physical", value));
        acceptedFrame.ifPresent(frame -> appendAcceptedFrame(lines, frame));
        presentation.ifPresent(value -> appendPresentation(lines, value));
        appendScaleOne(lines);
        appendRejection(lines);
        return String.join("\n", lines);
    }

    private void appendScaleOne(List<String> lines) {
        if (acceptedFrame.isEmpty() || javaDrawPhysicalBounds.isEmpty()) return;
        AcceptedFrame frame = acceptedFrame.get();
        SFMScreenPanelBounds draw = javaDrawPhysicalBounds.get();
        int widthDelta = draw.width() - frame.nativeWidth();
        int heightDelta = draw.height() - frame.nativeHeight();
        int widthTolerance = workspaceMetrics
                .map(SFMWorkspacePanelMetrics::localToPhysicalScaleX)
                .map(value -> Math.max(1, (int) Math.ceil(value)))
                .orElse(1);
        int heightTolerance = workspaceMetrics
                .map(SFMWorkspacePanelMetrics::localToPhysicalScaleY)
                .map(value -> Math.max(1, (int) Math.ceil(value)))
                .orElse(1);
        lines.add("properties_java_draw_physical_width_delta=" + widthDelta);
        lines.add("properties_java_draw_physical_height_delta=" + heightDelta);
        lines.add("properties_texture_to_framebuffer_scale_x="
                + draw.width() / (double) Math.max(1, frame.nativeWidth()));
        lines.add("properties_texture_to_framebuffer_scale_y="
                + draw.height() / (double) Math.max(1, frame.nativeHeight()));
        lines.add("properties_java_framebuffer_scale_one="
                + (Math.abs(widthDelta) <= widthTolerance && Math.abs(heightDelta) <= heightTolerance));
        workspaceMetrics.ifPresent(metrics -> {
            SFMScreenPanelBounds viewport = metrics.physicalPixelBounds();
            lines.add("properties_letterbox_left=" + Math.max(0, draw.x() - viewport.x()));
            lines.add("properties_letterbox_top=" + Math.max(0, draw.y() - viewport.y()));
            lines.add("properties_letterbox_right=" + Math.max(0,
                    viewport.x() + viewport.width() - draw.x() - draw.width()));
            lines.add("properties_letterbox_bottom=" + Math.max(0,
                    viewport.y() + viewport.height() - draw.y() - draw.height()));
        });
    }

    private void appendRejection(List<String> lines) {
        lines.add("properties_last_rejection_present=" + lastTypedRejection.isPresent());
        lastTypedRejection.ifPresent(rejection -> {
            lines.add("properties_last_rejection_code=" + rejection.error().code());
            lines.add("properties_last_rejection_message="
                    + rejection.error().message().replace('\n', ' '));
            lines.add("properties_last_rejection_retryable=" + rejection.error().retryable());
            lines.add("properties_last_rejection_server_sequence=" + rejection.error().serverSequence());
            lines.add("properties_last_rejection_request=" + rejection.request().replace('\n', ' '));
        });
    }

    private static void appendSettings(
            List<String> lines,
            String prefix,
            SFMTerminalTuningSettings value
    ) {
        lines.add(prefix + "_surface_width=" + value.surfaceWidth());
        lines.add(prefix + "_surface_height=" + value.surfaceHeight());
        lines.add(prefix + "_font_pixel_size=" + value.fontPixelSize());
        lines.add(prefix + "_columns=" + value.columns());
        lines.add(prefix + "_rows=" + value.rows());
    }

    private static void appendMetrics(
            List<String> lines,
            String prefix,
            SFMWorkspacePanelMetrics metrics
    ) {
        appendBounds(lines, prefix + "_global_gui", metrics.globalGuiLogicalBounds());
        appendBounds(lines, prefix + "_physical", metrics.physicalPixelBounds());
        lines.add(prefix + "_render_scale=" + metrics.panelRenderScale());
        lines.add(prefix + "_gui_scale_override=" + metrics.panelGuiScaleOverride());
        lines.add(prefix + "_gui_to_physical_scale_x=" + metrics.guiToPhysicalScaleX());
        lines.add(prefix + "_gui_to_physical_scale_y=" + metrics.guiToPhysicalScaleY());
        lines.add(prefix + "_local_to_physical_scale_x=" + metrics.localToPhysicalScaleX());
        lines.add(prefix + "_local_to_physical_scale_y=" + metrics.localToPhysicalScaleY());
        lines.add(prefix + "_framebuffer_width=" + metrics.framebufferWidth());
        lines.add(prefix + "_framebuffer_height=" + metrics.framebufferHeight());
        lines.add(prefix + "_gui_width=" + metrics.guiWidth());
        lines.add(prefix + "_gui_height=" + metrics.guiHeight());
    }

    private static void appendAcceptedFrame(List<String> lines, AcceptedFrame frame) {
        lines.add("properties_accepted_frame_sequence=" + frame.sequence());
        lines.add("properties_accepted_stream_identity=" + frame.streamIdentity());
        lines.add("properties_accepted_columns=" + frame.columns());
        lines.add("properties_accepted_rows=" + frame.rows());
        lines.add("properties_accepted_target_width=" + frame.targetWidth());
        lines.add("properties_accepted_target_height=" + frame.targetHeight());
        lines.add("properties_accepted_native_width=" + frame.nativeWidth());
        lines.add("properties_accepted_native_height=" + frame.nativeHeight());
        lines.add("properties_accepted_cell_width=" + frame.cellWidth());
        lines.add("properties_accepted_cell_height=" + frame.cellHeight());
        lines.add("properties_accepted_font_pixel_size=" + frame.fontPixelSize());
        lines.add("properties_accepted_remainder_x=" + frame.remainderX());
        lines.add("properties_accepted_remainder_y=" + frame.remainderY());
        lines.add("properties_accepted_renderer=" + frame.renderer());
        lines.add("properties_accepted_transport=" + frame.transport());
        lines.add("properties_accepted_payload_bytes=" + frame.payloadBytes());
        lines.add("properties_accepted_correlation_id=" + frame.correlationId());
    }

    private static void appendPresentation(
            List<String> lines,
            SFMTerminalPresentationDiagnostics value
    ) {
        lines.add("properties_presentation_generation=" + value.presentationGeneration());
        lines.add("properties_terminal_sequence=" + value.terminalSequence());
        lines.add("properties_frame_sequence=" + value.frameSequence());
        lines.add("properties_base_frame_sequence=" + value.baseFrameSequence());
        lines.add("properties_full_resync=" + value.fullResync());
        lines.add("properties_frame_kind=" + value.frameKind());
        lines.add("properties_frame_payload_bytes=" + value.payloadBytes());
        lines.add("properties_maximum_payload_bytes=" + value.maximumPayloadBytes());
        lines.add("properties_frames_received=" + value.framesReceived());
        lines.add("properties_frames_accepted=" + value.framesAccepted());
        lines.add("properties_frames_rejected=" + value.framesRejected());
        lines.add("properties_stale_frames=" + value.staleFrames());
        lines.add("properties_receiver_failures=" + value.receiverFailures());
        lines.add("properties_full_resync_frames=" + value.fullResyncFrames());
        lines.add("properties_maximum_frame_bytes=" + value.maximumFrameBytes());
    }

    private static void appendBounds(List<String> lines, String prefix, SFMScreenPanelBounds bounds) {
        lines.add(prefix + "_x=" + bounds.x());
        lines.add(prefix + "_y=" + bounds.y());
        lines.add(prefix + "_width=" + bounds.width());
        lines.add(prefix + "_height=" + bounds.height());
    }

    private static String normalize(String value) {
        return value == null ? "" : value;
    }

    private static <T> Optional<T> optional(Optional<T> value) {
        return value == null ? Optional.empty() : value;
    }

    public record AcceptedFrame(
            long sequence,
            String streamIdentity,
            int columns,
            int rows,
            int targetWidth,
            int targetHeight,
            int cellWidth,
            int cellHeight,
            int fontPixelSize,
            String renderer,
            String transport,
            int payloadBytes,
            String correlationId
    ) {
        public AcceptedFrame {
            streamIdentity = normalize(streamIdentity);
            renderer = normalize(renderer);
            transport = normalize(transport);
            correlationId = normalize(correlationId);
        }

        public int nativeWidth() {
            return columns * cellWidth;
        }

        public int nativeHeight() {
            return rows * cellHeight;
        }

        public int remainderX() {
            return Math.max(0, targetWidth - nativeWidth());
        }

        public int remainderY() {
            return Math.max(0, targetHeight - nativeHeight());
        }
    }
}
