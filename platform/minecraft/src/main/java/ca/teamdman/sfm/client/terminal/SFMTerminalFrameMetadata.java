package ca.teamdman.sfm.client.terminal;

/**
 * Transport-neutral Rust frame metadata retained for presentation telemetry.
 * Durations are microseconds and are zero when the producer did not measure a
 * stage.
 */
record SFMTerminalFrameMetadata(
        long requestSequence,
        int logicalColumns,
        int logicalRows,
        int panelWidth,
        int panelHeight,
        int cellWidth,
        int cellHeight,
        int fontPixelSize,
        String rendererId,
        String transportId,
        long ptyDrainUs,
        long vtUpdateUs,
        long snapshotUs,
        long fontLoadUs,
        long rasterUs,
        long frameBuildUs,
        long encodeUs,
        long rustTotalUs,
        String correlationId) {
    SFMTerminalFrameMetadata {
        rendererId = rendererId == null ? "" : rendererId;
        transportId = transportId == null ? "" : transportId;
        correlationId = correlationId == null ? "" : correlationId;
    }

    /** Compatibility alias used only by the retired snapshot/frame binding. */
    String backendId() {
        return rendererId;
    }
}
