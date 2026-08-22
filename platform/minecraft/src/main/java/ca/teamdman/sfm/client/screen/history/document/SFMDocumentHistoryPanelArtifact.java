package ca.teamdman.sfm.client.screen.history.document;

import ca.teamdman.sfm.client.history.canvas.SFMHistoryCanvasLayout;
import ca.teamdman.sfm.client.history.canvas.SFMHistoryCanvasSpatialIndex;
import ca.teamdman.sfm.client.history.document.SFMDocumentHistoryContract;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Machine-readable evidence exposed by the live document-history panel. */
public record SFMDocumentHistoryPanelArtifact(
        String schema,
        String requestedSelector,
        SFMDocumentHistoryPanel.LoadStatus loadStatus,
        String statusMessage,
        long catalogRevision,
        long publicationGeneration,
        Optional<String> resolvedSessionId,
        Optional<SFMDocumentHistoryContract.Projection> documentProjection,
        Optional<SFMHistoryCanvasLayout.Snapshot> canvasLayout,
        List<String> accessibleTranscript,
        Optional<SFMHistoryCanvasSpatialIndex.Subject> selectedSubject,
        SFMDocumentHistoryViewport viewport
) {
    public static final String SCHEMA = "sfm.document-history-panel/1";

    public SFMDocumentHistoryPanelArtifact {
        if (!SCHEMA.equals(schema)) throw new IllegalArgumentException("Unsupported panel artifact schema: " + schema);
        requestedSelector = requireText(requestedSelector, "requestedSelector");
        Objects.requireNonNull(loadStatus, "loadStatus");
        statusMessage = requireText(statusMessage, "statusMessage");
        if (catalogRevision < -1) throw new IllegalArgumentException("catalogRevision must be at least -1");
        if (publicationGeneration < -1) {
            throw new IllegalArgumentException("publicationGeneration must be at least -1");
        }
        Objects.requireNonNull(resolvedSessionId, "resolvedSessionId");
        Objects.requireNonNull(documentProjection, "documentProjection");
        Objects.requireNonNull(canvasLayout, "canvasLayout");
        accessibleTranscript = List.copyOf(accessibleTranscript);
        Objects.requireNonNull(selectedSubject, "selectedSubject");
        Objects.requireNonNull(viewport, "viewport");
    }

    private static String requireText(String value, String label) {
        Objects.requireNonNull(value, label);
        if (value.isBlank()) throw new IllegalArgumentException(label + " must not be blank");
        return value;
    }
}
